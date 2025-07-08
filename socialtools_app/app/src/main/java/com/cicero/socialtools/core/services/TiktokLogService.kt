package com.cicero.socialtools.core.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class TiktokLogService : AccessibilityService() {

    companion object {
        const val ACTION_LOG_FINISHED = "com.cicero.socialtools.TIKTOK_LOG_FINISHED"
    }

    private lateinit var writer: BufferedWriter

    override fun onServiceConnected() {
        writer = BufferedWriter(FileWriter(File(filesDir, "tiktok_ui_log.txt"), true))
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
        event ?: return
        val pkg = event.packageName?.toString()
        writer.apply {
            write("Event: ${eventTypeToString(event.eventType)} package=$pkg\n")
            rootInActiveWindow?.let { dumpNode(it, 0) }
            write("----\n")
            flush()
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != "com.zhiliaoapp.musically" && pkg != "com.ss.android.ugc.trill") {
                sendBroadcast(Intent(ACTION_LOG_FINISHED))
                stopSelf()
            }
        }
    }

    private fun dumpNode(node: AccessibilityNodeInfo, indent: Int) {
        val prefix = "  ".repeat(indent)
        writer.write(prefix + node.className + " text=" + (node.text ?: "") + " content=" + (node.contentDescription ?: "") + "\n")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, indent + 1) }
        }
    }

    private fun eventTypeToString(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        else -> type.toString()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try {
            writer.close()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

