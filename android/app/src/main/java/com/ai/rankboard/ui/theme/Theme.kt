package com.ai.rankboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.rankboard.data.ThemeMode

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E9FF),
    onPrimaryContainer = Color(0xFF1B1B62),
    secondary = Color(0xFF0E8781),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F6F3),
    onSecondaryContainer = Color(0xFF06302E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECCB),
    onTertiaryContainer = Color(0xFF4A2000),
    error = Color(0xFFDC2626),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF161A20),
    surface = Color(0xFFF9FAFB),
    onSurface = Color(0xFF161A20),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF4F6F9),
    surfaceContainerHigh = Color(0xFFF0F2F6),
    surfaceContainerHighest = Color(0xFFEBEEF3),
    surfaceVariant = Color(0xFFE6EAF0),
    onSurfaceVariant = Color(0xFF5D6675),
    outlineVariant = Color(0xFFDFE4EC),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF141A45),
    primaryContainer = Color(0xFF37306E),
    onPrimaryContainer = Color(0xFFE7E9FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF04322F),
    secondaryContainer = Color(0xFF0D4C47),
    onSecondaryContainer = Color(0xFFD8F6F3),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3B2500),
    tertiaryContainer = Color(0xFF543405),
    onTertiaryContainer = Color(0xFFFFECCB),
    error = Color(0xFFF87171),
    background = Color(0xFF070A10),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF0C1017),
    onSurface = Color(0xFFE8ECF4),
    surfaceContainerLowest = Color(0xFF0D1118),
    surfaceContainerLow = Color(0xFF111620),
    surfaceContainer = Color(0xFF151B26),
    surfaceContainerHigh = Color(0xFF1A212E),
    surfaceContainerHighest = Color(0xFF202735),
    surfaceVariant = Color(0xFF1C232F),
    onSurfaceVariant = Color(0xFFA7B1C2),
    outlineVariant = Color(0xFF262E3C),
)

private val RankboardTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
)

private val RankboardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RankboardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RankboardTypography,
        shapes = RankboardShapes,
        content = content,
    )
}
