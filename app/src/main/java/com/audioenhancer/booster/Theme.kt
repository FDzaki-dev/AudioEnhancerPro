package com.audioenhancer.booster

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
// Varian ke-3 — "Skeuomorphism" (Batch 38-39) -> "Neumorphism" Platinum+Ruby
// (Batch 46) -> Batch 52: DIROMBAK TOTAL, user lapor versi Batch 46 "gak
// eksplisit ala kadarnya". Root cause: (1) `NeumoBevelBrush` 5-stop gradient
// bikin permukaan kartu SENDIRI sudah keliatan "berlapis" — bertentangan sama
// definisi neumorphism genuine (permukaan HARUS flat/1 warna, kedalaman
// murni dari sepasang shadow terarah di LUAR bentuk, bukan gradient DI DALAM
// bentuknya — gradient-di-dalam itu ciri skeuomorphism/glass, bukan neumorphism).
// (2) `NeumoSpecularBrush` (sheen glossy) = ciri glassmorphism, neumorphism
// matte total, 0 sheen. (3) kontras dual-shadow (`shadowLightTint`/
// `shadowDarkTint`, dipakai `SkeuDualDirectionalShadow`) sebelumnya terlalu
// tipis buat kebaca di layar kecil. Fix Batch 52: kartu jadi flat solid,
// sheen dihapus (Transparent), kontras shadow pair dinaikkan signifikan — ini
// yang bikin "eksplisit". Palet direset total ke "Deep Navy & Classic Brass"
// (spek eksak dari user, keluarga warna Tailwind Slate + 1 aksen brass),
// GANTI TOTAL dari Platinum+Ruby lama. Brass HANYA dipakai di elemen
// interaktif/state-aktif (glow, primary, ring aktif) — TIDAK disebar ke bevel/
// border seperti Platinum dulu (spec user: aksen brass maks ~10% area visual).
// Nama var `Neumo*` DIPERTAHANKAN (bukan `Navy*`/`Brass*`) supaya referensi
// existing di `NeumorphismSkeuTokens`/`NeumorphismDarkColors`/
// `NeumoScreenBackgroundBrush` (dipakai `MainActivity.kt`, PROTECTED asset)
// TIDAK perlu disentuh. Toggle "Skeuomorphism" di Settings & persistence key
// TIDAK diubah (sama kayak Batch 46), TIDAK mengubah 3 varian existing lain.
// ============================================================================

/** appBg — latar utama layar (60% area), Tailwind slate-900. */
val NeumoBackground = Color(0xFF0F172A)

/** appCard — base surface DAN fill kartu (FLAT, lihat `NeumoBevelBrush` di
 *  bawah — beda dari era Batch 46 yang gradient 5-stop). Tailwind slate-800. */
val NeumoPanel = Color(0xFF1E293B)

/** appBorder dipakai dobel: (a) garis 1px pembatas kartu, (b) surface "raised"
 *  netral (icon-box hover, `elevatedSurface`) — 1 langkah lebih terang dari
 *  `NeumoPanel`, konsisten sama panduan komposisi (appBorder = pembatas
 *  ANTAR komponen). Tailwind slate-700. */
val NeumoBorder = Color(0xFF334155)
val NeumoPanelRaised = NeumoBorder

/** Sumur/pressed-well — lebih gelap dari `NeumoBackground`, dipakai base
 *  dual-shadow gelap & elemen "tertekan masuk" (track slider/switch OFF). */
val NeumoPanelRecessed = Color(0xFF060B14)

val NeumoTextPrimary = Color(0xFFF8FAFC)   // txtPrimary, slate-50
val NeumoTextSecondary = Color(0xFF94A3B8) // txtSecondary, slate-400
val NeumoTextMuted = Color(0xFF64748B)     // slate-500, 1 step lebih redup dari secondary

