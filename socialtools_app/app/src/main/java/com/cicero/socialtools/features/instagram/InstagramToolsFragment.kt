package com.cicero.socialtools.features.instagram

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cicero.socialtools.BuildConfig
import com.cicero.socialtools.R
import com.cicero.socialtools.ui.MainActivity
import com.cicero.socialtools.ui.AiCommentCheckActivity
import com.cicero.socialtools.utils.OpenAiUtils
import com.cicero.socialtools.utils.AccessibilityUtils
import com.cicero.socialtools.core.services.InstagramCommentService
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.IGClient.Builder.LoginHandler
import com.github.instagram4j.instagram4j.actions.timeline.TimelineAction
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
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
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import com.github.instagram4j.instagram4j.utils.IGUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val uploadDelayMs: Long = 10000L
    private val videoUploadExtraDelayMs: Long = 90000L
    private val postDelayMs: Long = 120000L
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
    private lateinit var targetLinkInput: EditText
    private val repostedIds = mutableSetOf<String>()
    private val likedIds = mutableSetOf<String>()
    private val commentedIds = mutableSetOf<String>()
    private val flareCommentedIds = mutableSetOf<String>()
    private val clientFile: File by lazy { File(requireContext().filesDir, "igclient.ser") }
    private val cookieFile: File by lazy { File(requireContext().filesDir, "igcookie.ser") }
    private var currentUsername: String? = null
    private var token: String = ""
    private var userId: String = ""
    private var targetUsername: String = "polres_ponorogo"
    private var isPremium: Boolean = false
    private var startTimeMs: Long = 0L
    private val accessibilityLogReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_ACCESSIBILITY_LOG) {
                intent.getStringExtra(MainActivity.EXTRA_LOG_MESSAGE)?.let { msg ->
                    appendLog(msg)
                    view?.let { v ->
                        com.google.android.material.snackbar.Snackbar
                            .make(v, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .show()
                    }
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

    private fun randomDelayMs(): Long = Random.nextLong(3000L, 12000L)
    private fun randomCommentDelayMs(): Long = Random.nextLong(30000L, 120000L)

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
        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()
        requireContext().registerReceiver(
            accessibilityLogReceiver,
            android.content.IntentFilter(MainActivity.ACTION_ACCESSIBILITY_LOG)
        )
    }

    override fun onStop() {
        requireContext().unregisterReceiver(accessibilityLogReceiver)
        super.onStop()
    }

    @SuppressLint("SetTextI18n")
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
        delayText.text =
            "Delay: ${delaySeekBar.progress} detik"
        delaySeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                actionDelayMs = progress * 1000L
                delayText.text = "Delay: $progress detik"
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
        clearLogsButton = view.findViewById(R.id.button_clear_logs)
        processTimeView = view.findViewById(R.id.text_process_time)


        clearLogsButton.setOnClickListener { clearLogs() }

        val authPrefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        token = authPrefs.getString("token", "") ?: ""
        userId = authPrefs.getString("userId", "") ?: ""
        val repostPrefs = requireContext().getSharedPreferences("reposted", Context.MODE_PRIVATE)
        repostedIds.addAll(repostPrefs.getStringSet("ids", emptySet()) ?: emptySet())
        val likePrefs = requireContext().getSharedPreferences("liked", Context.MODE_PRIVATE)
        likedIds.addAll(likePrefs.getStringSet("ids", emptySet()) ?: emptySet())
        val commentPrefs = requireContext().getSharedPreferences("commented", Context.MODE_PRIVATE)
        commentedIds.addAll(commentPrefs.getStringSet("ids", emptySet()) ?: emptySet())
        val flareCommentPrefs = requireContext().getSharedPreferences("flare_commented", Context.MODE_PRIVATE)
        flareCommentedIds.addAll(flareCommentPrefs.getStringSet("ids", emptySet()) ?: emptySet())
        fetchTargetAccount()

        startButton.setOnClickListener {
            val target = targetLinkInput.text.toString().trim()
            if (target.isBlank()) {
                Toast.makeText(requireContext(), "Link target wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            targetUsername = parseUsername(target)

            startTimeMs = System.currentTimeMillis()
            processTimeView.text = getString(R.string.loading)

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
        val httpClient = IGUtils.defaultHttpClientBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
        return IGClient.builder()
            .username(user)
            .password(pass)
            .client(httpClient.build())
            .onTwoFactor(twoFactorHandler)
            .onChallenge(challengeHandler)
            .login()
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
                    // ignore invalid session
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val tv = TextView(requireContext()).apply {
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

    private suspend fun commentFlareAccounts(client: IGClient, count: Int): Boolean {
        withContext(Dispatchers.Main) {
            appendLog(">>> Commenting flare accounts", animate = true)
        }
        for (username in flareTargets.shuffled().take(count)) {
            withContext(Dispatchers.Main) { appendLog("> flare @$username", animate = true) }
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
                    var commented = false
                    var skippedNoText = 0
                    var failed = 0
                    for (item in candidates) {
                        val id = item.id
                        val code = item.code
                        withContext(Dispatchers.Main) {
                            appendLog("> commenting [sc=$code, id=$id]", animate = true)
                        }
                        if (flareCommentedIds.contains(code)) {
                            withContext(Dispatchers.Main) { appendLog("> skip [$code] - already commented") }
                            continue
                        }
                        val captionText = item.caption?.text ?: ""
                        Log.d("InstagramToolsFragment", "Candidate $code caption: ${captionText.take(40)}")
                        // Generate a friendly and supportive comment for the post caption
                        // using OpenAI with a 15-word limit
                        val aiComment = withContext(Dispatchers.IO) { fetchAiComment(captionText) }
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
                            val (ok, err) = commentPostNative(code, text)
                            if (!ok) {
                                val msg = err?.let { "[$code] $it" } ?: "[$code]"
                                withContext(Dispatchers.Main) { appendLog("> failed commenting $msg") }
                                return false
                            }
                            withContext(Dispatchers.Main) {
                                appendLog("> commented [sc=$code, id=$id]", animate = true)
                            }
                            flareCommentedIds.add(code)
                            val prefs = requireContext().getSharedPreferences("flare_commented", Context.MODE_PRIVATE)
                            prefs.edit().putStringSet("ids", flareCommentedIds).apply()
                            commented = true
                            break
                        } catch (e: Exception) {
                            failed++
                            withContext(Dispatchers.Main) { appendLog("Error commenting [$code]: ${e.message}") }
                        }
                    }
                    if (!commented) {
                        val info = "skipped $skippedNoText, failed $failed"
                        withContext(Dispatchers.Main) {
                            appendLog("> all recent posts already commented or no text ($info)", animate = true)
                        }
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
        return true
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
                    likeFlareAccounts(client, 5)
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
                            val prefs = requireContext().getSharedPreferences("liked", Context.MODE_PRIVATE)
                            prefs.edit().putStringSet("ids", likedIds).apply()
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
                    appendLog("> commenting [sc=$code, id=$id]", animate = true)
                    val proceed = commentFlareAccounts(client, 5)
                    if (!proceed) break
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
                        val (ok, err) = commentPostNative(code, text)
                        if (!ok) {
                            val msg = err?.let { "[$code] $it" } ?: "[$code]"
                            appendLog("> failed commenting $msg")
                            break
                        }
                        appendLog(
                            "> commented on [sc=$code, id=$id]",
                            animate = true
                        )
                        commented++
                        commentedIds.add(code)
                        val prefs = requireContext().getSharedPreferences("commented", Context.MODE_PRIVATE)
                        prefs.edit().putStringSet("ids", commentedIds).apply()
                    } catch (e: Exception) {
                        appendLog("Error commenting: ${e.message}")
                    }
                    delay(randomCommentDelayMs())
                    scrollRandomFlareFeed(client)
                    delay(randomCommentDelayMs())
                }
                appendLog(
                    ">>> Comment routine finished. $commented posts commented.",
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
                        val prefs = requireContext().getSharedPreferences("reposted", Context.MODE_PRIVATE)
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

    private fun fetchAiComment(caption: String): String? {
        val apiKey = BuildConfig.OPENAI_API_KEY.ifBlank {
            System.getenv("OPENAI_API_KEY") ?: ""
        }
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
                    appendLog("> OpenAI request failed: ${resp.code} ${bodyStr?.take(80)}")
                    Log.d(
                        "InstagramToolsFragment",
                        "OpenAI request failed: ${resp.code} body: $bodyStr"
                    )
                    return null
                }
                Log.d("InstagramToolsFragment", "OpenAI raw response: ${bodyStr?.take(60)}")
                val obj = JSONObject(bodyStr ?: "{}")
                val text = obj.getJSONArray("choices")
                    .optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                text?.let { limitWords(it, 15) }
            }
        } catch (e: Exception) {
            val details = e.stackTraceToString()
            appendLog("> OpenAI call error: ${e.javaClass.simpleName}: ${e.message}\n$details")
            Log.e("InstagramToolsFragment", "OpenAI call error", e)
            null
        }
    }

    /**
     * Opens the Instagram post for the given shortcode and injects the provided
     * comment text using the accessibility service.
     */
    private suspend fun commentPostNative(shortcode: String, text: String): Pair<Boolean, String?> {
        val uri = Uri.parse("https://www.instagram.com/p/$shortcode/")
        val context = requireContext()
        if (!AccessibilityUtils.isServiceEnabled(context, InstagramCommentService::class.java)) {
            withContext(Dispatchers.Main) {
                startActivity(Intent(context, AiCommentCheckActivity::class.java))
            }
            return false to "service disabled"
        }
        val pm = context.packageManager
        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.instagram.android")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val canUseApp = appIntent.resolveActivity(pm) != null
        withContext(Dispatchers.Main) {
            if (canUseApp) {
                startActivity(appIntent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                Toast.makeText(
                    context,
                    "Instagram app not found, opening in browser",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        if (!canUseApp) return false to "instagram app missing"

        delay(10000)

        val result = withTimeoutOrNull(60000) {
            suspendCancellableCoroutine<Pair<Boolean, String?>> { cont ->
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action == MainActivity.ACTION_COMMENT_RESULT) {
                            ctx?.unregisterReceiver(this)
                            val success = intent.getBooleanExtra(
                                MainActivity.EXTRA_COMMENT_SUCCESS,
                                false
                            )
                            val error = intent.getStringExtra(MainActivity.EXTRA_COMMENT_ERROR)
                            if (cont.isActive) cont.resume(success to error) {}
                        }
                    }
                }
                context.registerReceiver(
                    receiver,
                    android.content.IntentFilter(MainActivity.ACTION_COMMENT_RESULT)
                )
                val intent = Intent(MainActivity.ACTION_INPUT_COMMENT).apply {
                    putExtra(MainActivity.EXTRA_COMMENT, text)
                }
                context.sendBroadcast(intent)
                cont.invokeOnCancellation { context.unregisterReceiver(receiver) }
            }
        } ?: (false to "timeout")

        delay(2000)
        val back = pm.getLaunchIntentForPackage(context.packageName)
        back?.let { withContext(Dispatchers.Main) { startActivity(it) } }

        return result
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
                        profileContainer.visibility = View.GONE
                        loginContainer.visibility = View.VISIBLE
                        currentUsername = null
                        clientFile.delete()
                        cookieFile.delete()
                    }
                }
                true
            }
            R.id.action_check_ai -> {
                startActivity(Intent(requireContext(), com.cicero.socialtools.ui.AiCommentCheckActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
