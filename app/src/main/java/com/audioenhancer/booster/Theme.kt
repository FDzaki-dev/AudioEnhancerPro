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
// BATCH 44 — "Skeuomorphism 2.0 (Hyper-Realism UI)": upgrade varian ke-3
// (Batch 38/39), diminta user eksplisit "otonom & gak pakai baseline theme
// lain" — SEMUA token baru di bawah ini DEDICATED buat Skeuomorphism doang
// (prefix `Skeuo*`, NOL reuse dari `Glass*`/`Radical*`), sama persis prinsip
// yang sudah dipegang sejak Batch 39 (cardRadius/iconBoxRadius otonom).
// 2 varian glass (Midnight/Aurora) di bawah SAMA SEKALI TIDAK disentuh —
// field baru di `SkeuTokens` SEMUA punya default value yang identik ke
// PERILAKU LAMA (bukan matikan fitur, tapi angka/warna yang PERSIS sama
// dengan hardcode sebelumnya), jadi 2 blok instance existing (`AmoledGlassSkeuTokens`/
// `RadicalSkeuoSkeuTokens`) TIDAK PERLU diedit sama sekali — nol resiko
// regresi ke variant yang sudah kerja. Ini deviasi SADAR dari konvensi lama
// "field baru wajib diisi eksplisit di semua instance" (catatan `SkeuTokens`
// di bawah) — dipilih justru demi keamanan yang LEBIH TINGGI (mustahil salah
// isi/kelupaan di 2 instance lama kalau gak disentuh sama sekali).
//
// Apa yang baru (hyper-realism cues, semua teknik native Compose yang SUDAH
// dipakai di file ini sebelumnya — bukan API baru yang belum tervalidasi):
// 1. Bevel/specular brush Skeuomorphism naik dari 4-stop ke gradasi lebih
//    kaya + 1 streak highlight anisotropic (khas brushed-metal difoto kena
//    cahaya), bukan cuma blend rata.
// 2. Rivet/screw accent — 4 titik radial-gradient kecil di pojok kartu &
//    tombol power (`SkeuoRivetBrush`, teknik `drawBehind`+`drawCircle` PERSIS
//    sama seperti `skeuGlow` yang sudah ada, cuma radius kecil + posisi
//    pojok, bukan API baru).
// 3. Knurled dial — slider knob dapat tekstur groove melingkar (`SkeuoKnurlColor`,
//    `drawCircle(style = Stroke(...))`, API `Stroke` standar Compose sejak 1.0).
// 4. Power button & kartu Skeuomorphism dapat elevation/glow-spread SENDIRI
//    (`buttonRestElevation`/`buttonGlowSpread`, default token = angka
//    hardcode LAMA persis `6.dp`/`14.dp` biar 2 varian glass identik ke
//    sebelumnya) — Skeuomorphism dibikin lebih dalam/menonjol (bevel-shadow
//    lebih ekstrem, khas hyper-realism), TANPA numpang angka shared.
// SEMUA teknik di atas cuma komposisi Brush/drawBehind/drawCircle/shadow()
// yang SUDAH ada contoh kerja di file ini — dipilih SENGAJA supaya "aman
// tanpa compiler buat verifikasi" (sandbox ini gak punya Gradle/Android
// runtime), bukan Modifier.blur()/RenderEffect atau API belum-pernah-dipakai
// lain yang gak bisa dipastikan sintaksnya tanpa compile beneran.
// Detail lengkap: `CHANGELOG.md` v1.82.0.
// ============================================================================

