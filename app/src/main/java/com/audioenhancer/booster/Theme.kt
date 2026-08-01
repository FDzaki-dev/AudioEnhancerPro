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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// BATCH 2 REDESIGN — "native ultra premium": glassmorphism, gradient glow per
// fitur, background gradient dalam (bukan hitam pekat rata), border tipis
// gradient (bukan border tebal solid ala Batch 1 / hairline tipis ala Apple).
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon/border/teks.
// Diredupkan dari versi neon awal (Batch readability fix) — hue tetap sama,
// saturasi/brightness diturunkan supaya tidak "nyelekit" di mata pada layar OLED.
val BassAccent = Color(0xFFE0865B); val BassAccent2 = Color(0xFFF0B48F)
val VirtualizerAccent = Color(0xFF4FB8C9); val VirtualizerAccent2 = Color(0xFF8DD3DE)
val LoudnessAccent = Color(0xFF4CB88A); val LoudnessAccent2 = Color(0xFF94D4B4)
val EqualizerAccent = Color(0xFFD97AA6); val EqualizerAccent2 = Color(0xFFE8A8C6)
val BatteryAccent = Color(0xFFD9A54A); val BatteryAccent2 = Color(0xFFE8C687)
val DynamicColorAccent = Color(0xFF8B7CF6); val DynamicColorAccent2 = Color(0xFFC4B5FD)

private val PremiumVioletDark = Color(0xFF8B7CF6)
private val PremiumVioletLight = Color(0xFF6D28D9)
private val PremiumVioletDark2 = Color(0xFFC4B5FD)

/** Brush gradasi latar layar dark theme — dalam & kaya, bukan hitam pekat rata. */
val DarkBackgroundBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF1B1330), Color(0xFF120C1F), Color(0xFF0A0714))
)

private val DarkColors = darkColorScheme(
    primary = PremiumVioletDark,
    onPrimary = Color(0xFF15101F),
    primaryContainer = Color(0xFF3E2E6B),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFF472B6),
    onSecondary = Color.White,
    background = Color(0xFF0A0714),
    onBackground = Color(0xFFF5F2FF),
    surface = Color(0xFF1C1730),
    onSurface = Color(0xFFF5F2FF),
    surfaceVariant = Color(0xFF2E2740),
    onSurfaceVariant = Color(0xFFAFA6CC),
    error = Color(0xFFFF6B81),
    onError = Color.White,
    errorContainer = Color(0xFF4A0F1E),
    onErrorContainer = Color(0xFFFFD8DF),
    outline = Color(0xFF4A4166)
)

private val LightColors = lightColorScheme(
    primary = PremiumVioletLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE0FF),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFFDB2777),
    onSecondary = Color.White,
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1A1625),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1625),
    surfaceVariant = Color(0xFFF0EBFA),
    onSurfaceVariant = Color(0xFF5B5175),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E7),
    onErrorContainer = Color(0xFF4A0F1E),
    outline = Color(0xFFCFC6E8)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
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

// Shape kembali membulat lembut (kesan "kaca premium"), bukan sudut tajam
// brutalist Batch 1 ataupun super-bulat minimal Apple.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AudioEnhancerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
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
