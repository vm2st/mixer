package com.vm2st.mixer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vm2st.mixer.data.ThemeMode

/** Акцентные цвета текущей темы. */
data class VMixerAccent(
    val accent: Color,
    val onAccent: Color,
    val track: Color,
    val tileSurface: Color,
    val outlineSoft: Color
)

val LocalVMixerAccent = staticCompositionLocalOf {
    VMixerAccent(
        accent = DarkAccent,
        onAccent = DarkOnAccent,
        track = DarkTrack,
        tileSurface = DarkSurface,
        outlineSoft = DarkOutline
    )
}

@Composable
fun VMixerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        ThemeMode.DARK -> darkColorScheme(
            primary = DarkAccent,
            onPrimary = DarkOnAccent,
            primaryContainer = DarkSurfaceVariant,
            onPrimaryContainer = DarkOnBg,
            secondary = DarkAccent,
            onSecondary = DarkOnAccent,
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = DarkOnBg,
            background = DarkBg,
            onBackground = DarkOnBg,
            surface = DarkSurface,
            onSurface = DarkOnBg,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkMuted,
            outline = DarkOutline,
            error = Color(0xFFFF6B6B),
            onError = Color(0xFF2A0A0A),
            scrim = Color(0xCC000000)
        )

        ThemeMode.LIGHT -> lightColorScheme(
            primary = LightAccent,
            onPrimary = LightOnAccent,
            primaryContainer = LightSurfaceVariant,
            onPrimaryContainer = LightOnBg,
            secondary = LightAccent,
            onSecondary = LightOnAccent,
            secondaryContainer = LightSurfaceVariant,
            onSecondaryContainer = LightOnBg,
            background = LightBg,
            onBackground = LightOnBg,
            surface = LightSurface,
            onSurface = LightOnBg,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightMuted,
            outline = LightOutline,
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            scrim = Color(0x99000000)
        )

        ThemeMode.BORDEAUX -> darkColorScheme(
            primary = Bordeaux,
            onPrimary = BordeauxOnAccent,
            primaryContainer = BordeauxSurfaceVariant,
            onPrimaryContainer = BordeauxOnBg,
            secondary = Bordeaux,
            onSecondary = BordeauxOnAccent,
            secondaryContainer = BordeauxSurfaceVariant,
            onSecondaryContainer = BordeauxOnBg,
            background = BordeauxBg,
            onBackground = BordeauxOnBg,
            surface = BordeauxSurface,
            onSurface = BordeauxOnBg,
            surfaceVariant = BordeauxSurfaceVariant,
            onSurfaceVariant = BordeauxMuted,
            outline = BordeauxOutline,
            error = Color(0xFFFF7A6B),
            onError = Color(0xFF2A0A0A),
            scrim = Color(0xCC000000)
        )
    }

    val accent = when (mode) {
        ThemeMode.DARK -> VMixerAccent(
            accent = DarkAccent,
            onAccent = DarkOnAccent,
            track = DarkTrack,
            tileSurface = DarkSurface,
            outlineSoft = DarkOutline
        )

        ThemeMode.LIGHT -> VMixerAccent(
            accent = LightAccent,
            onAccent = LightOnAccent,
            track = LightTrack,
            tileSurface = LightSurface,
            outlineSoft = LightOutline
        )

        ThemeMode.BORDEAUX -> VMixerAccent(
            accent = Bordeaux,
            onAccent = BordeauxOnAccent,
            track = BordeauxTrack,
            tileSurface = BordeauxSurface,
            outlineSoft = BordeauxOutline
        )
    }

    CompositionLocalProvider(LocalVMixerAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
