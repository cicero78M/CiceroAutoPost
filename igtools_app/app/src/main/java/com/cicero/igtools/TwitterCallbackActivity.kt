package com.cicero.igtools

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TwitterCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val verifier = intent.data?.getQueryParameter("oauth_verifier")
        if (verifier.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = TwitterAuthManager.finishAuth(this@TwitterCallbackActivity, verifier)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@TwitterCallbackActivity,
                    result,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}
