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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// BATCH 38 — tambahan (BUKAN rewrite ulang Batch 37): varian tema ke-3, awalnya
// "Skeuomorphism" ("theme custom Skeuomorphism dark mode yang asli") — toggle baru
// di Settings, SEJAJAR toggle Aurora Glass (bukan sub-opsinya), TIDAK mengubah/
// menghapus 2 varian glass Batch 37.
//
// BATCH 46 — UPGRADE FILOSOFI (diminta user eksplisit "Skeuomorphism -> Neumorphism
// ultra realistic+immersive, aksen Platinum+Ruby"): bahasa desain varian ke-3
// diganti dari skeuomorphism (bevel hard-edge, shadow Color.White/Black mentah)
// menjadi NEUMORPHISM genuine (soft-UI, shadow pasangan SEHUE base panel — pola
// yang sama seperti "Studio Equalizer" Batch 43, tapi palet & karakter beda total:
// platinum metalik netral-dingin + glow ruby jewel-tone, elevation/shadow LEBIH
// DALAM & sheen LEBIH KUAT dari Studio Equalizer untuk kesan "ultra realistic +
// immersive"). Semua token warna/brush di-RENAME `SkeuoXxx` -> `NeumoXxx` (ikut
// preseden Batch 34: pivot filosofi = rename total, bukan reuse nama lama) —
// `SkeuomorphismSkeuTokens`/`SkeuomorphismDarkColors`/`SkeuomorphismShapes` jadi
// `NeumorphismSkeuTokens`/`NeumorphismDarkColors`/`NeumorphismShapes`. TIDAK
// diubah (Protected Asset persistence key — data user lama harus tetap valid):
// enum `AppThemeStyle.SKEUOMORPHISM` & `PrefsHelper.APP_THEME_SKEUOMORPHISM`.
// Detail lengkap: `CHANGELOG.md` v1.83.0.
//
// BATCH 47 — user kirim screenshot + feedback: "kurang depth & tactile, ambient
// lighting-nya berasa bocor". Root cause: (1) `SkeuCard` cuma pakai 1 native
// `Modifier.shadow()` (default hitam, kontras rendah di atas panel gelap) + brush
// linear-gradient tunggal — gak ada shadow TERANG buat sisi "kena cahaya", padahal
// itu inti soft-UI neumorphism; (2) `skeuGlow` (SkeuomorphicComponents.kt) pakai
// radial 2-stop hard cutoff (`[color, Transparent]`) — falloff-nya kasar, kebaca
// sebagai "bocor" bukan "menyala ambient". Fix: `SkeuTokens` +2 field
// `shadowLightTint`/`shadowDarkTint` (NATIVE `Modifier.shadow(ambientColor=,
// spotColor=)`, BUKAN custom Paint/BlurMaskFilter — preseden Batch 14/32 larang
// hack blur custom krn gak reliable lintas API level), dipakai render 2 layer
// shadow terarah (terang kiri-atas + gelap kanan-bawah) KHUSUS Neumorphism (3
// varian lain tetap `Color.Transparent` = 0 perubahan). `skeuGlow` di-multi-stop
// (4-stop, falloff halus) — berlaku global ke SEMUA pemakainya (power button,
// switch, preset chip, semua varian), bukan cuma Neumorphism.
// ============================================================================

// ============================================================================
// BATCH 37 — REWRITE TOTAL sektor UI/UX (diminta user eksplisit): "iOS-style
// Glassmorphism" jadi bahasa desain DOMINAN di seluruh app, dengan Midnight-Blue
// SEKARANG sebagai gradasi/hint yang kelihatan jelas (bukan lagi "subtle 6%" era
// Batch 34) — TAPI readability tetap prioritas #1 (kontras teks dinaikkan
// signifikan, bukan dikorbankan demi estetika). Ini BUKAN ganti palet warna doang:
// 1. `MidnightBlueGlassBrush`/`RadicalGlassBrush` (kartu) sekarang multi-stop
//    diagonal genuine frosted-glass composition (4 stop, bukan 3 stop lama).
// 2. Token BARU `specularBrush` (SkeuTokens) — sheen/kilau kaca ala iOS di
//    pojok kiri-atas kartu & tombol power, digambar sebagai layer background
//    KEDUA (lihat SkeuomorphicComponents.kt `SkeuCard`/`SkeuTintedCard`/
//    `SkeuPowerButton` — bukan cuma warna, tapi layer render baru).
// 3. Border kartu (`cardBorderBrush`) sekarang gradient highlight->transparent
//    (`GlassBorderBrush`), bukan solid alpha tipis — emulasi tepi kaca miring
//    kena cahaya, ciri khas iOS glass (Control Center/Notification Shade).
// 4. Radius dinaikkan (`SkeuCardRadius` 20->26dp, `SkeuIconBoxRadius` 14->16dp,
//    `AppShapes` semua step) — iOS-style rounded, bukan Android-standar.
// 5. Root screen background (dipakai `MainActivity.kt`) sekarang
//    `ScreenBackgroundBrush` — vertical gradient Midnight-Blue -> nyaris-hitam,
//    supaya kartu glass punya backdrop ber-variasi buat "dibaca" sebagai kaca
//    (glassmorphism butuh backdrop yang gak flat monoton di baliknya).
// 6. 2 sistem desain (switch Settings, arsitektur `SkeuTokens`/`AppThemeStyle`
//    dari Batch 36 DIPERTAHANKAN — bukan fitur yang dihapus) SEKARANG DUA-DUANYA
//    varian iOS Glassmorphism (dominan), bukan lagi 1 glass + 1 skeuomorphism
//    bevel-raised: default "Midnight Glass" (restrained, tenang), varian kedua
//    "Aurora Glass" (lebih vivid/saturated, sheen & glow lebih kuat) — beda
//    intensitas, BUKAN beda bahasa desain lagi. Nama const persistence
//    (`APP_THEME_AMOLED_GLASS`/`APP_THEME_RADICAL_SKEUO`, `PrefsHelper.kt`) & nama
//    enum (`AppThemeStyle.AMOLED_GLASS`/`RADICAL_SKEUO`) SENGAJA TIDAK diubah —
//    itu Protected Asset persistence key, ganti nama const akan pecah data user
//    lama tanpa migrasi; cukup REPRESENTASI VISUAL-nya yang di-rewrite total.
// 7. Kontras teks dinaikkan tegas (`TextPrimary`/`TextSecondary`/`TextMuted`,
//    `RadicalText*`) — permintaan eksplisit user "readability maksimal", bukan
//    dikorbankan demi efek kaca.
// ============================================================================

