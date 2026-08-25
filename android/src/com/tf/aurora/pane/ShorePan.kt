package com.tf.aurora.pane

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Slides the WebView over the IME. Size stays locked; cutout padding is the safe zone. */
class ShorePan(private val host: View) {
    private var web: WebView? = null
    private var fieldTop = -1f
    private var fieldBottom = -1f
    private var framed = false
    private var kb = 0
    private var riding = false
    private var declared = 0
    private var settled = 0

    private val ping = Runnable {
        web?.evaluateJavascript("window.__hvKbPing&&window.__hvKbPing();", null)
    }

    private val layoutWatch = ViewTreeObserver.OnGlobalLayoutListener {
        if (!riding) adopt(measureKb(ViewCompat.getRootWindowInsets(host)))
    }

    val imeUp: Boolean get() = riding || kb > 0
    val sheet: String get() = SCRIPT

    fun install() {
        ViewCompat.setOnApplyWindowInsetsListener(host) { view, insets ->
            padCutout(view, insets)
            if (!riding) adopt(measureKb(insets))
            stripIme(insets)
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            host,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) riding = true
                }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        declared = bounds.upperBound.bottom
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    running: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (riding) {
                        rise(measureKb(insets))
                        slide()
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    riding = false
                    declared = 0
                    adopt(measureKb(ViewCompat.getRootWindowInsets(host)))
                    if (kb > 0) ask(120L) else slide()
                }
            }
        )
        host.viewTreeObserver.addOnGlobalLayoutListener(layoutWatch)
        ViewCompat.requestApplyInsets(host)
    }

    fun bind(view: WebView) {
        web = view
        wipe()
        view.translationY = 0f
        view.addJavascriptInterface(FocusBridge(), BRIDGE)
    }

    fun wipe() {
        host.removeCallbacks(ping)
        fieldTop = -1f
        fieldBottom = -1f
        framed = false
        slide()
    }

    fun afterTurn() {
        wipe()
        settled = 0
        kb = 0
        web?.translationY = 0f
        ViewCompat.requestApplyInsets(host)
        if (measureKb(ViewCompat.getRootWindowInsets(host)) > 0) ask(160L)
    }

    private fun ask(delay: Long) {
        host.removeCallbacks(ping)
        host.postDelayed(ping, delay)
    }

    private fun adopt(height: Int) {
        if (height == kb) {
            if (height > 0) slide()
            return
        }
        val slack = (8f * host.resources.displayMetrics.density).toInt()
        if (kb > 0 && height > 0 && abs(height - kb) < slack) return
        kb = height
        if (height > 0) settled = height
        slide()
        if (kb > 0) ask(80L)
    }

    private fun rise(height: Int) {
        val rest = if (settled > 0) settled else declared
        kb = if (rest > 0) min(height, rest) else height
    }

    private fun measureKb(insets: WindowInsetsCompat?): Int {
        val fromIme = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        if (fromIme > 0) return fromIme
        val frame = Rect()
        host.getWindowVisibleDisplayFrame(frame)
        val gap = host.rootView.height - frame.bottom
        val floor = (68f * host.resources.displayMetrics.density).toInt()
        return if (gap >= floor) gap else 0
    }

    private fun padCutout(view: View, insets: WindowInsetsCompat) {
        if (imeUp) return
        val raw = insets.toWindowInsets()?.displayCutout
        val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val left = maxOf(cut.left, raw?.safeInsetLeft ?: 0)
        val top = maxOf(cut.top, raw?.safeInsetTop ?: 0)
        val right = maxOf(cut.right, raw?.safeInsetRight ?: 0)
        val bottom = maxOf(cut.bottom, raw?.safeInsetBottom ?: 0)
        if (
            view.paddingLeft == left &&
            view.paddingTop == top &&
            view.paddingRight == right &&
            view.paddingBottom == bottom
        ) return
        view.setPadding(left, top, right, bottom)
    }

    private fun stripIme(insets: WindowInsetsCompat): WindowInsetsCompat =
        runCatching {
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
        }.getOrDefault(insets)

    private fun slide() {
        val view = web ?: return
        val target = -shift(view)
        if (abs(view.translationY - target) < 0.5f) return
        view.animate().cancel()
        view.translationY = target
    }

    private fun shift(view: View): Float {
        val height = kb
        val span = view.height
        if (height <= 0 || span <= 0 || fieldBottom < 0f) return 0f
        val aim: Float
        val cap: Float
        if (framed) {
            aim = fieldBottom
            cap = min(height.toFloat(), max(0f, fieldTop))
        } else {
            aim = min(fieldBottom, fieldTop + 92f * view.resources.displayMetrics.density)
            cap = height.toFloat()
        }
        return (aim - (span - height)).coerceIn(0f, cap)
    }

    private inner class FocusBridge {
        @JavascriptInterface
        fun mark(frame: Boolean, top: Double, bottom: Double) {
            val view = web ?: return
            view.post {
                val nextTop = top.toFloat()
                val nextBot = bottom.toFloat()
                val same =
                    framed == frame &&
                        abs(fieldTop - nextTop) < 2f &&
                        abs(fieldBottom - nextBot) < 2f
                framed = frame
                fieldTop = nextTop
                fieldBottom = nextBot
                if (kb > 0 && !same) slide()
            }
        }
    }

    companion object {
        const val BRIDGE = "HvFocus"
        private val SCRIPT = """
        (function(){
          if(window.__hvKbHook) return; window.__hvKbHook=1;
          var meta=document.querySelector('meta[name=viewport]');
          if(meta){
            var content=meta.getAttribute('content')||'';
            if(content.indexOf('interactive-widget')<0){
              meta.setAttribute('content', content + (content?',':'') + 'interactive-widget=overlays-content');
            }
          }
          var SKIP={checkbox:1,radio:1,button:1,submit:1,reset:1,file:1,range:1,hidden:1};
          function isField(node){
            if(!node) return false;
            var tag=node.tagName;
            if(tag==='INPUT') return !SKIP[(node.type||'text').toLowerCase()];
            return tag==='TEXTAREA'||node.isContentEditable===true;
          }
          function caretBox(node, win){
            if(node.isContentEditable){
              try{
                var sel=win.getSelection();
                if(sel&&sel.rangeCount){
                  var box=sel.getRangeAt(0).getBoundingClientRect();
                  if(box&&box.height>0) return box;
                }
              }catch(err){}
            }
            return node.getBoundingClientRect();
          }
          function locate(){
            var node=document.activeElement, win=window, lift=0, hops=0;
            while(node&&(node.tagName==='IFRAME'||node.tagName==='FRAME')&&hops++<4){
              var outer=node.getBoundingClientRect(), innerDoc=null;
              try{innerDoc=node.contentDocument;}catch(err){innerDoc=null;}
              var inner=innerDoc?innerDoc.activeElement:null;
              if(!inner||inner===innerDoc.body){
                return {framed:true,top:lift+outer.top,bot:lift+outer.bottom};
              }
              bindDoc(innerDoc);
              win=node.contentWindow||win;
              lift+=outer.top;
              node=inner;
            }
            if(!isField(node)) return null;
            var rect=caretBox(node,win);
            return {framed:false,top:lift+rect.top,bot:lift+rect.bottom};
          }
          function pulse(){
            var spot=locate();
            if(!spot) return;
            var px=window.devicePixelRatio||1;
            try{ HvFocus.mark(spot.framed,spot.top*px,(spot.bot+10)*px); }catch(err){}
          }
          window.__hvKbPing=pulse;
          function bindDoc(doc){
            try{
              if(!doc||doc.__hvKbDoc) return;
              doc.__hvKbDoc=1;
              doc.addEventListener('focusin', function(ev){
                if(!isField(ev.target)) return;
                pulse();
              }, true);
            }catch(err){}
          }
          bindDoc(document);
        })();
        """.trimIndent()
    }
}