// ============================================================================
// BATCH 38 — tambahan (BUKAN rewrite ulang Batch 37): varian tema ke-3
// "Skeuomorphism" diminta user eksplisit ("theme custom Skeuomorphism dark mode
// yang asli, gak kurang gak lebih") — toggle baru di Settings, SEJAJAR toggle Aurora
// Glass (bukan sub-opsinya), TIDAK mengubah/menghapus 2 varian glass Batch 37.
// Bahasa desain beda total dari glass: panel gunmetal/charcoal netral (bukan biru),
// bevel raised/recessed extrusion kuat, aksen metalik hangat (tembaga #C98A4C, bukan
// biru dingin). Lihat blok token `SkeuoXxx` & `SkeuomorphismSkeuTokens`/
// `SkeuomorphismDarkColors` di bawah. Detail: `CHANGELOG.md` v1.77.0.
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
// Varian ke-3 ("Skeuomorphism", Batch 38, warna direvisi Batch 39) — diminta user
// eksplisit di luar 2 varian glass di atas: "theme custom Skeuomorphism dark mode
// yang asli". Bahasa desain BEDA TOTAL, bukan sub-varian glass: panel gunmetal/
// charcoal netral (BUKAN biru-tint), bevel raised/recessed tegas dengan shadow
// lebih dalam (ekstrusi fisik nyata, bukan sheen kaca lembut), aksen metalik
// titanium+silver (Batch 39 — sebelumnya tembaga/perunggu di Batch 38, diganti atas
// permintaan user; kontras sengaja dari accent biru dingin 2 varian lain TETAP
// dipertahankan lewat saturasi netral-cool, bukan lewat hue hangat lagi). Dipilih
// via toggle "Skeuomorphism" di Settings (sejajar toggle Aurora Glass,
// BoosterScreen.kt), TIDAK mengubah/menghapus 2 varian existing.
// ============================================================================

val SkeuoBackground = Color(0xFF19191C)
val SkeuoPanel = Color(0xFF232326)
val SkeuoPanelRaised = Color(0xFF2E2E33)
val SkeuoPanelRecessed = Color(0xFF131315)

val SkeuoEdgeHighlight = Color.White.copy(alpha = 0.12f)
val SkeuoEdgeShadow = Color.Black.copy(alpha = 0.78f)

val SkeuoTextPrimary = Color(0xFFF1F1EF)
val SkeuoTextSecondary = Color(0xFFBBBBC0)
val SkeuoTextMuted = Color(0xFF8B8B90)

/** Aksen titanium+silver metalik (Batch 39, ganti dari tembaga/perunggu Batch 38) —
 *  cool neutral-grey dengan sedikit undertone biru-baja, meniru permukaan titanium
 *  brushed asli (BUKAN pure grey netral/abu-abu polos — ada hint kebiruan tipis
 *  yang khas logam titanium teroksidasi). Tetap kontras dari `MidnightBlueAccent`/
 *  `RadicalAccent` (biru SATURATED) karena ini desaturated/metalik, bukan pure hue. */
val SkeuoAccent = Color(0xFFAEB4BF)

/** Nada titanium lebih gelap — dipakai buat variasi highlight/shadow metalik di
 *  komponen yang butuh 2 stop metalik (bukan cuma 1 warna flat), meniru refleksi
 *  brushed-metal yang selalu punya gradasi terang-gelap di permukaannya. */
val SkeuoAccentDeep = Color(0xFF6E737D)

/** Panel fisik raised — extrusion kuat (highlight tipis di puncak, shadow dalam di
 *  dasar), BUKAN sheen kaca lembut seperti 2 varian glass. Batch 39: stop
 *  puncak/dasar dicampur sedikit `SkeuoAccent`/`SkeuoAccentDeep` (bukan cuma
 *  putih/hitam polos) — biar karakter metalik titanium-silver kerasa di SELURUH
 *  panel, bukan cuma di 1 chip accent kecil.
 *  Batch 44: 4-stop -> 6-stop + 1 streak anisotropic tambahan di tengah (bukan
 *  cuma puncak/dasar rata) — meniru brushed-metal difoto kena cahaya (permukaan
 *  logam disikat SELALU punya lebih dari 1 pita terang/gelap, bukan gradasi
 *  tunggal halus). Tetap linear top-left->bottom-right, konsisten arah cahaya
 *  tunggal project ini sejak Batch 32. */
