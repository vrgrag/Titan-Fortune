package com.titanfortune.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun GameView(
    assets: Assets,
    edition: GameEdition,
    startGem: Gem,
    zone: Int,
    onExit: (score: Int, wave: Int, essence: Int) -> Unit
) {
    val state = remember { GameState(edition, startGem, zone) }
    var move by remember { mutableStateOf(Offset.Zero) }
    var showPause by remember { mutableStateOf(false) }
    var lastFrame by remember { mutableStateOf(0L) }

    LaunchedEffect(state) { state.onSound = { assets.play(it) } }

    BackHandler {
        if (!state.finished) {
            showPause = !showPause
            state.paused = showPause
        }
    }
    LaunchedEffect(state) {
        while (isActive) {
            withFrameNanos { now ->
                if (lastFrame != 0L) state.tick((now - lastFrame) / 1_000_000_000f, move.x, move.y)
                lastFrame = now
            }
        }
    }

    val bgPath = listOf(
        "bg/olympus_marble_arena_asset.png",
        "bg/zeus_temple_background_asset.png",
        "bg/sky_islands_background_asset.png",
        "bg/storm_sky_background_asset.png"
    )[zone % 4]
    val images = remember {
        (Gem.entries.map { it.sprite } + listOf(
            "sprites/hero.png", "sprites/hoplite.png", "sprites/minotaur.png",
            "sprites/harpy.png", "sprites/spider.png", "sprites/eagle.png",
            "sprites/golem.png", "sprites/storm_wolf.png", "sprites/spearman.png",
            "sprites/elite_spear.png", "sprites/elite_crystal.png",
            "sprites/titan_earth.png", "sprites/titan_sea.png",
            "sprites/titan_void.png", "sprites/titan_storm.png",
            "sprites/olympian_essence_shard_asset_0.png",
            "sprites/greek_columns_set_asset_0.png"
        )).associateWith { assets.bitmap(it)?.asImageBitmap() }
    }
    val bg = remember(bgPath) { assets.bitmap(bgPath)?.asImageBitmap() }
    val pulse by rememberInfiniteTransition(label = "combat").animateFloat(
        .7f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse"
    )
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        CombatCanvas(state, images, bg, pulse, textPaint)
        DamageVignette(state.hurt)
        WaveBanner(state)

        TopHud(state, edition) {
            showPause = true
            state.paused = true
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp)) {
            Joystick { move = it }
        }
        ActionCluster(
            state = state,
            edition = edition,
            move = move,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp)
        )

        if (showPause) PauseOverlay(
            resume = { showPause = false; state.paused = false },
            exit = { onExit(state.score, state.wave, state.essence) }
        )
        if (state.finished) ResultOverlay(state) {
            onExit(state.score, state.wave, state.essence)
        }
    }
}

@Composable
private fun TopHud(state: GameState, edition: GameEdition, pause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Vitality
        HudCard(Modifier.width(186.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VITALITY", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("${state.hp.toInt()}", color = Marble, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(3.dp))
            Meter(state.hp / 100f, listOf(Color(0xFF15B37A), Mint), 7.dp)
        }

        // Wave + score
        HudCard(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WAVE ${state.wave}/${edition.waves}",
                    color = DivineGold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp
                )
                Spacer(Modifier.weight(1f))
                if (state.combo > 1) {
                    Text("x${state.combo}", color = Color(0xFFFF9F4A), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                }
                Text("${state.score}", color = Marble, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(3.dp))
            Meter(state.waveProgress, listOf(DeepGold, DivineGold), 4.dp)
        }

        // Boss health takes over the right slot while a titan is alive.
        state.enemies.firstOrNull { it.boss }?.let { boss ->
            HudCard(Modifier.width(180.dp)) {
                Text("⚡ TITAN", color = Danger, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(3.dp))
                Meter(boss.hp / boss.maxHp, listOf(Color(0xFF9E1B26), Danger), 7.dp)
            }
        }

        RoundButton("❚❚", diameter = 40.dp, accent = DivineGold, onClick = pause)
    }
}

