package com.vm2st.mixer.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeShaper
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.vm2st.mixer.R
import java.io.File

/** Куда сейчас уходит звук. */
enum class OutputRoute(val label: String, val emoji: String) {
    SPEAKER("Динамик телефона", "\uD83D\uDD0A"),
    WIRED("Наушники", "\uD83C\uDFA7"),
    BLUETOOTH("Bluetooth", "\uD83D\uDCF6"),
    USB("USB / ЦАП", "\uD83D\uDD0C"),
    UNKNOWN("Аудиовыход", "\uD83C\uDFB5")
}

/**
 * Ядро микшера: громкость системного потока, бас через системные эффекты
 * и проигрывание коротких спецэффектов. Вывод звука всегда тот, который
 * система выбрала сама: динамик, наушники, Bluetooth или USB.
 * Смена вывода отслеживается в реальном времени.
 */
class AudioEngine(context: Context) {

    interface Listener {
        fun onRouteChanged(route: OutputRoute, deviceName: String?)
        fun onOutputLost()
        fun onPlaybackFinished()
    }

    var listener: Listener? = null

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var panelPlayer: MediaPlayer? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var globalBassBoost: BassBoost? = null
    private var globalEqualizer: Equalizer? = null

    private var bassLevel: Float = 0f
    private var volumeFraction: Float = 1f
    private var volumeFallback = false

    var lastRoute: OutputRoute = OutputRoute.SPEAKER
        private set

    var lastRouteName: String? = null
        private set

