package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class PortalThemePreset(
    val key: String,
    val label: String,
    val primary: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val softContainer: Color,
    val onSoftContainer: Color,
    val darkPrimary: Color,
    val onDarkPrimary: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
) {
    GREEN(
        key = "green",
        label = "Xanh lá (Mặc định)",
        primary = Color(0xFF1B5E3A),
        headerStart = Color(0xFF1B5E3A),
        headerEnd = Color(0xFF257A4B),
        softContainer = Color(0xFFDDF3E4),
        onSoftContainer = Color(0xFF124228),
        darkPrimary = Color(0xFF6EE7B7),
        onDarkPrimary = Color(0xFF0A2E1F),
        darkContainer = Color(0xFF1E4D36),
        onDarkContainer = Color(0xFFDDF3E4),
    ),
    RED(
        key = "red",
        label = "Đỏ truyền thống",
        primary = Color(0xFFA32424),
        headerStart = Color(0xFF991B1B),
        headerEnd = Color(0xFFBE3131),
        softContainer = Color(0xFFFEE2E2),
        onSoftContainer = Color(0xFF7F1D1D),
        darkPrimary = Color(0xFFFCA5A5),
        onDarkPrimary = Color(0xFF3F0D0D),
        darkContainer = Color(0xFF632121),
        onDarkContainer = Color(0xFFFEE2E2),
    ),
    BLUE(
        key = "blue",
        label = "Xanh dương",
        primary = Color(0xFF0C6394),
        headerStart = Color(0xFF075985),
        headerEnd = Color(0xFF0E74AC),
        softContainer = Color(0xFFE0F2FE),
        onSoftContainer = Color(0xFF0C4A6E),
        darkPrimary = Color(0xFF7DD3FC),
        onDarkPrimary = Color(0xFF082F49),
        darkContainer = Color(0xFF154766),
        onDarkContainer = Color(0xFFE0F2FE),
    ),
    TEAL(
        key = "teal",
        label = "Xanh ngọc",
        primary = Color(0xFF146B65),
        headerStart = Color(0xFF115E59),
        headerEnd = Color(0xFF178079),
        softContainer = Color(0xFFCCFBF1),
        onSoftContainer = Color(0xFF134E4A),
        darkPrimary = Color(0xFF5EEAD4),
        onDarkPrimary = Color(0xFF0B332F),
        darkContainer = Color(0xFF194B47),
        onDarkContainer = Color(0xFFCCFBF1),
    ),
    BROWN(
        key = "brown",
        label = "Nâu đất",
        primary = Color(0xFF854016),
        headerStart = Color(0xFF78350F),
        headerEnd = Color(0xFF9A4A1C),
        softContainer = Color(0xFFFEF3C7),
        onSoftContainer = Color(0xFF662C0B),
        darkPrimary = Color(0xFFFCD34D),
        onDarkPrimary = Color(0xFF3B1D06),
        darkContainer = Color(0xFF5C3417),
        onDarkContainer = Color(0xFFFEF3C7),
    );

    companion object {
        fun fromKey(key: String): PortalThemePreset =
            entries.firstOrNull { it.key == key } ?: GREEN
    }
}

// Balanced Light surfaces & ink (comfortable contrast, never glaring)
val SurfaceLight = Color(0xFFF1F5F2)
val CardLight = Color(0xFFFCFDFC)
val SurfaceVariantLight = Color(0xFFE6ECE8)
val TextPrimaryLight = Color(0xFF1E293B)
val TextMutedLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)

// Balanced Dark surfaces & ink (comfortable slate, never pitch black)
val SurfaceDark = Color(0xFF151D19)
val CardDark = Color(0xFF1F2A24)
val SurfaceVariantDark = Color(0xFF2A3831)
val TextPrimaryDark = Color(0xFFE8EFEA)
val TextMutedDark = Color(0xFFA7B7AE)
val OutlineDark = Color(0xFF3B4D43)

data class PortalSemanticColors(
    val themePreset: PortalThemePreset,
    val amberBg: Color,
    val amberText: Color,
    val successBg: Color,
    val successText: Color,
    val infoBg: Color,
    val infoText: Color,
    val dangerBg: Color,
    val dangerText: Color,
    val neutralPillBg: Color,
    val neutralPillText: Color,
)

fun lightSemanticColors(preset: PortalThemePreset) = PortalSemanticColors(
    themePreset = preset,
    amberBg = Color(0xFFFEF3C7),
    amberText = Color(0xFF92400E),
    successBg = Color(0xFFDCFCE7),
    successText = Color(0xFF166534),
    infoBg = Color(0xFFDBEAFE),
    infoText = Color(0xFF1E40AF),
    dangerBg = Color(0xFFFEE2E2),
    dangerText = Color(0xFF991B1B),
    neutralPillBg = Color(0xFFE2E8F0),
    neutralPillText = Color(0xFF334155),
)

fun darkSemanticColors(preset: PortalThemePreset) = PortalSemanticColors(
    themePreset = preset,
    amberBg = Color(0xFF4A3310),
    amberText = Color(0xFFFDE68A),
    successBg = Color(0xFF1A4331),
    successText = Color(0xFFA7F3D0),
    infoBg = Color(0xFF1C385B),
    infoText = Color(0xFFBFDBFE),
    dangerBg = Color(0xFF4E1D1D),
    dangerText = Color(0xFFFECACA),
    neutralPillBg = Color(0xFF2D3C34),
    neutralPillText = Color(0xFFD1DDD6),
)