@Composable
private fun ActionCluster(
    state: GameState,
    edition: GameEdition,
    move: Offset,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (edition.advanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                state.gems.forEach { gem ->
                    GemChip(gem, state.active == gem) { state.active = gem }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HudCard(Modifier.width(206.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.active.title,
                    color = state.active.color, fontSize = 9.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.overcharge) "OVERCHARGE" else "${state.charge.toInt()}%",
                    color = if (state.overcharge) Color(0xFFFF79F2) else Marble.copy(alpha = .8f),
                    fontSize = 9.sp, fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(3.dp))
            Meter(
                state.charge / 100f,
                if (state.overcharge) listOf(Color(0xFFB44BFF), Color.White)
                else listOf(state.active.color.copy(alpha = .5f), state.active.color),
                7.dp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            if (edition.complete) {
                RoundButton(
                    "ZEUS", diameter = 48.dp, tone = Tone.Steel,
                    enabled = state.godReady, cooldown = state.godCooldown / 14f, accent = Azure
                ) { state.godPower() }
            }
            if (edition.advanced) {
                RoundButton(
                    "DASH", diameter = 48.dp, tone = Tone.Steel,
                    enabled = state.dashReady, cooldown = state.dashCooldown / 1.6f, accent = Mint
                ) { state.dash(move.x, move.y) }
            }
            RoundButton(
                "STRIKE", diameter = 72.dp, tone = Tone.Gold,
                enabled = state.chargeReady, accent = DivineGold
            ) { state.discharge() }
        }
    }
}

@Composable
private fun GemChip(gem: Gem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    if (selected) listOf(gem.color.copy(alpha = .75f), gem.color.copy(alpha = .3f))
                    else listOf(Color(0xB3142544), Color(0xB3081226))
                )
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else gem.color.copy(alpha = .55f), CircleShape)
            .clickable {
                Assets.current?.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            gem.title.take(1),
            color = if (selected) Color.White else gem.color,
            fontWeight = FontWeight.Black,
            fontSize = if (selected) 15.sp else 13.sp
        )
    }
}

@Composable
private fun HudCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xCC16294B), Color(0xE0060D1F))))
            .border(1.dp, DivineGold.copy(alpha = .32f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun DamageVignette(intensity: Float) {
    if (intensity <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Danger.copy(alpha = .42f * intensity)),
                center = center,
                radius = size.maxDimension * .55f
            )
        )
    }
}

