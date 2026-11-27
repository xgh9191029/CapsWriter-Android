package com.capswriter.offline.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.capswriter.offline.R
import com.capswriter.offline.audio.VoiceSession
import com.capswriter.offline.ui.MainActivity
import com.capswriter.offline.util.AppLog
import com.capswriter.offline.util.AppPrefs
import com.capswriter.offline.util.AppStatus
import com.capswriter.offline.util.HotwordProcessor
import com.capswriter.offline.util.OutputTextProcessor
import com.capswriter.offline.util.ServerAddress
import com.capswriter.offline.util.VoiceState

/**
 * Long-lived foreground service. It is started from MainActivity while the app is
 * visible, which satisfies Android 14's while-in-use rule for microphone services.
 * The accessibility overlay talks to it through the in-process singleton.
 */
class VoiceForegroundService : Service() {

    companion object {
        @Volatile
        var instance: VoiceForegroundService? = null
            private set

        /** Set by the accessibility service: insert text into the focused input. */
        @Volatile
        var onResult: ((text: String, expectedPackage: String?) -> Unit)? = null

        /** Set by the accessibility service: package that owned the input focus. */
        @Volatile
        var targetWindowProvider: (() -> String?)? = null

        val isRunning: Boolean get() = instance != null

        const val ACTION_CANCEL = "com.capswriter.offline.action.CANCEL_RECORDING"

        private const val CHANNEL_ID = "caps_writer_voice"
        private const val NOTIFICATION_ID = 2106

        fun start(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceForegroundService::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPrefs
    private var notificationManager: NotificationManager? = null
    private var session: VoiceSession? = null
    private var targetPackage: String? = null
    private var notificationStarted = false

    @Volatile
    private var destroyed = false

    @Volatile
    var state: VoiceState = VoiceState.IDLE
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = AppPrefs(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        val notification = buildNotification(getString(R.string.notification_idle))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                // Foreground-service types were extended with microphone in API 30.
                startForeground(NOTIFICATION_ID, notification)
            }
            notificationStarted = true
        } catch (error: Exception) {
            AppLog.add(this, "Cannot start foreground service: ${error.javaClass.simpleName} ${error.message.orEmpty()}")
            stopSelf()
            return
        }

        AppStatus.update(
            this,
            state = VoiceState.IDLE,
            message = getString(R.string.state_idle),
            lastResult = prefs.lastResult,
            serviceRunning = true,
        )
        AppLog.add(this, "Voice foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        val active = session
        session = null
        active?.cancel(getString(R.string.reason_service_stopped))
        instance = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        notificationManager?.cancel(NOTIFICATION_ID)
        AppStatus.update(
            this,
            state = VoiceState.IDLE,
            message = getString(R.string.state_service_stopped),
            lastResult = prefs.lastResult,
            serviceRunning = false,
        )
        AppLog.add(this, "Voice foreground service stopped")
        super.onDestroy()
    }

    fun startRecording(): Boolean {
        if (session != null) return false
        if (!hasMicrophonePermission()) {
            toast(getString(R.string.toast_need_mic))
            return false
        }
        val serverUrl = runCatching { ServerAddress.normalize(prefs.serverUrl) }.getOrNull()
        if (serverUrl.isNullOrBlank()) {
            toast(getString(R.string.toast_need_server))
            openMainActivity()
            return false
        }

        targetPackage = runCatching { targetWindowProvider?.invoke() }.getOrNull()
        val hotwordConfig = HotwordProcessor.parse(prefs.hotwords)
        state = VoiceState.CONNECTING
        AppStatus.update(
            this,
            state = VoiceState.CONNECTING,
            message = getString(R.string.state_connecting),
            lastResult = prefs.lastResult,
            serviceRunning = true,
        )
        updateNotification(getString(R.string.notification_recording))

        val newSession = VoiceSession(
            context = this,
            serverUrl = serverUrl,
            hotwordContext = hotwordConfig.context,
            language = prefs.language,
            saveAudio = prefs.saveAudio,
            minRecordMs = prefs.minRecordMs,
            callback = sessionCallback,
        )
        session = newSession
        newSession.start()
        return true
    }

    fun stopRecording() {
        session?.stop()
    }

    fun cancelRecording(reason: String? = null) {
        val active = session
        session = null
        active?.cancel(reason ?: getString(R.string.state_cancelled))
        if (active == null) {
            state = VoiceState.IDLE
            updateNotification(getString(R.string.notification_idle))
        }
    }

    private val sessionCallback = object : VoiceSession.Callback {
        override fun onState(state: VoiceState, message: String) {
            if (destroyed) return
            this@VoiceForegroundService.state = state
            AppStatus.update(
                this@VoiceForegroundService,
                state = state,
                message = message,
                lastResult = prefs.lastResult,
                serviceRunning = true,
            )
            when (state) {
                VoiceState.CONNECTING -> updateNotification(getString(R.string.notification_recording))
                VoiceState.RECORDING -> updateNotification(getString(R.string.notification_recording))
                VoiceState.PROCESSING -> updateNotification(getString(R.string.notification_processing))
                else -> Unit
            }
        }

        override fun onResult(text: String) {
            if (destroyed) return
            var processed = HotwordProcessor.apply(text, prefs.hotwords)
            if (prefs.trimTrailingPunctuation) {
                val activePackage = runCatching { targetWindowProvider?.invoke() }.getOrNull()
                val forceForWeChat = activePackage == "com.tencent.mm" ||
                    targetPackage == "com.tencent.mm"
                processed = OutputTextProcessor.trimTrailingPunctuation(
                    processed,
                    threshold = prefs.trashPuncThreshold,
                    force = forceForWeChat,
                )
            }
            processed = processed.ifBlank { text }
            if (processed != text) {
                AppLog.add(this@VoiceForegroundService, "Output post-processing ${text.length} -> ${processed.length} chars")
            }
            prefs.lastResult = processed
            AppStatus.update(
                this@VoiceForegroundService,
                state = VoiceState.PROCESSING,
                message = getString(R.string.state_recognition_complete),
                lastResult = processed,
                serviceRunning = true,
                notify = true,
            )

            val handler = onResult
            if (handler != null) {
                runCatching { handler(processed, targetPackage) }
                    .onFailure { copyToClipboard(processed) }
            } else {
                copyToClipboard(processed)
            }
        }

        override fun onError(message: String) {
            if (destroyed) return
            AppLog.add(this@VoiceForegroundService, message)
            toast(message)
            this@VoiceForegroundService.state = VoiceState.ERROR
            AppStatus.update(
                this@VoiceForegroundService,
                state = VoiceState.ERROR,
                message = message,
                lastResult = prefs.lastResult,
                serviceRunning = true,
            )
            updateNotification(getString(R.string.notification_idle))
        }

        override fun onCancelled(message: String) {
            if (destroyed) return
            toast(message)
        }

        override fun onFinished() {
            if (destroyed) return
            session = null
            targetPackage = null
            if (this@VoiceForegroundService.state != VoiceState.ERROR) {
                this@VoiceForegroundService.state = VoiceState.IDLE
                AppStatus.update(
                    this@VoiceForegroundService,
                    state = VoiceState.IDLE,
                    message = getString(R.string.state_idle),
                    lastResult = prefs.lastResult,
                    serviceRunning = true,
                )
            } else {
                main.postDelayed({
                    if (!destroyed && session == null &&
                        this@VoiceForegroundService.state == VoiceState.ERROR
                    ) {
                        this@VoiceForegroundService.state = VoiceState.IDLE
                        AppStatus.update(
                            this@VoiceForegroundService,
                            state = VoiceState.IDLE,
                            message = getString(R.string.state_idle),
                            lastResult = prefs.lastResult,
                            serviceRunning = true,
                        )
                    }
                }, 3_000)
            }
            updateNotification(getString(R.string.notification_idle))
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("CapsWriter", text))
        }
        toast(getString(R.string.toast_copied))
    }

    private fun toast(message: String) {
        main.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        runCatching { startActivity(intent) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, VoiceForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state != VoiceState.IDLE) {
            builder.addAction(
                0,
                getString(R.string.notification_action_cancel),
                cancelIntent,
            )
        }
        return builder.build()
    }

    private fun updateNotification(text: String) {
        if (!notificationStarted) return
        runCatching {
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }
}
