package com.tf.aurora.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.net.InetSocketAddress
import java.net.Socket
import java.util.IdentityHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PulseCheck {
    fun alive(ctx: Context): Boolean {
        val cap = caps(ctx) ?: return false
        if (!cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!radio(cap)) return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun reachable(ctx: Context): Boolean {
        if (!alive(ctx)) return false
        return withContext(Dispatchers.IO) {
            PROBES.any { (host, port) -> knock(host, port) }
        }
    }

    fun watch(
        ctx: Context,
        onUp: () -> Unit,
        onDown: (() -> Unit)? = null
    ): ConnectivityManager.NetworkCallback {
        val app = ctx.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        var last: Boolean? = null

        fun emit(up: Boolean) {
            if (last == up) return
            last = up
            if (up) onUp() else onDown?.invoke()
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emit(alive(app))
            }

            override fun onLost(network: Network) {
                // Default network is gone — do not ask alive(): the dying
                // network can still look VALIDATED for a beat.
                emit(false)
            }

            override fun onUnavailable() {
                emit(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                emit(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        radio(caps)
                )
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (blocked) emit(false)
                else emit(alive(app))
            }
        }

        val tick = object : Runnable {
            override fun run() {
                // Only force DOWN from the poll. UP comes from callbacks so a
                // dying network that still reports VALIDATED cannot hide Lost.
                if (cm?.activeNetwork == null || !alive(app)) emit(false)
                main.postDelayed(this, 700)
            }
        }
        synchronized(watches) { watches[cb] = Watch(main, tick) }
        runCatching { cm?.registerDefaultNetworkCallback(cb, main) }
        emit(alive(app))
        main.postDelayed(tick, 700)
        return cb
    }

    fun drop(ctx: Context, cb: ConnectivityManager.NetworkCallback?) {
        if (cb == null) return
        val w = synchronized(watches) { watches.remove(cb) }
        w?.handler?.removeCallbacks(w.tick)
        runCatching {
            ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(cb)
        }
    }

    private fun caps(ctx: Context): NetworkCapabilities? {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(net)
    }

    private fun radio(cap: NetworkCapabilities): Boolean =
        cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            || cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    private fun knock(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1_500)
            true
        }
    } catch (_: Throwable) {
        false
    }

    private data class Watch(val handler: Handler, val tick: Runnable)

    private val watches = IdentityHashMap<ConnectivityManager.NetworkCallback, Watch>()
    private val PROBES = listOf("1.1.1.1" to 443, "8.8.8.8" to 53)
}
