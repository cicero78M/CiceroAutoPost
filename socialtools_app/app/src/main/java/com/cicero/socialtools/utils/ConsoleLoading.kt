package com.cicero.socialtools.utils

import android.util.Log

/** Utility to demonstrate a loading progress in the console log. */
object ConsoleLoading {
    /**
     * Log a simple loading progress from 0% to 100%.
     * This is useful for showing long-running tasks in logcat.
     */
    fun showLoading(tag: String = "ConsoleLoading", steps: Int = 10, delayMillis: Long = 300) {
        for (i in 1..steps) {
            val percent = i * 100 / steps
            Log.d(tag, "Loading... $percent%")
            try {
                Thread.sleep(delayMillis)
            } catch (e: InterruptedException) {
                Log.d(tag, "Interrupted", e)
                return
            }
        }
        Log.d(tag, "Loading complete")
    }
}
