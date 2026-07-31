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

// ============================================================================
// BATCH 1 REDESIGN — kebalikan dari Apple-style yang minimalis-monokrom-lembut.
// Arahnya "maximalist neo-brutalist": tiap fitur punya warna sendiri yang vivid,
// border TEBAL (bukan hairline tipis), shape lebih geometris/tegas (bukan bubble
// bulat lembut ala iOS), tanpa jadi Material default yang abu-abu membosankan.
// ============================================================================

// Warna aksen per-fitur — dipakai LANGSUNG di komponen (bukan cuma lewat 1 warna
// primary tunggal kayak sebelumnya), biar tiap bagian app kerasa beda & hidup.
val BassAccent = Color(0xFFFF6B35)          // oranye membara
val VirtualizerAccent = Color(0xFF06B6D4)   // cyan elektrik
val LoudnessAccent = Color(0xFF10B981)      // hijau emerald
val EqualizerAccent = Color(0xFFEC4899)     // pink magenta
val BatteryAccent = Color(0xFFF59E0B)       // amber
val DynamicColorAccent = Color(0xFF8B5CF6)  // violet

private val ElectricVioletDark = Color(0xFFA78BFA)
private val ElectricVioletLight = Color(0xFF7C3AED)
private val HotPink = Color(0xFFEC4899)
private val NeonRedDark = Color(0xFFFF5470)
private val NeonRedLight = Color(0xFFE11D48)

private val DarkColors = darkColorScheme(
    primary = ElectricVioletDark,
    onPrimary = Color(0xFF1A1625),
    primaryContainer = Color(0xFF3E2E6B),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = HotPink,
    onSecondary = Color.White,
    background = Color(0xFF0D0B14),
    onBackground = Color(0xFFF2EFFA),
    surface = Color(0xFF1C1826),
    onSurface = Color(0xFFF2EFFA),
    surfaceVariant = Color(0xFF2E2740),
    onSurfaceVariant = Color(0xFFC9BFE0),
    error = NeonRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF4A0F1E),
    onErrorContainer = Color(0xFFFFD8DF),
    outline = Color(0xFF6C5F94)
)

private val LightColors = lightColorScheme(
    primary = ElectricVioletLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE0FF),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = HotPink,
    onSecondary = Color.White,
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1A1625),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1625),
    surfaceVariant = Color(0xFFF0EBFA),
    onSurfaceVariant = Color(0xFF5B5175),
    error = NeonRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E7),
    onErrorContainer = Color(0xFF4A0F1E),
    outline = Color(0xFF1A1625)
)

// Typografi masih tegas/besar, tapi tracking DINAIKKAN (bukan dirapatkan ala SF Pro) —
// kesan poster/ekspresif, bukan refined-elegant.
private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )
)

// Shape lebih GEOMETRIS/TEGAS (sudut lebih kecil) — kebalikan dari bubble membulat
// lembut ala iOS. Dikombinasikan dengan border tebal di komponen untuk kesan
// neo-brutalist yang berani, bukan Material default yang medium-rounded biasa.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
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
