package com.capswriter.offline.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.capswriter.offline.R
import com.capswriter.offline.databinding.OverlayCalibrationPanelBinding
import com.capswriter.offline.databinding.OverlayTargetBinding
import com.capswriter.offline.ui.MainActivity
import com.capswriter.offline.util.AppLog
import com.capswriter.offline.util.AppPrefs
import com.capswriter.offline.util.OverlayProfile
import com.capswriter.offline.util.VoiceState
import kotlin.math.roundToInt

/**
 * Owns two accessibility overlay windows:
 *
 *  - a tiny, fully transparent touch target placed over a key of the user's IME;
 *  - a calibration card shown only while the user positions that target.
 *
 * TYPE_ACCESSIBILITY_OVERLAY is used instead of TYPE_APPLICATION_OVERLAY because
 * application overlays are explicitly layered *below* the IME; an accessibility
 * overlay with FLAG_NOT_FOCUSABLE is layered above it. Do not add
 * FLAG_ALT_FOCUSABLE_IM here: that flag would put the target back under the keyboard.
 */
class OverlayController(
    private val service: CapsWriterAccessibilityService,
    private val prefs: AppPrefs,
) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val powerManager = service.getSystemService(PowerManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val inflater = LayoutInflater.from(ContextThemeWrapper(service, R.style.Theme_CapsWriter))
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var targetBinding: OverlayTargetBinding? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var panelBinding: OverlayCalibrationPanelBinding? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var imeBounds: Rect? = null
    private var screenBounds: Rect = Rect()
    private var activeImeKey: String = ""
    private var editMode = false
    private var calibrationWidthDp = prefs.buttonWidthDp
    private var calibrationHeightDp = prefs.buttonHeightDp
    private var originalWidthDp = prefs.buttonWidthDp
    private var originalHeightDp = prefs.buttonHeightDp
    private var updatingSeekBar = false
    private var refreshScheduled = false
    private var destroyed = false

    private var pressing = false
    private var moved = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragging = false

    private val poll = object : Runnable {
        override fun run() {
            if (destroyed) return
            refresh()
            main.postDelayed(this, 300)
        }
    }

    fun start() {
        screenBounds = readScreenBounds()
        activeImeKey = currentImeKey()
        refresh()
        main.postDelayed(poll, 250)
    }

    fun destroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        removePanel()
        removeTarget()
    }

    fun onWindowEvent() {
        if (destroyed || refreshScheduled) return
        refreshScheduled = true
        main.postDelayed({
            refreshScheduled = false
            refresh()
        }, 80)
    }

    fun refresh() {
        if (destroyed) return
        if (main.looper.thread != Thread.currentThread()) {
            main.post { refresh() }
            return
        }

        val newScreen = readScreenBounds()
        if (newScreen != screenBounds) {
            screenBounds = newScreen
            if (editMode) updatePanelWidth()
        }
        imeBounds = findImeBounds()
        val newImeKey = currentImeKey()
        if (newImeKey != activeImeKey) {
            activeImeKey = newImeKey
            prefs.profile(newImeKey)?.let {
                prefs.buttonWidthDp = it.widthDp
                prefs.buttonHeightDp = it.heightDp
            }
            if (!editMode) applyNormalGeometry()
        }
        val interactive = powerManager?.isInteractive != false

        if (!prefs.enabled) {
            if (editMode) {
                editMode = false
                restoreOriginalSize()
                removePanel()
            }
            removeTarget()
            return
        }

        val showTarget = interactive && (editMode || imeBounds != null)
        if (showTarget) {
            if (targetBinding == null) {
                addTarget()
            } else {
                applyTargetAppearance()
                if (!editMode) applyNormalGeometry()
            }
            if (editMode) {
                targetParams?.let { params ->
                    val clamped = clampToBounds(params.x, params.y, params.width, params.height)
                    if (clamped.first != params.x || clamped.second != params.y) {
                        params.x = clamped.first
                        params.y = clamped.second
                        updateTargetLayout()
                    }
                }
                showPanelIfNeeded()
            }
        } else {
            val voice = VoiceForegroundService.instance
            if (!editMode && voice != null &&
                (voice.state == VoiceState.RECORDING || voice.state == VoiceState.CONNECTING)
            ) {
                voice.cancelRecording(service.getString(R.string.reason_ime_hidden))
            }
            removeTarget()
        }

        if (!editMode) removePanel()
    }

    fun cancelCalibration() {
        if (editMode) finishCalibration(false)
    }

    fun startCalibration() {
        if (destroyed) return
        if (!prefs.enabled) {
            toast(service.getString(R.string.calibration_disabled))
            return
        }
        if (editMode) return

        val profile = prefs.profile(activeImeKey)
        originalWidthDp = profile?.widthDp ?: prefs.buttonWidthDp
        originalHeightDp = profile?.heightDp ?: prefs.buttonHeightDp
        calibrationWidthDp = originalWidthDp
        calibrationHeightDp = originalHeightDp
        editMode = true

        if (targetBinding == null) addTarget() else resizeTarget()
        applyTargetAppearance()
        showPanelIfNeeded()
        AppLog.add(service, "Entered overlay calibration: $activeImeKey")
    }

    private fun finishCalibration(save: Boolean) {
        if (!editMode) return

        if (save) {
            val ime = imeBounds
            val params = targetParams
            if (ime == null || params == null) {
                toast(service.getString(R.string.calibration_need_ime))
                return
            }
            val centerX = params.x + params.width / 2f
            val centerY = params.y + params.height / 2f
            val ratioX = ((centerX - ime.left) / ime.width().toFloat()).coerceIn(0.02f, 0.98f)
            val ratioY = ((centerY - ime.top) / ime.height().toFloat()).coerceIn(0.02f, 0.98f)

            prefs.buttonWidthDp = calibrationWidthDp
            prefs.buttonHeightDp = calibrationHeightDp
            prefs.saveProfile(
                activeImeKey,
                OverlayProfile(
                    xRatio = ratioX,
                    yRatio = ratioY,
                    widthDp = calibrationWidthDp,
                    heightDp = calibrationHeightDp,
                ),
            )
            toast(service.getString(R.string.calibration_saved))
            AppLog.add(
                service,
                "IME profile saved: $activeImeKey " +
                    "(${"%.2f".format(ratioX)}, ${"%.2f".format(ratioY)}, " +
                    "${calibrationWidthDp}x${calibrationHeightDp}dp)",
            )
        } else {
            restoreOriginalSize()
        }

        editMode = false
        removePanel()
        applyTargetAppearance()
        refresh()
    }

    private fun addTarget() {
        val widthDp = if (editMode) calibrationWidthDp else targetWidthDp()
        val heightDp = if (editMode) calibrationHeightDp else targetHeightDp()
        val width = dp(widthDp)
        val height = dp(heightDp)
        val position = calculatePosition(width, height)
        val params = baseParams(width, height, position.first, position.second)
        val binding = OverlayTargetBinding.inflate(inflater)

        binding.root.setOnTouchListener { _, event -> onTargetTouch(event) }
        binding.root.isClickable = true
        binding.root.isFocusable = false

        try {
            windowManager.addView(binding.root, params)
            targetBinding = binding
            targetParams = params
            applyTargetAppearance()
        } catch (error: Exception) {
            AppLog.add(service, "Cannot add overlay target: ${error.javaClass.simpleName} ${error.message.orEmpty()}")
            targetBinding = null
            targetParams = null
        }
    }

    private fun applyNormalGeometry() {
        val params = targetParams ?: return
        val width = dp(targetWidthDp())
        val height = dp(targetHeightDp())
        val position = calculatePosition(width, height)
        if (params.width != width || params.height != height || params.x != position.first || params.y != position.second) {
            params.width = width
            params.height = height
            params.x = position.first
            params.y = position.second
            updateTargetLayout()
        }
    }

    private fun targetWidthDp(): Int =
        prefs.profile(activeImeKey)?.widthDp ?: prefs.buttonWidthDp

    private fun targetHeightDp(): Int =
        prefs.profile(activeImeKey)?.heightDp ?: prefs.buttonHeightDp

    /** Called when the size sliders in the main screen move. */
    fun updateOverlaySize(widthDp: Int, heightDp: Int) {
        prefs.buttonWidthDp = widthDp
        prefs.buttonHeightDp = heightDp
        val profile = prefs.profile(activeImeKey)
        if (profile != null) {
            prefs.saveProfile(
                activeImeKey,
                profile.copy(widthDp = widthDp, heightDp = heightDp),
            )
        }
        if (!editMode) applyNormalGeometry()
    }

    fun clearAllProfiles() {
        prefs.clearProfiles()
        applyNormalGeometry()
    }

    private fun applyTargetAppearance() {
        val binding = targetBinding ?: return
        if (editMode) {
            binding.root.setBackgroundResource(R.drawable.bg_target_edit)
            binding.targetEditContent.visibility = View.VISIBLE
            binding.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            binding.root.contentDescription = service.getString(R.string.calibration_title)
        } else {
            binding.root.background = null
            binding.root.setBackgroundColor(Color.TRANSPARENT)
            binding.targetEditContent.visibility = View.GONE
            binding.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            binding.root.contentDescription = null
        }
    }

    private fun calculatePosition(width: Int, height: Int): Pair<Int, Int> {
        val ime = imeBounds
        val profile = prefs.profile(activeImeKey)
        val defaultX: Int
        val defaultY: Int
        if (ime != null && ime.width() > 0 && ime.height() > 0) {
            val ratioX = profile?.xRatio ?: 0.82f
            val ratioY = profile?.yRatio ?: 0.52f
            defaultX = (ime.left + ime.width() * ratioX - width / 2f).roundToInt()
            defaultY = (ime.top + ime.height() * ratioY - height / 2f).roundToInt()
        } else {
            defaultX = screenBounds.width() - width - dp(20)
            defaultY = (screenBounds.height() * 0.60f).roundToInt()
        }
        return clampToBounds(defaultX, defaultY, width, height)
    }

    private fun clampToBounds(x: Int, y: Int, width: Int, height: Int): Pair<Int, Int> {
        val area = imeBounds ?: screenBounds
        val maxX = (area.right - width).coerceAtLeast(area.left)
        val maxY = (area.bottom - height).coerceAtLeast(area.top)
        return Pair(x.coerceIn(area.left, maxX), y.coerceIn(area.top, maxY))
    }

    private fun showPanelIfNeeded() {
        if (!editMode || panelBinding != null || destroyed) return

        val binding = OverlayCalibrationPanelBinding.inflate(inflater)
        val panelWidth = minOf(screenBounds.width() - dp(24), dp(560)).coerceAtLeast(dp(240))
        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + dp(8)
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        updatingSeekBar = true
        binding.calibrationWidth.max = AppPrefs.MAX_WIDTH_DP - AppPrefs.MIN_WIDTH_DP
        binding.calibrationWidth.progress = calibrationWidthDp - AppPrefs.MIN_WIDTH_DP
        binding.tvCalibrationWidth.text = "${calibrationWidthDp}dp"
        binding.calibrationHeight.max = AppPrefs.MAX_HEIGHT_DP - AppPrefs.MIN_HEIGHT_DP
        binding.calibrationHeight.progress = calibrationHeightDp - AppPrefs.MIN_HEIGHT_DP
        binding.tvCalibrationHeight.text = "${calibrationHeightDp}dp"
        updatingSeekBar = false

        binding.calibrationWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingSeekBar) return
                calibrationWidthDp = (AppPrefs.MIN_WIDTH_DP + progress)
                    .coerceIn(AppPrefs.MIN_WIDTH_DP, AppPrefs.MAX_WIDTH_DP)
                binding.tvCalibrationWidth.text = "${calibrationWidthDp}dp"
                resizeTarget()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.calibrationHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingSeekBar) return
                calibrationHeightDp = (AppPrefs.MIN_HEIGHT_DP + progress)
                    .coerceIn(AppPrefs.MIN_HEIGHT_DP, AppPrefs.MAX_HEIGHT_DP)
                binding.tvCalibrationHeight.text = "${calibrationHeightDp}dp"
                resizeTarget()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnCalibrationCancel.setOnClickListener { finishCalibration(false) }
        binding.btnCalibrationSave.setOnClickListener { finishCalibration(true) }

        try {
            windowManager.addView(binding.root, params)
            panelBinding = binding
            panelParams = params
        } catch (error: Exception) {
            AppLog.add(service, "Cannot add calibration panel: ${error.message.orEmpty()}")
        }
    }

    private fun resizeTarget() {
        val params = targetParams ?: return
        val newWidth = dp(calibrationWidthDp)
        val newHeight = dp(calibrationHeightDp)
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        params.width = newWidth
        params.height = newHeight
        val clamped = clampToBounds(centerX - newWidth / 2, centerY - newHeight / 2, newWidth, newHeight)
        params.x = clamped.first
        params.y = clamped.second
        updateTargetLayout()
    }

    private fun updatePanelWidth() {
        val params = panelParams ?: return
        val width = minOf(screenBounds.width() - dp(24), dp(560)).coerceAtLeast(dp(240))
        if (params.width != width) {
            params.width = width
            runCatching { windowManager.updateViewLayout(panelBinding?.root, params) }
        }
    }

    private fun onTargetTouch(event: MotionEvent): Boolean {
        if (editMode) return onCalibrationTouch(event)

        val voice = VoiceForegroundService.instance
        if (voice == null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                toast(service.getString(R.string.toast_missing_service))
                openMainActivity()
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                moved = false
                pressing = prefs.holdToTalk && voice.startRecording()
                if (pressing) haptic(18)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.rawX - downRawX) > touchSlop ||
                    kotlin.math.abs(event.rawY - downRawY) > touchSlop
                ) {
                    moved = true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (prefs.holdToTalk) {
                    if (pressing) {
                        voice.stopRecording()
                        haptic(12)
                    }
                } else if (!moved) {
                    val state = voice.state
                    if (state == VoiceState.RECORDING || state == VoiceState.CONNECTING) {
                        voice.stopRecording()
                        haptic(12)
                    } else if (state != VoiceState.PROCESSING) {
                        if (voice.startRecording()) haptic(18)
                    }
                }
                pressing = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (pressing) {
                    voice.cancelRecording(service.getString(R.string.state_cancelled))
                }
                pressing = false
                return true
            }
        }
        return false
    }

    private fun onCalibrationTouch(event: MotionEvent): Boolean {
        val params = targetParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                downRawX = event.rawX
                downRawY = event.rawY
                dragStartX = params.x
                dragStartY = params.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val x = dragStartX + (event.rawX - downRawX).toInt()
                    val y = dragStartY + (event.rawY - downRawY).toInt()
                    val clamped = clampToBounds(x, y, params.width, params.height)
                    params.x = clamped.first
                    params.y = clamped.second
                    updateTargetLayout()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return false
    }

    private fun baseParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
        windowAnimations = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun updateTargetLayout() {
        val params = targetParams ?: return
        val view = targetBinding?.root ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removeTarget() {
        targetBinding?.let { binding ->
            runCatching { windowManager.removeView(binding.root) }
        }
        targetBinding = null
        targetParams = null
        dragging = false
        pressing = false
    }

    private fun removePanel() {
        panelBinding?.let { binding ->
            runCatching { windowManager.removeView(binding.root) }
        }
        panelBinding = null
        panelParams = null
    }

    private fun currentImeKey(): String {
        val id = runCatching {
            Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()
        val packageName = id?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: "default"
        val orientation = if (screenBounds.width() > screenBounds.height()) "land" else "port"
        return "${packageName}_$orientation"
    }

    private fun restoreOriginalSize() {
        calibrationWidthDp = originalWidthDp
        calibrationHeightDp = originalHeightDp
    }

    private fun findImeBounds(): Rect? {
        val windows = runCatching { service.windows.orEmpty() }.getOrElse { return null }
        val candidates = ArrayList<Pair<AccessibilityWindowInfo, Rect>>()
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val rect = Rect()
            window.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() < dp(80)) continue
            val clipped = Rect(rect)
            if (!clipped.intersect(screenBounds) || clipped.isEmpty) continue
            candidates.add(window to clipped)
        }
        // Do not filter on isVisible(): AccessibilityWindowInfo does not expose it
        // publicly on every supported API level. The IME window list only contains
        // currently attached windows, so the largest valid input-method window is
        // the keyboard.
        val chosen = candidates
            .maxByOrNull { it.second.width().toLong() * it.second.height() }
            ?: return null
        return chosen.second
    }

    private fun readScreenBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.bounds
        }
        val point = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(point)
        return Rect(0, 0, point.x, point.y)
    }

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).roundToInt()

    private fun haptic(milliseconds: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                service.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                service.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun toast(text: String) {
        Toast.makeText(service, text, Toast.LENGTH_SHORT).show()
    }

    private fun openMainActivity() {
        val intent = Intent(service, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        runCatching { service.startActivity(intent) }
    }
}
