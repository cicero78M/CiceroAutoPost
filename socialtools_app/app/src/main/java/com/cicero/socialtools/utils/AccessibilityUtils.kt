package com.cicero.socialtools.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ComponentName
import android.view.accessibility.AccessibilityManager

/** Utility for checking accessibility service status. */
object AccessibilityUtils {
    /**
     * Return true if the given accessibility service is enabled.
     */
    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expected = ComponentName(context, serviceClass)
        for (service in enabled) {
            val info = service.resolveInfo.serviceInfo
            val enabledName = ComponentName(info.packageName, info.name)
            if (enabledName == expected) {
                return true
            }
        }
        return false
    }
}
