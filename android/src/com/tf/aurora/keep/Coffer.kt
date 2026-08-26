package com.tf.aurora.keep

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Persistent lane after a real config answer. Never written on a transport miss. */
class Coffer(ctx: Context) {
    private val app = ctx.applicationContext
    private val box = app.getSharedPreferences("hearth.vault.v6", Context.MODE_PRIVATE)

    fun lane(): Int = box.getInt(MARK, 0)
    fun href(): String = box.getString(HREF, "").orEmpty()
    fun till(): Long = box.getLong(TILL, 0L)

    fun lockMirror(url: String, expires: Long) {
        box.edit().putInt(MARK, MIRROR).putString(HREF, url).putLong(TILL, expires).apply()
    }

    fun lockStone() {
        box.edit().putInt(MARK, STONE).apply()
    }

    fun rememberHref(url: String, expires: Long) {
        box.edit().putString(HREF, url).putLong(TILL, expires).apply()
    }

    fun keepTap(url: String) {
        val dest = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
        box.edit().putString(TAP, dest).commit()
    }

    fun peekTap(): String = box.getString(TAP, "").orEmpty()

    fun takeTap(): String? {
        val v = peekTap().ifBlank { return null }
        box.edit().remove(TAP).commit()
        return v
    }

    fun markSkip() {
        box.edit()
            .putLong(SKIP, nowSec() + 3L * 24L * 60L * 60L)
            .commit()
    }

    fun markShut() {
        box.edit().putBoolean(SHUT, true).commit()
    }

    fun markAsked() {
        box.edit().putBoolean(ASKED, true).commit()
    }

    fun markAllowed() {
        box.edit().putBoolean(OK, true).commit()
    }

    fun shouldOffer(host: Activity): Boolean {
        if (osGranted()) {
            markAllowed()
            return false
        }
        if (box.getBoolean(OK, false) || box.getBoolean(SHUT, false)) return false
        val until = box.getLong(SKIP, 0L)
        if (until > 0L) return nowSec() >= until
        if (box.getBoolean(ASKED, false)) return false
        return true
    }

    fun osGranted(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L

    companion object {
        const val OPEN = 0
        const val MIRROR = 1
        const val STONE = 2
        private const val MARK = "lane.mark"
        private const val HREF = "href.keep"
        private const val TILL = "till.unix"
        private const val SKIP = "skip.epoch"
        private const val SHUT = "os.deny"
        private const val ASKED = "os.asked"
        private const val OK = "os.ok"
        private const val TAP = "tap.href"
    }
}
