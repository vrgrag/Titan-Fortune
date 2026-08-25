package com.titanfortune.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class Gem(val title: String, val sprite: String, val skill: String, val color: Color) {
    SAPPHIRE("SAPPHIRE", "sprites/sapphire.png", "Piercing divine bolt", Color(0xFF4A8DFF)),
    RUBY("RUBY", "sprites/ruby.png", "Explosive solar pulse", Color(0xFFFF4B45)),
    EMERALD("EMERALD", "sprites/emerald.png", "Chained storm arc", Color(0xFF48E47B)),
    AMETHYST("AMETHYST", "sprites/amethyst.png", "Temporal slowing field", Color(0xFFBF62FF)),
    DIAMOND("DIAMOND", "sprites/diamond.png", "Titan-breaking strike", Color(0xFFE8F5FF))
}

/** Plain class on purpose: combat relies on identity, not structural, equality when removing foes. */
class Foe(
    var x: Float,
    var y: Float,
    var hp: Float,
    val maxHp: Float,
    val speed: Float,
    val sprite: String,
    val elite: Boolean = false,
    val boss: Boolean = false,
    val ranged: Boolean = false,
    var hit: Float = 0f,
    var slow: Float = 0f,
    var attackClock: Float = 1.2f,
    var bob: Float = 0f
)

data class Spark(var x: Float, var y: Float, var life: Float, val maxLife: Float, val color: Color, val vx: Float, val vy: Float)
data class Bolt(var x: Float, var y: Float, val tx: Float, val ty: Float, var life: Float, val color: Color, val width: Float)
data class Shot(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float)
data class Pickup(var x: Float, var y: Float, var life: Float, val value: Int)
data class FloatText(var x: Float, var y: Float, var life: Float, val text: String, val color: Color, val scale: Float)

class GameState(val edition: GameEdition, start: Gem, val zone: Int) {
    var heroX by mutableFloatStateOf(.5f)
    var heroY by mutableFloatStateOf(.52f)
    var hp by mutableFloatStateOf(100f)
    var charge by mutableFloatStateOf(if (edition.advanced) 25f else 100f)
    var wave by mutableIntStateOf(1)
    var score by mutableIntStateOf(0)
    var essence by mutableIntStateOf(0)
    var orbit by mutableFloatStateOf(0f)
    var active by mutableStateOf(start)
    var paused by mutableStateOf(false)
    var overcharge by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var victory by mutableStateOf(false)
    var dashCooldown by mutableFloatStateOf(0f)
    var godCooldown by mutableFloatStateOf(0f)

    /** Red screen pulse right after the hero is damaged. */
    var hurt by mutableFloatStateOf(0f)

    /** -1 when the champion walks left, +1 when he walks right. Drives sprite mirroring. */
    var facing by mutableFloatStateOf(1f)

    /** Free-running clock for the idle breathing bob. */
    var idle by mutableFloatStateOf(0f)

    var dashTrail by mutableFloatStateOf(0f)
    var bannerTime by mutableFloatStateOf(0f)
    var bannerText by mutableStateOf("")
    var bannerSub by mutableStateOf("")
    var combo by mutableIntStateOf(0)
    var kills by mutableIntStateOf(0)
    var waveProgress by mutableFloatStateOf(0f)

    val enemies = mutableStateListOf<Foe>()
    val sparks = mutableStateListOf<Spark>()
    val bolts = mutableStateListOf<Bolt>()
    val shots = mutableStateListOf<Shot>()
    val pickups = mutableStateListOf<Pickup>()
    val texts = mutableStateListOf<FloatText>()
    val gems = if (edition.advanced) Gem.entries.toList() else listOf(start)

    /** Set by the view so combat can trigger audio without knowing about assets. */
    var onSound: ((String) -> Unit)? = null

    private val waveLength = 15f
    private var spawn = .6f
    private var waveClock = 0f
    private var comboClock = 0f
    private var autoClock = .4f
    private var bossCreated = false
    private var closing = 0f
    private val rng = Random(zone * 31 + 77)

    val dashReady get() = dashCooldown <= 0f
    val godReady get() = godCooldown <= 0f
    val chargeReady get() = !edition.advanced || charge >= 45f

    init {
        banner("WAVE 1", "SURVIVE THE ONSLAUGHT")
    }

    private fun banner(title: String, sub: String) {
        bannerText = title
        bannerSub = sub
        bannerTime = 2.2f
    }

