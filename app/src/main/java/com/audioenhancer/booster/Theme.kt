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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// BATCH 12 — "Neumorphic Hybrid": glassmorphism (Batch 2-4, v1.29-v1.49)
// DICABUT TOTAL di level struktur, bukan cuma palet lagi. Draft divalidasi
// dulu di docs/preview/current.html sebelum di-port ke sini (lihat
// PROJECT_STATE.md poin 5 "Riwayat pivot"). Perubahan kunci:
// 1. TIDAK ADA translucency/alpha-blend surface lagi — semua kartu SOLID
//    (containerColor = surface tanpa .copy(alpha=...)). Translucent+blur
//    adalah sumber utama kontras teks yang tidak konsisten di struktur lama.
// 2. Background dinaikkan dari hitam pekat (#0A0A0A) / putih nyaris murni
//    (#F7F5F1) ke abu graphite medium (dark) / abu hangat medium (light).
//    WAJIB — neumorphism butuh base color yang tidak terlalu gelap ATAU
//    terlalu terang supaya SISI TERANG dual-shadow-nya (bukan cuma sisi
//    gelap) kelihatan. Kalau base terlalu ekstrem, satu sisi shadow jadi
//    nyaris invisible (persis pola masalah alpha-di-atas-hitam-pekat era
//    Apple-style, Batch 1 — LESSON yang sama berlaku lagi di sini).
// 3. Tidak ada lagi gradient-clip TEXT (headline/value yang teksnya sendiri
//    di-gradient) — kontras jadi tidak konsisten tergantung posisi. Gradient
//    sekarang HANYA dipakai di elemen non-teks (ikon, waveform dekoratif).
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon (bukan lagi
// buat border/teks — lihat NeumorphicCard di MainActivity.kt).
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

// Warna dual-shadow neumorphic — dipakai lewat Modifier.neumorphicDepth() /
// neumorphicInnerShadow() (MainActivity.kt, drawBehind manual, BUKAN Modifier.shadow
// elevation — lihat catatan Batch 14). Batch 15: alpha diselaraskan PERSIS ke spec
// design system user ("Hybrid Neumorphism"): sisi gelap ~60% black, sisi terang ~4%
// white (dark theme) — jauh lebih tipis dari draft Batch 12/14 (0x33=20%), tapi justru
// itu yang bikin efeknya "subtle premium" bukan glow norak, PERSIS kayak HTML aslinya.
val NeuShadowDarkSide = Color(0x99000000)        // ~60% black — dark theme, sisi gelap
val NeuShadowLightSideDark = Color(0x0AFFFFFF)   // ~4% white — dark theme, sisi terang
val NeuShadowLightSideLight = Color(0xFFFFFFFF)  // dipakai di light theme (di luar scope spec user)
val NeuShadowDarkSideLight = Color(0x40000000)   // sisi gelap versi light theme (di luar scope spec user)

// Batch 15: token radius & parameter shadow terpusat di sini (bukan angka hardcode
// tersebar di MainActivity.kt) — sesuai spec design system user.
val NeuCardRadius = 22.dp
val NeuIconBoxRadius = 14.dp
val NeuShadowDarkOffset = 8.dp
val NeuShadowDarkBlur = 17.dp
val NeuShadowLightOffset = 6.dp
val NeuShadowLightBlur = 15.dp

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

private val LightColors = lightColorScheme(
    primary = PremiumBronzeLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEE0C4),
    onPrimaryContainer = Color(0xFF3D311B),
    secondary = Color(0xFF5B6068),
    onSecondary = Color.White,
    background = Color(0xFFE7E4DC),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFE7E4DC),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFDEDBD2),
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

// Shape kembali membulat lembut (kesan "kaca premium"), bukan sudut tajam
// brutalist Batch 1 ataupun super-bulat minimal Apple.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Batch 12: NeumorphicCard/NeumorphicTintedCard (MainActivity.kt) butuh tahu status
 *  dark/light AKTUAL (hasil resolusi override manual user di ThemeModeToggle), BUKAN
 *  cuma `isSystemInDarkTheme()` — kalau dipakai langsung, kartu bakal salah pilih
 *  warna shadow pas user override tema berlawanan dari sistem. `darkTheme` yang
 *  sudah diresolusi di sini (bukan raw system value) di-broadcast lewat CompositionLocal
 *  ini supaya composable manapun di bawah bisa ikut tahu tanpa perlu parameter manual. */
val LocalIsDarkTheme = compositionLocalOf { false }

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
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
