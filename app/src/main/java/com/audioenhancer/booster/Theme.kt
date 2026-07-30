package com.audioenhancer.booster

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palet terinspirasi iOS system colors — biru khas Apple, permukaan gelap berlapis
// (bukan hitam pekat rata), abu-abu netral yang tenang untuk teks sekunder.
private val AppleBlueDark = Color(0xFF0A84FF)
private val AppleBlueLight = Color(0xFF007AFF)
private val AppleCyan = Color(0xFF64D2FF)
private val AppleRedDark = Color(0xFFFF453A)
private val AppleRedLight = Color(0xFFFF3B30)
private val AppleGreen = Color(0xFF30D158)

private val DarkColors = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF10345C),
    onPrimaryContainer = Color(0xFFCFE6FF),
    secondary = AppleCyan,
    onSecondary = Color(0xFF00303F),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    error = AppleRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF3A0E0C),
    onErrorContainer = Color(0xFFFFD5D1),
    outline = Color(0xFF676767)
)

private val LightColors = lightColorScheme(
    primary = AppleBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBFF),
    onPrimaryContainer = Color(0xFF00315C),
    secondary = Color(0xFF32ADE6),
    onSecondary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6E6E73),
    error = AppleRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E1),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF949494)
)

// Tracking huruf agak rapat di judul-judul besar — mendekati kesan SF Pro Display.
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )
)

// Shape membulat generous — kesan kartu premium ala iOS, bukan kotak tajam Material default.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AudioEnhancerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Dynamic color (Material You) cuma dipakai kalau user MENGAKTIFKAN sendiri dan HP
    // support (Android 12+/API 31+). Default-nya OFF, karena palet iOS-style di atas ini
    // sengaja dirancang sebagai identitas visual app — dynamic color akan menimpanya total
    // dengan warna hasil ekstrak wallpaper, jadi ini pilihan user, bukan default paksa.
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