    fun nearest(): Foe? = enemies.minByOrNull { hypot(it.x - heroX, it.y - heroY) }

    fun tick(dt: Float, moveX: Float, moveY: Float) {
        if (paused) return
        val d = dt.coerceIn(0f, .034f)

        // Keep the result overlay animating even after the run ends.
        if (finished) {
            decayEffects(d)
            return
        }

        val speed = if (moveX != 0f || moveY != 0f) 1f else 0f
        heroX = (heroX + moveX * d * .27f).coerceIn(.06f, .94f)
        heroY = (heroY + moveY * d * .40f).coerceIn(.16f, .88f)
        if (abs(moveX) > .15f) facing = if (moveX < 0f) -1f else 1f
        idle += d * (2.2f + speed * 3.4f)
        orbit = (orbit + d * (1.5f + speed * .4f)) % 6.283f
        charge = (charge + d * (6f + kills * .02f)).coerceAtMost(130f)
        overcharge = charge > 100f
        dashCooldown = (dashCooldown - d).coerceAtLeast(0f)
        godCooldown = (godCooldown - d).coerceAtLeast(0f)
        hurt = (hurt - d * 2.4f).coerceAtLeast(0f)
        dashTrail = (dashTrail - d * 3.5f).coerceAtLeast(0f)
        bannerTime = (bannerTime - d).coerceAtLeast(0f)

        comboClock = (comboClock - d).coerceAtLeast(0f)
        if (comboClock <= 0f) combo = 0

        advanceWave(d)
        autoAttack(d)
        moveEnemies(d)
        moveShots(d)
        collectPickups(d)
        decayEffects(d)

        if (hp <= 0f) {
            hp = 0f
            finish(false)
        }
    }

    private fun advanceWave(d: Float) {
        waveClock += d
        waveProgress = (waveClock / waveLength).coerceIn(0f, 1f)
        spawn -= d

        val cap = 8 + wave
        if (spawn <= 0f && enemies.size < cap && !bossCreated) {
            spawnEnemy()
            spawn = (1.7f - wave * .09f).coerceAtLeast(.5f)
        }
        if (waveClock >= waveLength && wave < edition.waves) {
            wave++
            waveClock = 0f
            score += 120
            banner("WAVE $wave", if (wave == edition.waves) "FINAL STAND" else "THE HORDE GROWS")
            onSound?.invoke("reward_received_asset.mp3")
        }
        if (wave >= edition.waves && !bossCreated) {
            if (edition.advanced) {
                spawnBoss()
                bossCreated = true
                banner("TITAN AWAKENS", "BREAK THE COLOSSUS")
                onSound?.invoke("zeus_ability_asset.mp3")
            } else {
                // The simple edition ends by clearing the last wave instead of a boss fight.
                closing += d
                if (closing > 6f && enemies.isEmpty()) finish(true)
            }
        }
    }

    /** The orbiting core fires on its own so the player focuses on movement and abilities. */
    private fun autoAttack(d: Float) {
        autoClock -= d
        if (autoClock > 0f) return
        val target = enemies.filter { hypot(it.x - heroX, it.y - heroY) < .42f }
            .minByOrNull { hypot(it.x - heroX, it.y - heroY) } ?: return
        autoClock = .5f
        damage(target, 13f + wave * 1.4f, small = true)
        bolts += Bolt(heroX, heroY, target.x, target.y, .13f, active.color, 3.5f)
    }

    private fun moveEnemies(d: Float) {
        val defeated = ArrayList<Foe>()
        enemies.toList().forEach { e ->
            val dx = heroX - e.x
            val dy = heroY - e.y
            val len = hypot(dx, dy).coerceAtLeast(.001f)
            val slowFactor = if (e.slow > 0f) .42f else 1f
            e.bob += d * 6f
            e.hit = (e.hit - d).coerceAtLeast(0f)
            e.slow = (e.slow - d).coerceAtLeast(0f)

            // Ranged foes hold their distance and volley instead of charging in.
            val keep = if (e.ranged) .30f else 0f
            if (len > keep) {
                e.x += dx / len * e.speed * slowFactor * d
                e.y += dy / len * e.speed * slowFactor * d
            }
            if (e.ranged) {
                e.attackClock -= d
                if (e.attackClock <= 0f && len < .55f) {
                    e.attackClock = 2.4f
                    val s = .34f
                    shots += Shot(e.x, e.y, dx / len * s, dy / len * s, 3f)
                    onSound?.invoke("electric_arc_asset.mp3")
                }
            } else if (len < .055f) {
                hurtHero(d * if (e.boss) 20f else if (e.elite) 14f else 8f)
            }

            gems.forEachIndexed { index, _ ->
                val angle = orbit + index * 6.283f / gems.size
                val gx = heroX + cos(angle) * .095f
                val gy = heroY + sin(angle) * .150f
                if (hypot(e.x - gx, e.y - gy) < .045f) {
                    e.hp -= d * 40f
                    e.hit = .08f
                }
            }
            if (e.hp <= 0f) defeated += e
        }
        defeated.forEach { kill(it) }
        if (defeated.isNotEmpty()) enemies.removeAll(defeated.toSet())
    }

