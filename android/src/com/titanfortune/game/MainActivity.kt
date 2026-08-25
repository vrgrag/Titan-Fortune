package com.titanfortune.game

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var assets: Assets

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        assets = Assets(this)
        val edition = runCatching { GameEdition.valueOf(BuildConfig.GAME_EDITION) }
            .getOrDefault(GameEdition.V3_COMPLETE)

        setContent {
            TitanTheme {
                TitanApp(
                    assets = assets,
                    edition = edition,
                    lockLandscape = {
                        requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        assets.release()
        super.onDestroy()
    }
}