// Tiap fitur punya PASANGAN warna (gelap->terang) buat gradient icon — identitas per
// fitur, independen dari surface hierarchy glass di atas. Dipertahankan dari batch
// sebelumnya (bukan sumber keluhan desain).
val BassAccent = Color(0xFFE0865B); val BassAccent2 = Color(0xFFF0B48F)
val VirtualizerAccent = Color(0xFF4FB8C9); val VirtualizerAccent2 = Color(0xFF8DD3DE)
val LoudnessAccent = Color(0xFF4CB88A); val LoudnessAccent2 = Color(0xFF94D4B4)
val EqualizerAccent = Color(0xFFD97AA6); val EqualizerAccent2 = Color(0xFFE8A8C6)
val BatteryAccent = Color(0xFFD9A54A); val BatteryAccent2 = Color(0xFFE8C687)

// Aksen netral buat swatch toggle Material You — tetap netral, gak rebutan sama
// AccentBlue sebagai satu-satunya sinyal "state aktif/functional accent".
val DynamicColorAccent = Color(0xFF9C9890); val DynamicColorAccent2 = Color(0xFFC9C4BC)

// ---- Root — 2-tone dasar (splash + di balik gradient layar). Midnight-blue-black,
// BUKAN abu-netral/graphite lama — root sekarang eksplisit condong biru gelap supaya
// hint Midnight-Blue kerasa dari detik pertama (splash), bukan cuma di dalam kartu. ----
val AmoledBlack = Color(0xFF03040B)
val AmoledSurface = Color(0xFF080B1A)

// ---- Glass surface hierarchy — base kartu jadi lebih pekat biru-navy (bukan
// nyaris-netral lama) supaya "midnight" beneran kebaca sebagai warna, bukan cuma
// tint 6% yang nyaris gak kelihatan. ----
val GlassBase = Color(0xFF121A33)
val GlassElevated = Color(0xFF1C2748)
val GlassPressed = Color(0xFF0A0E20)

val GlassWhite = Color.White.copy(alpha = 0.08f)
val GlassHighlight = Color.White.copy(alpha = 0.22f)
val GlassBorder = Color.White.copy(alpha = 0.16f)
val GlassShadow = Color.Black.copy(alpha = 0.55f)

// ---- Midnight Blue — sekarang HINT DOMINAN (bukan atmospheric 6% lama), tapi tetap
// dikomposisi lewat brush multi-stop (poin 1 catatan batch di atas), bukan solid
// dominan penuh 1 warna (biar gak jadi "blue interface with black elements" versi
// ekstrem lain — tetap glass, cuma birunya sekarang kentara). ----
val MidnightBlue = Color(0xFF24359E)
val MidnightBlueAccent = Color(0xFF5E7BFF)
val MidnightBlueAmbientAlpha = 0.20f

/** Kartu struktural — 4-stop diagonal (top-left cerah/biru -> bottom-right gelap),
 *  komposisi genuine frosted-glass, bukan 3-stop rata lama. `lerp()` dipakai biar
 *  tiap stop punya campuran biru midnight yang konsisten satu sama lain, bukan
 *  hex hardcode independen per-stop. */
val MidnightBlueGlassBrush: Brush = Brush.linearGradient(
    listOf(
        lerp(GlassElevated, MidnightBlue, MidnightBlueAmbientAlpha + 0.12f),
        GlassElevated,
        GlassBase,
        lerp(GlassBase, MidnightBlue, MidnightBlueAmbientAlpha * 0.55f)
    )
)

/** Border kartu — gradient highlight->transparent (bukan solid alpha tipis lama),
 *  emulasi tepi kaca miring kena cahaya dari sudut kiri-atas (§ konsisten arah
 *  cahaya tunggal top-left->bottom-right yang sudah dipakai project ini sejak
 *  Batch 32). */
val GlassBorderBrush: Brush = Brush.linearGradient(listOf(GlassHighlight, GlassBorder, Color.Transparent))

/** Sheen/kilau kaca ala iOS — layer BACKGROUND KEDUA (bukan cuma warna dipakai di
 *  1 tempat), dipasang di atas `cardBrush`/`bevelBrush` tapi di bawah `border()` &
 *  konten (lihat `SkeuomorphicComponents.kt`). Diagonal default (tanpa start/end
 *  eksplisit) otomatis resolve pojok-ke-pojok bounding box (perilaku Compose yang
 *  sudah dikonfirmasi dipakai sejak Batch 32) — 3 stop supaya sheen-nya
 *  terkonsentrasi di ~30% pojok kiri-atas, sisanya transparan penuh (TIDAK menutupi
 *  teks di tengah/bawah kartu, readability tetap aman). */