// ============================================================================
// Batch 108 — user minta eksplisit "rombak total typography+shape 'Neumorphism'
// theme jadi ala Blade Runner tapi dengan aksen warna 'Aurora'". Scope SENGAJA
// dibatasi 3 sumbu (BUKAN full palette rewrite ala Batch 52): (1) shape — radius
// kartu/icon-box + `NeumorphismShapes` (M3 default shapes) turun ke near-flat/
// angular (lihat komentar `NeumoCardRadius` di bawah); (2) typography — sebelumnya
// GLOBAL 1 `Typography` (`AppTypography`) dipakai SEMUA 4 varian, sekarang
// per-varian (arsitektur baru, `NeumorphismTypography`, WAJIB diisi di
// `AudioEnhancerTheme()` seperti `shapes`/`colors` sudah per-varian sejak Batch 39)
// — TIDAK embed font baru (keputusan sadar project ini, font sistem Android tetap
// dipakai), efek "terminal/HUD Blade Runner" dicapai murni dari letterSpacing/
// fontWeight (tracked-out ala title card film, bukan monospace asli); (3) aksen
// warna — Brass (Batch 52) diganti Aurora (`NeumoAurora`, SAMA PERSIS
// `RadicalAccent` varian 2 "Aurora Glass" — literal reuse, bukan hue baru hasil
// tebakan) — **SUPERSEDED Batch 109**: aksen Aurora diganti lagi jadi "Misty Pine
// Forest" (`NeumoMistyPine`/`NeumoMistyPineDeep`, instruksi eksplisit user),
// lihat blok komentar Batch 109 di dekat deklarasi var untuk detail lengkap; shape
// & typography Blade Runner di poin (1)+(2) TIDAK ikut disentuh Batch 109 (scope
// eksplisit hanya "aksen warna"). TIDAK disentuh: base palette Deep Navy (`NeumoBackground`/`NeumoPanel`/
// `NeumoBorder`/dual-shadow tint) — scope user eksplisit "typography+shape"+aksen,
// BUKAN "background"/"base palette", identitas neumorphism Deep Navy dipertahankan
// utuh. `SkeuomorphicComponents.kt` (dipakai 3 varian lain) 0 disentuh — regresi
// risk ke Midnight Glass/Aurora Glass/Studio Eq = NOL. Nama var `Neumo*` sengaja
// TETAP prefix generik (bukan `Blade*`/`Aurora*` literal) — persistence key +
// referensi internal file ini tidak berubah/pecah, konsisten precedent Batch 52.
// ============================================================================

/** Aurora — DIGANTI TOTAL Batch 109 (instruksi eksplisit user: aksen ->
 *  "Misty Pine Forest"/Hutan Pinus Berkabut) → `NeumoMistyPine`/
 *  `NeumoMistyPineDeep`. Var Aurora lama DIHAPUS (bukan sekadar redefinisi
 *  nilai di bawah nama sama) — konsisten precedent rename Batch 108
 *  (Brass->Aurora): nama var yang secara harfiah nama HUE spesifik WAJIB
 *  ikut berubah kalau isinya berubah hue, supaya gak menyesatkan sesi
 *  berikutnya (baca nama var = asumsi warna). Grep dikonfirmasi 0 referensi
 *  eksternal (`NeumoAurora`/`NeumoAuroraDeep` cuma dipakai internal file
 *  ini) — rename AMAN, 0 file lain kena dampak selain 2 string desc
 *  (`theme_style_skeuo_desc` ID+EN, diupdate terpisah). Hue baru: sage/pine
 *  hijau desaturasi dengan undertone abu-kebiruan (kesan "berkabut", bukan
 *  hijau forest saturasi tinggi) — brightness level SENGAJA disamakan
 *  dengan Aurora lama (persepsi luminance ~153/255 vs ~152/255) supaya
 *  kontras `onPrimary`=`NeumoBackground` (teks gelap di atas tombol/switch
 *  aktif) TETAP aman tanpa perlu re-tune WCAG dari nol. */
val NeumoMistyPine = Color(0xFF80A891)
/** Turunan gelap — formula lerp SAMA PERSIS precedent Aurora (0.45f ke
 *  `NeumoPanelRecessed`), cuma base hue yang beda. */
val NeumoMistyPineDeep: Color = lerp(NeumoMistyPine, NeumoPanelRecessed, 0.45f)

