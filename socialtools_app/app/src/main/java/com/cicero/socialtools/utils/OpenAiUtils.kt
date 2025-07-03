package com.cicero.socialtools.utils

import org.json.JSONObject

/** Utility helpers for OpenAI API requests */
object OpenAiUtils {
    /**
     * Build the request JSON for OpenAI chat completion.
     * This was previously located in [InstagramToolsFragment].
     */
    fun buildRequestJson(caption: String): String {
        val prompt = "Buat komentar Instagram yang ceria, bersahabat, dan mendukung. " +
            "Maksimal 15 kata. Gunakan nada ringan dan tulus untuk caption berikut: " + caption
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
