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
import java.util.ArrayDeque

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
        val packageName = root.packageName?.toString()
        var input: AccessibilityNodeInfo? = null
        if (packageName == "com.instagram.android") {
            input = root.findAccessibilityNodeInfosByViewId(
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
        } else {
            // Running inside Chrome. Try to locate the comment box by heuristics.
            input = findChromeInput(root)
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
        findNodeByText(root, "Post")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        currentComment = null
    }

    private fun findChromeInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val className = node.className?.toString()
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (className == "android.widget.EditText" &&
                (text.contains("comment", true) || desc.contains("comment", true))) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString() ?: ""
            if (text.equals(target, true)) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
