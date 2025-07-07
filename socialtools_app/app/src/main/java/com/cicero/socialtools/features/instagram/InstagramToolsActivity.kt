package com.cicero.socialtools.features.instagram

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cicero.socialtools.BuildConfig
import com.cicero.socialtools.R
import com.cicero.socialtools.ui.LandingActivity
import com.cicero.socialtools.utils.OpenAiUtils
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.actions.timeline.TimelineAction
import com.github.instagram4j.instagram4j.models.media.timeline.ImageCarouselItem
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineCarouselMedia
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineImageMedia
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineVideoMedia
import com.github.instagram4j.instagram4j.models.media.timeline.VideoCarouselItem
import com.github.instagram4j.instagram4j.models.user.User
import com.github.instagram4j.instagram4j.requests.accounts.AccountsLogoutRequest
import com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest
import com.github.instagram4j.instagram4j.requests.media.MediaActionRequest
import com.github.instagram4j.instagram4j.requests.friendships.FriendshipsActionRequest
import com.github.instagram4j.instagram4j.responses.media.MediaResponse
import com.github.instagram4j.instagram4j.utils.IGUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import com.google.android.material.snackbar.Snackbar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class PostInfo(
    val code: String,
    val id: String,
    val caption: String?,
    val isVideo: Boolean,
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val coverUrl: String? = null
)

@Suppress("DEPRECATION")
class InstagramToolsActivity : AppCompatActivity() {
    private lateinit var profileContainer: View
    private lateinit var startButton: Button
    private lateinit var likeCheckbox: android.widget.CheckBox
    private lateinit var repostCheckbox: android.widget.CheckBox
    // Removed user-configurable delay UI
    private fun randomActionDelayMs(): Long = Random.nextLong(60000L, 180000L)
    private val uploadDelayMs: Long = 10000L
    private val videoUploadExtraDelayMs: Long = 90000L
    private val postDelayMs: Long = 120000L
    private val THREE_DAYS_MS: Long = 3 * 24 * 60 * 60 * 1000L
    private lateinit var badgeView: ImageView
    private lateinit var logContainer: android.widget.LinearLayout
    private lateinit var logScroll: android.widget.ScrollView
    private lateinit var clearLogsButton: android.widget.ImageButton
    private lateinit var avatarView: ImageView
    private lateinit var usernameView: TextView
    private lateinit var nameView: TextView
    private lateinit var bioView: TextView
    private lateinit var postsView: TextView
    private lateinit var followersView: TextView
    private lateinit var followingView: TextView
    private lateinit var processTimeView: TextView
    // Removed Twitter and TikTok UI elements
    private lateinit var targetLinkInput: AutoCompleteTextView
    private lateinit var targetAdapter: android.widget.ArrayAdapter<String>
    private val targetLinks = mutableSetOf<String>()
    private val repostedIds = mutableSetOf<String>()
    private val likedIds = mutableSetOf<String>()
    private val clientFile: File by lazy { File(this.filesDir, "igclient.ser") }
    private val cookieFile: File by lazy { File(this.filesDir, "igcookie.ser") }
    private var currentUsername: String? = null
    private var token: String = ""
    private var userId: String = ""
    private var targetUsername: String = "polres_ponorogo"
    private var isPremium: Boolean = false
    private var startTimeMs: Long = 0L