@Composable
private fun WaveBanner(state: GameState) {
    if (state.bannerTime <= 0f) return
    // Fade in fast, hold, then fade out over the tail of the timer.
    val t = state.bannerTime
    val alpha = (if (t > 1.9f) (2.2f - t) / .3f else (t / 1.0f)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .scale(.94f + alpha * .06f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xCC060D1F).copy(alpha = .8f * alpha), Color.Transparent)
                    )
                )
                .padding(horizontal = 54.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                state.bannerText,
                color = DivineGold.copy(alpha = alpha),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp
            )
            Text(
                state.bannerSub,
                color = Marble.copy(alpha = .75f * alpha),
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun CombatCanvas(
    state: GameState,
    images: Map<String, ImageBitmap?>,
    background: ImageBitmap?,
    pulse: Float,
    paint: Paint
) {
    Canvas(Modifier.fillMaxSize()) {
        background?.let {
            drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
        drawRect(Color(0x59060D1F))
        drawArena(pulse)

        state.pickups.forEach { p ->
            val x = p.x * size.width
            val y = p.y * size.height
            val s = size.minDimension * .046f
            val a = (p.life / 2f).coerceIn(.35f, 1f)
            drawCircle(Azure.copy(alpha = .28f * a), s * .8f, Offset(x, y))
            images["sprites/olympian_essence_shard_asset_0.png"]?.let {
                drawImage(
                    it,
                    dstOffset = IntOffset((x - s / 2).toInt(), (y - s / 2).toInt()),
                    dstSize = IntSize(s.toInt(), s.toInt()),
                    alpha = a
                )
            } ?: drawCircle(Azure.copy(alpha = a), s * .3f, Offset(x, y))
        }

        state.enemies.forEach { foe -> drawFoe(foe, images) }
        drawReticle(state, pulse)
        drawHero(state, images, pulse)

        state.shots.forEach { s ->
            val x = s.x * size.width
            val y = s.y * size.height
            drawCircle(Danger.copy(alpha = .35f), 11f, Offset(x, y))
            drawCircle(Color(0xFFFFC38F), 5f, Offset(x, y))
        }
        state.bolts.forEach { b ->
            val from = Offset(b.x * size.width, b.y * size.height)
            val to = Offset(b.tx * size.width, b.ty * size.height)
            val a = (b.life * 6f).coerceIn(0f, 1f)
            drawLine(b.color.copy(alpha = .55f * a), from, to, b.width * 2f)
            drawLine(b.color.copy(alpha = a), from, to, b.width)
            drawLine(Color.White.copy(alpha = .85f * a), from, to, b.width * .35f)
        }
        state.sparks.forEach { s ->
            val a = (s.life / s.maxLife).coerceIn(0f, 1f)
            drawCircle(
                s.color.copy(alpha = a),
                (2f + a * 4f),
                Offset(s.x * size.width, s.y * size.height)
            )
        }

        // Floating combat text is cheapest through the native canvas.
        drawContext.canvas.nativeCanvas.let { canvas ->
            state.texts.forEach { t ->
                val a = (t.life / .8f).coerceIn(0f, 1f)
                paint.textSize = size.minDimension * .035f * t.scale
                paint.color = t.color.toArgb()
                paint.alpha = (a * 255).toInt()
                canvas.drawText(t.text, t.x * size.width, t.y * size.height, paint)
            }
        }
    }
}

private fun DrawScope.drawArena(pulse: Float) {
    val r = size.minDimension * .46f
    drawCircle(DivineGold.copy(alpha = .18f), r, center, style = Stroke(2.5f))
    drawCircle(Azure.copy(alpha = .10f), r * .72f, center, style = Stroke(1.5f))
    rotate(pulse * 12f) {
        repeat(12) { i ->
            val a = i * 6.283f / 12f
            val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
            drawCircle(DivineGold.copy(alpha = .22f + pulse * .12f), 2.5f, p)
        }
    }
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Night.copy(alpha = .6f)),
            center = center,
            radius = size.maxDimension * .58f
        )
    )
}

private fun DrawScope.drawFoe(foe: Foe, images: Map<String, ImageBitmap?>) {
    val x = foe.x * size.width
    val y = foe.y * size.height + sin(foe.bob) * (if (foe.ranged) 3.5f else 1.2f)
    val base = when {
        foe.boss -> size.minDimension * .26f
        foe.elite -> size.minDimension * .13f
        else -> size.minDimension * .088f
    }
    // Ground shadow anchors sprites to the arena floor.
    drawOval(
        Color.Black.copy(alpha = .32f),
        topLeft = Offset(x - base * .3f, y + base * .38f),
        size = Size(base * .6f, base * .16f)
    )
    if (foe.slow > 0f) drawCircle(Color(0x66BF62FF), base * .48f, Offset(x, y))
    images[foe.sprite]?.let { image ->
        val ratio = image.width.toFloat() / image.height
        val w = base * ratio
        drawImage(
            image,
            dstOffset = IntOffset((x - w / 2).toInt(), (y - base / 2).toInt()),
            dstSize = IntSize(w.toInt(), base.toInt()),
            alpha = if (foe.hit > 0f) .55f else 1f
        )
        if (foe.hit > 0f) {
            drawCircle(Color.White.copy(alpha = .30f * (foe.hit / .22f).coerceIn(0f, 1f)), base * .42f, Offset(x, y))
        }
    } ?: drawCircle(if (foe.elite) DivineGold else Danger, base * .35f, Offset(x, y))

    if (foe.hp < foe.maxHp) {
        val bw = base * (if (foe.boss) .8f else 1.05f)
        val top = y - base * (if (foe.boss) .56f else .68f)
        drawRoundRect(Color(0xB3000000), Offset(x - bw / 2, top), Size(bw, 5f))
        drawRoundRect(
            if (foe.boss) Danger else if (foe.elite) DivineGold else Mint,
            Offset(x - bw / 2, top),
            Size(bw * (foe.hp / foe.maxHp).coerceIn(0f, 1f), 5f)
        )
    }
}

