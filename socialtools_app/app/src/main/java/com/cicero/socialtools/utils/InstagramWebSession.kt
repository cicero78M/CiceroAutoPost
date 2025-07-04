package com.cicero.socialtools.utils

import com.github.instagram4j.instagram4j.IGClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Helper to post comments via the Instagram web endpoint using cookies from
 * an existing Instagram4j session. The previous web login mechanism has been
 * removed to avoid triggering Instagram warnings.
 */
object InstagramWebSession {
    /**
     * Post a comment using cookies from the given [IGClient] session.
     */
    fun postComment(client: IGClient, mediaId: String, shortcode: String, text: String): Boolean {
        val cookieJar = client.httpClient.cookieJar
        val cookies = cookieJar.loadForRequest("https://www.instagram.com/".toHttpUrl())
        val cookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }

        val body = "comment_text=" + URLEncoder.encode(text, "UTF-8")
        val req = Request.Builder()
            .url("https://www.instagram.com/web/comments/$mediaId/add/")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("User-Agent", "Mozilla/5.0")
            .addHeader("X-CSRFToken", client.csrfToken)
            .addHeader("Cookie", cookieHeader)
            .addHeader("Referer", "https://www.instagram.com/p/$shortcode/")
            .build()

        return client.httpClient.newCall(req).execute().use { it.isSuccessful }
    }
}
