package com.titanfortune.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class Assets(private val context: Context) {
    private val images = ConcurrentHashMap<String, Bitmap>()
    private val sounds = ConcurrentHashMap<String, Int>()
    private val prefs = context.getSharedPreferences("titan_fortune", Context.MODE_PRIVATE)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private var music: MediaPlayer? = null

    var sfxVolume: Float = prefs.getFloat("sfx", .85f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("sfx", field).apply()
        }
    var musicVolume: Float = prefs.getFloat("music", .5f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("music", field).apply()
            music?.setVolume(field, field)
        }

    var essence: Int
        get() = prefs.getInt("essence", 0)
        set(value) = prefs.edit().putInt("essence", value).apply()
    var bestWave: Int
        get() = prefs.getInt("best_wave", 0)
        set(value) = prefs.edit().putInt("best_wave", value).apply()

    private val imageFiles = listOf(
        "ui/Game_Name.png", "bg/olympus_background_asset.png",
        "bg/olympus_marble_arena_asset.png", "bg/zeus_temple_background_asset.png",
        "bg/sky_islands_background_asset.png", "bg/storm_sky_background_asset.png",
        "sprites/hero.png", "sprites/sapphire.png", "sprites/ruby.png",
        "sprites/emerald.png", "sprites/amethyst.png", "sprites/diamond.png",
        "sprites/hoplite.png", "sprites/minotaur.png", "sprites/harpy.png",
        "sprites/spider.png", "sprites/eagle.png", "sprites/golem.png",
        "sprites/storm_wolf.png", "sprites/spearman.png",
        "sprites/elite_spear.png", "sprites/elite_crystal.png",
        "sprites/titan_earth.png", "sprites/titan_sea.png",
        "sprites/titan_void.png", "sprites/titan_storm.png",
        "sprites/olympian_essence_shard_asset_0.png",
        "sprites/greek_columns_set_asset_0.png",
        "sprites/greek_columns_set_asset_1.png",
        "sprites/magical_storm_crystal_asset_0.png"
    )
    private val soundFiles = listOf(
        "button_click_asset.mp3", "lightning_launch_asset.mp3",
        "electric_arc_asset.mp3", "chain_reaction_asset.mp3",
        "reflector_energy_asset.mp3", "reward_received_asset.mp3",
        "level_complete_asset.mp3", "defeat_asset.mp3", "zeus_ability_asset.mp3"
    )

    init {
        current = this
    }

    fun splash(name: String): Bitmap? = loadBitmap("ui/$name")

    suspend fun loadAll(onProgress: (Float, String) -> Unit) = withContext(Dispatchers.IO) {
        val total = imageFiles.size + soundFiles.size + 2
        var done = 0
        fun report(stage: String) {
            done++
            onProgress((done.toFloat() / total).coerceAtMost(.98f), stage)
        }
        imageFiles.forEach { path ->
            if (!images.contains(path)) loadBitmap(path)
            report("Forging ${path.substringAfterLast('/')}")
        }
        soundFiles.forEach { name ->
            runCatching {
                val fd = context.assets.openFd("audio/$name")
                sounds[name] = soundPool.load(fd, 1)
                fd.close()
            }
            report("Tuning divine sound")
        }
        report("Awakening Olympus")
        report("Ready")
        onProgress(1f, "Ready")
    }

    private fun loadBitmap(path: String): Bitmap? {
        images[path]?.let { return it }
        // Backdrops never use transparency, so half-weight RGB_565 keeps the heap small.
        val options = BitmapFactory.Options().apply {
            inPreferredConfig =
                if (path.startsWith("bg/")) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
                ?.also { images[path] = it }
        }.getOrNull()
    }

    fun bitmap(path: String): Bitmap? = images[path] ?: loadBitmap(path)

    /**
     * Drops the splash artwork references. The bitmaps are never recycled here because Compose
     * can still be holding them for the frame that is currently being drawn.
     */
    fun finishSplash() {
        images.remove("ui/Horizontal_Loading_Screen.png")
        images.remove("ui/Vertical_Loading_Screen.png")
    }

    fun play(name: String, volume: Float = 1f) {
        val v = volume * sfxVolume
        if (v <= 0f) return
        sounds[name]?.let { soundPool.play(it, v, v, 1, 0, 1f) }
    }

    fun click() = play("button_click_asset.mp3", .7f)

    fun startMusic() {
        if (music != null) return
        runCatching {
            val fd = context.assets.openFd("audio/background_music_asset.mp3")
            music = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                isLooping = true
                setVolume(musicVolume, musicVolume)
                prepare()
                start()
            }
            fd.close()
        }
    }

    fun release() {
        music?.release()
        music = null
        soundPool.release()
        images.clear()
        if (current === this) current = null
    }

    companion object {
        /** Lets shared UI widgets fire feedback sounds without threading the instance everywhere. */
        var current: Assets? = null
            private set
    }
}
