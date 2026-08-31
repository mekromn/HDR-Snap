package com.mekromn.hdrsnap.capture

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class HdrCaptureAccessibilityService : AccessibilityService() {
    private lateinit var monitor: ScreenshotMediaObserver
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        HdrSnapBridge.connect(this)
        monitor = ScreenshotMediaObserver(this)
        monitor.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (::monitor.isInitialized) monitor.stop()
        HdrSnapBridge.disconnect(this)
        super.onDestroy()
    }

    fun requestSystemScreenshot(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun requestSystemScreenshotDelayed(delayMs: Long): Boolean {
        if (delayMs <= 0L) return requestSystemScreenshot()
        mainHandler.postDelayed(
            { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) },
            delayMs
        )
        return true
    }

    fun processLatestScreenshot(): Boolean {
        if (!::monitor.isInitialized) return false
        monitor.processLatest()
        return true
    }
}
