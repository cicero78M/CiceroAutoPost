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
