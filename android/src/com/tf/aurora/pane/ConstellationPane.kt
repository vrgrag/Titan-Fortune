package com.tf.aurora.pane

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.tf.aurora.mask.Face
import com.tf.aurora.net.Ledger
import com.tf.aurora.net.PulseCheck

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
    val lostHold = remember { arrayOf(onLost) }
    lostHold[0] = onLost
    var appliedHref by remember { mutableStateOf(href) }
    val stepBack = remember { arrayOf({}) }

    BackHandler(true) { stepBack[0]() }

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val safe = WindowInsets.displayCutout.only(
        if (landscape) WindowInsetsSides.Horizontal else WindowInsetsSides.Top
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(ComposeColor.Black)
            .windowInsetsPadding(safe)
            .consumeWindowInsets(safe)
    ) {
    AndroidView(
        factory = { ctx ->
            val shell = FrameLayout(ctx).apply {
                setBackgroundColor(Color.BLACK)
                fitsSystemWindows = false
                clipChildren = false
                clipToPadding = false
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
                fitsSystemWindows = false
                setBackgroundColor(Color.BLACK)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    textZoom = 100
                    setSupportMultipleWindows(true)
                    userAgentString = Face.line()
                }
            }
            var deepest = href
            var settled = href
            var landing: String? = null
            var hops = 0
            var chainSettled = false
            var veil: FrameLayout? = null
            var loadGen = 0
            var unveiling = false
            var hideSeq = 0
            var maxArmed = false

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

            fun immerse() {
                val act = ctx as? Activity ?: return
                val bars = WindowInsetsControllerCompat(act.window, act.window.decorView)
                bars.hide(WindowInsetsCompat.Type.systemBars())
                bars.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            fun hideVeil() {
                hideSeq++
                veil?.let { runCatching { shell.removeView(it) } }
                veil = null
                unveiling = false
            }

            fun showVeil() {
                hideSeq++
                unveiling = false
                immerse()
                if (!maxArmed) {
                    maxArmed = true
                    web.postDelayed({
                        if (veil != null) {
                            chainSettled = true
                            hideVeil()
                        }
                    }, 15_000)
                }
                veil?.let {
                    it.bringToFront()
                    return
                }
                val dip = ctx.resources.displayMetrics.density
                val size = (22f * dip).toInt()
                val frame = FrameLayout(ctx).apply {
                    setBackgroundColor(Color.BLACK)
                    isClickable = true
                    isFocusable = true
                    fitsSystemWindows = false
                    elevation = 24f
                }
                val spin = ProgressBar(ctx, null, android.R.attr.progressBarStyleSmallInverse).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
                }
                frame.addView(spin, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
                shell.addView(
                    frame,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                veil = frame
            }

            /** Only the landing -> second page hop is covered; the first load and everything after it are not. */
            fun maybeVeil(url: String) {
                if (chainSettled || landing == null) return
                if (url.isEmpty() || url == "about:blank") return
                showVeil()
            }

            fun absHref(raw: String): String {
                if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
                val base = web.url ?: settled
                return runCatching { java.net.URL(java.net.URL(base), raw).toString() }.getOrDefault(raw)
            }

            fun pollReady(gen: Int, tries: Int) {
                if (gen != loadGen) {
                    unveiling = false
                    return
                }
                if (veil == null) {
                    unveiling = false
                    return
                }
                if (tries >= 80) {
                    hideVeil()
                    return
                }
                web.evaluateJavascript(PAINTED) { result ->
                    val ok = result == "1" || result == "\"1\""
                    if (ok) {
                        web.post { if (gen == loadGen) hideVeil() }
                    } else {
                        web.postDelayed({ pollReady(gen, tries + 1) }, 80)
                    }
                }
            }

            /** A hop's script fires after its own load ends, so wait a beat before calling the chain done. */
            fun armHide() {
                if (chainSettled) {
                    hideVeil()
                    return
                }
                val seq = ++hideSeq
                val gen = loadGen
                web.postDelayed({
                    if (seq != hideSeq || gen != loadGen) return@postDelayed
                    chainSettled = true
                    if (veil == null) return@postDelayed
                    unveiling = true
                    pollReady(gen, 0)
                }, 700)
            }

            fun frost() {
                web.evaluateJavascript(FROST, null)
                web.evaluateJavascript(pan.sheet, null)
            }

            fun takeUrl(view: WebView, raw: String, popup: Boolean = false): Boolean {
                if (!PulseCheck.alive(ctx)) {
                    lostHold[0]()
                    return true
                }
                val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return true
                val scheme = uri.scheme.orEmpty().lowercase()
                if (scheme == "about" || scheme == "data" || scheme == "blob" ||
                    scheme == "file" || scheme == "javascript"
                ) {
                    return false
                }
                val http = scheme == "http" || scheme == "https" || scheme.isEmpty()
                if (http) {
                    var dest = if (scheme.isEmpty()) absHref(raw) else raw
                    dest = Ledger.https(dest)
                    val destScheme = runCatching { Uri.parse(dest).scheme }.getOrNull().orEmpty().lowercase()
                    if (destScheme != "https") return true
                    deepest = dest
                    maybeVeil(dest)
                    if (scheme == "http" || popup || view !== web) {
                        web.loadUrl(dest)
                        return true
                    }
                    return false
                }
                if (scheme == "intent") {
                    openOutsideIntent(ctx, web, raw)
                    return true
                }
                openOutside(ctx, raw)
                return true
            }

            fun stepTowardHome() {
                hideVeil()
                val home = landing
                val current = web.url.orEmpty()
                if (current.isEmpty() || current == "about:blank") return
                if (home != null && samePane(current, home)) return
                val list = web.copyBackForwardList()
                val idx = list.currentIndex
                var homeIdx = -1
                if (home != null) {
                    for (i in 0 until list.size) {
                        val item = list.getItemAtIndex(i)?.url ?: continue
                        if (samePane(item, home)) {
                            homeIdx = i
                            break
                        }
                    }
                }
                if (homeIdx >= 0 && idx > 0 && idx - 1 < homeIdx) {
                    web.goBackOrForward(homeIdx - idx)
                    return
                }
                if (web.canGoBack()) {
                    web.goBack()
                    return
                }
                if (home != null && !samePane(current, home)) web.loadUrl(home)
            }
            stepBack[0] = { stepTowardHome() }

            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.isForMainFrame && request.hasGesture() && chainSettled) {
                        hideVeil()
                    }
                    return takeUrl(view, request.url.toString())
                }

                @Deprecated("WebView still calls this on some OEM builds")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return takeUrl(view, url)
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (!PulseCheck.alive(ctx)) {
                        view.stopLoading()
                        hideVeil()
                        lostHold[0]()
                        return
                    }
                    loadGen++
                    unveiling = false
                    if (url != "about:blank") deepest = url
                    pan.wipe()
                    maybeVeil(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "about:blank") return
                    settled = url
                    deepest = url
                    hops = 0
                    CookieManager.getInstance().flush()
                    frost()
                    if (landing == null) landing = url else armHide()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (!request.isForMainFrame) return
                    val code = error.errorCode
                    if (code == ERROR_UNSUPPORTED_SCHEME || code == ERROR_BAD_URL) {
                        hideVeil()
                        return
                    }
                    val failed = request.url.toString()
                    if (failed.startsWith("http://", ignoreCase = true)) {
                        view.loadUrl(Ledger.https(failed))
                        return
                    }
                    val lost = code == ERROR_HOST_LOOKUP ||
                        code == ERROR_CONNECT ||
                        code == ERROR_TIMEOUT ||
                        code == ERROR_IO ||
                        code == ERROR_PROXY_AUTHENTICATION ||
                        code == ERROR_FAILED_SSL_HANDSHAKE ||
                        !PulseCheck.alive(ctx)
                    if (lost) {
                        hideVeil()
                        lostHold[0]()
                        return
                    }
                    if (code == ERROR_REDIRECT_LOOP && hops < 6) {
                        hops++
                        view.loadUrl(deepest.ifBlank { settled })
                        return
                    }
                    hideVeil()
                }
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) = Unit

                override fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) = Unit

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 100 && chainSettled) hideVeil()
                }

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
                    if (isUserGesture && chainSettled) hideVeil()
                    val msg = resultMsg ?: return true
                    val trampoline = WebView(ctx)
                    trampoline.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            takeUrl(web, request.url.toString(), popup = true)
                            return true
                        }

                        @Deprecated("WebView still calls this on some OEM builds")
                        override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean {
                            takeUrl(web, url, popup = true)
                            return true
                        }
                    }
                    (msg.obj as? WebView.WebViewTransport)?.webView = trampoline
                    msg.sendToTarget()
                    return true
                }
            }
            pan.bind(web)
            shell.addView(web)
            web.settings.userAgentString = Face.line()
            web.loadUrl(Ledger.https(href))
            shell
        },
        update = { shell ->
            if (href.isBlank() || href == appliedHref) return@AndroidView
            val view = (0 until shell.childCount)
                .map { shell.getChildAt(it) }
                .filterIsInstance<WebView>()
                .firstOrNull() ?: return@AndroidView
            appliedHref = href
            view.loadUrl(Ledger.https(href))
        },
        modifier = Modifier.fillMaxSize()
    )
    }
}

