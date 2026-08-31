package com.mekromn.hdrsnap

import android.content.Context

class HdrSnapPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hdr_snap", Context.MODE_PRIVATE)

    var convertTrueHdrToJpegR: Boolean
        get() = prefs.getBoolean(KEY_TRUE_HDR, true)
        set(value) = prefs.edit().putBoolean(KEY_TRUE_HDR, value).apply()

    var sdrUpconversionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SDR_UPCONVERT, false)
        set(value) = prefs.edit().putBoolean(KEY_SDR_UPCONVERT, value).apply()

    var keepNativeHdrPng: Boolean
        get() = prefs.getBoolean(KEY_KEEP_NATIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_NATIVE, value).apply()

    var lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "No screenshot processed yet") ?: "No screenshot processed yet"
        set(value) = prefs.edit().putString(KEY_LAST_STATUS, value).apply()

    companion object {
        private const val KEY_TRUE_HDR = "convert_true_hdr"
        private const val KEY_SDR_UPCONVERT = "sdr_upconvert"
        private const val KEY_KEEP_NATIVE = "keep_native_hdr_png"
        private const val KEY_LAST_STATUS = "last_status"
    }
}
