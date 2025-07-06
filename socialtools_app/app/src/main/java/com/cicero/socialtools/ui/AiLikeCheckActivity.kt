package com.cicero.socialtools.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.cicero.socialtools.R
import com.cicero.socialtools.core.services.InstagramLikeService
import com.cicero.socialtools.utils.AccessibilityUtils

/** Screen that links to accessibility settings so the user can enable the like service. */
class AiLikeCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_like_check)

        findViewById<Button>(R.id.button_open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AccessibilityUtils.isServiceEnabled(this, InstagramLikeService::class.java)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            finish()
        }
    }
}
