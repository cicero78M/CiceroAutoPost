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
        fetchTargetAccount()

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
                fetchTodayPosts(doLike, doRepost)
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


    private fun fetchTodayPosts(doLike: Boolean, doRepost: Boolean) {
        appendLog(
            ">>> Booting IG automation engine...",
            animate = true
        )
        appendLog(
            ">>> Target locked: @$targetUsername :: initializing recon...",
            animate = true
        )
        CoroutineScope(Dispatchers.IO).launch {
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
                val userAction = client.actions().users().findByUsername(targetUsername).join()
                ensureFollowing(client, targetUsername)
                val user = userAction.user
                val req = FeedUserRequest(user.pk)
                val resp = client.sendRequest(req).join()
                val today = java.time.LocalDate.now()
                val zone = java.time.ZoneId.systemDefault()
                val posts = mutableListOf<PostInfo>()
                for (item in resp.items) {
                    val date = java.time.Instant.ofEpochSecond(item.taken_at)
                        .atZone(zone).toLocalDate()
                    if (date == today) {
                        val caption = item.caption?.text
                        var isVideo = false
                        var videoUrl: String? = null
                        var coverUrl: String? = null
                        val images = mutableListOf<String>()
                        when (item) {
                            is TimelineVideoMedia -> {
                                isVideo = true
                                videoUrl = item.video_versions?.firstOrNull()?.url
                                coverUrl = item.image_versions2?.candidates?.firstOrNull()?.url
                            }
                            is TimelineImageMedia -> {
                                item.image_versions2?.candidates?.firstOrNull()?.url?.let { images.add(it) }
                            }
                            is TimelineCarouselMedia -> {
                                for (c in item.carousel_media) {
                                    when (c) {
                                        is ImageCarouselItem -> c.image_versions2.candidates.firstOrNull()?.url?.let { images.add(it) }
                                        is VideoCarouselItem -> {
                                            isVideo = true
                                            if (videoUrl == null) videoUrl = c.video_versions?.firstOrNull()?.url
                                            if (coverUrl == null) coverUrl = c.image_versions2.candidates.firstOrNull()?.url
                                        }
                                    }
                                }
                            }
                        }
                        posts.add(
                            PostInfo(
                                code = item.code,
                                id = item.id,
                                caption = caption,
                                isVideo = isVideo,
                                imageUrls = images,
                                videoUrl = videoUrl,
                                coverUrl = coverUrl
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    launchLogAndLikes(client, posts, doLike, doRepost)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error: ${e.message}")
                }
            }
        }
    }

    private fun launchLogAndLikes(
        client: IGClient,
        posts: List<PostInfo>,
        doLike: Boolean,
        doRepost: Boolean
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            for (post in posts) {
                val code = post.code
                appendLog(
                    "> found post: https://instagram.com/p/$code",
                    animate = true
                )
                // Removed caption logging
                delay(500)
            }
            appendLog(
                ">>> Scrape complete.",
                animate = true
            )

            if (doLike) {
                appendLog(
                    ">>> Preparing like sequence...",
                    animate = true
                )
                delay(2000)
                appendLog(
                    ">>> Executing like routine",
                    animate = true
                )
                var liked = 0
                for (post in posts.filter { !likedIds.contains(it.code) }) {
                    appendLog("> processing target post [${post.code}]", animate = true)
                    if (!isPremium && !canLikeBasic(targetUsername)) {
                        appendLog("> basic like limit reached for @$targetUsername", animate = true)
                        break
                    }
                    likeFlareAccounts(client, 3)
                    val id = post.id
                    val code = post.code
                    appendLog("> checking like status for $code", animate = true)
                    val alreadyLiked = try {
                        withContext(Dispatchers.IO) {
                            client.sendRequest(
                                com.github.instagram4j.instagram4j.requests.media.MediaInfoRequest(id)
                            ).join()
                        }.items.firstOrNull()?.isHas_liked == true
                    } catch (_: Exception) { false }
                    val statusText = if (alreadyLiked) "already liked" else "not yet liked"
                    appendLog("> status: $statusText", animate = true)
                    if (!alreadyLiked) {
                        try {
                            withContext(Dispatchers.IO) {
                                client.sendRequest(
                                    MediaActionRequest(id, MediaActionRequest.MediaAction.LIKE)
                                ).join()
                            }
                            appendLog("> liked post [$code]", animate = true)
                            liked++
                            likedIds.add(code)
                            val prefs = this@InstagramToolsActivity.getSharedPreferences("liked", Context.MODE_PRIVATE)
                            prefs.edit().putStringSet("ids", likedIds).apply()
                            if (!isPremium) {
                                recordBasicLike(targetUsername)
                            }
                        } catch (e: Exception) {
                            appendLog("Error liking: ${e.message}")
                        }
                    }
                    delay(randomDelayMs())
                    scrollRandomFlareFeed(client)
                    delay(randomDelayMs())
                }
                appendLog(
                    ">>> Like routine finished. $liked posts liked.",
                    animate = true
                )
            }




            if (doRepost) {
                if (doLike) {
                    delay(randomActionDelayMs())
                }
                appendLog(
                    ">>> Initiating environment for re-post ops...",
                    animate = true
                )
                Log.d("InstagramToolsFragment", "Start reposting posts")
                launchRepostSequence(client, posts)
            } else {
                withContext(Dispatchers.Main) { onProcessComplete() }
            }
        }
    }

    private fun launchRepostSequence(client: IGClient, posts: List<PostInfo>) {
        Log.d("InstagramToolsFragment", "Start repost sequence")
        val toProcess = posts.filter { !repostedIds.contains(it.code) }
        CoroutineScope(Dispatchers.IO).launch {
            for (post in toProcess) {
                Log.d("InstagramToolsFragment", "Processing post ${post.code}")
                if (isCaptionDuplicate(client, post.caption)) {
                    appendLog("> skip [${post.code}] - caption already used", animate = true)
                    continue
                }
                val files = withContext(Dispatchers.IO) {
                    Log.d("InstagramToolsFragment", "Downloading media for ${post.code}")
                    downloadMedia(post)
                }
                if (files.isEmpty()) continue
                try {
                    val fileList = files.joinToString { it.name }
                    appendLog("> uploading [${post.code}] $fileList", animate = true)
                    post.caption?.takeIf { it.isNotBlank() }?.let {
                        appendLog("> caption: $it", animate = true)
                    }
                    var newLink: String? = null
                    withContext(Dispatchers.IO) {
                        Log.d("InstagramToolsFragment", "Uploading ${post.code}")
                        val response: MediaResponse = (
                            if (post.isVideo && post.videoUrl != null) {
                                val video = files.first { it.extension == "mp4" }
                                val cover = files.firstOrNull { it.extension != "mp4" } ?: video
                                uploadVideoWithRetry(client, video, cover, post.caption ?: "")
                            } else {
                                if (files.size == 1) {
                                    client.actions().timeline().uploadPhoto(files[0], post.caption ?: "").join()
                                } else {
                                    val infos = files.map { TimelineAction.SidecarPhoto.from(it) }
                                    client.actions().timeline().uploadAlbum(infos, post.caption ?: "").join()
                                }
                            }
                        )
                        // wait to ensure upload/transcode finishes before continuing
                        val wait = uploadDelayMs + if (post.isVideo) videoUploadExtraDelayMs else 0L
                        delay(wait)
                        newLink = response.media?.code?.let { "https://instagram.com/p/$it" }
                    }
                    appendLog("> upload success [${post.code}]", animate = true)
                    newLink?.let {
                        Log.d("InstagramToolsFragment", "Send link $it")
                        appendLog("> repost link: $it", animate = true)
                        withContext(Dispatchers.IO) { sendRepostLink(post.code, it) }
                        repostedIds.add(post.code)
                        val prefs = this@InstagramToolsActivity.getSharedPreferences("reposted", Context.MODE_PRIVATE)
                        prefs.edit().putStringSet("ids", repostedIds).apply()
                    }
                    // do not delete downloaded files
                } catch (e: Exception) {
                    Log.e("InstagramToolsFragment", "Error uploading", e)
                    appendLog("Error uploading: ${e.message}")
                }
                Log.d("InstagramToolsFragment", "Waiting before next post")
                appendLog("> waiting for next post", animate = true)
                showWaitingDots(postDelayMs)
            }
            Log.d("InstagramToolsFragment", "Repost sequence finished")
            appendLog(
                ">>> Repost routine complete.",
                animate = true
            )
            withContext(Dispatchers.Main) { onProcessComplete() }
        }
    }

    private fun sendRepostLink(sc: String, link: String) {
        if (token.isBlank() || userId.isBlank()) return
        val json = JSONObject().apply {
            put("shortcode", sc)
            put("user_id", userId)
            put("instagram_link", link)
            put("twitter_link", JSONObject.NULL)
            put("tiktok_link", JSONObject.NULL)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val client = createHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/link-reports")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            client.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private suspend fun isCaptionDuplicate(client: IGClient, caption: String?): Boolean {
        if (caption.isNullOrBlank()) return false
        return try {
            val feed = withContext(Dispatchers.IO) {
                val req = FeedUserRequest(client.selfProfile.pk)
                client.sendRequest(req).join()
            }
            val clean = caption.trim()
            feed.items.take(12).any { it.caption?.text?.trim() == clean }
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadMedia(post: PostInfo): List<File> {
        val dir = File(this.getExternalFilesDir(null), "SocialToolsApp")
        if (!dir.exists()) dir.mkdirs()
        val files = mutableListOf<File>()
        if (post.isVideo && post.videoUrl != null) {
            val videoFile = File(dir, post.code + ".mp4")
            if (!videoFile.exists()) {
                appendLog("> downloading video", animate = true)
                downloadUrl(post.videoUrl, videoFile)
            } else {
                appendLog("> video already downloaded", animate = true)
            }
            files.add(videoFile)
            val coverUrl = post.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                val coverFile = File(dir, post.code + "_cover.jpg")
                if (!coverFile.exists()) {
                    appendLog("> downloading cover", animate = true)
                    downloadUrl(coverUrl, coverFile)
                } else {
                    appendLog("> cover already downloaded", animate = true)
                }
                files.add(coverFile)
            }
        } else {
            var idx = 1
            for (url in post.imageUrls) {
                val name = if (post.imageUrls.size > 1) "${post.code}_${idx++}.jpg" else "${post.code}.jpg"
                val f = File(dir, name)
                if (!f.exists()) {
                    appendLog("> downloading image", animate = true)
                    downloadUrl(url, f)
                } else {
                    appendLog("> image already downloaded", animate = true)
                }
                files.add(f)
            }
        }
        return files
    }

    private fun downloadUrl(url: String, file: File) {
        try {
            appendLog("> fetching $url", animate = true)
            val client = createHttpClient()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                val body = resp.body ?: return
                file.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            }
            appendLog("> saved to ${file.name}", animate = true)
        } catch (_: Exception) {
        }
    }

    private suspend fun uploadVideoWithRetry(
        client: IGClient,
        video: File,
        cover: File,
        caption: String,
        maxAttempts: Int = 3
    ): MediaResponse {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            try {
                return client.actions().timeline().uploadVideo(video, cover, caption).join()
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                if (msg.contains("transcode not finished", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        appendLog("> transcode not finished, waiting 90s", animate = true)
                    }
                    delay(videoUploadExtraDelayMs)
                    attempt++
                    continue
                }
                throw e
            }
        }
        throw lastError ?: RuntimeException("upload failed")
    }




    private suspend fun showWaitingDots(duration: Long) {
        var remaining = duration
        while (remaining > 0) {
            delay(3000)
            appendLog(".", animate = false)
            remaining -= 3000
        }
    }

    private fun limitWords(text: String, maxWords: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(maxWords).joinToString(" ").trim()
    }

    private fun onProcessComplete() {
        val elapsed = System.currentTimeMillis() - startTimeMs
        val seconds = (elapsed / 1000).toInt()
        processTimeView.text = getString(R.string.process_time_result, seconds)
    }


    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_instagram_profile, menu)
        return true
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
}
