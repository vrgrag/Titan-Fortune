package com.titanfortune.game

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

object LegalPages {
    const val PRIVACY_URL = "https://titanfortune.store/privacy-policy.html"
    const val SUPPORT_URL = "https://titanfortune.store/support.html"
    const val PRIVACY_ASSET = "file:///android_asset/web/privacy-policy.html"
    const val SUPPORT_ASSET = "file:///android_asset/web/support.html"
}

/**
 * In-app WebView for Privacy Policy and Support.
 * Tries the official URL first, then falls back to the bundled local HTML.
 * Shared by all three product flavors.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegalWebView(
    title: String,
    remoteUrl: String,
    localUrl: String,
    back: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var usingLocal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Night)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TopBar(title, if (usingLocal) "LOCAL COPY" else "titanfortune.store", back)
        }
        Box(Modifier.fillMaxSize().background(Color.White)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.WHITE)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            // Keep the legal pages inside this WebView.
                            setSupportMultipleWindows(false)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = false

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                view.evaluateJavascript(WHITE_BG_JS, null)
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame && !usingLocal) {
                                    usingLocal = true
                                    loading = true
                                    view.loadUrl(localUrl)
                                }
                            }
                        }
                        loadUrl(remoteUrl)
                    }
                },
                modifier = Modifier.fillMaxSize().background(Color.White)
            )
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = DivineGold,
                    trackColor = Color(0xFFE8E8E8)
                )
            }
            if (usingLocal && !loading) {
                Text(
                    "Showing the local copy",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                )
            }
        }
    }
}

private const val WHITE_BG_JS =
    "document.documentElement.style.background='#ffffff';" +
        "if(document.body){document.body.style.background='#ffffff';}"
