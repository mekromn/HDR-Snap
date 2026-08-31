package com.mekromn.hdrsnap.capture

object HdrSnapBridge {
    @Volatile
    private var service: HdrCaptureAccessibilityService? = null

    val isConnected: Boolean
        get() = service != null

    internal fun connect(value: HdrCaptureAccessibilityService) {
        service = value
    }

    internal fun disconnect(value: HdrCaptureAccessibilityService) {
        if (service === value) service = null
    }

    fun requestSystemScreenshot(): Boolean {
        return service?.requestSystemScreenshot() == true
    }

    fun processLatestScreenshot(): Boolean {
        return service?.processLatestScreenshot() == true
    }
}
