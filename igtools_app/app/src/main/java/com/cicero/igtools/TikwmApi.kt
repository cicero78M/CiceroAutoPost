package com.cicero.igtools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object TikwmApi {
    suspend fun fetchUser(username: String): JSONObject? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val encoded = URLEncoder.encode(username, "UTF-8")
        val url = "https://tikwm.com/api/user/info?unique_id=$encoded"
        val req = Request.Builder().url(url).build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optInt("code") == 0) {
                    json.getJSONObject("data").getJSONObject("user")
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
