package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TwitterPostService : AccessibilityService() {
    private var hasClicked = false

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
        }
        if (hasClicked) return
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText("Tweet")
        val node = nodes.firstOrNull { it.isClickable }
        if (node != null) {
            hasClicked = true
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() {}
}
