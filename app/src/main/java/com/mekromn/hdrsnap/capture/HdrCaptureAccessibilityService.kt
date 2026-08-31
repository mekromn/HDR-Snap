package com.mekromn.hdrsnap.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class HdrCaptureAccessibilityService : AccessibilityService() {
    private lateinit var monitor: ScreenshotMediaObserver

    override fun onServiceConnected() {
        super.onServiceConnected()
        HdrSnapBridge.connect(this)
        monitor = ScreenshotMediaObserver(this)
        monitor.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::monitor.isInitialized) monitor.stop()
        HdrSnapBridge.disconnect(this)
        super.onDestroy()
    }

    fun requestSystemScreenshot(): Boolean {
        // This deliberately invokes Android's own screenshot action so Android 16's
        // SurfaceFlinger HDR screenshot/gainmap pipeline remains responsible for capture.
        return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun processLatestScreenshot(): Boolean {
        if (!::monitor.isInitialized) return false
        monitor.processLatest()
        return true
    }
}
