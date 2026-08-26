package com.tf.aurora.track

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLink
import com.appsflyer.deeplink.DeepLinkResult
import com.tf.aurora.mask.Face
import com.tf.aurora.mask.Relic
import com.tf.aurora.mask.Trace
import com.tf.aurora.net.Ledger
import com.titanfortune.game.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class BeaconHub {
    @Volatile var lastConv: Map<String, Any?> = emptyMap()
        private set
    @Volatile var lastBody: JSONObject = JSONObject()
    @Volatile var gcdOk: Boolean = false
        private set

    private lateinit var app: Application
    @Volatile private var settled: Map<String, Any?>? = null
    @Volatile private var convWait = CompletableDeferred<Map<String, Any?>>()
    @Volatile private var linkWait = CompletableDeferred<Unit>()
    private val started = AtomicBoolean(false)
    private val bag = LinkedHashMap<String, Any?>()
    private var retraced = false
    @Volatile private var udlFound = false
    @Volatile private var inboundTap = false

    fun attach(host: Application) {
        app = host
        val af = AppsFlyerLib.getInstance()
        af.setDebugLog(false)
        af.setOneLinkCustomDomain("titanfortune.onelink.me")
        af.subscribeForDeepLink { result ->
            if (result.status == DeepLinkResult.Status.FOUND) {
                udlFound = true
                runCatching { absorbLink(result.deepLink) }
                Trace.line("udl FOUND")
            } else {
                Trace.line("udl status=${result.status}")
            }
            if (!linkWait.isCompleted) linkWait.complete(Unit)
        }
        af.init(
            Relic.beacon(),
            object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                    val payload = data.orEmpty().toMap()
                    settle(payload)
                    Trace.conv("sdk", lastConv, uid(host))
                }

                override fun onConversionDataFail(error: String?) {
                    Trace.line("sdk fail error=${error.orEmpty()} keep-wait")
                }

                override fun onAppOpenAttribution(data: MutableMap<String, String>?) {
                    data?.forEach { (k, v) -> putBag(k, v, true) }
                }

                override fun onAttributionFailure(error: String?) = Unit
            },
            app
        )
    }

    fun noteLaunch(intent: Intent?) {
        val src = intent ?: return
        val host = src.data?.host.orEmpty().lowercase()
        if (host.contains("onelink.me") || host.contains("titanfortune")) inboundTap = true
        if (!src.getStringExtra("af_deeplink").isNullOrBlank()) inboundTap = true
        absorbUri(src.data, false)
        absorbBundle(src.extras, false)
        src.getStringExtra("af_deeplink")?.let { runCatching { absorbUri(Uri.parse(it), false) } }
        src.getStringExtra("link")?.let { runCatching { absorbUri(Uri.parse(it), false) } }
    }

    fun ignite(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        AppsFlyerLib.getInstance().start(activity)
    }

    fun relay(activity: Activity, intent: Intent?) {
        val target = intent ?: return
        runCatching { AppsFlyerLib.getInstance().performOnDeepLinking(target, activity) }
    }

    fun retrace(activity: Activity) {
        if (!started.get()) {
            ignite(activity)
            return
        }
        val last = settled ?: return
        if (last.isNotEmpty()) return
        settled = null
        lastConv = emptyMap()
        retraced = true
        convWait = CompletableDeferred()
        AppsFlyerLib.getInstance().start(activity)
        Trace.line("retrace")
    }

    fun hasAttribution(): Boolean = settled?.isNotEmpty() == true

    fun uid(ctx: android.content.Context): String =
        AppsFlyerLib.getInstance().getAppsFlyerUID(ctx).orEmpty()

    suspend fun assemble(ctx: android.content.Context, first: Boolean): JSONObject = coroutineScope {
        val budget = when {
            retraced -> 8_050L
            first -> 30_800L
            else -> 10_350L
        }
        val linkJob = async { withTimeoutOrNull(4_920L) { linkWait.await() } }
        val dataJob = async { withTimeoutOrNull(budget) { convWait.await() } ?: emptyMap() }
        linkJob.await()
        var install = Ledger.flatten(dataJob.await())
        if (install.isEmpty()) {
            Trace.line("assemble empty after wait first=$first retraced=$retraced udl=$udlFound inbound=$inboundTap")
        }
        val status = install["af_status"]?.toString().orEmpty()
        if (status.isEmpty() || status.equals("Organic", ignoreCase = true)) {
            install = Ledger.flatten(secondLook(install))
        }
        val deep = Ledger.flatten(snapshot())
        JSONObject().apply {
            install.forEach { (k, v) -> putAny(k, v) }
            deep.forEach { (k, v) ->
                val incoming = v?.toString().orEmpty()
                if (incoming.isEmpty() || incoming == "null") return@forEach
                val current = opt(k)?.toString().orEmpty()
                if (current.isEmpty() || current == "null") putAny(k, v)
            }
            paintStatus(this)
            lastBody = this
            Trace.line("body af_status=${opt("af_status")} af_id=${uid(ctx)} keys=${length()}")
        }
    }

    private fun paintStatus(body: JSONObject) {
        if (BuildConfig.DEBUG) applyInlet(body)
        val status = body.opt("af_status")?.toString().orEmpty()
        if (status.isNotEmpty() && !status.equals("null", ignoreCase = true)) return
        if (!udlFound && !inboundTap) return
        body.put("af_status", "Non-organic")
        val merged = LinkedHashMap(lastConv)
        merged["af_status"] = "Non-organic"
        lastConv = merged
        if (settled.isNullOrEmpty()) settled = merged
        Trace.line("fill Non-organic udl=$udlFound inbound=$inboundTap")
    }

    private fun applyInlet(body: JSONObject) {
        val forced = BuildConfig.INLET_STATUS.trim()
        if (forced.isEmpty()) return
        body.put("af_status", forced)
        body.put("is_first_launch", true)
        BuildConfig.INLET_PARAMS.split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@forEach
            val key = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (key.isEmpty() || value.isEmpty()) return@forEach
            when (key) {
                "pid" -> body.put("media_source", value)
                "c" -> body.put("campaign", value)
                else -> body.put(key, value)
            }
        }
        val merged = LinkedHashMap(lastConv)
        merged["af_status"] = forced
        lastConv = merged
        if (settled.isNullOrEmpty()) settled = merged
        Trace.line("inlet force af_status=$forced")
    }

    private suspend fun secondLook(data: Map<String, Any?>): Map<String, Any?> {
        val status = data["af_status"]?.toString().orEmpty()
        val organic = data.isEmpty() || status.equals("Organic", ignoreCase = true)
        if (!organic) return data
        delay(5_150)
        val gcd = glimpse(app) ?: return data
        if (gcd.isEmpty()) return data
        val gcdStatus = gcd["af_status"]?.toString().orEmpty()
        if (gcdStatus.equals("Non-organic", ignoreCase = true) || data.isEmpty()) {
            Trace.conv("gcd-replace", gcd, "")
            settle(gcd)
            return gcd
        }
        return data
    }

    private suspend fun glimpse(ctx: android.content.Context): Map<String, Any?>? {
        val uid = uid(ctx)
        if (uid.isEmpty()) return null
        return runCatching {
            val url = Relic.glimpse() + ctx.packageName + "?device_id=" + uid
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer " + Relic.beacon())
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", Face.line())
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            gcdOk = code in 200..299
            Trace.line("gcd http=$code keys=${runCatching { JSONObject(text).length() }.getOrDefault(-1)}")
            if (code !in 200..299) null else jsonMap(JSONObject(text))
        }.getOrNull()
    }

    private fun absorbLink(link: DeepLink) {
        runCatching {
            val click = link.clickEvent
            click.keys().forEach { key -> putBag(key, click.opt(key), true) }
        }
        putBag("deep_link_value", link.deepLinkValue, true)
        putBag("media_source", link.mediaSource, true)
        putBag("campaign", link.campaign, true)
        putBag("campaign_id", link.campaignId, true)
        putBag("af_sub1", link.afSub1, true)
        putBag("af_sub2", link.afSub2, true)
        putBag("af_sub3", link.afSub3, true)
        putBag("af_sub4", link.afSub4, true)
        putBag("af_sub5", link.afSub5, true)
        putBag("match_type", link.matchType, true)
        Trace.line("udl media=${link.mediaSource} value=${link.deepLinkValue}")
    }

    private fun absorbUri(uri: Uri?, authoritative: Boolean) {
        val target = uri ?: return
        val scheme = target.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        if (!target.isHierarchical) return
        target.queryParameterNames.forEach { name ->
            val value = target.getQueryParameter(name) ?: return@forEach
            putBag(name, value, authoritative)
        }
    }

    private fun absorbBundle(bundle: Bundle?, authoritative: Boolean) {
        val extras = bundle ?: return
        extras.keySet().forEach { key ->
            when (val value = extras.get(key)) {
                is String, is Number, is Boolean -> putBag(key, value, authoritative)
                is Uri -> absorbUri(value, authoritative)
            }
        }
    }

    private fun putBag(key: String, value: Any?, authoritative: Boolean) {
        if (key.isEmpty() || value == null) return
        synchronized(bag) {
            if (authoritative || !bag.containsKey(key)) bag[key] = value
        }
    }

    private fun snapshot(): Map<String, Any?> = synchronized(bag) { LinkedHashMap(bag) }

    private fun settle(data: Map<String, Any?>) {
        settled = data
        lastConv = data
        if (!convWait.isCompleted) convWait.complete(data)
    }

    private fun jsonMap(obj: JSONObject): Map<String, Any?> {
        val src = when {
            obj.has("af_status") -> obj
            obj.optJSONObject("data") != null -> obj.optJSONObject("data")!!
            else -> obj
        }
        val out = LinkedHashMap<String, Any?>()
        src.keys().forEach { k -> out[k] = src.opt(k) }
        return out
    }

    private fun JSONObject.putAny(key: String, value: Any?) {
        if (key.isEmpty() || value == null || value == JSONObject.NULL) return
        when (value) {
            is JSONObject, is JSONArray -> put(key, value)
            is Map<*, *> -> put(key, JSONObject(value))
            is Collection<*> -> put(key, JSONArray(value))
            is Boolean, is Number, is String -> put(key, value)
            else -> put(key, value.toString())
        }
    }
}
