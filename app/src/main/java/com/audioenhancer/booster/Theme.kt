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
// BATCH REDESIGN v1.46 — "matte premium": neon-violet glassmorphism (Batch 2,
// v1.29+) DICABUT TOTAL. Ganti ke palet graphite/charcoal matte + aksen logam
// champagne-bronze desaturasi (bukan lagi ungu terang/neon) — kesan alat audio
// premium fisik (brushed metal, matte black), bukan "gamer RGB". Aksen
// per-fitur (Bass/Virtualizer/Loudness/Equalizer) TIDAK diubah — sudah cukup
// muted sejak awal, bukan sumber kesan "neon alay" yang dikeluhkan user.
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon/border/teks.
val BassAccent = Color(0xFFE0865B); val BassAccent2 = Color(0xFFF0B48F)
val VirtualizerAccent = Color(0xFF4FB8C9); val VirtualizerAccent2 = Color(0xFF8DD3DE)
val LoudnessAccent = Color(0xFF4CB88A); val LoudnessAccent2 = Color(0xFF94D4B4)
val EqualizerAccent = Color(0xFFD97AA6); val EqualizerAccent2 = Color(0xFFE8A8C6)
val BatteryAccent = Color(0xFFD9A54A); val BatteryAccent2 = Color(0xFFE8C687)

// Aksen "logam" netral matte — dipakai buat swatch toggle Material You & elemen
// netral lain yang dulu pinjam warna primary violet. Bukan lagi ungu.
val DynamicColorAccent = Color(0xFF9C9890); val DynamicColorAccent2 = Color(0xFFC9C4BC)

private val PremiumBronzeDark = Color(0xFFC2A26B)
private val PremiumBronzeLight = Color(0xFF8A6D3B)

/** Brush gradasi latar layar dark theme — graphite/charcoal matte, TIDAK ada
 * tint violet lagi (dulu 0xFF1B1330 dkk). Netral hangat, dalam tapi bukan
 * hitam pekat rata (biar tetap "berkedalaman" ala Batch 2, cuma ganti hue). */
val DarkBackgroundBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF1C1A17), Color(0xFF121110), Color(0xFF0A0A0A))
)

private val DarkColors = darkColorScheme(
    primary = PremiumBronzeDark,
    onPrimary = Color(0xFF241C0E),
    primaryContainer = Color(0xFF3D311B),
    onPrimaryContainer = Color(0xFFEFDEB8),
    secondary = Color(0xFF9CA3AC),
    onSecondary = Color(0xFF1A1A1C),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF1EFEA),
    surface = Color(0xFF191816),
    onSurface = Color(0xFFF1EFEA),
    surfaceVariant = Color(0xFF292724),
    onSurfaceVariant = Color(0xFFB0ACA4),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = Color(0xFF3E3B35)
)

private val LightColors = lightColorScheme(
    primary = PremiumBronzeLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEE0C4),
    onPrimaryContainer = Color(0xFF3D311B),
    secondary = Color(0xFF5B6068),
    onSecondary = Color.White,
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF5C594F),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFD9D4C7)
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
