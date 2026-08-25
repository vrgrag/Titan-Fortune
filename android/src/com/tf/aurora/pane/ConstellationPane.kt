package com.tf.aurora.pane

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tf.aurora.mask.Face
import com.tf.aurora.net.PulseCheck
import com.tf.aurora.skin.LostPlate

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConstellationPane(
    href: String,
    onLost: () -> Unit
) {
    var filePath by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        filePath?.onReceiveValue(uris.toTypedArray())
        filePath = null
    }
    var host by remember { mutableStateOf<WebView?>(null) }
    var showLost by remember { mutableStateOf(false) }

    BackHandler(true) {
        val w = host
        if (w != null && w.canGoBack()) w.goBack()
    }

    if (showLost) {
        LostPlate {
            showLost = false
            host?.reload()
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            val shell = FrameLayout(ctx).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val pan = ShorePan(shell)
            pan.install()
            val web = WebView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    textZoom = 100
                    setSupportMultipleWindows(false)
                    userAgentString = Face.line()
                }
            }
            var deepest = href
            var settled = href
            var landing: String? = null
            var hops = 0
            var lastFinish = 0L
            var hopSpent = false
            var veil: FrameLayout? = null

            fun samePane(a: String, b: String): Boolean {
                fun strip(raw: String): String {
                    var s = raw
                    val hash = s.indexOf('#')
                    if (hash >= 0) s = s.substring(0, hash)
                    if (s.endsWith('/') && s.count { it == '/' } > 2) s = s.dropLast(1)
                    return s
                }
                return strip(a) == strip(b)
            }

            fun hideVeil() {
                veil?.let { runCatching { shell.removeView(it) } }
                veil = null
            }

            fun showVeil() {
                if (veil != null) return
                val frame = FrameLayout(ctx).apply {
                    setBackgroundColor(Color.BLACK)
                    isClickable = true
                }
                val spin = ProgressBar(ctx).apply { isIndeterminate = true }
                frame.addView(
                    spin,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
                shell.addView(
                    frame,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                veil = frame
            }

            fun maybeVeil(url: String) {
                val home = landing ?: return
                if (url.isEmpty() || url == "about:blank") return
                if (hopSpent || samePane(url, home)) return
                if (SystemClock.elapsedRealtime() - lastFinish < 700L) return
                hopSpent = true
                showVeil()
            }

            fun frost() {
                web.evaluateJavascript(FROST, null)
                web.evaluateJavascript(pan.sheet, null)
            }

            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val scheme = uri.scheme.orEmpty().lowercase()
                    if (scheme == "http" || scheme == "https") {
                        if (request.isForMainFrame) {
                            deepest = uri.toString()
                            maybeVeil(deepest)
                        }
                        return false
                    }
                    return handOff(view, uri)
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (url != "about:blank") deepest = url
                    pan.wipe()
                    maybeVeil(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "about:blank") return
                    settled = url
                    deepest = url
                    hops = 0
                    lastFinish = SystemClock.elapsedRealtime()
                    if (landing == null) landing = url
                    if (landing != null && samePane(url, landing!!)) hopSpent = false
                    CookieManager.getInstance().flush()
                    frost()
                    hideVeil()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (!request.isForMainFrame) return
                    val lost = error.errorCode == ERROR_HOST_LOOKUP ||
                        error.errorCode == ERROR_CONNECT ||
                        error.errorCode == ERROR_TIMEOUT
                    if (lost && !PulseCheck.alive(ctx)) {
                        hideVeil()
                        showLost = true
                        return
                    }
                    if (error.errorCode == ERROR_REDIRECT_LOOP && hops < 6) {
                        hops++
                        view.loadUrl(deepest.ifBlank { settled })
                        return
                    }
                    hideVeil()
                    handOff(view, request.url)
                }
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
                ): Boolean {
                    filePath?.onReceiveValue(null)
                    filePath = callback
                    pick.launch(params?.acceptTypes?.firstOrNull()?.ifBlank { "*/*" } ?: "*/*")
                    return true
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val extra = view?.hitTestResult?.extra
                    if (!extra.isNullOrBlank()) view.loadUrl(extra)
                    return false
                }
            }
            pan.bind(web)
            shell.addView(web)
            host = web
            web.settings.userAgentString = Face.line()
            web.loadUrl(href)
            shell
        },
        update = { shell ->
            val view = (0 until shell.childCount)
                .map { shell.getChildAt(it) }
                .filterIsInstance<WebView>()
                .firstOrNull()
            if (view != null && view.url != href && href.isNotBlank()) {
                view.loadUrl(href)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun handOff(view: WebView, uri: Uri): Boolean {
    return try {
        view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        true
    }
}

private val FROST = """
(function(){
  if(window.__hvFrostBound) return; window.__hvFrostBound=1;
  var ID='hv-frost';
  var CSS=':root{' +
    '--hv-inset-t:0px;--hv-inset-r:0px;--hv-inset-b:0px;--hv-inset-l:0px;' +
    '--safe-area-inset-top:0px!important;--safe-area-inset-right:0px!important;' +
    '--safe-area-inset-bottom:0px!important;--safe-area-inset-left:0px!important;' +
    '--sat:0px!important;--sar:0px!important;--sab:0px!important;--sal:0px!important;' +
    '--safe-top:0px!important;--safe-bottom:0px!important;' +
    '--safe-left:0px!important;--safe-right:0px!important;' +
  '}' +
  '.gameview-mobile-header,.app-header,.js-safe-top{' +
    'padding-top:0!important;margin-top:0!important;' +
  '}';
  function paint(){
    var head=document.head||document.documentElement; if(!head) return;
    var meta=document.querySelector('meta[name="viewport"]');
    if(!meta){
      meta=document.createElement('meta');
      meta.setAttribute('name','viewport');
      meta.setAttribute('content','width=device-width, initial-scale=1, viewport-fit=contain');
      head.appendChild(meta);
    }else if(!/viewport-fit\s*=\s*contain/i.test(meta.getAttribute('content')||'')){
      var c=(meta.getAttribute('content')||'').replace(/,?\s*viewport-fit\s*=\s*\w+/ig,'').trim();
      meta.setAttribute('content', c+(c?', ':'')+'viewport-fit=contain');
    }
    var sheet=document.getElementById(ID);
    if(!sheet){
      sheet=document.createElement('style');
      sheet.id=ID;
      head.appendChild(sheet);
    }
    if(sheet.textContent!==CSS) sheet.textContent=CSS;
    if(head.lastElementChild!==sheet) head.appendChild(sheet);
  }
  paint();
  ['pushState','replaceState'].forEach(function(fn){
    var orig=history[fn];
    history[fn]=function(){
      var out=orig.apply(this,arguments);
      setTimeout(paint,80);
      setTimeout(paint,400);
      return out;
    };
  });
  window.addEventListener('popstate', function(){ setTimeout(paint,80); });
})();
""".trimIndent()