val GlassSpecularBrush: Brush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f), Color.Transparent)
)

// ---- Typography colors — kontras dinaikkan tegas dari batch sebelumnya (permintaan
// eksplisit user "readability maksimal"). TextMuted khususnya naik cukup jauh
// (#737E8C -> #8D96AC) karena sebelumnya ini dipakai buat caption/hint yang justru
// paling gampang gak kebaca di atas kartu kaca gelap. ----
val TextPrimary = Color(0xFFF3F6FF)
val TextSecondary = Color(0xFFC5CCE2)
val TextMuted = Color(0xFF8D96AC)

// ---- Accent System — restrained cool-blue, sama nilainya dengan MidnightBlueAccent
// (satu accent fungsional). ----
val AccentBlue = MidnightBlueAccent

// ---- Glow — dinaikkan sedikit dari 0.22 lama biar sepadan sama sheen kaca yang
// sekarang lebih hidup, tapi tetap direstrain (bukan neon). ----
val SkeuPrimaryGlow = MidnightBlueAccent.copy(alpha = 0.30f)

// ---- Lighting model komponen fisik (power button/knob) — arah cahaya tunggal
// top-left -> bottom-right, sekarang stop pertama dicampur sedikit putih biar ada
// highlight nyata di puncak tombol (bukan cuma 2-stop rata lama). ----
val SkeuBevelBrush: Brush = Brush.linearGradient(
    listOf(lerp(GlassElevated, Color.White, 0.07f), GlassElevated, GlassBase)
)
val SkeuBevelBorderBrush: Brush = Brush.linearGradient(listOf(GlassHighlight, Color.Transparent, GlassShadow))

// ---- Spacing & Shape Language — radius dinaikkan (iOS-style lebih membulat,
// bukan radius standar Android lama). ----
val SkeuCardRadius = 26.dp
val SkeuIconBoxRadius = 16.dp

// ============================================================================
// Varian kedua ("Aurora Glass", Settings switch existing dari Batch 36 — nama
// const/enum TETAP `RADICAL_SKEUO` di kode, lihat catatan poin 6 di atas) — SAMA
// bahasa desain iOS Glassmorphism + Midnight Blue, cuma lebih vivid/saturated:
// sheen lebih terang, elevation lebih terasa, accent lebih cerah. Dipilih via
// switch "Gaya Tampilan" di Settings, dipersist `PrefsHelper.getAppThemeStyle`.
// ============================================================================

val RadicalBackground = Color(0xFF05070F)
val RadicalSurface = Color(0xFF141C3E)
val RadicalSurfaceRaised = Color(0xFF212C5C)
val RadicalSurfaceRecessed = Color(0xFF090C1C)

val RadicalEdgeHighlight = Color.White.copy(alpha = 0.26f)
val RadicalEdgeShadow = Color.Black.copy(alpha = 0.62f)

val RadicalTextPrimary = Color(0xFFF6F8FF)
val RadicalTextSecondary = Color(0xFFCCD3EA)
val RadicalTextMuted = Color(0xFF9AA2C0)

val RadicalAccent = Color(0xFF7C93FF)

val RadicalBevelBrush: Brush = Brush.linearGradient(
    listOf(lerp(RadicalSurfaceRaised, Color.White, 0.09f), RadicalSurfaceRaised, RadicalSurface)
)
val RadicalBevelBorderBrush: Brush = Brush.linearGradient(
    listOf(RadicalEdgeHighlight, Color.Transparent, RadicalEdgeShadow)
)

/** Kartu struktural varian Aurora — SEKARANG juga genuine glass (bukan lagi
 *  `SolidColor` flat bevel-raised era Batch 36) supaya "iOS Glassmorphism dominan"
 *  berlaku di KEDUA varian tema, bukan cuma default. */
val RadicalGlassBrush: Brush = Brush.linearGradient(
    listOf(
        lerp(RadicalSurfaceRaised, Color.White, 0.08f),
        RadicalSurfaceRaised,
        RadicalSurface,
        RadicalSurfaceRecessed
    )
)

val RadicalGlassSpecularBrush: Brush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f), Color.Transparent)
)

val RadicalPrimaryGlow = RadicalAccent.copy(alpha = 0.34f)

/** Highlight solid buat radial gradient knob slider — dinaikkan ke nyaris putih
 *  penuh, meniru bead/thumb kaca-terang khas iOS Slider (accent tetap dibawa lewat
 *  ring border 2dp di komponennya, bukan lewat warna isi thumb). */
val RadicalKnobHighlight: Color = lerp(RadicalSurfaceRaised, Color.White, 0.32f)

