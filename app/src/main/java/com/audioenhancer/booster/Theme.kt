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
// BATCH 33 — "AMOLED Glassmorphism Hybrid + Midnight Blue Gradient (Skeuomorphism-
// lite Tactile UI)": palet bronze/graphite matte (Batch 27-32) DICABUT TOTAL, ganti
// total 100% sesuai acuan design guide user
// (compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md). Perintah
// eksplisit: "timpa theme lama hingga bersih". WAJIB dark-mode, tidak ada fallback
// light theme (lihat PROJECT_STATE.md poin "Riwayat pivot").
//
// Komposisi wajib guide §2.5 "Midnight Blue Gradient Layer" (urutan prioritas visual):
//   AMOLED BLACK (dominan) > FROSTED GLASS (dominan) > MIDNIGHT BLUE TINT (subtle,
//   HANYA gradient/tint di dalam permukaan glass) > GLASS HIGHLIGHT (restrained).
// Midnight Blue TIDAK BOLEH jadi identitas warna dominan (guide §21) — token
// `MidnightBlueAccent` dipakai TERBATAS buat state aktif/glow/border tactile, BUKAN
// buat mengecat seluruh background jadi biru.
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon — identitas per
// fitur, independen dari surface hierarchy AMOLED/Midnight Blue di atas.
val BassAccent = Color(0xFFE0865B); val BassAccent2 = Color(0xFFF0B48F)
val VirtualizerAccent = Color(0xFF4FB8C9); val VirtualizerAccent2 = Color(0xFF8DD3DE)
val LoudnessAccent = Color(0xFF4CB88A); val LoudnessAccent2 = Color(0xFF94D4B4)
val EqualizerAccent = Color(0xFFD97AA6); val EqualizerAccent2 = Color(0xFFE8A8C6)
val BatteryAccent = Color(0xFFD9A54A); val BatteryAccent2 = Color(0xFFE8C687)

// Aksen "logam" netral matte — dipakai buat swatch toggle Material You & elemen
// netral lain. Tetap netral (bukan biru) supaya gak rebutan sama MidnightBlueAccent
// sebagai satu-satunya sinyal "state aktif primary".
val DynamicColorAccent = Color(0xFF9C9890); val DynamicColorAccent2 = Color(0xFFC9C4BC)

// ---- §2 Dark Surface System — baseline tokens (guide "Suggested palette direction") ----
val AmoledBackground = Color(0xFF030508)
val GlassSurface = Color(0xFF0A0F16)
val GlassSurfaceElevated = Color(0xFF101722)
val GlassSurfacePressed = Color(0xFF070B11)

// Midnight Blue = lapisan gradient ambient di DALAM permukaan glass, BUKAN identitas
// background (guide §2.5 mandatory rule: "felt as atmosphere, not read as the primary
// background color").
val MidnightBlueTint = Color(0xFF191970)
val MidnightBlueAccent = Color(0xFF6670FF)
val MidnightBlueGradientAlpha = 0.08f

val TextPrimary = Color(0xFFEAF0F8)
val TextSecondary = Color(0xFFAAB5C4)

val GlassHighlight = Color.White.copy(alpha = 0.055f)
val GlassBorder = Color.White.copy(alpha = 0.035f)
val GlassShadow = Color.Black.copy(alpha = 0.70f)

// Ring/glow state aktif — guide §9 "Glow Rules": localized only, primary/cool accent,
// gak pernah Color.White polos.
val SkeuPrimaryGlow = MidnightBlueAccent.copy(alpha = 0.28f)

// ---- §4 Tactile Depth / Bevel — brush terpusat, dipakai SkeuomorphicComponents.kt ----
// Arah cahaya tunggal top-left -> bottom-right (guide §3): linearGradient tanpa
// start/end eksplisit sudah diagonal default di Compose, KONSISTEN di semua komponen
// (jangan overridedengan arah lain di file lain).
val SkeuBevelBrush: Brush = Brush.linearGradient(listOf(GlassSurfaceElevated, GlassSurface))
val SkeuBevelBorderBrush: Brush = Brush.linearGradient(listOf(GlassHighlight, Color.Transparent, GlassShadow))

// §2.5 gradient tint contoh guide — dipakai di kartu struktural glass (SkeuCard) biar
// "atmosphere", bukan solid flat, TAPI tintnya tetap subtle (alpha rendah, bukan biru
// pekat) sesuai Composition Priority guide.
val MidnightBlueGlassBrush: Brush = Brush.linearGradient(
    colors = listOf(
        GlassSurface,
        MidnightBlueTint.copy(alpha = MidnightBlueGradientAlpha),
        GlassSurfaceElevated
    )
)

// Radius token terpusat (dipakai SkeuomorphicComponents.kt).
val SkeuCardRadius = 20.dp
val SkeuIconBoxRadius = 14.dp

private val DarkColors = darkColorScheme(
    primary = MidnightBlueAccent,
    onPrimary = Color(0xFF04050C),
    primaryContainer = Color(0xFF1E2340),
    onPrimaryContainer = Color(0xFFD4D8FF),
    secondary = Color(0xFF9CA3AC),
    onSecondary = Color(0xFF04050C),
    background = AmoledBackground,
    onBackground = TextPrimary,
    surface = GlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceElevated,
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
 *  light (guide §1.1 + §13: "introduce a light-mode fallback" = implementation guardrail
 *  yang dilarang). */
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