    private fun canLikeBasic(username: String): Boolean {
        val prefs = getSharedPreferences("basic_like_limits", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val record = prefs.getString(username, null)
        var count = 0
        var since = now
        if (record != null) {
            val parts = record.split("|")
            if (parts.size == 2) {
                count = parts[0].toIntOrNull() ?: 0
                since = parts[1].toLongOrNull() ?: now
            }
            if (now - since >= THREE_DAYS_MS) {
                count = 0
                since = now
            }
        }
        return if (count >= 3) {
            false
        } else {
            prefs.edit().putString(username, "$count|$since").apply()
            true
        }
    }

    private fun recordBasicLike(username: String) {
        val prefs = getSharedPreferences("basic_like_limits", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val record = prefs.getString(username, null)
        var count = 0
        var since = now
        if (record != null) {
            val parts = record.split("|")
            if (parts.size == 2) {
                count = parts[0].toIntOrNull() ?: 0
                since = parts[1].toLongOrNull() ?: now
            }
            if (now - since >= THREE_DAYS_MS) {
                count = 0
                since = now
            }
        }
        count++
        prefs.edit().putString(username, "$count|$since").apply()
    }
    private val accessibilityLogReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LandingActivity.ACTION_ACCESSIBILITY_LOG) {
                intent.getStringExtra(LandingActivity.EXTRA_LOG_MESSAGE)?.let { msg ->
                    appendLog(msg)
                    val rootView = findViewById<View>(android.R.id.content)
                    com.google.android.material.snackbar.Snackbar
                        .make(rootView, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }
    private val flareTargets = listOf(
        "respaskot",
        "humas.polresblitar",
        "polres_ponorogo",
        "polreskediriofficial",
        "ditlantaspoldajatim",
        "humaspoldajatim",
        "ditlantas_poldariau",
        "ditlantaspoldajateng",
        "divhumaspolri",
        "divpropampolri",
        "divtikpolri"
    )

    private fun randomDelayMs(): Long = Random.nextLong(60000L, 180000L)
    private fun randomCommentDelayMs(): Long = Random.nextLong(60000L, 180000L)

    private fun createHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

    private suspend fun scrollRandomFlareFeed(client: IGClient) {
        val username = flareTargets.random()
        withContext(Dispatchers.Main) { appendLog("> scrolling @$username", animate = true) }
        try {
            withContext(Dispatchers.IO) {
                val action = client.actions().users().findByUsername(username).join()
                var maxId: String? = null
                repeat(3) {
                    val req = FeedUserRequest(action.user.pk, maxId)
                    val resp = client.sendRequest(req).join()
                    maxId = resp.next_max_id
                    if (maxId == null) return@withContext
                    delay(500)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { appendLog("Error scroll @$username: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instagram_tools)
        startPostService()

        setupViews()
    }

    override fun onStart() {
        super.onStart()
        this.registerReceiver(
            accessibilityLogReceiver,
            android.content.IntentFilter(LandingActivity.ACTION_ACCESSIBILITY_LOG)
        )
    }

    override fun onStop() {
        this.unregisterReceiver(accessibilityLogReceiver)
        super.onStop()
    }

    @SuppressLint("SetTextI18n")
    private fun setupViews() {
        profileContainer = findViewById(R.id.profile_layout)
        val profileView = findViewById<View>(R.id.profile_container)
        avatarView = profileView.findViewById(R.id.image_avatar)
        usernameView = profileView.findViewById(R.id.text_username)
        nameView = profileView.findViewById(R.id.text_name)
        bioView = profileView.findViewById(R.id.text_bio)
        postsView = profileView.findViewById(R.id.stat_posts)
        followersView = profileView.findViewById(R.id.stat_followers)
        followingView = profileView.findViewById(R.id.stat_following)
        profileView.findViewById<View>(R.id.text_nrp).visibility = View.GONE
        profileView.findViewById<View>(R.id.info_container).visibility = View.GONE
        profileView.findViewById<Button>(R.id.button_logout).visibility = View.GONE

        // Removed Twitter and TikTok containers
        targetLinkInput = findViewById(R.id.input_target_link)

        val targetPrefs = this.getSharedPreferences("target_links", Context.MODE_PRIVATE)
        targetLinks.addAll(targetPrefs.getStringSet("links", emptySet()) ?: emptySet())
        targetAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            targetLinks.toList()
        )
        targetLinkInput.setAdapter(targetAdapter)
        targetLinkInput.setOnClickListener { targetLinkInput.showDropDown() }


        startButton = findViewById(R.id.button_start)
        likeCheckbox = findViewById(R.id.checkbox_like)
        repostCheckbox = findViewById(R.id.checkbox_repost)
        badgeView = profileView.findViewById(R.id.image_badge)
        logContainer = findViewById(R.id.log_container)
        logScroll = findViewById(R.id.log_scroll)
        clearLogsButton = findViewById(R.id.button_clear_logs)
        processTimeView = findViewById(R.id.text_process_time)


        clearLogsButton.setOnClickListener { clearLogs() }

        val authPrefs = this.getSharedPreferences("auth", Context.MODE_PRIVATE)
        token = authPrefs.getString("token", "") ?: ""
        userId = authPrefs.getString("userId", "") ?: ""
        val repostPrefs = this.getSharedPreferences("reposted", Context.MODE_PRIVATE)
        repostedIds.addAll(repostPrefs.getStringSet("ids", emptySet()) ?: emptySet())
        val likePrefs = this.getSharedPreferences("liked", Context.MODE_PRIVATE)
        likedIds.addAll(likePrefs.getStringSet("ids", emptySet()) ?: emptySet())
        startButton.setOnClickListener {
            val target = targetLinkInput.text.toString().trim()
            if (target.isBlank()) {
                Toast.makeText(this, "Link target wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!targetLinks.contains(target)) {
                targetLinks.add(target)
                val prefs = this.getSharedPreferences("target_links", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("links", targetLinks).apply()
                targetAdapter.clear()
                targetAdapter.addAll(targetLinks.toList())
                targetAdapter.notifyDataSetChanged()
            }
            targetUsername = parseUsername(target)

            startTimeMs = System.currentTimeMillis()
            processTimeView.text = getString(R.string.loading)

            val doLike = likeCheckbox.isChecked
            val doRepost = repostCheckbox.isChecked
            if (doLike || doRepost) {
                fetchTodayPosts(doLike, doRepost, false)
            } else {
                Toast.makeText(this, "Pilih setidaknya satu aksi", Toast.LENGTH_SHORT).show()
            }
        }

        checkSubscriptionStatus(currentUsername)

        restoreSession()
    }

    @SuppressLint("SetTextI18n")
    private fun displayProfile(info: User?) {
        usernameView.text = "@${info?.username ?: ""}"
        nameView.text = info?.full_name ?: ""
        postsView.text = info?.media_count?.toString() ?: "0"
        followersView.text = info?.follower_count?.toString() ?: "0"
        followingView.text = info?.following_count?.toString() ?: "0"
        val url = info?.profile_pic_url
        if (!url.isNullOrBlank()) {
            Glide.with(this)
                .load(url)
                .circleCrop()
                .into(avatarView)
        } else {
            avatarView.setImageDrawable(null)
        }
        bioView.text = info?.biography ?: ""
        profileContainer.visibility = View.VISIBLE
        currentUsername = info?.username
        currentUsername?.let { loadSavedLogs(it) }

        ensureRemoteData(info)
        checkSubscriptionStatus(info?.username)
    }

    private fun restoreSession() {
        CoroutineScope(Dispatchers.IO).launch {
            if (clientFile.exists() && cookieFile.exists()) {
                try {
                    val client = IGClient.deserialize(
                        clientFile,
                        cookieFile,
                        IGUtils.defaultHttpClientBuilder()
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .readTimeout(120, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .callTimeout(180, TimeUnit.SECONDS)
                    )
                    val info = client.actions().users().info(client.selfProfile.pk).join()
                    withContext(Dispatchers.Main) { displayProfile(info) }
                    ensureRemoteData(info)
                    checkSubscriptionStatus(info?.username)
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@InstagramToolsActivity, com.cicero.socialtools.ui.LoginActivity::class.java))
                        finish()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@InstagramToolsActivity, com.cicero.socialtools.ui.LoginActivity::class.java))
                    finish()
                }
            }
        }
    }




    private fun checkSubscriptionStatus(username: String?) {
        val user = username ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = createHttpClient()
                val req = Request.Builder()
                    .url("https://papiqo.com/api/premium-subscriptions/user/$user/active")
                    .header("Authorization", "Bearer $token")
                    .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    val dataObj = try {
                        JSONObject(body ?: "{}").optJSONObject("data")
                    } catch (_: Exception) { null }
                    isPremium = resp.isSuccessful && dataObj != null
                    withContext(Dispatchers.Main) {
                        badgeView.setImageResource(
                            if (isPremium) R.drawable.ic_badge_premium else R.drawable.ic_badge_basic
                        )
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    badgeView.setImageResource(R.drawable.ic_badge_basic)
                }
            }
        }
    }

    private fun ensureRemoteData(info: User?) {
        val username = info?.username ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = createHttpClient()
            try {
                val checkUserReq = Request.Builder()
                    .url("https://papiqo.com/api/insta/instagram-user?username=$username")
                    .header("Authorization", "Bearer $token")
                    .build()
                val userExists = client.newCall(checkUserReq).execute().use { it.isSuccessful }
                if (!userExists) {
                    val fetchReq = Request.Builder()
                        .url("https://papiqo.com/api/insta/rapid-profile?username=$username")
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(fetchReq).execute().close()
                }

                val checkSubReq = Request.Builder()
                    .url("https://papiqo.com/api/premium-subscriptions/user/$username/active")
                    .header("Authorization", "Bearer $token")
                    .build()
                val subExists = client.newCall(checkSubReq).execute().use { resp ->
                    val bodyStr = resp.body?.string()
                    val dataObj = try {
                        JSONObject(bodyStr ?: "{}").optJSONObject("data")
                    } catch (_: Exception) { null }
                    resp.isSuccessful && dataObj != null
                }
                if (!subExists) {
                    val json = JSONObject().apply {
                        put("username", username)
                        put("start_date", java.time.LocalDate.now().toString())
                        put("is_active", false)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val postReq = Request.Builder()
                        .url("https://papiqo.com/api/premium-subscriptions")
                        .header("Authorization", "Bearer $token")
                        .post(body)
                        .build()
                    client.newCall(postReq).execute().close()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getLogFileForUser(user: String): File {
        return File(this.filesDir, "instalog_${user}.txt")
    }

    private fun loadSavedLogs(user: String) {
        logContainer.removeAllViews()
        val file = getLogFileForUser(user)
        if (file.exists()) {
            file.forEachLine { line ->
                appendLog(line, appendToFile = false)
            }
        }
    }

    private fun clearLogs() {
        logContainer.removeAllViews()
        currentUsername?.let { user ->
            val file = getLogFileForUser(user)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun appendLog(
        text: String,
        appendToFile: Boolean = true,
        animate: Boolean = false
    ) {
        lifecycleScope.launch(Dispatchers.Main) {
            val tv = TextView(this@InstagramToolsActivity).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(android.graphics.Color.parseColor("#00FF00"))
            }
            logContainer.addView(tv)
            if (animate && text.length <= 100) {
                for (c in text) {
                    tv.append(c.toString())
                    delay(30)
                }
            } else {
                tv.text = text
            }
            logScroll.fullScroll(View.FOCUS_DOWN)
        }
        if (appendToFile) {
            currentUsername?.let { user ->
                try {
                    getLogFileForUser(user).appendText(text + "\n")
                } catch (_: Exception) {
                    // ignore I/O errors
                }
            }
        }
    }

    private fun fetchTargetAccount() {
        if (token.isBlank() || userId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = createHttpClient()
            val userReq = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(userReq).execute().use { resp ->
                    val body = resp.body?.string()
                    val clientId = if (resp.isSuccessful) {
                        try {
                            JSONObject(body ?: "{}")
                                .optJSONObject("data")
                                ?.optString("client_id") ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                    if (clientId.isNotBlank()) {
                        val insta = fetchClientInsta(client, clientId)
                        if (!insta.isNullOrBlank()) {
                            withContext(Dispatchers.Main) { targetUsername = insta }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun fetchClientInsta(client: OkHttpClient, clientId: String): String? {
        val req = Request.Builder()
            .url("https://papiqo.com/api/clients/$clientId")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                try {
                    JSONObject(body ?: "{}")
                        .optJSONObject("data")
                        ?.optString("client_insta")
                        ?.takeIf { it.isNotBlank() }
                        ?: JSONObject(body ?: "{}").optString("client_insta")
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
    private fun parseUsername(input: String): String {
        var user = input
        user = user.removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("instagram.com/")
            .trim().trimEnd('/')
        if (user.startsWith("@")) user = user.substring(1)
        return user
    }

    private suspend fun ensureFollowing(client: IGClient, username: String) {
        try {
            val action = withContext(Dispatchers.IO) {
                client.actions().users().findByUsername(username).join()
            }
            val friendship = withContext(Dispatchers.IO) {
                action.getFriendship().join()
            }
            if (!friendship.isFollowing) {
                withContext(Dispatchers.IO) {
                    action.action(FriendshipsActionRequest.FriendshipsAction.CREATE).join()
                }
                withContext(Dispatchers.Main) { appendLog("> followed @$username", animate = true) }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun likeFlareAccounts(client: IGClient, count: Int) {
        withContext(Dispatchers.Main) {
            appendLog(">>> Liking flare accounts", animate = true)
        }
        for (username in flareTargets.shuffled().take(count)) {
            withContext(Dispatchers.Main) { appendLog("> flare @$username", animate = true) }
            ensureFollowing(client, username)
            var attempt = 0
            var success = false
            while (attempt < 5 && !success) {
                try {
                    val action = withContext(Dispatchers.IO) {
                        client.actions().users().findByUsername(username).join()
                    }
                    val resp = withContext(Dispatchers.IO) {
                        val req = FeedUserRequest(action.user.pk)
                        client.sendRequest(req).join()
                    }
                    val candidates = resp.items.take(12)
                    var liked = false
                    for (item in candidates) {
                        val id = item.id
                        val code = item.code
                        val already = try {
                            withContext(Dispatchers.IO) {
                                client.sendRequest(
                                    com.github.instagram4j.instagram4j.requests.media.MediaInfoRequest(id)
                                ).join()
                            }.items.firstOrNull()?.isHas_liked == true
                        } catch (_: Exception) { false }
                        if (!already) {
                            withContext(Dispatchers.IO) {
                                client.sendRequest(
                                    MediaActionRequest(id, MediaActionRequest.MediaAction.LIKE)
                                ).join()
                            }
                            withContext(Dispatchers.Main) { appendLog("> liked [$code]", animate = true) }
                            liked = true
                            break
                        }
                    }
                    if (!liked) {
                        withContext(Dispatchers.Main) { appendLog("> all recent posts already liked", animate = true) }
                    }
                    success = true
                } catch (e: Exception) {
                    attempt++
                    withContext(Dispatchers.Main) { appendLog("Error flare @$username: ${e.message}") }
                    if (attempt < 5) delay(30000)
                }
            }
            delay(randomCommentDelayMs())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (clientFile.exists() && cookieFile.exists()) {
                            val client = IGClient.deserialize(
                                clientFile,
                                cookieFile,
                                IGUtils.defaultHttpClientBuilder()
                                    .connectTimeout(60, TimeUnit.SECONDS)
                                    .readTimeout(120, TimeUnit.SECONDS)
                                    .writeTimeout(60, TimeUnit.SECONDS)
                                    .callTimeout(180, TimeUnit.SECONDS)
                            )
                            client.sendRequest(AccountsLogoutRequest()).join()
                        }
                    } catch (_: Exception) {
                    }
                    withContext(Dispatchers.Main) {
                        currentUsername = null
                        clientFile.delete()
                        cookieFile.delete()
                        startActivity(Intent(this@InstagramToolsActivity, com.cicero.socialtools.ui.LoginActivity::class.java))
                        finish()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startPostService() {
        val intent = Intent(this, com.cicero.socialtools.core.services.PostService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * Previous versions performed automated like and repost actions here.
     * The full implementation was removed, but the start button still
     * expects this entry point. For now we simply log a placeholder
     * so the app can compile without the legacy workflow.
     */
    private fun fetchTodayPosts(doLike: Boolean, doRepost: Boolean, doComment: Boolean) {
        appendLog("> IG automation has been disabled in this build")
    }
}
