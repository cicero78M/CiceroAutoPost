package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cicero.socialtools.BuildConfig
import com.cicero.socialtools.ui.MainActivity

/** Accessibility service that presses the like button on Instagram posts. */
class InstagramLikeService : AccessibilityService() {
    private var pendingLike: Boolean = false

    companion object {
        private const val TAG = "InstagramLikeService"
        private const val ACCESS_DELAY_MS = 3000L
    }

    private fun sendLog(message: String) {
        val intent = Intent(MainActivity.ACTION_ACCESSIBILITY_LOG).apply {
            putExtra(MainActivity.EXTRA_LOG_MESSAGE, message)
        }
        sendBroadcast(intent)
        Log.d(TAG, message)
    }

    private fun sendResult(success: Boolean, error: String? = null) {
        val intent = Intent(MainActivity.ACTION_LIKE_RESULT).apply {
            putExtra(MainActivity.EXTRA_LIKE_SUCCESS, success)
            putExtra(MainActivity.EXTRA_LIKE_ERROR, error)
        }
        sendBroadcast(intent)
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
            if (intent?.action == MainActivity.ACTION_INPUT_LIKE) {
                pendingLike = true
                performLike()
            }
        }
    }

    override fun onServiceConnected() {
        registerReceiver(receiver, IntentFilter(MainActivity.ACTION_INPUT_LIKE))
        sendLog("Like service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.instagram.android") return
        if (pendingLike) {
            Handler(Looper.getMainLooper()).postDelayed({ performLike() }, 2000)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        unregisterReceiver(receiver)
        sendLog("Like service destroyed")
        super.onDestroy()
    }

    private fun performLike() {
        if (!pendingLike) return
        pendingLike = false
        Thread {
            sendLog("Starting like workflow")
            Thread.sleep(ACCESS_DELAY_MS)
            var root = waitForRoot(10)
            if (BuildConfig.DEBUG) Log.d(TAG, "Root: ${'$'}root")
            val rootNode = root ?: run {
                val msg = "Instagram UI not ready"
                sendLog(msg)
                sendResult(false, msg)
                performGlobalAction(GLOBAL_ACTION_BACK)
                return@Thread
            }
            val likeBtn = findByDesc(rootNode, "Like") ?: rootNode.findAccessibilityNodeInfosByText("Like").firstOrNull()
            if (likeBtn == null) {
                val msg = "Like button not found"
                sendLog(msg)
                sendResult(false, msg)
                return@Thread
            }
            likeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendLog("Pressed Like button")
            sendResult(true)
        }.start()
    }
}
