package com.capswriter.offline.util

import android.content.Context

/**
 * A calibrated position and touch-target size for one input method / orientation.
 * Ratios are relative to the IME window so the same profile survives keyboard height
 * changes and different screen sizes.
 */
data class OverlayProfile(
    val xRatio: Float,
    val yRatio: Float,
    val widthDp: Int,
    val heightDp: Int,
)

class AppPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("caps_writer_settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Hide the task from the recent-tasks list (the manifest default is still true). */
    var hideFromRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RECENTS, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_RECENTS, value).apply()

    /**
     * In hold-to-talk mode, a tap shorter than the hold threshold is forwarded
     * to the IME instead of being recorded, so the keyboard's own voice key still
     * works through the transparent overlay.
     */
    var passThroughShortTap: Boolean
        get() = prefs.getBoolean(KEY_PASS_THROUGH_SHORT_TAP, true)
        set(value) = prefs.edit().putBoolean(KEY_PASS_THROUGH_SHORT_TAP, value).apply()

    var holdToTalk: Boolean
        get() = prefs.getBoolean(KEY_HOLD, true)
        set(value) = prefs.edit().putBoolean(KEY_HOLD, value).apply()

    /** CapsWriter server language hint: auto / chinese / english / japanese. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "auto")
            ?.takeIf { it in LANGUAGES }
            ?: "auto"
        set(value) = prefs.edit()
            .putString(KEY_LANGUAGE, value.takeIf { it in LANGUAGES } ?: "auto")
            .apply()

    /** Mirrors config_client.py's trash_punc behavior for mobile dictation. */
    var trimTrailingPunctuation: Boolean
        get() = prefs.getBoolean(KEY_TRIM_PUNCT, true)
        set(value) = prefs.edit().putBoolean(KEY_TRIM_PUNCT, value).apply()

    /** Mirrors config_client.py's trash_punc_thresh. 0 means always trim. */
    var trashPuncThreshold: Int
        get() = prefs.getInt(KEY_PUNC_THRESHOLD, DEFAULT_PUNC_THRESHOLD)
            .coerceIn(MIN_PUNC_THRESHOLD, MAX_PUNC_THRESHOLD)
        set(value) = prefs.edit()
            .putInt(KEY_PUNC_THRESHOLD, value.coerceIn(MIN_PUNC_THRESHOLD, MAX_PUNC_THRESHOLD))
            .apply()

    /** Save each dictation as a 16 kHz mono WAV under the app's Music directory. */
    var saveAudio: Boolean
        get() = prefs.getBoolean(KEY_SAVE_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_AUDIO, value).apply()

    /** Mirrors config_client.py's threshold (seconds). */
    var minRecordMs: Int
        get() = prefs.getInt(KEY_MIN_RECORD_MS, DEFAULT_MIN_RECORD_MS)
            .coerceIn(MIN_RECORD_MS_LIMIT, MAX_RECORD_MS_LIMIT)
        set(value) = prefs.edit()
            .putInt(KEY_MIN_RECORD_MS, value.coerceIn(MIN_RECORD_MS_LIMIT, MAX_RECORD_MS_LIMIT))
            .apply()

    /** Default width of the transparent touch target, in dp. */
    var buttonWidthDp: Int
        get() = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
        set(value) = prefs.edit()
            .putInt(KEY_WIDTH_DP, value.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP))
            .apply()

    /** Default height. Independent from width so square/circular IME keys fit too. */
    var buttonHeightDp: Int
        get() = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        set(value) = prefs.edit()
            .putInt(KEY_HEIGHT_DP, value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))
            .apply()

    /** Client-side hotword text. Parsed by HotwordProcessor. */
    var hotwords: String
        get() = prefs.getString(KEY_HOTWORDS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HOTWORDS, value).apply()

    /**
     * Legacy single-position setting, kept so installations upgraded from 1.0.0 keep
     * their old calibration as the "default" profile.
     */
    private val legacyPositionX: Float
        get() = prefs.getFloat(KEY_X_RATIO, -1f)

    private val legacyPositionY: Float
        get() = prefs.getFloat(KEY_Y_RATIO, -1f)

    fun profile(key: String): OverlayProfile? {
        val prefix = profilePrefix(key)
        val x = prefs.getFloat(prefix + "x", -1f)
        val y = prefs.getFloat(prefix + "y", -1f)
        if (x >= 0f && y >= 0f) {
            return OverlayProfile(
                xRatio = x,
                yRatio = y,
                widthDp = prefs.getInt(prefix + "w", buttonWidthDp)
                    .coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP),
                heightDp = prefs.getInt(prefix + "h", buttonHeightDp)
                    .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
            )
        }

        if (legacyPositionX >= 0f && legacyPositionY >= 0f) {
            return OverlayProfile(
                xRatio = legacyPositionX,
                yRatio = legacyPositionY,
                widthDp = buttonWidthDp,
                heightDp = buttonHeightDp,
            )
        }
        return null
    }

    fun saveProfile(key: String, profile: OverlayProfile) {
        val prefix = profilePrefix(key)
        prefs.edit()
            .putFloat(prefix + "x", profile.xRatio.coerceIn(0f, 1f))
            .putFloat(prefix + "y", profile.yRatio.coerceIn(0f, 1f))
            .putInt(prefix + "w", profile.widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP))
            .putInt(prefix + "h", profile.heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))
            .apply()
    }

    fun hasProfile(key: String): Boolean = profile(key) != null

    fun clearProfiles() {
        val editor = prefs.edit()
            .remove(KEY_X_RATIO)
            .remove(KEY_Y_RATIO)
        for (key in prefs.all.keys) {
            if (key.startsWith(PROFILE_PREFIX)) editor.remove(key)
        }
        editor.apply()
    }

    private fun profilePrefix(key: String): String =
        PROFILE_PREFIX + key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + "_"

    var lastResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    fun clearPosition() = clearProfiles()

    companion object {
        const val MIN_WIDTH_DP = 32
        const val MAX_WIDTH_DP = 200
        const val DEFAULT_WIDTH_DP = 72

        const val MIN_HEIGHT_DP = 24
        const val MAX_HEIGHT_DP = 160
        const val DEFAULT_HEIGHT_DP = 56

        const val MIN_RECORD_MS_LIMIT = 200
        const val MAX_RECORD_MS_LIMIT = 1_500
        const val DEFAULT_MIN_RECORD_MS = 300

        val LANGUAGES = setOf("auto", "chinese", "english", "japanese")

        const val MIN_PUNC_THRESHOLD = 0
        const val MAX_PUNC_THRESHOLD = 50
        const val DEFAULT_PUNC_THRESHOLD = 8

        const val DEFAULT_PROFILE_KEY = "default"

        private const val PROFILE_PREFIX = "overlay_profile_"
        private const val KEY_SERVER = "server_url"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HIDE_RECENTS = "hide_from_recents"
        private const val KEY_PASS_THROUGH_SHORT_TAP = "pass_through_short_tap"
        private const val KEY_HOLD = "hold_to_talk"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_TRIM_PUNCT = "trim_trailing_punctuation"
        private const val KEY_PUNC_THRESHOLD = "trash_punc_threshold"
        private const val KEY_SAVE_AUDIO = "save_audio"
        private const val KEY_MIN_RECORD_MS = "min_record_ms"
        private const val KEY_WIDTH_DP = "button_width_dp"
        private const val KEY_HEIGHT_DP = "button_height_dp"
        private const val KEY_HOTWORDS = "client_hotwords"
        private const val KEY_X_RATIO = "position_x_ratio"
        private const val KEY_Y_RATIO = "position_y_ratio"
        private const val KEY_LAST_RESULT = "last_result"
    }
}