val SkeuoBevelBrush: Brush = Brush.linearGradient(
    listOf(
        lerp(SkeuoPanelRaised, Color.White, 0.16f),
        lerp(SkeuoPanelRaised, SkeuoAccent, 0.12f),
        SkeuoPanelRaised,
        lerp(SkeuoPanel, SkeuoAccent, 0.05f),
        SkeuoPanel,
        lerp(SkeuoPanelRecessed, SkeuoAccentDeep, 0.10f)
    )
)
/** Batch 44: kontras highlight/shadow dinaikkan (alpha highlight 0.12->0.20,
 *  shadow ganti dari `SkeuoEdgeShadow` alpha 0.78 -> solid black alpha 0.85) —
 *  border extrusion lebih "dalam"/hyper-realistic, khas hardware fisik kena
 *  cahaya keras, bukan gradasi lembut lagi. */
val SkeuoBevelBorderBrush: Brush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.20f), Color.Transparent, Color.Black.copy(alpha = 0.85f))
)

/** Highlight glossy lebih tajam/terkonsentrasi (bukan sheen airy iOS) — meniru
 *  reflection keras di permukaan tombol/dial fisik berlapis kaca/plastik glossy.
 *  Batch 44: tambah 1 stop (3->4) biar sheen-nya kerasa sebagai STREAK sempit
 *  (bukan gradasi lebar) — ciri khas reflection keras di logam glossy. */
val SkeuoSpecularBrush: Brush = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.32f),
        Color.White.copy(alpha = 0.10f),
        Color.White.copy(alpha = 0.02f),
        Color.Transparent
    )
)

val SkeuoPrimaryGlow = SkeuoAccent.copy(alpha = 0.30f)

/** Batch 44 — rivet/screw accent DEDICATED (dipakai `Modifier.skeuRivets` di
 *  `SkeuomorphicComponents.kt`, cuma render kalau `SkeuTokens.hyperRealism` true,
 *  jadi 0 dampak ke 2 varian glass). Radial gradient kecil: highlight terang di
 *  pusat (simulasi kena cahaya) -> titanium deep -> nyaris hilang ke panel gelap
 *  di tepi, meniru kepala screw metal kecil yang menonjol di panel fisik asli —
 *  detail hardware klasik hyper-realism skeuomorphism (bukan cuma tekstur, tapi
 *  elemen fungsional-terlihat: "ini dibaut ke panel"). */
val SkeuoRivetBrush: Brush = Brush.radialGradient(
    listOf(
        lerp(SkeuoAccent, Color.White, 0.45f),
        SkeuoAccentDeep,
        SkeuoPanelRecessed
    )
)

/** Batch 44 — warna groove/knurl DEDICATED buat tekstur cincin melingkar di
 *  slider knob (`SkeuSliderThumb`, `drawCircle(style = Stroke(...))`), meniru
 *  permukaan dial logam machined/knurled asli (bukan bola polos). Alpha rendah
 *  (0.5) biar tetap SUBTLE — detail tekstur, bukan elemen dominan yang
 *  mengorbankan `sliderKnobHighlight` yang sudah ada. */
val SkeuoKnurlColor = SkeuoAccentDeep.copy(alpha = 0.5f)

/** Knob slider — nyaris putih, meniru bead kaca/plastik glossy fisik (ring accent
 *  titanium-silver dibawa lewat border 2dp di komponennya, bukan warna isi). */
val SkeuoKnobHighlight: Color = lerp(SkeuoPanelRaised, Color.White, 0.34f)

/** Radius kartu/icon-box KHUSUS varian ini (Batch 39) — lebih kecil/tegas dari
 *  `SkeuCardRadius`/`SkeuIconBoxRadius` (26dp/16dp, radius iOS-glass Batch 37) —
 *  skeuomorphism klasik pakai sudut lebih tegas/hardware-like, bukan bubbly-rounded
 *  ala iOS. Dibaca lewat `SkeuTokens.cardRadius`/`iconBoxRadius`, BUKAN const
 *  global lagi — supaya benar-benar otonom per-varian (lihat catatan Batch 39 di
 *  `SkeuTokens` di bawah). */