    private fun moveShots(d: Float) {
        shots.toList().forEach { s ->
            s.x += s.vx * d
            s.y += s.vy * d
            s.life -= d
            if (hypot(s.x - heroX, s.y - heroY) < .04f) {
                hurtHero(9f)
                s.life = 0f
            }
        }
        shots.removeAll { it.life <= 0f || it.x < -.05f || it.x > 1.05f || it.y < -.05f || it.y > 1.05f }
    }

    private fun collectPickups(d: Float) {
        pickups.toList().forEach { p ->
            p.life -= d
            val dist = hypot(p.x - heroX, p.y - heroY)
            if (dist < .18f) {
                // Magnetise toward the hero once in range.
                p.x += (heroX - p.x) * d * 5f
                p.y += (heroY - p.y) * d * 5f
            }
            if (dist < .04f) {
                essence += p.value
                score += p.value * 5
                p.life = 0f
                texts += FloatText(p.x, p.y, .8f, "+${p.value}", Azure, .9f)
                onSound?.invoke("reward_received_asset.mp3")
            }
        }
        pickups.removeAll { it.life <= 0f }
    }

    private fun decayEffects(d: Float) {
        sparks.toList().forEach {
            it.life -= d
            it.x += it.vx * d
            it.y += it.vy * d
        }
        sparks.removeAll { it.life <= 0f }
        bolts.toList().forEach { it.life -= d }
        bolts.removeAll { it.life <= 0f }
        texts.toList().forEach {
            it.life -= d
            it.y -= d * .05f
        }
        texts.removeAll { it.life <= 0f }
    }

    private fun hurtHero(amount: Float) {
        hp -= amount
        hurt = 1f
        combo = 0
    }

    private fun damage(foe: Foe, amount: Float, small: Boolean = false) {
        val crit = !small && rng.nextFloat() < .18f
        val dealt = if (crit) amount * 1.8f else amount
        foe.hp -= dealt
        foe.hit = if (small) .1f else .22f
        if (!small || rng.nextFloat() < .5f) {
            texts += FloatText(
                foe.x, foe.y - .04f, .7f,
                dealt.toInt().toString(),
                if (crit) DivineGold else Marble,
                if (crit) 1.35f else .85f
            )
        }
        if (foe.hp <= 0f && enemies.contains(foe)) {
            kill(foe)
            enemies.remove(foe)
        }
    }

    private fun kill(foe: Foe) {
        kills++
        combo++
        comboClock = 3f
        val bonus = 1f + combo * .05f
        score += ((if (foe.boss) 1500 else if (foe.elite) 180 else 35) * bonus).toInt()
        charge = (charge + if (foe.elite) 20f else 9f).coerceAtMost(130f)
        val drop = if (foe.boss) 25 else if (foe.elite) 5 else 1
        pickups += Pickup(foe.x, foe.y, 9f, drop)
        repeat(if (foe.boss) 28 else 10) {
            val a = rng.nextFloat() * 6.283f
            val v = .05f + rng.nextFloat() * .18f
            sparks += Spark(foe.x, foe.y, .45f + rng.nextFloat() * .35f, .8f, active.color, cos(a) * v, sin(a) * v)
        }
        if (combo >= 5) texts += FloatText(heroX, heroY - .09f, .8f, "COMBO x$combo", DivineGold, 1f)
        if (foe.boss) finish(true)
    }

    private fun finish(won: Boolean) {
        if (finished) return
        victory = won
        finished = true
        onSound?.invoke(if (won) "level_complete_asset.mp3" else "defeat_asset.mp3")
    }