/** Tint dual-shadow terarah (`SkeuDualDirectionalShadow`, SkeuomorphicComponents.kt)
 *  — Batch 110 (instruksi eksplisit user, "Misty Pine Forest kalah dominan dari
 *  warna yang gak diminta"): SEBELUMNYA sehue navy independen (`0xFF4A6690`,
 *  keputusan Batch 52/108 "brass/aurora dijaga cuma buat state-aktif, bukan
 *  ambient shadow"). Root cause komplain user: `SkeuDualDirectionalShadow` re-draw
 *  outline shape ini BERULANG (loop `steps downTo 1`, `SkeuTintedCard` steps+1dp
 *  dibanding `SkeuCard`) di SETIAP kartu/banner dengan spread makin lebar — visual
 *  effect "stack kartu berlapis" yang KELIHATAN di 2 banner (Service berjalan/
 *  Output audio berubah) MAYORITAS warnanya justru dari tint SEHUE NAVY ini
 *  (bukan `NeumoMistyPine`), jadi kebaca "masih biru" walau accent asli (tombol
 *  power, tab, teks status) sudah pine — literally warna paling dominan di layar
 *  karena diulang di SETIAP kartu. Fix: tint highlight sekarang DITURUNKAN dari
 *  `NeumoMistyPine` (bukan hex navy independen), shadow gelap dapat tint pine
 *  tipis juga (15% campur) — base Deep Navy (`NeumoBackground`/`NeumoPanel`/
 *  `NeumoBorder`, dipakai base surface/border kartu ITU SENDIRI, BUKAN shadow-nya)
 *  TETAP TIDAK disentuh (scope user "aksen warna", bukan base palette). Alpha
 *  0.72f/0.97f TIDAK diubah (kontras depth Batch 56 dipertahankan, cuma hue yang
 *  ganti) — behavior/parameter lain `SkeuDualDirectionalShadow` (steps, spread
 *  multiplier 1.6f, falloff) 0 disentuh. */
val NeumoEdgeHighlight: Color = lerp(NeumoMistyPine, Color.White, 0.30f).copy(alpha = 0.72f)
val NeumoEdgeShadow: Color = lerp(NeumoPanelRecessed, NeumoMistyPine, 0.15f).copy(alpha = 0.97f)

/** FLAT — inti fix "eksplisit" Batch 52. Neumorphism genuine: permukaan 1
 *  warna solid, kedalaman 100% dari `SkeuDualDirectionalShadow` (native
 *  `Modifier.shadow` ambientColor/spotColor, bukan gradient internal). Tetap
 *  bertipe `Brush` (`SolidColor`) biar `SkeuTokens.cardBrush`/`bevelBrush`
 *  (tipe `Brush`) tidak perlu diubah. */
val NeumoBevelBrush: Brush = SolidColor(NeumoPanel)

/** Border tipis 1px SEHUE `appBorder`, flat (bukan gradient highlight->shadow
 *  ala Batch 46) — neumorphism genuine idealnya minim/tanpa border sama
 *  sekali (kedalaman dari shadow, bukan outline), tapi border sangat tipis
 *  tetap dipertahankan buat definisi tepi di layar kecil (readability). */
val NeumoBevelBorderBrush: Brush = SolidColor(NeumoBorder.copy(alpha = 0.45f))

/** DIHAPUS TOTAL (Transparent) — sheen glossy adalah ciri glassmorphism/
 *  skeuomorphism, BUKAN neumorphism (neumorphism = matte total, 0 kilap).
 *  `SkeuCard`/`SkeuTintedCard` tetap manggil `.background(specularBrush)` apa
 *  adanya (SkeuomorphicComponents.kt tidak disentuh) — Transparent = layer itu
 *  0 efek visual, cara paling aman hapus sheen tanpa ubah komponen bersama. */
val NeumoSpecularBrush: Brush = SolidColor(Color.Transparent)

/** Glow aurora — dipakai state-aktif/primary saja (aturan komposisi SAMA seperti
 *  brass dulu: maks ~10% area, JANGAN teks paragraf panjang — cek semua pemakaian
 *  tetap di primary/onPrimaryContainer/glow/ring). */
val NeumoPrimaryGlow = NeumoMistyPine.copy(alpha = 0.36f)

/** Knob slider — highlight netral navy-terang (BUKAN brass — brass cuma buat
 *  ring accent aktif di style komponennya, dibawa terpisah lewat
 *  `accentColor`/`primary`, bukan warna isi bead). */
val NeumoKnobHighlight: Color = lerp(NeumoPanelRaised, Color.White, 0.14f)

