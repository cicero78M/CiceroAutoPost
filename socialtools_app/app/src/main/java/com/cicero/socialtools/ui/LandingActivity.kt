package com.cicero.socialtools.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import com.cicero.socialtools.R
import com.cicero.socialtools.features.instagram.InstagramToolsActivity
import java.io.File

/** Simple landing page displayed on app launch. */
class LandingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clientFile = File(filesDir, "igclient.ser")
        val cookieFile = File(filesDir, "igcookie.ser")
        if (clientFile.exists() && cookieFile.exists()) {
            startActivity(Intent(this, InstagramToolsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_landing)

        findViewById<Button>(R.id.button_start).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
