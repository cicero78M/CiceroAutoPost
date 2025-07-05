package com.cicero.socialtools.utils

import org.json.JSONObject

/** Utility helpers for OpenAI API requests */
object OpenAiUtils {
    /** Remove @mentions and #hashtags from caption */
    fun sanitizeCaption(raw: String): String {
        return raw
            .replace(Regex("@\\w+"), "")
            .replace(Regex("#\\w+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Build the request JSON for OpenAI chat completion.
     * This was previously located in [InstagramToolsFragment].
     */
    fun buildRequestJson(caption: String): String {
        val clean = sanitizeCaption(caption)
        val prompt =
            "Buat komentar Instagram yang ceria, bersahabat, dan mendukung. " +
                "Maksimal 15 kata. Tanpa hashtag, mention, atau emotikon. " +
                "Gunakan nada ringan dan tulus untuk caption berikut: " +
                clean
        val message = JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        }
        return JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", org.json.JSONArray().put(message))
        }.toString()
    }
}