/** Radius kartu/icon-box — Batch 108: DIROMBAK TOTAL dari soft-UI rounded (22dp/
 *  15dp, Batch 46) ke near-flat/angular ala panel HUD Blade Runner (Deckard's
 *  Voight-Kampff console, Tyrell Corp terminal — sudut nyaris tegas, BUKAN sudut
 *  100% lancip: tetap 2-3dp residual biar anti-aliasing tepi gak keras/pecah di
 *  layar kecil, prinsip yang sama dengan kenapa `NeumoBevelBorderBrush` tetap ada
 *  1px border tipis di neumorphism "genuine" — bukan murni estetika, itu
 *  readability tepi). Dual-shadow depth system (`NeumoEdgeHighlight`/
 *  `NeumoEdgeShadow`, `SkeuDualDirectionalShadow`) TIDAK disentuh — technique
 *  neumorphism-nya (bayangan sepasang terarah) TETAP, cuma bentuk siluet yang
 *  dibayangi sekarang tegas bukan bulat. 0 perubahan di `SkeuomorphicComponents.kt`
 *  — shape masih `RoundedCornerShape` (bukan diganti `CutCornerShape`), radius
 *  yang mendekati nol sudah cukup baca sebagai "sharp" tanpa perlu shape-type baru
 *  di `SkeuTokens` (lebih rendah risiko regresi ke 3 varian lain yang share
 *  komponen sama). */
val NeumoCardRadius = 4.dp
val NeumoIconBoxRadius = 3.dp

/** Background layar — FLAT solid `appBg` (bukan gradient vertical era Batch
 *  46). Alasan sama dengan `NeumoBevelBrush`: backdrop bergradasi bikin tint
 *  dual-shadow di atasnya jadi ketebak salah warna di sebagian area (shadow
 *  di-tune buat 1 warna dasar flat, `NeumoBackground`) — flatten backdrop =
 *  bagian dari fix "eksplisit", bukan sekadar preferensi gaya. */
val NeumoScreenBackgroundBrush: Brush = SolidColor(NeumoBackground)

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

/** Varian 5: "Serene M3" — Batch 111, genuine Material 3 (BUKAN keluarga
 *  glass/skeuo/neumorphism 4 varian di atas). Base surface FLAT M3 tonal
 *  (bukan gradient/bevel/dual-shadow siapapun), zero baseline dishare —
 *  warna/shape/tipografi 100% independen dari 4 varian lain. Aksen "calm":
 *  sage hijau-abu desaturasi + lavender-abu sebagai secondary, brightness
 *  medium (bukan neon/vivid ala Aurora/Studio Eq), dipilih spesifik biar
 *  "memanjakan mata" sesuai request user — bukan sekadar palet M3 default. */
val SereneBackground = Color(0xFF1B1F1C)
val SereneSurface = Color(0xFF20241F)
val SereneSurfaceRaised = Color(0xFF272C26)
val SereneAccent = Color(0xFF9CB89F) // sage, primary
val SereneAccentDeep = Color(0xFF3C4A3D) // container gelap sage
val SereneSecondary = Color(0xFFB7AFC9) // lavender-abu, secondary
val SereneSecondaryDeep = Color(0xFF3E3A4A)
val SereneTextPrimary = Color(0xFFE7ECE6)
val SereneTextSecondary = Color(0xFFAEB6AC)
val SereneTextMuted = Color(0xFF838C81)
val SereneOutline = Color(0xFF3A3F38)
val SereneBorderBrush: Brush = SolidColor(Color(0xFF3A3F38))

/** Card brush flat-tonal (BUKAN glass frosted, BUKAN bevel neumorphism) — 1 warna
 *  solid `SereneSurfaceRaised` dibungkus `Brush` cuma buat kompatibel tipe field
 *  `SkeuTokens.cardBrush` (Brush), 0 gradient/multi-stop seperti 4 varian lain. */
val SereneCardBrush: Brush = SolidColor(SereneSurfaceRaised)
val SereneSpecularBrush: Brush = SolidColor(Color.Transparent) // M3 flat: 0 sheen/kilau kaca

/** Radius unik — organic-asymmetric (bukan rounded-uniform iOS-glass, bukan
 *  angular Blade Runner, bukan generous-soft neumorphism). Nilai sengaja
 *  di antara 2 ekstrem itu, dikombinasi shape M3 `CutCornerShape` sebagian
 *  biar beneran "unique" — 1 sudut terpotong di beberapa komponen besar
 *  (lihat `SereneShapes`), bukan cuma rounded-corner biasa berulang. */
val SereneCardRadius = 18.dp
val SereneIconBoxRadius = 12.dp

