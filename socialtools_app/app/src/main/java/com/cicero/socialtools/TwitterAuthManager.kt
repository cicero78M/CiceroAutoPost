package com.cicero.socialtools

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import twitter4j.auth.AccessToken
import twitter4j.auth.RequestToken
import twitter4j.conf.ConfigurationBuilder
import twitter4j.TwitterFactory

object TwitterAuthManager {

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            "twitter",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveRequestToken(context: Context, token: RequestToken) {
        prefs(context).edit(commit = true) {
            putString("request_token", token.token)
            putString("request_secret", token.tokenSecret)
        }
    }

    fun loadRequestToken(context: Context): RequestToken? {
        val p = prefs(context)
        val token = p.getString("request_token", null)
        val secret = p.getString("request_secret", null)
        return if (token != null && secret != null) RequestToken(token, secret) else null
    }

    fun clearRequestToken(context: Context) {
        prefs(context).edit(commit = true) {
            remove("request_token")
            remove("request_secret")
        }
    }

    fun saveAccessToken(context: Context, token: AccessToken) {
        prefs(context).edit(commit = true) {
            putString("token", token.token)
            putString("secret", token.tokenSecret)
        }
    }

    fun loadAccessToken(context: Context): Pair<String, String>? {
        val p = prefs(context)
        val token = p.getString("token", null)
        val secret = p.getString("secret", null)
        return if (token != null && secret != null) token to secret else null
    }

    fun clearTokens(context: Context) {
        prefs(context).edit(commit = true) { clear() }
    }

    fun saveLastResponse(context: Context, response: String) {
        prefs(context).edit(commit = true) { putString("last_response", response) }
    }

    fun loadLastResponse(context: Context): String? {
        return prefs(context).getString("last_response", null)
    }

    fun finishAuth(context: Context, verifier: String): String {
        return try {
            val reqToken = loadRequestToken(context) ?: return "Missing request token"
            val config = ConfigurationBuilder()
                .setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY)
                .setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET)
                .build()
            val twitter = TwitterFactory(config).instance
            val access = twitter.getOAuthAccessToken(reqToken, verifier)
            saveAccessToken(context, access)
            clearRequestToken(context)
            val user = twitter.verifyCredentials()
            val response = "token=${'$'}{access.token}\nsecret=${'$'}{access.tokenSecret}\nuser=@${'$'}{user.screenName}"
            saveLastResponse(context, response)
            response
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            saveLastResponse(context, msg)
            msg
        }
    }
}
