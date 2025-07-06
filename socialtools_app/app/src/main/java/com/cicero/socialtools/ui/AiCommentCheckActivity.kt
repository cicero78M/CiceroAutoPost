package com.cicero.socialtools.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import com.cicero.socialtools.core.services.InstagramCommentService
import com.cicero.socialtools.utils.AccessibilityUtils
import androidx.appcompat.app.AppCompatActivity
import com.cicero.socialtools.R

/** Screen that links to accessibility settings so the user can enable the service. */
class AiCommentCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_comment_check)

        findViewById<Button>(R.id.button_open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AccessibilityUtils.isServiceEnabled(this, InstagramCommentService::class.java)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            finish()
        }
    }
}