private fun DrawScope.drawReticle(state: GameState, pulse: Float) {
    val target = state.nearest() ?: return
    val x = target.x * size.width
    val y = target.y * size.height
    val r = size.minDimension * .06f * (0.9f + pulse * .12f)
    repeat(4) { i ->
        val a = i * 90f + pulse * 40f
        rotate(a, Offset(x, y)) {
            drawLine(
                DivineGold.copy(alpha = .55f),
                Offset(x + r, y - r * .35f),
                Offset(x + r, y + r * .35f),
                2f
            )
        }
    }
}

private fun DrawScope.drawHero(state: GameState, images: Map<String, ImageBitmap?>, pulse: Float) {
    val hx = state.heroX * size.width
    val hy = state.heroY * size.height + sin(state.idle) * size.minDimension * .006f
    val h = size.minDimension * .19f
    val ringRadius = size.minDimension * .125f

    // Divine ground circle so the champion always reads as the centre of the arena.
    drawCircle(state.active.color.copy(alpha = .16f * pulse), ringRadius * 1.35f, Offset(hx, hy))
    drawCircle(
        state.active.color.copy(alpha = .35f),
        ringRadius,
        Offset(hx, hy + h * .30f),
        style = Stroke(2f)
    )
    drawOval(
        Color.Black.copy(alpha = .40f),
        topLeft = Offset(hx - h * .26f, hy + h * .34f),
        size = Size(h * .52f, h * .14f)
    )
    if (state.dashTrail > 0f) {
        drawCircle(Mint.copy(alpha = .32f * state.dashTrail), h * (.55f + (1f - state.dashTrail) * .7f), Offset(hx, hy))
    }

    images["sprites/hero.png"]?.let { image ->
        val w = h * image.width / image.height
        val left = (hx - w / 2).toInt()
        val top = (hy - h / 2).toInt()
        // Mirror the sprite so the champion always leans into the direction of travel.
        drawScale(if (state.facing < 0f) -1f else 1f, 1f, Offset(hx, hy)) {
            drawImage(
                image,
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(w.toInt(), h.toInt())
            )
        }
    } ?: drawCircle(DivineGold, h * .3f, Offset(hx, hy))

    // Impact flash when the hero takes damage.
    if (state.hurt > 0f) {
        drawCircle(Danger.copy(alpha = .30f * state.hurt), h * .45f, Offset(hx, hy))
    }

    // Vitality arc hugging the champion's feet.
    val gaugeRadius = ringRadius * 1.08f
    drawArc(
        color = Color(0x99040912),
        startAngle = 150f,
        sweepAngle = 240f,
        useCenter = false,
        topLeft = Offset(hx - gaugeRadius, hy + h * .30f - gaugeRadius),
        size = Size(gaugeRadius * 2, gaugeRadius * 2),
        style = Stroke(4f)
    )
    drawArc(
        color = if (state.hp > 35f) Mint else Danger,
        startAngle = 150f,
        sweepAngle = 240f * (state.hp / 100f).coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(hx - gaugeRadius, hy + h * .30f - gaugeRadius),
        size = Size(gaugeRadius * 2, gaugeRadius * 2),
        style = Stroke(4f)
    )

    // Orbiting gems: the shield ring and melee weapon in one.
    state.gems.forEachIndexed { index, gem ->
        val a = state.orbit + index * 6.283f / state.gems.size
        val x = hx + cos(a) * size.minDimension * .115f
        val y = hy + sin(a) * size.minDimension * .17f
        val selected = state.active == gem
        val s = size.minDimension * if (selected) .066f * pulse else .052f
        drawCircle(gem.color.copy(alpha = if (selected) .34f else .18f), s * .85f, Offset(x, y))
        images[gem.sprite]?.let {
            drawImage(
                it,
                dstOffset = IntOffset((x - s / 2).toInt(), (y - s / 2).toInt()),
                dstSize = IntSize(s.toInt(), s.toInt())
            )
        } ?: drawCircle(gem.color, s * .35f, Offset(x, y))
    }
}

