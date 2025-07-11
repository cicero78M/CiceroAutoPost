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
    private var backPressed = false
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { performClick() }
    private val stepDelayMs = 5000L

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
                backPressed = false
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
        }
    }

    private fun performClick() {
        val root = rootInActiveWindow ?: return

        if (!videoSelected) {
            val videoKeywords = listOf("Video", "Next", "Selanjutnya", "Berikutnya", "Pilih")
            val selectNode = findClickableNodeByText(root, videoKeywords)
            if (selectNode != null) {
                videoSelected = true
                selectNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(clickRunnable, stepDelayMs)
                return
            } else if (!backPressed) {
                backPressed = true
                performGlobalAction(GLOBAL_ACTION_BACK)
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
                    var newText = editNode.text?.toString()
                    if (newText.isNullOrBlank() || newText != text.toString()) {
                        editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        newText = editNode.text?.toString()
                    }
                    if (!newText.isNullOrBlank() && newText == text.toString()) {
                        captionInserted = true
                        backPressed = false
                        handler.postDelayed(clickRunnable, stepDelayMs)
                    } else {
                        handler.postDelayed(clickRunnable, stepDelayMs)
                    }
                    return
                }
            }
            handler.postDelayed(clickRunnable, stepDelayMs)
            return
        }

        if (hasClicked) return
        val keywords = listOf("Post", "Posting")
        val node = findClickableNodeByText(root, keywords)
        if (node != null) {
            hasClicked = true
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendBroadcast(Intent(ACTION_UPLOAD_FINISHED))
            performGlobalAction(GLOBAL_ACTION_HOME)
            stopSelf()
        } else if (!backPressed) {
            backPressed = true
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun findClickableNodeByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (t in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(t)
            for (n in nodes) {
                var current: AccessibilityNodeInfo? = n
                while (current != null && !current.isClickable) {
                    current = current.parent
                }
                if (current != null && current.isClickable) {
                    return current
                }
            }
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if ("android.widget.EditText" == node.className || node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {}
}

