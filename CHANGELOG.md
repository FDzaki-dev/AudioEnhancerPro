# Changelog

## v1.79.0 - Pangkas waktu compile CI (Batch 40, infra-only, 0 perubahan kode Kotlin)

Diminta user eksplisit: "terapkan konfigurasi agar waktu compile GitHub project ini
dapat dipangkas sebanyak mungkin, apapun caranya". Batch INFRA MURNI — 3 file
berubah (`.github/workflows/build.yml`, `gradle.properties`, `app/build.gradle.kts`
cuma version bump), **0 file Kotlin/Compose/resource disentuh**, jadi risiko regresi
visual/fungsional = nihil. Root cause utama lambatnya CI project ini SELAMA INI:
**tidak ada caching sama sekali** — tiap push, CI bootstrap wrapper Gradle dari nol
(generate via Gradle sistem 9.6.1 di direktori scratch, insiden Batch 19-21) LALU
download distribusi Gradle 8.7 dari internet LAGI buat jalanin build-nya — 2x
overhead per run, TANPA ada dependency AndroidX/Compose/Hilt yang di-cache antar-run
sama sekali (~150-300MB re-download tiap push kode sekecil apapun).

**1. Ganti bootstrap manual -> `gradle/actions/setup-gradle@v4` (win terbesar):**
- Step "Bootstrap Gradle Wrapper (isolated dir)" (workaround Batch 19-21, ~35 baris
  script) DIHAPUS TOTAL di kedua job (`build`+`release`). Diganti 1 step:
  `gradle/actions/setup-gradle@v4` dengan `gradle-version: '8.7'` — action resmi ini
  provision Gradle 8.7 langsung ke PATH (gak perlu wrapper/gradlew apapun) DAN
  otomatis cache distribusi Gradle + `~/.gradle/caches/modules-2` (semua dependency
  AndroidX/Compose/Hilt) + Gradle Build Cache lokal **ANTAR-RUN CI** lewat GitHub
  Actions cache backend. Run pertama sama kayak sebelumnya (semua di-download), tapi
  run berikutnya (update harian, dependency gak berubah): dependency di-restore dari
  cache dalam hitungan detik, task kapt/compileKotlin yang input-nya gak berubah
  di-restore dari Build Cache (bukan dieksekusi ulang).
- Kedua job sekarang panggil `gradle assembleDebug`/`gradle assembleRelease`
  langsung (bukan `./gradlew`) — action yang expose binary `gradle` ke PATH.
- `gradle-wrapper-bootstrap.log` dihapus dari path upload artifact kegagalan (gak
  ada lagi step yang menghasilkan file itu).

**2. `gradle.properties` — flag compile-time (lihat komentar inline di file):**
- `org.gradle.parallel=true`, `org.gradle.caching=true` (WAJIB biar Build Cache di
  atas kepakai), `org.gradle.configureondemand=true`.
- `kapt.incremental.apt=true`, `kapt.use.worker.api=true`,
  `kapt.include.compile.classpath=false` — kapt (Hilt annotation processing) adalah
  task PALING LAMBAT di build project ini, 3 flag resmi ini mempercepatnya langsung.
- `org.gradle.jvmargs` heap dinaikkan `-Xmx2048m` -> `-Xmx4096m` — kapt+Compose
  compiler plugin butuh heap lebih besar biar gak GC-thrashing (runner ubuntu-latest
  7GB RAM, 4GB buat Gradle daemon masih aman).

**3. Job `release` gak lagi nunggu job `build` (`needs: build` dicabut):**
- Sebelumnya SEQUENTIAL (release nunggu build selesai duluan) — sekarang PARALEL.
  Waktu WALL-CLOCK CI total = `max(waktu build, waktu release)`, bukan lagi
  `waktu build + waktu release`. **TRADE-OFF disengaja** (didokumentasikan lengkap
  di komentar `build.yml`): kalau kode beneran gak kompilasi, dulu job `release`
  otomatis ke-skip (hemat waktu), sekarang tetap jalan sampai gagal sendiri secara
  paralel — tapi karena PARALEL (bukan nambah di belakang), durasi TOTAL run tetap
  gak lebih lambat dari sebelumnya, dan repo ini PUBLIC (GitHub Actions minutes
  gratis/unlimited, dicatat eksplisit di PROJECT_STATE.md) jadi gak ada biaya nyata.

**SENGAJA TIDAK dikerjakan (dipertimbangkan, ditunda — alasan di PROJECT_STATE.md
bagian "Batasan sandbox"):** `org.gradle.configuration-cache=true` — berpotensi
mempercepat fase konfigurasi Gradle lebih jauh, TAPI kompatibilitasnya dengan
kapt+Hilt di kombinasi AGP 8.5.2/Kotlin 1.9.24 project ini belum bisa diverifikasi
tanpa compiler (sandbox Claude gak bisa compile-check) — resiko break build lebih
besar dari manfaat speed tambahannya untuk project 1-modul ini. Kandidat lanjutan
kalau user mau coba (dengan resiko yang dipahami).

**Belum divalidasi CI/runtime** — perubahan ini PALING GAMPANG diverifikasi
dibanding batch-batch sebelumnya (bukan logic Kotlin, sandbox Claude gak bisa
compile-check gimanapun), tapi validasi SEBENARNYA baru kelihatan di run CI ke-2
setelah batch ini (run pertama masih cold-cache, seharusnya durasi mirip biasanya;
run kedua dst BARU kelihatan efek cache-nya — itu ukuran keberhasilan sebenarnya).


## v1.78.0 - Skeuomorphism 100% otonom + aksen titanium-silver metalik

Diminta user 2 hal eksplisit: (1) buat varian Skeuomorphism (Batch 38) berdiri
otonom penuh — jangan numpang radius/shape ke baseline glass; (2) ganti aksen dari
tembaga/perunggu ke titanium+silver metalik.

**1. Otonomi radius/shape (sebelumnya numpang ke const global iOS-glass Batch 37):**
- `SkeuTokens` (Theme.kt) tambah 2 field baru: `cardRadius`, `iconBoxRadius`. SEMUA
  instance (`AmoledGlassSkeuTokens`/`RadicalSkeuoSkeuTokens`/
  `SkeuomorphismSkeuTokens`) diisi eksplisit — 2 varian glass tetap pakai
  `SkeuCardRadius`/`SkeuIconBoxRadius` (26dp/16dp, TIDAK berubah), Skeuomorphism
  dapat radius sendiri: `SkeuoCardRadius` (14dp) & `SkeuoIconBoxRadius` (10dp) —
  lebih tegas/kecil, khas hardware fisik, bukan bubbly-rounded ala iOS.
- `SkeuomorphicComponents.kt`: `SkeuCard`/`SkeuTintedCard`/icon-box baca radius dari
  `LocalSkeuTokens.current.cardRadius`/`iconBoxRadius` (bukan const global
  `SkeuCardRadius`/`SkeuIconBoxRadius` langsung lagi).
- `Theme.kt`: `Shapes` Material3 (dipakai default oleh `Button`/`AlertDialog`/dll)
  sekarang juga per-varian — `SkeuomorphismShapes` baru (4/8/12/16/20dp, sudut
  tegas) dipilih via `AudioEnhancerTheme` composable kalau `themeStyle ==
  SKEUOMORPHISM`, 2 varian glass tetap pakai `AppShapes` (10/16/22/28/34dp) lama.

**2. Aksen tembaga -> titanium+silver metalik:**
- `SkeuoAccent`: `#C98A4C` (tembaga hangat) -> `#AEB4BF` (silver-titanium cool
  neutral-grey, sedikit undertone biru-baja).
- Token baru `SkeuoAccentDeep` (`#6E737D`, nada titanium lebih gelap) — dipakai di
  `SkeuoBevelBrush` (stop puncak/dasar panel dicampur sedikit `SkeuoAccent`/
  `SkeuoAccentDeep`, bukan cuma putih/hitam polos) supaya karakter metalik
  titanium-silver kerasa di SELURUH panel kartu, bukan cuma di 1 chip accent kecil.
- `SkeuomorphismDarkColors`: `onPrimary`/`primaryContainer`/`onPrimaryContainer`
  diganti dari warm-brown (`#1A1005`/`#4A331A`/`#F5DFC4`) ke neutral-cool
  (`#15161A`/`#3A3D44`/`#E7E9ED`) — konsisten sama accent baru.
- `values/strings.xml` + `values-en/strings.xml`: desc toggle diganti "aksen
  titanium-silver metalik"/"metallic titanium-silver accent" (sebelumnya "tembaga
  hangat"/"warm copper"). Parity tetap 98/98 (isi string diganti, jumlah tidak).
- `app/build.gradle.kts`: versionCode 78->79, versionName 1.77.0->1.78.0.

**SENGAJA TIDAK diubah**: 2 varian glass (Midnight Glass/Aurora Glass) — warna,
radius, shape, semuanya utuh sejak Batch 37/38. Panel netral
(`SkeuoBackground`/`SkeuoPanel`/dll, charcoal netral) tidak diubah — sudah cocok
buat titanium/silver (netral, bukan warm-tinted), cukup accent + bevel yang
disesuaikan.

**Belum divalidasi runtime** — statis only (brace/paren balance 0/0 full sweep
project, parity string ID/EN 98/98, grep konfirmasi tidak ada referensi
`SkeuCardRadius`/`SkeuIconBoxRadius` yang kelewat di luar Theme.kt & 2 instance
token glass).



## v1.77.0 - Tambah varian tema ke-3: Skeuomorphism (dark-mode asli)

Diminta user eksplisit: tambahkan toggle theme baru di bawah "Gaya Aurora Glass",
berisi theme custom "Skeuomorphism" dark mode yang asli — "gak kurang, gak lebih".
Scope SENGAJA dibatasi ketat: nambah 1 varian tema baru + toggle-nya saja, TIDAK
menyentuh/mengubah 2 varian glass (Midnight Glass, Aurora Glass) dari Batch 37.

**Bahasa desain varian baru** — beda total dari 2 varian glass (bukan sub-opsi/
turunan-nya): panel gunmetal/charcoal netral (`SkeuoBackground`/`SkeuoPanel`, BUKAN
biru-tint), bevel raised/recessed dengan extrusion shadow lebih dalam
(`SkeuoBevelBrush`/`SkeuoBevelBorderBrush`, meniru panel fisik nyata — bukan sheen
kaca lembut), highlight glossy lebih tajam/terkonsentrasi (`SkeuoSpecularBrush`,
meniru reflection keras di permukaan tombol fisik berlapis kaca/plastik, beda dari
sheen airy iOS di varian glass), dan aksen metalik hangat tembaga/perunggu
(`SkeuoAccent` #C98A4C — SENGAJA beda hue dari accent biru dingin 2 varian lain, ciri
khas skeuomorphism klasik era iOS 6/brushed-metal UI).

**File yang berubah:**
1. `Theme.kt` — enum `AppThemeStyle` tambah `SKEUOMORPHISM` (dari 2 jadi 3 nilai).
   Token baru: `SkeuoBackground`/`SkeuoPanel`/`SkeuoPanelRaised`/`SkeuoPanelRecessed`,
   `SkeuoEdgeHighlight`/`SkeuoEdgeShadow`, `SkeuoText*`, `SkeuoAccent`,
   `SkeuoBevelBrush`/`SkeuoBevelBorderBrush`, `SkeuoSpecularBrush`,
   `SkeuoPrimaryGlow`, `SkeuoKnobHighlight`, `SkeuoScreenBackgroundBrush` (gradient
   netral gunmetal, ganti dari gradient biru 2 varian lain). Instance baru
   `SkeuomorphismSkeuTokens` (cardElevation 8dp — extrusion paling kuat dari 3
   varian) & `SkeuomorphismDarkColors`. `AudioEnhancerTheme` composable di-`when`-kan
   (dari if/else 2-cabang) buat resolve 3 varian.
2. `PrefsHelper.kt` — const baru `APP_THEME_SKEUOMORPHISM = "skeuomorphism"`. Const
   lama (`APP_THEME_AMOLED_GLASS`/`APP_THEME_RADICAL_SKEUO`) TIDAK diubah (persisted
   String, sudah didesain sejak Batch 36 buat nampung >2 varian — data user lama
   tetap valid).
3. `MainActivity.kt` — mapping String->enum & pemilihan `screenBrush` di-`when`-kan
   (dari if/else). Param `BoosterScreen` ganti dari
   `themeStyleIsRadical: Boolean`/`onThemeStyleChange: (Boolean) -> Unit` jadi
   `appThemeStyleKey: String`/`onThemeStyleChange: (String) -> Unit` — perlu String
   langsung karena sekarang 3 pilihan saling eksklusif, bukan cuma on/off.
4. `BoosterScreen.kt` — signature param ikut berubah (lihat poin 3). Kartu toggle
   "Gaya Aurora Glass" (existing) logic-nya disesuaikan ke pola String. Kartu BARU
   "Skeuomorphism" ditambahkan PERSIS DI BAWAHNYA (posisi sesuai permintaan user),
   struktur/style kartu 100% konsisten dengan kartu Aurora Glass di atasnya (SkeuCard
   + Icon + judul + desc + SkeuSwitch, toggleable Role.Switch + haptic). Icon baru
   `Icons.Filled.Build` (import ditambah). Toggle salah satu varian otomatis
   nonaktifkan varian lain (1 pilihan tunggal, 3 opsi) — matiin toggle mana pun balik
   ke default Midnight Glass.
5. `values/strings.xml` + `values-en/strings.xml` — string baru
   `theme_style_skeuo_title`/`theme_style_skeuo_desc` (ID+EN). Parity tetap terjaga:
   98/98.
6. `docs/preview/current.html` — footer note diperbarui (transparan, menyebut
   varian ke-3 ada), TIDAK dibuatkan mockup visual terpisah untuk varian ini (di
   luar scope "toggle + theme", preview HTML tetap representasi varian default/
   Aurora seperti sebelumnya — kalau user mau preview visual Skeuomorphism secara
   HTML, minta eksplisit di sesi berikutnya).
7. `app/build.gradle.kts` — versionCode 77->78, versionName 1.76.0->1.77.0.

**SENGAJA TIDAK diubah**: 2 varian glass (Midnight Glass/Aurora Glass) dari Batch 37
— warna, brush, radius, semuanya utuh. `SkeuomorphicComponents.kt` (render logic
SkeuCard/SkeuSwitch/dll) TIDAK disentuh — sudah generik lewat `LocalSkeuTokens`,
otomatis kompatibel dengan varian baru tanpa perubahan kode render.

**Belum divalidasi runtime** — statis only (brace/paren balance 0/0 di 4 file Kotlin
yang disentuh + full sweep project, parity string ID/EN 98/98, tidak ada referensi
`AppThemeStyle`/`themeStyleIsRadical` lain yang kelewat di luar `Theme.kt`/
`MainActivity.kt`/`BoosterScreen.kt`). `when` di `AudioEnhancerTheme`/`MainActivity`
punya `else` branch (exhaustive secara efektif), aman dari missing-branch compile
error meski Kotlin `when` atas non-sealed subject tetap technically butuh else/
default.



## v1.76.0 - Rewrite total UI/UX: iOS Glassmorphism + Midnight-Blue dominan

Diminta user eksplisit: "Rewrite total (bukan ganti pallet warna murahan) di sektor
UI & UX dengan gaya visual 'Glassmorphism ios-style' dominan, dengan tambahan hint &
gradasi 'Midnight-Blue' tanpa embel-embel yang bikin desain gagal. Readability
maksimal." Arsitektur 2-varian (`SkeuTokens`/`AppThemeStyle`, Batch 36) DIPERTAHANKAN
— bukan fitur yang dihapus — tapi ISI kedua varian ditulis ulang total jadi iOS
Glassmorphism (sebelumnya 1 glass restrained + 1 skeuomorphism bevel-raised).

