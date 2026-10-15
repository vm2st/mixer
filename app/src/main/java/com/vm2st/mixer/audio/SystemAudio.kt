package com.vm2st.mixer.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.Settings

/** Открытие встроенных в систему звуковых настроек и эквалайзера. */
object SystemAudio {

    private const val PANEL_ACTION = AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL
    private const val MUSIC_FX_PACKAGE = "com.android.musicfx"

    /**
     * Известные панели эффектов. MusicFX разных сборок: ControlPanel, ActivityMusic
     * (на AOSP-сборках и Nothing панель — именно ActivityMusic), MainActivity, EqActivity.
     */
    private val KNOWN_PANELS = listOf(
        MUSIC_FX_PACKAGE to "com.android.musicfx.ControlPanel",
        MUSIC_FX_PACKAGE to "com.android.musicfx.ActivityMusic",
        MUSIC_FX_PACKAGE to "com.android.musicfx.MainActivity",
        MUSIC_FX_PACKAGE to "com.android.musicfx.EqActivity",
        "com.sec.android.app.soundalive" to "com.sec.android.app.soundalive.SoundAliveActivity",
        "com.dolby.daxappUI" to "com.dolby.daxappUI.MainActivity"
    )

    /**
     * Варианты интента панели эффектов, от самого точного к самому общему.
     *
     * Важно: панель MusicFX узнаёт вызывающее приложение через `getCallingPackage()`,
     * а он возвращает имя пакета только при запуске через `startActivityForResult`.
     * Поэтому флаги здесь не добавляются — интент нужно запускать именно так.
     */
    fun panelIntents(context: Context, sessionId: Int): List<Intent> {
        val packageName = context.packageName
        val intents = mutableListOf<Intent>()

        // 1. Панель, которую система отдаёт по действию (на Nothing — Compatibility$Redirector).
        resolvePanel(context, baseIntent(sessionId, packageName))?.let { component ->
            intents += baseIntent(sessionId, packageName).setComponent(component)
        }

        // 2. Панель внутри пакета MusicFX — имя активности в разных сборках отличается.
        resolvePanel(context, baseIntent(sessionId, packageName).setPackage(MUSIC_FX_PACKAGE))?.let { component ->
            intents += baseIntent(sessionId, packageName).setComponent(component)
        }

        // 3. Известные компоненты панелей.
        KNOWN_PANELS.forEach { (pkg, cls) ->
            intents += baseIntent(sessionId, packageName).setComponent(ComponentName(pkg, cls))
        }

        // 4. Активность запуска MusicFX, если отдельной панели нет.
        launchIntentForPackage(context, MUSIC_FX_PACKAGE)?.let { intents += it }

        // 5. Любой обработчик системного действия.
        intents += baseIntent(sessionId, packageName)

        return intents
    }

    private fun baseIntent(sessionId: Int, packageName: String): Intent =
        Intent(PANEL_ACTION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }

    /** Имя пакета найденной панели эффектов — для диагностики. */
    fun panelPackage(context: Context): String? {
        val resolved = runCatching {
            context.packageManager.queryIntentActivities(Intent(PANEL_ACTION), 0)
        }.getOrNull()?.firstOrNull { it.activityInfo != null }?.activityInfo?.packageName

        if (resolved != null) return resolved

        return KNOWN_PANELS
            .map { it.first }
            .distinct()
            .firstOrNull { isInstalled(context, it) }
    }

    fun openSoundSettings(context: Context): Boolean {
        if (launch(context, Intent(Settings.ACTION_SOUND_SETTINGS))) return true
        return launch(
            context,
            Intent().setComponent(
                ComponentName("com.android.settings", "com.android.settings.Settings\$SoundSettingsActivity")
            )
        )
    }

    fun openBluetoothSettings(context: Context): Boolean =
        launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openLink(context: Context, url: String): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun launchIntentForPackage(context: Context, packageName: String): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()

    private fun resolvePanel(context: Context, intent: Intent): ComponentName? {
        val activities = runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
        }.getOrNull() ?: return null

        val info = activities
            .firstOrNull { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            ?.activityInfo ?: return null

        return ComponentName(info.packageName, info.name)
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
