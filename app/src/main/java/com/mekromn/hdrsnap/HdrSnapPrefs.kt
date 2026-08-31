package com.mekromn.hdrsnap

import android.content.Context

class HdrSnapPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hdr_snap", Context.MODE_PRIVATE)

    var autoProcessScreenshots: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PROCESS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PROCESS, value).apply()

    var convertTrueHdrToJpegR: Boolean
        get() = prefs.getBoolean(KEY_TRUE_HDR, true)
        set(value) = prefs.edit().putBoolean(KEY_TRUE_HDR, value).apply()

    var sdrUpconversionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SDR_UPCONVERT, true)
        set(value) = prefs.edit().putBoolean(KEY_SDR_UPCONVERT, value).apply()

    var deleteOriginalAfterVerify: Boolean
        get() = prefs.getBoolean(KEY_DELETE_ORIGINAL, true)
        set(value) = prefs.edit().putBoolean(KEY_DELETE_ORIGINAL, value).apply()

    var lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "No screenshot processed yet")
            ?: "No screenshot processed yet"
        set(value) = prefs.edit().putString(KEY_LAST_STATUS, value).apply()

    companion object {
        private const val KEY_AUTO_PROCESS = "auto_process_screenshots"
        private const val KEY_TRUE_HDR = "convert_true_hdr"
        private const val KEY_SDR_UPCONVERT = "sdr_upconvert"
        private const val KEY_DELETE_ORIGINAL = "delete_original_after_verify"
        private const val KEY_LAST_STATUS = "last_status"
    }
}
