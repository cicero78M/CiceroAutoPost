package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import com.cicero.socialtools.BuildConfig
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cicero.socialtools.ui.MainActivity

/** Accessibility service that fills the comment field in Instagram and presses Post. */
class InstagramCommentService : AccessibilityService() {
    private var currentComment: String? = null

    companion object {
        private const val TAG = "InstagramCommentService"
    }

    private fun sendLog(message: String) {
        val intent = Intent(MainActivity.ACTION_ACCESSIBILITY_LOG).apply {
            putExtra(MainActivity.EXTRA_LOG_MESSAGE, message)
        }
        sendBroadcast(intent)
        Log.d(TAG, message)
    }

    private fun sendResult(success: Boolean) {
        val intent = Intent(MainActivity.ACTION_COMMENT_RESULT).apply {
            putExtra(MainActivity.EXTRA_COMMENT_SUCCESS, success)
        }
        sendBroadcast(intent)
    }

    private fun logTree(node: AccessibilityNodeInfo?, indent: String = "") {
        if (!BuildConfig.DEBUG) return
        if (node == null) {
            Log.d(TAG, indent + "null")
            return
        }
        val info = "class=${node.className} text=${node.text} id=${node.viewIdResourceName}"
        Log.d(TAG, indent + info)
        for (i in 0 until node.childCount) {
            logTree(node.getChild(i), indent + "  ")
        }
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditText(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

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
        sendLog("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        unregisterReceiver(receiver)
        sendLog("Accessibility service destroyed")
        super.onDestroy()
    }

    private fun fillComment() {
        val text = currentComment ?: return
        sendLog("Received comment: ${'$'}{text.take(30)}")
        currentComment = null

        Thread {
            sendLog("Starting comment workflow")
            // wait to ensure post is fully opened
            Thread.sleep(2500)
            var root = rootInActiveWindow
            if (BuildConfig.DEBUG) logTree(root)
            if (root == null) {
                sendLog("Root window is null")
                sendResult(false)
                return@Thread
            }

            // detect and open comment composer
            root.findAccessibilityNodeInfosByText("Comment")
                .firstOrNull()?.let {
                    sendLog("Opening comment composer")
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Thread.sleep(500)
                    root = rootInActiveWindow
                    if (BuildConfig.DEBUG) logTree(root)
                }

            var input = findFirstEditText(root)
            if (input == null) {
                sendLog("Comment input not found, retrying")
                Thread.sleep(500)
                root = rootInActiveWindow
                if (BuildConfig.DEBUG) logTree(root)
                input = findFirstEditText(root)
            }
            if (input == null) {
                sendLog("Comment input not found")
                sendResult(false)
                return@Thread
            }

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            input?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            input?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            sendLog("Inserted comment text")
            root?.findAccessibilityNodeInfosByText("Post")
                ?.firstOrNull()?.let {
                    sendLog("Pressing Post button")
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            sendLog("Comment workflow complete")
            sendResult(true)
        }.start()
    }
}
