package com.cicero.socialtools

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.provider.Settings
import android.webkit.WebSettings
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.instagram4j.instagram4j.exceptions.IGResponseException
import com.github.instagram4j.instagram4j.responses.accounts.LoginResponse
import com.bumptech.glide.Glide
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.IGClient.Builder.LoginHandler
import com.github.instagram4j.instagram4j.IGDevice
import com.github.instagram4j.instagram4j.IGAndroidDevice
import com.github.instagram4j.instagram4j.actions.timeline.TimelineAction
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import com.cicero.socialtools.BuildConfig
import com.github.instagram4j.instagram4j.models.media.timeline.ImageCarouselItem
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineCarouselMedia
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineImageMedia
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineVideoMedia
import com.github.instagram4j.instagram4j.models.media.timeline.VideoCarouselItem
import com.github.instagram4j.instagram4j.models.user.User
import com.github.instagram4j.instagram4j.requests.accounts.AccountsLogoutRequest
import com.github.instagram4j.instagram4j.requests.media.MediaActionRequest
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Callable
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
class InstagramToolsFragment : Fragment(R.layout.fragment_instagram_tools) {
    private lateinit var loginContainer: View
    private lateinit var profileContainer: View
    private lateinit var startButton: Button
    private lateinit var likeCheckbox: android.widget.CheckBox
    private lateinit var repostCheckbox: android.widget.CheckBox
    private lateinit var commentCheckbox: android.widget.CheckBox
    private lateinit var delaySeekBar: android.widget.SeekBar
    private lateinit var delayText: TextView
    private var actionDelayMs: Long = 30000L
    private lateinit var badgeView: ImageView
    private lateinit var logContainer: android.widget.LinearLayout
    private lateinit var logScroll: android.widget.ScrollView
    private lateinit var avatarView: ImageView
    private lateinit var usernameView: TextView
    private lateinit var nameView: TextView
    private lateinit var bioView: TextView
    private lateinit var postsView: TextView
    private lateinit var followersView: TextView
    private lateinit var followingView: TextView
    // Removed Twitter and TikTok UI elements
    private lateinit var targetLinkInput: EditText
    private val repostedIds = mutableSetOf<String>()
    private val likedIds = mutableSetOf<String>()
    private val commentedIds = mutableSetOf<String>()
    private val clientFile: File by lazy { File(requireContext().filesDir, "igclient.ser") }
    private val cookieFile: File by lazy { File(requireContext().filesDir, "igcookie.ser") }
    private var currentUsername: String? = null
    private var token: String = ""
    private var userId: String = ""
    private var targetUsername: String = "polres_ponorogo"
    private var isPremium: Boolean = false
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

    private fun randomDelayMs(): Long = Random.nextLong(3000L, 12000L)
    private fun randomCommentDelayMs(): Long = Random.nextLong(5000L, 20000L)

