package com.vm2st.mixer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Минималистичная типографика: плотные заголовки, спокойный основной текст. */
val VMixerTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
)
