package com.tf.aurora.net

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Fills backend ladder fields after conversion + UDL are already in the body. */
object Ledger {
    private val skipPack = setOf("push_token", "firebase_project_id", "extra_param_7")

    fun flatten(raw: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        raw.forEach { (k, v) ->
            val n = scalar(v) ?: return@forEach
            out[k] = n
        }
        return out
    }

    fun stamp(body: JSONObject) {
        alias(body, "pid", "media_source")
        alias(body, "c", "campaign")
        alias(body, "af_c_id", "campaign_id")
        unpack(body)
        subIds(body)
        extras(body)
    }

    fun carry(url: String, body: JSONObject): String {
        val parsed = runCatching { Uri.parse(https(url)) }.getOrNull() ?: return https(url)
        if (!parsed.getQueryParameter("sub_id_1").isNullOrEmpty() ||
            !parsed.getQueryParameter("extra_param_2").isNullOrEmpty()
        ) return https(url)
        val b = parsed.buildUpon().clearQuery()
        parsed.queryParameterNames.forEach { name ->
            parsed.getQueryParameters(name).forEach { value ->
                b.appendQueryParameter(name, value)
            }
        }
        for (i in 1..11) copy(b, body, "sub_id_$i")
        for (i in 2..8) copy(b, body, "extra_param_$i")
        return b.build().toString().let { https(it) }
    }

    fun https(raw: String): String {
        val s = raw.trim()
        if (s.startsWith("http://", ignoreCase = true)) return "https://" + s.substring(7)
        return s
    }

    private fun copy(builder: Uri.Builder, body: JSONObject, key: String) {
        val text = scalar(body.opt(key))?.toString() ?: return
        if (text.isNotEmpty()) builder.appendQueryParameter(key, text)
    }

    private fun alias(body: JSONObject, from: String, to: String) {
        if (body.optString(to).isNotEmpty()) return
        val text = scalar(body.opt(from))?.toString() ?: return
        if (text.isNotEmpty()) body.put(to, text)
    }

    private fun unpack(body: JSONObject) {
        val raw = body.optString("deep_link_value")
        if (raw.isEmpty() || !raw.contains('=')) return
        raw.split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@forEach
            val key = runCatching { Uri.decode(pair.substring(0, eq)) }.getOrNull() ?: return@forEach
            val value = runCatching { Uri.decode(pair.substring(eq + 1)) }.getOrNull() ?: return@forEach
            if (key.isEmpty() || value.isEmpty() || body.optString(key).isNotEmpty()) return@forEach
            body.put(key, value)
        }
    }

    private fun subIds(body: JSONObject) {
        val campaign = body.optString("campaign").ifEmpty { body.optString("c") }
        val tokens = campaign.substringBefore('#').trim().split('_').filter { it.isNotEmpty() }
        for (i in 1..11) {
            val slot = "sub_id_$i"
            if (filled(body.opt(slot))) continue
            val picked = when {
                i <= 5 && filled(body.opt("af_sub$i")) -> body.opt("af_sub$i")
                i <= 10 && filled(body.opt("deep_link_sub$i")) -> body.opt("deep_link_sub$i")
                i == 1 && tokens.isNotEmpty() -> tokens.last().substringBefore(' ').trim()
                i == 2 && tokens.size >= 2 -> tokens[1]
                i == 3 && filled(body.opt("af_adset")) -> body.opt("af_adset")
                i == 3 && filled(body.opt("adset")) -> body.opt("adset")
                i == 4 && filled(body.opt("campaign_id")) -> body.opt("campaign_id")
                i == 4 && filled(body.opt("af_c_id")) -> body.opt("af_c_id")
                i == 5 -> body.opt("bundle_id")
                i == 7 -> body.opt("push_token")
                i == 10 -> body.opt("af_id")
                i == 11 -> body.opt("media_source") ?: body.opt("pid")
                else -> null
            }
            if (filled(picked)) body.put(slot, scalar(picked))
        }
    }

    private fun extras(body: JSONObject) {
        putEmpty(body, "extra_param_2", body.opt("af_sub1"))
        putEmpty(body, "extra_param_3", body.opt("af_sub2"))
        putEmpty(body, "extra_param_4", body.opt("af_sub3"))
        putEmpty(body, "extra_param_5", body.opt("af_sub4"))
        putEmpty(body, "extra_param_6", body.opt("af_sub5"))
        putEmpty(body, "extra_param_8", body.opt("campaign") ?: body.opt("c"))
        if (!filled(body.opt("extra_param_7"))) body.put("extra_param_7", pack(body))
    }

    private fun pack(body: JSONObject): String {
        val parts = ArrayList<String>()
        body.keys().asSequence().toList().sorted().forEach { key ->
            if (key in skipPack || key.startsWith("sub_id_") || key.startsWith("extra_param_")) return@forEach
            val value = scalar(body.opt(key))?.toString() ?: return@forEach
            if (value.isEmpty()) return@forEach
            parts += "${enc(key)}=${enc(value)}"
        }
        return parts.joinToString("&")
    }

    private fun putEmpty(body: JSONObject, key: String, value: Any?) {
        if (filled(body.opt(key))) return
        val n = scalar(value) ?: return
        if (n.toString().isNotEmpty()) body.put(key, n)
    }

    private fun scalar(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is String, is Number, is Boolean -> value
        is JSONArray -> {
            if (value.length() == 0) null
            else if (value.length() == 1) scalar(value.opt(0))
            else (0 until value.length()).mapNotNull { scalar(value.opt(it))?.toString() }
                .filter { it.isNotEmpty() }
                .joinToString(",")
        }
        is Collection<*> -> {
            if (value.isEmpty()) null
            else if (value.size == 1) scalar(value.first())
            else value.mapNotNull { scalar(it)?.toString() }.filter { it.isNotEmpty() }.joinToString(",")
        }
        is JSONObject -> value.toString()
        else -> value.toString()
    }

    private fun filled(value: Any?): Boolean {
        val text = scalar(value)?.toString() ?: return false
        return text.isNotEmpty() && text != "null"
    }

    private fun enc(raw: String): String =
        URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
}