/** Batch 112: shape ASLI kartu Serene M3 (cut-corner asimetris), didefinisikan
 *  di sini (top-level, public) supaya bisa dipakai DI 2 TEMPAT — `SereneShapes.large`
 *  (buat komponen Material3 default) DAN `SereneSkeuTokens.cardShape` (buat
 *  `SkeuCard`/`SkeuTintedCard`, SkeuomorphicComponents.kt) — root cause kartu toggle
 *  screenshot user masih "rounded biasa" adalah `SkeuCard` SEBELUMNYA 0 pernah baca
 *  shape asli varian manapun (selalu bikin `RoundedCornerShape` sendiri dari radius
 *  Dp), jadi cut-corner ini 0% ke-render di kartu manapun. 1 sumber kebenaran shape,
 *  bukan didefinisikan dobel beda tempat. */
val SereneCardShape: CornerBasedShape = CutCornerShape(
    topEnd = 20.dp,
    topStart = SereneCardRadius,
    bottomStart = SereneCardRadius,
    bottomEnd = SereneCardRadius
)

val SereneScreenBackgroundBrush: Brush = Brush.verticalGradient(
    listOf(SereneSurface, SereneBackground, Color(0xFF14170F))
)

/** Token yang beda antar 5 varian desain (Batch 111: +1, sebelumnya 4), dibaca
 *  lewat `LocalSkeuTokens.current` (SkeuomorphicComponents.kt) — 1 kode komponen,
 *  5 varian, TANPA duplikasi. Field baru WAJIB diisi di SEMUA instance di bawah
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
    val shadowDarkTint: Color,
    // Batch 112: `SkeuCard`/`SkeuTintedCard` (SkeuomorphicComponents.kt) SEBELUMNYA
    // selalu bikin `RoundedCornerShape(cardRadius)` sendiri, TIDAK PERNAH baca shape
    // asli per-varian (`AppShapes`/`NeumorphismShapes`/`StudioEqShapes`/
    // `SereneShapes` di bawah) — akibatnya shape "unique" Serene M3 (cut-corner)
    // 0% kepakai di kartu manapun, cuma dipakai komponen Material3 DEFAULT yang
    // jarang muncul di layar ini (root cause komplain user screenshot Batch 111:
    // "kartu Serene M3 masih rounded biasa"). Field baru INI = shape ASLI tiap
    // varian (bukan cuma radius Dp), dibaca `SkeuCard`/`SkeuTintedCard` LANGSUNG
    // — 4 varian lama diisi `RoundedCornerShape(cardRadius)` SAMA PERSIS dgn
    // behavior lama (0 perubahan visual), Serene M3 diisi `SereneShapes.large`
    // (cut-corner asli) supaya AKHIRNYA kebaca di kartu.
    val cardShape: Shape
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
    shadowDarkTint = Color.Transparent,
    // Batch 112: `RoundedCornerShape(SkeuCardRadius)` — SAMA PERSIS shape yang
    // sebelumnya dibikin inline di `SkeuCard`, 0 perubahan visual.
    cardShape = RoundedCornerShape(SkeuCardRadius)
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
    shadowDarkTint = Color.Transparent,
    cardShape = RoundedCornerShape(SkeuCardRadius)
)

/** Varian 3: "Neumorphism" — Batch 52: palet Deep Navy & Classic Brass, kartu
 *  FLAT + dual-shadow kontras tinggi (lihat blok komentar token di atas untuk
 *  rationale lengkap fix "eksplisit"). `cardElevation` 13dp (Batch 56: naik
 *  dari 10dp — "push lebih dalam lagi", tertinggi dari 4 varian — depth dari
 *  shadow, bukan dari gradient permukaan lagi). `shadowLightTint`/
 *  `shadowDarkTint` sekarang `NeumoEdgeHighlight`/`NeumoEdgeShadow` (bukan
 *  hardcode `NeumoPlatinum`/`NeumoPanelRecessed` inline lagi) — 1 sumber
 *  kebenaran token warna, dipakai juga oleh `NeumoBevelBorderBrush`/border. */
val NeumorphismSkeuTokens = SkeuTokens(
    mutedText = NeumoTextMuted,
    bevelBrush = NeumoBevelBrush,
    bevelBorderBrush = NeumoBevelBorderBrush,
    primaryGlow = NeumoPrimaryGlow,
    baseSurface = NeumoPanel,
    elevatedSurface = NeumoPanelRaised,
    cardBrush = NeumoBevelBrush,
    cardBorderBrush = NeumoBevelBorderBrush,
    cardElevation = 13.dp,
    sliderKnobHighlight = NeumoKnobHighlight,
    specularBrush = NeumoSpecularBrush,
    cardRadius = NeumoCardRadius,
    iconBoxRadius = NeumoIconBoxRadius,
    shadowLightTint = NeumoEdgeHighlight,
    shadowDarkTint = NeumoEdgeShadow,
    cardShape = RoundedCornerShape(NeumoCardRadius)
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
    shadowDarkTint = Color.Transparent,
    cardShape = RoundedCornerShape(StudioEqCardRadius)
)

