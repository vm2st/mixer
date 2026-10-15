package com.vm2st.mixer.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vm2st.mixer.audio.AudioEngine
import com.vm2st.mixer.audio.OutputRoute
import com.vm2st.mixer.data.DEFAULT_BASS
import com.vm2st.mixer.data.EffectTile
import com.vm2st.mixer.data.MAX_CLIP_MS
import com.vm2st.mixer.data.Prefs
import com.vm2st.mixer.data.SliderId
import com.vm2st.mixer.data.SliderMode
import com.vm2st.mixer.data.TILE_PALETTE
import com.vm2st.mixer.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class VmixerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val engine = AudioEngine(application)

    var theme by mutableStateOf(prefs.theme)
        private set
    var volume by mutableStateOf(prefs.volume)
        private set
    var bass by mutableStateOf(prefs.bass)
        private set
    var volumeMode by mutableStateOf(prefs.volumeMode)
        private set
    var bassMode by mutableStateOf(prefs.bassMode)
        private set
    var tiles by mutableStateOf(prefs.tiles)
        private set
    var playingTileId by mutableStateOf<String?>(null)
        private set
    var route by mutableStateOf(OutputRoute.SPEAKER)
        private set
    var routeName by mutableStateOf<String?>(null)
        private set
    var autoPauseOnUnplug by mutableStateOf(prefs.autoPauseOnUnplug)
        private set
    var maxVolumeIndex by mutableStateOf(engine.maxVolumeIndex)
        private set
    var systemVolumeIndex by mutableStateOf(engine.volumeIndex)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Счётчики внешних изменений слайдеров: пресет, сброс, системная громкость. */
    var volumeSyncTick by mutableStateOf(0)
        private set
    var bassSyncTick by mutableStateOf(0)
        private set

    val visibleTiles: List<EffectTile> get() = tiles.filter { !it.hidden }
    val hiddenTiles: List<EffectTile> get() = tiles.filter { it.hidden }
    val hiddenCount: Int get() = tiles.count { it.hidden }
    val activeSessionId: Int get() = engine.sessionId

    /** Ожидающие значения для троттлинга: системные вызовы не чаще 30 раз в секунду. */
    private var pendingVolume: Float? = null
    private var pendingBass: Float? = null
    private var persistJob: Job? = null

    /** Сигнал «есть новое значение». Пока изменений нет, обработчик спит. */
    private val audioUpdates = Channel<Unit>(Channel.CONFLATED)

    init {
        engine.listener = object : AudioEngine.Listener {
            override fun onRouteChanged(route: OutputRoute, deviceName: String?) {
                this@VmixerViewModel.route = route
                routeName = deviceName
                message = if (deviceName.isNullOrBlank()) {
                    "Звук идёт на: ${route.label}"
                } else {
                    "Звук идёт на: ${route.label} · $deviceName"
                }
            }

            override fun onOutputLost() {
                if (autoPauseOnUnplug && playingTileId != null) {
                    stopPlayback()
                    message = "Вывод отключён — воспроизведение остановлено"
                }
            }

            override fun onPlaybackFinished() {
                // Запоздалый сигнал от предыдущего эффекта не должен гасить подсветку
                // плитки, которая играет сейчас.
                if (!engine.isPlaying) playingTileId = null
            }
        }

        // Значения применяются пачками, не чаще 30 раз в секунду: каждый вызов —
        // это обращение к аудиосервису, а слайдер шлёт значение каждый кадр.
        viewModelScope.launch {
            for (signal in audioUpdates) {
                pendingVolume?.let { value ->
                    pendingVolume = null
                    engine.setVolumeFraction(value)
                }
                pendingBass?.let { value ->
                    pendingBass = null
                    engine.setBassFraction(value)
                }
                delay(THROTTLE_MS)
            }
        }

        engine.setBassFraction(bass)
        // громкость берём из системы, чтобы слайдер показывал реальное значение
        maxVolumeIndex = engine.maxVolumeIndex
        systemVolumeIndex = engine.volumeIndex
        volume = engine.currentVolumeFraction()
        route = engine.lastRoute
        routeName = engine.lastRouteName
    }

    // ------------------------------------------------------------------- тема

    fun applyTheme(mode: ThemeMode) {
        theme = mode
        prefs.theme = mode
    }

    // -------------------------------------------------------------- слайдеры

    fun changeVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        volume = clamped
        pendingVolume = clamped
        audioUpdates.trySend(Unit)
        systemVolumeIndex = (clamped * maxVolumeIndex).roundToInt()
        schedulePersist()
    }

    fun changeBass(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        bass = clamped
        pendingBass = clamped
        audioUpdates.trySend(Unit)
        schedulePersist()
    }

    /**
     * Запись настроек не чаще, чем раз в [PERSIST_DELAY_MS]: во время движения
     * слайдера значения меняются каждый кадр, и писать их на диск каждый кадр незачем.
     */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DELAY_MS)
            prefs.volume = volume
            prefs.bass = bass
        }
    }

    /** Изменение не слайдером: пресет или сброс — слайдер должен подхватить значение. */
    fun setVolumeFromOutside(value: Float) {
        changeVolume(value)
        volumeSyncTick++
    }

    fun setBassFromOutside(value: Float) {
        changeBass(value)
        bassSyncTick++
    }

    fun setSliderMode(id: SliderId, mode: SliderMode) {
        when (id) {
            SliderId.VOLUME -> {
                volumeMode = mode
                prefs.volumeMode = mode
            }

            SliderId.BASS -> {
                bassMode = mode
                prefs.bassMode = mode
            }
        }
    }

    /** Значение по умолчанию для сброса долгим нажатием. */
    fun resetVolume() = setVolumeFromOutside(0.8f)

    fun resetBass() = setBassFromOutside(DEFAULT_BASS)

    fun refreshSystemVolume() {
        maxVolumeIndex = engine.maxVolumeIndex
        systemVolumeIndex = engine.volumeIndex
        val actual = engine.currentVolumeFraction()
        volume = actual
        prefs.volume = actual
        volumeSyncTick++
    }

    // ------------------------------------------------------------- маршрут

    fun refreshRoute() {
        engine.refreshRoute()
        route = engine.lastRoute
        routeName = engine.lastRouteName
        message = if (routeName.isNullOrBlank()) {
            "Текущий вывод: ${route.label}"
        } else {
            "Текущий вывод: ${route.label} · $routeName"
        }
    }

    fun updateAutoPause(enabled: Boolean) {
        autoPauseOnUnplug = enabled
        prefs.autoPauseOnUnplug = enabled
    }

    // ------------------------------------------------------------ эффекты

    fun playTile(tile: EffectTile) {
        playingTileId = tile.id
        val raw = tile.builtInRaw
        if (raw != null) {
            engine.playRawResource(raw)
            return
        }
        val path = tile.filePath
        val file = if (path != null) File(path) else null
        if (file == null || !file.exists()) {
            playingTileId = null
            message = "Файл эффекта не найден"
            return
        }
        engine.playFile(file)
    }

    fun stopPlayback() {
        engine.stop()
        playingTileId = null
    }

    fun setTileColor(id: String, colorHex: String) {
        tiles = tiles.map { if (it.id == id) it.copy(colorHex = colorHex) else it }
        persistTiles()
    }

    fun hideTile(id: String) {
        tiles = tiles.map { if (it.id == id) it.copy(hidden = true) else it }
        persistTiles()
        message = "Плитка скрыта — вернуть можно через меню"
    }

    fun restoreTile(id: String) {
        tiles = tiles.map { if (it.id == id) it.copy(hidden = false) else it }
        persistTiles()
        message = "Плитка возвращена"
    }

    fun restoreAllHidden() {
        if (hiddenCount == 0) {
            message = "Скрытых плиток нет"
            return
        }
        tiles = tiles.map { it.copy(hidden = false) }
        persistTiles()
        message = "Возвращено плиток: $hiddenCount"
    }

    fun deleteTile(id: String) {
        val tile = tiles.firstOrNull { it.id == id } ?: return
        if (!tile.custom) {
            hideTile(id)
            return
        }
        tile.filePath?.let { path -> runCatching { File(path).delete() } }
        tiles = tiles.filterNot { it.id == id }
        persistTiles()
        if (playingTileId == id) stopPlayback()
        message = "Свой эффект удалён"
    }

    fun addCustomTile(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { importClip(uri) }.getOrElse { error ->
                    Result.failure(
                        IllegalStateException(error.message ?: "Не удалось добавить эффект")
                    )
                }
            }
            result.fold(
                onSuccess = { tile ->
                    tiles = tiles + tile
                    persistTiles()
                    val seconds = String.format(Locale.US, "%.1f", tile.durationMs / 1000.0)
                    message = "Добавлен эффект «${tile.name}» ($seconds с)"
                },
                onFailure = { error -> message = error.message ?: "Не удалось добавить эффект" }
            )
        }
    }

    private fun importClip(uri: Uri): Result<EffectTile> {
        val resolver = getApplication<Application>().contentResolver
        val target = File(
            File(getApplication<Application>().filesDir, "effects").apply { mkdirs() },
            "clip_${System.currentTimeMillis()}.${extensionFor(resolver.getType(uri))}"
        )

        val stream = resolver.openInputStream(uri)
        if (stream == null) {
            target.delete()
            return Result.failure(IllegalStateException("Не удалось открыть файл"))
        }
        val copyError = runCatching {
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.exceptionOrNull()
        if (copyError != null) {
            target.delete()
            return Result.failure(
                IllegalStateException("Не удалось прочитать файл: ${copyError.message ?: "ошибка"}")
            )
        }

        val duration = readDuration(target)
        if (duration <= 0L) {
            target.delete()
            return Result.failure(IllegalStateException("Не удалось определить длительность файла"))
        }
        if (duration > MAX_CLIP_MS) {
            target.delete()
            val seconds = String.format(Locale.US, "%.1f", duration / 1000.0)
            return Result.failure(
                IllegalStateException("Файл длиннее 15 секунд ($seconds с) — нужен короткий эффект")
            )
        }

        val name = displayName(resolver, uri)?.take(22)?.trim().orEmpty().ifEmpty { "Свой эффект" }
        return Result.success(
            EffectTile(
                name = name,
                colorHex = TILE_PALETTE.random(),
                filePath = target.absolutePath,
                durationMs = duration,
                custom = true
            )
        )
    }

    private fun readDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            raw?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun displayName(resolver: android.content.ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast('.')

    private fun extensionFor(mime: String?): String = when {
        mime == null -> "audio"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        mime.contains("wav") || mime.contains("wave") -> "wav"
        mime.contains("ogg") -> "ogg"
        mime.contains("flac") -> "flac"
        mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
        mime.contains("3gpp") -> "3gp"
        mime.contains("amr") -> "amr"
        else -> "audio"
    }

    private fun persistTiles() {
        prefs.tiles = tiles
    }

    // ------------------------------------------------------------------ сброс

    fun resetEverything() {
        stopPlayback()
        prefs.resetAll()
        theme = prefs.theme
        volumeMode = prefs.volumeMode
        bassMode = prefs.bassMode
        bass = prefs.bass
        autoPauseOnUnplug = prefs.autoPauseOnUnplug
        tiles = prefs.tiles
        engine.setBassFraction(bass)
        bassSyncTick++
        message = "Настройки сброшены к значениям по умолчанию"
    }

    /**
     * Сессия для системной панели эффектов: MusicFX открывается только при живой
     * сессии, иначе окно сразу закрывается.
     */
    fun panelSessionId(): Int =
        if (engine.sessionId > 0) engine.sessionId else engine.ensurePanelSession()

    fun closePanelSession() = engine.releasePanelSession()

    /** Возврат в приложение: тихая сессия для панели больше не нужна. */
    fun onAppResumed() {
        engine.releasePanelSession()
        refreshSystemVolume()
    }

    /** Показать короткое сообщение пользователю. */
    fun notify(text: String) {
        message = text
    }

    fun clearMessage() {
        message = null
    }

    private companion object {
        /** Системные вызовы громкости и эффектов — не чаще 30 раз в секунду. */
        const val THROTTLE_MS = 33L

        /** Задержка перед записью настроек на диск. */
        const val PERSIST_DELAY_MS = 400L
    }

    override fun onCleared() {
        // Сохраняем последние значения, не дожидаясь отложенной записи.
        persistJob?.cancel()
        prefs.volume = volume
        prefs.bass = bass
        engine.release()
        super.onCleared()
    }
}
