package com.cicero.socialtools.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** Utility for checking accessibility service status. */
object AccessibilityUtils {
    /**
     * Return true if the given accessibility service is enabled.
     */
    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabled) {
            val info = service.resolveInfo.serviceInfo
            if (info.packageName == context.packageName && info.name == serviceClass.name) {
                return true
            }
        }
        return false
    }
}