/** Varian 5: "Serene M3" (Batch 111) — flat tonal Material 3, 0 bevel/dual-shadow/
 *  glass sheen (`shadowLightTint`/`shadowDarkTint`/`specularBrush` semua Transparent
 *  — genuine M3 rely ke elevation tonal, bukan custom shadow layer manapun).
 *  `cardElevation` rendah (2dp, terendah dari 5 varian) — M3 real biasanya subtle,
 *  bukan deep-press look. */
val SereneSkeuTokens = SkeuTokens(
    mutedText = SereneTextMuted,
    bevelBrush = SereneCardBrush,
    bevelBorderBrush = SereneBorderBrush,
    primaryGlow = SereneAccent,
    baseSurface = SereneSurface,
    elevatedSurface = SereneSurfaceRaised,
    cardBrush = SereneCardBrush,
    cardBorderBrush = SereneBorderBrush,
    cardElevation = 2.dp,
    sliderKnobHighlight = SereneAccent,
    specularBrush = SereneSpecularBrush,
    cardRadius = SereneCardRadius,
    iconBoxRadius = SereneIconBoxRadius,
    shadowLightTint = Color.Transparent,
    shadowDarkTint = Color.Transparent,
    cardShape = SereneCardShape
)

/** Pilihan varian aktif — persisted lewat `PrefsHelper.getAppThemeStyle` (String
 *  constants `APP_THEME_AMOLED_GLASS`/`APP_THEME_RADICAL_SKEUO`, nama TIDAK diubah
 *  biar data user lama valid), di-map ke enum ini di `MainActivity.kt`. Default
 *  `AMOLED_GLASS` ("Midnight Glass"). Batch 43: +`STUDIO_EQ` (varian ke-4).
 *  Batch 111: +`SERENE_M3` (varian ke-5, genuine Material 3). */
enum class AppThemeStyle { AMOLED_GLASS, RADICAL_SKEUO, SKEUOMORPHISM, STUDIO_EQ, SERENE_M3 }

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
    // Batch 109: primary sekarang Misty Pine (ganti Aurora Batch 108). onPrimary
    // TETAP `NeumoBackground` (dark navy) — kontras WCAG masih aman, Misty Pine
    // (0x80A891) mid-brightness mirip Aurora lama (lihat komentar `NeumoMistyPine`
    // di atas), pola sama seperti `RadicalDarkColors` (`onPrimary` dark di atas
    // `RadicalAccent`, hue keluarga sama).
    primary = NeumoMistyPine,
    onPrimary = NeumoBackground,
    primaryContainer = NeumoMistyPineDeep,
    onPrimaryContainer = NeumoTextPrimary,
    secondary = NeumoTextSecondary,
    onSecondary = NeumoBackground,
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
    outline = NeumoBorder
)

/** Batch 111: colorScheme M3 buat "Serene M3" (varian ke-5) — `primary` sage
 *  (`SereneAccent`), `secondary` lavender-abu (`SereneSecondary`), keduanya calm/
 *  desaturasi (bukan vivid ala Aurora/neon Studio Eq) sesuai request eksplisit
 *  user "aksen warna calm yang memanjakan mata". `onPrimary`/`onSecondary` gelap
 *  (kontras WCAG aman di atas sage/lavender mid-brightness), 0 warna dipinjam
 *  dari 4 `darkColorScheme` lain di atas. */
private val SereneDarkColors = darkColorScheme(
    primary = SereneAccent,
    onPrimary = Color(0xFF12190F),
    primaryContainer = SereneAccentDeep,
    onPrimaryContainer = Color(0xFFDCEEDC),
    secondary = SereneSecondary,
    onSecondary = Color(0xFF19141F),
    secondaryContainer = SereneSecondaryDeep,
    onSecondaryContainer = Color(0xFFE9E3F2),
    background = SereneBackground,
    onBackground = SereneTextPrimary,
    surface = SereneSurface,
    onSurface = SereneTextPrimary,
    surfaceVariant = SereneSurfaceRaised,
    onSurfaceVariant = SereneTextSecondary,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = SereneOutline
)

