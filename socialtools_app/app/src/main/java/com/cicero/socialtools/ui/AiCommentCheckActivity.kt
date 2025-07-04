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
import java.io.File
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest
import com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest
import java.util.concurrent.TimeUnit

/** Simple screen to test AI comment generation using OpenAI. */
class AiCommentCheckActivity : AppCompatActivity() {

    private fun createHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_comment_check)

        val input = findViewById<EditText>(R.id.input_caption)
        val result = findViewById<TextView>(R.id.text_result)
        val button = findViewById<Button>(R.id.button_generate)
        val commentButton = findViewById<Button>(R.id.button_comment_last_post)

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

        commentButton.setOnClickListener {
            result.text = getString(R.string.loading)
            commentLatestPost(result)
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
        val client = createHttpClient()
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
                    Log.d("AiCommentCheck", "Error ${resp.code} response: ${bodyStr}")
                    return "Error ${resp.code}: ${bodyStr}".trim()
                }
                Log.d("AiCommentCheck", "Raw response: $bodyStr")
                val obj = JSONObject(bodyStr ?: "{}")
                val text = obj.getJSONArray("choices")
                    .optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                if (text.isNullOrBlank()) {
                    return "Raw response: ${bodyStr?.take(200)}"
                }
                limitWords(text, 15)
            }
        } catch (e: Exception) {
            "Exception: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun commentLatestPost(resultView: TextView) {
        CoroutineScope(Dispatchers.IO).launch {
            val clientFile = File(filesDir, "igclient.ser")
            val cookieFile = File(filesDir, "igcookie.ser")
            if (!clientFile.exists() || !cookieFile.exists()) {
                withContext(Dispatchers.Main) {
                    resultView.text = "Instagram session not found"
                }
                return@launch
            }
            try {
                val client = IGClient.deserialize(clientFile, cookieFile)
                val userAction = client.actions().users()
                    .findByUsername("polresbojonegoroofficial").join()
                val feed = client.sendRequest(
                    FeedUserRequest(userAction.user.pk)
                ).join()
                val item = feed.items.firstOrNull()
                if (item == null) {
                    withContext(Dispatchers.Main) {
                        resultView.text = "No posts found"
                    }
                    return@launch
                }
                val captionText = item.caption?.text ?: ""
                val commentText = fetchAiComment(captionText)
                if (commentText.isBlank()) {
                    withContext(Dispatchers.Main) {
                        resultView.text = getString(R.string.error_generating_comment)
                    }
                    return@launch
                }
                client.sendRequest(
                    MediaCommentRequest(item.id, commentText)
                ).join()
                withContext(Dispatchers.Main) {
                    resultView.text = "Comment posted: $commentText"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultView.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun limitWords(text: String, maxWords: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(maxWords).joinToString(" ").trim()
    }
}
