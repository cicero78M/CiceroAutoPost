package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cicero.socialtools.ui.MainActivity

/** Accessibility service that fills the comment field in Instagram and presses Post. */
class InstagramCommentService : AccessibilityService() {
    private var currentComment: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_INPUT_COMMENT) {
                currentComment = intent.getStringExtra(MainActivity.EXTRA_COMMENT)
                fillComment()
            }
        }
    }

    override fun onServiceConnected() {
        registerReceiver(receiver, IntentFilter(MainActivity.ACTION_INPUT_COMMENT))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun fillComment() {
        val text = currentComment ?: return
        var root = rootInActiveWindow ?: return
        var input = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/layout_comment_thread_edittext"
        ).firstOrNull()

        if (input == null) {
            // Try opening the comment composer first
            root.findAccessibilityNodeInfosByText("Comment")
                .firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Allow the UI to update
            Thread.sleep(500)
            root = rootInActiveWindow ?: return
            input = root.findAccessibilityNodeInfosByViewId(
                "com.instagram.android:id/layout_comment_thread_edittext"
            ).firstOrNull()
        }

        if (input == null) return

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        root.findAccessibilityNodeInfosByText("Post")
            .firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        currentComment = null
    }
}
