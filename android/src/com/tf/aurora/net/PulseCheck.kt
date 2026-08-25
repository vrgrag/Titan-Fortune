package com.tf.aurora.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

object PulseCheck {
    fun alive(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        val pipe = cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            || cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return pipe && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun watch(ctx: Context, onUp: () -> Unit): ConnectivityManager.NetworkCallback {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (alive(ctx)) onUp()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) onUp()
            }
        }
        cm?.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            cb
        )
        return cb
    }

    fun drop(ctx: Context, cb: ConnectivityManager.NetworkCallback?) {
        if (cb == null) return
        runCatching {
            ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
        }
    }
}