    fun discharge(): Boolean {
        if (finished || !chargeReady) return false
        val target = nearest() ?: return false
        val multiplier = if (overcharge) 1.5f else 1f
        when (active) {
            Gem.SAPPHIRE -> {
                val angle = atan2(target.y - heroY, target.x - heroX)
                enemies.toList().filter {
                    abs(atan2(it.y - heroY, it.x - heroX) - angle) < .25f
                }.forEach { damage(it, 38f * multiplier) }
            }
            Gem.RUBY -> enemies.toList().filter { hypot(it.x - target.x, it.y - target.y) < .24f }
                .forEach { damage(it, 32f * multiplier) }
            Gem.EMERALD -> enemies.toList().sortedBy { hypot(it.x - target.x, it.y - target.y) }.take(6)
                .forEach { damage(it, 26f * multiplier) }
            Gem.AMETHYST -> enemies.toList().filter { hypot(it.x - heroX, it.y - heroY) < .38f }
                .forEach { it.slow = 3.5f; damage(it, 16f * multiplier) }
            Gem.DIAMOND -> damage(target, 75f * multiplier * if (target.boss || target.elite) 1.35f else 1f)
        }
        bolts += Bolt(heroX, heroY, target.x, target.y, .26f, active.color, 8f)
        repeat(10) {
            val a = rng.nextFloat() * 6.283f
            sparks += Spark(target.x, target.y, .4f, .6f, active.color, cos(a) * .16f, sin(a) * .16f)
        }
        charge = 0f
        overcharge = false
        onSound?.invoke(
            when (active) {
                Gem.EMERALD -> "chain_reaction_asset.mp3"
                Gem.RUBY, Gem.AMETHYST -> "electric_arc_asset.mp3"
                else -> "lightning_launch_asset.mp3"
            }
        )
        return true
    }

    fun dash(dx: Float, dy: Float): Boolean {
        if (!edition.advanced || dashCooldown > 0f || finished) return false
        val len = hypot(dx, dy)
        val nx = if (len < .1f) 0f else dx / len
        val ny = if (len < .1f) -1f else dy / len
        heroX = (heroX + nx * .17f).coerceIn(.06f, .94f)
        heroY = (heroY + ny * .23f).coerceIn(.16f, .88f)
        dashCooldown = 1.6f
        dashTrail = 1f
        onSound?.invoke("reflector_energy_asset.mp3")
        return true
    }

    fun godPower(): Boolean {
        if (!edition.complete || godCooldown > 0f || finished) return false
        enemies.toList().forEach { damage(it, 30f) }
        repeat(24) {
            val a = rng.nextFloat() * 6.283f
            sparks += Spark(heroX, heroY, .6f, .8f, DivineGold, cos(a) * .3f, sin(a) * .3f)
        }
        texts += FloatText(heroX, heroY - .12f, 1f, "DIVINE WRATH", DivineGold, 1.3f)
        godCooldown = 14f
        onSound?.invoke("zeus_ability_asset.mp3")
        return true
    }

    private fun spawnEnemy() {
        // Spawn just outside the arena on a random edge.
        val side = rng.nextInt(4)
        val x = when (side) {
            0 -> .02f
            1 -> .98f
            else -> rng.nextFloat() * .84f + .08f
        }
        val y = when (side) {
            2 -> .10f
            3 -> .92f
            else -> rng.nextFloat() * .74f + .14f
        }
        val elite = edition.advanced && wave >= 3 && rng.nextFloat() < .14f
        val ranged = edition.advanced && wave >= 2 && !elite && rng.nextFloat() < .22f
        val names = listOf("hoplite", "minotaur", "spider", "golem", "storm_wolf")
        val flyers = listOf("harpy", "eagle")
        val sprite = when {
            elite -> if (rng.nextBoolean()) "elite_spear" else "elite_crystal"
            ranged -> flyers[rng.nextInt(flyers.size)]
            else -> names[rng.nextInt(names.size)]
        }
        val hp = if (elite) 130f + wave * 22f else 26f + wave * 8f
        val speed = if (elite) .030f else if (ranged) .034f else .026f + wave * .0016f
        enemies += Foe(x, y, hp, hp, speed, "sprites/$sprite.png", elite, false, ranged)
    }

    private fun spawnBoss() {
        val names = listOf("titan_earth", "titan_sea", "titan_void", "titan_storm")
        val hp = 1400f + zone * 240f
        enemies += Foe(.5f, .12f, hp, hp, .020f, "sprites/${names[zone % names.size]}.png", boss = true)
    }
}