val SkeuoCardRadius = 14.dp
val SkeuoIconBoxRadius = 10.dp

/** Background layar — vertical gradient gunmetal netral, BUKAN biru. Skeuomorphism
 *  gak butuh backdrop vivid (beda dari glass yang butuh variasi buat translucency
 *  kebaca) — cukup shading halus konsisten sama arah cahaya panel fisik. */
val SkeuoScreenBackgroundBrush: Brush = Brush.verticalGradient(
    listOf(Color(0xFF232327), SkeuoPanel, SkeuoBackground)
)

/** Token yang beda antar 3 varian desain, dibaca lewat `LocalSkeuTokens.current`
 *  (SkeuomorphicComponents.kt) — 1 kode komponen, 3 varian, TANPA duplikasi. Field
 *  baru WAJIB diisi di SEMUA instance di bawah kalau ditambah lagi.
 *  Batch 39: `cardRadius`/`iconBoxRadius` ditambah — sebelumnya radius kartu/icon-box
 *  hardcode ke const global `SkeuCardRadius`/`SkeuIconBoxRadius` (dipakai SEMUA
 *  varian tanpa beda), sekarang per-varian supaya Skeuomorphism (radius lebih
 *  tegas/kecil, khas hardware fisik) beneran otonom — gak numpang radius iOS-glass
 *  Batch 37 punya 2 varian glass.
 *  Batch 44: 5 field baru buat "Skeuomorphism 2.0 Hyper-Realism" — DEVIASI SADAR
 *  dari konvensi "wajib diisi eksplisit di semua instance" di atas: field baru ini
 *  PUNYA DEFAULT VALUE yang identik ke perilaku hardcode LAMA (`buttonRestElevation`
 *  = `6.dp`, `buttonGlowSpread` = `14.dp`, persis angka yang sebelumnya hardcode di
 *  `SkeuPowerButton`), jadi `AmoledGlassSkeuTokens`/`RadicalSkeuoSkeuTokens` di
 *  bawah TIDAK PERLU diedit sama sekali — nol resiko regresi ke 2 varian glass yang
 *  sudah kerja (gak mungkin kelupaan isi field di instance yang gak disentuh). */
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
    // Batch 44 (Hyper-Realism 2.0) — default = perilaku lama persis, lihat catatan di atas.
    val hyperRealism: Boolean = false,
    val buttonRestElevation: Dp = 6.dp,
    val buttonGlowSpread: Dp = 14.dp,
    val rivetBrush: Brush = SolidColor(Color.Transparent),
    val knurlColor: Color = Color.Transparent
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
    iconBoxRadius = SkeuIconBoxRadius
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
    iconBoxRadius = SkeuIconBoxRadius
)

/** Varian 3: "Skeuomorphism 2.0 (Hyper-Realism UI)" — bahasa desain fisik/tekstural
 *  asli (bevel-shadow ekstrusi kuat + aksen metalik titanium-silver, Batch 39),
 *  BUKAN glass sama sekali. Radius sendiri (`SkeuoCardRadius`/`SkeuoIconBoxRadius`,
 *  14dp/10dp) — BUKAN numpang const iOS-glass punya 2 varian di atas.
 *  Batch 44: `cardElevation` 8dp->10dp (extrusion lebih dalam) + 4 field baru
 *  DEDICATED (`hyperRealism=true`, `buttonRestElevation`/`buttonGlowSpread` naik
 *  dari default shared 6dp/14dp ke 11dp/20dp, `rivetBrush`/`knurlColor` token baru
 *  Batch 44 di atas) — SEMUA cuma dipakai kalau `hyperRealism` true, jadi otonom
 *  penuh dari 2 varian glass yang defaultnya tetap tidak berubah. */
