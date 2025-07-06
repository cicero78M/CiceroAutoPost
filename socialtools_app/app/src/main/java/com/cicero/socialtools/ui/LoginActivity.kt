package com.cicero.socialtools.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cicero.socialtools.R
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.IGClient.Builder.LoginHandler
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import com.github.instagram4j.instagram4j.utils.IGUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {
    private val clientFile: File by lazy { File(filesDir, "igclient.ser") }
    private val cookieFile: File by lazy { File(filesDir, "igcookie.ser") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val username = findViewById<EditText>(R.id.input_username)
        val password = findViewById<EditText>(R.id.input_password)
        findViewById<Button>(R.id.button_login_insta).setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (user.isNotBlank() && pass.isNotBlank()) {
                performLogin(user, pass)
            } else {
                Toast.makeText(this, "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loginWithDeviceInfo(
        user: String,
        pass: String,
        twoFactorHandler: LoginHandler,
        challengeHandler: LoginHandler
    ): IGClient {
        val httpClient = IGUtils.defaultHttpClientBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
        return IGClient.builder()
            .username(user)
            .password(pass)
            .client(httpClient.build())
            .onTwoFactor(twoFactorHandler)
            .onChallenge(challengeHandler)
            .login()
    }

    private fun performLogin(user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val codePrompt = Callable { runBlocking { promptCode() } }
            val twoFactorHandler = LoginHandler { client, resp ->
                IGChallengeUtils.resolveTwoFactor(client, resp, codePrompt)
            }
            val challengeHandler = LoginHandler { client, resp ->
                IGChallengeUtils.resolveChallenge(client, resp, codePrompt)
            }
            try {
                loginWithDeviceInfo(user, pass, twoFactorHandler, challengeHandler)
                    .serialize(clientFile, cookieFile)
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@LoginActivity, com.cicero.socialtools.features.instagram.InstagramToolsActivity::class.java))
                    finish()
                }
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Gagal login: ${e.loginResponse.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val message = e.message ?: e.toString()
                    Toast.makeText(this@LoginActivity, "Error: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun promptCode(): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val view = layoutInflater.inflate(R.layout.dialog_two_factor, null)
            val input = view.findViewById<EditText>(R.id.edit_code)
            androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    cont.resume(input.text.toString()) {}
                }
                .setNegativeButton("Batal") { _, _ ->
                    cont.resume("") {}
                }
                .show()
        }
    }
}
