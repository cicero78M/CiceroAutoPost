package com.cicero.socialtools.utils

import org.json.JSONObject
import com.cicero.socialtools.utils.OpenAiUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiUtilsTest {
    @Test
    fun buildRequestJsonHandlesNewlines() {
        val caption = "Hello\nWorld"
        val jsonStr = OpenAiUtils.buildRequestJson(caption)
        val obj = JSONObject(jsonStr)
        assertEquals("gpt-3.5-turbo", obj.getString("model"))
        assertTrue(!obj.has("max_tokens"))
        val msg = obj.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", msg.getString("role"))
        val content = msg.getString("content")
        assertTrue(content.contains(caption))
    }
}
