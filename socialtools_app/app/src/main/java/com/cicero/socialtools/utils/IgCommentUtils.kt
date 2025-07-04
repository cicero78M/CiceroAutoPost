package com.cicero.socialtools.utils

import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/** Helper to post Instagram comments with a fallback using web requests. */
suspend fun IGClient.commentWithFallback(mediaId: String, text: String) {
    try {
        withContext(Dispatchers.IO) {
            sendRequest(MediaCommentRequest(mediaId, text)).join()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.IO) {
            postWebComment(mediaId, text)
        }
    }
}

@Throws(IOException::class)
fun IGClient.postWebComment(mediaId: String, text: String) {
    val body = FormBody.Builder()
        .add("comment_text", text)
        .add("replied_to_comment_id", "")
        .build()
    val request = Request.Builder()
        .url("https://www.instagram.com/web/comments/${mediaId}/add/")
        .header("X-CSRFToken", csrfToken)
        .header("Referer", "https://www.instagram.com")
        .post(body)
        .build()
    httpClient.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) {
            throw IOException("HTTP ${'$'}{resp.code}")
        }
    }
}
