package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val DisplayFontFamily = FontFamily(
    Font(R.font.be_vietnam_pro, FontWeight.Normal),
    Font(R.font.be_vietnam_pro, FontWeight.Medium),
    Font(R.font.be_vietnam_pro, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro, FontWeight.Bold),
)

val BodyFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

fun buildPortalTypography(baseFontSize: Int): Typography {
    val scale = (baseFontSize.coerceIn(13, 20)) / 15f
    fun scaled(spVal: Float) = (spVal * scale).sp

    return Typography(
        headlineMedium = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scaled(22f),
            lineHeight = scaled(28f),
        ),
        headlineSmall = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scaled(19f),
            lineHeight = scaled(25f),
        ),
        titleLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = scaled(18f),
            lineHeight = scaled(24f),
        ),
        titleMedium = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaled(16f),
            lineHeight = scaled(22f),
        ),
        titleSmall = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaled(14f),
            lineHeight = scaled(20f),
        ),
        bodyLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scaled(15f),
            lineHeight = scaled(22f),
        ),
        bodyMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scaled(14f),
            lineHeight = scaled(20f),
        ),
        bodySmall = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = scaled(12.5f),
            lineHeight = scaled(18f),
        ),
        labelLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaled(13.5f),
            lineHeight = scaled(18f),
        ),
        labelMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = scaled(12f),
            lineHeight = scaled(16f),
        ),
        labelSmall = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaled(11f),
            lineHeight = scaled(14f),
        ),
    )
}