    private suspend fun scrollRandomFlareFeed(client: IGClient) {
        val username = flareTargets.random()
        withContext(Dispatchers.Main) { appendLog("> scrolling @$username", animate = true) }
        try {
            val action = client.actions().users().findByUsername(username).join()
            var maxId: String? = null
            repeat(3) {
                val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(action.user.pk, maxId)
                val resp = client.sendRequest(req).join()
                maxId = resp.next_max_id
                if (maxId == null) return
                delay(500)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { appendLog("Error scroll @$username: ${e.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val username = view.findViewById<EditText>(R.id.input_username)
        val password = view.findViewById<EditText>(R.id.input_password)
        loginContainer = view.findViewById(R.id.login_container)
        profileContainer = view.findViewById(R.id.profile_layout)
        val profileView = view.findViewById<View>(R.id.profile_container)
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
        targetLinkInput = view.findViewById(R.id.input_target_link)

        delaySeekBar = view.findViewById(R.id.seekbar_delay)
        delayText = view.findViewById(R.id.text_delay_value)
        actionDelayMs = delaySeekBar.progress * 1000L
        delayText.text = "Delay: ${delaySeekBar.progress} detik"
        delaySeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                actionDelayMs = progress * 1000L
                delayText.text = "Delay: ${progress} detik"
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })


        startButton = view.findViewById(R.id.button_start)
        likeCheckbox = view.findViewById(R.id.checkbox_like)
        repostCheckbox = view.findViewById(R.id.checkbox_repost)
        commentCheckbox = view.findViewById(R.id.checkbox_comment)
        badgeView = profileView.findViewById(R.id.image_badge)
        logContainer = view.findViewById(R.id.log_container)
        logScroll = view.findViewById(R.id.log_scroll)

        val authPrefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        token = authPrefs.getString("token", "") ?: ""
        userId = authPrefs.getString("userId", "") ?: ""
        val repostPrefs = requireContext().getSharedPreferences("reposted", Context.MODE_PRIVATE)
        repostedIds.addAll(repostPrefs.getStringSet("ids", emptySet()) ?: emptySet())
        val likePrefs = requireContext().getSharedPreferences("liked", Context.MODE_PRIVATE)
        likedIds.addAll(likePrefs.getStringSet("ids", emptySet()) ?: emptySet())
        val commentPrefs = requireContext().getSharedPreferences("commented", Context.MODE_PRIVATE)
        commentedIds.addAll(commentPrefs.getStringSet("ids", emptySet()) ?: emptySet())
        fetchTargetAccount()

        startButton.setOnClickListener {
            val target = targetLinkInput.text.toString().trim()
            if (target.isBlank()) {
                Toast.makeText(requireContext(), "Link target wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            targetUsername = parseUsername(target)

            val doLike = likeCheckbox.isChecked
            val doRepost = repostCheckbox.isChecked
            val doComment = commentCheckbox.isChecked
            if (doLike || doRepost || doComment) {
                fetchTodayPosts(doLike, doRepost, doComment)
            } else {
                Toast.makeText(requireContext(), "Pilih setidaknya satu aksi", Toast.LENGTH_SHORT).show()
            }
        }

        checkSubscriptionStatus(currentUsername)

        restoreSession()


        view.findViewById<Button>(R.id.button_login_insta).setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (user.isNotBlank() && pass.isNotBlank()) {
                performLogin(user, pass)
            } else {
                Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun loginWithDeviceInfo(
        user: String,
        pass: String,
        twoFactorHandler: LoginHandler,
        challengeHandler: LoginHandler
    ): IGClient {
        val androidId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val userAgent = WebSettings.getDefaultUserAgent(requireContext())
        val device = IGDevice(userAgent, IGAndroidDevice.CAPABILITIES, emptyMap())

        val client = IGClient.builder()
            .username(user)
            .password(pass)
            .device(device)
            .build()

        runCatching {
            val field = IGClient::class.java.getDeclaredField("deviceId")
            field.isAccessible = true
            field.set(client, androidId)
        }

        val response = client.sendLoginRequest()
            .exceptionally { tr ->
                var login = IGResponseException.IGFailedResponse.of(tr.cause, LoginResponse::class.java)
                if (login.two_factor_info != null) {
                    login = twoFactorHandler.accept(client, login)
                }
                if (login.challenge != null) {
                    login = challengeHandler.accept(client, login)
                    runCatching {
                        val method = IGClient::class.java.getDeclaredMethod(
                            "setLoggedInState",
                            LoginResponse::class.java
                        )
                        method.isAccessible = true
                        method.invoke(client, login)
                    }
                }
                login
            }.join()

        if (!client.isLoggedIn) {
            throw IGLoginException(client, response)
        }

        return client
    }


    private fun performLogin(user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val codePrompt = Callable {
                runBlocking { promptCode() }
            }

            val twoFactorHandler = LoginHandler { client, resp ->
                IGChallengeUtils.resolveTwoFactor(client, resp, codePrompt)
            }
            val challengeHandler = LoginHandler { client, resp ->
                IGChallengeUtils.resolveChallenge(client, resp, codePrompt)
            }

            try {
                val client = loginWithDeviceInfo(
                    user,
                    pass,
                    twoFactorHandler,
                    challengeHandler
                )
                client.serialize(clientFile, cookieFile)
                val info = client.actions().users().info(client.selfProfile.pk).join()
                withContext(Dispatchers.Main) {
                    displayProfile(info)
                }
                ensureRemoteData(info)
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal login: ${e.loginResponse.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("InstagramToolsFragment", "Login failed", e)
                withContext(Dispatchers.Main) {
                    val message = e.message ?: e.toString()
                    Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun promptCode(): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_two_factor, null)
            val input = view.findViewById<EditText>(R.id.edit_code)
            AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    cont.resume(input.text.toString()) {}
                }
                .setNegativeButton("Batal") { _, _ ->
                    cont.resume("") {}
                }
                .show()
        }
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
        loginContainer.visibility = View.GONE
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
                val client = IGClient.deserialize(clientFile, cookieFile)
                val info = client.actions().users().info(client.selfProfile.pk).join()
                withContext(Dispatchers.Main) { displayProfile(info) }
                ensureRemoteData(info)
                checkSubscriptionStatus(info?.username)
                } catch (_: Exception) {
                    // ignore invalid session
                }
            }
        }
    }




    private fun checkSubscriptionStatus(username: String?) {
        val user = username ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
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
            val client = OkHttpClient()
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
        return File(requireContext().filesDir, "instalog_${user}.txt")
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

    private fun appendLog(
        text: String,
        appendToFile: Boolean = true,
        animate: Boolean = false
    ) {
        val tv = TextView(requireContext()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
        }
        logContainer.addView(tv)
        CoroutineScope(Dispatchers.Main).launch {
            if (animate) {
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
            val client = OkHttpClient()
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

    private suspend fun likeFlareAccounts(client: IGClient, count: Int) {
        withContext(Dispatchers.Main) {
            appendLog(">>> Liking flare accounts", animate = true)
        }
        for (username in flareTargets.shuffled().take(count)) {
            withContext(Dispatchers.Main) { appendLog("> flare @$username", animate = true) }
            try {
                val action = client.actions().users().findByUsername(username).join()
                val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(action.user.pk)
                val resp = client.sendRequest(req).join()
                val candidates = resp.items.take(12)
                var liked = false
                for (item in candidates) {
                    val id = item.id
                    val code = item.code
                    val already = try {
                        client.sendRequest(
                            com.github.instagram4j.instagram4j.requests.media.MediaInfoRequest(id)
                        ).join().items.firstOrNull()?.isHas_liked == true
                    } catch (_: Exception) { false }
                    if (!already) {
                        client.sendRequest(
                            MediaActionRequest(id, MediaActionRequest.MediaAction.LIKE)
                        ).join()
                        withContext(Dispatchers.Main) { appendLog("> liked [$code]", animate = true) }
                        liked = true
                        break
                    }
                }
                if (!liked) {
                    withContext(Dispatchers.Main) { appendLog("> all recent posts already liked", animate = true) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("Error flare @$username: ${'$'}{e.message}") }
            }
            delay(randomCommentDelayMs())
        }
    }

    private suspend fun commentFlareAccounts(client: IGClient, count: Int) {
        withContext(Dispatchers.Main) {
            appendLog(">>> Commenting flare accounts", animate = true)
        }
        for (username in flareTargets.shuffled().take(count)) {
            withContext(Dispatchers.Main) { appendLog("> flare @$username", animate = true) }
            try {
                val action = client.actions().users().findByUsername(username).join()
                val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(action.user.pk)
                val resp = client.sendRequest(req).join()
                val candidates = resp.items.take(12)
                var commented = false
                var skippedNoText = 0
                var failed = 0
                for (item in candidates) {
                    val id = item.id
                    val code = item.code
                    val captionText = item.caption?.text ?: ""
                    Log.d("InstagramToolsFragment", "Candidate $code caption: ${captionText.take(40)}")
                    // Generate a friendly and supportive comment for the post
                    // caption using OpenAI with a 30 token limit
                    val aiComment = fetchAiComment(captionText, 30)
                    if (aiComment == null) {
                        withContext(Dispatchers.Main) { appendLog("> AI comment generation returned null") }
                        Log.d("InstagramToolsFragment", "AI comment generation returned null")
                    }
                    val text = aiComment ?: ""
                    if (text.isBlank()) {
                        skippedNoText++
                        withContext(Dispatchers.Main) { appendLog("> skip [$code] - no comment text") }
                        continue
                    }
                    try {
                        client.sendRequest(
                            com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest(id, text)
                        ).join()
                        withContext(Dispatchers.Main) { appendLog("> commented [$code]", animate = true) }
                        commented = true
                        break
                    } catch (e: Exception) {
                        failed++
                        withContext(Dispatchers.Main) { appendLog("Error commenting [$code]: ${'$'}{e.message}") }
                    }
                }
                if (!commented) {
                    val info = "skipped ${'$'}skippedNoText, failed ${'$'}failed"
                    withContext(Dispatchers.Main) {
                        appendLog("> all recent posts already commented or no text (${'$'}info)", animate = true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("Error flare @$username: ${'$'}{e.message}") }
            }
            delay(randomCommentDelayMs())
        }
    }



    private fun fetchTodayPosts(doLike: Boolean, doRepost: Boolean, doComment: Boolean) {
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
                val client = IGClient.deserialize(clientFile, cookieFile)
                val userAction = client.actions().users().findByUsername(targetUsername).join()
                val user = userAction.user
                val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(user.pk)
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
                    launchLogAndLikes(client, posts, doLike, doRepost, doComment)
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
        doRepost: Boolean,
        doComment: Boolean
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            for (post in posts) {
                val code = post.code
                appendLog(
                    "> found post: https://instagram.com/p/$code",
                    animate = true
                )
                post.caption?.let {
                    appendLog(
                        "> caption: $it",
                        animate = true
                    )
                }
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
                    appendLog("> processing target post [${'$'}{post.code}]", animate = true)
                    likeFlareAccounts(client, 5)
                    val id = post.id
                    val code = post.code
                    appendLog("> checking like status for ${'$'}code", animate = true)
                    val alreadyLiked = try {
                        withContext(Dispatchers.IO) {
                            client.sendRequest(
                                com.github.instagram4j.instagram4j.requests.media.MediaInfoRequest(id)
                            ).join()
                        }.items.firstOrNull()?.isHas_liked == true
                    } catch (_: Exception) { false }
                    val statusText = if (alreadyLiked) "already liked" else "not yet liked"
                    appendLog("> status: ${'$'}statusText", animate = true)
                    if (!alreadyLiked) {
                        try {
                            withContext(Dispatchers.IO) {
                                client.sendRequest(
                                    MediaActionRequest(id, MediaActionRequest.MediaAction.LIKE)
                                ).join()
                            }
                            appendLog("> liked post [${'$'}code]", animate = true)
                            liked++
                            likedIds.add(code)
                            val prefs = requireContext().getSharedPreferences("liked", Context.MODE_PRIVATE)
                            prefs.edit().putStringSet("ids", likedIds).apply()
                        } catch (e: Exception) {
                            appendLog("Error liking: ${'$'}{e.message}")
                        }
                    }
                    delay(randomDelayMs())
                    scrollRandomFlareFeed(client)
                    delay(randomDelayMs())
                }
                appendLog(
                    ">>> Like routine finished. ${'$'}liked posts liked.",
                    animate = true
                )
            }

            if (doComment) {
                if (doLike) {
                    delay(actionDelayMs)
                }
                appendLog(
                    ">>> Preparing comment sequence...",
                    animate = true
                )
                delay(2000)
                appendLog(
                    ">>> Executing comment routine",
                    animate = true
                )
                var commented = 0
                for (post in posts.filter { !commentedIds.contains(it.code) }) {
                    val code = post.code
                    val id = post.id
                    post.caption?.let {
                        appendLog("> caption: ${'$'}it", animate = true)
                    }
                    commentFlareAccounts(client, 5)
                    val text = withContext(Dispatchers.IO) {
                        fetchAiComment(post.caption ?: "")
                    } ?: ""
                    if (text.isBlank()) {
                        delay(randomCommentDelayMs())
                        scrollRandomFlareFeed(client)
                        delay(randomCommentDelayMs())
                        continue
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            client.sendRequest(
                                com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest(id, text)
                            ).join()
                        }
                        appendLog(
                            "> commented on [$code]",
                            animate = true
                        )
                        commented++
                        commentedIds.add(code)
                        val prefs = requireContext().getSharedPreferences("commented", Context.MODE_PRIVATE)
                        prefs.edit().putStringSet("ids", commentedIds).apply()
                    } catch (e: Exception) {
                        appendLog("Error commenting: ${'$'}{e.message}")
                    }
                    delay(randomCommentDelayMs())
                    scrollRandomFlareFeed(client)
                    delay(randomCommentDelayMs())
                }
                appendLog(
                    ">>> Comment routine finished. ${'$'}commented posts commented.",
                    animate = true
                )
            }

            if (doRepost) {
                if (doLike) {
                    delay(actionDelayMs)
                }
                appendLog(
                    ">>> Initiating environment for re-post ops...",
                    animate = true
                )
                Log.d("InstagramToolsFragment", "Start reposting posts")
                launchRepostSequence(client, posts)
            }
        }
    }

    private fun launchRepostSequence(client: IGClient, posts: List<PostInfo>) {
        Log.d("InstagramToolsFragment", "Start repost sequence")
        val toProcess = posts.filter { !repostedIds.contains(it.code) }
        CoroutineScope(Dispatchers.Main).launch {
            for (post in toProcess) {
                Log.d("InstagramToolsFragment", "Processing post ${'$'}{post.code}")
                val files = withContext(Dispatchers.IO) {
                    Log.d("InstagramToolsFragment", "Downloading media for ${'$'}{post.code}")
                    downloadMedia(post)
                }
                if (files.isEmpty()) continue
                try {
                    var newLink: String? = null
                    withContext(Dispatchers.IO) {
                        Log.d("InstagramToolsFragment", "Uploading ${'$'}{post.code}")
                        val response = if (post.isVideo && post.videoUrl != null) {
                            val video = files.first { it.extension == "mp4" }
                            val cover = files.firstOrNull { it.extension != "mp4" } ?: video
                            client.actions().timeline().uploadVideo(video, cover, post.caption ?: "").join()
                        } else {
                            if (files.size == 1) {
                                client.actions().timeline().uploadPhoto(files[0], post.caption ?: "").join()
                            } else {
                                val infos = files.map { TimelineAction.SidecarPhoto.from(it) }
                                client.actions().timeline().uploadAlbum(infos, post.caption ?: "").join()
                            }
                        }
                        newLink = response.media?.code?.let { "https://instagram.com/p/$it" }
                    }
                    newLink?.let {
                        Log.d("InstagramToolsFragment", "Send link ${'$'}it")
                        appendLog("> repost link: ${'$'}it", animate = true)
                        withContext(Dispatchers.IO) { sendRepostLink(post.code, it) }
                        repostedIds.add(post.code)
                        val prefs = requireContext().getSharedPreferences("reposted", Context.MODE_PRIVATE)
                        prefs.edit().putStringSet("ids", repostedIds).apply()
                    }
                    // do not delete downloaded files
                } catch (e: Exception) {
                    Log.e("InstagramToolsFragment", "Error uploading", e)
                    appendLog("Error uploading: ${'$'}{e.message}")
                }
                Log.d("InstagramToolsFragment", "Waiting before next post")
                appendLog("> waiting for next post", animate = true)
                showWaitingDots(actionDelayMs)
            }
            Log.d("InstagramToolsFragment", "Repost sequence finished")
            appendLog(
                ">>> Repost routine complete.",
                animate = true
            )
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
        val client = OkHttpClient()
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

    private fun downloadMedia(post: PostInfo): List<File> {
        val dir = File(requireContext().getExternalFilesDir(null), "SocialToolsApp")
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
            val client = OkHttpClient()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                val body = resp.body ?: return
                file.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            }
            appendLog("> saved to ${'$'}{file.name}", animate = true)
        } catch (_: Exception) {
        }
    }

    private fun fetchAiComment(caption: String, maxTokens: Int = 30): String? {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            appendLog("> AI comment skipped: API key blank")
            Log.d("InstagramToolsFragment", "OpenAI API key is blank")
            return null
        }
        if (caption.isBlank()) {
            appendLog("> AI comment skipped: caption empty")
            Log.d("InstagramToolsFragment", "Caption empty, skipping AI comment")
            return null
        }
        Log.d("InstagramToolsFragment", "Requesting AI comment for caption: ${caption.take(40)}")
        val json = buildOpenAiRequestJson(caption, maxTokens)
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
                    appendLog("> OpenAI request failed: ${'$'}{resp.code} ${'$'}{bodyStr?.take(80)}")
                    Log.d(
                        "InstagramToolsFragment",
                        "OpenAI request failed: ${'$'}{resp.code} body: ${'$'}{bodyStr}"
                    )
                    return null
                }
                Log.d("InstagramToolsFragment", "OpenAI raw response: ${'$'}{bodyStr?.take(60)}")
                val obj = JSONObject(bodyStr ?: "{}")
                obj.getJSONArray("choices")
                    .optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
            }
        } catch (e: Exception) {
            val details = e.stackTraceToString()
            appendLog("> OpenAI call error: ${'$'}{e.javaClass.simpleName}: ${'$'}{e.message}\n$details")
            Log.e("InstagramToolsFragment", "OpenAI call error", e)
            null
        }
    }

    internal fun buildOpenAiRequestJson(caption: String, maxTokens: Int = 30): String {
        val prompt = "Buat komentar Instagram yang ceria, bersahabat, dan mendukung untuk " +
                "caption berikut. Gunakan nada yang ringan dan tulus: " + caption
        val message = JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        }
        return JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", org.json.JSONArray().put(message))
            put("max_tokens", maxTokens)
        }.toString()
    }

    private suspend fun showWaitingDots(duration: Long) {
        var remaining = duration
        while (remaining > 0) {
            delay(5000)
            appendLog(".", animate = false)
            remaining -= 5000
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_instagram_profile, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (clientFile.exists() && cookieFile.exists()) {
                            val client = IGClient.deserialize(clientFile, cookieFile)
                            client.sendRequest(AccountsLogoutRequest()).join()
                        }
                    } catch (_: Exception) {
                    }
                    withContext(Dispatchers.Main) {
                        profileContainer.visibility = View.GONE
                        loginContainer.visibility = View.VISIBLE
                        currentUsername = null
                        clientFile.delete()
                        cookieFile.delete()
                    }
                }
                true
            }
            R.id.action_register_premium -> {
                val intent = android.content.Intent(requireContext(), PremiumRegistrationActivity::class.java)
                intent.putExtra("username", currentUsername ?: "")
                startActivity(intent)
                true
            }
            R.id.action_confirm_subscription -> {
                val intent = android.content.Intent(requireContext(), SubscriptionConfirmActivity::class.java)
                intent.putExtra("username", currentUsername ?: "")
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
