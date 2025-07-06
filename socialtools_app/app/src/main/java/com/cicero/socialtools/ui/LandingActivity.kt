package com.cicero.socialtools.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import com.cicero.socialtools.R

/** Simple landing page displayed on app launch. */
class LandingActivity : AppCompatActivity() {
    companion object {
        const val ACTION_INPUT_COMMENT = "com.cicero.socialtools.INPUT_COMMENT"
        const val EXTRA_COMMENT = "extra_comment"
        const val ACTION_ACCESSIBILITY_LOG = "com.cicero.socialtools.ACCESS_LOG"
        const val EXTRA_LOG_MESSAGE = "extra_log"
        const val ACTION_COMMENT_RESULT = "com.cicero.socialtools.COMMENT_RESULT"
        const val EXTRA_COMMENT_SUCCESS = "extra_success"
        const val EXTRA_COMMENT_ERROR = "extra_error"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)

        findViewById<Button>(R.id.button_start).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
