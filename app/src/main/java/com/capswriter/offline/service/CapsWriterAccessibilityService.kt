package com.capswriter.offline.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.capswriter.offline.R
import com.capswriter.offline.util.AppLog
import com.capswriter.offline.util.AppPrefs
import com.capswriter.offline.util.AppStatus
import com.capswriter.offline.util.VoiceState

class CapsWriterAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CapsWriterAccessibilityService? = null
            private set

        fun isEnabledInSettings(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == context.packageName &&
                        serviceInfo.name == CapsWriterAccessibilityService::class.java.name
                }
        }
    }

    private lateinit var prefs: AppPrefs
    private var overlay: OverlayController? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = AppPrefs(this)
        overlay = OverlayController(this, prefs).also { it.start() }

        VoiceForegroundService.onResult = { text, expectedPackage ->
            onRecognizedText(text, expectedPackage)
        }
        VoiceForegroundService.targetWindowProvider = {
            runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        }

        registerScreenReceiver()
        AppStatus.update(
            this,
            state = AppStatus.state,
            message = AppStatus.message.ifBlank { getString(R.string.state_accessibility_connected) },
            lastResult = prefs.lastResult,
            serviceRunning = VoiceForegroundService.isRunning,
        )
        AppLog.add(this, "Accessibility service connected; watching the IME")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            -> overlay?.onWindowEvent()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        overlay?.destroy()
        overlay = null
        VoiceForegroundService.instance?.cancelRecording(getString(R.string.reason_accessibility_disconnected))
        VoiceForegroundService.onResult = null
        VoiceForegroundService.targetWindowProvider = null
        unregisterScreenReceiver()
        AppStatus.update(
            this,
            state = VoiceState.IDLE,
            message = getString(R.string.state_accessibility_disconnected),
            lastResult = if (::prefs.isInitialized) prefs.lastResult else "",
            serviceRunning = VoiceForegroundService.isRunning,
        )
        AppLog.add(this, "Accessibility service disconnected")
        super.onDestroy()
    }

    fun startCalibration() {
        overlay?.startCalibration()
    }

    fun cancelCalibration() {
        overlay?.cancelCalibration()
    }

    fun refreshOverlay() {
        overlay?.refresh()
    }

    fun updateOverlaySize(widthDp: Int, heightDp: Int) {
        overlay?.updateOverlaySize(widthDp, heightDp)
    }

    fun clearProfiles() {
        overlay?.clearAllProfiles()
    }

    private fun onRecognizedText(text: String, expectedPackage: String?) {
        prefs.lastResult = text
        AppStatus.update(
            this,
            state = AppStatus.state,
            message = getString(R.string.state_recognition_complete),
            lastResult = text,
            serviceRunning = VoiceForegroundService.isRunning,
        )
        TextInserter.insert(this, text, expectedPackage)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        VoiceForegroundService.instance?.cancelRecording(getString(R.string.reason_screen_off))
                        overlay?.cancelCalibration()
                        overlay?.refresh()
                    }

                    Intent.ACTION_SCREEN_ON -> overlay?.refresh()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
    }
}
