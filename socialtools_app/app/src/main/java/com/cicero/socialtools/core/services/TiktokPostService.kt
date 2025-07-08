package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TiktokPostService : AccessibilityService() {

    companion object {
        const val ACTION_UPLOAD_FINISHED = "com.cicero.socialtools.TIKTOK_UPLOAD_FINISHED"
    }

    private var hasClicked = false
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { performClick() }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            packageNames = arrayOf("com.zhiliaoapp.musically")
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                hasClicked = false
                handler.postDelayed(clickRunnable, 500)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handler.postDelayed(clickRunnable, 200)
            }
        }
    }

    private fun performClick() {
        if (hasClicked) return
        val root = rootInActiveWindow ?: return
        val keywords = listOf("Post", "Posting")
        var node: AccessibilityNodeInfo? = null
        for (k in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(k)
            node = nodes.firstOrNull { it.isClickable }
            if (node != null) break
        }
        if (node != null) {
            hasClicked = true
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendBroadcast(Intent(ACTION_UPLOAD_FINISHED))
            stopSelf()
        }
    }

    override fun onInterrupt() {}
}

