package com.cicero.igtools

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SubscriptionConfirmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_confirm)

        val confirmButton = findViewById<Button>(R.id.button_confirm)
        val cancelButton = findViewById<Button>(R.id.button_cancel)

        cancelButton.setOnClickListener { finish() }

        confirmButton.setOnClickListener {
            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
            val token = prefs.getString("token", "") ?: ""
            val username = intent.getStringExtra("username") ?: ""
            if (token.isBlank() || username.isBlank()) {
                Toast.makeText(this, "Anda belum login", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                val client = OkHttpClient()
                val json = JSONObject().apply { put("username", username) }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://papiqo.com/api/subscription-confirmations")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                val success = try {
                    client.newCall(req).execute().use { it.isSuccessful }
                } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@SubscriptionConfirmActivity, "Konfirmasi berhasil dikirim", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@SubscriptionConfirmActivity, "Gagal mengirim konfirmasi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