// ============================================================================
// Varian ke-3 — awalnya "Skeuomorphism" (Batch 38-39), di-UPGRADE Batch 46 jadi
// "Neumorphism" ultra realistic+immersive, aksen Platinum+Ruby (diminta user
// eksplisit). Beda kunci vs Skeuomorphism lama: shadow pasangan (terang+gelap)
// SEKARANG SEHUE base panel (bukan Color.White/Black mentah — itu pembeda inti
// neumorphism vs skeuomorphism, lihat juga blok komentar Studio Equalizer di
// bawah). "Ultra realistic+immersive" = elevation/shadow LEBIH DALAM (10dp,
// tertinggi dari 4 varian) + bevel 5-stop (bukan 4) + sheen specular LEBIH KUAT
// dari Studio Equalizer, supaya kesan "timbul"-nya lebih dramatis/immersive.
// Platinum = aksen metalik netral-dingin (bevel highlight, border, knob — dipakai
// LUAS di seluruh panel), Ruby = warna jewel-tone jenuh KHUSUS primary/state-aktif
// (glow, ring, primary color) — kombinasi platinum-netral + ruby-vivid meniru
// perhiasan/jam tangan mewah platinum bermata ruby. Dipilih via toggle
// "Skeuomorphism" di Settings (nama toggle & persistence key TIDAK diubah, lihat
// header Batch 46 di atas), TIDAK mengubah/menghapus 3 varian existing lain.
// ============================================================================

val NeumoBackground = Color(0xFF1B1B1F)
val NeumoPanel = Color(0xFF242429)
val NeumoPanelRaised = Color(0xFF2E2E36)
val NeumoPanelRecessed = Color(0xFF101012)

val NeumoTextPrimary = Color(0xFFF3F2F0)
val NeumoTextSecondary = Color(0xFFC0C0C6)
val NeumoTextMuted = Color(0xFF87878F)

/** Aksen Platinum — metalik netral-dingin (hex dunia-nyata platinum ~#E5E4E2),
 *  dipakai LUAS buat bevel highlight/border/knob di SELURUH panel (bukan cuma 1
 *  chip), meniru brushed-platinum asli. Ruby (di bawah) yang bawa saturasi/hue —
 *  Platinum sengaja netral supaya ruby "menyala" kontras di atasnya. */
val NeumoPlatinum = Color(0xFFE4E3E0)

/** Nada platinum lebih gelap — variasi 2-stop metalik (refleksi brushed-metal
 *  selalu punya gradasi terang-gelap, bukan flat 1 warna). */
val NeumoPlatinumDeep = Color(0xFF9C9CA1)

/** Aksen Ruby — jewel-tone merah jenuh, KHUSUS primary/state-aktif (glow, ring,
 *  chip terpilih, primary color) — BUKAN warna permukaan panel pasif (beda dari
 *  Platinum yang menyebar luas). Kontras sengaja tinggi dari netral Platinum &
 *  dari accent biru 2 varian glass / lime Studio Equalizer. */
val NeumoRuby = Color(0xFFE0115F)
val NeumoRubyDeep = Color(0xFF9E0C43)

/** Edge highlight/shadow border SEHUE (platinum-tinted & panel-recessed-tinted,
 *  BUKAN Color.White/Black mentah seperti era Skeuomorphism lama) — syarat wajib
 *  neumorphism genuine di project ini. Sengaja dideklarasikan SETELAH
 *  `NeumoPlatinum`/`NeumoPanelRecessed` di atas (Kotlin top-level `val` di-init
 *  berurutan sesuai posisi file — referensi ke `val` yang belum di-init akan
 *  null/crash saat class-load, BUKAN cuma soal gaya baca kode). */
val NeumoEdgeHighlight = NeumoPlatinum.copy(alpha = 0.26f)
val NeumoEdgeShadow = NeumoPanelRecessed.copy(alpha = 0.84f)

/** Bevel raised/recessed — 5-stop (lebih banyak dari 4-stop era Skeuomorphism)
 *  buat transisi lebih halus/dalam ("ultra realistic"), tetap SEHUE base panel di
 *  ujung-ujungnya, dicampur sedikit Platinum/PlatinumDeep di titik puncak/dasar —
 *  biar karakter metalik platinum kerasa di SELURUH panel. */
val NeumoBevelBrush: Brush = Brush.linearGradient(
    listOf(
        lerp(NeumoPanelRaised, NeumoPlatinum, 0.18f),
        lerp(NeumoPanelRaised, NeumoPlatinum, 0.05f),
        NeumoPanel,
        lerp(NeumoPanelRecessed, NeumoPlatinumDeep, 0.10f),
        NeumoPanelRecessed
    )
)
val NeumoBevelBorderBrush: Brush = Brush.linearGradient(
    listOf(NeumoEdgeHighlight, Color.Transparent, NeumoEdgeShadow)
)

/** Sheen specular — LEBIH KUAT dari Studio Equalizer (alpha 0.16f) supaya kesan
 *  "immersive"-nya lebih terasa, tapi tetap platinum-tinted (bukan Color.White
 *  polos era Skeuomorphism) — glossy metalik, bukan sheen kaca. */
val NeumoSpecularBrush: Brush = Brush.linearGradient(
    listOf(NeumoPlatinum.copy(alpha = 0.30f), NeumoPlatinum.copy(alpha = 0.06f), Color.Transparent)
)

/** Glow ruby lebih pekat (0.38f) dari 3 varian lain (0.30f) — "immersive" berarti
 *  state aktif menyala lebih dramatis. */
val NeumoPrimaryGlow = NeumoRuby.copy(alpha = 0.38f)

/** Knob slider — bead platinum terang (ring accent ruby dibawa lewat border 2dp
 *  di komponennya, bukan warna isi knob). */
val NeumoKnobHighlight: Color = lerp(NeumoPanelRaised, NeumoPlatinum, 0.55f)

/** Radius kartu/icon-box KHUSUS varian ini — generous/soft-UI (neumorphism genuine
 *  butuh sudut lebih membulat dari skeuomorphism 14dp/10dp lama), tapi beda dari
 *  Studio Equalizer (20dp/14dp) & iOS-glass (26dp/16dp) biar tetap otonom. */
