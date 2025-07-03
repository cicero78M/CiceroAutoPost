package com.cicero.socialtools

import com.cicero.socialtools.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object InstagramGraphApi {
    data class Post(
        val id: String,
        val caption: String?,
        val mediaUrl: String
    )

    suspend fun fetchRecentPosts(limit: Int = 10): List<Post> = withContext(Dispatchers.IO) {
        val token = BuildConfig.IG_GRAPH_TOKEN
        val userId = BuildConfig.IG_GRAPH_USER_ID
        if (token.isBlank() || userId.isBlank()) return@withContext emptyList<Post>()
        val fields = "id,caption,media_url"
        val url = "https://graph.instagram.com/$userId/media?fields=$fields&access_token=$token&limit=$limit"
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        return@withContext try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList<Post>()
                val body = resp.body?.string() ?: return@withContext emptyList<Post>()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: JSONArray()
                List(data.length()) { i ->
                    val item = data.getJSONObject(i)
                    Post(
                        id = item.getString("id"),
                        caption = item.optString("caption"),
                        mediaUrl = item.getString("media_url")
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
