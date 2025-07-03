package com.cicero.socialtools

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstagramToolsFragmentTest {
    @Test
    fun buildRequestJsonHandlesNewlines() {
        val caption = "Hello\nWorld"
        val jsonStr = buildOpenAiRequestJson(caption, 15)
        val obj = JSONObject(jsonStr)
        assertEquals("gpt-3.5-turbo", obj.getString("model"))
        assertEquals(15, obj.getInt("max_tokens"))
        val msg = obj.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", msg.getString("role"))
        val content = msg.getString("content")
        assertTrue(content.contains(caption))
    }
}