val NeumoCardRadius = 22.dp
val NeumoIconBoxRadius = 15.dp

/** Background layar — vertical gradient netral sehue panel, TETAP tidak vivid
 *  (ruby sengaja disimpan cuma buat state aktif, bukan ambient backdrop — biar
 *  kontrasnya "menyala" pas dipakai, bukan tenggelam jadi hint tipis kayak Midnight
 *  Blue di varian glass). */
val NeumoScreenBackgroundBrush: Brush = Brush.verticalGradient(
    listOf(NeumoPanelRaised, NeumoPanel, NeumoBackground)
)

// ============================================================================
// Batch 43: Varian 4 "Studio Equalizer" — NEUMORPHISM (soft-UI), sama filosofi
// dengan varian 3 "Neumorphism" (Batch 46, blok di atas) tapi palet & karakter
// beda total: netral abu-abu studio + glow lime, low-contrast/subtle by design
// (BUKAN "ultra realistic+immersive" — bevel/elevation Studio Eq sengaja lebih
// halus dari varian 3). Beda kunci neumorphism vs glass (AmoledGlass/Radical)
// atau skeuomorphism hard-edge (era lama varian 3, sebelum Batch 46): shadow
// pasangan (terang+gelap) yang dipakai buat kesan timbul/cekung TETAP SEHUE sama
// base panel (bukan pure black/white alpha) — makanya seluruh token di bawah
// nurunin dari 4 warna EKSAK yang diminta user (bukan hasil rekaan), TIDAK ada
// Color.White/Color.Black dipakai buat shadow (beda dari NeumoEdgeHighlight/
// NeumoEdgeShadow di atas — beda PALET, sama-sama sehue).
// Palet asli diminta user, tema "papan mixer studio rekaman profesional":
//  - Background/Base   #1E222A (abu-abu studio gelap)
//  - Dark Shadow       #14171D (bayangan sudut BAWAH)
//  - Light Shadow      #282D37 (bayangan sudut ATAS)
//  - Aksen Glow (Aktif) #39FF14 (hijau lime elektrik, lampu indikator)
// ============================================================================

val StudioEqBackground = Color(0xFF1E222A)
val StudioEqDarkShadow = Color(0xFF14171D)
val StudioEqLightShadow = Color(0xFF282D37)

/** Hijau lime elektrik — dipakai KHUSUS buat elemen "menyala/aktif" (ring glow
 *  power button ditekan, primaryGlow, indikator level) — meniru lampu LED VU-meter
 *  papan mixer studio, BUKAN warna permukaan panel (panel tetap netral abu-abu
 *  studio gelap, hijau cuma nyala pas ada state aktif — sesuai deskripsi user
 *  "hijau neon ... kesan frekuensi audio presisi & aman"). */
val StudioEqAccent = Color(0xFF39FF14)

val StudioEqTextPrimary = Color(0xFFF0F2F5)
val StudioEqTextSecondary = Color(0xFFC2C7D1)
val StudioEqTextMuted = Color(0xFF9AA1AC)

/** Fill panel/kartu — gradient 3-stop TERANG(atas)->base->GELAP(bawah), persis
 *  arah yang dideskripsikan user ("Dark Shadow: bayangan sudut bawah" / "Light
 *  Shadow: bayangan sudut atas") — bukan bevel tegas ala skeuomorphism, transisi
 *  jauh lebih halus/subtle (khas neumorphism soft-UI, low-contrast by design). */
val StudioEqCardBrush: Brush = Brush.linearGradient(
    listOf(StudioEqLightShadow, StudioEqBackground, StudioEqDarkShadow)
)

/** Dipakai buat elemen "raised" lain (SkeuPowerButton) — arah sama dengan
 *  StudioEqCardBrush, satu bahasa visual konsisten di seluruh varian ini. */
val StudioEqBevelBrush: Brush = StudioEqCardBrush

/** Border SEHUE shadow (bukan Color.White/Black) — palet beda dari
 *  `NeumoEdgeHighlight`/`NeumoEdgeShadow` (varian 3) tapi sama-sama neumorphism
 *  genuine, sama-sama BUKAN skeuomorphism hard-edge era lama. */
val StudioEqBevelBorderBrush: Brush = Brush.linearGradient(
    listOf(StudioEqLightShadow.copy(alpha = 0.55f), Color.Transparent, StudioEqDarkShadow.copy(alpha = 0.65f))
)

/** Sheen atas SANGAT halus — neumorphism matte-subtle (Studio Eq low-contrast by
 *  design), bukan glossy kaca (GlassSpecularBrush) atau sheen platinum lebih kuat
 *  (NeumoSpecularBrush, varian 3, Batch 46 "immersive"). */
val StudioEqSpecularBrush: Brush = Brush.linearGradient(
    listOf(StudioEqLightShadow.copy(alpha = 0.16f), Color.Transparent, Color.Transparent)
)

val StudioEqPrimaryGlow = StudioEqAccent.copy(alpha = 0.30f)

/** Knob slider tetap netral terang (bukan hijau) — hijau accent DIJAGA cuma
 *  buncul di state aktif/glow (primaryGlow, ring power button), sesuai deskripsi
 *  user "lampu indikator", bukan warna komponen pasif. */
val StudioEqKnobHighlight: Color = lerp(StudioEqLightShadow, Color.White, 0.45f)