val SkeuomorphismSkeuTokens = SkeuTokens(
    mutedText = SkeuoTextMuted,
    bevelBrush = SkeuoBevelBrush,
    bevelBorderBrush = SkeuoBevelBorderBrush,
    primaryGlow = SkeuoPrimaryGlow,
    baseSurface = SkeuoPanel,
    elevatedSurface = SkeuoPanelRaised,
    cardBrush = SkeuoBevelBrush,
    cardBorderBrush = SkeuoBevelBorderBrush,
    cardElevation = 10.dp,
    sliderKnobHighlight = SkeuoKnobHighlight,
    specularBrush = SkeuoSpecularBrush,
    cardRadius = SkeuoCardRadius,
    iconBoxRadius = SkeuoIconBoxRadius,
    hyperRealism = true,
    buttonRestElevation = 11.dp,
    buttonGlowSpread = 20.dp,
    rivetBrush = SkeuoRivetBrush,
    knurlColor = SkeuoKnurlColor
)

/** Pilihan varian aktif — persisted lewat `PrefsHelper.getAppThemeStyle` (String
 *  constants `APP_THEME_AMOLED_GLASS`/`APP_THEME_RADICAL_SKEUO`, nama TIDAK diubah
 *  biar data user lama valid), di-map ke enum ini di `MainActivity.kt`. Default
 *  `AMOLED_GLASS` ("Midnight Glass"). */
enum class AppThemeStyle { AMOLED_GLASS, RADICAL_SKEUO, SKEUOMORPHISM }

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

private val SkeuomorphismDarkColors = darkColorScheme(
    primary = SkeuoAccent,
    // Batch 39: onPrimary/primaryContainer diganti dari warm-brown (era aksen
    // tembaga) ke neutral-cool (era aksen titanium-silver) — teks di atas swatch
    // primary sekarang near-black netral (bukan coklat gelap), container-nya
    // steel-grey gelap (bukan coklat tua).
    onPrimary = Color(0xFF15161A),
    primaryContainer = Color(0xFF3A3D44),
    onPrimaryContainer = Color(0xFFE7E9ED),
    secondary = SkeuoTextSecondary,
    onSecondary = Color(0xFF15161A),
    background = SkeuoBackground,
    onBackground = SkeuoTextPrimary,
    surface = SkeuoPanel,
    onSurface = SkeuoTextPrimary,
    surfaceVariant = SkeuoPanelRaised,
    onSurfaceVariant = SkeuoTextSecondary,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFD8D8),
    outline = SkeuoEdgeHighlight
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

/** Batch 39: shape khusus varian Skeuomorphism — sudut lebih tegas/kecil (konsisten
 *  sama `SkeuoCardRadius`/`SkeuoIconBoxRadius` di atas), supaya komponen Material3
 *  default (AlertDialog/Button/dll) yang belum pakai shape manual JUGA otonom, gak
 *  ikut radius iOS-glass punya 2 varian lain. */
private val SkeuomorphismShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
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
    // Midnight/Aurora Glass/Skeuomorphism.
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        themeStyle == AppThemeStyle.RADICAL_SKEUO -> RadicalDarkColors
        themeStyle == AppThemeStyle.SKEUOMORPHISM -> SkeuomorphismDarkColors
        else -> DarkColors
    }
    val skeuTokens = when (themeStyle) {
        AppThemeStyle.RADICAL_SKEUO -> RadicalSkeuoSkeuTokens
        AppThemeStyle.SKEUOMORPHISM -> SkeuomorphismSkeuTokens
        else -> AmoledGlassSkeuTokens
    }
    // Batch 39: shapes juga di-pilih per-varian (sebelumnya `AppShapes` statis buat
    // semua) — Skeuomorphism pakai `SkeuomorphismShapes` (sudut lebih tegas).
    val shapes = if (themeStyle == AppThemeStyle.SKEUOMORPHISM) SkeuomorphismShapes else AppShapes
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
