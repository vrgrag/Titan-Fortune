package com.tf.aurora.boot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.tf.aurora.keep.Coffer
import com.tf.aurora.mask.Trace
import com.tf.aurora.net.Ledger
import com.tf.aurora.net.OraclePost
import com.tf.aurora.net.OracleReply
import com.tf.aurora.net.PulseCheck
import com.tf.aurora.track.BeaconHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Stage {
    data object Pulse : Stage()
    data object Lost : Stage()
    data class Ask(val href: String, val once: Boolean) : Stage()
    data class Pane(val href: String, val once: Boolean) : Stage()
    data object Arena : Stage()
}

class FateDirector(
    private val coffer: Coffer,
    val beacon: BeaconHub,
    private val oracle: OraclePost
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private val _stage = MutableStateFlow<Stage>(Stage.Pulse)
    val stage: StateFlow<Stage> = _stage
    private val _veil = MutableStateFlow(false)
    val veil: StateFlow<Boolean> = _veil
    val load = MutableStateFlow(0.08f)

    @Volatile var warmHref: String? = null
        private set

    fun intake(activity: Activity, intent: Intent?) {
        val extra = peekUrl(intent)
        extra?.let { coffer.keepTap(it) }
        val fromPush = intent?.getBooleanExtra("hearth.from_push", false) == true || extra != null
        val tap = extra ?: if (fromPush) coffer.peekTap().ifBlank { null } else null
        val inbound = inboundLink(intent)
        Trace.line(
            "intake lane=${coffer.lane()} inbound=$inbound tap=${!tap.isNullOrBlank()} " +
                "net=${PulseCheck.alive(activity)} af_id=${beacon.uid(activity)} data=${intent?.data}"
        )
        if (coffer.lane() != Coffer.STONE || inbound) beacon.noteLaunch(intent)
        when {
            coffer.lane() == Coffer.STONE && !inbound -> {
                Trace.line("route Arena stone")
                _veil.value = false
                _stage.value = Stage.Arena
            }
            coffer.lane() == Coffer.MIRROR && !inbound -> {
                if (!PulseCheck.alive(activity)) {
                    Trace.line("route Lost mirror offline")
                    _veil.value = false
                    _stage.value = Stage.Lost
                } else {
                    val dest = tap?.takeIf { it.isNotBlank() } ?: coffer.href().ifBlank { null }
                    if (!dest.isNullOrBlank()) {
                        Trace.line("route Pane mirror tap=${!tap.isNullOrBlank()}")
                        reveal(activity, dest, once = !tap.isNullOrBlank(), force = !tap.isNullOrBlank())
                        if (!tap.isNullOrBlank()) {
                            coffer.takeTap()
                            forgetPulse(activity)
                        }
                        scope.launch { refreshMirror(activity) }
                    } else scope.launch { openMirror(activity, tap) }
                }
            }
            else -> {
                if (!PulseCheck.alive(activity)) {
                    Trace.line("route Lost first offline")
                    _veil.value = false
                    _stage.value = Stage.Lost
                } else scope.launch { firstPass(activity, intent, tap) }
            }
        }
    }

    fun retry(activity: Activity) {
        if (!PulseCheck.alive(activity)) {
            if (_stage.value is Stage.Pane) {
                _veil.value = true
                return
            }
            _stage.value = Stage.Lost
            return
        }
        _veil.value = false
        if (_stage.value is Stage.Pane) return
        intake(activity, activity.intent)
    }

    fun lose() {
        Trace.line("lose from ${_stage.value::class.simpleName}")
        when (_stage.value) {
            is Stage.Pane -> _veil.value = true
            else -> {
                _veil.value = false
                _stage.value = Stage.Lost
            }
        }
    }

    fun regain() {
        if (!_veil.value) return
        Trace.line("regain keep pane")
        _veil.value = false
    }

    fun homeIfOnce() {
        val home = coffer.href().ifBlank { return }
        when (val now = _stage.value) {
            is Stage.Pane -> {
                if (!now.once) return
                coffer.takeTap()
                _stage.value = Stage.Pane(home, once = false)
            }
            is Stage.Ask -> {
                if (!now.once) return
                coffer.takeTap()
                _stage.value = Stage.Ask(home, once = false)
            }
            else -> Unit
        }
    }

    fun accept(activity: Activity) {
        val now = _stage.value
        if (now is Stage.Ask) reveal(activity, now.href, now.once, force = true)
    }

    fun skip(activity: Activity) {
        coffer.markSkip()
        val now = _stage.value
        if (now is Stage.Ask) reveal(activity, now.href, now.once, force = true)
    }

    fun shutOs() {
        coffer.markShut()
    }

    fun noteAsked() {
        coffer.markAsked()
    }

    fun noteAllowed() {
        coffer.markAllowed()
    }

    fun warm(url: String) {
        if (coffer.lane() == Coffer.STONE) return
        val dest = sanitize(url) ?: return
        warmHref = dest
        when (val now = _stage.value) {
            is Stage.Pane -> {
                coffer.takeTap()
                if (now.href != dest) _stage.value = Stage.Pane(dest, once = true)
            }
            is Stage.Ask -> {
                coffer.takeTap()
                _stage.value = Stage.Ask(dest, once = true)
            }
            else -> coffer.keepTap(dest)
        }
    }

    private suspend fun firstPass(activity: Activity, intent: Intent?, tap: String?) {
        _stage.value = Stage.Pulse
        climb(0.18f)
        beacon.relay(activity, intent)
        beacon.ignite(activity)
        climb(0.42f)
        val body = withContext(Dispatchers.IO) { beacon.assemble(activity, first = true) }
        climb(0.72f)
        val reply = withContext(Dispatchers.IO) { oracle.send(activity, beacon, body) }
        climb(1f)
        delay(180)
        decideFirst(activity, reply, tap)
    }

    private suspend fun openMirror(activity: Activity, tap: String?) {
        _stage.value = Stage.Pulse
        climb(0.3f)
        beacon.relay(activity, activity.intent)
        beacon.ignite(activity)
        val body = withContext(Dispatchers.IO) { beacon.assemble(activity, first = false) }
        climb(0.7f)
        val reply = withContext(Dispatchers.IO) { oracle.send(activity, beacon, body) }
        val saved = coffer.href()
        val fresh = if (reply.ok && !reply.href.isNullOrBlank()) {
            coffer.rememberHref(reply.href, reply.till)
            reply.href
        } else saved
        val href = tap?.takeIf { it.isNotBlank() } ?: fresh
        climb(1f)
        delay(160)
        if (href.isNullOrBlank()) {
            Trace.line("mirror empty href")
            _stage.value = Stage.Lost
        } else {
            if (!tap.isNullOrBlank()) {
                coffer.takeTap()
                forgetPulse(activity)
            }
            reveal(activity, href, once = !tap.isNullOrBlank(), force = !tap.isNullOrBlank())
        }
    }

    private suspend fun refreshMirror(activity: Activity) {
        beacon.relay(activity, activity.intent)
        beacon.ignite(activity)
        val body = withContext(Dispatchers.IO) { beacon.assemble(activity, first = false) }
        val reply = withContext(Dispatchers.IO) { oracle.send(activity, beacon, body) }
        if (reply.ok && !reply.href.isNullOrBlank()) {
            coffer.rememberHref(reply.href, reply.till)
        }
    }

    private fun decideFirst(activity: Activity, reply: OracleReply, tap: String?) {
        val painted = beacon.lastBody.opt("af_status")?.toString().orEmpty()
        val status = beacon.lastConv["af_status"]?.toString().orEmpty()
            .ifBlank { painted }
            .ifBlank { "EMPTY" }
        Trace.line(
            "decide ok=${reply.ok} reached=${reply.reached} href=${!reply.href.isNullOrBlank()} " +
                "hasConv=${beacon.hasAttribution()} af_status=$status af_id=${beacon.uid(activity)}"
        )
        if (reply.ok && !reply.href.isNullOrBlank()) {
            coffer.lockMirror(reply.href, reply.till)
            val dest = tap?.takeIf { it.isNotBlank() } ?: reply.href
            Trace.line("route Pane/Ask mirror tap=${dest != reply.href}")
            reveal(activity, dest, once = dest != reply.href, force = dest != reply.href)
            if (dest != reply.href) forgetPulse(activity)
            return
        }
        val organic = status.equals("Organic", ignoreCase = true)
        if (reply.reached && beacon.hasAttribution() && organic) {
            coffer.lockStone()
            Trace.line("lock stone organic")
        }
        Trace.line("route Arena")
        _veil.value = false
        _stage.value = Stage.Arena
    }

    private fun reveal(activity: Activity, href: String, once: Boolean, force: Boolean = false) {
        if (!PulseCheck.alive(activity)) {
            Trace.line("reveal Lost offline")
            _veil.value = false
            _stage.value = Stage.Lost
            return
        }
        val dest = Ledger.carry(href, beacon.lastBody)
        if (!force && coffer.shouldOffer(activity)) {
            Trace.line("reveal Ask")
            _stage.value = Stage.Ask(dest, once)
        } else {
            Trace.line("reveal Pane")
            _veil.value = false
            _stage.value = Stage.Pane(dest, once)
        }
    }

    private suspend fun climb(target: Float) {
        while (load.value + 0.04f < target) {
            load.value = (load.value + 0.05f).coerceAtMost(target)
            delay(40)
        }
        load.value = target
    }

    private fun forgetPulse(activity: Activity) {
        activity.intent?.let { src ->
            src.removeExtra("hearth.tap")
            src.removeExtra("hearth.from_push")
            src.removeExtra("url")
            src.removeExtra("link")
            src.removeExtra("target_url")
        }
        activity.intent = Intent(activity, activity.javaClass).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    }

    private fun inboundLink(intent: Intent?): Boolean {
        if (intent == null) return false
        val data = intent.data
        if (data != null) {
            val host = data.host.orEmpty().lowercase()
            if (host.contains("onelink.me") || host.contains("titanfortune")) return true
        }
        if (!intent.getStringExtra("af_deeplink").isNullOrBlank()) return true
        return false
    }

    private fun peekUrl(intent: Intent?): String? {
        if (intent == null) return null
        TAP_KEYS.forEach { key ->
            extraText(intent, key)?.let { sanitize(it) }?.let { return it }
        }
        intent.extras?.keySet()?.forEach { key ->
            if (key.contains("url", ignoreCase = true) || key.contains("link", ignoreCase = true)) {
                extraText(intent, key)?.let { sanitize(it) }?.let { return it }
            }
        }
        return sanitize(intent.data?.toString())
    }

    private fun extraText(intent: Intent, key: String): String? {
        intent.getStringExtra(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = intent.extras?.get(key) ?: return null
        return raw.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun sanitize(raw: String?): String? {
        var s = Ledger.https(raw?.trim().orEmpty())
        if (s.isEmpty()) return null
        if (!s.startsWith("https://", ignoreCase = true)) return null
        val host = runCatching { Uri.parse(s).host }.getOrNull()
        if (host.isNullOrBlank()) return null
        return s
    }

    companion object {
        private val TAP_KEYS = listOf("hearth.tap", "url", "link", "target_url", "gcm.notification.url")
    }
}