    /** Активная аудиосессия — её использует системная панель эквалайзера. */
    val sessionId: Int
        get() = player?.let { runCatching { it.audioSessionId }.getOrDefault(0) } ?: 0

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshRoute()
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                handler.post { listener?.onOutputLost() }
            }
        }
    }

    init {
        runCatching { audioManager.registerAudioDeviceCallback(deviceCallback, handler) }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        attachGlobalEffects()
        refreshRoute()
    }

    // ---------------------------------------------------------------- громкость

    val maxVolumeIndex: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    val volumeIndex: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun currentVolumeFraction(): Float = volumeIndex.toFloat() / maxVolumeIndex.toFloat()

    /**
     * Громкость микшера — это громкость системного потока музыки.
     * Плеер всегда играет на полной громкости: иначе звук глушился бы дважды.
     */
    fun setVolumeFraction(fraction: Float) {
        val value = fraction.coerceIn(0f, 1f)
        volumeFraction = value
        val index = Math.round(value * maxVolumeIndex)
        try {
            if (index != volumeIndex) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
            volumeFallback = false
        } catch (t: Throwable) {
            // Например, запрещено политикой «Не беспокоить» — тогда работает
            // запасной вариант: громкость самого плеера.
            volumeFallback = true
        }
        applyPlayerVolume()
    }

    private fun applyPlayerVolume() {
        val level = if (volumeFallback) volumeFraction else 1f
        runCatching { player?.setVolume(level, level) }
    }

    // --------------------------------------------------------------------- бас

    /** Бас: 0..1 -> системный BassBoost + низкие полосы эквалайзера. */
    fun setBassFraction(fraction: Float) {
        bassLevel = fraction.coerceIn(0f, 1f)
        applyBass()
    }

    private fun applyBass() {
        val strength = (bassLevel * 1000f).toInt().coerceIn(0, 1000)
        val enabled = bassLevel > 0.02f

        bassBoost?.let { effect ->
            runCatching {
                effect.setEnabled(enabled)
                effect.setStrength(strength.toShort())
            }
        }
        globalBassBoost?.let { effect ->
            runCatching {
                effect.setEnabled(enabled)
                effect.setStrength(strength.toShort())
            }
        }

        listOfNotNull(equalizer, globalEqualizer).forEach { eq ->
            runCatching {
                val range = eq.bandLevelRange
                val min = range[0].toInt()
                val max = range[1].toInt()
                for (band in 0 until eq.numberOfBands) {
                    val center = eq.getCenterFreq(band.toShort()) / 1000
                    val gain = when {
                        center <= 120 -> max * bassLevel * 0.9f
                        center <= 300 -> max * bassLevel * 0.55f
                        else -> 0f
                    }
                    eq.setBandLevel(band.toShort(), gain.toInt().coerceIn(min, max).toShort())
                }
                eq.enabled = true
            }
        }

        loudness?.let { effect ->
            runCatching {
                effect.enabled = enabled
                effect.setTargetGain((bassLevel * 600f).toInt())
            }
        }
    }

    private fun attachPlayerEffects(session: Int) {
        releasePlayerEffects()
        if (session == 0) return
        bassBoost = runCatching { BassBoost(0, session) }.getOrNull()
        equalizer = runCatching { Equalizer(0, session) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(session) }.getOrNull()
        applyBass()
    }

    /** Эффекты на общий выход — чтобы бас слышно было и в других приложениях. */
    private fun attachGlobalEffects() {
        releaseGlobalEffects()
        globalBassBoost = runCatching { BassBoost(0, 0) }.getOrNull()
        globalEqualizer = runCatching { Equalizer(0, 0) }.getOrNull()
        applyBass()
    }

    private fun releasePlayerEffects() {
        runCatching { bassBoost?.release() }
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        bassBoost = null
        equalizer = null
        loudness = null
    }

    private fun releaseGlobalEffects() {
        runCatching { globalBassBoost?.release() }
        runCatching { globalEqualizer?.release() }
        globalBassBoost = null
        globalEqualizer = null
    }

    // ------------------------------------------------------------ проигрывание

    fun playRawResource(rawName: String) {
        val resId = rawResourceId(rawName)
        if (resId == 0) {
            notifyFinished()
            return
        }
        val file = runCatching { cacheRaw(rawName, resId) }.getOrNull()
        if (file == null) {
            notifyFinished()
            return
        }
        playFile(file)
    }

    fun playFile(file: File) {
        if (!file.exists()) {
            notifyFinished()
            return
        }

        val current = ensurePlayer() ?: run {
            notifyFinished()
            return
        }

        runCatching { if (current.isPlaying) current.stop() }
        val prepared = runCatching {
            current.reset()
            current.setAudioAttributes(attributes)
            current.setDataSource(file.absolutePath)
            current.prepare()
        }.isSuccess

        if (!prepared) {
            runCatching { current.reset() }
            notifyFinished()
            return
        }

        applyPlayerVolume()
        val started = runCatching { current.start() }.isSuccess
        if (!started) {
            runCatching { current.reset() }
            notifyFinished()
            return
        }
        fadeIn(current)
    }

    /** Проигрыватель создаётся один раз: сессия и эффекты живут вместе с ним. */
    private fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }

        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setOnCompletionListener { handler.post { listener?.onPlaybackFinished() } }
                setOnErrorListener { _, _, _ ->
                    handler.post { listener?.onPlaybackFinished() }
                    true
                }
            }
        }.getOrNull() ?: return null

        player = created
        val session = runCatching { created.audioSessionId }.getOrDefault(0)
        attachPlayerEffects(session)
        notifySessionOpen(session)
        return created
    }

    /** Остановка: проигрыватель остаётся готовым к следующему эффекту. */
    fun stop() {
        val current = player ?: return
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.reset() }
    }

    val isPlaying: Boolean
        get() = player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false

    private fun notifyFinished() = handler.post { listener?.onPlaybackFinished() }

    /**
     * Протокол системных эффектов: пока идёт звук, приложение сообщает системе об
     * открытой аудиосессии, и панели эффектов (MusicFX и другие) подключаются к ней.
     */
    private fun notifySessionOpen(session: Int) {
        sendSessionBroadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, session)
    }

    private fun notifySessionClose(session: Int) {
        sendSessionBroadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, session)
    }

    private fun sendSessionBroadcast(action: String, session: Int) {
        if (session <= 0) return
        runCatching {
            val intent = Intent(action).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, appContext.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            appContext.sendBroadcast(intent)
        }
    }

    /** Короткий вход без щелчка. */
    private fun fadeIn(target: MediaPlayer) {
        runCatching {
            val config = VolumeShaper.Configuration.Builder()
                .setDuration(70)
                .setCurve(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f))
                .build()
            target.createVolumeShaper(config)?.apply(VolumeShaper.Operation.PLAY)
        }
    }

    /** Явные идентификаторы: без отражения ресурсов и без сюрпризов при сжатии ресурсов. */
    private fun rawResourceId(name: String): Int = when (name) {
        "fx_kick" -> R.raw.fx_kick
        "fx_clap" -> R.raw.fx_clap
        "fx_riser" -> R.raw.fx_riser
        "fx_zap" -> R.raw.fx_zap
        SILENCE_RAW -> R.raw.silence
        else -> 0
    }

    private fun cacheRaw(rawName: String, resId: Int): File {
        val dir = File(appContext.cacheDir, "builtin").apply { mkdirs() }
        val out = File(dir, "$rawName.wav")
        if (!out.exists() || out.length() == 0L) {
            appContext.resources.openRawResource(resId).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return out
    }

    // ------------------------------------- сессия для панели эффектов системы

    /**
     * Панель эффектов (MusicFX) открывается только при живой аудиосессии —
     * без неё она сразу закрывается. На время открытия панели держим тихий
     * проигрыватель, который даёт настоящую сессию.
     */
    fun ensurePanelSession(): Int {
        val current = panelPlayer?.let { runCatching { it.audioSessionId }.getOrDefault(0) } ?: 0
        if (current > 0) return current

        val resId = rawResourceId(SILENCE_RAW)
        if (resId == 0) return sessionId

        val created = runCatching {
            val file = cacheRaw("silence", resId)
            val p = MediaPlayer()
            p.setAudioAttributes(attributes)
            p.setDataSource(file.absolutePath)
            p.isLooping = true
            p.prepare()
            p
        }.getOrNull() ?: return sessionId

        panelPlayer = created
        runCatching { created.start() }
        notifySessionOpen(runCatching { created.audioSessionId }.getOrDefault(0))
        handler.removeCallbacks(panelTimeout)
        handler.postDelayed(panelTimeout, PANEL_SESSION_TIMEOUT_MS)
        return runCatching { created.audioSessionId }.getOrDefault(sessionId)
    }

    fun releasePanelSession() {
        val current = panelPlayer ?: return
        panelPlayer = null
        handler.removeCallbacks(panelTimeout)
        val session = runCatching { current.audioSessionId }.getOrDefault(0)
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.release() }
        notifySessionClose(session)
    }

    private val panelTimeout = Runnable { releasePanelSession() }

    // ------------------------------------------------------------------ маршрут

    /** Проверить, куда система отправляет звук прямо сейчас. */
    fun refreshRoute() {
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())

        val active = pickActiveDevice(devices)
        val route = routeOf(active)
        val name = active?.productName?.toString()?.takeIf { it.isNotBlank() }

        if (route != lastRoute || name != lastRouteName) {
            lastRoute = route
            lastRouteName = name
            // на некоторых устройствах эффекты слетают при смене вывода
            attachGlobalEffects()
            handler.post { listener?.onRouteChanged(route, name) }
        }
    }

    /**
     * Константы TYPE_BLE_* и TYPE_HEARING_AID появились в новых версиях Android,
     * но это обычные числа: они встраиваются при компиляции и здесь только сравниваются,
     * поэтому на старых устройствах ничего не ломается.
     */
    @Suppress("InlinedApi")
    private fun pickActiveDevice(devices: Array<AudioDeviceInfo>): AudioDeviceInfo? {
        fun firstOf(vararg types: Int): AudioDeviceInfo? =
            devices.firstOrNull { device -> types.any { it == device.type } }

        return firstOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        ) ?: devices.firstOrNull()
    }

    @Suppress("InlinedApi")
    private fun routeOf(device: AudioDeviceInfo?): OutputRoute = when (device?.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputRoute.BLUETOOTH

        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputRoute.USB

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputRoute.WIRED

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputRoute.SPEAKER

        else -> OutputRoute.UNKNOWN
    }

    fun release() {
        val current = player
        player = null
        val session = current?.let { runCatching { it.audioSessionId }.getOrDefault(0) } ?: 0
        current?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        releasePlayerEffects()
        notifySessionClose(session)

        releasePanelSession()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        releaseGlobalEffects()
    }

    private companion object {
        /** Страховка: тихая сессия не должна жить дольше двух минут. */
        const val PANEL_SESSION_TIMEOUT_MS = 120_000L

        const val SILENCE_RAW = "silence"
    }
}
