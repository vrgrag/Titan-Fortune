package com.tf.aurora.note

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tf.aurora.boot.HearthApp
import com.tf.aurora.keep.Coffer
import com.titanfortune.game.MainActivity
import com.titanfortune.game.R
import java.net.URL
import kotlin.concurrent.thread

class PulseCatcher : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val coffer = Coffer(this)
        if (coffer.lane() == Coffer.MIRROR) {
            // Token refresh is picked up on the next config POST.
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val url = message.data["url"].orEmpty()
        val title = message.notification?.title ?: message.data["title"].orEmpty()
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val image = message.notification?.imageUrl?.toString() ?: message.data["image"].orEmpty()
        val stone = Coffer(this).lane() == Coffer.STONE
        if (!stone && url.isNotBlank() && HearthApp.instance.director.stage.value is com.tf.aurora.boot.Stage.Pane) {
            HearthApp.instance.director.warm(url)
        }
        thread(name = "pulse-paint") {
            val pic = image.takeIf { it.startsWith("http") }?.let { fetch(it) }
            paint(title.ifBlank { "Titan Fortune" }, body, url, pic, stone)
        }
    }

    private fun fetch(href: String): Bitmap? = runCatching {
        URL(href).openStream().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun paint(title: String, body: String, url: String, pic: Bitmap?, stone: Boolean) {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            mgr.createNotificationChannel(
                NotificationChannel(CHAN, "Offers", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!stone && url.isNotBlank()) putExtra("url", url)
        }
        val pi = PendingIntent.getActivity(
            this, url.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val note = NotificationCompat.Builder(this, CHAN)
            .setSmallIcon(R.drawable.ic_pulse_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .apply {
                if (pic != null) setStyle(NotificationCompat.BigPictureStyle().bigPicture(pic))
            }
            .build()
        mgr.notify((System.currentTimeMillis() % 10_000).toInt(), note)
    }

    companion object {
        private const val CHAN = "hearth.pulse.lane"
    }
}
