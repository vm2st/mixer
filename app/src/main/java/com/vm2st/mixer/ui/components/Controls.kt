package com.vm2st.mixer.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm2st.mixer.ui.theme.LocalVMixerAccent

fun hexToColor(hex: String): Color =
    runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color(0xFF2E86AB))

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 10.dp)
    )
}

/** Компактная «таблетка»: статус вывода звука, тема, счётчики. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accent = LocalVMixerAccent.current
    val haptics = LocalHapticFeedback.current
    val background = if (selected) accent.accent else accent.track.copy(alpha = 0.75f)
    val content = if (selected) accent.onAccent else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier.pointerInput(text, selected) {
                        detectTapGestures {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Круглая кнопка-квадрат: остановить воспроизведение. */
@Composable
fun StopSquareButton(enabled: Boolean, onClick: () -> Unit, size: Dp = 64.dp) {
    val accent = LocalVMixerAccent.current
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(if (enabled) accent.tileSurface else accent.track.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = if (enabled) accent.accent.copy(alpha = 0.55f) else accent.outlineSoft,
                shape = RoundedCornerShape(size / 3.2f)
            )
            .pointerInput(enabled) {
                detectTapGestures {
                    if (enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size / 3.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
        )
    }
}

/** Круглая кнопка «плюс»: добавить свой эффект. */
@Composable
fun RoundAddButton(onClick: () -> Unit, size: Dp = 64.dp) {
    val accent = LocalVMixerAccent.current
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.accent)
            .pointerInput(Unit) {
                detectTapGestures {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = accent.onAccent,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light
        )
    }
}

/** Точки-индикатор страниц. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val accent = LocalVMixerAccent.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) accent.accent
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

/** Палитра для смены цвета плитки. */
@Composable
fun PalettePicker(
    colors: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    columns: Int = 6
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.chunked(columns).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowColors.forEach { hex ->
                    val color = hexToColor(hex)
                    val isSelected = selected.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .pointerInput(hex) { detectTapGestures { onPick(hex) } }
                    )
                }
            }
        }
    }
}

/** Строка с подписью и значением — для диалога «О приложении». */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(Modifier.height(2.dp))
}
