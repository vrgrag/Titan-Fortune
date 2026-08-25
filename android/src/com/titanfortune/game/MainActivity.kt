package com.titanfortune.game

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tf.aurora.boot.HearthApp
import com.tf.aurora.boot.Stage
import com.tf.aurora.net.PulseCheck
import com.tf.aurora.pane.ConstellationPane
import com.tf.aurora.skin.AskPlate
import com.tf.aurora.skin.LostPlate
import com.tf.aurora.skin.PulsePlate

class MainActivity : ComponentActivity() {
    private var assets: Assets? = null
    private var netWatch: android.net.ConnectivityManager.NetworkCallback? = null

    private val notifyAsk = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) HearthApp.instance.director.noteAllowed()
        else if (Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            HearthApp.instance.director.shutOs()
        }
        HearthApp.instance.director.accept(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        HearthApp.instance.director.intake(this, intent)
        setContent {
            val stage by HearthApp.instance.director.stage.collectAsState()
            val progress by HearthApp.instance.director.load.collectAsState()
            LaunchedEffect(stage) {
                if (stage is Stage.Lost) {
                    PulseCheck.drop(this@MainActivity, netWatch)
                    netWatch = PulseCheck.watch(this@MainActivity) {
                        runOnUiThread { HearthApp.instance.director.retry(this@MainActivity) }
                    }
                } else {
                    PulseCheck.drop(this@MainActivity, netWatch)
                    netWatch = null
                }
                val pane = stage is Stage.Pane
                immerse(stage is Stage.Arena || pane)
                window.setSoftInputMode(
                    if (pane) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }
            when (val now = stage) {
                Stage.Pulse -> PulsePlate(progress)
                Stage.Lost -> LostPlate { HearthApp.instance.director.retry(this) }
                is Stage.Ask -> AskPlate(
                    accept = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            HearthApp.instance.director.noteAsked()
                            notifyAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            HearthApp.instance.director.noteAllowed()
                            HearthApp.instance.director.accept(this)
                        }
                    },
                    skip = { HearthApp.instance.director.skip(this) }
                )
                is Stage.Pane -> ConstellationPane(
                    href = now.href,
                    onLost = { HearthApp.instance.director.retry(this) }
                )
                Stage.Arena -> {
                    val pack = assets ?: Assets(this).also { assets = it }
                    val edition = runCatching { GameEdition.valueOf(BuildConfig.GAME_EDITION) }
                        .getOrDefault(GameEdition.V3_COMPLETE)
                    TitanTheme {
                        TitanApp(
                            assets = pack,
                            edition = edition,
                            lockLandscape = {
                                requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runCatching {
            com.appsflyer.AppsFlyerLib.getInstance().performOnDeepLinking(intent, this)
        }
        val url = intent.getStringExtra("url")
        if (!url.isNullOrBlank()) HearthApp.instance.director.warm(url)
        else HearthApp.instance.director.intake(this, intent)
    }

    override fun onDestroy() {
        PulseCheck.drop(this, netWatch)
        assets?.release()
        super.onDestroy()
    }

    private fun immerse(hideBars: Boolean) {
        val bars = WindowInsetsControllerCompat(window, window.decorView)
        if (hideBars) {
            bars.hide(WindowInsetsCompat.Type.systemBars())
            bars.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
