package com.cicero.socialtools.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cicero.socialtools.BuildConfig
import com.cicero.socialtools.R
import com.cicero.socialtools.utils.OpenAiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Simple screen to test AI comment generation using OpenAI. */
class AiCommentCheckActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_comment_check)

        val input = findViewById<EditText>(R.id.input_caption)
        val result = findViewById<TextView>(R.id.text_result)
        val button = findViewById<Button>(R.id.button_generate)

        button.setOnClickListener {
            result.text = getString(R.string.loading)
            val caption = input.text.toString()
            CoroutineScope(Dispatchers.IO).launch {
                val responseText = fetchAiComment(caption)
                withContext(Dispatchers.Main) {
                    result.text = responseText
                }
            }
        }
    }

    private fun fetchAiComment(caption: String): String {
        val apiKey = BuildConfig.OPENAI_API_KEY.ifBlank {
            System.getenv("OPENAI_API_KEY") ?: ""
        }
        if (apiKey.isBlank()) {
            return "API key missing"
        }
        if (caption.isBlank()) {
            return "Caption empty"
        }

        val json = OpenAiUtils.buildRequestJson(caption)
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string()
                if (!resp.isSuccessful) {
                    Log.d("AiCommentCheck", "Error ${'$'}{resp.code} response: ${'$'}{bodyStr}")
                    return "Error ${'$'}{resp.code}: ${'$'}{bodyStr}".trim()
                }
                Log.d("AiCommentCheck", "Raw response: ${'$'}bodyStr")
                val obj = JSONObject(bodyStr ?: "{}")
                val text = obj.getJSONArray("choices")
                    .optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                if (text.isNullOrBlank()) {
                    return "Raw response: ${'$'}{bodyStr?.take(200)}"
                }
                limitWords(text, 15)
            }
        } catch (e: Exception) {
            "Exception: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun limitWords(text: String, maxWords: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(maxWords).joinToString(" ").trim()
    }
}
