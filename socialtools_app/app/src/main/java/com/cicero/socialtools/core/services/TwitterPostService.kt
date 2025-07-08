package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TwitterPostService : AccessibilityService() {
    private var hasClicked = false
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { performClick() }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            packageNames = arrayOf("com.twitter.android")
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            hasClicked = false
            handler.postDelayed(clickRunnable, 500)
        } else if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handler.postDelayed(clickRunnable, 200)
        }
    }

    private fun performClick() {
        if (hasClicked) return
        val root = rootInActiveWindow ?: return
        val keywords = listOf("Tweet", "Post")
        var node: AccessibilityNodeInfo? = null
        for (k in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(k)
            node = nodes.firstOrNull { it.isClickable }
            if (node != null) break
        }
        if (node != null) {
            hasClicked = true
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() {}
}
