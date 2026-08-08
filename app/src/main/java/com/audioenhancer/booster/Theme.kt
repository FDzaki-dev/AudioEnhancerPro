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
// BATCH 34 — KOREKSI dari Batch 33: user salah upload file acuan Batch 33
// (compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md, terlalu
// tactile-first). File YANG BENAR: compose-amoled-hybrid-glass-final.md —
// "Premium AMOLED Hybrid Glassmorphism + Subtle Midnight Blue + Micro-Skeuomorphism".
// Beda kunci vs Batch 33 (lihat PROJECT_STATE.md Batch 34 buat detail lengkap):
// 1. Glass adalah MATERIAL UTAMA (bukan tactile/bevel) — kartu struktural WAJIB
//    "glass surfaces first, not physical objects" (guide §14), TIDAK BOLEH strong
//    bevel/heavy shadow/thick border/bright glow/exaggerated extrusion.
// 2. Skeuomorphism turun jadi "micro" — HANYA buat interaksi fisik (button/switch/
//    slider/knob), bukan lagi identitas visual kedua kayak Batch 33.
// 3. Midnight Blue ambient alpha 0.06 (Batch 33 pakai 0.08 — guide baru eksplisit
//    kasih angka beda, guide baru menang karena ini instruksi yang benar).
// 4. Token names disesuaikan PERSIS ke guide baru: GlassBase/GlassElevated/
//    GlassPressed (ganti GlassSurface/GlassSurfaceElevated/GlassSurfacePressed),
//    AmoledBlack + AmoledSurface baru (2-tone root buat separasi luminance —
//    guide §3 "Important": jangan pure black di semua surface), TextMuted baru.
// 5. Slider knob TIDAK BOLEH lagi "metallic realism" (radial gradient putih->accent
//    ala dial logam Batch 33) — guide §13 eksplisit "Avoid metallic realism that
//    conflicts with the glass aesthetic".
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon — identitas per
// fitur, independen dari surface hierarchy AMOLED/Glass/Midnight Blue di atas.
val BassAccent = Color(0xFFE0865B); val BassAccent2 = Color(0xFFF0B48F)
val VirtualizerAccent = Color(0xFF4FB8C9); val VirtualizerAccent2 = Color(0xFF8DD3DE)
val LoudnessAccent = Color(0xFF4CB88A); val LoudnessAccent2 = Color(0xFF94D4B4)
val EqualizerAccent = Color(0xFFD97AA6); val EqualizerAccent2 = Color(0xFFE8A8C6)
val BatteryAccent = Color(0xFFD9A54A); val BatteryAccent2 = Color(0xFFE8C687)

// Aksen "logam" netral matte — dipakai buat swatch toggle Material You. Tetap netral
// (bukan biru) supaya gak rebutan sama AccentBlue sebagai satu-satunya sinyal
// "state aktif/functional accent" (guide §17).
val DynamicColorAccent = Color(0xFF9C9890); val DynamicColorAccent2 = Color(0xFFC9C4BC)

// ---- §3 AMOLED Foundation — 2-tone root buat separasi luminance ----
// AmoledBlack = root sejati (splash, di belakang segalanya). AmoledSurface =
// canvas layar app (Level 0), sedikit lebih terang dari AmoledBlack biar glass
// Level 1+ di atasnya tetap "perceptible" (guide §3 "Important": don't use pure
// black for every surface).
val AmoledBlack = Color(0xFF030508)
val AmoledSurface = Color(0xFF070A0F)

// ---- §5 Glass Color Tokens — surface hierarchy §4 (Level 1 base, Level 2 elevated,
// Level 3+ interactive/focused dikomposisi di komponen masing-masing, bukan token
// warna terpisah). ----
val GlassBase = Color(0xFF0A0F16)
val GlassElevated = Color(0xFF101722)
val GlassPressed = Color(0xFF070B11)

val GlassWhite = Color.White.copy(alpha = 0.045f)
val GlassHighlight = Color.White.copy(alpha = 0.065f)
val GlassBorder = Color.White.copy(alpha = 0.035f)

val GlassShadow = Color.Black.copy(alpha = 0.70f)

// ---- §6 Midnight Blue — Atmospheric Layer ONLY, gak pernah jadi base surface. ----
val MidnightBlue = Color(0xFF191970)
val MidnightBlueAccent = Color(0xFF6670FF)
val MidnightBlueAmbientAlpha = 0.06f

// §6 "Correct use" — gradient 3-stop persis contoh guide, dipakai kartu/permukaan
// glass yang butuh ambient tint (BUKAN semua permukaan — guide §6 "must NOT
// dominate every card").
val MidnightBlueGlassBrush: Brush = Brush.linearGradient(
    colors = listOf(
        GlassBase,
        MidnightBlue.copy(alpha = MidnightBlueAmbientAlpha),
        GlassElevated
    )
)

// ---- §16 Typography colors ----
val TextPrimary = Color(0xFFEAF0F8)
val TextSecondary = Color(0xFFAAB5C4)
val TextMuted = Color(0xFF737E8C)

// ---- §17 Accent System — restrained cool-blue, sama nilainya dengan
// MidnightBlueAccent (satu accent fungsional, bukan 2 sistem warna berbeda). ----
val AccentBlue = MidnightBlueAccent

// ---- §18 Glow — accent, BUKAN material. Localized only (focused/selected/active),
// alpha direstrain biar gak jadi hal pertama yang dilihat user (guide §18 "If glow
// becomes one of the first things users notice, reduce it"). ----
val SkeuPrimaryGlow = MidnightBlueAccent.copy(alpha = 0.22f)

// ---- §9 Lighting Model — arah cahaya tunggal top-left -> bottom-right, dipakai
// HANYA di komponen tactile micro-skeuomorphic (§10: button/switch/slider/knob),
// TIDAK dipakai di kartu struktural (kartu = glass murni, guide §14 "avoid strong
// bevel"). ----
val SkeuBevelBrush: Brush = Brush.linearGradient(listOf(GlassElevated, GlassBase))
val SkeuBevelBorderBrush: Brush = Brush.linearGradient(listOf(GlassHighlight, Color.Transparent, GlassShadow))

// ---- §19 Spacing & Shape Language ----
val SkeuCardRadius = 20.dp
val SkeuIconBoxRadius = 14.dp

private val DarkColors = darkColorScheme(
    primary = MidnightBlueAccent,
    onPrimary = Color(0xFF04050C),
    primaryContainer = Color(0xFF1E2340),
    onPrimaryContainer = Color(0xFFD4D8FF),
    secondary = Color(0xFF9CA3AC),
    onSecondary = Color(0xFF04050C),
    background = AmoledSurface,
    onBackground = TextPrimary,
    surface = GlassBase,
    onSurface = TextPrimary,
    surfaceVariant = GlassElevated,
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = GlassBorder
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

/** WAJIB dark-mode -> CompositionLocal ini dipertahankan (dipakai
 *  SkeuomorphicComponents.kt) tapi NILAINYA SELALU `true`, tidak ada resolusi/override
 *  light. */
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
