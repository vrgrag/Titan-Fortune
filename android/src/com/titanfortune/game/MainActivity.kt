package com.titanfortune.game

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
        else HearthApp.instance.director.shutOs()
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        HearthApp.instance.director.intake(this, intent)
        setContent {
            val stage by HearthApp.instance.director.stage.collectAsState()
            val progress by HearthApp.instance.director.load.collectAsState()
            val veil by HearthApp.instance.director.veil.collectAsState()
            val lane = when (stage) {
                is Stage.Lost -> 1
                is Stage.Ask -> 2
                is Stage.Pane -> 3
                else -> 0
            }
            LaunchedEffect(lane) {
                PulseCheck.drop(this@MainActivity, netWatch)
                netWatch = when (lane) {
                    1 -> PulseCheck.watch(
                        this@MainActivity,
                        onUp = { runOnUiThread { HearthApp.instance.director.retry(this@MainActivity) } }
                    )
                    2, 3 -> PulseCheck.watch(
                        this@MainActivity,
                        onUp = { runOnUiThread { HearthApp.instance.director.regain() } },
                        onDown = { runOnUiThread { HearthApp.instance.director.lose() } }
                    )
                    else -> null
                }
                val pane = stage is Stage.Pane
                immerse(stage is Stage.Arena || pane)
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
            SideEffect {
                immerse(stage is Stage.Arena || stage is Stage.Pane)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.ime)
            ) {
                when (val now = stage) {
                    Stage.Pulse -> PulsePlate(progress)
                    Stage.Lost -> LostPlate { HearthApp.instance.director.retry(this@MainActivity) }
                    is Stage.Ask -> AskPlate(
                        accept = {
                            HearthApp.instance.director.noteAsked()
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notifyAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                HearthApp.instance.director.noteAllowed()
                                HearthApp.instance.director.accept(this@MainActivity)
                            }
                        },
                        skip = { HearthApp.instance.director.skip(this@MainActivity) }
                    )
                    is Stage.Pane -> Box(Modifier.fillMaxSize()) {
                        key(now.href) {
                            ConstellationPane(
                                href = now.href,
                                onLost = { HearthApp.instance.director.lose() }
                            )
                        }
                        if (veil) {
                            BackHandler(true) {}
                            LostPlate {
                                HearthApp.instance.director.retry(this@MainActivity)
                            }
                        }
                    }
                    Stage.Arena -> {
                        val pack = assets ?: Assets(this@MainActivity).also { assets = it }
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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immerse(HearthApp.instance.director.stage.value is Stage.Pane ||
            HearthApp.instance.director.stage.value is Stage.Arena)
    }

    override fun onResume() {
        super.onResume()
        val dir = HearthApp.instance.director
        if (dir.stage.value !is Stage.Pane) return
        if (PulseCheck.alive(this)) dir.regain()
        else dir.lose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runCatching {
            com.appsflyer.AppsFlyerLib.getInstance().performOnDeepLinking(intent, this)
        }
        val url = intent.getStringExtra("hearth.tap")
            ?: intent.getStringExtra("url")
            ?: intent.getStringExtra("link")
            ?: intent.getStringExtra("target_url")
        if (!url.isNullOrBlank() || intent.getBooleanExtra("hearth.from_push", false)) {
            if (!url.isNullOrBlank()) HearthApp.instance.director.warm(url)
            else HearthApp.instance.director.intake(this, intent)
            setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            )
            return
        }
        when (HearthApp.instance.director.stage.value) {
            is Stage.Pane, is Stage.Ask -> {
                HearthApp.instance.director.homeIfOnce()
                return
            }
            Stage.Arena -> return
            else -> HearthApp.instance.director.intake(this, intent)
        }
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