/** Radius sendiri — soft-UI neumorphism klasik pakai rounded generous (gak
 *  se-tegas Skeuomorphism 14dp/10dp, gak se-bubbly iOS-glass 26dp/16dp). */
val StudioEqCardRadius = 20.dp
val StudioEqIconBoxRadius = 14.dp

/** Background layar — gradient netral gelap konsisten sama base panel (bukan
 *  biru midnight ala glass, bukan gunmetal netral ala skeuo) — nuansa "studio
 *  rack gelap" sendiri. */
val StudioEqScreenBackgroundBrush: Brush = Brush.verticalGradient(
    listOf(StudioEqLightShadow, StudioEqBackground, StudioEqDarkShadow)
)

/** Token yang beda antar 4 varian desain (Batch 43: +1, sebelumnya 3), dibaca
 *  lewat `LocalSkeuTokens.current` (SkeuomorphicComponents.kt) — 1 kode komponen,
 *  4 varian, TANPA duplikasi. Field baru WAJIB diisi di SEMUA instance di bawah
 *  kalau ditambah lagi.
 *  Batch 39: `cardRadius`/`iconBoxRadius` ditambah — sebelumnya radius kartu/icon-box
 *  hardcode ke const global `SkeuCardRadius`/`SkeuIconBoxRadius` (dipakai SEMUA
 *  varian tanpa beda), sekarang per-varian supaya Skeuomorphism (radius lebih
 *  tegas/kecil, khas hardware fisik) beneran otonom — gak numpang radius iOS-glass
 *  Batch 37 punya 2 varian glass.
 *  Batch 47: `shadowLightTint`/`shadowDarkTint` ditambah — dipakai `SkeuCard`/
 *  `SkeuTintedCard` (SkeuomorphicComponents.kt) buat render 2 layer
 *  `Modifier.shadow(ambientColor=, spotColor=)` NATIVE terarah (terang
 *  offset kiri-atas + gelap offset kanan-bawah) di ATAS shadow tunggal lama —
 *  BUKAN custom BlurMaskFilter/Paint (preseden Batch 14/32 larang, gak reliable
 *  lintas API level). Default `Color.Transparent` di 3 varian lain = layer ini
 *  DI-SKIP total (0 perubahan visual/perf dari sebelum Batch 47) — cuma varian 3
 *  Neumorphism yang diisi warna asli, sesuai keluhan user soal "kurang depth &
 *  tactile" (screenshot Batch 46). */
data class SkeuTokens(
    val mutedText: Color,
    val bevelBrush: Brush,
    val bevelBorderBrush: Brush,
    val primaryGlow: Color,
    val baseSurface: Color,
    val elevatedSurface: Color,
    val cardBrush: Brush,
    val cardBorderBrush: Brush,
    val cardElevation: Dp,
    val sliderKnobHighlight: Color,
    val specularBrush: Brush,
    val cardRadius: Dp,
    val iconBoxRadius: Dp,
    val shadowLightTint: Color,
    val shadowDarkTint: Color
)

/** Varian 1 (default): "Midnight Glass" — iOS glassmorphism restrained/tenang. */
val AmoledGlassSkeuTokens = SkeuTokens(
    mutedText = TextMuted,
    bevelBrush = SkeuBevelBrush,
    bevelBorderBrush = SkeuBevelBorderBrush,
    primaryGlow = SkeuPrimaryGlow,
    baseSurface = GlassBase,
    elevatedSurface = GlassElevated,
    cardBrush = MidnightBlueGlassBrush,
    cardBorderBrush = GlassBorderBrush,
    cardElevation = 3.dp,
    sliderKnobHighlight = Color.White.copy(alpha = 0.92f),
    specularBrush = GlassSpecularBrush,
    cardRadius = SkeuCardRadius,
    iconBoxRadius = SkeuIconBoxRadius,
    // Batch 47: TETAP Transparent — kartu glass sengaja "visually quiet" (guide
    // §8 lama, Batch 32), dual-shadow terarah CUMA buat Neumorphism.
    shadowLightTint = Color.Transparent,
    shadowDarkTint = Color.Transparent
)

/** Varian 2: "Aurora Glass" — iOS glassmorphism lebih vivid/saturated, sheen &
 *  elevation lebih terasa, accent lebih cerah. Tetap glass murni, BUKAN
 *  skeuomorphism bevel-raised lagi (beda dari era Batch 36). */
val RadicalSkeuoSkeuTokens = SkeuTokens(
    mutedText = RadicalTextMuted,
    bevelBrush = RadicalBevelBrush,
    bevelBorderBrush = RadicalBevelBorderBrush,
    primaryGlow = RadicalPrimaryGlow,
    baseSurface = RadicalSurface,
    elevatedSurface = RadicalSurfaceRaised,
    cardBrush = RadicalGlassBrush,
    cardBorderBrush = RadicalBevelBorderBrush,
    cardElevation = 6.dp,
    sliderKnobHighlight = RadicalKnobHighlight,
    specularBrush = RadicalGlassSpecularBrush,
    cardRadius = SkeuCardRadius,
    iconBoxRadius = SkeuIconBoxRadius,
    shadowLightTint = Color.Transparent,
    shadowDarkTint = Color.Transparent
)

