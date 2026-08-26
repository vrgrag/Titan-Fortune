package com.tf.aurora.note

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tf.aurora.keep.Coffer
import com.titanfortune.game.MainActivity
import com.titanfortune.game.R
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class PulseCatcher : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title.orEmpty()
        val body = message.data["body"] ?: message.notification?.body.orEmpty()
        if (title.isEmpty() && body.isEmpty()) return
        val url = (message.data["url"] ?: message.data["link"] ?: message.data["target_url"]).orEmpty()
        val tap = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
        val image = message.data["image"] ?: message.notification?.imageUrl?.toString().orEmpty()
        val stone = Coffer(this).lane() == Coffer.STONE
        if (!stone && tap.startsWith("https://")) Coffer(applicationContext).keepTap(tap)
        val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val app = applicationContext
        paint(app, id, title, body, tap, null, stone)
        if (image.startsWith("http")) {
            thread(name = "pulse-paint") {
                paint(app, id, title, body, tap, fetch(image), stone)
            }
        }
    }

    companion object {
        const val CHAN = "hearth.pulse.lane"

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHAN) != null) return
            mgr.createNotificationChannel(
                NotificationChannel(CHAN, "Offers", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        private fun canPost(ctx: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }

        private fun paint(
            ctx: Context,
            id: Int,
            title: String,
            body: String,
            url: String,
            pic: Bitmap?,
            stone: Boolean
        ) {
            ensureChannel(ctx)
            if (!canPost(ctx)) return
            val launch = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("hearth.from_push", true)
                if (!stone && url.isNotBlank()) {
                    putExtra("url", url)
                    putExtra("hearth.tap", url)
                }
            }
            val pi = PendingIntent.getActivity(
                ctx,
                id,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val note = NotificationCompat.Builder(ctx, CHAN)
                .setSmallIcon(R.drawable.ic_pulse_mark)
                .setContentTitle(title.ifBlank { "Titan Fortune" })
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pi)
                .apply {
                    if (pic != null) {
                        setLargeIcon(pic)
                        setStyle(
                            NotificationCompat.BigPictureStyle().bigPicture(pic).bigLargeIcon(null as Bitmap?)
                        )
                    } else if (body.isNotBlank()) {
                        setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    }
                }
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(id, note) }
        }

        private fun fetch(href: String): Bitmap? = try {
            val conn = URL(href).openConnection() as HttpURLConnection
            conn.connectTimeout = 2_500
            conn.readTimeout = 2_500
            conn.instanceFollowRedirects = true
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
    }
}
