package com.audioenhancer.booster

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// BATCH 31 — "Skeuomorphism-lite (Tactile UI)": neumorphism DICABUT TOTAL, ganti
// total sesuai acuan design guide user (compose-skeuomorphism-lite.md). WAJIB
// dark-mode — tidak ada lagi light theme / theme mode toggle (lihat
// PROJECT_STATE.md poin "Riwayat pivot"). Perubahan kunci vs Batch 12-26:
// 1. TIDAK ADA lagi dual-shadow neumorphic (dark-side/light-side Paint layer).
//    Kedalaman sekarang dari bevel gradient + border highlight/shadow tipis
//    (native vector primitives) sesuai Golden Rule guide — no heavy texture asset.
// 2. Struktur kartu kembali FLAT & minimal (guide poin 3: "Isolated Skeuomorphic
//    Accents") — realisme tactile HANYA dipakai di elemen fisik (power button,
//    slider knob), bukan lagi di semua kartu.
// 3. Dark-mode adaptation (guide poin "Accessibility & Performance Safeguards"):
//    highlight terang diganti "primary glow" tipis, bukan Color.White alpha.
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon.
val BassAccent = Color(0xFFE0865B); val BassAccent2 = Color(0xFFF0B48F)
val VirtualizerAccent = Color(0xFF4FB8C9); val VirtualizerAccent2 = Color(0xFF8DD3DE)
val LoudnessAccent = Color(0xFF4CB88A); val LoudnessAccent2 = Color(0xFF94D4B4)
val EqualizerAccent = Color(0xFFD97AA6); val EqualizerAccent2 = Color(0xFFE8A8C6)
val BatteryAccent = Color(0xFFD9A54A); val BatteryAccent2 = Color(0xFFE8C687)

// Aksen "logam" netral matte — dipakai buat swatch toggle Material You & elemen
// netral lain yang dulu pinjam warna primary violet. Bukan lagi ungu.
val DynamicColorAccent = Color(0xFF9C9890); val DynamicColorAccent2 = Color(0xFFC9C4BC)

private val PremiumBronzeDark = Color(0xFFC2A26B)

// ---- Skeuomorphism-lite tactile tokens (dark-mode only) --------------------
// Simulasi sumber cahaya top-down: gradient permukaan gelap->lebih gelap (bukan
// dual-shadow neumorphic). Dipakai oleh komponen "physical utility" saja
// (SkeuPowerButton/SkeuSliderThumb) — kartu struktural TETAP flat.
val SkeuSurfaceTop = Color(0xFF2E2C29)
val SkeuSurfaceBottom = Color(0xFF201F1D)
val SkeuBevelHighlight = Color(0x33FFFFFF)   // tepi terang tipis (emboss)
val SkeuBevelShadow = Color(0x66000000)      // tepi gelap (recessed edge)
// Dark-mode adaptation guide: pengganti highlight putih -> glow primary tipis,
// dipakai buat ring/state aktif komponen tactile (bukan Color.White alpha lagi).
val SkeuPrimaryGlow = Color(0x40C2A26B)

val SkeuBevelBrush: Brush = Brush.linearGradient(listOf(SkeuSurfaceTop, SkeuSurfaceBottom))
val SkeuBevelBorderBrush: Brush = Brush.linearGradient(listOf(SkeuBevelHighlight, Color.Transparent, SkeuBevelShadow))

// Radius token terpusat (dipakai SkeuomorphicComponents.kt).
val SkeuCardRadius = 20.dp
val SkeuIconBoxRadius = 14.dp

private val DarkColors = darkColorScheme(
    primary = PremiumBronzeDark,
    onPrimary = Color(0xFF241C0E),
    primaryContainer = Color(0xFF3D311B),
    onPrimaryContainer = Color(0xFFEFDEB8),
    secondary = Color(0xFF9CA3AC),
    onSecondary = Color(0xFF1A1A1C),
    background = Color(0xFF232220),
    onBackground = Color(0xFFF6F4EF),
    surface = Color(0xFF232220),
    onSurface = Color(0xFFF6F4EF),
    surfaceVariant = Color(0xFF2A2926),
    onSurfaceVariant = Color(0xFFB8B3A8),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = Color(0xFF3E3B35)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Sebelumnya (Batch 12-26) broadcast status dark/light hasil resolusi manual user
 *  buat pilih warna shadow neumorphic. WAJIB dark-mode sekarang -> CompositionLocal
 *  ini dipertahankan (dipakai SkeuomorphicComponents.kt) tapi NILAINYA SELALU `true`,
 *  tidak ada lagi resolusi/override light. */
val LocalIsDarkTheme = compositionLocalOf { true }

@Composable
fun AudioEnhancerTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        DarkColors
    }
    CompositionLocalProvider(LocalIsDarkTheme provides true) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