**File yang berubah (Atomic Change, 1 batch — total UI/UX rewrite, alasan sama
seperti pivot desain besar sebelumnya Batch 31/33/36):**
1. `Theme.kt` — token warna & brush ditulis ulang total (bukan cuma ganti hex di
   posisi sama): kartu jadi 4-stop diagonal glass gradient (`MidnightBlueGlassBrush`/
   `RadicalGlassBrush`, sebelumnya 3-stop rata & solid flat), border kartu jadi
   gradient highlight->transparan (`GlassBorderBrush`, sebelumnya solid alpha tipis),
   token BARU `specularBrush` (sheen kaca ala iOS) di `SkeuTokens`, radius naik
   (kartu 20->26dp, icon-orb 14->16dp, `AppShapes` semua step), background layar
   baru `ScreenBackgroundBrush`/`AuroraScreenBackgroundBrush` (gradient vertikal
   Midnight-Blue -> nyaris-hitam, ganti flat `colorScheme.background`), kontras teks
   dinaikkan tegas (`TextPrimary` #EAF0F8->#F3F6FF, `TextSecondary` #AAB5C4->#C5CCE2,
   `TextMuted` #737E8C->#8D96AC, setara di varian Aurora), `MidnightBlueAmbientAlpha`
   0.06->0.20 (hint dominan, bukan subtle lagi).
2. `SkeuomorphicComponents.kt` — perubahan STRUKTURAL, bukan cuma warna:
   `SkeuCard`/`SkeuTintedCard`/`SkeuPowerButton` sekarang punya layer
   `.background(tokens.specularBrush)` KEDUA (sheen kaca pojok kiri-atas). `SkeuSwitch`
   blend ON dinaikkan 0.35->0.55 (midnight-blue lebih dominan saat aktif), thumb OFF
   dinaikkan ke campuran putih 45% (bead kaca terang ala iOS, sebelumnya abu gelap
   polos `tokens.elevatedSurface`). Nama fungsi/komponen TIDAK diubah.
3. `MainActivity.kt` (edit parsial, protected asset) — root `Surface` background
   diganti dari `MaterialTheme.colorScheme.background` flat ke
   `ScreenBackgroundBrush`/`AuroraScreenBackgroundBrush` (dipilih sesuai varian aktif).
4. `values/colors.xml` + `values-night/colors.xml` — splash `#030508`->`#03040B`
   (selaras `AmoledBlack` baru).
5. `drawable/widget_background.xml` — gradient 3-stop XML disesuaikan ke komposisi
   `MidnightBlueGlassBrush` baru + radius 20dp->26dp.
6. `drawable/ic_shortcut_preset.xml` — fill `#6670FF`->`#5E7BFF` (selaras
   `MidnightBlueAccent` baru).
7. `values/strings.xml` + `values-en/strings.xml` — copy switch tema disesuaikan ke
   nama varian baru: "Gaya Aurora Glass"/"Aurora Glass Style" (sebelumnya "Gaya
   Tampilan Radikal"/"Radical Visual Style"), desc dijelaskan sebagai varian glass
   lebih vivid (bukan lagi "physical bevels"). Parity ID/EN tetap 96/96.
8. `docs/preview/current.html` — disinkronkan penuh ke komposisi baru (gradient
   background, sheen kartu via `::before`, radius, token warna) — ground truth visual
   project ini, WAJIB ikut berubah bareng Kotlin (lesson lama Batch 33/34).
9. `app/build.gradle.kts` — versionCode 76->77, versionName 1.75.1->1.76.0.

**SENGAJA TIDAK diubah (transparan)**:
- Nama const persistence (`PrefsHelper.APP_THEME_AMOLED_GLASS`/
  `APP_THEME_RADICAL_SKEUO`) & nama enum (`AppThemeStyle.AMOLED_GLASS`/
  `RADICAL_SKEUO`) — Protected Asset persistence key, ganti nama akan pecah data
  user lama tanpa migrasi. Cukup REPRESENTASI VISUAL yang di-rewrite total; secara
  konsep sekarang "Midnight Glass" (default) & "Aurora Glass" (switch ON).
- Warna aksen per-fitur (`BassAccent`, `VirtualizerAccent`, dst) — bukan sumber
  keluhan, identitas per-fitur independen dari surface hierarchy.
- Typography scale (ukuran/berat font) — sudah readable, resiko layout kalau diubah
  tanpa compiler tidak sepadan manfaatnya untuk task ini.
- App launcher icon (`ic_launcher_background.xml`, era palet bronze lama) — di luar
  scope "sektor UI & UX" (ini brand icon, bukan layar app), TIDAK disentuh supaya
  tidak merusak Adaptive Icon tanpa diminta eksplisit.

**Belum divalidasi runtime** — statis only (brace/paren balance 0/0 di 3 file Kotlin
yang disentuh + full sweep project, parity string ID/EN 96/96, XML valid semua file
resource yang disentuh). `Modifier.background(brush)` dipanggil 2x berurutan
(base + specular) adalah pola BARU pertama kali dipakai di project ini — kandidat
pertama dicurigai kalau ada laporan kartu render aneh (sheen gak nongol/salah posisi).
Referensi: Compose foundation `background(brush: Brush, shape: Shape, alpha: Float)`
API standar sejak lama, dipanggil berurutan pada modifier chain yang sama dijamin
digambar berurutan (base dulu, lalu specular di atasnya) — perilaku ini konsisten
dengan cara `Modifier.background()` bekerja di seluruh ekosistem Compose.



> 🧠 **Sesi Claude baru?** Baca `PROJECT_STATE.md` dulu (bukan file ini) — didesain khusus buat konteks AI: keputusan desain+alasannya, batasan teknis, riwayat pivot. File ini (CHANGELOG) cuma buat detail teknis per-versi.

> 🎨 **Preview UI/UX terkini (live, selalu update)**: [buka di sini](https://htmlpreview.github.io/?https://github.com/FDzaki-dev/AudioEnhancerPro/blob/main/docs/preview/current.html) — render langsung dari `docs/preview/current.html` di repo ini, jadi selalu mencerminkan arah desain yang lagi didiskusikan sebelum di-build jadi APK.

## v1.75.1 - Batch 36 Fix: kaptGenerateStubsDebugKotlin FAILED (CI run #81)
**Root cause**: `Theme.kt` (Batch 36) nambah `data class SkeuTokens(..., val cardElevation:
Dp, ...)` — tipe `Dp` dipakai eksplisit sebagai type annotation tapi importnya cuma
`androidx.compose.ui.unit.dp` (extension property lowercase, buat nulis `20.dp`), BUKAN
`androidx.compose.ui.unit.Dp` (class-nya, huruf besar). Unresolved reference ini di-swallow
kapt jadi pesan generik `e: Could not load module <Error module>` tanpa file:line — makanya
butuh baca `gradle-build-debug.log` mentah (bukan cuma pesan permukaan) buat ketemu akar
masalahnya: satu file (`Theme.kt`) dicek baris-per-baris terhadap semua import yang
dipakai, ketemu `SkeuomorphicComponents.kt` sudah lama import `Dp` classnya (dipakai di
`skeuGlow`/`SkeuCard` param) tapi `Theme.kt` belum pernah butuh sampai Batch 36 nambah
field baru itu.
**Fix**: tambah `import androidx.compose.ui.unit.Dp` di `Theme.kt`. 1 baris, 0 perubahan
logika/fitur — semua yang di CHANGELOG v1.75 di atas TETAP BERLAKU APA ADANYA.
**Lesson buat sesi depan**: `e: Could not load module <Error module>` dari
`kaptGenerateStubsDebugKotlin` HAMPIR SELALU unresolved reference/import kurang di salah
satu file yang BARU diubah di batch itu — cek dulu SEMUA tipe eksplisit (`: Dp`, `: Color`,
`: Brush`, dst) di file yang disentuh punya import class-nya (bukan cuma extension
property/fungsinya), sebelum curiga ke hal lain (dependency version, kapt config, dst).

## v1.75 - Batch 36: fitur baru — switch 2 sistem desain (AMOLED Glass vs Radical Literal Skeuomorphism)
User minta fitur "setting custom switch theme" yang konfigurasinya 100% mengikuti guide
baru yang diupload: `compose-skeuomorphism-radical-literal-dark-readability-performance-final.md`
("Radical + Literal Skeuomorphism — Dark Mode Only — Performance First"). Guide ini BEDA
TOTAL dari guide AMOLED Hybrid Glass yang jadi tema aktif sekarang (Batch 33-35) —
sebelum implementasi, scope dikonfirmasi eksplisit ke user via pilihan (bukan diasumsikan,
lihat lesson Batch 34 soal ini): user pilih opsi **"tambah toggle pilih salah satu dari 2
tema, dua-duanya tetap ada"** — BUKAN replace total, BUKAN switch mandiri di luar tema aktif.

**Implementasi (arsitektur token, BUKAN duplikasi composable per-tema)**:
1. `Theme.kt` — port token warna guide baru 1:1 (0 interpretasi hex): `RadicalBackground
   #050505`, `RadicalSurface #101010`, `RadicalSurfaceRaised #171717`,
   `RadicalSurfaceRecessed #080808`, `RadicalEdgeHighlight` (white@7.5%), `RadicalEdgeShadow`
   (black@80%), `RadicalTextPrimary/Secondary/Muted`, `RadicalAccent #5F9EFF` (guide §2).
   Bevel brush top-left→bottom-right (guide §4/§5) & glow (guide §18) turunan dari token ini.
2. `data class SkeuTokens` (Theme.kt, baru) — kumpulan field yang beda FILOSOFI antar 2 tema
   (card fill/border/elevation, muted text, bevel brush, slider knob highlight). 2 instance:
   `AmoledGlassSkeuTokens` (existing, glass-first, kartu flat-tint) & `RadicalSkeuoSkeuTokens`
   (baru, bevel-first — guide §1 "every major interactive object should have implied
   physical construction", jadi KARTU STRUKTURAL di tema ini pun raised-bevel, BEDA dari
   AMOLED Glass yang sengaja "glass murni" di kartu). Disediakan lewat `LocalSkeuTokens`
   (CompositionLocal) + `LocalAppThemeStyle` (`enum AppThemeStyle { AMOLED_GLASS,
   RADICAL_SKEUO }`), di-provide `AudioEnhancerTheme(themeStyle=...)` (param baru).
3. `SkeuomorphicComponents.kt` — `SkeuCard`/`SkeuTintedCard`/`SkeuPowerButton`/
   `SkeuSliderThumb`/`SkeuSwitch`/`FeatureControl` (helpText) di-refactor baca
   `LocalSkeuTokens.current` (fill/border/elevation/glow/muted-text/knob-highlight),
   BUKAN lagi val top-level `Glass*`/`TextMuted`/`SkeuBevelBrush`/`SkeuPrimaryGlow`
   hardcoded — 1 kode komponen jalan otomatis buat 2 tema, TANPA percabangan `when()`
   di tiap komponen maupun duplikasi file composable.
4. `BoosterScreen.kt` — SEMUA referensi `TextMuted`/`SkeuPrimaryGlow` langsung (7+2
   lokasi: desc power toggle, label chip preset built-in+custom, hint preset kosong,
   char-count nama preset, desc Material You, subtitle equalizer, glow chip preset aktif)
   diganti `LocalSkeuTokens.current.mutedText`/`.primaryGlow` — supaya readability &
   glow ikut 100% konsisten ke tema aktif di SELURUH layar, bukan cuma komponen baru
   (guide baru §26 final checklist "Readability" + final verdict "if physical realism
   and readability conflict, readability wins" — jadi WAJIB scope-nya menyeluruh, bukan
   parsial di 1 kartu doang). Kartu baru "Gaya Tampilan Radikal" (`SkeuSwitch`) ditaruh
   persis di bawah kartu Material You (pattern UI identik: icon + title + desc + switch,
   `toggleable` di `Row`, `onCheckedChange=null` di `SkeuSwitch` — sama seperti Material
   You, parent `Row` yang pegang `toggleable`-nya sendiri).
5. `MainActivity.kt` — state `appThemeStyleKey` (String, persisted) di-`remember` di
   `setContent{}` sejajar `useDynamicColor`, di-map ke `AppThemeStyle` enum lalu di-pass
   ke `AudioEnhancerTheme(themeStyle=...)` & `BoosterScreen(themeStyleIsRadical=...,
   onThemeStyleChange=...)`. Ganti tema = langsung recompose (tidak perlu restart Activity).
6. `PrefsHelper.kt` — `KEY_APP_THEME_STYLE` baru (String, "amoled_glass"/"radical_skeuo"),
   default `APP_THEME_AMOLED_GLASS` (user lama TIDAK berubah tampilannya kalau belum
   pernah sentuh switch baru). `KEY_THEME_MODE` (dead code sejak Batch 31, soal terang/
   gelap) TIDAK disentuh — ini fitur BEDA (2 sistem desain, app tetap dark-only).
7. String baru (ID+EN, parity dijaga 96/96): `theme_style_title`, `theme_style_desc`.
8. `PrefsHelperTest.kt` — 2 test baru (`app theme style defaults to amoled glass`,
   `app theme style round-trips through prefs`), pola sama persis test dynamic color
   yang sudah ada.
9. `app/build.gradle.kts`: versionCode 74→75, versionName 1.74→1.75.

**Batch Limit**: 8 file kode+test (`Theme.kt`, `SkeuomorphicComponents.kt`,
`BoosterScreen.kt`, `MainActivity.kt`, `PrefsHelper.kt`, `PrefsHelperTest.kt`,
`strings.xml` x2) + `build.gradle.kts` = 9 file — dalam batas 10 file/1 modul, TIDAK
perlu diklaim Atomic Change meski scope-nya lumayan luas (semua touch point saling
terkait sebagai 1 fitur utuh, gak bisa dipecah tanpa state setengah jadi).

**SENGAJA TIDAK dikerjakan di batch ini (transparan)**:
- `docs/preview/current.html` TIDAK disinkronkan ke tema Radical baru — mockup HTML
  statis butuh 1 varian visual baru penuh (bukan cuma ganti beberapa hex) buat
  merepresentasikan bevel-first card structure yang beda filosofi dari glass existing;
  effort-nya melebihi scope batch ini. **PENTING buat sesi depan**: HTML preview
  SEKARANG CUMA merepresentasikan tema AMOLED Glass (state ON switch baru TIDAK
  tervisualisasi di sana) — kalau user minta validasi visual tema Radical, JANGAN
  rujuk ke `current.html`, harus build APK asli atau bikin varian HTML terpisah.
- Icon/asset splash (`values/colors.xml`, `ic_launcher_background.xml`, dst) TIDAK
  ikut berubah — 2 tema ini cuma soal Compose runtime UI, splash screen (native,
  render sebelum Compose sempat jalan) TETAP 1 warna AMOLED Glass apapun switch-nya
  (guide baru TIDAK eksplisit minta splash ikut berubah, dan splash by design render
  duluan sebelum ada state buat tahu preferensi user).
- **Belum divalidasi runtime** — sama seperti batch-batch sebelumnya, sandbox Claude
  gak punya compiler. Kandidat pertama dicurigai kalau ada laporan render aneh: state
  `RADICAL_SKEUO` (API `CompositionLocalProvider` 3-value baru di `AudioEnhancerTheme`,
  `SkeuTokens` data class baru pertama kali dipakai di project ini).

## v1.74 - Batch 35: tutup technical debt Batch 34 — TextMuted dipakai + §15 Navigation dikonfirmasi N/A
User minta gabung semua item yang sempat disebutkan (belum wajib, opsional) jadi 1 batch
biar gak numpuk technical debt ke depan. 2 item dari closing note Batch 34:
1. **`TextMuted`** (`Theme.kt`, didefinisikan sejak Batch 34, 0 pemanggil) — sekarang
   dipakai di semua teks caption-tier (guide §16 hierarki: Display>Title>Section>Body>
   Secondary>Caption). `MaterialTheme.colorScheme.onSurfaceVariant` (= `TextSecondary`)
   DIPERTAHANKAN buat teks "Secondary" (supporting/body-level), diganti `TextMuted`
   HANYA di teks yang bener-bener caption-tier:
   - `SkeuomorphicComponents.kt`: `FeatureControl` helpText (bodySmall di bawah slider).
   - `BoosterScreen.kt`: desc power toggle (bodySmall di bawah label), hint preset kosong
     (bodySmall), char-count input nama preset custom, subtitle "Warna ikut wallpaper"
     & subtitle equalizer (2x pola title+bodySmall-desc), label chip preset UNSELECTED
     (2x — built-in & custom preset chips; guide §15 nav pattern "unselected item: muted
     text/icon" analog dipakai ke chip selection, konsisten sama semantik "muted").
   - **SENGAJA TIDAK diubah**: icon tint power button state OFF (`BoosterScreen.kt` baris
     144) — itu tint ikon (state visual), bukan teks, di luar scope hierarki tipografi
     §16. Tetap `onSurfaceVariant`.
2. **§15 Navigation** — dicek ulang, DIKONFIRMASI N/A: app ini gak punya bottom
   navigation/nav rail/tab bar (single-screen `BoosterScreen` + onboarding flow), jadi
   gak ada "selected/unselected nav item" buat diterapin. Didokumentasikan di sini biar
   gak dicek ulang sesi depan tanpa alasan.
- **`app/build.gradle.kts`**: versionCode 73→74, versionName 1.73→1.74.
- Tidak ada perubahan token/hex warna di batch ini — murni migrasi pemakaian token yang
  sudah ada (0 breaking change ke palet).

## v1.73 - Batch 34: KOREKSI Batch 33 — acuan sebelumnya salah upload
User bilang eksplisit "saya salah kirim" file acuan Batch 33
(`compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md`) dan upload
file yang BENAR: `compose-amoled-hybrid-glass-final.md` — "Premium AMOLED Hybrid
Glassmorphism + Subtle Midnight Blue + Micro-Skeuomorphism". Filosofi guide baru
GESER dari Batch 33: glass jadi material utama (bukan tactile/bevel-first), kartu
struktural wajib "glass surfaces first, not physical objects" (§14), skeuomorphism
turun jadi "micro" (hanya tactile control, bukan identitas visual kedua).
- **`Theme.kt`**: token warna diganti nama PERSIS ke guide baru (guide punya
  penamaan beda dari Batch 33 meski hex banyak yang sama): `GlassBase`/
  `GlassElevated`/`GlassPressed` (ganti `GlassSurface`/`GlassSurfaceElevated`/
  `GlassSurfacePressed`), `AmoledBlack #030508` + `AmoledSurface #070A0F` baru
  (2-tone root — `AmoledBlack` = splash/root sejati, `AmoledSurface` = canvas
  layar/`colorScheme.background`, biar glass Level 1+ tetap "perceptible" di
  atasnya sesuai guide §3 "Important"). `MidnightBlueAmbientAlpha` **0.06** (Batch
  33 pakai 0.08 — guide baru kasih angka eksplisit beda, guide baru menang karena
  ini instruksi yang benar). `TextMuted #737E8C` baru (hierarki tipografi §16).
  `SkeuPrimaryGlow` alpha diturunkan 0.28→0.22 (guide §18: "if glow becomes one of
  the first things users notice, reduce it").
- **`SkeuomorphicComponents.kt`**: semua referensi token lama diganti ke nama baru.
  `SkeuCard` elevation shadow 3dp→2dp (guide §14 "avoid heavy shadow" buat kartu
  struktural — sekarang restrained). `SkeuSliderThumb` di-desain ulang TOTAL:
  radial gradient putih→accent ala dial logam (Batch 31-33, "metallic realism")
  DICABUT — guide §13 eksplisit melarang ("Avoid metallic realism that conflicts
  with the glass aesthetic"). Diganti radial restrained: `GlassHighlight` di pusat
  (reflection tipis, BUKAN dial metalik) fading ke `GlassElevated` di-tint
  accentColor 30% (aktif)/8% (nonaktif) di tepi — functional color cue
  dipertahankan tanpa sheen metalik.
- **`widget_background.xml`**: `centerColor` dihitung ulang buat alpha 0.06 (bukan
  0.08) — `#0F1330`→`#0B101B` (mix `GlassElevated` + `MidnightBlue` 6%).
- **`docs/preview/current.html`**: `:root` CSS var di-kalibrasi ulang (`--bg
  #070A0F` = AmoledSurface bukan AmoledBlack lagi biar match `colorScheme.
  background` Kotlin, `--midnight-tint` alpha .08→.06, `--glow` alpha .28→.22,
  token `--highlight`/`--mutedmost` baru). **Bug ketemu & difix**: `.slider-thumb`
  CSS masih pakai `color-mix(...var(--accent) 65%, white)` (metallic, sama kayak
  Kotlin lama) DAN `.skeu-switch.on` border-color masih hardcode bronze lama
  `rgba(194,162,107,.6)` — DUA-duanya lolos dari sweep Batch 33 (miss, bukan
  disengaja), sekarang disamakan ke token baru.
- **`app/build.gradle.kts`**: versionCode 72→73, versionName 1.72→1.73.
- Feature accent colors (Bass/Virtualizer/Loudness/Equalizer/Battery) TETAP TIDAK
  diubah — sama seperti Batch 33, identitas per-fitur independen dari surface
  hierarchy guide.

## v1.72 - Batch 33: re-theme total ke "AMOLED Glassmorphism Hybrid + Midnight Blue Gradient"
User upload guide baru `compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md`
dan minta timpa theme lama sampai bersih, **wajib 100% sesuai** (bukan lagi "suggested
palette direction" seperti Batch 32 yang sengaja mempertahankan graphite `#232220`).
Semua token warna diganti total — struktur komponen (SkeuCard/SkeuPowerButton/
SkeuSliderThumb/SkeuSwitch) TETAP, cuma warnanya.
- **`Theme.kt`**: palet bronze/graphite (`PremiumBronzeDark #C2A26B`, `SkeuSurfaceTop/
  Bottom #2E2C29/#201F1D`) DICABUT TOTAL. Token baru persis sesuai guide §2:
  `AmoledBackground #030508`, `GlassSurface #0A0F16`, `GlassSurfaceElevated #101722`,
  `GlassSurfacePressed #070B11`, `MidnightBlueTint #191970` (alpha 0.08, HANYA dipakai
  di dalam gradient glass — guide §2.5 "felt as atmosphere, not the primary background
  color"), `MidnightBlueAccent #6670FF` (primary/state-aktif, gantikan bronze).
  `primary` color scheme = MidnightBlueAccent, `background`/`surface`/`surfaceVariant`
  = Amoled/Glass tokens. `SkeuPrimaryGlow` sekarang derivatif `MidnightBlueAccent`
  (bukan bronze lagi).
- **`SkeuomorphicComponents.kt`**: `SkeuCard`/`SkeuTintedCard` — background solid flat
  DICABUT, ganti `MidnightBlueGlassBrush` (gradient 3-stop AMOLED→midnight-tint-subtle→
  glass-elevated, sesuai contoh brush guide §2.5) — kartu struktural tetap "quiet"
  (guide §8) tapi materialnya sekarang frosted-glass wajib, bukan solid lagi. Referensi
  `SkeuSurfaceTop`/`SkeuSurfaceBottom` lama (slider thumb dial, switch thumb OFF) diganti
  `GlassSurfaceElevated`/`GlassSurface`.
- **`widget_background.xml`**: gradient bronze `#2B2620→#141210` diganti gradient 3-stop
  `#101722→#0F1330(midnight tint)→#030508`, angle 135° dipertahankan (konsisten arah
  cahaya top-left→bottom-right guide §3). RemoteViews gak dukung Compose Brush jadi
  Midnight Blue disimulasikan lewat `centerColor` XML gradient.
- **`values/colors.xml`**: `splash_background` terang `#F7F5F1` DICABUT (guide §1.1 +
  §13: "introduce a light-mode fallback" = dilarang) — disamakan persis dengan
  `values-night/colors.xml`, keduanya `#030508` (AmoledBackground). Tidak ada lagi
  varian terang di splash sama sekali.
- **`docs/preview/current.html`**: `:root` CSS vars re-theme total (`--bg #030508`,
  `--surface #0A0F16`, `--primary #6670FF`, `--glow rgba(102,112,255,.28)`, dst),
  `.card` background diganti gradient midnight-tint (sebelumnya solid `var(--surface)`),
  judul & header comment diperbarui ke nama guide baru.
- **`app/build.gradle.kts`**: versionCode 71→72, versionName 1.71→1.72.
- Feature accent colors (`BassAccent`/`VirtualizerAccent`/`LoudnessAccent`/
  `EqualizerAccent`/`BatteryAccent`) SENGAJA TIDAK diubah — itu identitas per-fitur,
  independen dari surface hierarchy AMOLED/Midnight Blue, guide gak melarang variasi
  hue di accent non-primary.

## v1.71 - Batch 32: terapkan gap dari update guide `compose-skeuomorphism-lite-dark.md`
User upload versi acuan design guide yang lebih detail (beda dari `compose-skeuomorphism-lite.md`
basis Batch 31), minta terapkan poin yang belum ada. Diff dilakukan poin-per-poin ke Definition
of Done guide + dibanding `docs/preview/current.html` (ground truth visual project ini, ternyata
sudah lebih maju dari Kotlin di 2 hal) — detail alasan lengkap tiap poin ada di `PROJECT_STATE.md`
Batch 32.
- **`SkeuomorphicComponents.kt`**: `Modifier.skeuGlow(color, spread)` baru (guide §9 "Glow
  Rules") — halo lembut native `Brush.radialGradient` via `drawBehind`, dipasang di
  `SkeuPowerButton` (state ON) & dibungkus `Box` di 2 loop chip preset built-in/custom
  (`BoosterScreen.kt`, saat `selected`). Token `SkeuPrimaryGlow` (Theme.kt, sejak Batch 31)
  sekarang BENERAN dipakai — sebelumnya didefinisikan tapi 0 pemanggil.
- **`SkeuomorphicComponents.kt`**: `SkeuSwitch` baru (guide §7 "Toggles / Switches") — ganti
  `Switch` Material3 bawaan polos (dipakai toggle "Warna ikut wallpaper", `BoosterScreen.kt`)
  yang sebelumnya 0 treatment tactile. Track pill (OFF muted/`surfaceVariant`, ON blend 35% ke
  accent), thumb bundar (OFF flat, ON solid+glow, PRESSED scale 0.88) — 2 cue state (posisi +
  warna) sesuai syarat a11y guide.
- **Dicek, TERNYATA BUKAN gap** (didokumentasikan biar gak diulang sesi depan): arah cahaya
  diagonal top-left→bottom-right (guide §3) — `Brush.linearGradient` tanpa `start`/`end`
  eksplisit sudah diagonal secara default di Compose. Background AMOLED near-black (guide
  Definition-of-Done) — TIDAK diubah, HTML preview (ground truth project ini) sengaja tetap
  pakai graphite `#232220`, guide cuma kasih "suggested palette direction" bukan hex wajib.
- **`docs/preview/current.html`**: `.skeu-switch` CSS baru + 1 card demo toggle (elemen ini
  sebelumnya gak pernah ada di mockup sama sekali), footer versi diperbarui.
- **`build.gradle.kts`** (app): versionCode 70→71, versionName 1.70→1.71.
- Tidak ada perubahan logic/state audio. **Belum diverifikasi CI/runtime** — `skeuGlow`/
  `SkeuSwitch` API pertama kali dipakai di project ini, kandidat pertama dicurigai kalau ada
  laporan glow ke-clip/gak nongol atau thumb switch salah posisi.

## v1.70 - Batch 31: PIVOT DESAIN TOTAL — Neumorphism dicabut, ganti Skeuomorphism-lite (WAJIB dark-mode)
User kirim acuan design guide `compose-skeuomorphism-lite.md` + instruksi eksplisit: hapus
semua jejak neumorphism, ganti total sesuai acuan, **WAJIB dark-mode**. Ini pivot STRUKTUR
penuh (bukan cuma palet), sama skalanya kayak pivot glassmorphism→Neumorphic Hybrid dulu
(Batch 12) — lihat "Riwayat pivot" poin 6 di PROJECT_STATE.md buat detail alasan tiap
perbedaan.
- **`NeumorphicComponents.kt` DIHAPUS**, diganti **`SkeuomorphicComponents.kt`** (file baru):
  - `neumorphicDepth()`/`neumorphicInnerShadow()` (dual custom-Paint shadow-layer, teknik inti
    neumorphism) **dihapus total**, tidak ada penggantinya yang setara — kedalaman sekarang
    dari pendekatan berbeda (lihat poin di bawah).
  - `NeumorphicCard`/`NeumorphicTintedCard` → `SkeuCard`/`SkeuTintedCard`: sesuai guide poin 3
    ("keep structural container cards flat and minimal"), kartu struktural SEKARANG FLAT —
    solid surface + border 1dp + `Modifier.shadow` kecil (3dp), BUKAN extruded dual-shadow lagi.
  - `NeumorphicCircleButton` → `SkeuPowerButton`: satu-satunya elemen "physical utility" bundar
    (guide poin 3) — dapat bevel gradient top-down (`SkeuBevelBrush`) + border emboss
    (`SkeuBevelBorderBrush`) + micro-interaction klik PERSIS snippet guide poin 2
    (`Modifier.scale` + `Modifier.shadow(elevation)` via `animateFloatAsState`/`animateDpAsState`),
    ganti total custom Paint shadow-layer.
  - `NeumorphicSliderThumb` → `SkeuSliderThumb`: radial gradient metalik (guide poin 3.2 —
    "crisp radial gradient resembling a tactile metallic dial"), ganti dual-shadow bundar lama.
  - `NoRippleIndication` dipertahankan apa adanya (utilitas UI generik, bukan neumorphism-specific).
- **`Theme.kt`**: `LightColors` + parameter `darkTheme` di `AudioEnhancerTheme()` **dihapus** —
  app WAJIB dark (`DarkColors` satu-satunya scheme, `LocalIsDarkTheme` sekarang selalu `true`).
  Token neumorphic lama (`NeuShadowDarkSide`, `NeuShadowLightSideDark/Light`, `NeuShadowDarkSideLight`,
  `NeuCardRadius`, dst) dihapus, diganti token Skeuomorphism-lite: `SkeuSurfaceTop/Bottom`,
  `SkeuBevelHighlight/Shadow`, `SkeuPrimaryGlow` (pengganti `Color.White` alpha sesuai guide
  "Dark Mode Adaptation" — highlight terang diganti glow warna primary tipis), `SkeuCardRadius`,
  `SkeuIconBoxRadius`, `SkeuBevelBrush`/`SkeuBevelBorderBrush`.
- **`MainActivity.kt`** (edit parsial — protected asset): `themeMode`/`isSystemInDarkTheme`
  branching dihapus, `AudioEnhancerTheme()` dipanggil tanpa `darkTheme` (selalu dark).
  Status bar/nav bar icon di-set gelap sekali (tidak perlu `SideEffect` resync lagi karena
  tema tidak lagi bisa berubah runtime). Param `themeMode`/`onThemeModeChange` dihapus dari
  pemanggilan `BoosterScreen(...)`.
- **`BoosterScreen.kt`**: semua referensi `Neumorphic*` di-rename ke `Skeu*`. Composable
  `ThemeModeToggle` (ikon Terang/Gelap/Ikuti-Sistem) **dihapus total** dari header — tidak ada
  lagi pilihan tema di UI, konsisten dengan WAJIB dark-mode. Import icon `LightMode`/`DarkMode`/
  `Brightness4` dihapus (sudah tidak dipakai).
- **`strings.xml`** (`values/` id + `values-en/`): 3 string unreferenced dihapus —
  `theme_desc_light`, `theme_desc_dark`, `theme_desc_system` (dulu dipakai `ThemeModeToggle`).
- **`docs/preview/current.html`**: mockup diupdate penuh ke Skeuomorphism-lite dark-mode — CSS
  `box-shadow` dual-tone neumorphic diganti `linear-gradient` bevel (power button) +
  `radial-gradient` metalik (slider thumb) + kartu flat (`border` tipis, shadow kecil).
- **`PrefsHelper.kt`**: **TIDAK diubah** — `getThemeMode`/`setThemeMode`/`THEME_MODE_*` sengaja
  dibiarkan sebagai dead code (bukan dipanggil lagi dari UI manapun) supaya `PrefsHelperTest.kt`
  tidak perlu ikut diubah di batch ini (minimasi resiko regresi test, bukan lupa).
- **`app/build.gradle.kts`** (edit parsial — protected asset): versionCode 69→70, versionName
  1.69→1.70.
- Tidak ada perubahan logic/state audio (Bass/Virtualizer/Loudness/Equalizer) — murni pivot
  visual/tema. **Belum diverifikasi CI/runtime** — rekomendasi: cek render power button
  (bevel+ring saat ON), slider knob (radial gradient), dan pastikan tidak ada crash
  `Unresolved reference` sisa `Neumorphic*`/`darkTheme`/`themeMode` di titik lain.

## v1.69 - Batch 30: empty state hint preset custom (polish UI/UX kecil, lanjutan tema Batch 26)
User konfirmasi crash-loop v1.67 SUDAH gak muncul lagi di v1.68 (screenshot nunjukin banner
cuma nampilin crash LAMA dari sebelum update, app jalan normal "Aktif"). Lanjut "Next" — audit
item pending dari daftar Medium ("empty/error state") di PROJECT_STATE.md: user baru yang
belum pernah simpan preset custom cuma lihat 4 chip bawaan + 1 chip "Simpan" polos tanpa
konteks apapun soal APA yang disimpan/KAPAN berguna.
- **`BoosterScreen.kt`**: hint 1 baris (`presets_empty_hint`) ditambahkan di bawah Row chip
  preset, tapi CUMA render kalau `customPresets.isEmpty()` — begitu user nyimpen preset
  custom pertama, hint otomatis hilang selamanya (gak numpuk jadi noise permanen tiap buka
  app). Style `bodySmall` + warna `onSurfaceVariant`, konsisten sama hint/caption lain di app
  (gak ada style baru yang nyempil beda).
- **String baru** (ID+EN, parity 97/97): `presets_empty_hint`.
- Tidak ada perubahan state/behavior fungsional — murni penambahan 1 Text kondisional.
- **`build.gradle.kts`** (app): versionCode 68→69, versionName 1.68→1.69.
- **Belum diverifikasi runtime.**

## v1.68 - Batch 29: hotfix CRASH RUNTIME v1.67 ("Invalid token LIMIT", app crash-loop startup)
User upload crash log dari device asli (Infinix, Android 16/SDK 36) — SANGAT PENTING:
CrashLogger v1.66/v1.67 TERBUKTI BEKERJA (metadata Version/OS/Model/Timestamp/Thread lengkap
kebaca), tapi isinya jadi bug BARU yang lebih parah: `java.lang.IllegalArgumentException:
Invalid token LIMIT` dari `ContentResolver.query`, terjadi SINKRON saat `dispatchAttachedToWindow`
(startup app, Compose attach) — **app crash total di setiap buka**, bukan cuma gagal baca log.
- **Root cause**: `latestFromMediaStore()` (dipanggil `CrashBanner` di inisialisasi state,
  LANGSUNG saat composition, DI LUAR try-catch `install()` yang cuma lindungi jalur TULIS)
  pakai `sortOrder = "$DATE_MODIFIED DESC LIMIT 1"` — nempelin klausa SQL `LIMIT` mentah ke
  parameter `sortOrder` itu TRIK, bukan API resmi, cuma "kebetulan" diterima ContentProvider
  AOSP standar. ContentProvider OEM tertentu (kejadian nyata: MediaProvider Infinix, Android
  16) validasi `sortOrder`-nya lebih ketat dan NOLAK token `LIMIT`, throw exception yang TIDAK
  ketangkep di mana pun (crash sampai ke `ActivityThread.main`).
- **Fix ganda** (defense-in-depth, BUKAN cuma 1 lapis):
  1. `sortOrder` dikembalikan jadi `"$DATE_MODIFIED DESC"` polos (hapus `LIMIT 1`) — cukup
     `cursor.moveToFirst()` dari hasil DESC, gak butuh LIMIT di level query (data maks 50
     baris karena retensi FIFO Batch 27, query tanpa LIMIT tetap murah).
  2. `latestCrashLog()` (dan turunannya, `latestFromMediaStore`/`latestFromLegacy` via
     panggilan itu) SEKARANG dibungkus `runCatching { }.getOrNull()` di level PALING LUAR —
     supaya kalau ada lagi ContentProvider OEM manapun yang nolak query dengan cara TAK
     TERDUGA lain di masa depan, app TIDAK ikut crash, cuma banner crash gak muncul (gagal
     aman, bukan gagal total). `hasUnseenCrash()`/`markCrashSeen()` otomatis ikut terlindungi
     (keduanya manggil `latestCrashLog()`). `deleteAllLogs()` (dipanggil tombol UI, sama-sama
     di luar try-catch `install()`) juga dibungkus `runCatching` terpisah.
- **LESSON buat sesi depan (WAJIB baca sebelum sentuh query MediaStore/ContentResolver lagi)**:
  (a) JANGAN PERNAH nempelin klausa SQL bebas (`LIMIT`, dst) ke parameter `sortOrder`
  `ContentResolver.query()` — itu bukan kontrak resmi API-nya, perilakunya BEDA-BEDA antar
  ContentProvider (AOSP vs OEM vs versi Android). Kalau butuh batasi jumlah baris, filter di
  sisi Kotlin (`moveToFirst()` doang, atau `Bundle` args `ContentResolver.QUERY_ARG_LIMIT` di
  API 30+ — TAPI project ini minSdk 24, jadi opsi itu pun gak portable ke semua target). (b)
  Fungsi APAPUN yang manggil `ContentResolver`/API sistem lain dari jalur BACA (bukan cuma
  tulis) yang dipanggil LANGSUNG dari inisialisasi Composable state — WAJIB dibungkus
  `runCatching` sendiri, JANGAN asumsikan try-catch di `install()` (yang cuma proteksi jalur
  tulis crash handler) otomatis melindungi jalur baca juga — 2 jalur yang terpisah total.
- **`build.gradle.kts`** (app): versionCode 67→68, versionName 1.67→1.68.
- Tidak ada perubahan string/UI. 1 file Kotlin (`CrashLogger.kt`).
- **Belum diverifikasi ulang di device Infinix yang sama** — user perlu install v1.68 &
  konfirmasi app gak crash lagi di startup. Kandidat pertama dicurigai kalau MASIH crash:
  cek apakah ada ContentProvider OEM lain yang juga nolak `selection`/`args` cara kita query
  (`RELATIVE_PATH = ?`) — belum pernah dilaporkan gagal, tapi sekarang minimal app gak akan
  ikut crash total karena sudah dibungkus `runCatching`.

## v1.67 - Batch 28: hotfix CI v1.66 (compile error, const val non-constant initializer)
User upload log run #72 — `compileDebugKotlin FAILED`. Root cause: `CrashLogger.kt:36`,
`private const val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/logs/"`
— `Environment.DIRECTORY_DOCUMENTS` itu field runtime Android (String biasa), BUKAN
compile-time constant Kotlin. `const val` WAJIB nilai yang bisa di-resolve compiler saat
kompilasi (literal atau `const` lain) — compiler nolak dengan pesan persis "Const 'val'
initializer should be a constant value". Ini murni typo kebiasaan (semua konstanta lain di
file itu memang literal, kebawa reflex pakai `const` tanpa sadar baris ini beda), BUKAN soal
Gradle/CI infra (wrapper bootstrap di log ini SUKSES).
- **`CrashLogger.kt`**: `private const val RELATIVE_PATH` → `private val RELATIVE_PATH`
  (hapus `const` doang, isi/logic TIDAK berubah sama sekali).
- Audit ikutan: grep semua `const val` di project (17 titik lain) — SEMUA nilainya literal
  murni (String/Int hardcode), TIDAK ada yang match pola sama seperti `RELATIVE_PATH`. Aman.
- **`build.gradle.kts`** (app): versionCode 66→67, versionName 1.66→1.67.
- **PENTING buat sesi depan**: kalau bikin `const val` baru yang nilainya diambil dari API
  Android (`Environment.*`, `Build.*`, dll, bukan literal string/angka manual), JANGAN pakai
  `const` — pakai `val` polos. `const` di Kotlin cuma valid buat primitif/String yang
  benar-benar bisa di-resolve compiler tanpa runtime, bukan field constant dari library luar.
- **Belum diverifikasi runtime** — HARUS dicek run CI berikutnya.

## v1.66 - Batch 27: CrashLogger MediaStore (standing spec) + debugging/robustness
User konfirmasi CI v1.65 HIJAU (Release v1.65 sukses, body dinamis dari CHANGELOG sudah
tampil, bukan link compare kosong lagi). Lanjut "Next" — audit ketidaksesuaian `CrashLogger.kt`
terhadap standing spec crash logger user (MediaStore API 29+, `Documents/<App>/logs/`, TANPA
permission legacy, FIFO retention 50, metadata lengkap Version/OS/Model/Timestamp/Thread/
StackTrace, fail-safe). Implementasi SEBELUMNYA (`filesDir/crash_logs/` internal, rotasi 5,
cuma isi stack trace polos tanpa metadata) TIDAK PERNAH match spec ini sejak awal project —
gap nyata, bukan regresi baru.
- **`CrashLogger.kt`** (rewrite penuh, Atomic Change — 1 file tapi banyak titik saling
  bergantung, gak bisa dipecah batch tanpa compiler buat verifikasi konsistensi):
  1. **API 29+ (Q)**: tulis via `ContentResolver.insert()` ke `MediaStore.Files` dengan
     `RELATIVE_PATH = Documents/AudioEnhancerPro/logs/` — TANPA `WRITE_EXTERNAL_STORAGE` atau
     permission storage apapun (scoped storage, terverifikasi: `AndroidManifest.xml` TIDAK
     disentuh, nol permission baru). File langsung terlihat dari File Manager/Files by Google
     mana pun, bukan cuma bisa dilihat lewat dialog in-app kayak sebelumnya.
  2. **API 24-28 (di bawah Q, `MediaStore.Files.RELATIVE_PATH` belum ada)**: fallback OTOMATIS
     ke `filesDir/crash_logs/` (perilaku lama) — tetap tanpa permission apapun, cuma gak nongol
     di File Manager (keterbatasan versi Android, minSdk project ini 24).
  3. **Metadata lengkap** ditambahkan di header tiap file log (sebelumnya CUMA stack trace
     polos): `Version` (dari `PackageManager`), `OS` (`Build.VERSION.RELEASE`+SDK int), `Model`
     (`Build.MANUFACTURER`+`Build.MODEL`), `Timestamp` (presisi milidetik), `Thread` (nama
     thread yang crash) — baru diikuti `StackTrace` di bawah separator `---`.
  4. **FIFO retention naik dari 5 → 50 file** (query+prune MediaStore via `DATE_ADDED DESC`
     + drop di luar 50 pertama, fallback legacy pakai pola `sortedByDescending` yang sama
     seperti sebelumnya cuma angkanya disamakan ke 50).
  5. **Fail-safe TIDAK berubah** (tetap try-catch penuh di `install()`, logger gagal nulis TIDAK
     PERNAH menelan/mencegah crash asli — `previousHandler?.uncaughtException()` tetap selalu
     dipanggil di akhir, apapun hasil `writeCrashLog`).
  6. **Abstraksi baru**: `CrashLogEntry` (data class internal) menyatukan 2 sumber (MediaStore
     `Uri` di API 29+, `File` legacy di API lama) di balik 1 tipe yang sama — `readText(context)`
     & `lastModifiedMillis` seragam, pemanggil (`CrashBanner`) gak perlu tahu sumbernya dari
     mana. `latestCrashLog()`/`hasUnseenCrash()`/`markCrashSeen()`/`deleteAllLogs()` semua
     dirombak ikut abstraksi baru ini, TAPI signature publiknya (nama fungsi, jumlah/tipe
     parameter selain return type `latestCrashLog`) sengaja TETAP SAMA — minim blast radius ke
     pemanggil.
  - **PENTING (unit konversi waktu, WAJIB dibaca sebelum sentuh ulang file ini)**:
    `MediaStore.MediaColumns.DATE_MODIFIED`/`DATE_ADDED` tersimpan dalam **DETIK** (Unix epoch),
    BUKAN milidetik seperti `File.lastModified()` — kalau dibandingkan langsung ke
    `PrefsHelper.getLastSeenCrashTimestamp()` (yang isinya milidetik dari jalur legacy lama)
    tanpa dikali 1000, `hasUnseenCrash()` akan SELALU true tiap buka app (banner gak pernah
    hilang meski udah "dilihat"). Sudah di-fix (`dateModifiedSeconds * 1000` di
    `latestFromMediaStore()`) — JANGAN dihapus konversi ini kalau refactor lagi.
- **`BoosterScreen.kt`** (`CrashBanner`, penyesuaian minimal ikutan rewrite di atas):
  `var crashFile: File?` → `var crashEntry: CrashLogger.CrashLogEntry?`, `file.readText()` →
  `entry.readText(context)` (butuh `context` sekarang, karena `Uri` di API 29+ perlu
  `ContentResolver` buat dibaca, beda dari `File` polos yang bisa baca dirinya sendiri).
  TIDAK ADA perubahan lain di composable ini — dialog, tombol hapus/tutup, semua behavior
  visual/UX persis sama seperti sebelumnya.
- Tidak ada string baru (nol perubahan `strings.xml`, parity ID/EN TETAP 96/96).
- **Belum divalidasi runtime SAMA SEKALI** — ini area BARU yang belum pernah disentuh
  sandbox Claude sebelumnya (ContentResolver/MediaStore API), confidence diturunkan
  eksplisit di report. Kandidat pertama dicurigai kalau ada laporan "crash banner gak
  pernah muncul lagi" atau "log gak ketemu di Files by Google": cek dulu apakah device user
  API 29+ (harusnya MediaStore) atau di bawahnya (harusnya fallback filesDir, gak akan
  nongol di File Manager, itu memang expected).

## v1.65 - Batch 26: release notes dinamis + polish kecil (char limit preset, haptic konsisten)
User konfirmasi CI v1.64 HIJAU (Release v1.64 sukses publish, APK signed muncul di sidebar
Releases — dikonfirmasi via screenshot). 2 permintaan: (1) ganti body GitHub Release yang
sebelumnya cuma link "Full Changelog: v1.62...v1.64" kosong, jadi info dinamis per-rilis;
(2) fokus debugging/optimasi + polish UI/UX + detail kecil buat kemudahan user.
- **`.github/workflows/build.yml`**: step baru "Extract changelog entry for this version"
  (job `release`) — `awk` ambil isi entry `CHANGELOG.md` versi yang lagi dirilis (dari
  heading `## v<versi> - <judul>` sampai sebelum heading `## v` berikutnya), ditulis ke
  `release_notes.md`. `Publish GitHub Release` sekarang pakai `body_path: release_notes.md`
  (bukan `generate_release_notes: true` lagi) — orang yang buka halaman Release langsung baca
  ringkasan perubahan sungguhan dari CHANGELOG, bukan cuma link compare mentah tanpa isi.
  Fallback aman: kalau versionName gak ketemu entry-nya di CHANGELOG (lupa update), body jadi
  placeholder 1 baris (bukan bikin step gagal).
- **`BoosterScreen.kt`** (polish kecil, murni UX, tidak ada perubahan state/logic inti):
  1. Nama custom preset sekarang dibatasi **24 karakter** (`PRESET_NAME_MAX_LENGTH`) + counter
     "x/24 karakter" real-time di dialog simpan preset (gantiin supporting text kosong kalau
     gak ada error). Alasan: nama kepanjangan sebelumnya bisa bikin chip preset (scroll
     horizontal) meluber & dynamic shortcut (label ikon launcher) ke-truncate paksa sistem
     tanpa peringatan.
  2. Haptic feedback (`HapticFeedbackType.LongPress`, konsisten sama tombol lain di app)
     ditambahkan ke 5 titik yang SEBELUMNYA kelewat: tombol "Mulai Ulang" (badge service mati),
     tombol "Coba Lagi" (connection error), tombol buka pengaturan notifikasi, confirm simpan
     preset, confirm hapus preset. Sebelumnya tombol-tombol ini "senyap" (gak ada feedback taktil
     sama sekali) walau hampir semua interaksi lain di app sudah konsisten pakai haptic.
- **String baru** (ID+EN, parity dijaga 96/96): `preset_save_char_count` ("%1$d/%2$d
  karakter" / "%1$d/%2$d characters").
- Tidak ada perubahan state/behavior fungsional inti — murni penyempurnaan detail kecil +
  infra CI. 1 file Kotlin, 2 file strings.xml, 1 file workflow.
- **Belum diverifikasi runtime** — HARUS dicek run CI berikutnya (compile) DAN halaman
  Release v1.65 (isi body sesuai entry ini, bukan lagi link compare kosong).

## v1.64 - Batch 25: hotfix CI v1.63 (compile error, LocalIndication non-null)
User upload log run #69 — `compileDebugKotlin FAILED`, 4 error di baris yang SAMA persis
dengan 4 titik `CompositionLocalProvider(LocalIndication provides null)` dari Batch 24.
Root cause: `LocalIndication` di compose-foundation versi project ini (compose-bom
2024.06.00) bertipe `CompositionLocal<Indication>` **NON-NULL** — `provides null` gagal
compile (`Null can not be a value of a non-null type Indication`). BEDA total dari
asumsi Batch 24 (banyak contoh/tutorial online pakai versi compose-foundation lama yang
nullable) — bukan soal Gradle/CI infra (wrapper bootstrap di log ini SUKSES lagi).
- **`NeumorphicComponents.kt`**: `internal object NoRippleIndication : Indication` baru —
  no-op `IndicationInstance` (`drawIndication()` cuma `drawContent()`, gak gambar apapun
  ekstra), efek visual identik "ripple hilang" tapi valid secara tipe. Ditaruh di file
  ini (bukan `BoosterScreen.kt`) karena reusable/shared component, konsisten sama pola
  file ini.
- **`BoosterScreen.kt`**: 4 titik `provides null` → `provides NoRippleIndication`.
  `NeumorphicCircleButton` (`.clickable(indication = null, ...)`, Batch 15) TIDAK
  disentuh — itu parameter `indication` di modifier `clickable()` yang MEMANG nullable
  (`Indication?`), API BEDA dari `LocalIndication` CompositionLocal, gak kena masalah
  yang sama.
- **`build.gradle.kts`** (app): versionCode 63→64, versionName 1.63→1.64.
- **PENTING buat sesi depan**: kalau butuh matikan ripple/indication lagi di tempat lain,
  pakai `NoRippleIndication` yang sudah ada (`CompositionLocalProvider(LocalIndication
  provides NoRippleIndication)`), JANGAN pakai `provides null` lagi — sudah terbukti
  gagal compile di versi compose-foundation project ini.
- **Belum diverifikasi runtime** — HARUS dicek run CI berikutnya.

## v1.63 - Batch 24: ripple removal Material3 default (Button/FilterChip/AssistChip)
User konfirmasi v1.62 CI hijau, lanjut "Next" — item terakhir dari spec design system
yang belum dikerjakan (dicatat pending sejak Batch 15): ripple Material3 bawaan di
`Button`/`FilterChip`/`AssistChip` dimatikan, konsisten sama `NeumorphicCircleButton`
(sudah `indication = null` sejak Batch 15) — biar SELURUH komponen interaktif app pakai
1 bahasa feedback (dual-shadow/scale neumorphic), bukan campur ripple Material default +
custom feedback.
- **`BoosterScreen.kt`**: 4 titik dibungkus `CompositionLocalProvider(LocalIndication
  provides null)`: `Button` restart service (badge), `Button` retry connection, `Button`
  buka notification settings, dan 1 `Row` yang isinya SEMUA preset chip (built-in +
  custom `FilterChip`, + `AssistChip` simpan preset) — dibungkus sekali di level Row
  daripada 1-1 per chip, biar gak numpuk boilerplate untuk item dalam `forEach`.
  +import `androidx.compose.foundation.LocalIndication`.
- **TIDAK disentuh** (di luar scope spec eksplisit user): `TextButton`, `IconButton`,
  `OutlinedButton` — spec cuma sebut `Button`/`FilterChip`/`AssistChip` 3 nama itu,
  bukan semua komponen clickable Material3. Kalau user mau diperluas, minta konfirmasi
  eksplisit dulu (Strict Delete/Change Guard — jangan extend scope tanpa diminta).
- **`build.gradle.kts`** (app): versionCode 62→63, versionName 1.62→1.63.
- Tidak ada perubahan state/logic — murni visual/interaksi, 1 file Kotlin.
- **Belum diverifikasi runtime** — kandidat pertama dicurigai kalau ada laporan "tombol
  kerasa gak responsif" (ripple hilang bisa terasa aneh buat sebagian user meski
  desainnya sengaja).

## v1.62 - Batch 23: hotfix CI v1.61 (compile error, Slider experimental API)
User upload log run #67 — `compileDebugKotlin FAILED`. Root cause: API `thumb=`/`track=`/
`SliderState` yang dipakai Batch 22 masih `@ExperimentalMaterial3Api` di material3 1.2.1 —
Kotlin compiler treat opt-in annotation ini sebagai **ERROR** (bukan cuma warning) kalau
dipakai tanpa `@OptIn` eksplisit, bukan soal Gradle/CI infra sama sekali (wrapper bootstrap
di log ini SUKSES — beda root cause total dari Batch 19-21).
- **`NeumorphicComponents.kt`**: tambah `@OptIn(ExperimentalMaterial3Api::class)` di
  `NeumorphicSliderTrack` (pakai `SliderState`) dan `FeatureControl` (pakai overload
  `thumb=`/`track=`). Scope kecil (2 fungsi), BUKAN ubah compiler flag global — biar
  opt-in experimental API ini gak diam-diam nutupin API experimental lain yang mungkin
  kepake gak sengaja di masa depan.
- **`build.gradle.kts`** (app): versionCode 61→62, versionName 1.61→1.62.
- **PENTING buat sesi depan**: kalau nambah API Compose baru yang ditandai
  `@ExperimentalXxxApi` (Material3, Foundation, dll), WAJIB cek dulu apakah perlu
  `@OptIn` — sandbox Claude gak bisa compile-check, jadi typo/skip opt-in kayak ini BARU
  ketauan pas CI jalan. Kandidat generalisasi: kalau butuh sering pakai API experimental
  material3, pertimbangkan `@OptIn` di level file (`@file:OptIn(...)`) daripada per-fungsi
  — belum dilakukan di batch ini karena baru 2 fungsi yang kena.
- **Belum diverifikasi runtime** — HARUS dicek run CI berikutnya sebelum dianggap tuntas.

## v1.61 - Batch 22: Slider custom (CI v1.60 CONFIRMED HIJAU)
User konfirmasi build v1.60 lolos CI penuh — root cause Gradle wrapper bootstrap (Batch
19-21) FINAL selesai, tidak perlu opsi cadangan (commit wrapper manual). Sesi ini mulai
fase "polish, debugging, eksekusi sampai matang" — item pertama: item pending design
system terakhir yang belum di-port, **Slider custom** (spec: track 10dp, thumb 22dp +
shadow + ring).
- **`NeumorphicComponents.kt`**: `Slider` di `FeatureControl` ganti dari
  `SliderDefaults.colors()` polos ke overload `thumb=`/`track=` (material3 1.2+, aman di
  compose-bom 2024.06.00). 2 composable baru: `NeumorphicSliderTrack` (Box dual-layer,
  height 10dp, rounded 5dp, active-width dari `SliderState.value` fraction) dan
  `NeumorphicSliderThumb` (22dp circle, `neumorphicDepth()` yang SAMA dipakai
  NeumorphicCard/NeumorphicCircleButton — dual-shadow konsisten, bukan reimplementasi
  terpisah — + ring border 2dp warna aksen fitur).
- Tidak ada perubahan file lain — murni 1 file Kotlin, visual-only, tidak ada perubahan
  state/logic.
- **PENTING buat sesi depan**: item design system pending YANG TERSISA:
  `drawWithCache` optimasi (belum urgent, cuma manfaat di list besar/animasi berat,
  BUKAN static card — app ini gak punya itu), ripple removal Material3 default
  (`Button`/`FilterChip`/`AssistChip` di `BoosterScreen.kt` masih pakai ripple bawaan,
  cuma `NeumorphicCircleButton` yang sudah dimatikan sejak Batch 15). Radius "Phone:
  44dp" tetap diabaikan (dikonfirmasi ulang, gak ada elemen UI yang jadi target).
- **Belum divalidasi runtime** — statis only (brace/paren balance). API `thumb=`/`track=`
  Slider TIDAK PERNAH dipakai sebelumnya di project ini, jadi ini kandidat pertama
  dicurigai kalau ada laporan render aneh (slider gak muncul/salah posisi) setelah update.

## v1.60 - Batch 21: fix LANJUTAN CI lagi (v1.59 belum tuntas)
User upload log run #65 — penamaan artifact sudah benar (konfirmasi fix Batch 20 soal
itu berhasil). Progress: isolasi direktori scratch (Batch 20) BERHASIL menghindari
Gradle sistem mengevaluasi project kita. Tapi ketemu lapisan masalah baru: Gradle 9.6.1
ternyata WAJIB direktori tempat `gradle wrapper` dijalankan punya file settings dulu
("Directory does not contain a Gradle build"), beda dari versi Gradle lama yang bisa
bootstrap di direktori kosong tanpa settings apapun.
- **`.github/workflows/build.yml`**: tambah 1 baris `echo` yang bikin
  `settings.gradle.kts` KOSONG di direktori scratch, tepat sebelum
  `gradle wrapper --gradle-version 8.7` dipanggil di sana (kedua job).
- **`build.gradle.kts`** (app): versionCode 59→60, versionName 1.59→1.60.
- Tidak ada perubahan kode Kotlin — murni infra CI (lanjutan v1.58/v1.59).
- **Kalau masih gagal lagi setelah ini**: lihat `PROJECT_STATE.md` Batch 21 — opsi
  cadangan (commit langsung file wrapper dari mesin dev, bukan generate di CI) sudah
  dicatat di sana.

## v1.59 - Batch 20: fix LANJUTAN CI (v1.58 belum tuntas) + 3 bug penamaan artifact
User upload log kegagalan run #64. Detail lengkap kenapa v1.58 belum cukup ada di
`PROJECT_STATE.md` Batch 20 — ringkas: `gradle wrapper` (bahkan dengan versi eksplisit)
TETAP mengevaluasi seluruh project pakai Gradle sistem runner (9.6.1) sebelum sempat
generate wrapper-nya sendiri.
- **`.github/workflows/build.yml`**:
  - Fix akar masalah: generate wrapper di **direktori kosong terpisah** (`mktemp -d`),
    baru salin 4 file hasilnya ke root project — Gradle sistem 9.6.1 gak pernah
    menyentuh `build.gradle.kts` project sama sekali sekarang.
  - Fix bug penamaan: step "Extract version name" dipindah ke **paling awal** (sebelum
    step apapun yang bisa gagal) di kedua job, dan di job `release` jadi
    **unconditional** (sebelumnya nunggu `has_secret==true`).
  - Fix kondisi upload log release: `if: failure()` saja (sebelumnya juga cek
    `has_secret==true`, yang bisa kosong kalau step sebelumnya gagal duluan).
- **`build.gradle.kts`** (app): versionCode 58→59, versionName 1.58→1.59.
- Tidak ada perubahan kode Kotlin — murni infra CI (lanjutan v1.58).

## v1.58 - Batch 19: fix root cause CI gagal (v1.57) + artifact log_fail_v*
User upload log Actions run yang gagal. Root cause **BUKAN Hilt** — detail lengkap di
`PROJECT_STATE.md` Batch 19 (WAJIB baca kalau bingung kenapa v1.57 dilabeli ulang jadi
"belum teruji" alih-alih "gagal").
- **`.github/workflows/build.yml`**:
  - Root cause fix: `gradle wrapper` → `gradle wrapper --gradle-version 8.7` (pin
    eksplisit, sebelumnya ikut versi Gradle bawaan runner GitHub yang naik ke 9.6.1 dan
    gak kompatibel Kotlin Gradle Plugin 1.9.24 project ini — gagal di tahap konfigurasi
    project, sebelum kode Kotlin manapun sempat dikompilasi).
  - Fitur diminta user: artifact `log_fail_v<versi>-<debug|release>-run<N>`, otomatis
    ke-upload cuma kalau step build gagal (`if: failure()`), isi = full output
    `--stacktrace` + `**/build/reports/**`.
- **`build.gradle.kts`** (app): versionCode 57→58, versionName 1.57→1.58.
- Tidak ada perubahan kode Kotlin — murni infra CI.

## v1.57 - Batch 18 (penutup audit High): Hilt DI — ⚠️ RISIKO PALING TINGGI
Detail risiko + rencana recovery kalau CI gagal ada di `PROJECT_STATE.md` Batch 18 —
WAJIB dibaca sebelum lanjut kalau build error. Batch ini nyentuh build system
(plugin/annotation-processing), BUKAN cuma kode Kotlin — sandbox Claude gak bisa
verifikasi resolusi Gradle plugin sama sekali.
- **`build.gradle.kts`** (root): +plugin `com.google.dagger.hilt.android` v2.51.1.
- **`app/build.gradle.kts`**: +plugin `kotlin.kapt` + `hilt.android`, +dependency
  `hilt-android`+`hilt-android-compiler` (v2.51.1), +block `kapt { correctErrorTypes = true }`.
- **`AudioEnhancerApp.kt`**: `@HiltAndroidApp`.
- **`MainActivity.kt`**: `@AndroidEntryPoint`.
- **`BoosterViewModel.kt`**: `@HiltViewModel`, constructor jadi `@Inject constructor(application: Application)`.
- **`build.gradle.kts`** (app): versionCode 56→57, versionName 1.56→1.57.
- Belum ada `@Module`/`@InstallIn` — cuma `Application` yang di-inject (binding bawaan
  Hilt), belum ada binding custom lain.

## v1.56 - Batch 17 (lanjutan audit High #2): ekstraksi ViewModel
Detail lengkap + alasan perubahan perilaku Context bindService ada di `PROJECT_STATE.md`
Batch 17 (Hilt DI & item Medium/Low audit MASIH PENDING, belum dikerjakan batch ini).
- **`BoosterViewModel.kt`** (baru): `AndroidViewModel` polos (belum pakai Hilt/Koin).
  Nampung `connectionState` (+ enum `ConnectionState`, pindah dari `MainActivity`),
  status dukungan fitur (bass/virtualizer/loudness/equalizer), `ServiceConnection`,
  4 buffer pending, dan fungsi `startBoosterService()`/`attemptBindService()`/
  `setBass()`/`setVirtualizer()`/`setLoudness()`/`setEqualizerBand()`.
- **`MainActivity.kt`**: 318→226 baris. Sekarang cuma pegang `viewModel: BoosterViewModel
  by viewModels()` + state yang inheren Activity-only (notification permission, shortcut
  preset name). Override `onDestroy()` dihapus (unbind sekarang di
  `BoosterViewModel.onCleared()`).
- **`BoosterScreen.kt`**: parameter `connectionState` ganti tipe dari
  `MainActivity.ConnectionState` ke `BoosterViewModel.ConnectionState` (4 titik).
- **`build.gradle.kts`**: tambah 3 dependency (`activity-ktx`, `lifecycle-viewmodel-ktx`,
  `lifecycle-viewmodel-compose`), versionCode 55→56, versionName 1.55→1.56.
- **PERUBAHAN PERILAKU (didisclose)**: bindService/unbindService sekarang pakai
  Application Context (lewat `AndroidViewModel.getApplication()`), bukan Activity
  Context — alasan & dampak praktis dijelaskan di `PROJECT_STATE.md`.

## v1.55 - Batch 16 (audit dari user): God Activity split (MainActivity.kt → 3 file)
User kirim audit checklist (High/Medium/Low), serahkan prioritas ke Claude. Detail
keputusan+alasan lengkap ada di `PROJECT_STATE.md` Batch 16 (baca itu dulu kalau lanjut
kerjain sisa audit — MVVM/DI/UI polish BELUM dikerjakan, sengaja ditunda).
- **`MainActivity.kt`**: 1421→318 baris. Sekarang cuma Activity class (lifecycle, service
  binding, permission launcher, `BoosterScreen()` call).
- **`BoosterScreen.kt`** (baru): `BoosterScreen`, `ServiceStatusBadge`, `PowerToggleRow`,
  `CrashBanner`, `ThemeModeToggle`, `EqualizerSection`, `Preset`, `formatFreqLabel`.
- **`NeumorphicComponents.kt`** (baru): `NeumorphicCard`, `NeumorphicTintedCard`,
  `NeumorphicCircleButton`, `SectionLabel`, `FeatureControl`, `neumorphicDepth()`,
  `neumorphicInnerShadow()`.
- 5 composable (`NeumorphicCard`/`NeumorphicTintedCard`/`NeumorphicCircleButton`/
  `SectionLabel`/`FeatureControl`) diubah `private`→`internal` karena sekarang dipanggil
  lintas-file dalam package yang sama.
- **`build.gradle.kts`**: versionCode 54→55, versionName 1.54→1.55.
- **ZERO perubahan logic/behavior/UI** — murni pemindahan lokasi kode. Diverifikasi:
  16/16 deklarasi cocok (tidak ada yang hilang/dobel), brace/paren balance SEMUA file
  Kotlin project (bukan cuma 3 file baru), string parity ID/EN tetap 95/95.

## v1.54 - Batch 15 (diminta user): terapkan spec design system "Hybrid Neumorphism"
User kirim spec lengkap tertulis (color/shadow/radius/elevation/pressed-state/typography/
layout). Diterapkan selektif — detail lengkap + yang BELUM dikerjakan ada di
`PROJECT_STATE.md` Batch 15 (baca itu dulu kalau lanjut kerjain sisanya).
- **`Theme.kt`**: alpha shadow diturunkan ke spec (gelap 60%, terang ~4%), token radius/
  offset/blur baru (`NeuCardRadius=22dp`, `NeuIconBoxRadius=14dp`,
  `NeuShadowDark/LightOffset/Blur`), `headlineMedium` 30sp→28sp, `AppShapes.medium`
  20dp→22dp. Warna primary/background/text TIDAK diubah (kebetulan sudah match persis).
- **`MainActivity.kt`**: `neumorphicDepth()` sekarang asimetris per-sisi (bukan simetris
  lagi). Fungsi baru `neumorphicInnerShadow()` buat pressed/carved state (ganti border
  rata). `NeumorphicCard` terima parameter `radius` baru. Icon-box `FeatureControl`
  36dp→40dp + radius 14dp. `NeumorphicCircleButton` dapat scale 0.97x on-press (gesture,
  `MutableInteractionSource`) + ripple dimatikan. `SectionLabel` font 12sp/letterSpacing
  1.4 hardcoded. Padding root 24→22dp, gap card 20→16dp.
- **`build.gradle.kts`**: versionCode 53→54, versionName 1.53→1.54.

## v1.53 - Batch 14 (fix urgent, dilaporkan user): efek kedalaman neumorphic invisible
User install v1.52, screenshot device: kartu Bass Boost/Virtualizer/status card nyaris
FLAT — beda jauh dari `docs/preview/current.html` yang jelas timbul. Root cause: lihat
`PROJECT_STATE.md` Batch 14 (ringkas: `Modifier.shadow` Android dibatasi alpha keras
oleh sistem, gak bisa setebal CSS `box-shadow`).
- **`MainActivity.kt`**: tambah `Modifier.neumorphicDepth()` — shadow manual pakai
  `Paint.setShadowLayer` via `drawBehind`+`drawIntoCanvas` (gated API 28+, fallback tanpa
  shadow di API lebih lama). `NeumorphicCard`, `NeumorphicTintedCard`,
  `NeumorphicCircleButton` semua dipindah ke sini — struktur disederhanakan dari
  Box+2-child-offset jadi 1 modifier chain (`Column`/`Box` modifier langsung).
- Import baru: `drawBehind`, `Outline`, `Path`, `Shape`, `addOutline`, `asAndroidPath`,
  `drawIntoCanvas`, `nativeCanvas`, `toArgb`, `android.graphics.Paint as AndroidPaint`.
  Import lama yang jadi tidak terpakai (`androidx.compose.ui.draw.shadow`) dihapus.
- **`build.gradle.kts`**: versionCode 52→53, versionName 1.52→1.53.
- Tidak ada perubahan behavior/logic lain — murni fix visual dual-shadow.

## v1.52 - Batch 13 (diminta user): porting elemen yang hilang dari HTML preview
User bandingin screenshot APK terpasang vs `docs/preview/current.html` — ketauan HTML
sudah lebih maju dari Kotlin di 2 elemen, padahal footer HTML klaim "sudah live di app"
(klaim itu salah/basi, diperbaiki juga di commit ini).
- **`MainActivity.kt`**: tambah `PowerToggleRow` (composable baru) — tombol bundar 64dp
  "Aktif/Nonaktif" pakai `NeumorphicCircleButton` (composable baru, varian `CircleShape`
  dari teknik dual-shadow `NeumorphicCard`, + ring 2dp `primary` saat ON). Toggle
  memanggil `AudioEnhancerService.requestStart`/`requestStop` — TIDAK bikin jalur
  start/stop baru, pakai fungsi yang sama persis dipakai `ShortcutHelper` &
  `QuickToggleTileService`. Ditaruh tepat di bawah header, sebelum status card
  (posisi sama seperti draft HTML).
- **`MainActivity.kt`**: tambah `SectionLabel("Kontrol")` sebelum kartu Bass Boost —
  sebelumnya kartu kontrol langsung tampil tanpa header section.
- **`strings.xml` (ID) + `values-en/strings.xml`**: 6 string baru, parity dijaga —
  `power_toggle_on_label`, `power_toggle_off_label`, `power_toggle_on_desc`,
  `power_toggle_off_desc`, `cd_power_toggle`, `controls_title`.
- **`build.gradle.kts`**: versionCode 51→52, versionName 1.51→1.52.
- **TIDAK diubah/dihapus**: waveform decorative row (ada di Kotlin, gak ada di HTML) —
  di luar scope permintaan (cuma 2 elemen di atas yang diminta), lihat `PROJECT_STATE.md`
  buat detail kenapa dibiarkan.

## v1.51 - Batch 12 (diminta user): port "Neumorphic Hybrid" ke Kotlin (final verdict)
Redesain BAHASA DESAIN (bukan cuma palet lagi, beda dari Batch 10) + naikkan legibility,
sesuai draft `docs/preview/current.html` yang sudah divalidasi user. Arah dipilih dari 3
opsi via `ask_user_input_v0`: **Neumorphic Hybrid**.
- **`Theme.kt`**: background dark digeser `#0A0A0A` → `#232220` (flat solid, gradient
  `DarkBackgroundBrush` DICABUT — neumorphism butuh base color merata biar dual-shadow
  konsisten); background light `#F7F5F1` → `#E7E4DC` (alasan sama, versi terang). Tambah
  token `NeuShadowDarkSide`/`NeuShadowLightSideDark`/`NeuShadowLightSideLight`/
  `NeuShadowDarkSideLight` buat dual-shadow. Tambah `LocalIsDarkTheme` (CompositionLocal)
  supaya kartu neumorphic tahu status dark/light AKTUAL (hasil override manual user),
  bukan cuma `isSystemInDarkTheme()` mentah — cegah bug warna shadow salah kalau user
  override tema berlawanan dari sistem.
- **`MainActivity.kt`**: `GlassCard`/`GlassTintedCard` (fill translucent + border gradient)
  di-rename & tulis ulang total jadi `NeumorphicCard`/`NeumorphicTintedCard` (permukaan
  SOLID, kedalaman dari dual `Modifier.shadow` offset berlawanan arah — shadow asli
  Android, BUKAN `Modifier.blur`, supaya aman lintas API 24+ tanpa isu versi). Semua 9
  call-site otomatis ikut karena signature dijaga tetap kompatibel (`accentColor`/
  `accentColor2` masih diterima tapi sudah tidak dipakai buat border). Gradient-clip TEXT
  dibuang total: judul app (`headlineMedium`) & value slider (`FeatureControl`) sekarang
  warna solid, bukan `Brush.linearGradient` di teks — sumber utama inkonsistensi kontras
  di struktur lama. Icon-orb jadi neumorphic mini-card sendiri (fill solid + tint ikon
  pakai accent, bukan lagi gradient fill putih-di-atas-gradient).
- Aksen per-fitur (Bass/Virtualizer/Loudness/Equalizer) & primary bronze TIDAK diubah,
  cuma cara pakainya (icon/slider/value-text doang, bukan border/fill kartu).
- `docs/preview/current.html` disinkronkan status jadi "sudah live", bukan draft lagi.
- Bump `versionCode` → 51, `versionName` → "1.51".
- **Batasan static-analysis (jujur, belum ada runner buat verifikasi visual beneran)**:
  dual-shadow neumorphic BELUM divalidasi di device/emulator asli — cuma brace/paren
  balance & baca-ulang logic. Kalau device screenshot pas dipasang ternyata shadow-nya
  kurang pas (terlalu tipis/tebal/offset kurang jauh), itu tuning `elevation`/`offset` di
  `NeumorphicCard`, bukan bug struktural.

## v1.50 - Batch 11 (audit lanjutan, diminta user): "audit/pematangan lanjutan"
Full sweep: brace/paren balance 12 file Kotlin (bersih), semua XML parse-validated,
parity string ID/EN (89/89), `FILE_MANIFEST.txt` vs file fisik (sinkron), scan
drawable/mipmap/string orphan (0 ketemu, `app_name` sempat kedeteksi false-positive
karena cuma dipakai di `AndroidManifest.xml`, bukan resource lain). Ketemu 2 hal nyata:
1. **Bug logika (`BootReceiver.kt`)**: start service TANPA SYARAT tiap BOOT_COMPLETED/
   MY_PACKAGE_REPLACED — kontradiksi langsung sama kontrak "hormati pilihan user" yang
   eksplisit didesain buat `ServiceWatchdogWorker` di Batch 9
   (`PrefsHelper.getUserWantsRunning()`). Skenario nyata: user tekan "Matikan" di
   notifikasi (flag jadi `false`), lalu HP reboot → service tetap nyala sendiri tanpa
   consent, padahal watchdog di jalur lain sudah didesain diam kalau flag ini `false`.
   Fix: `BootReceiver` sekarang baca flag yang sama sebelum `requestStart()`.
2. **CI tidak sesuai instruksi standing user** (`.github/workflows/build.yml`): job
   `release` cuma `actions/upload-artifact`, TIDAK PERNAH publish GitHub Release —
   padahal aturan rilis project ini eksplisit minta APK muncul di sidebar repo (tab
   "Releases"), bukan cuma Actions Artifact yang expire otomatis & tersembunyi di tab
   Actions. Fix: tambah step `softprops/action-gh-release@v2` (tag `v{versionName}`,
   upload APK signed sebagai release asset, `permissions: contents: write` di job
   level), upload-artifact lama TETAP dipertahankan sebagai tambahan (retensi pendek).
   `README.md` bagian "Build"/"Versioning APK Release"/"Setup Release Signing"
   diselaraskan ke behavior baru ini.
- Tidak ada perubahan fungsional/UI lain — audit murni, bukan batch fitur baru.
- Bump `versionCode` → 50, `versionName` → "1.50".

## v1.49 - Batch 10 (diminta user): redesign matte "native ultra premium & expensive"
- **Root request user**: tema violet/glassmorphism (era Batch 2, v1.29+) dianggap
  "neon ungu alay" — diminta ganti total ke kesan matte, premium, mahal.
- **Palet primary/background diganti total**: violet neon (`#8B7CF6` primary,
  gradient background `#1B1330→#0A0714`) → graphite/charcoal matte netral
  (`#0A0A0A` background) + aksen logam champagne-bronze desaturasi (`#C2A26B`
  dark / `#8A6D3B` light), kesan alat audio fisik premium (brushed metal),
  bukan RGB gamer.
- **TIDAK diubah**: aksen per-fitur (Bass/Virtualizer/Loudness/Equalizer/Battery)
  — warnanya sudah muted/earthy sejak awal, bukan sumber keluhan "neon alay".
  Shape (rounded, glassmorphism card structure) & typography juga TIDAK diubah,
  cuma palet warna.
- File diubah: `Theme.kt` (primary/background/DynamicColorAccent + komentar),
  `colors.xml` + `values-night/colors.xml` (splash), `ic_launcher_background.xml`,
  `widget_background.xml`, `ic_shortcut_preset.xml` (hardcoded hex violet →
  bronze/graphite), `docs/preview/current.html` (mockup diselaraskan).
- Semua 4 lokasi `#8B7CF6` (hex neon violet) di seluruh repo sudah di-grep ulang
  pasca-perubahan — 0 sisa, cuma 1 baris komentar historis di `Theme.kt`.
- Bump `versionCode` → 49, `versionName` → "1.49".

## v1.48 - Batch 9 (diminta user): "keluarkan semua trik biar app berfungsi 100% lifetime"
- **Sebelum implementasi, sudah dijelaskan eksplisit ke user di chat**: klaim "100%
  lifetime" TIDAK bisa dijamin dari kode app manapun — battery/task manager
  proprietary OEM (MIUI, ColorOS, EMUI, XOS, dst) bisa membunuh foreground service
  di luar kendali app (sudah didokumentasikan sejak insiden v1.34). Yang realistis:
  maksimalkan peluang bertahan + secepat mungkin sembuh sendiri kalau dibunuh.
- **Ditambah**: `ServiceWatchdogWorker` (WorkManager `PeriodicWorkRequest`, interval
  15 menit — minimum yang diizinkan WorkManager, gak bisa lebih cepat). Dijadwalkan
  sekali di `AudioEnhancerApp.onCreate()` via `enqueueUniquePeriodicWork(...,
  ExistingPeriodicWorkPolicy.KEEP, ...)`. Cek tiap siklus: kalau service harusnya
  hidup tapi ternyata mati, restart via `AudioEnhancerService.requestStart()`.
- **Flag baru**: `PrefsHelper.getUserWantsRunning()`/`setUserWantsRunning()` — beda
  dari `AudioEnhancerService.isRunning` (state runtime doang, reset ke `false`
  kalau process app mati). Flag ini persisten, jadi watchdog bisa bedakan "OS yang
  bunuh paksa" (restart) vs "user sengaja matiin lewat notifikasi 'Matikan'"
  (jangan restart, hormati pilihan user). Di-set di 2 titik di
  `AudioEnhancerService.onStartCommand()`: `true` di jalur start normal, `false`
  di jalur `ACTION_STOP`.
- **Trik yang SENGAJA ditolak** (dibahas di chat, biar gak diulang tanya lagi):
  AccessibilityService disalahgunakan sebagai watchdog, DeviceAdminReceiver,
  `foregroundServiceType` yang di-declare palsu, reflection ke hidden API OEM.
  Semua itu pola "bypass consent user diam-diam" — levelnya sama dengan "trik VPN"
  yang sudah ditolak sebelumnya (lihat histori QS Tile v1.40).
- Dependency baru: `androidx.work:work-runtime-ktx:2.9.1`.
- Tidak ada perubahan UI di batch ini (persistent banner battery-optimization
  reminder sengaja DIPISAH ke Batch 10 kalau diminta lanjut, biar batch ini gak
  nyentuh >10 file — lihat PROJECT_STATE.md bagian "PENDING").
- Bump `versionCode` → 48, `versionName` → "1.48".

## v1.47 - Batch 8 (audit lanjutan, diminta user): "audit/pematangan lanjutan"
- Full re-read semua 12 file Kotlin (brace/paren balance dicek via script), parity
  string ID/EN (89/89 cocok, termasuk setelah edit batch ini), `FILE_MANIFEST.txt`
  vs file fisik (sinkron 100%), CI (`build.yml`) & `README.md` (masih sinkron dari
  batch 6). Ketemu 1 bug logika nyata:
  - **Bug**: dialog "Simpan Preset" di `MainActivity.kt` cuma mengecek tabrakan
    nama (case-insensitive) terhadap 4 preset BAWAAN (`presets`) — TIDAK PERNAH
    dicek terhadap sesama preset CUSTOM lain (`customPresets`), padahal
    `PrefsHelper.CustomPreset` eksplisit menjanjikan "nama harus unik". Akibatnya
    user bisa membuat 2 custom preset berbeda isi (bass/virtualizer/loudness)
    tapi nama nyaris identik (mis. "Rock" dan "rock") — chip preset & dynamic
    shortcut jadi membingungkan karena labelnya kelihatan sama padahal itu 2
    preset terpisah.
  - **Fix**: tambah pengecekan `customPresets.any { it.name != trimmedPresetName
    && it.name.equals(trimmedPresetName, ignoreCase = true) }` ke kondisi
    `nameCollidesWithBuiltIn`. Pengecualian `it.name != trimmedPresetName`
    sengaja ditambahkan supaya menyimpan ulang preset custom dengan nama PERSIS
    SAMA (exact match) tetap diizinkan — itu perilaku "timpa yang lama" yang
    memang disengaja di `PrefsHelper.addCustomPreset`, bukan bug yang perlu
    ikut diblokir.
  - String `preset_save_name_collision_error` (ID+EN) digeneralisasi dari
    "sudah dipakai preset bawaan" menjadi "sudah dipakai preset lain"/"already
    used by another preset", karena pesan error ini sekarang berlaku untuk
    kedua jenis tabrakan (built-in maupun sesama custom).
- Bug ini murni logic gap yang lolos dari audit batch 1-7 sebelumnya karena
  butuh skenario spesifik (2 custom preset beda case) yang belum pernah
  ditest manual — bukan regresi dari perubahan batch manapun.
- Bump `versionCode` → 47, `versionName` → "1.47".

## v1.46 - Batch 7 (audit lanjutan, diminta user): "penyempurnaan & debugging tuntas"
- Diminta user eksplisit: "gak usah update fitur baru, fokus penyempurnaan aplikasi
  dan debugging sampai tuntas". Full re-read semua 12 file Kotlin (brace/paren
  balance dicek via script, bukan cuma baca mata), semua XML (parse-validated),
  cross-check parity string ID/EN (89/89 cocok termasuk format specifier `%s`/`%d`),
  `FILE_MANIFEST.txt` vs file fisik (sinkron 100%). Sebagian besar bersih — ketemu
  1 bug nyata + 1 dead code:
  1. **Bug**: `values/colors.xml` & `values-night/colors.xml` (`splash_background`)
     masih pakai warna era "Apple-style minimalis" (`#F2F2F7` terang / `#000000`
     gelap) dari v1.11-v1.23 — padahal `Theme.kt` sudah lama pivot ke tema violet
     "native ultra premium" (`LightColors.background = #FAF7FF`,
     `DarkColors.background = #0A0714`). Splash screen jadi kedip warna abu-abu/
     hitam pekat generik sebelum lompat ke background violet-tinted app — transisi
     kelihatan patah, bukan mulus. Fix: `splash_background` disamakan persis ke
     warna background tema aktif di kedua file, komentar basi yang masih nyebut
     "Apple-style"/"iOS-style" juga diperbarui.
  2. **Dead code**: `AudioEnhancerService.getEqualizer()` didefinisikan tapi
     `grep` nol hasil pemanggil di seluruh codebase (UI ambil data equalizer
     lewat `getEqualizerBandCount()`/`getEqualizerBandLevel()`/dst, bukan lewat
     objek `Equalizer` mentah). Dihapus.
- Pola temuan kali ini beda dari batch 1/5/6 sebelumnya (yang semuanya soal
  dokumentasi/resource-wiring gak sinkron ke kode) — ini soal 2 konstanta warna
  yang ketinggalan pas migrasi arah desain, di file yang gak ke-cover audit
  Kotlin (batch 1-5) maupun audit gradle/CI/README (batch 6).
- Bump `versionCode` → 46, `versionName` → "1.46".

## v1.46 (hotfix pengiriman) - Insiden: `.github/workflows/build.yml` + `.gitignore` sempat hilang dari repo
- **BUKAN perubahan kode app apapun** — versionCode/versionName TETAP di 46/"1.46",
  karena ini murni insiden packaging ZIP, bukan rilis fitur/fix baru.
- **Insiden**: command `zip` yang dipakai buat bikin ZIP pengiriman v1.46 pertama
  pakai flag exclude `-x ".*"` yang (tanpa disadari) ikut membuang SEMUA folder/file
  berawalan titik dari isi ZIP — termasuk `.github/workflows/build.yml` (CI) dan
  `.gitignore`. `FILE_MANIFEST.txt` di dalam ZIP itu sendiri masih benar (tetap
  mencatat kedua file itu harus ada), tapi ZIP fisiknya sendiri yang cacat — validasi
  yang cuma bandingkan isi ZIP ke `FILE_MANIFEST.txt` di dalam ZIP yang sama gak
  nangkep ini, karena dua-duanya "cocok" secara keliru (sama-sama kekurangan
  file yang sama).
- **Dampak nyata**: user jalanin command Termux standar (bersihkan folder lokal →
  extract ZIP baru → commit → push). Karena ZIP-nya gak punya `.github`, folder itu
  ikut kehapus dari clone lokal, lalu ikut ke-commit sebagai deletion pas push ke
  `main`. Akibatnya CI (`build.yml`) lenyap dari repo — nggak ada Action yang
  ke-trigger sama sekali setelah push v1.46, gak ada build yang jalan atau APK yang
  dihasilkan.
- **Fix**: `.github/workflows/build.yml` dan `.gitignore` dipulihkan persis seperti
  sebelumnya (isi tidak diubah, cuma dikembalikan) di ZIP hotfix ini.
- **LESSON buat sesi Claude berikutnya**: JANGAN PERNAH pakai `zip -x ".*"` (atau
  pola exclude sejenis yang match folder/file berawalan titik) saat packaging ZIP
  proyek ini — itu ikut membuang `.github` dan `.gitignore` yang WAJIB ada. Kalau
  perlu exclude sesuatu, exclude nama spesifiknya satu-satu, JANGAN pola wildcard
  yang match semua dotfile/dotdir. Sebelum kirim ZIP, WAJIB jalankan `unzip -l`
  pada ZIP HASIL AKHIR dan cek `.github/workflows/` ada di listing-nya — validasi
  manifest internal ZIP TIDAK CUKUP karena bisa sama-sama cacat dengan cara yang
  konsisten (persis seperti insiden ini).

## v1.45 - Batch 6 (audit lanjutan): file non-Kotlin (gradle/CI/README)
  proguard) — belum pernah diaudit khusus sebelumnya (batch 1-5 fokus Kotlin +
  resource Android). `build.gradle.kts` (root+app), `settings.gradle.kts`,
  `gradle.properties`, `proguard-rules.pro` bersih, gak ada temuan. Ketemu 4 hal
  nyata di `build.yml` (CI) dan `README.md`:
  1. **CI**: step `secret_check` cuma validasi `KEYSTORE_BASE64` doang, padahal
     signing butuh 4 secrets (`KEYSTORE_BASE64`+`KEYSTORE_PASSWORD`+`KEY_ALIAS`+
     `KEY_PASSWORD`). Kalau cuma 1-3 dari 4 secrets keset (misal typo pas `gh
     secret set`), job release TETAP jalan lanjut, terus gagal ambigu jauh di
     dalam step signing — bukan skip bersih dengan warning jelas kayak kasus
     "belum diset sama sekali". Sekarang validasi keempat-empatnya.
  2. **README**: bagian "Setup Release Signing" klaim nama artifact
     `audio-enhancer-pro-release-apk-signed` — padahal `build.yml` yang beneran
     jalan pakai nama DINAMIS `AudioEnhancerPro-v{versionName}-release`. Sudah
     disamakan.
  3. **README**: bagian "Batasan jujur" masih klaim "Belum ada icon asli — ganti
     placeholder" — padahal Adaptive Icon custom (vector gradient + Material You
     monochrome) sudah dikerjain beberapa batch lalu. Klaim basi dihapus.
  4. **README**: bagian "Fitur" gak nyebut QS Tile/App Shortcuts/Widget sama
     sekali (fitur v1.40-v1.43) — README ketinggalan, gak diupdate bareng
     fitur-fitur itu walau checklist minta README diupdate tiap ada perubahan
     fitur. 3 poin baru ditambahin.
- Pola sama kayak Batch 1 (v1.36): dokumentasi gak sinkron sama kode — bedanya
  batch itu di README+CHANGELOG lama, ini di README+CI workflow.
- Bump `versionCode` → 45, `versionName` → "1.45".

## v1.44 - Batch 5 (audit lanjutan): 2 resource orphan di AndroidManifest.xml
- Diminta user eksplisit: audit/bug-hunt lagi kayak batch 1-4. Full re-read semua
  file Kotlin (termasuk kode baru v1.41-v1.43) + cross-check parity string ID/EN +
  cek resource yang gak kepakai. Kotlin logic bersih, gak ada temuan baru — tapi
  ketemu 2 hal di `AndroidManifest.xml`:
  1. `android:label="Audio Booster"` di-hardcode langsung, padahal string
     `app_name` (ID+EN, isinya identik) udah ada dari awal dan gak pernah dipakai
     sama sekali. Sekarang `android:label="@string/app_name"`.
  2. `ic_launcher_round` (mipmap semua densitas) di-generate pas batch Adaptive
     Icon redesign, tapi `android:roundIcon` gak pernah ditambahin ke manifest —
     jadi file-nya nganggur gak pernah dipakai. Sekarang
     `android:roundIcon="@mipmap/ic_launcher_round"` ditambahin.
  - Dampak sebelum fix: nyaris tidak kelihatan (label sama persis "Audio Booster"
    di kedua locale, dan launcher tanpa `roundIcon` otomatis fallback ke
    `ic_launcher` yang di-mask sistem) — tapi tetap defect arsitektur nyata:
    resource orphan + gak konsisten sama konvensi Android standar
    (`android:label` semestinya rujuk string resource, bukan literal).
- Bump `versionCode` → 44, `versionName` → "1.44".

## v1.43 - Widget home screen: toggle + status tanpa buka app
- **Widget baru** (`BoosterWidgetProvider`) — kartu kecil di home screen nunjukin status
  (dot ijo/merah + teks "Aktif"/"Nonaktif", reuse label QS Tile) dan bisa di-tap buat
  toggle langsung, TANPA buka MainActivity sama sekali. Beda dari App Shortcuts (v1.42)
  yang cuma jalan pintas buka app — widget ini beneran interaktif dari home screen.
- **Refresh status real-time, bukan periodic**: `updatePeriodMillis="0"` (auto-update
  bawaan Android minimal 30 menit, kelewat lambat). Update didorong manual dari SATU
  titik: `AudioEnhancerService` manggil `BoosterWidgetProvider.refreshAll()` tiap
  `isRunning` berubah — otomatis nutup SEMUA jalur toggle (MainActivity, BootReceiver,
  QS Tile, App Shortcut, widget sendiri) tanpa perlu hook terpisah di masing-masing.
- Warna dot status disamain persis sama `ServiceStatusBadge` di layar utama (`#30D158`
  ijo / `#FF453A` merah) — konsisten, bukan re-invent palet baru.
- RemoteViews cuma dukung subset View terbatas (gak bisa Compose/Material3), jadi
  layout widget pakai `LinearLayout`+`TextView`+`ImageView` klasik dengan warna dibakar
  langsung di drawable (`widget_background.xml` gradient, `widget_status_dot_*.xml`) —
  pola yang sama kayak fix v1.41, sengaja dihindari dari awal.
- File baru: `BoosterWidgetProvider.kt`, `res/layout/widget_booster.xml`,
  `res/xml/widget_booster_info.xml`, `widget_background.xml`, `widget_status_dot_on.xml`,
  `widget_status_dot_off.xml`. File diubah: `AudioEnhancerService.kt` (3 hook refresh),
  `AndroidManifest.xml` (receiver + meta-data).
- Bump `versionCode` → 43, `versionName` → "1.43".
- **Kedua fitur shortcut yang diminta user (App Shortcuts v1.42 + Widget v1.43) SELESAI.**

## v1.42 - App Shortcuts: long-press ikon launcher buat akses instan
- **Shortcut statis "Nyalakan/Matikan"**: long-press ikon app di launcher → toggle booster
  langsung, gak perlu buka app dulu (mirip QS Tile, tapi dari home screen). Dideklarasikan
  lewat `res/xml/shortcuts.xml`, dieksekusi di `MainActivity.handleShortcutIntent()`.
- **Shortcut dinamis buat preset custom** (maksimal 3, yang paling baru disimpan tampil
  duluan): tap shortcut → app kebuka DAN preset itu langsung diterapkan otomatis. Ini yang
  paling kepake — preset custom yang user pernah bikin tapi lupa dipakai lagi, sekarang
  sejangkauan long-press ikon. Di-refresh otomatis tiap kali preset custom ditambah/dihapus
  lewat `ShortcutHelper.refreshCustomPresetShortcuts()`.
- Kenapa cuma 3 slot dynamic (bukan semua preset custom): sisa slot aman setelah 1 slot
  statis (toggle) dipakai, dari jaminan minimal 4 shortcut total per app di kebanyakan
  launcher/OEM.
- Ikon shortcut (`ic_shortcut_preset.xml`, `ic_shortcut_toggle.xml`) sengaja dibuat SEMUA
  warnanya dibakar langsung di path (bukan `?attr/...` theme reference) — insiden AAPT2
  gagal build di v1.40/v1.41 sengaja dihindari dari awal di sini.
- File baru: `ShortcutHelper.kt`, `res/xml/shortcuts.xml`, `ic_shortcut_preset.xml`,
  `ic_shortcut_toggle.xml`. File diubah: `MainActivity.kt` (onNewIntent + handling preset
  dari shortcut), `AndroidManifest.xml` (meta-data shortcuts), `strings.xml` + `values-en`.
- Bump `versionCode` → 42, `versionName` → "1.42".
- **Belum dikerjain**: widget home screen (toggle + status tanpa buka app) — batch
  berikutnya, disepakati bareng user sebagai fitur shortcut kedua yang diminta.

## v1.41 - Fix build gagal: `ic_qs_tile.xml` referensi theme attr yang gak valid
- **Root cause**: `ic_qs_tile.xml` (ditambahin di v1.40) pakai `android:tint="?attr/colorControlNormal"` tanpa prefix `android:` di depan `attr`. AAPT2 nyari attr itu di namespace package sendiri (`com.audioenhancer.booster:attr/colorControlNormal`) yang emang gak pernah dideklarasikan — bukan attr framework/AppCompat yang dimaksud. Hasilnya: `processDebugResources FAILED` (resource linking error), CI merah total, gak ada APK yang ke-generate.
- **Fix**: attribut `android:tint` di drawable itu dihapus total. Gak butuh tint manual di sini — Quick Settings tile di Android render iconnya sebagai alpha-mask dan sistem yang otomatis nge-tint (aktif/nonaktif) sesuai state tile, jadi tint di level drawable emang gak kepake/gak perlu.
- Sudah dicek: gak ada referensi `?attr/` lain yang belum ke-resolve di seluruh folder `res/`.
- Bump `versionCode` → 41, `versionName` → "1.41".

## v1.40 - Quick Settings Tile: toggle Audio Booster tanpa buka app dulu
- **Tile baru** (`QuickToggleTileService`) muncul di daftar edit Quick Settings (tarik notification shade → pensil edit → tambah "Audio Booster"). Tap tile langsung nyalain/matiin service — persis pola UX tile "1.1.1.1" milik Cloudflare — TANPA harus buka MainActivity/nongkrong di recent apps dulu.
- **Klarifikasi penting**: ini BUKAN "trik VPN" buat ngakalin OEM battery-killer Infinix yang sempat dibahas — itu limitasi battery manager custom Transsion yang beroperasi di luar jangkauan kode app manapun, gak bisa diakali dari sisi app. Tile ini murni UX shortcut: toggle instan + cara tercepat "membangunkan" ulang service kalau sempat dimatikan OEM.
- **Refactor kecil (DRY, bagian dari pematangan)**: logika start/stop service yang sebelumnya copy-paste identik di `MainActivity` dan `BootReceiver` ditarik jadi 2 fungsi companion `AudioEnhancerService.requestStart()`/`requestStop()`, sekarang dipakai bareng oleh `MainActivity`, `BootReceiver`, dan tile baru ini. Perilaku 100% identik dengan sebelumnya, cuma gak triplikat lagi.
- Ikon tile (`ic_qs_tile.xml`) sengaja pakai motif equalizer-bar yang sama persis dengan `ic_launcher_monochrome.xml` (cuma diskalakan ke 24dp), biar konsisten identitas visual.
- String baru `qs_tile_label`/`qs_tile_subtitle_on`/`qs_tile_subtitle_off` — ID+EN, parity dicek ulang.
- Bump `versionCode` → 40, `versionName` → "1.40".

## v1.39 - Batch 4 (penutup audit): sikat 3 temuan minor terakhir
- **`BootReceiver` sekarang validasi `intent.action`** sebelum start service. Sebelumnya
  `onReceive` langsung start service tanpa cek action sama sekali — karena `exported=true`
  (wajib, biar BOOT_COMPLETED/MY_PACKAGE_REPLACED yang protected-broadcast bisa nyampe),
  app lain sebenarnya bisa kirim explicit intent ke receiver ini dengan action APAPUN
  (explicit intent lewati pengecekan intent-filter) dan tetap memicu service nyala. Sudah
  divalidasi manual (bukan spoof BOOT_COMPLETED-nya — itu tetap gak bisa karena protected
  broadcast — tapi celah "action bikinan sendiri lewat explicit intent"-nya).
- **`CrashLogger` timestamp file sekarang unik per-milidetik**, bukan per-detik. Sebelumnya
  dua crash beruntun dalam detik yang sama saling menimpa nama file, bikin rotasi
  `MAX_LOGS=5` gak akurat (log lama yang harusnya masih ada malah ketiban/hilang duluan).
- **Test coverage `PrefsHelperTest` diperluas** — nambah 9 test baru: dynamic color
  (default + round-trip), custom preset (default kosong, round-trip JSON penuh, multi-preset
  independen, timpa-by-name, delete-by-name), dan crash-seen timestamp (default + round-trip).
  Total sekarang 17 test (sebelumnya 8), nutup semua state persisten yang sebelumnya
  cuma diverifikasi manual.
- **Ini menutup semua temuan dari audit kecacatan logika** yang dimulai sesi ini (Batch
  1-4). Tidak ada temuan lain yang tersisa per audit terakhir — lihat riwayat lengkap di
  `PROJECT_STATE.md`.
- Bump `versionCode` → 39, `versionName` → "1.39".

## v1.38 - Fix regresi audit: custom preset bisa tabrakan nama dengan preset bawaan
- **Root cause**: dialog "Simpan Preset" tidak validasi nama custom terhadap 4 label preset bawaan (Flat/Bass Heavy/Vocal Boost/Treble Boost). Kalau user simpan custom preset dengan nama persis sama (mis. "Flat"), chip built-in DAN chip custom sama-sama ke-highlight "selected" bareng saat `activePreset` cocok nama itu — state visual ambigu, walau tiap chip tetap menerapkan nilai yang benar saat ditekan.
- **Fix**: `OutlinedTextField` di dialog simpan preset sekarang validasi real-time (case-insensitive) terhadap label preset bawaan — kalau tabrakan, field jadi `isError` merah + supporting text penjelasan, dan tombol "Simpan" otomatis disabled sampai nama diganti.
- String baru `preset_save_name_collision_error` ditambahkan lengkap ID + EN, cross-checked.
- Bump `versionCode` → 38, `versionName` → "1.38".

## v1.37 - Fix regresi audit: emoji hardcoded di OnboardingScreen (lolos dari pembersihan v1.27)
- **Root cause**: pembersihan emoji v1.27 ("hapus SEMUA emoji, ganti vector icon") cuma menyisir `strings.xml` — sementara `OnboardingScreen.kt` punya 6 emoji (🎧🔊🌐📢🛡️🔔) yang HARDCODED di Kotlin sebagai field `emoji: String`, jadi lolos dari audit itu. Ditemukan di audit kecacatan logika sesi ini.
- **Fix**: `OnboardingPage.emoji` diganti `icon: ImageVector` + `accentColor`/`accentColor2`, dirender sebagai icon-orb gradient 88dp (gaya sama persis dengan icon-box `FeatureControl` di layar utama, cuma lebih besar).
- **Bonus konsistensi (bukan cuma hapus emoji)**: ikon & warna tiap halaman onboarding disamakan PERSIS dengan ikon/aksen fitur yang sama di layar utama — Bass Boost pakai `VolumeUp`+`BassAccent`, Virtualizer pakai `SurroundSound`+`VirtualizerAccent`, Loudness pakai `Campaign`+`LoudnessAccent`, halaman izin baterai pakai `Shield`+`BatteryAccent` (sama kayak card baterai). Halaman welcome pakai `Headset`+`DynamicColorAccent` (violet, identitas app), halaman notifikasi pakai `Notifications`+`EqualizerAccent` (belum ada yang pakai warna ini di onboarding). Efeknya onboarding sekarang "mengenalkan" visual yang nanti user kenali lagi persis di layar utama, bukan cuma dekorasi lepas.
- Import `sp` yang jadi tidak terpakai (fontSize emoji dihapus) ikut dibersihkan.
- `docs/preview/current.html` SENGAJA tidak disentuh — mockup itu memang pakai emoji sebagai placeholder ikon (bukan representasi pixel-perfect, sudah dicatat di README), dan perubahan ini cuma menerapkan arah desain yang sudah dikunci sejak v1.27/v1.30, bukan eksplorasi arah baru yang perlu divalidasi ulang lewat preview.
- Bump `versionCode` → 37, `versionName` → "1.37".

## v1.36 - Audit dokumentasi: README sinkron ulang dengan kode aktual (Batch 1 dari audit kecacatan logika)
- **Audit menyeluruh dilakukan** terhadap semua file Kotlin, Manifest, Gradle, `strings.xml` (ID+EN), dan workflow CI — sebagian besar bersih (service lifecycle, effect handling, OEM autostart, tema, reset EQ, parity i18n semuanya sudah benar). Tapi ketemu 2 dokumentasi yang sudah tidak sinkron dengan kode:
- **Fix klaim fitur usang**: `README.md` bagian "Fitur" masih menyebut "Restart otomatis saat task di-swipe (`onTaskRemoved`)" — padahal mekanisme itu SUDAH DICABUT sejak v1.34 (diganti murni `stopWithTask="false"` + `START_STICKY`, tanpa restart manual). Diperbaiki supaya cocok dengan komentar di `AudioEnhancerService.kt` sendiri.
- **Fix klaim CI yang keliru**: `README.md` bagian "Build" klaim APK debug hasil CI "ada di tab Actions > Artifacts" — padahal dicek ulang ke `.github/workflows/build.yml`, job `build` (`assembleDebug`) TIDAK punya step upload-artifact sama sekali, cuma verifikasi kompilasi. Yang benar-benar upload artifact cuma job `release` (butuh secret keystore). Diperbaiki supaya tidak menyesatkan.
- **Belum dikerjakan (menunggu instruksi user)**: item audit lain yang sudah ditemukan tapi sengaja belum disentuh — emoji hardcoded di `OnboardingScreen.kt` yang lolos dari pembersihan emoji v1.27, potensi tabrakan nama custom preset dengan preset bawaan, `BootReceiver` exported tanpa permission, resolusi timestamp `CrashLogger` per-detik, dan test coverage `PrefsHelperTest` yang belum cover custom preset/dynamic color.
- Tidak ada perubahan perilaku/kode fungsional — murni perbaikan dokumentasi.
- Bump `versionCode` → 36, `versionName` → "1.36".

## v1.35 - Tambah kandidat Infinix/Tecno/itel (Transsion) di OemAutostartHelper
- Kandidat Intent baru: `com.transsion.phonemanager` / `com.itel.autobootmanager.activity.AutoBootMgrActivity` — dipakai bersama di Infinix (XOS), Tecno (HiOS), itel OS (satu grup Transsion Holdings).
- **Catatan kejujuran**: ini kandidat PALING GAK TERVERIFIKASI dari semua OEM yang didukung. Riset dilakukan (bukan tebakan), tapi bahkan library open-source populer `judemanutd/AutoStarter` masih punya issue terbuka soal Infinix/Tecno sejak 2020. Kalau gagal, otomatis fallback ke halaman App Info (tidak crash) — tapi user Infinix/Tecno/itel sebaiknya juga tahu jalur manual: Settings → Apps → App Management → cari app → aktifkan "Autostart", + Settings → Battery → Power saving mode → Exceptions → tambah app, + kunci app di recent apps (swipe-down kartu app, tap ikon gembok).
- Teks `battery_card_body` & `ob5_detail` (ID+EN) diupdate, sekarang sebut XOS/HiOS/Infinix/Tecno juga.
- Bump `versionCode` → 35, `versionName` → "1.35".

## v1.34 - Investigasi tuntas: notifikasi/service ikut mati saat swipe dari recent apps
- **Audit menyeluruh dilakukan** terhadap seluruh lifecycle service (`stopWithTask`, `START_STICKY`, kombinasi started+bound, `foregroundServiceType`, `onTaskRemoved`) — semuanya SUDAH BENAR secara implementasi Android standar. Bukan bug logika di `AudioEnhancerService.kt`.
- **Root cause**: battery/task manager proprietary OEM (Xiaomi/MIUI, Oppo/ColorOS, Vivo, Huawei/EMUI, Samsung, OnePlus, dst) membunuh foreground service TANPA PEDULI `stopWithTask`/`START_STICKY`, kecuali user manual mengizinkan "Autostart"/"No restriction" di pengaturan khusus tiap merk. Tidak ada API publik Android buat app minta izin ini otomatis — ini keterbatasan platform, bukan sesuatu yang bisa di-fix murni dari kode app.
- **Mitigasi ditambahkan**: `OemAutostartHelper.kt` baru — deep-link ke halaman pengaturan Autostart/battery manager yang relevan per-merk (coba beberapa kandidat Intent berurutan, fallback otomatis ke halaman App Info bawaan Android kalau semua gagal, jadi TIDAK PERNAH dead-end).
- Tombol "Buka Pengaturan Autostart" ditambahkan di card baterai (label tombol otomatis menyesuaikan: spesifik per-merk kalau device dikenal, generik kalau tidak).
- String baru ditambahkan lengkap di ID + EN, cross-checked.
- Temuan investigasi didokumentasikan di `PROJECT_STATE.md` sebagai jaring pengaman, biar laporan serupa di masa depan tidak salah diagnosis ke arah kode service lagi.
- Bump `versionCode` → 34, `versionName` → "1.34".

## v1.33 - Icon launcher ikut palet baru + robustness (crash log lokal & preset custom)
**Batch A — Icon launcher:**
- Gradient background icon (adaptive + legacy PNG semua densitas) diganti dari biru iOS lama (`#0A84FF→#64D2FF`) ke violet (`#8B7CF6→#3E2E6B`), konsisten sama primary/primaryContainer app saat ini. Motif 4-bar equalizer putih di foreground dipertahankan persis (bentuk & posisi gak berubah).
- PNG legacy (`mipmap-*dpi`, buat device API<26 yang gak pakai adaptive icon) di-regenerate per-pixel: alpha mask (bentuk squircle/lingkaran) & bar putih dipertahankan exact, cuma warna background yang di-recolor.
- `ic_launcher_monochrome.xml` (varian Android 13+ themed icon) sengaja TIDAK disentuh — warnanya di-override otomatis sama sistem berdasar wallpaper, RGB sumbernya gak ngaruh ke tampilan akhir.

**Batch B — Robustness:**
- **Crash log lokal** (`CrashLogger.kt` baru, `AudioEnhancerApp.kt` baru sebagai Application class): uncaught exception ditangkap & disimpan ke file internal (`filesDir/crash_logs/`, rotasi maks 5 file), rethrow ke handler default supaya perilaku crash Android normal tetap jalan. Kalau ada crash log yang belum dilihat, muncul banner kecil di layar utama dengan tombol "Lihat Detail" (dialog scrollable) dan "Hapus Log". Ini nutupin gap: sebelumnya kalau service crash di background, satu-satunya jejak cuma notifikasi yang tiba-tiba hilang tanpa penjelasan.
- **Preset custom**: selain 4 preset bawaan, sekarang bisa simpan kombinasi bass/virtualizer/loudness sendiri lewat chip "+ Simpan" → dialog nama → tersimpan permanen (`PrefsHelper`, JSON via `org.json` bawaan Android, gak nambah dependency baru). Preset custom muncul sebagai chip dengan ikon "×" buat hapus (dengan dialog konfirmasi). Preset custom sengaja TIDAK ikut reset equalizer manual saat diterapkan (beda dari preset bawaan) karena memang gak menyimpan state EQ.
- String baru ditambahkan lengkap di `values/strings.xml` DAN `values-en/strings.xml` (cross-checked, tidak ada key yang hilang di salah satu locale).
- Bump `versionCode` → 33, `versionName` → "1.33".

## v1.32 - Fix bug kritis: teks nyaris hitam-tak-terbaca di atas background gelap
- **Root cause ditemukan**: root `Surface` di-set `color = Color.Transparent` (biar gradient background keliatan tembus), tapi Compose Material3 gak bisa nentuin `contentColor` otomatis dari warna transparent — fallback ke default library, yaitu hitam pekat. Bug ini turun ke `GlassCard`/`GlassTintedCard` juga karena `containerColor`-nya (`surface.copy(alpha=0.6f)`) juga gak match slot warna manapun di color scheme.
- Akibatnya semua `Text()` tanpa `color=` eksplisit (judul "Bass Boost"/"Virtualizer"/"Loudness Gain", subtitle "Efek berlaku ke seluruh audio sistem") ikut hitam di atas background gelap — nyaris tak terbaca.
- **Fix**: tambah `contentColor` eksplisit di root `Surface` (→ `onBackground`) dan di `GlassCard`/`GlassTintedCard` (→ `onSurface`). Semua teks yang sebelumnya nge-inherit warna sekarang otomatis benar tanpa perlu disentuh satu-satu.
- **Tone-down accent per-fitur** (Bass/Virtualizer/Loudness/Equalizer/Battery): hue dipertahankan, saturasi & brightness diturunkan supaya nggak "neon" nyelekit di layar OLED gelap. `DynamicColorAccent` dibiarkan (sudah selaras primary violet).
- `docs/preview/current.html` disinkronkan dengan warna accent baru.
- Bump `versionCode` → 32, `versionName` → "1.32".

## v1.31 - PROJECT_STATE.md: file konteks khusus AI, biar sesi manapun bisa lanjut instan
- **`PROJECT_STATE.md` baru di root repo** — bukan README (buat manusia) atau CHANGELOG (log historis), tapi file PADAT yang didesain khusus dibaca AI di awal sesi: status terkini, keputusan desain & alasannya (yang gak boleh diubah tanpa alasan baru), batasan sandbox Claude (gak ada kotlinc/gradle, insiden `Icons.AutoMirrored.Filled.VolumeUp`), riwayat pivot arah desain (Apple-style → neo-brutalist → glassmorphism, plus KENAPA tiap pivot terjadi), command Termux standar, dan TODO yang sengaja belum dikerjain.
- **README & CHANGELOG di-update** nunjuk ke `PROJECT_STATE.md` sebagai bacaan PERTAMA sebelum apapun lain, supaya sesi Claude baru gak mulai dari nol atau ngulang pertanyaan/kesalahan yang sama.
- Bump `versionCode` → 31, `versionName` → "1.31".

## v1.30 - Port "native ultra premium" (glassmorphism) ke Kotlin — bukan cuma HTML mockup lagi
- **`GlassCard`/`GlassTintedCard`** menggantikan `AppleCard` neo-brutalist Batch 1: fill translucent (`surface.copy(alpha=0.6f)`) di atas background gradient, border TIPIS bergradasi (`Brush.linearGradient`, bukan solid tebal), shadow lembut (elevation 4-6dp, bukan 0dp flat).
- **Background gradient dalam**: `DarkBackgroundBrush` (violet gelap → hitam) menggantikan hitam pekat rata `#0D0B14` — dipasang di root `Surface` cuma untuk dark theme.
- **Tiap fitur sekarang punya PASANGAN warna** (`BassAccent`/`BassAccent2`, dst) buat gradient icon-orb, border kartu, dan teks angka (pakai `TextStyle(brush=...)`, fitur gradient-text Compose).
- **Signature waveform motif** di header — 8 bar gradient violet dengan tinggi bervariasi, elemen visual "audio" yang hidup, bukan dekorasi generik.
- **Judul "Audio Booster"** sekarang gradient text (putih → violet primary).
- Equalizer band individual TIDAK dibungkus kartu terpisah (`wrapInCard = false`) — sudah di dalam kartu "Equalizer Manual", biar gak numpuk kaca-di-atas-kaca.
- Shape kembali membulat lembut (`medium = 20dp`, `extraLarge = 32dp`) — kesan kaca premium, bukan sudut tajam brutalist ataupun bubble minimal Apple.
- `docs/preview/current.html` di-update biar tetap sinkron sama hasil Kotlin ini.
- Bump `versionCode` → 30, `versionName` → "1.30".

## v1.29 - README instruksi konteks instan + preview arah "native ultra premium"
- **README & CHANGELOG**: tambah baris instruksi eksplisit paling atas — paste link repo di sesi Claude baru buat konteks instan (fetch langsung, gak perlu upload ulang zip).
- **`docs/preview/current.html` diganti arah baru**: dari neo-brutalist border tebal (Batch 1) ke **glassmorphism premium** — kartu kaca buram (`backdrop-filter: blur`), border tipis gradient-glow senada aksen tiap fitur, slider dengan gradient track & thumb bercahaya, signature waveform bar di header, background gradient radial ungu-gelap (bukan hitam pekat rata). Arah ini belum di-port ke Kotlin — nunggu konfirmasi dulu lewat link preview.
- Bump `versionCode` → 29, `versionName` → "1.29".

## v1.28 - Live UI preview tertanam permanen di README &amp; CHANGELOG
- **`docs/preview/current.html`**: mockup HTML/CSS ditaruh permanen di repo (bukan cuma dikirim sekali di chat). Setiap kali ada perubahan arah desain besar yang perlu didiskusikan dulu sebelum di-build APK, file ini di-update bareng commit-nya.
- **Link live tertanam di README.md & CHANGELOG.md**: pakai [htmlpreview.github.io](https://htmlpreview.github.io) — render langsung dari file di repo, jadi link-nya PERMANEN (gak pernah ganti) tapi isinya SELALU versi terbaru. Gak perlu setup GitHub Pages manual.
- **Fix bug lama yang gak sengaja ketemu**: `CHANGELOG.md` ternyata gak berurutan sejak entry pertama saya (v1.10 sampai v1.5 nyempil di tengah antara v1.11 dan v1.4, karena saya nge-target string yang salah pas insert pertama kali). Sudah diurutkan ulang jadi murni descending v1.28 → v1.0, tidak ada isi yang hilang (diverifikasi 28 section utuh).
- Bump `versionCode` → 28, `versionName` → "1.28".

## v1.27 - 🎯 Fix yang beneran kelihatan: hapus SEMUA emoji, ganti vector icon monokrom
- **Root cause sebenarnya dari "gak kerasa Apple"**: dari 2 screenshot yang dikirim, ketauan biang keroknya bukan warna/shape/shadow (yang emang subtle di dark theme) — tapi **emoji berwarna-warni sebagai icon UI** (🔊🌐📢🎚️🛡️🎨🔕⚠️). Apple/iOS TIDAK PERNAH pakai emoji sebagai icon fungsional — mereka pakai SF Symbols monokrom bertema. Emoji berwarna itu langsung bikin kesan "generic Android app", seberapapun rapi shape/warna di sekitarnya.
- **Fix**: semua emoji dihapus dari `strings.xml` (ID+EN), diganti `Icon` vector monokrom bertema warna aksen (primary) di kode: Bass→VolumeUp, Virtualizer→SurroundSound, Loudness→Campaign, Equalizer→GraphicEq, Baterai→Shield, Dynamic Color→Palette, Notifikasi→NotificationsOff, Error→Warning.
- Ini perubahan STRUKTURAL (bentuk elemen berubah total, bukan cuma nuansa warna) — dijamin kelihatan bedanya di screenshot manapun, bukan cuma di dark theme.
- Bump `versionCode` → 27, `versionName` → "1.27".

## v1.26 - 🐛 Fix akar masalah "gak ada perubahan sama sekali" (v1.24/v1.25)
- **Root cause ketemu**: `AppleTintedCard` (v1.25) pakai `tint.copy(alpha = 0.14f)` — transparansi mentah di atas background dark theme yang HITAM PEKAT (`#000000`). Alpha tipis di atas hitam pekat hasilnya IKUT NYARIS HITAM (dihitung: biru brand di alpha 14% di atas hitam pekat = RGB (1,18,36), nyaris tak terbedakan dari `#000000`). Ini bukan gagal install atau gagal build — fix v1.25 itu BENERAN ke-compile & ke-install dengan benar, tapi hasilnya secara visual nyaris tidak kelihatan bedanya. Realistis banget kalau kerasa "kayak gak ada perubahan sama sekali".
- **Fix**: ganti total pendekatannya dari transparansi mentah ke **blend warna solid** pakai `lerp(surface, tint, fraction)` — container di-blend 22% ke arah warna aksen, border 55%. Hasilnya warna solid pekat yang PASTI kelihatan bedanya apapun warna di baliknya, bukan bergantung pada seberapa gelap background di belakangnya.
- Kemungkinan ini juga akar masalah yang sama di proyek GifMaker kalau pernah pakai pendekatan alpha-transparency serupa di atas background gelap pekat — worth dicek juga di sana kalau relevan.
- Bump `versionCode` → 26, `versionName` → "1.26".

## v1.25 - Fix polish v1.24 kurang kerasa: banner solid → soft-tint ala iOS
- **Root cause dari screenshot yang dikirim**: banner status/warning (Service berjalan, izin notifikasi, dll) masih pakai `errorContainer`/`primaryContainer` — warna "container" Material yang tetap solid pekat, bukan gaya iOS yang biasanya pastel/tint lembut.
- **Fix**: komponen baru `AppleTintedCard` — background cuma 14% opacity dari warna aksennya (bukan fill solid), border 30% opacity, teks pakai warna aksen penuh di atasnya. Diterapkan ke SEMUA banner: status service, error koneksi, izin notifikasi, banner chipset tidak didukung.
- **Catatan penting soal font tebal di screenshot**: teks yang terlihat bold merata di SEMUA elemen (termasuk paragraf deskripsi yang di kode eksplisit `FontWeight.Normal`) kemungkinan besar berasal dari setting Aksesibilitas "Teks Tebal" di HP — ini override paksa dari Android ke SEMUA app, tidak bisa di-override balik dari sisi app manapun (termasuk app native buatan Apple/Google sendiri kalau mereka di Android). Kalau mau font sesuai desain aslinya (mix bold/regular), cek Settings > Aksesibilitas > Ukuran & Tampilan Teks > matikan "Teks Tebal".
- Bump `versionCode` → 25, `versionName` → "1.25".

## v1.24 - Polish "Apple-Style" UI/UX
- **Kartu jadi flat ala iOS grouped-list**: semua `Card` diganti komponen baru `AppleCard` — tanpa shadow Material, cuma pembatas tipis 1dp — lebih mendekati tampilan Settings app iOS dibanding kartu Material yang "mengambang".
- **Preset jadi pill/segmented ala iOS**: chip preset sekarang bentuk pil penuh (bukan rounded-rect kecil Material), terisi solid biru saat aktif, abu-abu lembut saat tidak — mirip segmented control iOS, tanpa border.
- **Label section ala iOS Settings**: "PRESET CEPAT" sekarang tampil kecil, kapital, abu-abu, dengan letter-spacing — gaya khas header section di Settings iOS, menggantikan judul bold biasa.
- **Slider lebih minimal**: warna thumb & track aktif konsisten pakai warna primer, track tidak aktif abu-abu lembut — kesan lebih bersih, satu titik perubahan yang otomatis nyakup semua slider (Bass/Virtualizer/Loudness/Equalizer).
- **Large Title ala iOS**: judul "Audio Booster" di header sekarang 32sp Bold (naik dari 28sp SemiBold) — lebih dekat ke gaya "Large Title" khas iOS Settings/Mail/dsb.
- Bump `versionCode` → 24, `versionName` → "1.24".

## v1.23 - Audit tuntas: retry nagging, dependency mati, CI vs dokumentasi tidak sinkron
- **Fix retry connection ikut memicu ulang dialog izin**: tombol "Coba Lagi" (connection error) sebelumnya manggil `recreate()`, yang menjalankan ulang SELURUH `onCreate()` — termasuk `requestNotificationPermissionIfNeeded()` dan `requestIgnoreBatteryOptimizations()`, berpotensi memunculkan dialog sistem yang tidak diminta di tengah proses retry. Sekarang dipecah jadi `attemptBindService()` yang cuma coba re-bind ke service, tanpa efek samping ke permission dialog. Sebagai bonus, retry juga jadi lebih mulus (tidak ada flicker recreate activity).
- **Hapus `SCHEDULE_EXACT_ALARM`** (dari v1.22) — permission mati, nol pemakaian `AlarmManager` di kode manapun.
- **Hapus dependency `androidx.work:work-runtime-ktx`**: dicek ke seluruh source, nol pemakaian `WorkManager`/`Worker`/`WorkRequest`. Dependency ini nambah ukuran APK & waktu build tanpa memberi nilai apapun.
- **Hapus dependency `androidx.lifecycle:lifecycle-runtime-ktx`**: nol pemakaian `lifecycleScope`/`repeatOnLifecycle` langsung di kode manapun — kalaupun dibutuhkan transitif oleh Compose/Activity, Gradle tetap akan resolve otomatis tanpa perlu deklarasi eksplisit ini.
- **Sinkronkan CI (`build.yml`) dengan dokumentasi README**: README selama ini bilang "job release skip otomatis kalau secret belum diset, job build (debug) tetap sukses" — tapi workflow aslinya cuma punya 1 job ("release") yang `exit 1` (HARD FAIL) kalau `KEYSTORE_BASE64` belum diset, dan job "build" terpisah itu sama sekali tidak ada. Sekarang workflow benar-benar dipecah jadi 2 job sesuai dokumentasi: `build` (assembleDebug, selalu jalan tanpa butuh secret) dan `release` (assembleRelease, skip step-nya secara graceful — bukan exit 1 — kalau secret belum lengkap).
- Bump `versionCode` → 23, `versionName` → "1.23".

### ⚠️ Temuan yang SENGAJA belum diubah (butuh keputusanmu)
- **`MODIFY_AUDIO_SETTINGS`**: dicek, juga nol pemakaian `AudioManager` langsung di kode. TAPI — mekanisme inti app ini (nempel `BassBoost`/`Virtualizer`/`Equalizer`/`LoudnessEnhancer` ke audio session global `0`) itu sendiri sudah di luar cara resmi API ini didokumentasikan dipakai. Ada laporan anekdotal dari app sejenis bahwa sebagian chipset/OEM tetap butuh permission ini biar efek session-0 nempel dengan benar, walau tidak didokumentasikan resmi. Karena saya tidak punya device fisik buat verifikasi langsung, dan ini menyentuh mekanisme INTI app, saya tidak berani hapus sepihak — risikonya kalau salah adalah app berhenti berfungsi di sebagian HP tanpa saya bisa tahu. Kasih tahu kalau mau saya coba hapus (lalu kita pantau lewat testing manual di HP asli), atau biarkan saja karena tidak ada ruginya untuk tetap ada.
- **⚠️ Catatan jujur soal perubahan CI**: saya tidak punya akses GitHub Actions runner sungguhan di sandbox ini untuk benar-benar menjalankan workflow yang sudah diubah. Syntax YAML sudah divalidasi valid, dan pola `steps.X.outputs.Y` untuk conditional step ini adalah pola GitHub Actions yang umum & terdokumentasi — tapi tolong perhatikan hasil run pertama setelah push, kalau ada yang aneh kasih tahu saya.

## v1.22 - 2 temuan lanjutan: permission mati &amp; preset tidak konsisten
- **Hapus permission `SCHEDULE_EXACT_ALARM` yang tidak terpakai**: dicek ke seluruh source code, nol pemanggilan `AlarmManager` dimanapun. Permission ini nyasar di Manifest tanpa fungsi — di Android 12+ ini muncul sebagai izin terpisah ("Alarm & pengingat") di pengaturan HP, berpotensi bikin bingung siapapun yang cek daftar izin app ini.
- **Fix preset tidak konsisten dengan Equalizer Manual**: sebelumnya nge-tap preset (termasuk "Flat") cuma reset Bass/Virtualizer/Loudness — Equalizer Manual dibiarkan di posisi terakhir. Kalau user sempat oprek equalizer manual, preset "Flat" jadi tidak benar-benar flat. Sekarang tiap preset diterapkan, semua band equalizer ikut direset ke 0, dan tampilan slider-nya di kartu Equalizer Manual ikut ter-update (bukan cuma nilai di background yang berubah).
- Bump `versionCode` → 22, `versionName` → "1.22".

## v1.21 - 🐛 Fix bug backend penting: tombol "Matikan" di notifikasi tidak benar-benar mematikan efek
- **Root cause**: `AudioEnhancerService` di-*start* (dari `startForegroundService`) SEKALIGUS di-*bind* (dari `MainActivity`, selama app masih kebuka/belum di-destroy sistem). Android cuma benar-benar men-destroy Service kalau KEDUA ref-count (started + bound) sama-sama nol. Tombol "Matikan" di notifikasi sebelumnya cuma manggil `stopSelf()`, yang cuma clear ref-count "started" — kalau MainActivity masih bound, Service TETAP HIDUP dan efek audio TETAP AKTIF meski user sudah tap "Matikan".
- **Fix**: tambah `disableEffects()`/`enableEffects()` (reversible — beda dari `releaseEffects()` yang cuma dipakai saat Service betulan di-destroy). Sekarang tap "Matikan" langsung: (1) nonaktifkan ke-4 efek audio secara eksplisit, apapun status bind, (2) lepas foreground + hapus notifikasi seketika, (3) baru `stopSelf()`. Buka app lagi otomatis nyalain ulang efeknya via `enableEffects()`.
- **Tambahan UX terkait**: badge status sekarang punya tombol "Nyalakan Lagi" langsung kalau service kedeteksi tidak berjalan (misal habis di-"Matikan" lewat notifikasi sementara app masih kebuka) — sebelumnya user cuma disuruh "tutup & buka ulang app" manual lewat teks doang.
- String status diperbarui (ID+EN) supaya lebih akurat mendeskripsikan kondisi ini.
- Bump `versionCode` → 21, `versionName` → "1.21".

## v1.20 - Fix bug nyata: ikon status bar tidak sync sama tema manual
- **Bug**: `enableEdgeToEdge()` cuma dipanggil sekali di `onCreate()`, sebelum toggle tema manual (dari v1.11) sempat diresolve. Akibatnya kalau tema aktual app (hasil override manual) berlawanan dari tema sistem — misal sistem terang tapi kamu paksa Dark — warna ikon status bar/nav bar TIDAK ikut berubah, tetap ngikut sistem. Hasilnya ikon jadi nyaris tidak kelihatan (gelap-di-atas-gelap atau terang-di-atas-terang).
- **Fix**: tambah `SideEffect` yang sync ulang `isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` via `WindowCompat` setiap kali `darkTheme` yang SEBENARNYA aktif berubah — bukan cuma dibaca sekali dari sistem di awal. Sekarang ikon status bar selalu kontras dengan background app, apapun kombinasi tema sistem vs override manual.
- Bump `versionCode` → 20, `versionName` → "1.20".

### Audit ulang menyeluruh: bagian frontend lain sudah tuntas (di luar 5 item yang sudah disepakati tidak urgent: rotasi/config change, font scaling besar, landscape phone, RTL, kontras tombol biru).

## v1.19 - Haptic feedback + Loading/Error state eksplisit
- **Haptic feedback halus** di titik-titik interaksi utama:
  - Semua slider (Bass/Virtualizer/Loudness/Equalizer per-band) — getar halus saat jari dilepas (bukan tiap tick geser, biar tidak berisik/annoying)
  - Terapkan preset cepat — 1 getar konfirmasi
  - Toggle tema (sistem/terang/gelap) — 1 getar tiap ganti
  - Toggle "Warna ikut wallpaper" — 1 getar, sekalian seluruh baris (bukan cuma switch kecil) jadi target getar+tap
  - Expand/collapse kartu Equalizer Manual — 1 getar
- **Loading state eksplisit**: begitu app dibuka, muncul kartu kecil dengan spinner "Menyambungkan ke service audio…" selama proses `bindService()` masih berlangsung — sebelumnya user tidak tahu app "lagi nyambung" atau "sudah connected tapi ga ada apa-apa untuk ditampilkan".
- **Error state eksplisit**: kalau `bindService()` gagal total (return `false`) atau melempar exception, muncul kartu error jelas + tombol "Coba Lagi" (restart activity untuk retry) — sebelumnya kegagalan ini silent, app kelihatan "diam" tanpa penjelasan sama sekali ke user.
- String baru (`connection_loading`, `connection_error_title`, `connection_error_body`, `connection_retry`) sudah lengkap ID+EN, konsisten dengan Batch 10.
- Bump `versionCode` → 19, `versionName` → "1.19".

### Status roadmap "expert/user-friendly": semua item prioritas user (Batch 9, 10, 12, + haptic & loading/error) sudah tuntas.
### Sisa non-prioritas (dianggap tidak urgent oleh user): rotasi layar/config change, font scaling besar, landscape phone, RTL, kontras tombol biru.

## v1.18 - Batch 9: Aksesibilitas (a11y) — TalkBack &amp; kontras WCAG
- **Semua slider sekarang punya label TalkBack**: sebelumnya screen reader cuma baca angka polos tanpa konteks ("500", doang). Sekarang setiap slider (Bass, Virtualizer, Loudness, dan tiap band Equalizer) diberi `contentDescription` gabungan nama fitur + nilainya, lewat 1 titik perbaikan di `FeatureControl` (dipakai semua slider).
- **Toggle "Warna ikut wallpaper" sekarang 1 target sentuh utuh**: sebelumnya cuma `Switch` kecil yang bisa di-tap, teks di sampingnya tidak ikut jadi bagian tombol. Sekarang seluruh baris (judul+deskripsi+switch) jadi satu target tap & satu node TalkBack dengan role Switch yang benar, sekalian bikin area sentuh lebih besar/gampang buat semua orang.
- **Audit kontras warna WCAG** (dihitung manual pakai rumus luminance resmi WCAG di kedua tema):
  - Ditemukan `outline` (warna border) di kedua tema gagal standar non-text contrast 3:1 — light theme cuma 1.52:1, dark theme cuma 1.86:1. **Sudah diperbaiki**: light theme → 3.03:1, dark theme → 3.01:1. Warna brand utama (biru khas app) sama sekali tidak disentuh.
  - Semua pasangan warna teks lain (onSurface/surface, onBackground/background, dst) sudah lolos AA (≥4.5:1) di kedua tema, tidak perlu perubahan.
- **⚠️ Temuan yang SENGAJA belum diubah** (bukan bug baru, tapi worth diketahui): teks putih di atas warna biru brand (`primary`, dipakai di tombol) kontrasnya 4.02:1 (light) / 3.65:1 (dark) — lolos WCAG AA untuk teks besar tapi berada di ambang batas untuk teks kecil. Ini menyentuh warna brand utama yang sengaja dipertahankan sebagai identitas visual, jadi tidak diubah sepihak — kasih tahu kalau mau saya perbaiki (opsinya: gelapkan sedikit biru khusus untuk background tombol).
- Bump `versionCode` → 18, `versionName` → "1.18".

## v1.17 - Batch 10: Lokalisasi/i18n (Indonesia + Inggris)
- **Semua teks UI dipindah ke `strings.xml`** — sebelumnya 40+ string hardcode langsung di Kotlin (MainActivity, OnboardingScreen, notifikasi service). Mencakup: header, badge status, banner dukungan chipset, preset, semua feature control (Bass/Virtualizer/Loudness/Equalizer), kartu dynamic color & baterai, toggle tema, 6 halaman onboarding lengkap, sampai notifikasi foreground service (channel name, title, text, tombol "Matikan").
- **Tambah `values-en/strings.xml`** — terjemahan Inggris lengkap untuk semua key yang sama. User dengan bahasa HP Inggris sekarang otomatis dapat UI Inggris; bahasa lain tetap fallback ke `values/strings.xml` (Indonesia, default).
- Verifikasi: jumlah & nama key di `values/strings.xml` dan `values-en/strings.xml` **cocok 100%** (tidak ada key yang lupa diterjemahkan atau nyasar).
- Bump `versionCode` → 17, `versionName` → "1.17".

## v1.16 - Batch 12: Material You (opt-in) + dukungan tablet/foldable
- **Dynamic color (Material You), opt-in**: toggle baru "🎨 Warna ikut wallpaper" (hanya muncul di Android 12+/API 31+) — kalau diaktifkan, warna app ikut wallpaper HP (dynamicLightColorScheme/dynamicDarkColorScheme). **Default OFF** — palet biru khas iOS-style yang sudah dirancang sebagai identitas visual app tetap jadi default, dynamic color murni pilihan user yang mau lebih "nyatu" dengan tema HP-nya. Preferensi tersimpan permanen.
- **Dukungan tablet/foldable**: konten utama (BoosterScreen & OnboardingScreen) sekarang dibatasi max-width 600dp dan ditengahkan di layar lebar — sebelumnya slider/kartu melebar penuh sampai ke tepi layar tablet yang bikin proporsi aneh. Di HP biasa (<600dp) perilakunya identik seperti sebelumnya, tidak ada perubahan visual.
- Bump `versionCode` → 16, `versionName` → "1.16".

### Dicoret dari roadmap (murni manfaat developer, bukan user): arsitektur/ViewModel, linter/KDoc, testing tambahan — tetap belum dikerjakan sesuai arahan, fokus cuma yang kerasa ke user.

## v1.15 - Penutup audit: klarifikasi wakelock + keputusan foreground service type
- **Wakelock**: dicek langsung ke kode — ternyata **tidak ada `PowerManager.WakeLock` yang benar-benar dipegang** oleh `AudioEnhancerService`. Yang ada cuma komentar dokumentasi lama yang menyebut "wakelock" padahal tidak pernah diimplementasikan (kemungkinan sisa draft awal). Komentar itu sudah diperbaiki supaya akurat — "tidak mudah dibunuh" itu murni dari kombinasi foreground service + `START_STICKY`, bukan wakelock. Tidak ada perubahan perilaku baterai karena memang tidak ada apa-apa yang perlu dihapus.
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`**: **sengaja dibiarkan seperti sekarang** (keputusan sadar) — karena tidak ada rencana publish ke Play Store, risiko penolakan review tidak relevan, dan `specialUse` (API 34+) lebih ribet buat manfaat yang tidak dibutuhkan saat ini.
- Bump `versionCode` → 15, `versionName` → "1.15".
- **Audit v1.10 → v1.15 selesai semua**, kecuali icon (sudah, v1.14) dan branding lanjutan yang murni preferensi visual (bisa direvisi kapan saja kalau mau gaya lain).

## v1.14 - Dokumentasi + Icon launcher baru
- **README: tambah bagian Troubleshooting** — notifikasi tidak muncul, efek tidak kerasa, preset hilang, equalizer tidak muncul, build gagal di CI, dll. Sebelumnya panduan ini cuma ada di riwayat chat, sekarang permanen di repo.
- **Icon launcher didesain ulang total**: dari PNG statis gradient+bar sederhana, jadi **Adaptive Icon vector** (`mipmap-anydpi-v26`) dengan:
  - `ic_launcher_background.xml` — gradient diagonal pakai warna brand asli app (`#0A84FF` → `#64D2FF`, sama seperti Apple-blue di `Theme.kt`, bukan warna asal generate lagi)
  - `ic_launcher_foreground.xml` — motif 4-bar equalizer dengan ujung membulat, diposisikan presisi di safe-zone supaya tidak terpotong di mask icon bentuk apapun (bulat/squircle/kotak)
  - `ic_launcher_monochrome.xml` — varian untuk themed icon Android 13+ (Material You)
  - PNG lama di `mipmap-hdpi`…`mipmap-xxxhdpi` tetap dipertahankan sebagai fallback otomatis untuk device API 24–25 (di bawah Android 8), tidak perlu dihapus.
- Bump `versionCode` → 14, `versionName` → "1.14".

## v1.13 - Keamanan/kualitas rilis (.gitignore, automated test pertama)
- **Tambah `.gitignore`**: sebelumnya tidak ada sama sekali, ada risiko `release.keystore`, `build/`, `local.properties` ikut ter-commit ke repo publik kalau lupa exclude manual. Sekarang otomatis exclude keystore/jks, build output, .idea/.gradle, local.properties, apk/aab, dll.
- **Automated test pertama di project ini**: tambah Robolectric + JUnit (`PrefsHelperTest` — round-trip bass/virtualizer/loudness/preset aktif/tema/equalizer per-band lewat SharedPreferences) dan `FormatFreqLabelTest` (pure-Kotlin, label frekuensi equalizer). Sebelumnya semua verifikasi masih manual lewat log GitHub Actions.
- Bump `versionCode` → 13, `versionName` → "1.13".

### ⚠️ Belum disentuh — butuh keputusanmu (bagian vital, sesuai instruksi)
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`**: service ini pakai foreground service type `mediaPlayback` padahal app bukan media player asli (tidak play/pause audio sendiri, cuma nempel efek ke sesi audio lain). Ini berisiko ditolak Play Store review kalau publish publik, karena tidak sesuai kebijakan penggunaan type tersebut. Opsinya: ganti ke foreground service type lain yang lebih sesuai (misal `specialUse` di API 34+, perlu metadata justifikasi), atau tetap pakai `mediaPlayback` dengan risiko ditolak review. Belum diubah karena ini menyangkut arsitektur service & butuh testing ulang notifikasi — kasih tahu kalau mau saya kerjakan.
- **Icon launcher masih hasil generate sederhana** (gradient + bar equalizer) — belum lewat proses desain/branding matang untuk rilis publik. Ini kerjaan desain visual, bukan sekadar kode; kasih tahu kalau mau saya bikinkan beberapa alternatif icon baru.

## v1.12 - Reliability/fungsional (edge-to-edge, equalizer per-band UI, fix race condition, indikator strength)
- **Fix edge-to-edge**: `enableEdgeToEdge()` sekarang dibarengi `Modifier.safeDrawingPadding()` di root `Surface`, jadi konten tidak lagi berpotensi tumpang tindih dengan status bar/nav bar di HP dengan notch/punch-hole.
- **Equalizer per-band akhirnya punya UI**: sebelumnya `getEqualizer()`/`setEqualizerBand()` di service menggantung tanpa tampilan. Sekarang ada kartu "Equalizer Manual" (collapsible, disembunyikan default) yang menampilkan slider tiap pita frekuensi asli chipset HP — nilainya juga dipersist ke `PrefsHelper` per-band dan dipulihkan otomatis tiap service dibuat ulang (sebelumnya equalizer TIDAK ikut direstore sama sekali).
- **Fix silent fail BassBoost/Virtualizer**: tambah `isBassStrengthSupported()`/`isVirtualizerStrengthSupported()` (baca properti `strengthSupported` bawaan Android) di service. UI sekarang membedakan dua kondisi berbeda: "efek tidak ada sama sekali di HP ini" vs "efek aktif penuh tapi kontrol kekuatan bertingkat tidak didukung chipset" — sebelumnya exception `setStrength()` di-swallow diam-diam dan keduanya tampil sebagai pesan generik yang sama.
- **Fix race condition bind service**: kalau user geser slider (Bass/Virtualizer/Loudness/Equalizer) dalam <100ms setelah app dibuka sebelum `bindService()` selesai konek, perubahan itu sekarang ditampung di buffer (`pendingBass`/`pendingVirtualizer`/`pendingLoudness`/`pendingEqualizerBands`) dan otomatis diterapkan begitu service tersambung — sebelumnya perubahan itu silently no-op.
- Bump `versionCode` → 12, `versionName` → "1.12".

## v1.11 - Polish UX/desain (preset persisten, dark mode manual, splash icon asli, transisi onboarding)
- **Preset aktif kini persisten**: chip preset (Flat/Bass Heavy/dst) yang terakhir dipilih sekarang disimpan lewat `PrefsHelper` dan otomatis ter-highlight lagi saat app dibuka ulang — sebelumnya cuma nilai slidernya yang tersimpan, status "preset mana yang aktif" hilang tiap app ditutup.
- **Toggle dark/light mode manual**: tombol ikon baru di header (ikuti sistem ⇄ terang ⇄ gelap) memungkinkan user override tema, tidak lagi wajib ikut system theme. Pilihan tersimpan permanen via `PrefsHelper`.
- **Splash screen pakai icon aplikasi sendiri**: integrasikan `androidx.core:core-splashscreen`, tambah `Theme.App.Starting` di `themes.xml` dengan `windowSplashScreenAnimatedIcon` eksplisit ke `@mipmap/ic_launcher` (bukan lagi bergantung ke perilaku default sistem), lengkap dengan warna latar terang/gelap terpisah (`values/colors.xml` & `values-night/colors.xml`).
- **Transisi antar halaman onboarding kini smooth**: tiap halaman di `HorizontalPager` sekarang crossfade + scale halus mengikuti progres swipe (`graphicsLayer` + `currentPageOffsetFraction`), menggantikan perpindahan instan/patah sebelumnya.
- Bump `versionCode` → 11, `versionName` → "1.11".

## v1.10 - Hapus WakeLock yang tidak perlu (audit ketidakpastian)
- **FIX**: hapus `PARTIAL_WAKE_LOCK` dari `AudioEnhancerService` beserta permission `WAKE_LOCK` di Manifest. Wakelock ini tidak diperlukan (audio effect processing terjadi di level DSP/HAL sistem, bukan butuh CPU tetap nyala di app) dan berpotensi **kontraproduktif**: banyak OEM (MIUI dkk) justru menyasar app yang pegang wakelock sebagai "boros baterai" dan makin agresif membunuhnya di background.
- Servis sekarang murni mengandalkan `android:stopWithTask="false"` + `START_STICKY` untuk tetap hidup — tanpa mekanisme tambahan yang berisiko menambah ketidakpastian di device berbeda-beda.

## v1.9 - FIX ketidakpastian badge/notifikasi (root cause: FGS background start restriction)
- **AKAR MASALAH DITEMUKAN**: `onTaskRemoved()` sebelumnya aktif restart foreground service via broadcast setiap kali app di-swipe dari recent apps. Mulai Android 12, start foreground service dari background context (seperti dari `BroadcastReceiver` setelah app tidak foreground) dibatasi sistem secara **tidak konsisten** — kadang diizinkan kadang ditolak diam-diam tergantung timing OS. Ini penyebab notifikasi/badge status "kadang muncul kadang tidak".
- **FIX**: hapus total mekanisme restart manual itu. Cukup andalkan `android:stopWithTask="false"` (sekarang eksplisit ditulis di Manifest, bukan cuma default) + `START_STICKY` — kombinasi ini sudah membuat service tetap hidup walau task di-swipe, tanpa trik tambahan yang rawan gagal.
- Hapus `RestartReceiver.kt` yang sudah tidak terpakai (dead code) beserta deklarasinya di Manifest, supaya arsitektur restart lebih sederhana dan dapat diprediksi: hanya `BootReceiver` (untuk reboot HP) + `START_STICKY` (untuk low-memory kill oleh sistem) — dua mekanisme yang didukung resmi dan konsisten oleh Android.

## v1.8 - FIX notifikasi tidak muncul (izin runtime Android 13+)
- **FIX**: Android 13+ (API 33) mewajibkan izin runtime `POST_NOTIFICATIONS` — sudah dideklarasikan di Manifest sejak awal tapi belum pernah diminta aktif ke user, sehingga notifikasi "Audio Booster aktif" disembunyikan total oleh sistem meski service tetap berjalan.
- Tambah `requestNotificationPermissionIfNeeded()`: otomatis minta izin notifikasi saat app pertama dibuka (khusus API 33+, tidak berlaku/tidak perlu di versi Android lebih lama).
- Tambah banner peringatan di `BoosterScreen` kalau izin ditolak, lengkap dengan tombol "Buka Pengaturan Notifikasi" langsung ke halaman setting app.
- Status izin dicek ulang otomatis tiap `onResume()`, jadi kalau user aktifkan manual lewat Settings, banner otomatis hilang begitu balik ke app.

## v1.7 - Fix build gagal (enableEdgeToEdge)
- Fix `Unresolved reference: enableEdgeToEdge` — sebelumnya dipanggil pakai nama package lengkap (`androidx.activity.enableEdgeToEdge()`) yang tidak valid untuk extension function di Kotlin. Sekarang di-`import` dengan benar (`import androidx.activity.enableEdgeToEdge`) lalu dipanggil langsung (`enableEdgeToEdge()`).

## v1.6 - Artifact dinamis, dark mode premium, output tunggal
- **Nama artifact & APK dinamis**: workflow sekarang membaca `versionName` langsung dari `app/build.gradle.kts` dan memakainya untuk nama file APK (`AudioEnhancerPro-v{versi}-release.apk`) serta nama artifact GitHub Actions — tidak lagi nama statis.
- **Output tunggal**: workflow disederhanakan jadi 1 job (`release`) yang menghasilkan **hanya 1 artifact** (APK release signed). Job `build` (debug) dihapus total sesuai permintaan.
- **Dark mode premium "Apple experience"**: `Theme.kt` baru — palet warna terinspirasi iOS system colors (biru `#0A84FF`/`#007AFF`, permukaan gelap berlapis `#1C1C1E`/`#2C2C2E`, bukan hitam pekat rata), tipografi dengan tracking rapat ala SF Pro, shape membulat generous (12–28dp) di semua kartu/tombol. Otomatis ikut dark/light mode sistem HP.
- Tambah edge-to-edge display (`enableEdgeToEdge()`) untuk konten yang menyatu ke tepi layar.
- Bump `versionName` ke `"1.6"` dan `versionCode` ke `6` — wajib dinaikkan manual tiap rilis (lihat panduan "Versioning APK Release" di README).

## v1.5 - Indikator status service real-time
- Tambah `ServiceStatusBadge`: badge hijau/merah di layar utama yang mengecek `AudioEnhancerService.isRunning` tiap 1 detik, jadi user langsung tahu apakah booster benar-benar aktif di background tanpa perlu tarik notification bar.

## v1.4 - Polish: minify, preset cepat, deteksi device tak support, FIX pengaturan tidak persisten
- **FIX BUG PENTING**: pengaturan Bass Boost/Virtualizer/Loudness sebelumnya cuma tersimpan di memori (state UI), hilang total setiap app ditutup atau service di-restart sistem — makanya kerasa "balik ke default" tiap keluar app. Sekarang semua nilai disimpan ke `SharedPreferences` via `PrefsHelper` setiap kali diubah, dan `AudioEnhancerService` otomatis menerapkan ulang nilai tersimpan itu setiap kali efek audio dibuat (termasuk saat auto-restart dari `onTaskRemoved`/boot) — jadi setting benar-benar persisten dan aktif terus, bukan cuma saat app kebuka.
- UI (`BoosterScreen`) sekarang juga menampilkan nilai slider terakhir yang tersimpan saat dibuka, bukan selalu mulai dari nilai default.
- Aktifkan `isMinifyEnabled = true` + `isShrinkResources = true` di build release (R8) untuk APK lebih kecil dan sedikit lebih sulit di-reverse. Tambah `proguard-rules.pro` jaga-jaga untuk Kotlin metadata.
- Tambah 4 preset cepat (Flat, Bass Heavy, Vocal Boost, Treble Boost) sebagai chip yang langsung set 3 efek sekaligus — user tidak perlu geser slider satu-satu.
- `AudioEnhancerService`: tambah `isBassSupported()`, `isVirtualizerSupported()`, `isLoudnessSupported()` untuk deteksi kalau chipset/HP tidak mendukung efek tertentu.
- `BoosterScreen`: slider yang tidak didukung otomatis di-disable dengan pesan jelas ("Tidak didukung di HP ini"), plus banner peringatan di atas kalau ada efek yang tidak tersedia — daripada diam-diam gagal.

## v1.3 - Onboarding rinci per fitur
- `OnboardingScreen.kt`: onboarding 6 halaman (welcome, Bass Boost, Virtualizer, Loudness Gain, kenapa butuh izin baterai/autostart, penjelasan notifikasi persisten) dengan swipe pager + indikator titik + tombol Lewati/Lanjut.
- Onboarding otomatis muncul di pembukaan pertama (status disimpan via `PrefsHelper`/SharedPreferences), dan bisa dibuka ulang kapan saja lewat ikon bantuan (?) di layar utama.
- `BoosterScreen`: tiap slider (Bass Boost, Virtualizer, Loudness Gain) sekarang punya deskripsi singkat langsung di bawah judulnya, plus kartu penjelasan izin baterai/autostart, jadi user paham fungsi tiap kontrol tanpa harus buka onboarding.
- Tambah dependency `material-icons-extended` untuk ikon bantuan.

## v1.2 - Fix build gagal (AndroidX)
- Tambah `gradle.properties` dengan `android.useAndroidX=true` dan `android.nonTransitiveRClass=true`. Sebelumnya file ini belum ada sehingga build gagal: "Configuration :app:debugRuntimeClasspath contains AndroidX dependencies, but android.useAndroidX property is not enabled".

## v1.1 - Icon asli + Release signing CI
- Ganti icon placeholder dengan launcher icon asli (motif equalizer, gradasi ungu-biru) di semua density mdpi–xxxhdpi + versi round.
- Tambah `signingConfigs.release` di `app/build.gradle.kts` yang baca keystore & password dari environment variable (aman untuk CI, tidak hardcode di repo).
- Tambah job `release` di GitHub Actions: decode keystore dari secret `KEYSTORE_BASE64`, build `assembleRelease` bersanding, upload APK release signed sebagai artifact.
- Tambah panduan lengkap generate keystore + setup GitHub Secrets di README.

## v1.0 - Initial scaffold
- Setup proyek Kotlin + Jetpack Compose (Gradle KTS, minSdk 24, targetSdk 34).
- `AudioEnhancerService`: foreground service (mediaPlayback) yang menempelkan BassBoost, Virtualizer, Equalizer, LoudnessEnhancer ke audio session 0 (output sistem global).
- Mekanisme anti-kill: `START_STICKY`, wakelock partial, restart otomatis via `onTaskRemoved` + `RestartReceiver`, auto-start via `BootReceiver` saat device reboot.
- `MainActivity`: UI Compose dengan slider Bass Boost, Virtualizer, Loudness Gain + tombol request ignore battery optimization.
- Notifikasi foreground persisten dengan tombol "Matikan".
- GitHub Actions workflow (`build.yml`) untuk build APK debug otomatis tiap push.
