package com.cicero.socialtools.utils

import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Helper to post Instagram comments using Instagram4j. */
suspend fun IGClient.commentWithFallback(
    mediaId: String,
    shortcode: String,
    text: String
) {
    withContext(Dispatchers.IO) {
        sendRequest(MediaCommentRequest(mediaId, text)).join()
    }
}