/** Varian 3: "Neumorphism" ultra realistic+immersive (Batch 46, ganti dari
 *  Skeuomorphism bevel-hard Batch 38-39) — soft-UI genuine, shadow sehue base
 *  panel, aksen Platinum (metalik luas) + Ruby (glow/primary). `cardElevation`
 *  10dp — TERTINGGI dari 4 varian (immersive = pop paling dramatis). Radius
 *  sendiri (`NeumoCardRadius`/`NeumoIconBoxRadius`, 22dp/15dp) — otonom, bukan
 *  numpang const varian lain. Batch 47: `shadowLightTint`/`shadowDarkTint` diisi
 *  ASLI (satu-satunya varian yang diisi) — respon langsung ke keluhan user
 *  "kurang depth & tactile, ambient lighting bocor" di screenshot Batch 46. */
val NeumorphismSkeuTokens = SkeuTokens(
    mutedText = NeumoTextMuted,
    bevelBrush = NeumoBevelBrush,
    bevelBorderBrush = NeumoBevelBorderBrush,
    primaryGlow = NeumoPrimaryGlow,
    baseSurface = NeumoPanel,
    elevatedSurface = NeumoPanelRaised,
    cardBrush = NeumoBevelBrush,
    cardBorderBrush = NeumoBevelBorderBrush,
    cardElevation = 10.dp,
    sliderKnobHighlight = NeumoKnobHighlight,
    specularBrush = NeumoSpecularBrush,
    cardRadius = NeumoCardRadius,
    iconBoxRadius = NeumoIconBoxRadius,
    shadowLightTint = NeumoPlatinum.copy(alpha = 0.55f),
    shadowDarkTint = NeumoPanelRecessed.copy(alpha = 0.95f)
)

/** Varian 4: "Studio Equalizer" — neumorphism soft-UI (Batch 43), palet abu-abu
 *  studio gelap + shadow pasangan sehue + aksen neon-lime khusus state aktif.
 *  Lihat blok komentar panjang di atas buat detail palet & rasional tiap token. */
val StudioEqSkeuTokens = SkeuTokens(
    mutedText = StudioEqTextMuted,
    bevelBrush = StudioEqBevelBrush,
    bevelBorderBrush = StudioEqBevelBorderBrush,
    primaryGlow = StudioEqPrimaryGlow,
    baseSurface = StudioEqBackground,
    elevatedSurface = StudioEqLightShadow,
    cardBrush = StudioEqCardBrush,
    cardBorderBrush = StudioEqBevelBorderBrush,
    cardElevation = 6.dp,
    sliderKnobHighlight = StudioEqKnobHighlight,
    specularBrush = StudioEqSpecularBrush,
    cardRadius = StudioEqCardRadius,
    iconBoxRadius = StudioEqIconBoxRadius,
    // Batch 47: TETAP Transparent — Studio Eq "low-contrast/subtle by design"
    // (lihat komentar Batch 43), bukan target "ultra realistic" kayak varian 3.
    shadowLightTint = Color.Transparent,
    shadowDarkTint = Color.Transparent
)

/** Pilihan varian aktif — persisted lewat `PrefsHelper.getAppThemeStyle` (String
 *  constants `APP_THEME_AMOLED_GLASS`/`APP_THEME_RADICAL_SKEUO`, nama TIDAK diubah
 *  biar data user lama valid), di-map ke enum ini di `MainActivity.kt`. Default
 *  `AMOLED_GLASS` ("Midnight Glass"). Batch 43: +`STUDIO_EQ` (varian ke-4). */
enum class AppThemeStyle { AMOLED_GLASS, RADICAL_SKEUO, SKEUOMORPHISM, STUDIO_EQ }

val LocalAppThemeStyle = compositionLocalOf { AppThemeStyle.AMOLED_GLASS }
val LocalSkeuTokens = compositionLocalOf { AmoledGlassSkeuTokens }

/** Background layar root (`MainActivity.kt` Surface) — vertical gradient
 *  Midnight-Blue -> nyaris-hitam. Glassmorphism butuh backdrop yang gak flat
 *  monoton di baliknya supaya translucency kartu di atasnya "kebaca" sebagai kaca
 *  (bukan cuma kartu solid dengan border) — sekaligus ini tempat hint Midnight-Blue
 *  paling kentara di seluruh app (splash + kanvas layar, dua-duanya). */
val ScreenBackgroundBrush: Brush = Brush.verticalGradient(
    listOf(Color(0xFF10173A), AmoledSurface, AmoledBlack)
)

/** Varian Aurora Glass — background sedikit lebih vivid, konsisten sama sheen &
 *  elevation yang lebih kuat di varian ini. */
val AuroraScreenBackgroundBrush: Brush = Brush.verticalGradient(
    listOf(Color(0xFF161F4C), RadicalBackground, AmoledBlack)
)

private val RadicalDarkColors = darkColorScheme(
    primary = RadicalAccent,
    onPrimary = Color(0xFF04070C),
    primaryContainer = Color(0xFF1E2C63),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = RadicalTextSecondary,
    onSecondary = Color(0xFF04070C),
    background = RadicalBackground,
    onBackground = RadicalTextPrimary,
    surface = RadicalSurface,
    onSurface = RadicalTextPrimary,
    surfaceVariant = RadicalSurfaceRaised,
    onSurfaceVariant = RadicalTextSecondary,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = RadicalEdgeHighlight
)

/** Batch 43: colorScheme M3 buat "Studio Equalizer" — `primary` = hijau neon lime
 *  asli user (`StudioEqAccent`), `onPrimary` gelap kehijauan (kontras cukup di atas
 *  hijau terang), `primaryContainer` olive-dark desaturated (bukan hijau terang
 *  penuh — container tetap "quiet", nyala penuh cuma dipegang `primaryGlow`/ring
 *  aktif di SkeuTokens, konsisten sama deskripsi user "lampu indikator"). */
