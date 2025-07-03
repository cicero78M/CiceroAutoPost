package com.cicero.socialtools.features.instagram

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cicero.socialtools.BuildConfig
import com.cicero.socialtools.R

class InstaOauthLoginFragment : Fragment(R.layout.fragment_insta_oauth_login) {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val webView = view.findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true

        val clientId = BuildConfig.IG_APP_ID
        val redirectUri = BuildConfig.IG_REDIRECT_URI
        val authUrl = "https://api.instagram.com/oauth/authorize" +
            "?client_id=$clientId&redirect_uri=$redirectUri&scope=user_profile" +
            "&response_type=token"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && url.startsWith(redirectUri)) {
                    val token = Uri.parse(url).fragment?.substringAfter("access_token=")
                    if (!token.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), "Token: $token", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                return false
            }
        }

        webView.loadUrl(authUrl)
    }
}
