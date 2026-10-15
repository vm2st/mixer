package com.vm2st.mixer.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm2st.mixer.audio.SystemAudio
import com.vm2st.mixer.data.APP_VERSION
import com.vm2st.mixer.data.SliderId
import com.vm2st.mixer.data.TELEGRAM_HANDLE
import com.vm2st.mixer.data.TELEGRAM_URL
import com.vm2st.mixer.data.ThemeMode
import com.vm2st.mixer.ui.VmixerViewModel
import com.vm2st.mixer.ui.components.InfoRow
import com.vm2st.mixer.ui.components.PageDots
import com.vm2st.mixer.ui.components.Pill
import com.vm2st.mixer.ui.components.SectionTitle
import com.vm2st.mixer.ui.components.VerticalSlider
import com.vm2st.mixer.ui.theme.LocalVMixerAccent
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MixerScreen(viewModel: VmixerViewModel, onOpenEffects: () -> Unit) {
    val accent = LocalVMixerAccent.current
    val context = LocalContext.current

    var showThemeMenu by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }

    // Панель эффектов возвращает результат при закрытии — тогда отпускаем тихую сессию.
    val panelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.closePanelSession() }
    val launchPanel: (Intent) -> Boolean = remember(panelLauncher) {
        { intent -> runCatching { panelLauncher.launch(intent) }.isSuccess }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ------------------------------------------------------------- шапка
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VMixer",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(10.dp))
            Box {
                Pill(text = viewModel.theme.label, onClick = { showThemeMenu = true })
                DropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { showThemeMenu = false }
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                viewModel.applyTheme(mode)
                                showThemeMenu = false
                            },
                            trailingIcon = {
                                if (viewModel.theme == mode) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            PageDots(count = 2, current = 0)
            Spacer(Modifier.width(12.dp))
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Меню",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    MenuAction("Встроенный эквалайзер") {
                        showOverflow = false
                        openSystemEqualizer(context, viewModel, launchPanel)
                    }
                    MenuAction("Звук и вибрация") {
                        showOverflow = false
                        SystemAudio.openSoundSettings(context)
                    }
                    MenuAction("Bluetooth-выход") {
                        showOverflow = false
                        SystemAudio.openBluetoothSettings(context)
                    }
                    MenuAction("Проверить вывод звука") {
                        showOverflow = false
                        viewModel.refreshRoute()
                    }
                    MenuAction(
                        if (viewModel.hiddenCount > 0) {
                            "Вернуть скрытые плитки (${viewModel.hiddenCount})"
                        } else {
                            "Скрытые плитки"
                        }
                    ) {
                        showOverflow = false
                        viewModel.restoreAllHidden()
                    }
                    MenuAction(
                        if (viewModel.autoPauseOnUnplug) {
                            "Авто-пауза при отключении наушников: вкл"
                        } else {
                            "Авто-пауза при отключении наушников: выкл"
                        }
                    ) {
                        viewModel.updateAutoPause(!viewModel.autoPauseOnUnplug)
                    }
                    MenuAction("Telegram · $TELEGRAM_HANDLE") {
                        showOverflow = false
                        SystemAudio.openLink(context, TELEGRAM_URL)
                    }
                    MenuAction("О приложении") {
                        showOverflow = false
                        showAbout = true
                    }
                    MenuAction("Сбросить настройки") {
                        showOverflow = false
                        showReset = true
                    }
                }
            }
        }

        // ------------------------------------------------------ вывод звука
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(accent.tileSurface)
                .border(1.dp, accent.outlineSoft, RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectTapGestures { showOutput = true } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = viewModel.route.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Вывод звука",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = viewModel.routeName?.let { "${viewModel.route.label} · $it" }
                        ?: viewModel.route.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Pill(text = "авто")
        }

        // ---------------------------------------------------------- слайдеры
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VerticalSlider(
                value = viewModel.volume,
                onValueChange = viewModel::changeVolume,
                accent = accent.accent,
                onAccent = accent.onAccent,
                trackColor = accent.track,
                title = "Громкость",
                valueText = "${(viewModel.volume * 100f).roundToInt()}%",
                syncTick = viewModel.volumeSyncTick,
                mode = viewModel.volumeMode,
                onModeChange = { viewModel.setSliderMode(SliderId.VOLUME, it) },
                onReset = viewModel::resetVolume,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            VerticalSlider(
                value = viewModel.bass,
                onValueChange = viewModel::changeBass,
                accent = accent.accent,
                onAccent = accent.onAccent,
                trackColor = accent.track,
                title = "Басы",
                valueText = "${(viewModel.bass * 100f).roundToInt()}%",
                syncTick = viewModel.bassSyncTick,
                mode = viewModel.bassMode,
                onModeChange = { viewModel.setSliderMode(SliderId.BASS, it) },
                onReset = viewModel::resetBass,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // ----------------------------------------------------- пресеты
        Spacer(Modifier.height(22.dp))
        SectionTitle("Пресеты")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { preset ->
                Pill(
                    text = "${(preset * 100f).roundToInt()}%",
                    selected = abs(viewModel.volume - preset) < 0.03f,
                    onClick = { viewModel.setVolumeFromOutside(preset) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("выкл" to 0f, "мягко" to 0.3f, "средне" to 0.6f, "макс" to 1f)
                .forEach { (label, value) ->
                    Pill(
                        text = label,
                        selected = abs(viewModel.bass - value) < 0.03f,
                        onClick = { viewModel.setBassFromOutside(value) },
                        modifier = Modifier.weight(1f)
                    )
                }
        }

        // ------------------------------------------- системный эквалайзер
        Spacer(Modifier.height(24.dp))
        SectionTitle("Эквалайзер системы")
        OutlinedButton(
            onClick = { openSystemEqualizer(context, viewModel, launchPanel) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accent.outlineSoft),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Text("Открыть эквалайзер", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { SystemAudio.openSoundSettings(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accent.outlineSoft),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text("Звук")
            }
            OutlinedButton(
                onClick = { SystemAudio.openBluetoothSettings(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, accent.outlineSoft),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text("Bluetooth")
            }
        }

        // -------------------------------------------------- переход на плитки
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onOpenEffects,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.accent,
                contentColor = accent.onAccent
            )
        ) {
            Text("Спецэффекты", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        Spacer(Modifier.height(30.dp))
    }

    // -------------------------------------------------------------- диалоги
    if (showOutput) {
        // Поиск панели эффектов — обращение к системе, делаем его один раз.
        val panelPackage = remember { SystemAudio.panelPackage(context) }
        AlertDialog(
            onDismissRequest = { showOutput = false },
            title = { Text("Вывод звука") },
            text = {
                Column {
                    val volumePercent = if (viewModel.maxVolumeIndex > 0) {
                        (viewModel.systemVolumeIndex * 100f / viewModel.maxVolumeIndex).roundToInt()
                    } else {
                        0
                    }
                    InfoRow("Сейчас", viewModel.route.label)
                    InfoRow("Устройство", viewModel.routeName ?: "—")
                    InfoRow("Громкость", "${volumePercent} из 100")
                    InfoRow("Басы", "${(viewModel.bass * 100f).roundToInt()}%")
                    InfoRow("Панель эффектов", panelPackage ?: "не найдена")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Вывод подхватывается автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.refreshRoute()
                    showOutput = false
                }) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = { showOutput = false }) { Text("Закрыть") }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("VMixer") },
            text = {
                Column {
                    InfoRow("Версия", APP_VERSION)
                    InfoRow("Пакет", "com.vm2st.mixer")
                    InfoRow("Автор", "vm2 studios")
                    InfoRow("Telegram", TELEGRAM_HANDLE)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    SystemAudio.openLink(context, TELEGRAM_URL)
                    showAbout = false
                }) { Text("Telegram") }
            },
            dismissButton = {
                TextButton(onClick = { showAbout = false }) { Text("Закрыть") }
            }
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Сбросить настройки?") },
            text = {
                Text(
                    text = "Тема, режимы слайдеров, басы и плитки вернутся к значениям по умолчанию.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetEverything()
                    showReset = false
                }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Отмена") }
            }
        )
    }
}

/**
 * Панель эффектов системы.
 *
 * MusicFX узнаёт вызывающее приложение через `getCallingPackage()` и закрывается
 * сразу, если он пуст. Поэтому панель запускается через `startActivityForResult`
 * (ActivityResultLauncher) и без FLAG_ACTIVITY_NEW_TASK — иначе имя пакета
 * вызывающего приложения не передаётся.
 */
private fun openSystemEqualizer(
    context: android.content.Context,
    viewModel: VmixerViewModel,
    launchPanel: (Intent) -> Boolean
) {
    val session = viewModel.panelSessionId()
    val opened = SystemAudio.panelIntents(context, session).any { launchPanel(it) }
    if (!opened) {
        viewModel.closePanelSession()
        SystemAudio.openSoundSettings(context)
        viewModel.notify("Панель эквалайзера не найдена — открыты настройки звука")
    }
}

@Composable
private fun MenuAction(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        onClick = onClick
    )
}