private val AppTypography = Typography(
    // Batch 90 (roadmap.md Fase 7 Fase 2 opsi B, "Tipografi iOS"): dicek dulu
    // ke spek resmi Apple HIG (bukan tebak dari memori) — Large Title asli
    // iOS = 34pt, line-height ~41pt (rasio ~1.2x, sama seperti dipakai proyek
    // ini sebelumnya di token ini). Weight SwiftUI default Font `.largeTitle`
    // itu sendiri sebenarnya Regular, TAPI large-title yang benar-benar
    // terlihat di UINavigationBar stok Apple (Settings/Mail/Messages — pola
    // yang jadi acuan visual proyek ini, lihat "SkeuGroupDivider" ala
    // Settings.app di Batch 88) SELALU tampil Bold — itu bar chrome bawaan,
    // beda dari Font style abstrak. Dipilih Bold (bukan lagi ExtraBold 28sp
    // lama, ad-hoc belum pernah dicocokkan ke spek manapun) — masih cukup
    // tegas buat identitas brand "Boomly", tapi sekarang berbasis rasio iOS
    // asli, bukan angka sembarang. `letterSpacing` DINAIKKAN (diketatkan
    // dikit dari -0.3 ke -0.4) mengikuti prinsip umum "makin besar ukuran,
    // makin rapat tracking" — TIDAK diklaim sebagai angka tracking SF Pro
    // resmi (font sistem Android beda metrik total dari SF Pro, proyek ini
    // sengaja TIDAK embed font baru, lihat roadmap.md), cuma pendekatan
    // realistis sesuai instruksi awal user. Dipakai 2 tempat:
    // `BoosterScreen.kt` (header "Boomly", target utama task ini) DAN
    // `SettingsScreen.kt` (sebelum batch ini SAMA token, sekarang DIPISAH ke
    // `titleMedium` di file itu — 34pt kegedean buat inline title di sebelah
    // tombol back, lihat komentar di `SettingsScreen.kt`).
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp
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

/** Batch 108: typography KHUSUS varian Neumorphism ("Blade Runner" ala, lihat blok
 *  komentar Batch 108 di section Neumorphism). PERTAMA KALI typography jadi
 *  per-varian — sebelumnya `AppTypography` di atas dipakai statis SEMUA 4 varian
 *  (`MaterialTheme(typography = ...)` di `AudioEnhancerTheme()` cuma 1 pilihan).
 *  0 font baru di-embed (keputusan sadar project, font sistem Android tetap
 *  dipakai) — efek "terminal/HUD film title-card" dicapai murni dari
 *  `letterSpacing` POSITIF/tracked-out (kebalikan `AppTypography` yang malah
 *  NEGATIF/rapat di headline) + weight lebih berat rata-rata. `bodyMedium`
 *  (dipakai valueLabel slider Bass/Virtualizer/dll, mis. "450") sengaja paling
 *  tracked di antara body styles — kesan "digital readout angka presisi", bukan
 *  teks paragraf biasa. */
private val NeumorphismTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 1.2.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.8.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.6.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.4.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.8.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp
    )
)

/** Batch 111: typography KHUSUS varian Serene M3 — zero baseline dishare dengan
 *  `AppTypography` (2 varian glass) ATAU `NeumorphismTypography` (Blade Runner).
 *  Filosofi kebalikan keduanya: `AppTypography` tracking NEGATIF di headline,
 *  `NeumorphismTypography` tracking POSITIF kuat rata-rata weight Bold/Black —
 *  Serene M3 pakai tracking POSITIF tapi HALUS (0.1-0.3sp, bukan 0.6-1.2sp) +
 *  weight lebih ringan (Medium/SemiBold dominan, headlineMedium SEMIBOLD bukan
 *  Bold/Black manapun) — kesan tenang/lapang cocok "calm", skala ukuran juga beda
 *  sendiri (headlineMedium 30sp, bukan 34sp iOS ATAU 32sp Blade Runner). */
private val SereneTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        letterSpacing = 0.1.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.3.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
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
 *  otonom, gak ikut radius varian lain.
 *  Batch 108: DIROMBAK TOTAL lagi — ganti dari rounded generous ke near-flat/
 *  angular (konsisten `NeumoCardRadius`/`NeumoIconBoxRadius` yang sekarang 4dp/
 *  3dp, lihat komentar lengkap di sana), bagian dari rombak "Blade Runner". */
private val NeumorphismShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp)
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

