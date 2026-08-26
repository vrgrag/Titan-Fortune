package com.tf.aurora.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titanfortune.game.BtnSize
import com.titanfortune.game.DivineGold
import com.titanfortune.game.GoldButton
import com.titanfortune.game.Meter
import com.titanfortune.game.Tone
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

private fun sheet(ctx: Context, name: String): Bitmap? = runCatching {
    ctx.assets.open("shell/$name").use { BitmapFactory.decodeStream(it) }
}.getOrNull()

/** Painted card bottom as a fraction of the source bitmap. Crop-aware. */
private data class ArtCard(val artW: Int, val artH: Int, val bottom: Float) {
    fun band(frameW: Dp, frameH: Dp): Pair<Dp, Dp> {
        val w = frameW.value
        val h = frameH.value
        val factor = max(w / artW, h / artH)
        val painted = artH * factor
        val top = (h - painted) / 2f
        val cardBottom = (top + bottom * painted).coerceIn(0f, h)
        val artBottom = min(h, top + painted)
        return cardBottom.dp to artBottom.dp
    }
}

private val VoidPort = ArtCard(1080, 2400, 0.665f)
private val VoidLand = ArtCard(2400, 1080, 0.712f)
private val PulsePort = ArtCard(1080, 2400, 0.665f)
private val PulseLand = ArtCard(2400, 1080, 0.712f)

/**
 * [drop]: portrait — sit in the middle of the space under the card
 * (over the reels, not on the slot frame). Landscape stays pinned
 * right under the card.
 */
private fun pinUnder(
    cardBottom: Dp,
    artBottom: Dp,
    block: Dp,
    frameH: Dp,
    gap: Dp = 14.dp,
    floor: Dp = 16.dp,
    drop: Boolean = false,
): Dp {
    val bandTop = cardBottom.value + gap.value
    val ceiling = frameH.value - block.value - floor.value
    val pinned = if (drop) {
        val bandBottom = min(artBottom.value, frameH.value) - floor.value
        val slack = bandBottom - bandTop - block.value
        bandTop + max(0f, slack) / 2f
    } else {
        bandTop
    }
    return max(0f, min(pinned, ceiling)).dp
}

@Composable
private fun Plate(
    fileH: String,
    fileV: String,
    band: Float = 0.24f,
    pin: Alignment = Alignment.BottomCenter,
    footer: @Composable BoxScope.() -> Unit,
) {
    val portrait = LocalConfiguration.current.screenHeightDp > LocalConfiguration.current.screenWidthDp
    val ctx = LocalContext.current
    val bmp = remember(portrait) { sheet(ctx, if (portrait) fileV else fileH) }
    val absorb = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(indication = null, interactionSource = absorb, onClick = {})
    ) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val slot =
            if (band >= 1f) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().fillMaxHeight(band).align(Alignment.BottomCenter)
        Box(
            slot.padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = pin,
            content = footer,
        )
    }
}

@Composable
private fun CardStage(
    fileH: String,
    fileV: String,
    land: ArtCard,
    port: ArtCard,
    blockHeight: Dp,
    footer: @Composable (screenW: Dp, landscape: Boolean) -> Unit,
) {
    val portrait = LocalConfiguration.current.screenHeightDp > LocalConfiguration.current.screenWidthDp
    val ctx = LocalContext.current
    val bmp = remember(portrait) { sheet(ctx, if (portrait) fileV else fileH) }
    val absorb = remember { MutableInteractionSource() }
    val card = if (portrait) port else land
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(indication = null, interactionSource = absorb, onClick = {})
    ) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val frameW = maxWidth
        val frameH = maxHeight
        val (cardBottom, artBottom) = card.band(frameW, frameH)
        val top = pinUnder(cardBottom, artBottom, blockHeight, frameH, drop = portrait)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = top),
            contentAlignment = Alignment.TopCenter,
        ) {
            footer(frameW, !portrait)
        }
    }
}

@Composable
fun PulsePlate(progress: Float) {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dots = dots % 3 + 1
        }
    }
    Plate("h_pulse_load.png", "v_pulse_load.png") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Loading${".".repeat(dots)}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontSize = 22.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.9f),
                        offset = Offset(0f, 2f),
                        blurRadius = 10f
                    )
                )
            )
            Spacer(Modifier.height(8.dp))
            Meter(progress, listOf(Color(0xFFB1740D), DivineGold, Color.White), 12.dp)
        }
    }
}

@Composable
fun LostPlate(retry: () -> Unit) {
    val h = BtnSize.L.height
    CardStage("h_void.webp", "v_void.webp", VoidLand, VoidPort, h) { w, landscape ->
        val bw = if (landscape) w * 0.42f else minOf(w * 0.72f, 420.dp)
        GoldButton("RETRY", Modifier.width(bw), Tone.Gold, BtnSize.L, onClick = retry)
    }
}

@Composable
fun AskPlate(accept: () -> Unit, skip: () -> Unit) {
    val landscapeNow =
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val h = BtnSize.L.height
    val gap = 12.dp
    CardStage(
        "h_pulse.webp",
        "v_pulse.webp",
        PulseLand,
        PulsePort,
        blockHeight = if (landscapeNow) h else h * 2 + gap,
    ) { w, landscape ->
        if (landscape) {
            val bw = w * 0.28f
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoldButton("ACCEPT", Modifier.width(bw), Tone.Gold, BtnSize.L, onClick = accept)
                GoldButton("SKIP", Modifier.width(bw), Tone.Steel, BtnSize.L, onClick = skip)
            }
        } else {
            val bw = minOf(w * 0.72f, 420.dp)
            Column(
                Modifier.width(bw),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                GoldButton("ACCEPT", Modifier.width(bw), Tone.Gold, BtnSize.L, onClick = accept)
                GoldButton("SKIP", Modifier.width(bw), Tone.Steel, BtnSize.L, onClick = skip)
            }
        }
    }
}
