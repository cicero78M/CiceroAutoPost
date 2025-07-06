package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        private const val ACCESS_DELAY_MS = 3000L
        private const val COMMENT_READY_TEXT = "Tambahkan komentar…"
    }

    private fun sendLog(message: String) {
        val intent = Intent(MainActivity.ACTION_ACCESSIBILITY_LOG).apply {
            putExtra(MainActivity.EXTRA_LOG_MESSAGE, message)
        }
        sendBroadcast(intent)
        Log.d(TAG, message)
    }

    private fun sendResult(success: Boolean, error: String? = null) {
        val intent = Intent(MainActivity.ACTION_COMMENT_RESULT).apply {
            putExtra(MainActivity.EXTRA_COMMENT_SUCCESS, success)
            putExtra(MainActivity.EXTRA_COMMENT_ERROR, error)
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

    private fun printAllNodes(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (!BuildConfig.DEBUG) return
        if (node == null) return
        val prefix = " ".repeat(depth * 2)
        Log.d("NodeTree", "$prefix ${'$'}{node.className} text=${'$'}{node.text} id=${'$'}{node.viewIdResourceName}")
        for (i in 0 until node.childCount) {
            printAllNodes(node.getChild(i), depth + 1)
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

    private fun findByDesc(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(text, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findByDesc(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    private fun waitForRoot(maxAttempts: Int = 20): AccessibilityNodeInfo? {
        var attempts = 0
        var root: AccessibilityNodeInfo? = null
        while (attempts < maxAttempts && root == null) {
            root = rootInActiveWindow
            if (root == null) {
                Log.d(TAG, "Root window is null, polling...")
                Thread.sleep(ACCESS_DELAY_MS)
                attempts++
            }
        }
        return root
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
        if (event?.packageName != "com.instagram.android") return
        if (currentComment == null) return

        Handler(Looper.getMainLooper()).postDelayed({
            var root = waitForRoot()
            if (root == null) {
                Log.e(TAG, "Root window masih null setelah polling.")
                return@postDelayed
            }
            val captionNodes = root.findAccessibilityNodeInfosByText(COMMENT_READY_TEXT)
            if (captionNodes.isNullOrEmpty()) {
                Log.d(TAG, "Komentar belum siap")
                return@postDelayed
            }
            fillComment()
        }, 2000)
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
            Thread.sleep(ACCESS_DELAY_MS)
            var root = waitForRoot(10)
            if (BuildConfig.DEBUG) logTree(root)
            val rootNode = root ?: run {
                val msg = "Instagram UI not ready"
                sendLog(msg)
                sendResult(false, msg)
                performGlobalAction(GLOBAL_ACTION_BACK)
                return@Thread
            }

            // detect and open comment composer using the non-null rootNode
            val commentBtn =
                findByDesc(rootNode, "comment") ?: rootNode.findAccessibilityNodeInfosByText("Comment").firstOrNull()
            commentBtn?.let {
                sendLog("Opening comment composer")
                it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(ACCESS_DELAY_MS)
                root = rootInActiveWindow
                if (BuildConfig.DEBUG) logTree(root)
            }

            var input = root?.findAccessibilityNodeInfosByViewId(
                "com.instagram.android:id/layout_comment_thread_edittext"
            )?.firstOrNull() ?: findFirstEditText(root)
            if (input == null) {
                sendLog("Comment input not found, retrying")
                Thread.sleep(ACCESS_DELAY_MS)
                root = rootInActiveWindow
                if (BuildConfig.DEBUG) logTree(root)
                input = root?.findAccessibilityNodeInfosByViewId(
                    "com.instagram.android:id/layout_comment_thread_edittext"
                )?.firstOrNull() ?: findFirstEditText(root)
            }
            if (input == null) {
                val msg = "Comment input not found"
                sendLog(msg)
                sendResult(false, msg)
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
            val postBtn = root?.findAccessibilityNodeInfosByText("Post")?.firstOrNull()
                ?: findByDesc(root, "Post")
            postBtn?.let {
                sendLog("Pressing Post button")
                it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            sendLog("Comment workflow complete")
            sendResult(true)
        }.start()
    }
}
