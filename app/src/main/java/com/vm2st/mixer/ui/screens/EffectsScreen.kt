package com.vm2st.mixer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vm2st.mixer.data.EffectTile
import com.vm2st.mixer.data.TILE_PALETTE
import com.vm2st.mixer.ui.VmixerViewModel
import com.vm2st.mixer.ui.components.PageDots
import com.vm2st.mixer.ui.components.PalettePicker
import com.vm2st.mixer.ui.components.Pill
import com.vm2st.mixer.ui.components.RoundAddButton
import com.vm2st.mixer.ui.components.StopSquareButton
import com.vm2st.mixer.ui.components.hexToColor
import com.vm2st.mixer.ui.theme.LocalVMixerAccent
import java.util.Locale

@Composable
fun EffectsScreen(viewModel: VmixerViewModel, onBackToMixer: () -> Unit) {
    val accent = LocalVMixerAccent.current
    var showHiddenDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var editingTileId by remember { mutableStateOf<String?>(null) }
    val editingTile = viewModel.tiles.firstOrNull { it.id == editingTileId }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.addCustomTile(uri) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Спецэффекты",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(10.dp))
                Pill(text = "${viewModel.visibleTiles.size}")
                Spacer(Modifier.weight(1f))
                PageDots(count = 2, current = 1)
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (viewModel.hiddenCount > 0) {
                                        "Вернуть скрытые (${viewModel.hiddenCount})"
                                    } else {
                                        "Скрытые плитки"
                                    }
                                )
                            },
                            onClick = {
                                showOverflow = false
                                if (viewModel.hiddenCount == 0) {
                                    viewModel.restoreAllHidden()
                                } else {
                                    showHiddenDialog = true
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Остановить звук") },
                            onClick = {
                                showOverflow = false
                                viewModel.stopPlayback()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 2.dp,
                    bottom = 170.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = viewModel.visibleTiles, key = { it.id }) { tile ->
                    EffectTileView(
                        tile = tile,
                        playing = viewModel.playingTileId == tile.id,
                        onClick = { viewModel.playTile(tile) },
                        onLongClick = { editingTileId = tile.id }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Pill(text = "Микшер", onClick = onBackToMixer)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End
        ) {
            StopSquareButton(
                enabled = viewModel.playingTileId != null,
                onClick = { viewModel.stopPlayback() }
            )
            Spacer(Modifier.height(14.dp))
            RoundAddButton(
                onClick = {
                    val launched = runCatching { picker.launch(arrayOf("audio/*")) }.isSuccess
                    if (!launched) {
                        viewModel.notify("Не нашлось приложения для выбора аудиофайла")
                    }
                }
            )
        }
    }

    if (editingTile != null) {
        AlertDialog(
            onDismissRequest = { editingTileId = null },
            title = { Text("«${editingTile.name}»") },
            text = {
                Column {
                    Text(
                        text = "Цвет плитки",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    PalettePicker(
                        colors = TILE_PALETTE,
                        selected = editingTile.colorHex,
                        onPick = { hex -> viewModel.setTileColor(editingTile.id, hex) }
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.hideTile(editingTile.id)
                                editingTileId = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accent.outlineSoft),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) { Text("Скрыть") }

                        if (editingTile.custom) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.deleteTile(editingTile.id)
                                    editingTileId = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Удалить") }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.playTile(editingTile) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, accent.outlineSoft),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                )
                            ) { Text("Играть") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingTileId = null }) { Text("Готово") }
            }
        )
    }

    if (showHiddenDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenDialog = false },
            title = { Text("Скрытые плитки") },
            text = {
                if (viewModel.hiddenTiles.isEmpty()) {
                    Text("Скрытых плиток нет")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        viewModel.hiddenTiles.forEach { tile ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(hexToColor(tile.colorHex))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = tile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { viewModel.restoreTile(tile.id) }) {
                                    Text("Вернуть")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreAllHidden()
                    showHiddenDialog = false
                }) { Text("Вернуть все") }
            },
            dismissButton = {
                TextButton(onClick = { showHiddenDialog = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun EffectTileView(
    tile: EffectTile,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = remember(tile.colorHex) { hexToColor(tile.colorHex) }
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = if (playing) 0.38f else 0.16f))
            .border(
                width = 1.dp,
                color = if (playing) color.copy(alpha = 0.95f) else color.copy(alpha = 0.30f),
                shape = RoundedCornerShape(22.dp)
            )
            .pointerInput(tile.id) {
                detectTapGestures(
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
            }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (tile.custom) "свой" else "встроенный",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                Text(
                    text = tile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = durationLabel(tile.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (playing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = pulse))
            )
        }
    }
}

private fun durationLabel(durationMs: Long): String {
    if (durationMs <= 0L) return "—"
    return String.format(Locale.US, "%.1f с", durationMs / 1000.0)
}
