package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle

class TiktokPostService : AccessibilityService() {

    companion object {
        const val ACTION_UPLOAD_FINISHED = "com.cicero.socialtools.TIKTOK_UPLOAD_FINISHED"
    }

    private var hasClicked = false
    private var videoSelected = false
    private var captionInserted = false
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { performClick() }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            packageNames = arrayOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill"
            )
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                hasClicked = false
                videoSelected = false
                captionInserted = false
                handler.postDelayed(clickRunnable, 500)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handler.postDelayed(clickRunnable, 200)
            }
        }
    }

    private fun performClick() {
        val root = rootInActiveWindow ?: return

        if (!videoSelected) {
            val videoKeywords = listOf("Video", "Next", "Selanjutnya", "Pilih")
            var selectNode: AccessibilityNodeInfo? = null
            for (k in videoKeywords) {
                val nodes = root.findAccessibilityNodeInfosByText(k)
                selectNode = nodes.firstOrNull { it.isClickable }
                if (selectNode != null) break
            }
            if (selectNode != null) {
                videoSelected = true
                selectNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        if (!captionInserted) {
            val editNode = findEditText(root)
            if (editNode != null) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text
                if (!text.isNullOrBlank()) {
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    captionInserted = true
                    return
                }
            }
        }

        if (hasClicked) return
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
            performGlobalAction(GLOBAL_ACTION_HOME)
            stopSelf()
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if ("android.widget.EditText" == node.className) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {}
}

