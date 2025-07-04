package com.cicero.socialtools.utils

import android.content.Context
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest
import com.cicero.socialtools.utils.InstagramWebSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/** Helper to post Instagram comments with a fallback using web requests. */
suspend fun IGClient.commentWithFallback(
    context: Context,
    mediaId: String,
    shortcode: String,
    text: String
) {
    var success = false
    try {
        withContext(Dispatchers.IO) {
            sendRequest(MediaCommentRequest(mediaId, text)).join()
        }
        success = true
    } catch (_: Exception) {
    }
    if (!success) {
        try {
            withContext(Dispatchers.IO) { postWebComment(mediaId, text) }
            success = true
        } catch (_: Exception) {
        }
    }
    if (!success) {
        withContext(Dispatchers.IO) {
            InstagramWebSession.postComment(context, mediaId, shortcode, text)
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
