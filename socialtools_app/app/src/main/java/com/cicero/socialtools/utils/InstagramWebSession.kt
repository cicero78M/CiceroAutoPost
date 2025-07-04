package com.cicero.socialtools.utils

import android.content.Context
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Simple helper to manage a lightweight Instagram web session.
 * Cookies are persisted in SharedPreferences under the key "web_ig_cookies".
 */
object InstagramWebSession {
    private const val PREF_NAME = "web_ig_cookies"
    private val cookieMap = mutableMapOf<String, String>()

    private fun updateCookiesFromHeaders(headers: List<String>) {
        for (c in headers) {
            val pair = c.substringBefore(';').split('=', limit = 2)
            if (pair.size == 2) {
                cookieMap[pair[0]] = pair[1]
            }
        }
    }

    fun load(context: Context) {
        cookieMap.clear()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        for ((k, v) in prefs.all) {
            val value = v as? String ?: continue
            cookieMap[k] = value
        }
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            clear()
            cookieMap.forEach { (k, v) -> putString(k, v) }
            apply()
        }
    }

    private fun cookieHeader(): String =
        cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun isLoggedIn(): Boolean = cookieMap.containsKey("sessionid") && cookieMap.containsKey("csrftoken")

    /**
     * Perform basic web login to Instagram and persist cookies if successful.
     */
    fun login(context: Context, username: String, password: String): Boolean {
        val client = OkHttpClient()
        // initial request to obtain csrftoken
        val initReq = Request.Builder()
            .url("https://www.instagram.com/accounts/login/")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()
        client.newCall(initReq).execute().use { resp ->
            updateCookiesFromHeaders(resp.headers("Set-Cookie"))
        }
        val csrf = cookieMap["csrftoken"] ?: return false
        val body = FormBody.Builder()
            .add("username", username)
            .add("enc_password", password)
            .add("queryParams", "{}")
            .add("optIntoOneTap", "false")
            .build()
        val loginReq = Request.Builder()
            .url("https://www.instagram.com/accounts/login/ajax/")
            .header("User-Agent", "Mozilla/5.0")
            .header("X-CSRFToken", csrf)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/accounts/login/")
            .header("Cookie", cookieHeader())
            .post(body)
            .build()
        val success = client.newCall(loginReq).execute().use { resp ->
            updateCookiesFromHeaders(resp.headers("Set-Cookie"))
            resp.isSuccessful && resp.body?.string()?.contains("\"authenticated\": true") == true
        }
        if (success) save(context) else cookieMap.clear()
        return success
    }

    /**
     * Post a comment using the stored web session cookies.
     */
    fun postComment(context: Context, mediaId: String, shortcode: String, text: String): Boolean {
        if (!isLoggedIn()) {
            load(context)
            if (!isLoggedIn()) return false
        }
        val client = OkHttpClient()
        val body = "comment_text=" + URLEncoder.encode(text, "UTF-8")
        val req = Request.Builder()
            .url("https://www.instagram.com/web/comments/${'$'}mediaId/add/")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("User-Agent", "Mozilla/5.0")
            .addHeader("X-CSRFToken", cookieMap["csrftoken"] ?: "")
            .addHeader("Cookie", cookieHeader())
            .addHeader("Referer", "https://www.instagram.com/p/${'$'}shortcode/")
            .build()
        return client.newCall(req).execute().use { it.isSuccessful }
    }
}

