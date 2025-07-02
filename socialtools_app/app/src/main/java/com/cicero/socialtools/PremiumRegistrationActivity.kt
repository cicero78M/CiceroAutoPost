package com.cicero.socialtools

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
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

class PremiumRegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium_registration)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""

        val activeStatusView = findViewById<TextView>(R.id.text_active_status)
        val registrationContainer = findViewById<LinearLayout>(R.id.registration_container)

        val amount = 50000 + (100..999).random()
        val amountView = findViewById<TextView>(R.id.text_amount)
        amountView.text = "Rp. ${String.format("%,d", amount).replace(',', '.')}"

        val sessionEndView = findViewById<TextView>(R.id.text_session_end)
        val endTime = System.currentTimeMillis() + 10 * 60 * 1000
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        sessionEndView.text = getString(R.string.session_end_format, sdf.format(java.util.Date(endTime)))

        val username = findViewById<EditText>(R.id.input_username)
        val usernameExtra = intent.getStringExtra("username")
        usernameExtra?.takeIf { it.isNotBlank() }?.let {
            username.setText(it)
        }
        username.isEnabled = false
        val nama = findViewById<EditText>(R.id.input_nama_rekening)
        val nomor = findViewById<EditText>(R.id.input_nomor_rekening)
        val phone = findViewById<EditText>(R.id.input_phone)
        val button = findViewById<Button>(R.id.button_submit)
        val cancelButton = findViewById<Button>(R.id.button_cancel)

        if (token.isNotBlank() && !usernameExtra.isNullOrBlank()) {
            checkActiveSubscription(token, usernameExtra, activeStatusView, registrationContainer)
        }

        cancelButton.setOnClickListener { finish() }

        button.setOnClickListener {
            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
            val token = prefs.getString("token", "") ?: ""
            val userId = prefs.getString("userId", "") ?: ""
            if (token.isBlank() || userId.isBlank()) {
                Toast.makeText(this, "Anda belum login", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val usernameVal = username.text.toString().trim()
            val namaVal = nama.text.toString().trim()
            val nomorVal = nomor.text.toString().trim()
            val phoneVal = phone.text.toString().trim()

            CoroutineScope(Dispatchers.IO).launch {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("username", usernameVal)
                    put("nama_rekening", namaVal)
                    put("nomor_rekening", nomorVal)
                    put("phone", phoneVal)
                    put("amount", amount)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://papiqo.com/api/subscription-registrations")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                val success = try {
                    client.newCall(request).execute().use { it.isSuccessful }
                } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@PremiumRegistrationActivity, "Pendaftaran tersimpan", Toast.LENGTH_SHORT).show()
                        val intent = android.content.Intent(this@PremiumRegistrationActivity, SubscriptionConfirmActivity::class.java)
                        intent.putExtra("username", usernameVal)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@PremiumRegistrationActivity, "Gagal mendaftar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkActiveSubscription(
        token: String,
        username: String,
        statusView: TextView,
        formContainer: LinearLayout
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("https://papiqo.com/api/premium-subscriptions/user/$username/active")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string()
                    val dataObj = try {
                        JSONObject(bodyStr ?: "{}").optJSONObject("data")
                    } catch (_: Exception) { null }
                    val endDate = dataObj?.optString("end_date", "")
                    val active = resp.isSuccessful && dataObj != null
                    if (active) {
                        withContext(Dispatchers.Main) {
                            formContainer.visibility = android.view.View.GONE
                            statusView.visibility = android.view.View.VISIBLE
                            statusView.text = getString(R.string.active_subscription_status, endDate)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
