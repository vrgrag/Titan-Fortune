package com.tf.aurora.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titanfortune.game.BtnSize
import com.titanfortune.game.DivineGold
import com.titanfortune.game.GoldButton
import com.titanfortune.game.Meter
import com.titanfortune.game.Tone

private fun sheet(ctx: Context, name: String): Bitmap? = runCatching {
    ctx.assets.open("shell/$name").use { BitmapFactory.decodeStream(it) }
}.getOrNull()

@Composable
private fun Plate(fileH: String, fileV: String, footer: @Composable BoxScope.() -> Unit) {
    val portrait = LocalConfiguration.current.screenHeightDp > LocalConfiguration.current.screenWidthDp
    val ctx = LocalContext.current
    val bmp = remember(portrait) { sheet(ctx, if (portrait) fileV else fileH) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.24f)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter,
            content = footer
        )
    }
}

@Composable
fun PulsePlate(progress: Float) {
    Plate("h_pulse_load.png", "v_pulse_load.png") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Meter(progress, listOf(Color(0xFFB1740D), DivineGold, Color.White), 12.dp)
            Spacer(Modifier.height(6.dp))
            Text("LOADING", color = DivineGold, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 12.sp)
        }
    }
}

@Composable
fun LostPlate(retry: () -> Unit) {
    Plate("h_void.webp", "v_void.webp") {
        GoldButton("RETRY", Modifier.fillMaxWidth(), Tone.Gold, BtnSize.L, onClick = retry)
    }
}

@Composable
fun AskPlate(accept: () -> Unit, skip: () -> Unit) {
    Plate("h_pulse.webp", "v_pulse.webp") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GoldButton("ACCEPT", Modifier.fillMaxWidth(), Tone.Gold, BtnSize.L, onClick = accept)
            Spacer(Modifier.height(8.dp))
            GoldButton("SKIP", Modifier.fillMaxWidth(), Tone.Ghost, BtnSize.M, onClick = skip)
        }
    }
}