/** Batch 111: shape khusus Serene M3 (varian ke-5) — "unique underrated" sesuai
 *  request user, BUKAN rounded-uniform (2 glass + Studio Eq) ATAU angular seragam
 *  (Blade Runner). `large`/`extraLarge` (dipakai kartu besar/dialog) pakai
 *  `CutCornerShape` 1-sudut-terpotong (top-end saja) DIKOMBINASI radius di 3 sudut
 *  lain — organic-asymmetric genuinely beda dari 4 shape lain di file ini, tapi
 *  tetap "M3-safe" (Material3 native `CornerBasedShape`, bukan custom Path/hack).
 *  `extraSmall`/`small`/`medium` (chip/icon-box/kartu kecil) TETAP simetris rounded
 *  biar komponen kecil gak "ribut" — asimetri cuma di elemen besar yang punya
 *  ruang visual buat itu, konsisten prinsip iOS-glass Batch 37 (radius besar =
 *  fokus mata utama). Konsisten `SereneCardRadius`/`SereneIconBoxRadius` (18dp/12dp)
 *  di atas. Batch 112: `large` sekarang REUSE `SereneCardShape` (bukan literal
 *  terpisah lagi) — 1 sumber kebenaran shape, dipakai bareng `SereneSkeuTokens.
 *  cardShape` (`SkeuCard`/`SkeuTintedCard`) supaya kartu di layar BENERAN kebaca
 *  cut-corner, bukan cuma komponen Material3 default yang jarang tampil. */
private val SereneShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(SereneIconBoxRadius),
    large = SereneCardShape,
    extraLarge = CutCornerShape(topEnd = 26.dp, topStart = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
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
    // Midnight/Aurora Glass/Neumorphism/Studio Equalizer/Serene M3.
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        themeStyle == AppThemeStyle.RADICAL_SKEUO -> RadicalDarkColors
        themeStyle == AppThemeStyle.SKEUOMORPHISM -> NeumorphismDarkColors
        themeStyle == AppThemeStyle.STUDIO_EQ -> StudioEqDarkColors
        themeStyle == AppThemeStyle.SERENE_M3 -> SereneDarkColors
        else -> DarkColors
    }
    val skeuTokens = when (themeStyle) {
        AppThemeStyle.RADICAL_SKEUO -> RadicalSkeuoSkeuTokens
        AppThemeStyle.SKEUOMORPHISM -> NeumorphismSkeuTokens
        AppThemeStyle.STUDIO_EQ -> StudioEqSkeuTokens
        AppThemeStyle.SERENE_M3 -> SereneSkeuTokens
        else -> AmoledGlassSkeuTokens
    }
    // Batch 39: shapes juga di-pilih per-varian (sebelumnya `AppShapes` statis buat
    // semua). Batch 46: varian ke-3 pakai `NeumorphismShapes` (rounded soft-UI,
    // ganti dari `SkeuomorphismShapes` sudut tegas). Batch 43: +Studio Equalizer
    // pakai `StudioEqShapes` (rounded generous neumorphism). Batch 108: varian
    // ke-3 sekarang `NeumorphismShapes` near-flat/angular (Blade Runner). Batch 111:
    // +Serene M3 pakai `SereneShapes` (organic-asymmetric, cut-corner di elemen besar).
    val shapes = when (themeStyle) {
        AppThemeStyle.SKEUOMORPHISM -> NeumorphismShapes
        AppThemeStyle.STUDIO_EQ -> StudioEqShapes
        AppThemeStyle.SERENE_M3 -> SereneShapes
        else -> AppShapes
    }
    // Batch 108: typography PERTAMA KALI jadi per-varian (sebelumnya `AppTypography`
    // statis buat semua 4, sama seperti `shapes` sebelum Batch 39/43) — cuma varian
    // ke-3 (Neumorphism/"Blade Runner") yang beda, 3 varian lain TETAP `AppTypography`
    // (0 perubahan visual buat Midnight Glass/Aurora Glass/Studio Equalizer). Batch
    // 111: +Serene M3 pakai `SereneTypography` (0 baseline dishare dgn keduanya).
    val typography = when (themeStyle) {
        AppThemeStyle.SKEUOMORPHISM -> NeumorphismTypography
        AppThemeStyle.SERENE_M3 -> SereneTypography
        else -> AppTypography
    }
    CompositionLocalProvider(
        LocalIsDarkTheme provides true,
        LocalAppThemeStyle provides themeStyle,
        LocalSkeuTokens provides skeuTokens
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
