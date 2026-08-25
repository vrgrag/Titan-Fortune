package com.titanfortune.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DivineGold = Color(0xFFFFD98A)
val DeepGold = Color(0xFFC2871C)
val Night = Color(0xFF050A18)
val Deep = Color(0xFF0C1730)
val RoyalBlue = Color(0xFF1B4FA6)
val Azure = Color(0xFF5CC8FF)
val Glass = Color(0xB5122040)
val Marble = Color(0xFFF3F1EA)
val Danger = Color(0xFFFF6B75)
val Mint = Color(0xFF3FE0A5)

private val colors = darkColorScheme(
    primary = DivineGold,
    secondary = Azure,
    background = Night,
    surface = Deep,
    onPrimary = Color(0xFF2A1A00),
    onBackground = Marble,
    onSurface = Marble
)

@Composable
fun TitanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

enum class Tone { Gold, Steel, Ghost, Danger }

enum class BtnSize(val height: Dp, val font: TextUnit, val radius: Dp, val pad: Dp) {
    S(32.dp, 10.sp, 9.dp, 12.dp),
    M(40.dp, 12.sp, 11.dp, 16.dp),
    L(48.dp, 14.sp, 13.dp, 20.dp)
}

private fun Tone.fill(enabled: Boolean): List<Color> = when {
    !enabled -> listOf(Color(0xFF2A3040), Color(0xFF1A1F2C))
    this == Tone.Gold -> listOf(Color(0xFFFFEDBE), Color(0xFFF2B843), Color(0xFFB1740D))
    this == Tone.Steel -> listOf(Color(0xFF2B5490), Color(0xFF163061), Color(0xFF0C1A38))
    this == Tone.Danger -> listOf(Color(0xFFFF8E88), Color(0xFFCF3B45), Color(0xFF7C1A22))
    else -> listOf(Color(0x33244066), Color(0x330C1730))
}

private fun Tone.edge(enabled: Boolean): Color = when {
    !enabled -> Color(0xFF3C4356)
    this == Tone.Gold -> Color(0xFFFFF6DA)
    this == Tone.Steel -> DivineGold.copy(alpha = .55f)
    this == Tone.Danger -> Color(0xFFFFC7C2)
    else -> Marble.copy(alpha = .35f)
}

private fun Tone.ink(enabled: Boolean): Color = when {
    !enabled -> Color(0xFF6C7488)
    this == Tone.Gold -> Color(0xFF3A2402)
    else -> Marble
}

/**
 * Primary action control: layered gradient, gold rim, gloss highlight and a physical press dip.
 */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Steel,
    size: BtnSize = BtnSize.M,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed && enabled) 1f else 0f, tween(90), label = "press"
    )
    val shape = RoundedCornerShape(size.radius)

    Box(
        modifier
            .height(size.height)
            .defaultMinSize(minWidth = size.height)
            .scale(1f - press * .035f)
            .shadow(
                elevation = if (enabled) (10 - press * 7).dp else 0.dp,
                shape = shape,
                spotColor = if (tone == Tone.Gold) DeepGold else Color.Black,
                ambientColor = Color.Black
            )
            .clip(shape)
            .background(Brush.verticalGradient(tone.fill(enabled)))
            .border(1.dp, tone.edge(enabled).copy(alpha = if (enabled) .9f else .5f), shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ) {
                Assets.current?.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Glass gloss on the upper half sells the raised, polished look.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.5f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (enabled) .26f else .06f),
                            Color.Transparent
                        )
                    )
                )
        )
        Text(
            text,
            modifier = Modifier.padding(horizontal = size.pad),
            color = tone.ink(enabled),
            fontSize = size.font,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Circular combat control with an optional sweeping cooldown mask.
 */
@Composable
fun RoundButton(
    label: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 52.dp,
    tone: Tone = Tone.Steel,
    enabled: Boolean = true,
    cooldown: Float = 0f,
    accent: Color = DivineGold,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(90), label = "press")

    Box(
        modifier
            .size(diameter)
            .scale(1f - press * .06f)
            .shadow(if (enabled) (8 - press * 6).dp else 0.dp, CircleShape, spotColor = accent)
            .clip(CircleShape)
            .background(Brush.verticalGradient(tone.fill(enabled)))
            .border(1.5.dp, if (enabled) accent.copy(alpha = .85f) else Color(0xFF3C4356), CircleShape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
                Assets.current?.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.5f)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent)))
        )
        if (cooldown > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0xCC050A18),
                    startAngle = -90f,
                    sweepAngle = 360f * cooldown.coerceIn(0f, 1f),
                    useCenter = true
                )
            }
        }
        Text(
            label,
            color = if (enabled) Marble else Color(0xFF6C7488),
            fontSize = if (label.length > 4) 10.sp else 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .5.sp,
            maxLines = 1
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    radius: Dp = 18.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE01A2C50), Color(0xF0060D1F))))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(DivineGold.copy(alpha = .75f), Azure.copy(alpha = .18f), DeepGold.copy(alpha = .55f))
                ),
                shape
            )
            .padding(padding),
        contentAlignment = contentAlignment,
        content = content
    )
}

/** Compact status readout used for currencies and run stats. */
@Composable
fun Chip(text: String, tint: Color = DivineGold, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xB3081226))
            .border(1.dp, tint.copy(alpha = .45f), RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ScreenTitle(text: String, subtitle: String? = null, align: Alignment.Horizontal = Alignment.CenterHorizontally) {
    Column(horizontalAlignment = align) {
        Text(
            text,
            color = DivineGold,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = 2.5.sp,
            maxLines = 1
        )
        subtitle?.let {
            Text(it, color = Marble.copy(alpha = .6f), fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
        }
    }
}

/** Shared header: back control on the left, title block, free-form trailing widgets. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onBack?.let {
            GoldButton("‹  BACK", size = BtnSize.S, onClick = it)
            Spacer(Modifier.width(14.dp))
        }
        ScreenTitle(title, subtitle, Alignment.Start)
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing
        )
    }
}

/** Thin progress meter shared by the splash bar and the combat HUD. */
@Composable
fun Meter(progress: Float, colors: List<Color>, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color(0xCC040912))
            .border(1.dp, Color.White.copy(alpha = .10f), shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(Brush.horizontalGradient(colors))
        )
    }
}

/** Slowly drifting motes of light behind every menu screen. */
@Composable
fun StarField(intensity: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        repeat(26) { i ->
            val x = size.width * ((i * 37 % 100) / 100f)
            val y = size.height * ((i * 61 % 100) / 100f)
            val r = 1f + (i % 3)
            drawCircle(DivineGold.copy(alpha = intensity * (.25f + (i % 4) * .18f)), r, Offset(x, y))
        }
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Night.copy(alpha = .75f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.maxDimension * .62f
            )
        )
    }
}

/** Decorative gold rule used to separate header blocks from content. */
@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, DivineGold.copy(alpha = .55f), Color.Transparent)
                )
            )
    )
}

@Composable
fun RingGauge(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * .12f
        drawCircle(Color(0x99040912), size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(stroke)
        )
    }
}
