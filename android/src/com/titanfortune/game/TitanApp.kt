package com.titanfortune.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppScreen {
    SPLASH, MENU, EXPEDITION, GEMS, GAME, SETTINGS, UPGRADES, ARSENAL, GODS, TITANS, PRIVACY, SUPPORT
}

@Composable
fun TitanApp(assets: Assets, edition: GameEdition, lockLandscape: () -> Unit) {
    var screen by remember { mutableStateOf(AppScreen.SPLASH) }
    var zone by remember { mutableIntStateOf(0) }
    var gem by remember { mutableStateOf(Gem.SAPPHIRE) }
    var essence by remember { mutableIntStateOf(assets.essence) }
    var bestWave by remember { mutableIntStateOf(assets.bestWave) }

    BackHandler(screen != AppScreen.SPLASH && screen != AppScreen.MENU && screen != AppScreen.GAME) {
        screen = when (screen) {
            AppScreen.GEMS -> AppScreen.EXPEDITION
            AppScreen.PRIVACY, AppScreen.SUPPORT -> AppScreen.SETTINGS
            else -> AppScreen.MENU
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
        label = "screen"
    ) { current ->
        when (current) {
            AppScreen.SPLASH -> SplashView(assets) {
                lockLandscape()
                assets.startMusic()
                screen = AppScreen.MENU
            }
            AppScreen.MENU -> MainMenu(
                assets, edition, essence, bestWave,
                play = { screen = if (edition.advanced) AppScreen.EXPEDITION else AppScreen.GEMS },
                navigate = { screen = it }
            )
            AppScreen.EXPEDITION -> ExpeditionView(assets, essence, back = { screen = AppScreen.MENU }) {
                zone = it
                screen = AppScreen.GEMS
            }
            AppScreen.GEMS -> GemSelect(
                assets, edition,
                back = { screen = if (edition.advanced) AppScreen.EXPEDITION else AppScreen.MENU }
            ) {
                gem = it
                screen = AppScreen.GAME
            }
            AppScreen.GAME -> GameView(
                assets = assets,
                edition = edition,
                startGem = gem,
                zone = zone,
                onExit = { _, wave, earned ->
                    essence += earned
                    bestWave = maxOf(bestWave, wave)
                    assets.essence = essence
                    assets.bestWave = bestWave
                    screen = AppScreen.MENU
                }
            )
            AppScreen.SETTINGS -> SettingsView(
                assets,
                privacy = { screen = AppScreen.PRIVACY },
                support = { screen = AppScreen.SUPPORT },
                back = { screen = AppScreen.MENU }
            )
            AppScreen.UPGRADES -> UpgradeView(assets, essence, { essence = it; assets.essence = it }) {
                screen = AppScreen.MENU
            }
            AppScreen.ARSENAL -> CollectionView(
                assets, "GEM ARSENAL", "EVERY CORE REWRITES THE ORBIT",
                Gem.entries.map { Triple(it.title, it.sprite, it.skill) }
            ) { screen = AppScreen.MENU }
            AppScreen.GODS -> InfoCollection(
                assets, "DIVINE ABILITIES", "BLESSINGS OF THE PANTHEON",
                listOf(
                    Triple("ZEUS", "⚡", "Lightning damage and a divine storm that strikes every foe."),
                    Triple("POSEIDON", "🌊", "Tidal control with explosive force and heavy knockback."),
                    Triple("ATHENA", "🛡", "Precision, focus and slowed time around the arena."),
                    Triple("HADES", "💀", "Weakening aura that drains souls from the horde.")
                )
            ) { screen = AppScreen.MENU }
            AppScreen.TITANS -> CollectionView(
                assets, "TITAN COLLECTION", "COLOSSI THAT GUARD EACH REALM",
                listOf(
                    Triple("EARTH TITAN", "sprites/titan_earth.png", "Stone hide, crushing reach"),
                    Triple("SEA TITAN", "sprites/titan_sea.png", "Tidal surges and undertow"),
                    Triple("VOID TITAN", "sprites/titan_void.png", "Devours light and essence"),
                    Triple("STORM TITAN", "sprites/titan_storm.png", "Rides the lightning itself")
                )
            ) { screen = AppScreen.MENU }
            AppScreen.PRIVACY -> LegalWebView(
                title = "PRIVACY POLICY",
                remoteUrl = LegalPages.PRIVACY_URL,
                localUrl = LegalPages.PRIVACY_ASSET,
                back = { screen = AppScreen.SETTINGS }
            )
            AppScreen.SUPPORT -> LegalWebView(
                title = "SUPPORT",
                remoteUrl = LegalPages.SUPPORT_URL,
                localUrl = LegalPages.SUPPORT_ASSET,
                back = { screen = AppScreen.SETTINGS }
            )
        }
    }
}

@Composable
private fun SplashView(assets: Assets, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var stage by remember { mutableStateOf("Initializing divine engine") }
    var dots by remember { mutableIntStateOf(1) }
    val config = LocalConfiguration.current
    val portrait = config.screenHeightDp > config.screenWidthDp
    val bg = remember(portrait) {
        assets.splash(if (portrait) "Vertical_Loading_Screen.png" else "Horizontal_Loading_Screen.png")
            ?.asImageBitmap()
    }
    val shimmer by rememberInfinite(.35f, 1f, 900)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                while (progress < 1f) {
                    delay(340)
                    dots = dots % 3 + 1
                }
            }
            assets.loadAll { p, s ->
                progress = p
                stage = s
            }
        }
        delay(300)
        assets.finishSplash()
        onDone()
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        bg?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Night.copy(alpha = .9f)))
            )
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(.62f).padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "LOADING${".".repeat(dots)}",
                color = DivineGold.copy(alpha = shimmer),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(8.dp))
            Meter(progress, listOf(DeepGold, DivineGold, Color.White), 12.dp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stage, color = Marble.copy(alpha = .6f), fontSize = 9.sp, maxLines = 1)
                Text("${(progress * 100).toInt()}%", color = DivineGold.copy(alpha = .8f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MainMenu(
    assets: Assets,
    edition: GameEdition,
    essence: Int,
    bestWave: Int,
    play: () -> Unit,
    navigate: (AppScreen) -> Unit
) {
    Backdrop(assets, "bg/olympus_background_asset.png") {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: identity block
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                assets.bitmap("ui/Game_Name.png")?.let {
                    Image(it.asImageBitmap(), null, Modifier.height(112.dp), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "THE TITANS HAVE AWAKENED",
                    color = Marble, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.sp
                )
                Text(
                    "Command the living constellation of divine gems.",
                    color = Marble.copy(alpha = .62f), fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("◆ $essence  ESSENCE")
                    Chip("BEST WAVE $bestWave", Azure)
                    Chip(edition.title, Mint)
                }
            }

            Spacer(Modifier.width(20.dp))

            // Right: action deck
            Panel(Modifier.width(316.dp), padding = 16.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScreenTitle("OLYMPUS", "CHOOSE YOUR DESTINY")
                    Spacer(Modifier.height(10.dp))
                    GoldDivider()
                    Spacer(Modifier.height(12.dp))
                    GoldButton("BEGIN EXPEDITION", Modifier.fillMaxWidth(), Tone.Gold, BtnSize.L, onClick = play)
                    if (edition.complete) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoldButton("UPGRADES", Modifier.weight(1f), size = BtnSize.M) { navigate(AppScreen.UPGRADES) }
                            GoldButton("ARSENAL", Modifier.weight(1f), size = BtnSize.M) { navigate(AppScreen.ARSENAL) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoldButton("GODS", Modifier.weight(1f), size = BtnSize.M) { navigate(AppScreen.GODS) }
                            GoldButton("TITANS", Modifier.weight(1f), size = BtnSize.M) { navigate(AppScreen.TITANS) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GoldButton("SETTINGS", Modifier.fillMaxWidth(), Tone.Ghost, BtnSize.M) {
                        navigate(AppScreen.SETTINGS)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpeditionView(assets: Assets, essence: Int, back: () -> Unit, select: (Int) -> Unit) {
    val zones = listOf(
        Triple("GREAT OLYMPUS", "Marble arena of the gods", "bg/olympus_marble_arena_asset.png"),
        Triple("TEMPLE OF ZEUS", "Thunder rolls through the halls", "bg/zeus_temple_background_asset.png"),
        Triple("SKY ISLANDS", "Floating ruins above the clouds", "bg/sky_islands_background_asset.png"),
        Triple("STORM PEAKS", "Where titans were chained", "bg/storm_sky_background_asset.png")
    )
    Backdrop(assets, "bg/sky_islands_background_asset.png") {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopBar("SELECT EXPEDITION", "DANGER RISES WITH EACH REALM", back) {
                Chip("◆ $essence")
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                zones.forEachIndexed { index, (title, note, art) ->
                    ZoneCard(assets, index, title, note, art, Modifier.weight(1f)) { select(index) }
                }
            }
        }
    }
}

@Composable
private fun ZoneCard(
    assets: Assets,
    index: Int,
    title: String,
    note: String,
    art: String,
    modifier: Modifier,
    enter: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(Night)
            .border(1.dp, DivineGold.copy(alpha = .45f), shape)
            .clickable {
                Assets.current?.click()
                enter()
            }
    ) {
        assets.bitmap(art)?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Night.copy(alpha = .35f), Night.copy(alpha = .92f)))
            )
        )
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "0${index + 1}",
                color = DivineGold.copy(alpha = .35f),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(title, color = DivineGold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            Text(note, color = Marble.copy(alpha = .6f), fontSize = 9.sp, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("${5 + index * 3} WAVES", Azure)
                Chip(if (index == 0) "INITIATE" else "PWR ${120 + index * 80}", Mint)
            }
            Spacer(Modifier.height(8.dp))
            GoldButton("ENTER", Modifier.fillMaxWidth(), Tone.Gold, BtnSize.S, onClick = enter)
        }
    }
}

@Composable
private fun GemSelect(assets: Assets, edition: GameEdition, back: () -> Unit, select: (Gem) -> Unit) {
    val gems = if (edition.advanced) Gem.entries.toList() else listOf(Gem.SAPPHIRE)
    var focus by remember { mutableStateOf(gems.first()) }
    Backdrop(assets, "bg/zeus_temple_background_asset.png") {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopBar("CHOOSE YOUR CORE", "YOUR FIRST GEM DEFINES THE EXPEDITION", back) {
                GoldButton("BIND ${focus.title}", tone = Tone.Gold, size = BtnSize.S) { select(focus) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                gems.forEach { gem ->
                    GemCard(assets, gem, gem == focus, Modifier.weight(1f), { focus = gem }) { select(gem) }
                }
            }
        }
    }
}

@Composable
private fun GemCard(
    assets: Assets,
    gem: Gem,
    selected: Boolean,
    modifier: Modifier,
    focus: () -> Unit,
    bind: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (selected) listOf(gem.color.copy(alpha = .30f), Color(0xF0060D1F))
                    else listOf(Color(0xD0121F3B), Color(0xF0060D1F))
                )
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) gem.color else DivineGold.copy(alpha = .3f), shape)
            .clickable {
                Assets.current?.click()
                focus()
            }
            .padding(10.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(if (selected) 84.dp else 72.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(gem.color.copy(alpha = .28f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                assets.bitmap(gem.sprite)?.let {
                    Image(it.asImageBitmap(), null, Modifier.size(if (selected) 68.dp else 58.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(gem.title, color = gem.color, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
            Text(
                gem.skill,
                color = Marble.copy(alpha = .65f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
            Spacer(Modifier.weight(1f))
            GoldButton(
                if (selected) "BIND" else "SELECT",
                Modifier.fillMaxWidth(),
                if (selected) Tone.Gold else Tone.Steel,
                BtnSize.S
            ) { if (selected) bind() else focus() }
        }
    }
}

@Composable
private fun SettingsView(assets: Assets, privacy: () -> Unit, support: () -> Unit, back: () -> Unit) {
    var music by remember { mutableFloatStateOf(assets.musicVolume) }
    var sfx by remember { mutableFloatStateOf(assets.sfxVolume) }
    Backdrop(assets, "bg/storm_sky_background_asset.png") {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopBar("SETTINGS", "DIVINE CONFIGURATION", back)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Panel(Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                    Column {
                        Text("AUDIO", color = DivineGold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        SettingSlider("MUSIC", music) { music = it; assets.musicVolume = it }
                        SettingSlider("EFFECTS", sfx) {
                            sfx = it
                            assets.sfxVolume = it
                        }
                        Spacer(Modifier.height(4.dp))
                        GoldDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Gameplay is offline. Privacy Policy and Support open in a local WebView.",
                            color = Marble.copy(alpha = .6f), fontSize = 10.sp, lineHeight = 14.sp
                        )
                    }
                }
                Panel(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                    Column(Modifier.fillMaxSize()) {
                        Text("LEGAL & HELP", color = DivineGold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))
                        GoldButton("PRIVACY POLICY", Modifier.fillMaxWidth(), size = BtnSize.M, onClick = privacy)
                        Spacer(Modifier.height(8.dp))
                        GoldButton("SUPPORT", Modifier.fillMaxWidth(), size = BtnSize.M, onClick = support)
                        Spacer(Modifier.weight(1f))
                        Text("VERSION 1.0", color = Marble.copy(alpha = .4f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, change: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.width(78.dp), color = Marble, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Slider(
            value = value,
            onValueChange = change,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = DivineGold,
                activeTrackColor = DeepGold,
                inactiveTrackColor = Color(0x66FFFFFF)
            )
        )
        Text(
            "${(value * 100).toInt()}",
            Modifier.width(34.dp),
            color = DivineGold,
            fontSize = 10.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun UpgradeView(assets: Assets, essence: Int, update: (Int) -> Unit, back: () -> Unit) {
    var levels by remember { mutableStateOf(listOf(0, 0, 0)) }
    val upgrades = listOf(
        Triple("VITALITY", "+12 max vitality per rank", Mint),
        Triple("DIVINE DAMAGE", "+8% gem damage per rank", Danger),
        Triple("ORBIT SPEED", "+6% orbit velocity per rank", Azure)
    )
    Backdrop(assets, "bg/storm_sky_background_asset.png") {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopBar("DIVINE ASCENSION", "SPEND ESSENCE TO EMPOWER THE HERO", back) {
                Chip("◆ $essence  ESSENCE")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                upgrades.forEachIndexed { index, (name, note, tint) ->
                    val level = levels[index]
                    val cost = 8 + level * 6
                    Panel(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, color = tint, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                            Text(note, color = Marble.copy(alpha = .6f), fontSize = 9.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                                RingGauge((level / 10f), tint, Modifier.fillMaxSize())
                                Text("$level", color = Marble, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            GoldButton(
                                "ASCEND ◆ $cost",
                                Modifier.fillMaxWidth(),
                                Tone.Gold,
                                BtnSize.S,
                                enabled = essence >= cost && level < 10
                            ) {
                                update(essence - cost)
                                levels = levels.toMutableList().also { it[index] = level + 1 }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionView(
    assets: Assets,
    title: String,
    subtitle: String,
    items: List<Triple<String, String, String>>,
    back: () -> Unit
) {
    Backdrop(assets, "bg/olympus_background_asset.png") {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopBar(title, subtitle, back)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { (name, path, note) ->
                    Panel(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.weight(1f))
                            assets.bitmap(path)?.let {
                                Image(it.asImageBitmap(), null, Modifier.size(96.dp), contentScale = ContentScale.Fit)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(name, color = DivineGold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                            Text(
                                note,
                                color = Marble.copy(alpha = .6f),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCollection(
    assets: Assets,
    title: String,
    subtitle: String,
    items: List<Triple<String, String, String>>,
    back: () -> Unit
) {
    Backdrop(assets, "bg/zeus_temple_background_asset.png") {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TopBar(title, subtitle, back)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { (name, glyph, body) ->
                    Panel(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.weight(1f))
                            Text(glyph, fontSize = 34.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(name, color = DivineGold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                body,
                                color = Marble.copy(alpha = .65f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Backdrop(assets: Assets, path: String, content: @Composable BoxScope.() -> Unit) {
    val glow by rememberInfinite(.25f, .6f, 2400)
    Box(Modifier.fillMaxSize().background(Night)) {
        assets.bitmap(path)?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Night.copy(alpha = .78f), Night.copy(alpha = .55f), Night.copy(alpha = .88f)))
            )
        )
        StarField(glow)
        content()
    }
}

@Composable
private fun rememberInfinite(from: Float, to: Float, durationMs: Int) =
    rememberInfiniteTransition(label = "loop").animateFloat(
        from, to, infiniteRepeatable(tween(durationMs), RepeatMode.Reverse), label = "value"
    )