@Composable
private fun Joystick(onMove: (Offset) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    Box(
        Modifier.size(128.dp).pointerInput(Unit) {
            val radius = size.width / 2.6f
            detectDragGestures(
                onDragStart = { p ->
                    active = true
                    val c = Offset(size.width / 2f, size.height / 2f)
                    knob = (p - c).limit(radius)
                    onMove(knob / radius)
                },
                onDrag = { change, drag ->
                    change.consume()
                    knob = (knob + drag).limit(radius)
                    onMove(knob / radius)
                },
                onDragEnd = { active = false; knob = Offset.Zero; onMove(Offset.Zero) },
                onDragCancel = { active = false; knob = Offset.Zero; onMove(Offset.Zero) }
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f
            drawCircle(Color(0x66081226), outer)
            drawCircle(Azure.copy(alpha = if (active) .55f else .3f), outer, style = Stroke(2.5f))
            repeat(4) { i ->
                val a = i * 90f * 0.0174533f + .78f
                drawLine(
                    Azure.copy(alpha = .18f),
                    center + Offset(cos(a) * outer * .55f, sin(a) * outer * .55f),
                    center + Offset(cos(a) * outer * .85f, sin(a) * outer * .85f),
                    2f
                )
            }
            val kc = center + knob
            drawCircle(DivineGold.copy(alpha = .28f), outer * .46f, kc)
            drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE7A8), Color(0xFFB1740D))), outer * .34f, kc)
            drawCircle(Color.White.copy(alpha = .45f), outer * .14f, kc - Offset(0f, outer * .1f))
        }
    }
}

private fun Offset.limit(max: Float): Offset {
    val len = hypot(x, y)
    return if (len <= max || len == 0f) this else Offset(x / len * max, y / len * max)
}

@Composable
private fun PauseOverlay(resume: () -> Unit, exit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xD2040912)), contentAlignment = Alignment.Center) {
        Panel(Modifier.width(330.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScreenTitle("PAUSED", "THE ORBIT AWAITS")
                Spacer(Modifier.height(14.dp))
                GoldButton("RESUME", Modifier.fillMaxWidth(), Tone.Gold, BtnSize.M, onClick = resume)
                Spacer(Modifier.height(8.dp))
                GoldButton("ABANDON RUN", Modifier.fillMaxWidth(), Tone.Danger, BtnSize.M, onClick = exit)
            }
        }
    }
}

@Composable
private fun ResultOverlay(state: GameState, exit: () -> Unit) {
    val glow by rememberInfiniteTransition(label = "result").animateFloat(
        .5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow"
    )
    Box(Modifier.fillMaxSize().background(Color(0xE0040912)), contentAlignment = Alignment.Center) {
        Panel(Modifier.width(400.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.victory) "DIVINE VICTORY" else "THE LIGHT FADES",
                    color = (if (state.victory) DivineGold else Danger).copy(alpha = glow),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(10.dp))
                GoldDivider()
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("WAVE", "${state.wave}")
                    Stat("SCORE", "${state.score}")
                    Stat("KILLS", "${state.kills}")
                    Stat("ESSENCE", "◆ ${state.essence}")
                }
                Spacer(Modifier.height(14.dp))
                GoldButton("RETURN TO OLYMPUS", Modifier.fillMaxWidth(), Tone.Gold, BtnSize.M, onClick = exit)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Marble, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, color = DivineGold.copy(alpha = .7f), fontSize = 8.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}