private fun openOutside(ctx: Context, raw: String) {
    val intent = runCatching {
        Intent(Intent.ACTION_VIEW, Uri.parse(raw)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull() ?: return
    runCatching { ctx.startActivity(intent) }
}

private fun openOutsideIntent(ctx: Context, view: WebView, raw: String) {
    val parsed = runCatching {
        Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
    }.getOrNull() ?: return
    parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    parsed.addCategory(Intent.CATEGORY_BROWSABLE)
    parsed.component = null
    parsed.selector = null
    if (runCatching { ctx.startActivity(parsed) }.isSuccess) return
    parsed.`package` = null
    if (runCatching { ctx.startActivity(parsed) }.isSuccess) return
    val fallback = parsed.getStringExtra("browser_fallback_url")
        ?: parsed.getStringExtra("S.browser_fallback_url")
    if (!fallback.isNullOrBlank() &&
        (fallback.startsWith("http://") || fallback.startsWith("https://"))
    ) {
        view.loadUrl(fallback)
    }
}

private val PAINTED = """
(function(){
  if(document.readyState!=='complete') return 0;
  var b=document.body;
  if(!b) return 0;
  var root=document.getElementById('app')||document.getElementById('root')||b;
  if(root.childElementCount<1) return 0;
  var h=Math.max(b.scrollHeight||0, document.documentElement.scrollHeight||0);
  if(h<120) return 0;
  var frames=b.querySelectorAll('iframe');
  if(frames.length){
    var framed=false;
    for(var i=0;i<frames.length;i++){
      if(frames[i].offsetHeight>=80) framed=true;
    }
    if(!framed) return 0;
  }
  var live=b.querySelectorAll('img,canvas,iframe,form,input,button,a,video,svg').length;
  var text=(b.innerText||'').replace(/\s+/g,'');
  if(live<1 && text.length<12) return 0;
  return 1;
})();
""".trimIndent()

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
  function kbOpen(){
    return !!(window.visualViewport && window.innerHeight &&
      window.visualViewport.height < window.innerHeight * 0.75);
  }
  function paint(){
    if(kbOpen()) return;
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
