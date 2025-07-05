package com.cicero.socialtools.ui

import android.os.Bundle
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.cicero.socialtools.R
import com.cicero.socialtools.features.instagram.InstagramToolsFragment
import com.cicero.socialtools.core.services.PostService

class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_INPUT_COMMENT = "com.cicero.socialtools.INPUT_COMMENT"
        const val EXTRA_COMMENT = "extra_comment"
        const val ACTION_ACCESSIBILITY_LOG = "com.cicero.socialtools.ACCESS_LOG"
        const val EXTRA_LOG_MESSAGE = "extra_log"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startPostService()
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, InstagramToolsFragment())
                .commit()
        }

    }

    private fun startPostService() {
        val intent = Intent(this, PostService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