private val StudioEqDarkColors = darkColorScheme(
    primary = StudioEqAccent,
    onPrimary = Color(0xFF0A1408),
    primaryContainer = Color(0xFF223A18),
    onPrimaryContainer = Color(0xFFDFFFDA),
    secondary = StudioEqTextSecondary,
    onSecondary = Color(0xFF0A1408),
    background = StudioEqBackground,
    onBackground = StudioEqTextPrimary,
    surface = StudioEqBackground,
    onSurface = StudioEqTextPrimary,
    surfaceVariant = StudioEqLightShadow,
    onSurfaceVariant = StudioEqTextSecondary,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = StudioEqLightShadow
)

private val DarkColors = darkColorScheme(
    primary = MidnightBlueAccent,
    onPrimary = Color(0xFF04050C),
    primaryContainer = Color(0xFF232C5C),
    onPrimaryContainer = Color(0xFFDBE0FF),
    secondary = Color(0xFFA9B0C4),
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

private val NeumorphismDarkColors = darkColorScheme(
    // Batch 46: primary sekarang Ruby (era Skeuomorphism lama pakai Platinum/
    // titanium-silver sebagai primary) — Ruby dicadangkan khusus state-aktif
    // sesuai rasional Platinum(netral)+Ruby(vivid) di komentar token di atas.
    primary = NeumoRuby,
    onPrimary = Color.White,
    primaryContainer = NeumoRubyDeep,
    onPrimaryContainer = Color(0xFFFFE1EC),
    secondary = NeumoTextSecondary,
    onSecondary = Color(0xFF15161A),
    background = NeumoBackground,
    onBackground = NeumoTextPrimary,
    surface = NeumoPanel,
    onSurface = NeumoTextPrimary,
    surfaceVariant = NeumoPanelRaised,
    onSurfaceVariant = NeumoTextSecondary,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = NeumoEdgeHighlight
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
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )
)

// iOS-style rounded — radius dinaikkan di semua step (dipakai otomatis oleh
// komponen Material3 default: AlertDialog, Button, OutlinedButton, TextButton, dst
// yang belum di-override shape manual di BoosterScreen.kt/OnboardingScreen.kt).
// Dipakai 2 varian glass (Midnight Glass, Aurora Glass).
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/** Batch 46: shape khusus varian Neumorphism (ganti dari Skeuomorphism sudut
 *  tegas Batch 39) — generous/rounded soft-UI, konsisten sama
 *  `NeumoCardRadius`/`NeumoIconBoxRadius` (22dp/15dp) di atas, supaya komponen
 *  Material3 default (AlertDialog/Button/dll) yang belum pakai shape manual JUGA
 *  otonom, gak ikut radius varian lain. */
private val NeumorphismShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(15.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** Batch 43: shape khusus Studio Equalizer — rounded generous konsisten sama
 *  `StudioEqCardRadius`/`StudioEqIconBoxRadius` (20dp/14dp) di atas, biar komponen
 *  Material3 default yang belum pakai shape manual JUGA otonom (gak numpang
 *  radius varian lain — sama alasan `SkeuomorphismShapes` Batch 39). */
private val StudioEqShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** WAJIB dark-mode -> CompositionLocal ini dipertahankan (dipakai
 *  SkeuomorphicComponents.kt) tapi NILAINYA SELALU `true`, tidak ada resolusi/override
 *  light. */
val LocalIsDarkTheme = compositionLocalOf { true }

@Composable
fun AudioEnhancerTheme(
    useDynamicColor: Boolean = false,
    themeStyle: AppThemeStyle = AppThemeStyle.AMOLED_GLASS,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Material You (wallpaper) MENANG kalau opt-in aktif — independen dari pilihan
    // Midnight/Aurora Glass/Neumorphism/Studio Equalizer.
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        themeStyle == AppThemeStyle.RADICAL_SKEUO -> RadicalDarkColors
        themeStyle == AppThemeStyle.SKEUOMORPHISM -> NeumorphismDarkColors
        themeStyle == AppThemeStyle.STUDIO_EQ -> StudioEqDarkColors
        else -> DarkColors
    }
    val skeuTokens = when (themeStyle) {
        AppThemeStyle.RADICAL_SKEUO -> RadicalSkeuoSkeuTokens
        AppThemeStyle.SKEUOMORPHISM -> NeumorphismSkeuTokens
        AppThemeStyle.STUDIO_EQ -> StudioEqSkeuTokens
        else -> AmoledGlassSkeuTokens
    }
    // Batch 39: shapes juga di-pilih per-varian (sebelumnya `AppShapes` statis buat
    // semua). Batch 46: varian ke-3 pakai `NeumorphismShapes` (rounded soft-UI,
    // ganti dari `SkeuomorphismShapes` sudut tegas). Batch 43: +Studio Equalizer
    // pakai `StudioEqShapes` (rounded generous neumorphism).
    val shapes = when (themeStyle) {
        AppThemeStyle.SKEUOMORPHISM -> NeumorphismShapes
        AppThemeStyle.STUDIO_EQ -> StudioEqShapes
        else -> AppShapes
    }
    CompositionLocalProvider(
        LocalIsDarkTheme provides true,
        LocalAppThemeStyle provides themeStyle,
        LocalSkeuTokens provides skeuTokens
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = shapes,
            content = content
        )
    }
}
