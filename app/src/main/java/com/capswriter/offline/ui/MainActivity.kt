package com.capswriter.offline.ui

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.capswriter.offline.BuildConfig
import com.capswriter.offline.R
import com.capswriter.offline.audio.AudioWavWriter
import com.capswriter.offline.databinding.ActivityMainBinding
import com.capswriter.offline.net.ServerProbe
import com.capswriter.offline.net.UpdateChecker
import com.capswriter.offline.service.CapsWriterAccessibilityService
import com.capswriter.offline.service.VoiceForegroundService
import com.capswriter.offline.util.AppLog
import com.capswriter.offline.util.AppPrefs
import com.capswriter.offline.util.AppStatus
import com.capswriter.offline.util.HotwordProcessor
import com.capswriter.offline.util.ServerAddress
import com.capswriter.offline.util.VoiceState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPrefs

    private var updatingSwitch = false
    private var updatingRecents = false
    private var updatingSize = false
    private var updatingMode = false
    private var updatingPassThrough = false
    private var updatingPrewarm = false
    private var updatingOutput = false
    private var receiverRegistered = false
    private var showingSettings = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AppStatus.ACTION_STATUS -> updateGlobalStatus()
                AppLog.ACTION_LOG -> updateLogs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)
        applyRecentsPreference()

        setupServer()
        setupPermissions()
        setupOutputSettings()
        setupOverlaySettings()
        setupTestArea()
        setupHotwords()
        setupPageNavigation()
        showPage(false)
        updateAll()
        if (savedInstanceState == null) {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        UpdateChecker.check { update ->
            if (isFinishing || isDestroyed) return@check
            if (update == null ||
                !UpdateChecker.isNewer(update.version, BuildConfig.VERSION_NAME) ||
                update.version == prefs.skippedUpdateVersion
            ) {
                return@check
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(
                    getString(
                        R.string.update_available_message,
                        BuildConfig.VERSION_NAME,
                        update.version,
                    )
                )
                .setPositiveButton(R.string.update_button_github) { _, _ ->
                    openUpdatePage(update.pageUrl)
                }
                .setNeutralButton(R.string.update_button_later, null)
                .setNegativeButton(R.string.update_button_never) { _, _ ->
                    prefs.skippedUpdateVersion = update.version
                }
                .show()
        }
    }

    private fun openUpdatePage(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            toast(getString(R.string.update_browser_failed))
        }
    }

    private fun setupPageNavigation() {
        binding.btnOpenSettings.setOnClickListener {
            showPage(!showingSettings)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showingSettings) {
                    showPage(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun showPage(settings: Boolean) {
        showingSettings = settings
        binding.cardServer.isVisible = !settings
        binding.cardOverlay.isVisible = !settings
        binding.cardStatus.isVisible = settings
        binding.cardPermissions.isVisible = settings
        binding.cardOutput.isVisible = settings
        binding.cardTest.isVisible = settings
        binding.cardHotwords.isVisible = settings
        binding.cardGuide.isVisible = settings
        binding.cardLogs.isVisible = settings
        binding.tvHeaderTitle.setText(if (settings) R.string.settings_title else R.string.app_name)
        binding.tvHeaderSubtitle.setText(
            if (settings) R.string.settings_subtitle else R.string.app_subtitle
        )
        binding.btnOpenSettings.setText(if (settings) R.string.back_button else R.string.settings_button)
        binding.root.scrollTo(0, 0)
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(AppStatus.ACTION_STATUS)
                addAction(AppLog.ACTION_LOG)
            }
            ContextCompat.registerReceiver(
                this,
                statusReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onStop() {
        CapsWriterAccessibilityService.instance?.cancelCalibration()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(statusReceiver) }
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyRecentsPreference()
        updateAll()
        if (prefs.enabled && hasMicrophonePermission()) {
            startVoiceServiceSafely()
            updateGlobalStatus()
        }
        CapsWriterAccessibilityService.instance?.refreshOverlay()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionUi()
        if (requestCode == REQ_MIC && prefs.enabled && hasMicrophonePermission()) {
            startVoiceServiceSafely()
        }
    }

    private fun setupServer() {
        binding.etServer.setText(prefs.serverUrl)
        binding.btnSaveServer.setOnClickListener { saveAndTestServer() }
        binding.etServer.setOnEditorActionListener { _, _, _ ->
            saveAndTestServer()
            true
        }

        if (prefs.serverUrl.isBlank()) {
            binding.tvServerStatus.text = getString(R.string.server_example)
            binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        } else {
            binding.tvServerStatus.text = "${getString(R.string.server_saved)}：${prefs.serverUrl}"
            binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
        }
    }

    private fun saveAndTestServer() {
        val normalized = try {
            ServerAddress.normalize(binding.etServer.text?.toString().orEmpty())
        } catch (_: IllegalArgumentException) {
            binding.tilServer.error = getString(R.string.server_invalid)
            return
        }

        binding.tilServer.error = null
        prefs.serverUrl = normalized
        binding.etServer.setText(normalized)
        binding.etServer.clearFocus()
        hideKeyboard()

        binding.btnSaveServer.isEnabled = false
        binding.tvServerStatus.text = getString(R.string.server_testing)
        binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))

        ServerProbe.test(this, normalized) { ok, detail ->
            if (isFinishing || isDestroyed) return@test
            binding.btnSaveServer.isEnabled = true
            if (ok) {
                binding.tvServerStatus.text = "${getString(R.string.server_ok)} · $normalized"
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
                AppLog.add(this, "Server test succeeded: $normalized")
            } else {
                binding.tvServerStatus.text = "${getString(R.string.server_fail)}：$detail"
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                AppLog.add(this, "Server test failed: $detail")
            }
        }
    }

    private fun setupPermissions() {
        updatingSwitch = true
        binding.switchEnabled.isChecked = prefs.enabled
        updatingSwitch = false

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (checked) {
                tryEnableService()
            } else {
                prefs.enabled = false
                VoiceForegroundService.stop(this)
                CapsWriterAccessibilityService.instance?.refreshOverlay()
                toast(getString(R.string.toast_service_stopped))
                updateGlobalStatus()
            }
        }

        updatingRecents = true
        binding.switchHideRecents.isChecked = prefs.hideFromRecents
        updatingRecents = false
        binding.switchHideRecents.setOnCheckedChangeListener { _, checked ->
            if (updatingRecents) return@setOnCheckedChangeListener
            prefs.hideFromRecents = checked
            applyRecentsPreference()
        }

        binding.btnMic.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
        binding.btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
            }
        }
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
        binding.btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }
    }

    private fun applyRecentsPreference() {
        runCatching {
            val manager = getSystemService(ActivityManager::class.java)
            manager.appTasks
                .firstOrNull()
                ?.setExcludeFromRecents(prefs.hideFromRecents)
        }.onFailure {
            AppLog.add(this, "Cannot update recent-task visibility: ${it.javaClass.simpleName}")
        }
    }

    private fun tryEnableService() {
        if (!hasMicrophonePermission()) {
            setSwitchWithoutCallback(false)
            toast(getString(R.string.toast_need_mic))
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (!CapsWriterAccessibilityService.isEnabledInSettings(this)) {
            setSwitchWithoutCallback(false)
            showAccessibilityDialog()
            return
        }
        if (prefs.serverUrl.isBlank()) {
            setSwitchWithoutCallback(false)
            binding.tilServer.error = getString(R.string.server_empty)
            toast(getString(R.string.toast_need_server))
            return
        }

        prefs.enabled = true
        setSwitchWithoutCallback(true)
        if (!startVoiceServiceSafely()) {
            prefs.enabled = false
            setSwitchWithoutCallback(false)
            return
        }
        CapsWriterAccessibilityService.instance?.refreshOverlay()
        toast(getString(R.string.toast_service_started))
        updateGlobalStatus()
    }

    private fun startVoiceServiceSafely(): Boolean {
        return try {
            VoiceForegroundService.start(this)
            true
        } catch (error: Exception) {
            toast(getString(R.string.toast_foreground_start_failed, error.message ?: error.javaClass.simpleName))
            AppLog.add(this, "Foreground service start failed: ${error.javaClass.simpleName}")
            false
        }
    }

    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_enable_title)
            .setMessage(R.string.dialog_enable_message)
            .setPositiveButton(R.string.dialog_go) { _, _ -> openAccessibilitySettings() }
            .setNegativeButton(R.string.dialog_later, null)
            .show()
    }

    private fun openAccessibilitySettings() {
        toast(getString(R.string.accessibility_settings_hint))
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            toast(getString(R.string.toast_need_accessibility))
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val power = getSystemService(PowerManager::class.java)
        if (power?.isIgnoringBatteryOptimizations(packageName) == true) {
            toast(getString(R.string.battery_ignored))
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun setupOutputSettings() {
        updatingOutput = true
        binding.languageGroup.check(languageButtonId(prefs.language))
        binding.switchTrimPunctuation.isChecked = prefs.trimTrailingPunctuation
        binding.seekPuncThreshold.progress = prefs.trashPuncThreshold
        binding.switchSaveAudio.isChecked = prefs.saveAudio
        binding.seekMinRecord.progress = prefs.minRecordMs - AppPrefs.MIN_RECORD_MS_LIMIT
        updatingOutput = false
        updateMinRecordLabel()
        updatePuncThresholdLabel()
        updateRecordingPathUi()

        binding.languageGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingOutput || !isChecked) return@addOnButtonCheckedListener
            prefs.language = languageFromButton(checkedId)
        }

        binding.switchTrimPunctuation.setOnCheckedChangeListener { _, checked ->
            if (updatingOutput) return@setOnCheckedChangeListener
            prefs.trimTrailingPunctuation = checked
        }

        binding.switchSaveAudio.setOnCheckedChangeListener { _, checked ->
            if (updatingOutput) return@setOnCheckedChangeListener
            prefs.saveAudio = checked
            updateRecordingPathUi()
        }

        binding.seekPuncThreshold.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingOutput) return
                prefs.trashPuncThreshold = progress
                updatePuncThresholdLabel()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        binding.seekMinRecord.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingOutput) return
                prefs.minRecordMs = AppPrefs.MIN_RECORD_MS_LIMIT + progress
                updateMinRecordLabel()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
    }

    private fun updateMinRecordLabel() {
        binding.tvMinRecordValue.text = getString(
            R.string.min_record_value,
            prefs.minRecordMs / 1000f,
        )
    }

    private fun updatePuncThresholdLabel() {
        binding.tvPuncThreshold.text = getString(
            R.string.punc_threshold_value,
            prefs.trashPuncThreshold,
        )
    }

    private fun updateRecordingPathUi() {
        binding.tvRecordingPath.text = getString(
            R.string.recording_path,
            AudioWavWriter.directory(this).absolutePath,
        )
        binding.tvRecordingPath.alpha = if (prefs.saveAudio) 1f else 0.6f
    }

    private fun languageButtonId(language: String): Int = when (language) {
        "chinese" -> R.id.btnLangChinese
        "english" -> R.id.btnLangEnglish
        "japanese" -> R.id.btnLangJapanese
        else -> R.id.btnLangAuto
    }

    private fun languageFromButton(buttonId: Int): String = when (buttonId) {
        R.id.btnLangChinese -> "chinese"
        R.id.btnLangEnglish -> "english"
        R.id.btnLangJapanese -> "japanese"
        else -> "auto"
    }

    private fun setupOverlaySettings() {
        binding.seekWidth.max = AppPrefs.MAX_WIDTH_DP - AppPrefs.MIN_WIDTH_DP
        binding.seekHeight.max = AppPrefs.MAX_HEIGHT_DP - AppPrefs.MIN_HEIGHT_DP
        updateSizeUi(prefs.buttonWidthDp, prefs.buttonHeightDp)

        binding.seekWidth.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingSize) return
                val width = AppPrefs.MIN_WIDTH_DP + progress
                binding.tvWidthValue.text = "${width}dp"
                if (fromUser) applySize(width, prefs.buttonHeightDp)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        binding.seekHeight.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingSize) return
                val height = AppPrefs.MIN_HEIGHT_DP + progress
                binding.tvHeightValue.text = "${height}dp"
                if (fromUser) applySize(prefs.buttonWidthDp, height)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        updatingMode = true
        binding.modeGroup.check(if (prefs.holdToTalk) R.id.btnModeHold else R.id.btnModeTap)
        updatingMode = false
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingMode || !isChecked) return@addOnButtonCheckedListener
            prefs.holdToTalk = checkedId == R.id.btnModeHold
        }

        updatingPassThrough = true
        binding.switchPassThroughShortTap.isChecked = prefs.passThroughShortTap
        updatingPassThrough = false
        binding.switchPassThroughShortTap.setOnCheckedChangeListener { _, checked ->
            if (updatingPassThrough) return@setOnCheckedChangeListener
            prefs.passThroughShortTap = checked
        }

        updatingPrewarm = true
        binding.switchPrewarmMic.isChecked = prefs.prewarmMic
        updatingPrewarm = false
        binding.switchPrewarmMic.setOnCheckedChangeListener { _, checked ->
            if (updatingPrewarm) return@setOnCheckedChangeListener
            prefs.prewarmMic = checked
            // Ask the overlay to start/stop the warm microphone immediately.
            CapsWriterAccessibilityService.instance?.refreshOverlay()
        }

        binding.btnCalibrate.setOnClickListener {
            if (!prefs.enabled) {
                toast(getString(R.string.calibration_disabled))
                return@setOnClickListener
            }
            if (!CapsWriterAccessibilityService.isEnabledInSettings(this)) {
                toast(getString(R.string.toast_need_accessibility))
                openAccessibilitySettings()
                return@setOnClickListener
            }
            val service = CapsWriterAccessibilityService.instance
            if (service == null) {
                toast(getString(R.string.calibration_service_missing))
                return@setOnClickListener
            }
            // The test input lives on the settings page, so move there before
            // asking the IME to open for calibration.
            showPage(true)
            binding.root.postDelayed({
                showKeyboardForCalibration()
                binding.root.postDelayed({ service.startCalibration() }, 450)
            }, 150)
        }

        binding.btnResetPosition.setOnClickListener {
            prefs.clearProfiles()
            prefs.buttonWidthDp = AppPrefs.DEFAULT_WIDTH_DP
            prefs.buttonHeightDp = AppPrefs.DEFAULT_HEIGHT_DP
            CapsWriterAccessibilityService.instance?.clearProfiles()
            updatingSize = true
            updateSizeUi(prefs.buttonWidthDp, prefs.buttonHeightDp)
            updatingSize = false
            toast(getString(R.string.reset_done))
        }
    }

    private fun applySize(widthDp: Int, heightDp: Int) {
        prefs.buttonWidthDp = widthDp
        prefs.buttonHeightDp = heightDp
        CapsWriterAccessibilityService.instance?.updateOverlaySize(widthDp, heightDp)
            ?: CapsWriterAccessibilityService.instance?.refreshOverlay()
    }

    private fun showKeyboardForCalibration() {
        binding.etTest.requestFocus()
        binding.etTest.postDelayed({
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etTest, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    private fun setupTestArea() {
        binding.tvLastResult.text = prefs.lastResult.ifBlank { getString(R.string.no_result) }
    }

    private fun setupHotwords() {
        binding.etHotwords.setText(prefs.hotwords)
        binding.btnSaveHotwords.setOnClickListener {
            prefs.hotwords = binding.etHotwords.text?.toString().orEmpty().trim()
            binding.etHotwords.clearFocus()
            hideKeyboard()
            updateHotwordStatus()
            toast(binding.tvHotwordStatus.text.toString())
        }
        updateHotwordStatus()
    }

    private fun updateHotwordStatus() {
        val config = HotwordProcessor.parse(prefs.hotwords)
        val wordCount = config.context.split('、').count { it.isNotBlank() }
        binding.tvHotwordStatus.text = if (wordCount == 0 && config.rules.isEmpty()) {
            getString(R.string.hotwords_empty)
        } else {
            getString(R.string.hotwords_saved, wordCount, config.rules.size)
        }
    }

    private fun updateAll() {
        updatingSwitch = true
        binding.switchEnabled.isChecked = prefs.enabled
        updatingSwitch = false
        updatingRecents = true
        binding.switchHideRecents.isChecked = prefs.hideFromRecents
        updatingRecents = false
        updatingSize = true
        updateSizeUi(prefs.buttonWidthDp, prefs.buttonHeightDp)
        updatingSize = false
        updatingMode = true
        binding.modeGroup.check(if (prefs.holdToTalk) R.id.btnModeHold else R.id.btnModeTap)
        updatingMode = false
        updatingPassThrough = true
        binding.switchPassThroughShortTap.isChecked = prefs.passThroughShortTap
        updatingPassThrough = false
        updatingPrewarm = true
        binding.switchPrewarmMic.isChecked = prefs.prewarmMic
        updatingPrewarm = false
        updatingOutput = true
        binding.languageGroup.check(languageButtonId(prefs.language))
        binding.switchTrimPunctuation.isChecked = prefs.trimTrailingPunctuation
        binding.seekPuncThreshold.progress = prefs.trashPuncThreshold
        binding.switchSaveAudio.isChecked = prefs.saveAudio
        binding.seekMinRecord.progress = prefs.minRecordMs - AppPrefs.MIN_RECORD_MS_LIMIT
        updatingOutput = false
        updateMinRecordLabel()
        updatePuncThresholdLabel()
        updateRecordingPathUi()
        if (!binding.etHotwords.hasFocus()) {
            binding.etHotwords.setText(prefs.hotwords)
        }
        updateHotwordStatus()
        updatePermissionUi()
        updateGlobalStatus()
        updateLogs()
    }

    private fun updateSizeUi(widthDp: Int, heightDp: Int) {
        val width = widthDp.coerceIn(AppPrefs.MIN_WIDTH_DP, AppPrefs.MAX_WIDTH_DP)
        val height = heightDp.coerceIn(AppPrefs.MIN_HEIGHT_DP, AppPrefs.MAX_HEIGHT_DP)
        binding.seekWidth.progress = width - AppPrefs.MIN_WIDTH_DP
        binding.tvWidthValue.text = "${width}dp"
        binding.seekHeight.progress = height - AppPrefs.MIN_HEIGHT_DP
        binding.tvHeightValue.text = "${height}dp"
    }

    private fun updatePermissionUi() {
        val mic = hasMicrophonePermission()
        binding.tvMicStatus.text = getString(if (mic) R.string.granted else R.string.not_granted)
        binding.tvMicStatus.setTextColor(color(if (mic) R.color.success else R.color.warning))
        binding.btnMic.isEnabled = !mic

        val notification = hasNotificationPermission()
        binding.tvNotificationStatus.text = getString(if (notification) R.string.granted else R.string.not_granted)
        binding.tvNotificationStatus.setTextColor(color(if (notification) R.color.success else R.color.warning))
        binding.btnNotification.isVisible = !notification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val accessibility = CapsWriterAccessibilityService.isEnabledInSettings(this)
        binding.tvAccessibilityStatus.text = getString(if (accessibility) R.string.enabled else R.string.disabled)
        binding.tvAccessibilityStatus.setTextColor(color(if (accessibility) R.color.success else R.color.warning))

        val power = getSystemService(PowerManager::class.java)
        val ignoring = power?.isIgnoringBatteryOptimizations(packageName) == true
        binding.tvBatteryStatus.text = getString(if (ignoring) R.string.battery_ignored else R.string.battery_optimized)
        binding.tvBatteryStatus.setTextColor(color(if (ignoring) R.color.success else R.color.on_surface_variant))
        binding.btnBattery.isEnabled = !ignoring

        updateGlobalStatus()
    }

    private fun updateGlobalStatus() {
        if (!::prefs.isInitialized) return
        val running = VoiceForegroundService.isRunning
        val state = if (running) AppStatus.state else VoiceState.IDLE
        val text = when {
            !prefs.enabled -> getString(R.string.state_service_off)
            !running -> getString(R.string.state_service_not_running)
            state == VoiceState.CONNECTING -> getString(R.string.state_connecting)
            state == VoiceState.RECORDING -> getString(R.string.state_recording)
            state == VoiceState.PROCESSING -> getString(R.string.state_processing)
            state == VoiceState.ERROR -> AppStatus.message.ifBlank { getString(R.string.state_error) }
            else -> getString(R.string.state_idle)
        }
        binding.tvGlobalState.text = text
        val dotColor = when {
            !prefs.enabled -> R.color.on_surface_variant
            !running -> R.color.warning
            state == VoiceState.ERROR -> R.color.danger
            state == VoiceState.RECORDING || state == VoiceState.PROCESSING -> R.color.primary
            else -> R.color.success
        }
        binding.tvGlobalDot.setTextColor(color(dotColor))

        val last = prefs.lastResult.ifBlank { AppStatus.lastResult }
        binding.tvLastResult.text = last.ifBlank { getString(R.string.no_result) }
    }

    private fun updateLogs() {
        val lines = AppLog.snapshot()
        binding.tvLogs.text = if (lines.isEmpty()) {
            getString(R.string.logs_empty)
        } else {
            lines.takeLast(12).joinToString("\n")
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun setSwitchWithoutCallback(checked: Boolean) {
        updatingSwitch = true
        binding.switchEnabled.isChecked = checked
        updatingSwitch = false
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etServer.windowToken, 0)
    }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REQ_MIC = 100
        const val REQ_NOTIFICATION = 101
    }
}
