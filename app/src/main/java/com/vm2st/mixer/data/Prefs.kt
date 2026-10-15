package com.vm2st.mixer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Три темы оформления. Тёмная — по умолчанию. */
enum class ThemeMode(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    BORDEAUX("Бордовая");

    companion object {
        fun fromName(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: DARK
    }
}

/** Режим движения слайдера. */
enum class SliderMode(val label: String, val short: String, val hint: String) {
    FAST("Быстро", "за пальцем", "Слайдер идёт ровно за пальцем"),
    SMOOTH("Плавно", "плавно", "Слайдер мягко догоняет палец");

    companion object {
        fun fromName(value: String?): SliderMode = entries.firstOrNull { it.name == value } ?: FAST
    }
}

enum class SliderId { VOLUME, BASS }

/** Плитка спецэффекта. */
data class EffectTile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String,
    val hidden: Boolean = false,
    /** имя ресурса в res/raw для встроенного эффекта */
    val builtInRaw: String? = null,
    /** абсолютный путь к своему аудиофайлу */
    val filePath: String? = null,
    val durationMs: Long = 0L,
    val custom: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", colorHex)
        put("hidden", hidden)
        put("raw", builtInRaw ?: JSONObject.NULL)
        put("path", filePath ?: JSONObject.NULL)
        put("duration", durationMs)
        put("custom", custom)
    }

    companion object {
        fun fromJson(json: JSONObject): EffectTile = EffectTile(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Эффект"),
            colorHex = json.optString("color", "#2E86AB"),
            hidden = json.optBoolean("hidden", false),
            builtInRaw = if (json.isNull("raw")) null else json.optString("raw"),
            filePath = if (json.isNull("path")) null else json.optString("path"),
            durationMs = json.optLong("duration", 0L),
            custom = json.optBoolean("custom", false)
        )
    }
}

/** Четыре встроенных коротких эффекта. */
fun defaultTiles(): List<EffectTile> = listOf(
    EffectTile(id = "builtin-kick", name = "Kick", colorHex = "#FF3B30", builtInRaw = "fx_kick", durationMs = 550),
    EffectTile(id = "builtin-clap", name = "Clap", colorHex = "#2E86AB", builtInRaw = "fx_clap", durationMs = 500),
    EffectTile(id = "builtin-riser", name = "Riser", colorHex = "#6A4C93", builtInRaw = "fx_riser", durationMs = 1400),
    EffectTile(id = "builtin-zap", name = "Zap", colorHex = "#F4A259", builtInRaw = "fx_zap", durationMs = 420)
)

/** Палитра для смены цвета плиток. */
val TILE_PALETTE: List<String> = listOf(
    "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE", "#2E86AB",
    "#6A4C93", "#FF2D92", "#F4A259", "#892929", "#8E8E93", "#4C6EF5"
)

const val MAX_CLIP_MS: Long = 15_000L
const val DEFAULT_BASS: Float = 0.35f

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("vmixer_prefs", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = ThemeMode.fromName(sp.getString(KEY_THEME, null))
        set(value) = sp.edit().putString(KEY_THEME, value.name).apply()

    var volume: Float
        get() = sp.getFloat(KEY_VOLUME, 0.8f)
        set(value) = sp.edit().putFloat(KEY_VOLUME, value).apply()

    var bass: Float
        get() = sp.getFloat(KEY_BASS, DEFAULT_BASS)
        set(value) = sp.edit().putFloat(KEY_BASS, value).apply()

    var volumeMode: SliderMode
        get() = SliderMode.fromName(sp.getString(KEY_VOLUME_MODE, null))
        set(value) = sp.edit().putString(KEY_VOLUME_MODE, value.name).apply()

    var bassMode: SliderMode
        get() = SliderMode.fromName(sp.getString(KEY_BASS_MODE, null))
        set(value) = sp.edit().putString(KEY_BASS_MODE, value.name).apply()

    var autoPauseOnUnplug: Boolean
        get() = sp.getBoolean(KEY_AUTO_PAUSE, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_PAUSE, value).apply()

    var tiles: List<EffectTile>
        get() {
            val raw = sp.getString(KEY_TILES, null) ?: return defaultTiles()
            val parsed = runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    runCatching { EffectTile.fromJson(array.getJSONObject(index)) }.getOrNull()
                }
            }.getOrDefault(emptyList())
            return parsed.ifEmpty { defaultTiles() }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(it.toJson()) }
            sp.edit().putString(KEY_TILES, array.toString()).apply()
        }

    fun resetAll() {
        theme = ThemeMode.DARK
        volumeMode = SliderMode.FAST
        bassMode = SliderMode.FAST
        bass = DEFAULT_BASS
        autoPauseOnUnplug = true
        tiles = defaultTiles()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_VOLUME = "volume"
        const val KEY_BASS = "bass"
        const val KEY_VOLUME_MODE = "volume_mode"
        const val KEY_BASS_MODE = "bass_mode"
        const val KEY_AUTO_PAUSE = "auto_pause"
        const val KEY_TILES = "tiles"
    }
}
