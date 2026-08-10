# 🧠 PROJECT_STATE.md — baca file ini PALING PERTAMA

File ini didesain buat dibaca AI (Claude) di awal sesi baru, bukan cuma manusia.
Isinya padat & langsung actionable — bukan riwayat lengkap (itu ada di
CHANGELOG.md). Kalau kamu Claude dan baru diminta lanjut project ini:
1. Baca file ini full.
2. Baca 2-3 entry TERATAS CHANGELOG.md aja (bukan semua) buat detail teknis terbaru.
3. Baru mulai kerja. Jangan ulang pertanyaan yang jawabannya udah ada di sini.

---

## Status saat ini
- **Versi**: v1.79.0
- ⚡ **Batch 40 (v1.79.0, BELUM diverifikasi CI)**: pangkas waktu compile CI,
  diminta user eksplisit ("apapun caranya"). Infra-only, 0 file Kotlin disentuh.
  Ganti bootstrap wrapper manual (Batch 19-21) -> `gradle/actions/setup-gradle@v4`
  (provision + **cache** Gradle 8.7 + dependency + Build Cache antar-run CI —
  sebelumnya TIDAK ADA caching sama sekali, ini gap terbesar). + flag
  `gradle.properties` (parallel/caching/kapt speed). + job `release` gak lagi
  `needs: build` (jalan paralel, bukan sequential). **PENTING buat sesi depan**:
  efek cache BARU kelihatan di run CI KE-2 dst (run pertama tetap cold-cache,
  durasi mirip biasanya) — kalau user lapor "run pertama kok masih lama", itu
  NORMAL, bukan gagal, minta user push sekali lagi (perubahan kecil apapun) buat
  lihat run kedua. Detail lengkap: `CHANGELOG.md` v1.79.0.
- 🔩 **Batch 39 (v1.78.0, riwayat)**: 2 perbaikan varian
  Skeuomorphism (Batch 38) diminta user eksplisit setelah ditanya "apakah otonom
  penuh, gak numpang baseline default":
  1. **Radius/shape sekarang 100% otonom** — sebelumnya `SkeuCardRadius`/
     `SkeuIconBoxRadius` (const global 26dp/16dp, era iOS-glass Batch 37) dipakai
     SEMUA varian termasuk Skeuomorphism. Sekarang `SkeuTokens` punya field
     `cardRadius`/`iconBoxRadius` per-varian: 2 varian glass tetap 26dp/16dp,
     Skeuomorphism dapat radius sendiri `SkeuoCardRadius`/`SkeuoIconBoxRadius`
     (14dp/10dp, lebih tegas/kecil, khas hardware fisik). Material3 `Shapes`
     (dipakai default `Button`/`AlertDialog`/dll) juga di-pisah:
     `SkeuomorphismShapes` baru (4-20dp) vs `AppShapes` lama (10-34dp, 2 varian
     glass), dipilih di `AudioEnhancerTheme` composable via `themeStyle`.
  2. **Aksen tembaga -> titanium+silver metalik** — `SkeuoAccent` `#C98A4C` ->
     `#AEB4BF` (silver cool-neutral, hint biru-baja). Token baru `SkeuoAccentDeep`
     (`#6E737D`) dipakai buat variasi 2-stop metalik di `SkeuoBevelBrush` (bukan
     cuma di 1 accent chip — karakter metalik sekarang kerasa di SELURUH panel
     kartu). `SkeuomorphismDarkColors` (onPrimary/primaryContainer/
     onPrimaryContainer) disesuaikan ke neutral-cool.
  - File: `Theme.kt`, `SkeuomorphicComponents.kt` (baca `tokens.cardRadius`/
    `iconBoxRadius`, bukan const global lagi), `values/strings.xml`+`values-en/
    strings.xml` (desc toggle: "titanium-silver metalik", parity tetap 98/98).
  - **SENGAJA TIDAK diubah**: 2 varian glass (radius/shape/warna utuh), panel
    netral Skeuomorphism (`SkeuoBackground`/`SkeuoPanel`, sudah netral/cocok buat
    titanium-silver, gak perlu diubah).
  - Detail lengkap: `CHANGELOG.md` v1.78.0.
- ➕ **Batch 38 (v1.77.0, BELUM diverifikasi CI/runtime)**: tambah varian tema ke-3
  "Skeuomorphism" (dark-mode asli, bahasa desain fisik/bevel — BUKAN glass), diminta
  user eksplisit "gak kurang gak lebih". Toggle baru di Settings, PERSIS DI BAWAH
  toggle "Gaya Aurora Glass" (posisi sesuai permintaan), 1 pilihan tunggal dari 3
  opsi (nyalain 1 otomatis matiin yang lain, matiin balik ke default Midnight Glass).
  - `AppThemeStyle` enum: 2->3 nilai (`SKEUOMORPHISM` baru). `PrefsHelper`: const baru
    `APP_THEME_SKEUOMORPHISM`, const lama TIDAK diubah (persistence user lama aman).
  - Token baru di `Theme.kt`: `SkeuoXxx` (panel gunmetal netral, bevel extrusion kuat,
    aksen tembaga #C98A4C — SENGAJA beda hue dari accent biru 2 varian glass),
    `SkeuomorphismSkeuTokens` (cardElevation 8dp, paling kuat dari 3 varian),
    `SkeuomorphismDarkColors`, `SkeuoScreenBackgroundBrush`.
  - `MainActivity.kt`: mapping String->enum & screenBrush pakai `when` (bukan
    if/else lagi). `BoosterScreen.kt`: param `themeStyleIsRadical: Boolean` ->
    `appThemeStyleKey: String` (perlu String karena sekarang 3 opsi saling
    eksklusif, bukan cuma on/off) — kartu toggle Skeuomorphism ditambah persis di
    bawah kartu Aurora Glass, struktur SAMA PERSIS (SkeuCard+Icon+judul+desc+
    SkeuSwitch) biar konsisten visual.
  - `SkeuomorphicComponents.kt` TIDAK disentuh — sudah generik lewat
    `LocalSkeuTokens`, otomatis kompatibel varian baru.
  - String baru `theme_style_skeuo_title`/`theme_style_skeuo_desc` (ID+EN, parity
    98/98). `docs/preview/current.html` cuma footer note diperbarui (TIDAK ada
    mockup visual terpisah utk varian ini — di luar scope "toggle + theme").
  - **Belum divalidasi runtime** — statis only (brace/paren 0/0 semua file Kotlin
    disentuh + full sweep project, parity string 98/98, grep `AppThemeStyle` di luar
    3 file yang disentuh: nihil). Detail lengkap: `CHANGELOG.md` v1.77.0.
- 🎨 **Batch 37 (v1.76.0, BELUM diverifikasi CI/runtime)**: REWRITE TOTAL sektor
  UI/UX diminta user eksplisit — "iOS-style Glassmorphism" jadi bahasa desain
  DOMINAN di seluruh app, Midnight-Blue sekarang hint yang KENTARA (ambient alpha
  0.06->0.20, bukan subtle lagi), readability tetap prioritas #1 (kontras teks
  dinaikkan tegas, bukan dikorbankan demi efek kaca). Arsitektur 2-varian
  (`SkeuTokens`/`AppThemeStyle`/switch Settings, Batch 36) DIPERTAHANKAN — user
  tidak minta fitur switch dihapus — tapi ISI kedua varian ditulis ulang total:
  1. **Kartu struktural**: 4-stop diagonal glass gradient (ganti 3-stop rata lama)
     + layer sheen KEDUA (`tokens.specularBrush`, field baru di `SkeuTokens`) di
     pojok kiri-atas via `.background()` dipanggil 2x berurutan di
     `SkeuCard`/`SkeuTintedCard`/`SkeuPowerButton` — ini yang bikin kartu kebaca
     sebagai KACA sungguhan, bukan cuma kartu solid biru. Border kartu jadi
     gradient highlight->transparan (`GlassBorderBrush`), bukan solid alpha tipis.
  2. **Varian ke-2** (const/enum TETAP `RADICAL_SKEUO` di kode — Protected Asset
     persistence key, TIDAK diganti biar data user lama valid) SEKARANG juga glass
     genuine (`RadicalGlassBrush`), BUKAN lagi skeuomorphism bevel-raised solid flat
     era Batch 36. Copy user-facing di Settings diganti "Gaya Aurora Glass"/"Aurora
     Glass Style" (lebih vivid/saturated) — default tetap "Midnight Glass" (restrained).
  3. **Background layar** (`MainActivity.kt`, edit parsial) ganti dari flat
     `colorScheme.background` ke `ScreenBackgroundBrush`/`AuroraScreenBackgroundBrush`
     (gradient vertikal Midnight-Blue->nyaris-hitam) — glassmorphism butuh backdrop
     bervariasi supaya translucency kartu "kebaca".
  4. Radius dinaikkan (kartu 20->26dp, icon-orb 14->16dp, `AppShapes` semua step) —
     lebih membulat ala iOS, bukan radius standar Android lama.
  5. Kontras teks: `TextPrimary`/`TextSecondary`/`TextMuted` (+ setara Aurora) semua
     dinaikkan — permintaan user "readability maksimal" eksplisit.
  - File lain yang ikut disinkronkan (bukan sumber logic, tapi WAJIB konsisten):
    `values/colors.xml`+`values-night/colors.xml` (splash hex), `drawable/
    widget_background.xml` (gradient RemoteViews XML, gak bisa pakai Compose Brush),
    `drawable/ic_shortcut_preset.xml` (fill warna), `values/strings.xml`+`values-en/
    strings.xml` (copy switch tema), `docs/preview/current.html` (ground truth
    visual, WAJIB disinkron — lesson lama Batch 33/34 soal preview HTML gak boleh
    stale).
  - **SENGAJA TIDAK diubah**: nama const persistence & enum `AppThemeStyle` (lihat
    poin 2 di atas), warna aksen per-fitur (Bass/Virtualizer/Loudness/Equalizer,
    bukan sumber keluhan), typography scale (resiko layout tanpa compiler tidak
    sepadan), app launcher icon (`ic_launcher_background.xml`, di luar scope "sektor
    UI & UX layar app").
  - **PENTING buat sesi depan**: kalau nambah komponen Skeu baru yang butuh
    background glass, WAJIB pakai pola `.background(tokens.cardBrush).background(
    tokens.specularBrush)` (2 background berurutan = base + sheen), JANGAN cuma 1
    background flat — itu yang bikin efek "kaca" hilang lagi & balik ke kesan
    Batch 36 (kartu solid berwarna, bukan glassmorphism).
  - **Belum divalidasi runtime** — statis only (brace/paren 0/0 di semua file
    Kotlin yang disentuh + full sweep project, parity string ID/EN 96/96, XML valid).
    `Modifier.background(brush)` dipanggil 2x berurutan pada 1 chain modifier
    (SkeuCard/SkeuTintedCard/SkeuPowerButton) API standar Compose foundation, tapi
    pola pemakaian 2x berurutan ini PERTAMA KALI dipakai di project ini — kandidat
    pertama dicurigai kalau ada laporan sheen kartu gak nongol/salah posisi/nutup
    teks. Detail lengkap: `CHANGELOG.md` v1.76.0.
- 🔧 **Batch 36 Fix (v1.75.1, riwayat)**: CI run #81 FAILED di
  `kaptGenerateStubsDebugKotlin`
- 🔧 **Batch 36 Fix (v1.75.1)**: CI run #81 FAILED di `kaptGenerateStubsDebugKotlin`
  (`e: Could not load module <Error module>` — generik, kapt swallow detail). Root cause:
  `Theme.kt` field baru `SkeuTokens.cardElevation: Dp` pakai tipe `Dp` tanpa
  `import androidx.compose.ui.unit.Dp` (yang ada cuma `import ...unit.dp` extension
  property). Fix: tambah 1 baris import. **PENTING buat sesi depan**: kalau ketemu error
  kapt generik serupa lagi, cek dulu semua type annotation eksplisit di file yang BARU
  disentuh batch itu (paling sering `Dp`/`Color`/`Brush` yang lupa di-import class-nya,
  cuma extension function-nya doang) — jangan langsung curiga config/dependency. Detail:
  `CHANGELOG.md` v1.75.1.
- 🎨 **Batch 36 (v1.75, BELUM diverifikasi CI/runtime)**: fitur baru diminta user —
  "setting custom switch theme" yang konfigurasinya 100% mengikuti guide baru user-upload
  `compose-skeuomorphism-radical-literal-dark-readability-performance-final.md`. Guide ini
  BEDA TOTAL secara filosofi dari AMOLED Hybrid Glass (Batch 33-35, tema aktif sebelumnya):
  Radical = bevel/geometry-first + kartu struktural ikut physical construction; AMOLED
  Glass = frosted-glass first, kartu sengaja "quiet"/flat. **PENTING — scope dikonfirmasi
  via `ask_user_input_v0` SEBELUM implementasi** (ikuti lesson Batch 34 di bawah: guide
  baru yang beda detail dari batch terakhir JANGAN diasumsikan otomatis "ganti total"):
  user pilih **kedua tema TETAP ADA, dipilih via 1 switch baru di Settings** — BUKAN
  replace tema aktif, BUKAN switch berdiri sendiri di luar konteks tema.
  1. **Arsitektur**: `SkeuTokens` data class baru (`Theme.kt`) + `LocalSkeuTokens`/
     `LocalAppThemeStyle` CompositionLocal — komponen (`SkeuCard`/`SkeuTintedCard`/
     `SkeuPowerButton`/`SkeuSliderThumb`/`SkeuSwitch`/`FeatureControl`) baca token
     dinamis, BUKAN lagi val top-level `Glass*`/`TextMuted`/`SkeuBevelBrush`/
     `SkeuPrimaryGlow` hardcoded. **PENTING buat sesi depan**: kalau nambah komponen
     Skeu baru yang butuh warna surface/border/muted-text/glow, WAJIB baca dari
     `LocalSkeuTokens.current`, JANGAN reference `Glass*`/`Radical*` val langsung —
     kalau begitu komponen itu TIDAK akan ikut berubah pas user switch tema.
  2. **Semua** referensi `TextMuted`/`SkeuPrimaryGlow` di `BoosterScreen.kt` (9 lokasi
     total) ikut di-migrasi ke token dinamis — bukan cuma di kartu switch baru, biar
     readability & glow 100% konsisten ke tema aktif di SELURUH layar (guide baru §26 +
     final verdict eksplisit soal ini).
  3. Persisted via `PrefsHelper.getAppThemeStyle`/`setAppThemeStyle` (String
     "amoled_glass"/"radical_skeuo", default AMOLED — user lama tidak berubah kalau
     belum sentuh switch baru), di-map ke `AppThemeStyle` enum di `MainActivity.kt`.
  - **SENGAJA TIDAK dikerjakan (transparan, biar gak dikira 100%)**:
    (a) `docs/preview/current.html` TIDAK disinkronkan — HANYA merepresentasikan AMOLED
    Glass, state tema Radical TIDAK ada di sana. Kalau user minta validasi visual
    Radical, JANGAN rujuk HTML preview, harus build APK asli. (b) Splash screen
    (`values/colors.xml` dkk) TIDAK ikut berubah per-tema — tetap 1 warna AMOLED Glass
    apapun switch-nya (render sebelum Compose tahu preferensi user).
  - **Belum divalidasi runtime** — statis only (brace/paren balance semua file Kotlin,
    parity string ID/EN 96/96, XML valid). `CompositionLocalProvider` 3-value di
    `AudioEnhancerTheme` & `SkeuTokens` data class BARU PERTAMA KALI dipakai project
    ini — kandidat pertama dicurigai kalau ada laporan render aneh (switch gak
    ke-apply, tema gak konsisten di sebagian komponen).
  - Detail lengkap per-file: lihat CHANGELOG.md v1.75.
- 🎨 **Batch 35 (v1.74, riwayat)**: user minta gabung SEMUA item
  opsional yang disebutkan di closing note Batch 34 jadi 1 batch (biar gak numpuk
  technical debt). 2 item, KEDUANYA CLOSED sekarang:
  1. `TextMuted` (dead code sejak Batch 34) sekarang dipakai di semua teks caption-tier
     — daftar lengkap file/lokasi ada di CHANGELOG.md v1.74. `onSurfaceVariant`
     (`TextSecondary`) tetap dipakai buat teks "Secondary" tier, TextMuted khusus
     caption/hint/unselected-label. Icon tint (bukan teks) SENGAJA tetap onSurfaceVariant
     — di luar scope §16 tipografi.
  2. §15 Navigation dicek ulang & DIKONFIRMASI N/A permanen — app single-screen, gak ada
     nav component. JANGAN dicek ulang lagi sesi depan kecuali app beneran nambah nav
     bottom-bar/rail baru.
  - 0 perubahan hex/token warna di batch ini — murni migrasi pemakaian token existing,
    resiko regresi rendah.
- 🎨 **Batch 34 (v1.73, BELUM diverifikasi CI/runtime)**: KOREKSI Batch 33 — user
  eksplisit bilang "salah kirim" file acuan Batch 33, upload ulang file yang benar:
  `compose-amoled-hybrid-glass-final.md` ("Premium AMOLED Hybrid Glassmorphism +
  Subtle Midnight Blue + Micro-Skeuomorphism"). PENTING buat sesi depan: kalau ada
  guide baru lagi yang KELIHATAN mirip tapi beda detail sama yang dipakai batch
  terakhir, JANGAN asumsikan itu iterasi tambahan — cek dulu apa ini koreksi/ganti
  total kayak kasus ini (2x berturut-turut user upload file salah dulu). Detail
  lengkap diff Batch 33→34 ada di CHANGELOG.md v1.73. Ringkasan poin kunci:
  1. Filosofi geser: Batch 33 tactile/bevel-first ("skeuomorphism-lite" sbg identitas
     kedua) → Batch 34 GLASS adalah material utama, skeuomorphism turun jadi "micro"
     (cuma buat physical controls: button/switch/slider/knob).
  2. Nama token warna beda (`GlassBase` bukan `GlassSurface`, dst) meski sebagian
     hex sama — SEMUA referensi lama di-rename total, bukan cuma reuse.
  3. `MidnightBlueAmbientAlpha` 0.06 (bukan 0.08 Batch 33) — guide baru kasih angka
     eksplisit beda.
  4. Slider knob "metallic realism" (radial gradient putih→accent ala dial logam,
     bertahan dari Batch 31 sampai 33) AKHIRNYA dicabut — guide baru eksplisit
     melarang pola ini di §13. Ganti radial restrained tint accent tanpa sheen putih.
  5. Nemu 2 BUG dari sweep Batch 33 yang gak lengkap: `docs/preview/current.html`
     `.slider-thumb` (masih metallic gradient) dan `.skeu-switch.on` border-color
     (masih hardcode bronze `rgba(194,162,107,.6)` — literally warna lama Batch <31
     yang lolos 2 batch theme-rewrite berturut-turut karena preview HTML gak
     ke-grep bareng file .kt). **Lesson**: grep sweep integrity-check theme rewrite
     ke depan WAJIB include `*.html` juga, bukan cuma `*.kt`/`*.xml`.
  6. `AmoledBackground` (1 token, dipakai buat splash+background sekaligus di Batch
     33) dipecah jadi 2: `AmoledBlack` (splash/root sejati) + `AmoledSurface`
     (`colorScheme.background`, canvas layar) — guide §3 eksplisit minta 2-tone biar
     glass Level 1+ "perceptible" di atas root.
- 🎨 **Batch 33 (v1.72, BELUM diverifikasi CI/runtime, referensinya SALAH — lihat
  Batch 34)**: user upload guide baru
  `compose-skeuomorphism-lite-amoled-glass-hybrid-midnight-gradient.md` + perintah
  eksplisit "timpa theme lama hingga bersih, wajib 100% sesuai". BEDA sama Batch 32:
  Batch 32 sengaja MEMPERTAHANKAN graphite `#232220` karena guide lama cuma kasih
  "suggested palette direction" (bukan wajib hex). Guide baru ini + perintah user
  MEWAJIBKAN implementasi 100% — jadi keputusan Batch 32 itu di-OVERRIDE sengaja,
  BUKAN diabaikan tanpa alasan. Detail lengkap di CHANGELOG.md v1.72. Ringkasan:
  1. Palet total ganti ke AMOLED (`#030508`) + Glass surfaces (`#0A0F16`/`#101722`) +
     Midnight Blue HANYA sebagai tint subtle di dalam gradient glass (`MidnightBlueTint`
     alpha 0.08) — BUKAN identitas background dominan (guide §21 "Final Composition
     Constraint": salah kalau jadi "blue interface with black elements").
  2. `primary`/state-aktif/glow ganti dari bronze `#C2A26B` ke `MidnightBlueAccent
     #6670FF`.
  3. `SkeuCard`/`SkeuTintedCard` (kartu struktural) yang sebelumnya solid flat
     (Batch 31 sengaja bikin gini ikut guide lama poin 3) SEKARANG wajib frosted-glass
     gradient (guide §2.5) — tapi TETAP "quiet" dibanding tactile control fisik (guide
     §8 gak berubah, cuma materialnya).
  4. Splash screen (`values/colors.xml`) yang sebelumnya punya varian terang `#F7F5F1`
     DICABUT — disamakan ke AMOLED persis kayak `values-night`, konsisten sama guide
     §1.1/§13 "no light-mode fallback ever" (celah lama: splash bisa kilat terang
     sebelum Compose theme render).
  5. `docs/preview/current.html` (ground truth visual project ini per CHANGELOG) di-sync
     juga, TIDAK dibiarkan stale — kalau nggak, sesi depan bisa salah rujuk visual lama.
  - Feature accent colors (Bass/Virtualizer/Loudness/Equalizer/Battery) SENGAJA tetap
    dipertahankan — itu identitas per-fitur, bukan bagian surface hierarchy yang diatur
    guide.
- 🎨 **Batch 32 (v1.71)**: user upload versi acuan design
  guide yang LEBIH DETAIL (`compose-skeuomorphism-lite-dark.md`, beda dari
  `compose-skeuomorphism-lite.md` yang jadi basis Batch 31) — diminta terapkan poin yang
  BELUM ada dari update Batch 31. Diff dicek poin-per-poin ke Definition-of-Done guide
  (section 14) + dibandingkan ke `docs/preview/current.html` (yang ternyata SUDAH lebih
  maju dari Kotlin di 2 hal — sumber gap konkret, bukan tebakan):
  1. **Glow state aktif (guide §9 "Glow Rules")**: token `SkeuPrimaryGlow` (Theme.kt)
     sebelumnya DIDEFINISIKAN TAPI 0 PEMANGGIL — power button ON & chip preset aktif
     cuma punya ring/border solid, gak ada halo lembut yang HTML preview sudah punya
     sejak awal (`box-shadow: 0 0 16px var(--glow)` di `.power-btn.on`, `0 0 10px` di
     `.chip.active`). Fix: `Modifier.skeuGlow(color, spread)` baru (native
     `Brush.radialGradient` di `drawBehind`, BUKAN `BlurMaskFilter`/Paint hack — lesson
     Batch 14 soal shadow custom gak reliable lintas API tanpa compiler tetap berlaku),
     dipasang di `SkeuPowerButton` (saat `pressed=true`) & dibungkus `Box` di 2 loop chip
     preset (`BoosterScreen.kt`) saat `selected`. Urutan modifier SENGAJA sebelum
     `.shadow()`/`.clip()` supaya halo boleh \"bleed\" keluar batas shape.
  2. **Switch tactile (guide §7 "Toggles / Switches", eksplisit minta physical
     indentation)**: toggle Material You ("Warna ikut wallpaper", satu-satunya `Switch`
     di app) sebelumnya pakai `Switch` Material3 BAWAAN POLOS — 0 treatment tactile sama
     sekali, gap paling jelas terhadap guide baru. Fix: `SkeuSwitch` baru
     (`SkeuomorphicComponents.kt`) — track pill (OFF abu netral/`surfaceVariant` murni =
     "recessed/muted", ON blend 35% ke `accentColor` = "illuminated"), thumb bundar
     (OFF `SkeuSurfaceTop` datar, ON solid `accentColor` + `skeuGlow` tipis — 2 cue
     sekaligus: posisi/offset thumb DAN warna, sesuai syarat a11y guide "state must not
     depend solely on structural changes"), PRESSED = thumb `scale 0.88` sesaat (micro-
     interaction §6). `onCheckedChange = null` -> non-interaktif murni (pola sama kayak
     `Switch` Material3 lama — parent `Row` di `BoosterScreen.kt` yang pegang
     `toggleable`-nya sendiri, TIDAK diubah).
  - **Dicek TAPI TERNYATA BUKAN gap** (biar gak diulang tanya/dikerjain 2x sesi depan):
    (a) arah cahaya "top-left → bottom-right" (guide §3) — `SkeuBevelBrush`/
    `SkeuBevelBorderBrush` (Theme.kt) SUDAH diagonal SECARA DEFAULT karena
    `Brush.linearGradient(colors)` tanpa `start`/`end` eksplisit di Compose otomatis
    resolve ke diagonal pojok-ke-pojok bounding box saat digambar — TIDAK perlu
    diubah. (b) background AMOLED near-black (guide Definition-of-Done) — HTML preview
    (ground truth desain yang sudah divalidasi sejak Batch 31) SENGAJA TETAP pakai
    graphite `#232220`, BUKAN near-black `#05070A` contoh di guide (itu cuma "suggested
    palette direction", bukan hex wajib) — TIDAK diubah, biar konsisten sama HTML yang
    sudah jadi acuan visual project ini.
  - **PENTING buat sesi depan**: kalau butuh glow lagi di komponen baru, pakai
    `Modifier.skeuGlow(color, spread)` yang sudah ada (`SkeuomorphicComponents.kt`) —
    JANGAN reimplementasi Paint/BlurMaskFilter manual lagi. Dipakai SELEKTIF (cuma state
    aktif/selected, bukan didekorasi ke semua kartu/border — guide eksplisit larang itu
    di §9 & §13 "Implementation Guardrails").
  - **Belum divalidasi runtime** — statis only (brace/paren balance semua file Kotlin
    project, bersih). `animateColorAsState`/`toggleable`/`drawBehind`/`Brush.radialGradient`
    semua API Compose standar yang sudah lama stabil, tapi API `Modifier.skeuGlow` +
    `SkeuSwitch` ini pertama kali dipakai di project ini — kandidat pertama dicurigai
    kalau ada laporan render aneh (glow gak nongol/ke-clip, switch thumb salah posisi).

- 🎨 **Batch 31 (v1.70, riwayat)**: DESIGN LANGUAGE PIVOT TOTAL —
  "Neumorphic Hybrid" (Batch 12-26) DICABUT, ganti ke **"Skeuomorphism-lite (Tactile UI)"**
  sesuai acuan `compose-skeuomorphism-lite.md` user, **WAJIB dark-mode** (theme mode
  toggle terang/sistem dihapus total, tidak ada lagi pilihan). Detail teknis lengkap
  di CHANGELOG.md & "Riwayat pivot" di bawah.
- ✅ **CI CONFIRMED (user) crash-loop v1.68 gak muncul lagi** — banner cuma nampilin crash
  lama pre-update, app jalan normal.
- ⚠️ **Batch 30 (v1.69, BELUM diverifikasi CI/runtime)**: empty state hint di preset custom
  (`BoosterScreen.kt`, string baru `presets_empty_hint`, ID+EN parity 97/97) — polish kecil,
  murni tambahan 1 Text kondisional, nol perubahan logic/state. Detail di CHANGELOG.md.
- **Batch 28 (v1.67, riwayat)**: hotfix CI compile error — `const val RELATIVE_PATH` pakai
  `Environment.DIRECTORY_DOCUMENTS` (bukan compile-time constant Kotlin), fix: hapus `const`.
  **LESSON**: `const val` di Kotlin CUMA valid kalau nilainya bisa di-resolve compiler tanpa
  runtime (literal String/Int/dll). Field dari API Android manapun (`Environment.*`, `Build.*`,
  dst) BUKAN compile-time constant walau kelihatannya "konstan" secara semantik — WAJIB `val`
  biasa.
- **Batch 27 (v1.66, riwayat)**: rewrite `CrashLogger.kt` ke MediaStore (standing spec) —
  implementasi desain TETAP benar (terbukti Batch 29), detail lengkap di CHANGELOG.md.
- ✅ **CI CONFIRMED HIJAU di v1.65** (body Release dinamis dari CHANGELOG, dikonfirmasi
  user via screenshot — bukan link compare kosong lagi).
- ✅ **CI CONFIRMED HIJAU di v1.64** (hotfix `NoRippleIndication` Batch 25 berhasil, Release
  v1.64 sukses publish dengan APK signed — dikonfirmasi user via screenshot sidebar Releases).
- ⚠️ **Batch 26 (v1.65, BELUM diverifikasi run CI berikutnya)**: (1) body GitHub Release
  sekarang diambil dinamis dari entry CHANGELOG.md versi yang lagi dirilis (`awk` extract di
  `.github/workflows/build.yml`, `body_path:` bukan `generate_release_notes:true` lagi) — cek
  halaman Release v1.65 kalau isinya sudah sesuai entry CHANGELOG, bukan link compare kosong
  lagi. (2) Polish kecil `BoosterScreen.kt`: batas 24 karakter + counter nama custom preset,
  haptic feedback ditambah di 5 titik yang sebelumnya kelewat (restart service, retry
  connection, buka setting notifikasi, confirm simpan/hapus preset). Detail lengkap di entry
  Batch 26 CHANGELOG.md. Statis-only (brace/paren balance OK, parity string ID/EN 96/96, YAML
  workflow syntax valid via `python3 -c "import yaml"`) — TIDAK ada compile-check sungguhan.
- **PENTING buat sesi depan**: kalau nambah entry CHANGELOG.md versi baru, WAJIB pertahankan
  format heading persis `## v<versi> - <judul>` (spasi sebelum & sesudah `v<versi>`) — step
  ekstraksi release notes di CI (Batch 26) match berdasarkan prefix string ini, kalau formatnya
  berubah body Release akan fallback ke placeholder kosong.
- ⚠️ **Batch 25 (v1.64, riwayat)**: hotfix CI v1.63 gagal. compileDebugKotlin FAILED, root cause:
  `LocalIndication` CompositionLocal NON-NULL di compose-foundation versi project ini —
  `provides null` (Batch 24) gagal compile. Fix: `NoRippleIndication` object (no-op
  `Indication`, di `NeumorphicComponents.kt`) dipakai sebagai pengganti `null` di 4 titik
  `BoosterScreen.kt`. **BELUM diverifikasi run CI berikutnya.**
- **LESSON buat sesi depan (WAJIB baca sebelum pakai `LocalIndication`/`Indication?`
  lagi)**: versi API Compose Foundation project ini (compose-bom 2024.06.00) TIDAK selalu
  match asumsi umum dari internet/training data soal nullability — 2 insiden berturut
  (Batch 23 experimental opt-in, Batch 25 non-null CompositionLocal) SAMA-SAMA soal API
  surface yang beda dari ekspektasi, BUKAN soal logic. Sandbox Claude gak bisa compile-
  check, jadi kalau pakai API Compose yang belum pernah dipakai di project ini
  sebelumnya, confidence HARUS diturunkan eksplisit di report, bukan diasumsikan aman.
- ✅ **Batch 24 (v1.63): ripple removal** — implementasi TETAP benar secara desain (scope
  4 titik, `NoRippleIndication` custom), cuma cara nulis Kotlin-nya yang salah tipe.
  Detail lengkap di entry Batch 24 di bawah.
- **Next kandidat polish**: audit Medium/Low lama dari Batch 16 (recomposition/reusable
  component review, hierarki visual, white space, micro-animation tambahan, loading/
  success/error feedback, empty/error state, penjelasan fitur lanjutan) — belum pernah
  disentuh sama sekali.
- ✅ **CI CONFIRMED HIJAU di v1.62** (hotfix `@OptIn` Batch 23 berhasil). Slider custom
  Batch 22 sekarang FULLY VERIFIED (build + runtime compile, bukan cuma statis lagi).
  Fase lanjut: **"polish, debugging, eksekusi sampai matang"**.
- ✅ **CI CONFIRMED HIJAU di v1.60** (sebelum regresi Slider Batch 22 di v1.61) — root
  cause Gradle wrapper bootstrap (Batch 19-21) TETAP final selesai, TIDAK terkait hotfix
  ini. Fase tetap **"polish, debugging, eksekusi sampai matang"**.
- ✅ **Batch 22 (v1.61): Slider custom** — implementasi TETAP benar secara desain (track
  10dp, thumb 22dp+shadow+ring, konsisten `neumorphicDepth()`), cuma kurang 1 annotation
  opt-in. Detail lengkap di entry Batch 22 di bawah.
- ✅ **Batch 22 SELESAI: Slider custom** (item terakhir pending dari spec design system
  "Hybrid Neumorphism", ditunda sejak Batch 15). `NeumorphicSliderTrack` (10dp, rounded
  5dp) + `NeumorphicSliderThumb` (22dp, `neumorphicDepth()` sama dgn NeumorphicCard, ring
  2dp aksen) di `NeumorphicComponents.kt`, dipasang ke `Slider` di `FeatureControl` via
  overload `thumb=`/`track=` (material3 1.2+, tersedia di compose-bom 2024.06.00). 1 file
  berubah, visual-only, belum divalidasi runtime (API ini baru pertama kali dipakai project
  ini — kandidat pertama dicurigai kalau ada laporan render slider aneh).
- **Sisa item design system pending**: `drawWithCache` optimasi (skip, gak ada manfaat
  nyata di skala app ini — cuma static card, bukan list besar/animasi berat), ripple
  removal Material3 default di `Button`/`FilterChip`/`AssistChip` (`BoosterScreen.kt`,
  BELUM dikerjakan — kandidat batch polish berikutnya kalau user lanjut), radius "Phone:
  44dp" (diabaikan permanen, tidak ada elemen target).
- **Sisa item audit Medium/Low** (dari Batch 16, belum pernah disentuh): recomposition/
  reusable component review, hierarki visual, white space, micro-animation tambahan,
  loading/success/error feedback, empty/error state, penjelasan fitur lanjutan (Low).
  Kandidat batch polish berikutnya.

- ✅ **Batch 21 SELESAI: fix LANJUTAN CI lagi (v1.59 belum tuntas).** User upload log
  run #65 (`log_fail_v1_59-debug-run65.zip` — penamaan versi udah BENAR kali ini,
  konfirmasi fix Batch 20 soal itu berhasil).
  - **Progress dari Batch 20**: isolasi direktori scratch BERHASIL — Gradle sistem
    9.6.1 udah gak lagi coba evaluasi `build.gradle.kts` project kita (gak ada lagi
    error `Configuration.fileCollection(Spec)`). Tapi ketemu masalah BARU:
    `Directory '/tmp/tmp.xxx' does not contain a Gradle build` — task `wrapper` Gradle
    9.6.1 ternyata WAJIB direktorinya "valid Gradle project" (ada file settings) buat
    bisa jalan sama sekali, beda dari Gradle versi lama yang bisa generate wrapper di
    direktori kosong tanpa settings file apapun.
  - **Fix**: bikin `settings.gradle.kts` KOSONG (isinya cuma komentar) di direktori
    scratch SEBELUM manggil `gradle wrapper --gradle-version 8.7` di sana. File ini
    sengaja kosong total — gak ada project/subproject/dependency apapun buat
    dievaluasi, cuma syarat minimal biar Gradle 9.6.1 mau nganggep direktori itu
    "valid Gradle build".
  - **File yang berubah**: `.github/workflows/build.yml` (1 baris `echo` baru di kedua
    job, tepat sebelum pemanggilan `gradle wrapper`), `app/build.gradle.kts`
    (versionCode 59→60, versionName 1.59→1.60). Tidak ada perubahan kode Kotlin.
  - **PENTING buat sesi depan**: kalau CI MASIH gagal lagi setelah ini, kemungkinan
    besar bukan lagi soal bootstrap wrapper (itu levelnya udah makin dalam — versi
    Gradle, lalu isolasi direktori, lalu syarat settings file — pola berulang "Gradle
    9.6.1 punya persyaratan lebih ketat dari versi lama"). Kalau ternyata masih ada
    lapisan masalah lain di titik ini, PERTIMBANGKAN pendekatan alternatif yang lebih
    permanen: commit LANGSUNG file wrapper (`gradlew`, `gradlew.bat`,
    `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`) ke
    repo sekali aja secara manual (dari mesin dev manapun yang punya Gradle terinstal,
    BUKAN dari CI), supaya CI gak perlu bootstrap apapun lagi selamanya — ini pendekatan
    paling umum dipakai project Android sungguhan (wrapper SELALU dicommit, bukan
    di-generate on-the-fly). Sandbox Claude gak bisa lakuin ini sendiri (perlu Gradle
    binary + network yang gak ada di sandbox).
  - **Belum diverifikasi runtime** — sama seperti 2 batch CI sebelumnya, HARUS dicek
    run berikutnya.

- ✅ **Batch 20 (v1.59)** — status DIPERBARUI: fix isolasi direktori TERBUKTI BEKERJA
  (masalah `Configuration.fileCollection` gak muncul lagi), tapi ketemu lapisan masalah
  baru (lihat Batch 21). Fix penamaan artifact & kondisi upload log DIKONFIRMASI benar
  (nama file log kali ini sudah ada versi & timestamp yang tepat).

- ✅ **Batch 19 (v1.58)** — status TETAP: root cause awal (Gradle sistem naik ke 9.6.1)
  tetap benar dan valid, cuma fix-nya butuh 2 iterasi lagi (Batch 20, Batch 21).

- **Detail lengkap Batch 20 (fix isolasi direktori, v1.59)**: fix v1.58
  (`gradle wrapper --gradle-version 8.7`) TETAP dijalankan pakai Gradle SISTEM runner
  (9.6.1). Ternyata `gradle wrapper` — MESKIPUN cuma buat generate file wrapper — tetap
  memicu Gradle mengevaluasi PENUH seluruh project (baca `settings.gradle.kts` + SEMUA
  `build.gradle.kts` termasuk `:app`) di fase konfigurasi. Fix: generate wrapper di
  direktori kosong terpisah (`mktemp -d`), baru salin 4 file hasilnya ke root project.
  Juga memperbaiki 2 bug penamaan artifact (step "Extract version name" dipindah ke
  paling awal + jadi unconditional di job release; kondisi upload log release
  disederhanakan jadi `if: failure()` saja).

- **Detail lengkap Batch 20 (versi panjang, ditulis saat batch itu selesai)**:
    TETAP dijalankan pakai Gradle SISTEM runner (9.6.1). Ternyata `gradle wrapper` —
    MESKIPUN cuma buat generate file wrapper — tetap memicu Gradle mengevaluasi PENUH
    seluruh project (baca `settings.gradle.kts` + SEMUA `build.gradle.kts` termasuk
    `:app`) di fase konfigurasi, SEBELUM task `wrapper`-nya sendiri sempat jalan. Jadi
    incompatibility Gradle 9.6.1 vs Kotlin Gradle Plugin 1.9.24 TETAP kena, cuma
    gagalnya di step yang lebih awal (`Grant execute permission for gradlew`, sebelum
    sempat `tee` output ke log — makanya log kemarin cuma dapet
    `build/reports/problems/problems-report.html` isinya laporan WARNING dari task
    `wrapper` itu sendiri, BUKAN error fatalnya).
  - **Fix beneran (Batch 20)**: generate wrapper di **direktori kosong terpisah**
    (`mktemp -d`, file wrapper generik — TIDAK bergantung isi project manapun, sama
    persis buat project apapun yang target Gradle-nya sama), baru 4 file hasilnya
    (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
    `gradle/wrapper/gradle-wrapper.properties`) disalin ke root project. Dengan cara
    ini Gradle sistem 9.6.1 SAMA SEKALI TIDAK PERNAH menyentuh `build.gradle.kts`
    project ini — cuma dipakai buat bootstrap file wrapper generik doang.
  - **Bug KEDUA yang ikut ketemu & diperbaiki**: step "Extract version name" sebelumnya
    ada SETELAH step wrapper (build job) / SETELAH `secret_check` DAN digated
    `if: has_secret==true` (release job). Kalau step SEBELUM itu gagal (persis kasus
    kemarin), step ini ikut ke-skip → `steps.version.outputs.name` kosong → nama
    artifact log jadi `log_fail_v-debug-run64` (versi kosong, susah dikenali). Fix:
    step ini dipindah ke **paling awal** (langsung setelah Checkout) di KEDUA job, dan
    di job `release` jadi **unconditional** (sebelumnya gated secret) — cuma `grep` ke
    file teks, gak butuh gradlew/JDK/secret sama sekali, aman dijalankan paling depan.
  - **Bug KETIGA**: kondisi upload artifact log di job `release` sebelumnya
    `if: failure() && steps.secret_check.outputs.has_secret == 'true'` — kalau step
    wrapper gagal SEBELUM `secret_check` sempat jalan, `has_secret` kosong, kondisi
    `== 'true'` FALSE, artifact log gagal ke-upload padahal build-nya beneran gagal.
    Fix: disederhanakan jadi `if: failure()` saja (konsisten sama job `build`).
  - **File yang berubah**: `.github/workflows/build.yml` (restrukturisasi urutan step +
    3 fix di atas), `app/build.gradle.kts` (versionCode 58→59, versionName 1.58→1.59).
    TIDAK ADA perubahan kode Kotlin.
  - **PENTING buat sesi depan**: kalau nambah step baru di awal job yang BISA GAGAL
    (network call, external command, dll), taruh SETELAH "Extract version name", JANGAN
    SEBELUM — supaya penamaan artifact log kegagalan selalu punya nomor versi yang
    benar apapun yang gagal duluan.
  - **Belum diverifikasi runtime** — sama seperti Batch 19, HARUS dicek run CI
    berikutnya. Kalau bootstrap-di-direktori-terpisah ini MASIH gagal juga, kemungkinan
    besar bukan lagi soal versi Gradle — laporkan log lengkapnya (sekarang harusnya
    lebih informatif: ada `gradle-wrapper-bootstrap.log` terpisah dari
    `gradle-build-debug.log`, dan nama artifact-nya bakal ada nomor versi 1.59 dengan
    benar).

- ✅ **Batch 19 (v1.58)** — status DIPERBARUI: fix di batch itu TIDAK CUKUP (lihat
  detail Batch 20 di atas), tapi PENEMUAN root cause-nya (Gradle sistem runner naik ke
  9.6.1, gak kompatibel KGP 1.9.24) tetap BENAR dan jadi dasar fix Batch 20.

- ⚠️ **Batch 18 (v1.57, Hilt DI)** — status TETAP SAMA: masih belum sempat teruji
  beneran sampai satu run CI penuh berhasil lolos.

- **Detail lengkap Batch 19 (root cause investigation awal + fix parsial v1.58)**:
    `A problem occurred configuring project ':app'` →
    `'...Configuration.fileCollection(...Spec)'` — ini gagal di TAHAP KONFIGURASI
    project, SEBELUM task compile Kotlin manapun sempat jalan. Penyebab: project ini
    dari awal TIDAK commit `gradlew`/`gradle-wrapper.properties` — CI generate wrapper
    on-the-fly via `gradle wrapper` TANPA versi eksplisit, jadi ikut versi Gradle
    bawaan image runner. Runner image ter-update (`ubuntu-24.04` versi
    `20260720.247.2`) sekarang bawa **Gradle 9.6.1**, jauh lebih baru dari yang
    didukung Kotlin Gradle Plugin 1.9.24 project ini (KGP 1.9.24 rilis 2023, gak
    kompatibel API internal Gradle 9.x). Ini bug LATEN dari awal project (gak pernah
    pin versi Gradle) yang baru kepicu sekarang karena Gradle di sisi GitHub jalan
    terus naik versi — Batch 18 (Hilt) cuma KEBETULAN jadi push pertama SETELAH
    runner image ter-update, bukan penyebabnya.
  - **Fix**: `.github/workflows/build.yml` — `gradle wrapper` di kedua job (`build` &
    `release`) sekarang eksplisit `gradle wrapper --gradle-version 8.7` (kompatibel
    AGP 8.5.2 [min Gradle 8.7] + Kotlin 1.9.24). TIDAK ada perubahan kode Kotlin sama
    sekali di batch ini — murni infra CI.
  - **Fitur baru diminta user**: artifact `log_fail_v<versi>-<debug|release>-run<N>`
    di-upload OTOMATIS (`if: failure()`) tiap kali step build gagal, isi = output
    lengkap `./gradlew ... --stacktrace` (di-`tee` ke file) + `**/build/reports/**`
    (termasuk `problems-report.html`). Muncul di tab Actions run → bagian Artifacts,
    gak perlu scroll log mentah manual lagi. Kalau build sukses, artifact ini SAMA
    SEKALI TIDAK dibuat (gak numpuk sampah di setiap run sukses).
  - **PENTING buat sesi depan**: kalau suatu saat mau upgrade AGP/Kotlin lagi, versi
    `--gradle-version 8.7` di CI HARUS di-update bareng (dicek kompatibilitasnya) —
    JANGAN dibiarkan mismatch lagi kayak insiden ini.
  - **Belum diverifikasi**: fix ini VALIDASI-nya HARUS lewat CI beneran (push +
    lihat run baru) — sandbox gak bisa jalanin Gradle Wrapper generation sungguhan.
    Confidence tinggi karena root cause sudah jelas & fix-nya standar (pin versi),
    tapi tetap disarankan cek run berikutnya sebelum lanjut nambah fitur lagi.

- ⚠️ **Batch 18 (v1.57, Hilt DI)** — status DIPERBARUI: kegagalan CI sebelumnya
  TERNYATA BUKAN karena Hilt (lihat root cause Batch 19 di atas). Perubahan Hilt di
  v1.57 kemungkinan besar SUDAH BENAR, cuma belum sempat diuji beneran karena CI gagal
  duluan di tahap konfigurasi sebelum kode Hilt-nya sendiri sempat dikompilasi. Setelah
  fix Batch 19 di-push, run berikutnya baru akan jadi tes SUNGGUHAN buat kode Hilt.

- **Detail lengkap Batch 18 (Hilt DI, High #3, penutup 3 item High dari audit)**:
  - **Kenapa risiko lebih tinggi dari Batch 16/17**: ini pertama kalinya audit nyentuh
    LAPISAN BUILD SYSTEM (plugin resolution, annotation processing/`kapt`), bukan cuma
    kode Kotlin. Sandbox Claude gak bisa verifikasi resolusi plugin/dependency Gradle
    sama sekali (network disabled, gak ada Gradle/Maven cache) — beda dari
    brace/paren-check yang cukup buat error kode Kotlin biasa. Kalau ada TYPO versi atau
    ketidakcocokan Kotlin/Hilt/kapt, itu BARU KETAUAN pas CI jalan, gak bisa dicegah dari
    sandbox ini.
  - **Yang ditambahkan**: plugin `com.google.dagger.hilt.android` v2.51.1 (dipilih karena
    kompatibel dgn Kotlin 1.9.24 + AGP 8.5.2 yang sudah dipakai — BUKAN versi terbaru
    sembarangan), plugin `org.jetbrains.kotlin.kapt` (annotation processor, bukan KSP —
    alasan: KSP butuh versi terpisah yang harus persis cocok Kotlin version, kapt lebih
    aman tanpa compiler buat verifikasi), `kapt { correctErrorTypes = true }` (rekomendasi
    resmi dokumentasi Hilt).
  - **File yang berubah**: `build.gradle.kts` (root, +1 plugin), `app/build.gradle.kts`
    (+kapt/hilt plugin, +2 dependency, +kapt block), `AudioEnhancerApp.kt`
    (`@HiltAndroidApp`), `MainActivity.kt` (`@AndroidEntryPoint`), `BoosterViewModel.kt`
    (`@HiltViewModel` + constructor `Application` sekarang `@Inject constructor(...)`,
    BUKAN lagi constructor polos — `Application` di-provide OTOMATIS oleh Hilt, TIDAK
    perlu Module/Provides manual buat ini).
  - **KALAU CI GAGAL** (build error di step `assembleDebug`): kemungkinan besar
    ketidakcocokan versi Hilt/Kotlin/kapt yang gak kelihatan dari sandbox statis. Cara
    termudah recover: `git revert` commit batch ini (isinya kecil & terisolasi — cuma
    5 file berubah, gampang di-revert bersih), balik ke v1.56 (Batch 17, ViewModel tanpa
    DI, sudah "SELESAI" penuh confidence tinggi), laporkan pesan error CI lengkap biar
    bisa didiagnosis versi mana yang perlu diganti.
  - **PENTING buat sesi depan**: kalau nambah dependency/class baru yang butuh
    di-inject (bukan cuma `Application`), WAJIB pakai constructor injection
    (`@Inject constructor(...)`) — JANGAN bikin instance manual (`ClassName()`) untuk
    apapun yang seharusnya di-inject, itu ngelawan tujuan DI. Kalau butuh binding
    interface→implementation atau provide sesuatu yang bukan constructor-injectable
    (mis. `SharedPreferences`), butuh `@Module`/`@InstallIn` baru — BELUM ADA di project
    ini sama sekali, itu scope batch berikutnya kalau diperlukan (`PrefsHelper` saat ini
    masih object singleton biasa, BUKAN di-inject).
  - **Belum divalidasi runtime SAMA SEKALI** — ini benar-benar cuma statis (brace/paren
    balance + baca ulang tiap anotasi/import manual). Confidence rating diturunkan
    signifikan dibanding batch lain karena alasan di atas.

- ✅ **Batch 17 SELESAI**: lanjutan audit (High #2) — ekstraksi state + business logic
  seputar koneksi `AudioEnhancerService` dari `MainActivity.kt` ke `BoosterViewModel.kt`
  (baru, `AndroidViewModel` polos, **TANPA DI framework**).
  - **State pindah ke ViewModel** (jadi `var ... by mutableStateOf(...); private set`,
    exposed read-only): `connectionState` (enum-nya juga pindah, sekarang
    `BoosterViewModel.ConnectionState` bukan `MainActivity.ConnectionState`),
    `bassSupported`/`virtualizerSupported`/`loudnessSupported`/`bassStrengthSupported`/
    `virtualizerStrengthSupported`, semua state `equalizer*`, `service`/`bound` fields,
    `ServiceConnection`, 4 buffer `pending*`.
  - **Fungsi pindah**: `startBoosterService()`, `attemptBindService()`, ditambah 4 fungsi
    baru `setBass/setVirtualizer/setLoudness/setEqualizerBand()` (gantiin lambda inline
    `{ if (bound) service?.xxx else pendingXxx = it }` yang dulu ada di `setContent{}`
    MainActivity — sekarang tinggal `{ viewModel.setBass(it) }`).
  - **State yang SENGAJA TETAP di MainActivity** (bukan business logic audio, inheren
    API Activity-only): `notificationPermissionGranted` (butuh
    `ActivityResultLauncher`), `shortcutCustomPresetName` (butuh `Intent` dari Activity).
  - **PERUBAHAN PERILAKU kecil (didisclose, BUKAN zero-change seperti Batch 16)**:
    bindService/unbindService sekarang pakai `getApplication()` (Application Context)
    lewat `AndroidViewModel`, bukan Activity Context langsung. Alasan: ViewModel yang
    nyimpen Activity Context adalah context-leak risk (VM bisa outlive 1 instance
    Activity kalau ada config change). Dampak praktis nyaris nihil di app ini karena
    rotasi sudah dideprioritaskan user (VM tetap 1:1 umur dengan MainActivity). Unbind
    sekarang di `BoosterViewModel.onCleared()`, bukan `MainActivity.onDestroy()` lagi
    (override `onDestroy()` di MainActivity sudah dihapus, gak ada isinya lagi).
  - **Dependency baru** (`build.gradle.kts`, edit parsial): `androidx.activity:activity-ktx:1.9.1`
    (buat `by viewModels()`), `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4` +
    `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4`.
  - **PENTING buat sesi depan**: kalau nambah state/logic baru yang terkait
    audio/service (bukan cuma UI lokal), taruh di `BoosterViewModel.kt`, JANGAN balik
    ke pola field `by mutableStateOf` langsung di `MainActivity`. `BoosterScreen.kt`
    sekarang terima `connectionState: BoosterViewModel.ConnectionState` (bukan
    `MainActivity.ConnectionState` lagi) — kalau nambah screen/composable baru yang
    perlu tahu status koneksi, referensikan ke `BoosterViewModel.ConnectionState`.
  - **Belum dikerjakan dari audit** (urutan sama seperti dicatat di Batch 16, belum
    berubah): (1) Hilt DI (Atomic Change terpisah, butuh ubah
    `build.gradle.kts`+`settings.gradle.kts` lebih jauh — plugin Hilt, KSP/kapt, dst),
    (2) item Medium (recomposition/reusable component, hierarki visual, white space,
    micro-animation, loading/success/error feedback, empty/error state), (3) item Low
    (penjelasan fitur lanjutan).
  - **Belum divalidasi runtime** — statis only (brace/paren balance semua file project,
    grep sisa referensi `MainActivity.ConnectionState` = 0, cross-check import unused,
    string parity ID/EN tetap 95/95). Kandidat pertama dicurigai kalau ada laporan
    aneh soal binding/unbinding service setelah update: perubahan Context bindService
    di atas (Application vs Activity Context).

- ✅ **Batch 16 SELESAI**: audit kode (dari user, format checklist High/Medium/Low) — user
  eksplisit serahkan urutan prioritas ke Claude. Keputusan: item High "God Activity split"
  (MVVM+DI dianggap Atomic Change terlalu besar buat 1 batch tanpa compiler) dikerjakan
  DULUAN sebagai **pemindahan lokasi kode murni (zero logic change)**, karena:
  1. Foundational — memudahkan ekstraksi ViewModel di batch berikutnya (diff lebih kecil,
     lebih gampang dicek manual tanpa compiler).
  2. Risiko paling rendah di antara semua item High: TIDAK ada perubahan logic/state/
     behavior sama sekali, cuma potong-pindah blok kode + ubah `private`→`internal` di
     5 composable yang sekarang dipanggil lintas-file. Diverifikasi: cross-check daftar
     deklarasi (16/16 cocok, tidak ada yang hilang/dobel), brace/paren balance semua file
     project (bukan cuma 3 file baru), string parity ID/EN (95/95, tidak berubah).
  3. MVVM (ekstrak state+business logic ke ViewModel) & DI (Hilt/Koin) SENGAJA
     DITUNDA ke batch terpisah — 2 perubahan besar sekaligus (struktur file + arsitektur
     state) tanpa compiler sekali jalan terlalu berisiko (lihat insiden v1.40/v1.46 di
     bawah, root cause-nya selalu typo kecil yang lolos karena gak ada compile-check).
  - **File yang berubah**: `MainActivity.kt` dipecah jadi 3 file:
    - `MainActivity.kt` (318 baris, dari 1421) — CUMA Activity class: lifecycle, service
      binding, permission launcher, pemanggilan `BoosterScreen()`.
    - `BoosterScreen.kt` (baru, 824 baris) — `BoosterScreen` + composable spesifik-layar:
      `ServiceStatusBadge`, `PowerToggleRow`, `CrashBanner`, `ThemeModeToggle`,
      `EqualizerSection`, `Preset` (data class), `formatFreqLabel`.
    - `NeumorphicComponents.kt` (baru, 335 baris) — atom UI reusable: `NeumorphicCard`,
      `NeumorphicTintedCard`, `NeumorphicCircleButton`, `SectionLabel`, `FeatureControl`,
      `neumorphicDepth()`/`neumorphicInnerShadow()` (2 terakhir tetap `private`, cuma
      dipakai di file ini).
  - **Visibility yang diubah** (`private`→`internal`, WAJIB karena sekarang lintas-file):
    `NeumorphicCard`, `NeumorphicTintedCard`, `NeumorphicCircleButton`, `SectionLabel`,
    `FeatureControl`. `MainActivity.ConnectionState` (enum nested) TIDAK diubah — sudah
    public by default, dipanggil dari `BoosterScreen.kt` via `MainActivity.ConnectionState.X`.
  - **PENTING buat sesi depan**: kalau nambah composable baru yang generik/reusable
    (bukan spesifik 1 layar), taruh di `NeumorphicComponents.kt`. Kalau spesifik ke layar
    booster utama, taruh di `BoosterScreen.kt`. JANGAN tambah composable baru langsung di
    `MainActivity.kt` lagi — file itu sekarang murni Activity/lifecycle.
  - **Belum dikerjakan dari audit** (urutan rencana, TIDAK diminta dikerjakan sekaligus):
    1. Ekstrak state (`bass`/`virtualizer`/`loudness`/service-binding fields dkk) +
       business logic (`applyPreset`, `attemptBindService`, dst) ke `BoosterViewModel.kt`
       (plain ViewModel dulu, TANPA DI framework) — High #2.
    2. Introduce Hilt (butuh ubah `build.gradle.kts`+`settings.gradle.kts`, Atomic Change
       sendiri) — High #3.
    3. Item Medium (recomposition/reusable component, hierarki visual, white space,
       micro-animation, loading/success/error feedback, empty/error state) & Low
       (penjelasan fitur lanjutan) — dikerjain SETELAH arsitektur stabil, biar gak
       nambah lagi yang perlu di-refactor ulang pas MVVM masuk.
  - **Belum divalidasi runtime** — sama seperti batch-batch sebelumnya, sandbox Claude
    gak punya compiler, cuma statis (brace/paren + cross-check deklarasi + grep import).

- ✅ **Batch 15 SELESAI**: user kasih spec design system lengkap ("Hybrid Neumorphism")
  tertulis — diterapkan selektif (bukan semua poin, lihat "Belum dikerjakan" di bawah):
  - **Warna**: SUDAH 100% match sebelum batch ini (`#232220`/`#C2A26B`/`#F6F4EF`/
    `#B8B3A8` — kebetulan/gak sengaja sudah persis dari Batch 12). Tidak ada perubahan.
  - **Shadow**: alpha diturunkan drastis ke spec — sisi gelap 100%→**60% black**
    (`0x99000000`), sisi terang 20%→**~4% white** (`0x0AFFFFFF`). Offset/blur jadi
    ASIMETRIS: gelap `+8dp/17dp blur`, terang `-6dp/15dp blur` (sebelumnya simetris
    `7dp/16dp` di kedua sisi). Token terpusat di `Theme.kt`
    (`NeuShadowDark/LightOffset/Blur`) — JANGAN hardcode angka baru di `MainActivity.kt`.
  - **Radius**: Card 20dp→**22dp** (`NeuCardRadius`), Icon Box dipisah jadi **14dp**
    (`NeuIconBoxRadius`) + ukuran box 36dp→**40dp**. `NeumorphicCard` sekarang terima
    parameter `radius` (default `NeuCardRadius`, override ke `NeuIconBoxRadius` di
    icon-orb `FeatureControl`).
  - **Pressed state**: fungsi baru `neumorphicInnerShadow()` (clip+stroke+shadowLayer,
    lihat komentar di kodenya) gantiin border rata — dipakai di `NeumorphicCard(pressed
    = true)` & `NeumorphicCircleButton` saat toggle ON. Tombol power juga dapat scale
    **0.97x saat DITEKAN JARI** (gesture sesaat via `MutableInteractionSource`+
    `animateFloatAsState`, BEDA dari param `pressed` yang berarti "toggle ON") + ripple
    dimatikan (`indication = null`).
  - **Typography**: Heading 30sp→**28sp** (tetap ExtraBold/800). `SectionLabel` di-hardcode
    **12sp, letterSpacing 1.4** (lepas dari token `bodyMedium` global). Body 13-15sp &
    Subtitle 12-13sp SUDAH sesuai sebelumnya, tidak diubah.
  - **Layout**: padding root 24dp→**22dp**, gap antar-card 20dp→**16dp**.
  - **Ripple**: dimatikan cuma di `NeumorphicCircleButton` (custom clickable). Chip/Button
    Material3 bawaan (`FilterChip`, `AssistChip`, `Button`) MASIH pakai ripple default —
    di luar scope batch ini (lihat TODO).

  **⚠️ BELUM DIKERJAKAN dari spec user (transparan, biar gak dikira "udah 100%")**:
  1. Slider custom (`Track 10dp`, `Thumb 22dp + shadow + ring`) — Compose `Slider` masih
     dipakai default Material3 tanpa kustomisasi `thumb=`/`track=`. Butuh subclass Slider
     Material3 1.2+ API, effort besar, BELUM disentuh.
  2. `Modifier.drawWithCache()` buat optimasi render — belum dipakai, `drawBehind`/
     `drawWithContent` biasa (cukup buat skala UI app ini, drawWithCache manfaatnya
     kelihatan di list besar/animasi berat, bukan static card).
  3. Radius "Phone: 44dp" di spec — TIDAK ADA elemen UI yang jelas jadi target token ini
     (kemungkinan sisa dari referensi frame HTML mockup, bukan komponen app), diabaikan.
  4. Ripple Material3 default (`Button`, `FilterChip`, `AssistChip`) belum diganti jadi
     "sangat halus/nonaktif" — cuma tombol power custom yang sudah.
  5. **Belum divalidasi runtime** — semua di atas statis (brace/paren balance +
     compile-plausibility check), belum pernah di-build & dijalanin di device asli.

- ✅ **Batch 14 SELESAI (fix urgent dilaporkan user)**: "efek kedalaman belum kelihatan"
  di APK asli, padahal HTML preview kelihatan jelas. **ROOT CAUSE ketemu, BUKAN soal
  tuning angka** (dugaan awal di catatan Batch 12 di bawah — itu SALAH): `Modifier.shadow`
  Compose itu shadow ELEVATION bawaan Android, alpha ambient/spot-nya DIBATASI KERAS
  oleh sistem (~3-15% max secara internal) TIDAK PEDULI warna/opacity yang dikasih ke
  `ambientColor`/`spotColor` — API-nya sendiri gak sanggup setebal CSS `box-shadow`
  (yang opacity-nya 100% dikontrol manual). Fix: `NeumorphicCard`/`NeumorphicTintedCard`/
  `NeumorphicCircleButton` sekarang pakai `neumorphicDepth()` (extension `Modifier` baru,
  private, di bawah `NeumorphicCard`) — gambar shadow manual pakai
  `android.graphics.Paint.setShadowLayer()` lewat `drawBehind`+`drawIntoCanvas`, blur+offset
  bebas kita atur PERSIS cara CSS. **Gated API 28+** (`Build.VERSION_CODES.P`) karena shadow
  layer di canvas hardware-accelerated baru didukung penuh sejak Android 9 — di API <28
  fallback diam-diam TANPA shadow sama sekali (bukan crash, tapi juga bukan dual-shadow
  lama yang toh sama-sama nyaris invisible).
  - **PENTING buat sesi depan**: kalau nambah kartu/komponen baru yang butuh efek
    "timbul", WAJIB pakai `Modifier.neumorphicDepth(shape, darkColor, lightColor)` —
    JANGAN balik pakai `Modifier.shadow(...ambientColor...)` lagi, sudah terbukti gak
    kelihatan di device asli meski di preview compose/emulator kadang tampak oke.
  - **Belum divalidasi ulang di device asli** setelah fix ini (user yang laporan Batch 13
    kemarin perlu install v1.53 & konfirmasi). Kalau MASIH kurang tebal, itu murni soal
    naikkan `blurRadius`/`offset` di `neumorphicDepth()`, bukan ganti pendekatan lagi.

- ✅ **Batch 13 SELESAI**: porting 2 elemen yang hilang dari `docs/preview/current.html`
  ke Kotlin (gap ketauan karena user compare screenshot APK vs HTML preview) —
  **HTML lama SALAH KLAIM "sudah live di app"**, padahal 2 elemen ini belum pernah
  ada di `MainActivity.kt`:
  1. **Power toggle "Aktif/Nonaktif"** (`PowerToggleRow` + `NeumorphicCircleButton`,
     baru, 64dp circle, dual-shadow sama teknik `NeumorphicCard` tapi `CircleShape`,
     ring 2dp `primary` saat ON meniru `.power-btn.on` di HTML). Wired ke infra yang
     SUDAH ADA sebelumnya (bukan bikin state baru): `AudioEnhancerService.requestStart`/
     `requestStop` — sumber kebenaran yang sama dipakai `ShortcutHelper` toggle &
     `QuickToggleTileService`. Polling `isRunning` tiap 1 detik, pola sama persis
     `ServiceStatusBadge` (disengaja duplikasi kecil, biar composable ini berdiri
     sendiri & TIDAK mengubah perilaku badge lama).
  2. **Label section "Kontrol"** (`SectionLabel(stringResource(R.string.controls_title))`)
     ditaruh sebelum kartu Bass Boost/Virtualizer/Loudness Gain — sebelumnya kartu itu
     langsung tampil tanpa header, beda dari HTML.
  - String baru (ID+EN, parity dijaga): `power_toggle_on_label`, `power_toggle_off_label`,
    `power_toggle_on_desc`, `power_toggle_off_desc`, `cd_power_toggle`, `controls_title`.
  - **TIDAK dihapus**: waveform bar decorative row (motif audio kecil di bawah header) —
    itu ADA di Kotlin tapi TIDAK ADA di HTML preview. User cuma minta 2 elemen di atas
    di-porting, bukan minta app di-strip biar match 100% ke HTML (lihat Strict Delete
    Policy — gak boleh hapus fitur tanpa diminta eksplisit). Kalau user mau app match
    HTML *persis* termasuk soal ini, waveform row perlu dihapus eksplisit di sesi
    berikutnya.
  - **Belum divalidasi runtime** — sama seperti Batch 12, tuning visual (ukuran ring,
    radius icon) baru bisa dikonfirmasi kalau sudah dicoba di device asli.

- ✅ **Batch 12 SELESAI di-port ke Kotlin** (final verdict user, setelah draft preview
  divalidasi). Redesain BAHASA DESAIN penuh: glassmorphism (v1.29-v1.49) → **Neumorphic
  Hybrid**. Detail lengkap ada di `CHANGELOG.md` v1.51. Ringkas:
  - `Theme.kt`: background flat `#232220` (dark) / `#E7E4DC` (light), token dual-shadow
    baru (`NeuShadowDarkSide` dkk), `LocalIsDarkTheme` CompositionLocal (WAJIB dipakai,
    bukan `isSystemInDarkTheme()` mentah, di composable manapun yang perlu tahu status
    tema AKTUAL termasuk override manual user).
  - `MainActivity.kt`: `GlassCard`/`GlassTintedCard` → `NeumorphicCard`/
    `NeumorphicTintedCard` (dual `Modifier.shadow`, bukan `Modifier.blur` — sengaja,
    biar aman API 24+). Gradient-clip text (judul, value slider) → warna solid.
  - **PENTING buat sesi depan**: kalau nambah kartu/komponen baru di
    `MainActivity.kt`/`OnboardingScreen.kt`, WAJIB pakai `NeumorphicCard`/
    `NeumorphicTintedCard` (bukan `Card` Material3 polos / bikin translucent baru) &
    JANGAN pakai gradient-clip di teks — biar konsisten sama bahasa desain ini.
  - **Belum divalidasi runtime** (device/emulator) — kalau dual-shadow-nya kurang pas
    secara visual pas dicoba beneran, itu soal tuning angka (`elevation`, `offset` di
    `NeumorphicCard`), laporkan biar di-adjust, bukan redesign ulang dari nol.

- ✅ **Audit batch 11 (lanjutan batch 1-9) SELESAI** — diminta user eksplisit
  ("audit/pematangan lanjutan"). Full sweep: brace/paren balance semua Kotlin (bersih),
  XML parse-validated semua, parity string ID/EN (89/89), manifest vs fisik (sinkron),
  scan drawable/mipmap/string orphan (0 ketemu nyata). Ketemu 2 hal nyata:
  1. `BootReceiver.kt` start service tanpa syarat tiap boot — kontradiksi sama kontrak
     "hormati pilihan user" yang eksplisit didesain buat `ServiceWatchdogWorker` (Batch
     9, `PrefsHelper.getUserWantsRunning()`). User tekan "Matikan" → reboot HP → service
     nyala lagi sendiri tanpa consent. Fix: `BootReceiver` sekarang baca flag yang sama.
  2. `.github/workflows/build.yml` job `release` TIDAK PERNAH publish GitHub Release
     (cuma `upload-artifact`) — melanggar aturan rilis standing user (APK harus muncul
     di sidebar "Releases" repo, bukan cuma Actions Artifact). Fix: tambah step
     `softprops/action-gh-release@v2`, `permissions: contents: write` di job level,
     `README.md` diselaraskan. **PENTING buat sesi depan**: kalau nambah job/step CI
     baru yang butuh tulis ke repo (release, tag, dst), WAJIB cek `permissions:` block —
     default GITHUB_TOKEN di banyak org/repo settings itu read-only.
- **LESSON dari Batch 11**: kalau nambah flag "niat user" baru (kayak
  `getUserWantsRunning`) yang dipakai buat gating auto-restart di SATU tempat
  (watchdog), WAJIB grep semua call-site `requestStart()`/`requestStop()` lain
  yang juga jalan tanpa interaksi user langsung (boot receiver, alarm, dll) — jangan
  cuma cek tempat yang lagi difokuskan pas nulis flag itu pertama kali.
- 🎨 **REDESIGN TOTAL (v1.49, diminta user)**: tema violet/glassmorphism ("native
ultra premium" era v1.29+) DICABUT — user bilang kesannya "neon ungu alay". Ganti
ke **matte graphite/charcoal + aksen champagne-bronze desaturasi** (kesan alat
audio fisik premium/brushed-metal, bukan RGB gamer). Aksen per-fitur
(Bass/Virtualizer/Loudness/Equalizer/Battery) TIDAK diubah — sudah muted/earthy
sejak awal, bukan sumber keluhan. Shape (rounded, struktur glass card) & typography
TIDAK diubah, cuma palet warna. File yang kena: `Theme.kt`, `colors.xml` +
`values-night/colors.xml`, `ic_launcher_background.xml`, `widget_background.xml`,
`ic_shortcut_preset.xml`, `docs/preview/current.html`. **Arah desain UI aktif
SEKARANG**: "matte premium" — lihat bagian "Riwayat pivot" di bawah, entry baru
ditambahkan di sana.
- ✨ **Batch 9: WorkManager watchdog** (diminta user eksplisit: "keluarkan semua trik
biar app ini berfungsi 100% lifetime"). Lapisan kedua di luar `START_STICKY` +
`stopWithTask="false"` yang sudah ada (keduanya sudah diverifikasi BENAR sejak
insiden v1.34 — bukan itu yang diubah). `ServiceWatchdogWorker` jalan periodik
(15 menit, minimum interval WorkManager) via `WorkManager`, cek apakah service
harusnya hidup tapi ternyata mati (OS/OEM killer menang), kalau iya restart via
`AudioEnhancerService.requestStart()`.
  - **Penting**: watchdog ini WAJIB HORMAT ke pilihan user. Ditambahkan flag baru
`PrefsHelper.getUserWantsRunning()`/`setUserWantsRunning()` — TERPISAH dari
`AudioEnhancerService.isRunning` (yang cuma state runtime in-memory). Di-set
`true` di `onStartCommand()` (jalur normal start — dipanggil dari MainActivity,
BootReceiver, QS Tile, Widget, Shortcut, SEMUA lewat `requestStart()`), dan
`false` di jalur `ACTION_STOP` (user tekan "Matikan" di notifikasi). Watchdog
CUMA restart kalau flag ini `true` DAN `isRunning` ternyata `false` — kalau user
sengaja matiin, watchdog diam, TIDAK menghidupkan paksa lagi.
  - **Ditolak dieksekusi** (dibahas eksplisit di chat sebelum implementasi, biar
gak diulang tanya lagi di sesi depan): AccessibilityService disalahgunakan
sebagai watchdog, DeviceAdminReceiver, `foregroundServiceType` palsu, reflection
ke hidden API OEM buat matiin battery manager diam-diam — semua itu pola
"mem-bypass consent user diam-diam" yang levelnya sama kayak "trik VPN" yang
sudah ditolak di insiden sebelumnya (lihat "Keputusan sadar" & histori QS Tile
di bawah). TIDAK akan dikerjain lagi meski diminta ulang tanpa alasan baru yang
kuat.
  - **Batasan jujur (WAJIB tetap disampaikan ke user)**: ini BUKAN jaminan "100%
lifetime" — battery/task manager OEM tetap bisa menang di device tertentu,
watchdog cuma mempercepat "sembuh sendiri", bukan mencegah kill sepenuhnya.
Ekspektasi user harus tetap dikalibrasi ke ini, jangan overclaim di UI/copy.
  - **PENDING (bukan bagian batch ini, calon Batch 10 kalau user lanjut)**:
banner in-app persistent buat battery optimization belum di-ignore (beda dari
dialog one-shot yang sudah ada di first-open) — user minta ini juga di diskusi
awal tapi scope batch ini sengaja dibatasi ke watchdog doang (Batch Lock, biar
gak nyentuh >10 file dalam 1 batch).
- ✅ **Audit batch 8 (lanjutan batch 1-7) SELESAI** — diminta user eksplisit ("audit/pematangan
lanjutan"). Full re-read semua 12 file Kotlin (brace/paren balance via script), parity string
ID/EN (89/89, termasuk setelah edit batch ini), `FILE_MANIFEST.txt` vs fisik (sinkron), CI/gradle
(sinkron ke README). Ketemu 1 bug logika nyata: dialog "Simpan Preset" (`MainActivity.kt`) cuma
cek tabrakan nama case-insensitive ke 4 preset BAWAAN (`presets`), TIDAK PERNAH dicek ke sesama
preset CUSTOM lain (`customPresets`) — padahal `PrefsHelper.CustomPreset` eksplisit bilang "nama
harus unik". Akibatnya user bisa bikin 2 custom preset berbeda isi tapi nama nyaris identik
(mis. "Rock" & "rock"), chip & dynamic shortcut jadi membingungkan (isinya beda tapi labelnya
kelihatan sama). Fix: tambah pengecekan ke `customPresets` (case-insensitive), TAPI sengaja
kecualikan exact-match (`it.name != trimmedPresetName`) supaya "simpan ulang dengan nama PERSIS
SAMA = timpa yang lama" (perilaku disengaja di `PrefsHelper.addCustomPreset`, BUKAN bug) tidak
ikut ke-block. String `preset_save_name_collision_error` (ID+EN) digeneralisasi dari "sudah
dipakai preset bawaan" jadi "sudah dipakai preset lain", karena sekarang berlaku ke dua-duanya.
Tidak ada perubahan fungsional lain — semua fitur (QS Tile, App Shortcuts, Widget) tetap
SELESAI, tidak ada regresi ditemukan di area lain.
- ✅ **Audit batch 7 (lanjutan batch 1-6) SELESAI** — diminta user eksplisit:
"gak usah update fitur baru, fokus penyempurnaan aplikasi dan debugging sampai
tuntas". Full re-read 12 file Kotlin (brace/paren balance dicek via script),
semua XML (parse-validated), parity string ID/EN (89/89), manifest vs file
fisik (sinkron). Ketemu 2 hal: (1) `splash_background` di
`values/colors.xml`+`values-night/colors.xml` MASIH warna era "Apple-style"
lama (`#F2F2F7`/`#000000`) padahal `Theme.kt` udah lama pivot ke tema violet
"native ultra premium" — splash kedip warna gak nyambung sebelum masuk app,
sudah disamakan ke `#FAF7FF`/`#0A0714`. (2) `AudioEnhancerService.getEqualizer()`
dead code (nol pemanggil), dihapus. LESSON buat sesi berikutnya: kalau ada
pivot arah desain warna besar lagi (lihat "Riwayat pivot arah desain" di
bawah), WAJIB cross-check `values/colors.xml` DAN `values-night/colors.xml`
juga — bukan cuma `Theme.kt`. Splash screen gampang kelewat karena jarang
dibuka lama-lama pas testing manual.
- **Sebelumnya (v1.45 dst)**: audit batch 1-6 (logika Kotlin + resource +
gradle/CI/README) sudah selesai duluan, tidak ada temuan baru dari batch itu
di sesi ini. Fitur (QS Tile, App Shortcuts, Widget) semua tetap SELESAI, tidak
ada perubahan fungsional di batch 7 — murni polish/debug sesuai permintaan
eksplisit user.
- ✅ **Audit batch 6 (lanjutan batch 1-5) SELESAI** — diminta user eksplisit,
  kali ini file NON-KOTLIN (gradle, CI workflow, README, proguard). Gradle
  files bersih. Ketemu 4 hal nyata: (1) CI `secret_check` cuma validasi 1 dari
  4 secrets keystore yang dibutuhkan, resiko gagal ambigu kalau setup secrets
  partial — sudah di-fix validasi keempat-empatnya. (2) README klaim nama
  artifact yang SALAH (beda dari yang beneran jalan di `build.yml`). (3) README
  masih klaim icon "placeholder" padahal Adaptive Icon custom udah lama ada.
  (4) README "Fitur" gak nyebut QS Tile/App Shortcuts/Widget (v1.40-v1.43) sama
  sekali. Semua sudah di-fix. Pola sama kayak Batch 1: dokumentasi gak sinkron
  kode, cuma sekarang lokasinya di README+CI, bukan README+CHANGELOG.
- ✅ **Audit batch 5 (lanjutan batch 1-4) SELESAI** — diminta user eksplisit.
  Full re-read semua Kotlin + cross-check parity string ID/EN + cek resource
  gak kepakai. Kotlin logic bersih (termasuk kode baru v1.41-v1.43, App
  Shortcuts & Widget). Ketemu 2 resource orphan di `AndroidManifest.xml`:
  `android:label` di-hardcode padahal `@string/app_name` udah ada gak kepakai,
  dan `ic_launcher_round` (mipmap semua densitas, dari batch Adaptive Icon)
  gak pernah di-wire ke `android:roundIcon`. Kedua-duanya udah di-fix.
  Dampak sebelum fix nyaris gak kelihatan, tapi tetap defect arsitektur nyata.
- ✨ **Fitur baru: Widget home screen** (`BoosterWidgetProvider`) — status real-time +
  toggle sekali tap tanpa buka app. Refresh didorong dari satu hook di
  `AudioEnhancerService` (tiap `isRunning` berubah), bukan periodic update (kelewat
  lambat). **Kedua fitur "shortcut" yang diminta user (App Shortcuts v1.42 + Widget
  v1.43) SELESAI** — gak ada lagi item pending dari request shortcut ini.
- ✨ **Fitur baru: App Shortcuts** (long-press ikon launcher) — `ShortcutHelper.kt` +
  `res/xml/shortcuts.xml`. 1 shortcut statis "Nyalakan/Matikan", + shortcut dinamis
  (maks. 3, terbaru duluan) satu per preset custom user, tap → app kebuka & preset
  langsung diterapkan. Konteks: user bilang eksplisit fitur macam "shortcut" ini yang
  paling dia mau tapi sering kelupaan minta — jadi ini prioritas baru, BUKAN cuma
  nice-to-have. Widget home screen (v1.43) nyusul langsung di batch berikutnya.
- 🔽 **Autostart (OemAutostartHelper) DIDEPRIORITASKAN oleh user** — user bilang
  masalahnya "perkara mudah", fokusnya sekarang ke fitur shortcut. Item PENDING di
  bawah (konfirmasi Autostart di Infinix) TETAP dicatat (belum dihapus, belum ada
  laporan gagal), tapi JANGAN diprioritaskan/dikerjain proaktif kecuali user
  singgung lagi duluan.
- ✨ **Fitur baru: Quick Settings Tile** (`QuickToggleTileService`) — toggle service
  langsung dari notification shade, gak perlu buka app dulu (pola UX kayak tile
  "1.1.1.1" Cloudflare). User awalnya minta "trik VPN" buat ngakalin OEM
  battery-killer Infinix; setelah dijelaskan itu gak akan efektif (Transsion punya
  battery manager sendiri terpisah dari Doze/VPN-whitelist Android standar) DAN
  beresiko (bikin dialog konsen VPN sistem yang isinya menyesatkan kalau app gak
  benar-benar pakai VPN), user klarifikasi maksudnya cuma pola UX tile shortcut —
  BUKAN VPN sungguhan. Diimplementasikan sebagai TileService biasa, tanpa VpnService
  apapun. **Keputusan penting**: tile ini TIDAK dan TIDAK diklaim menyelesaikan
  masalah OEM battery-killer — itu tetap limitasi yang gak bisa diakali dari kode
  app manapun, cuma bisa diminimalisir lewat Autostart/battery-unrestricted manual
  di setting OEM.
- 🔧 **Refactor kecil**: `AudioEnhancerService.requestStart()`/`requestStop()`
  companion function baru, dipakai bareng `MainActivity`+`BootReceiver`+tile baru
  (sebelumnya logika start/stop identik ke-copy-paste 2x).
- ✅ **Audit kecacatan logika (batch 1-4) SELESAI** — diminta user eksplisit sesi
  sebelumnya: "berhenti tambah fitur, fokus pematangan & audit kecacatan logika
  hingga tuntas". Semua temuan sudah di-fix:
  - ✅ v1.36 (Batch 1): README klaim `onTaskRemoved` restart yang sudah dicabut sejak
    v1.34, dan README klaim APK debug CI muncul di Artifacts padahal job `build` tidak
    upload apapun.
  - ✅ v1.37 (Batch 2): `OnboardingScreen.kt` emoji hardcoded (🎧🔊🌐📢🛡️🔔) — lolos
    dari pembersihan emoji v1.27 karena emoji-nya di Kotlin, bukan `strings.xml`.
    Diganti icon vector + accent color yang SAMA PERSIS dengan fitur terkait di layar
    utama.
  - ✅ v1.38 (Batch 3): custom preset bisa tabrakan nama dengan preset bawaan. Fix:
    validasi real-time di dialog simpan preset (case-insensitive).
  - ✅ v1.39 (Batch 4, penutup): `BootReceiver` sekarang validasi `intent.action`.
    `CrashLogger` timestamp file sekarang unik per-milidetik. Test coverage
    `PrefsHelperTest` diperluas 8→17 test.
  - Setelah v1.40 (fitur tile) mulai lagi masuk mode fitur normal, bukan audit lagi —
    kalau nemu kecacatan baru pas testing manual, catat sebagai temuan baru di sini.
- ⏳ **PENDING**: v1.35 sudah dikirim, TAPI belum dikonfirmasi user apakah
  kandidat Infinix/Tecno di `OemAutostartHelper.kt` berhasil buka halaman
  Autostart yang benar di device user (**Infinix Note 50 Pro 4G & Note 40
  Pro 4G**, keduanya XOS). Kalau user balik lapor "masih ke App Info aja"
  atau "masih ilang notifnya walau Autostart udah aktif" — lanjut dari sini,
  JANGAN mulai investigasi dari nol (baca insiden v1.34 & v1.35 di bawah dulu).
- **Arah desain UI aktif (Batch 37, v1.76.0 + Batch 38, v1.77.0, ARAH SEKARANG)**:
  "iOS Glassmorphism + Midnight-Blue dominan", WAJIB dark-mode. Kartu struktural =
  genuine frosted-glass (4-stop gradient + layer sheen kedua), border gradient
  highlight->transparan, radius besar ala iOS, background layar gradient
  Midnight-Blue->nyaris-hitam, kontras teks dinaikkan (readability-first). **3
  varian** tersedia via switch Settings (arsitektur `SkeuTokens`/`AppThemeStyle`
  dari Batch 36 dipertahankan & diperluas Batch 38): default "Midnight Glass"
  (restrained), "Aurora Glass" (lebih vivid/saturated, DUA-DUANYA genuine glass),
  dan "Skeuomorphism" (Batch 38, radius/shape 100% otonom + aksen titanium-silver
  sejak Batch 39 — BUKAN glass — panel gunmetal netral + bevel
  extrusion fisik + aksen titanium-silver metalik, bahasa desain sengaja berbeda total dari
  2 varian glass). 1 pilihan tunggal dari 3 (bukan kombinasi). Warna aksen per-fitur
  (Bass/Virtualizer/Loudness/Equalizer) TETAP dipertahankan (bukan sumber keluhan,
  independen dari 3 varian di atas). Detail lengkap: `CHANGELOG.md` v1.76.0 & v1.77.0.
  **Riwayat sebelum Batch 37** (biar gak nyoba ulang): "Skeuomorphism-lite (Tactile
  UI)" (Batch 31-36, v1.70-v1.75.1) — kartu flat/minimal + tactile HANYA di power
  button/slider — DICABUT TOTAL di Batch 37 atas permintaan eksplisit user (bukan
  cuma ganti palet, struktur render kartu juga berubah). Lihat "Riwayat pivot" di
  bawah buat kronologi lengkap. **Catatan penting**: varian "Skeuomorphism" Batch 38
  BUKAN kebangkitan "Skeuomorphism-lite" Batch 31-36 — beda total (Batch 31-36 =
  kartu flat + tactile micro di 2 komponen saja; Batch 38 = 1 varian tema penuh
  dengan bevel-extrusion di SEMUA kartu, dipilih eksplisit lewat toggle, hidup
  berdampingan dengan 2 varian glass, bukan menggantikannya).
- **Preview visual live**: `docs/preview/current.html` — render via
  https://htmlpreview.github.io/?https://github.com/FDzaki-dev/AudioEnhancerPro/blob/main/docs/preview/current.html
  SELALU update file ini bareng perubahan Kotlin yang visual-related,
  SEBELUM ngirim APK build — jauh lebih murah buat validasi arah desain
  daripada muter penuh build+install+screenshot.

## Riwayat pivot arah desain (biar gak nanya/nyoba ulang hal yang sama)
1. **Apple-style minimalis** (v1.11-v1.23) — awalnya dikira sukses, ternyata
   user gak ngerasa "beda" sama sekali. Investigasi ketemu 2 sebab:
   (a) device user kemungkinan besar punya setting Aksesibilitas "Teks Tebal"
       yang maksa SEMUA font bold, override apapun yang app minta — DI LUAR
       kendali app manapun.
   (b) fix banner pakai alpha-transparency tipis di atas dark background yang
       HITAM PEKAT (`#000000`) — transparansi tipis + hitam pekat = hasilnya
       ikut nyaris hitam juga, jadi nyaris invisible. LESSON: kalau mau bikin
       tint/banner di dark theme, pakai solid color blend (`lerp()`), JANGAN
       alpha transparan mentah kalau background di baliknya gelap pekat.
   (c) Root cause PALING besar: app ini pakai EMOJI (🔊🌐📢) sebagai icon UI.
       Apple/iOS gak pernah pakai emoji buat icon fungsional. Ini yang bikin
       user ngerasa "gak pernah berubah" walau kode-nya beda tiap versi.
2. **Neo-brutalist** (kebalikan Apple, "bukan android membosankan") — border
   tebal (2.5dp) solid berwarna, sudut tajam (8-14dp), warna vivid per-fitur.
   User: masih kurang "premium".
3. **Native ultra premium / glassmorphism, palet violet** (v1.29-v1.48) —
   kartu translucent + border gradient tipis + shadow lembut + background
   gradient dalam + waveform motif di header. User akhirnya bilang palet
   violet-nya kesan "neon ungu alay" walau strukturnya sendiri disukai.
4. **Matte premium, palet graphite/bronze** (v1.49) — STRUKTUR
   glassmorphism dari #3 dipertahankan 100% (kartu translucent, border gradient,
   shadow, waveform header), cuma PALET WARNA diganti: primary violet neon
   `#8B7CF6` → champagne-bronze desaturasi `#C2A26B`, background gradient
   violet-hitam → graphite/charcoal netral `#0A0A0A`. Kesan alat audio fisik
   premium (brushed metal, matte black), bukan RGB gamer. Aksen per-fitur TIDAK
   disentuh. LESSON: kalau user komplain "kesan alay/norak" di masa depan, cek
   dulu apakah masalahnya di STRUKTUR (shape/layout) atau cuma di PALET WARNA
   sebelum redesign besar — di kasus ini cuma palet, jadi scope-nya kecil
   (Theme.kt + 2 colors.xml + 3 drawable hardcoded hex + preview HTML).
5. **Neumorphic Hybrid** (Batch 12, v1.51-v1.69, riwayat — DICABUT di Batch 31) —
   kali ini STRUKTUR ikut diganti, bukan cuma palet,
   diminta user eksplisit sambil minta legibility ditingkatkan. Translucency/
   backdrop-blur & gradient-clip text (dua-duanya sumber inkonsistensi kontras di
   struktur glassmorphism #3/#4) dibuang total. Kedalaman visual dari
   dual-shadow neumorphic (extruded = "timbul", inset = "ditekan"), background
   base dinaikkan ke abu graphite medium `#232220` (bukan hitam pekat) supaya sisi
   highlight shadow-nya kelihatan. Warna aksen per-fitur & primary bronze TETAP,
   cuma dipakai lebih hemat (icon/slider/ring, bukan teks).
6. **Skeuomorphism-lite (Tactile UI)** (Batch 31, v1.70, **ARAH SEKARANG**) — user
   kirim acuan design guide (`compose-skeuomorphism-lite.md`) minta neumorphism
   DICABUT TOTAL + **WAJIB dark-mode** (theme mode toggle terang/ikuti-sistem
   dihapus, `LightColors` scheme dihapus, `LocalIsDarkTheme` sekarang selalu
   `true`). Beda kunci vs Neumorphic Hybrid:
   (a) Kedalaman TIDAK LAGI dari dual custom-Paint shadow-layer (`neumorphicDepth`/
       `neumorphicInnerShadow`, `NeuShadowDarkSide`/`NeuShadowLightSideDark`) — semua
       dihapus. Sekarang dari bevel gradient (`SkeuBevelBrush` top-down light source)
       + border highlight/shadow tipis, plus `Modifier.shadow` elevation standar
       Compose (`animateDpAsState`) buat micro-interaction klik.
   (b) Realisme tactile DIPERSEMPIT hanya ke komponen "physical utility" (power
       button, slider knob) sesuai guide poin 3 — kartu struktural (`SkeuCard`/
       `SkeuTintedCard`, ex `NeumorphicCard`/`NeumorphicTintedCard`) sekarang FLAT
       & minimal (solid surface + border 1dp + shadow kecil), BUKAN extruded lagi.
   (c) Slider knob pakai radial gradient metalik (`SkeuSliderThumb`), bukan dual-shadow
       bundar lagi.
   (d) Dark-mode adaptation guide: highlight terang diganti "primary glow" tipis
       (`SkeuPrimaryGlow`), bukan `Color.White` alpha mentah.
   File `NeumorphicComponents.kt` dihapus, diganti `SkeuomorphicComponents.kt`.
   `docs/preview/current.html` & warna aksen per-fitur/primary bronze TETAP
   dipertahankan (bukan sumber keluhan), cuma struktur kartu & shadow yang berubah.
7. **iOS Glassmorphism + Midnight-Blue dominan** (Batch 37, v1.76.0, **ARAH
   SEKARANG**) — user minta rewrite total sektor UI/UX, eksplisit "bukan ganti
   pallet warna murahan", gaya iOS-style glassmorphism dominan + Midnight-Blue jadi
   hint yang kentara, readability maksimal. Beda kunci vs Skeuomorphism-lite (poin 6):
   (a) Kartu struktural balik jadi glass genuine (4-stop gradient + sheen kedua),
       BUKAN lagi flat solid + border tipis.
   (b) Border kartu jadi gradient highlight->transparan, bukan solid alpha tipis.
   (c) Radius naik signifikan (20->26dp kartu) — lebih membulat ala iOS, bukan radius
       standar Android era sebelumnya.
   (d) Background layar jadi gradient Midnight-Blue->nyaris-hitam, bukan flat solid
       — supaya kartu kaca "kebaca" sebagai kaca di atas backdrop bervariasi.
   (e) Midnight Blue naik dari "subtle 6%" (Batch 34) jadi "dominan 20%" — instruksi
       eksplisit user kali ini beda dari guide lama yang minta subtle.
   (f) Kontras teks dinaikkan tegas di semua tier (Primary/Secondary/Muted) —
       readability adalah syarat eksplisit, bukan trade-off boleh dikorbankan demi
       estetika kaca.
   Arsitektur 2-varian (switch Settings, Batch 36) TETAP ADA — user tidak minta
   dihapus — tapi kedua varian sekarang glass (default "Midnight Glass" restrained,
   opsi ke-2 "Aurora Glass" lebih vivid), bukan lagi 1 glass + 1 skeuomorphism.
   Detail lengkap: `CHANGELOG.md` v1.76.0.

## Keputusan sadar yang JANGAN diubah tanpa alasan baru dari user
- **`MODIFY_AUDIO_SETTINGS` permission**: kelihatan gak dipakai di kode
  (`grep` nol hasil), TAPI mekanisme inti app ini (nempel efek audio ke
  session ID `0` global) itu sendiri di luar cara resmi API ini
  didokumentasikan Android — ada laporan anekdotal beberapa OEM/chipset
  butuh permission ini biar efek session-0 nempel dengan benar. TIDAK
  dihapus karena resikonya (app berhenti berfungsi di sebagian HP) gak
  bisa diverifikasi tanpa device fisik.
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`**: dipertahankan apa adanya meski app
  bukan media player asli — user gak ada niat publish Play Store, jadi resiko
  rejection review gak relevan buat dia.
- **Dynamic color (Material You)**: default OFF, opt-in toggle. Alasan:
  biar palet warna custom app gak ketiban tema wallpaper user secara paksa.
- **Equalizer band individual TIDAK dibungkus card sendiri** (`wrapInCard =
  false`) — udah di dalam card "Equalizer Manual", biar gak numpuk
  kaca-di-atas-kaca kalau bandnya banyak.
- **Preset custom (v1.33) TIDAK ikut reset equalizer manual** saat diterapkan
  — beda dari 4 preset bawaan yang eksplisit reset EQ ke flat. Alasan: preset
  custom cuma menyimpan bass/virtualizer/loudness (bukan state EQ), jadi
  reset paksa EQ user tanpa alasan justru terasa seperti kehilangan data.

## Batasan sandbox Claude (PENTING — biar gak ngulang insiden yang sama)
- **Batch 40 (v1.79.0)**: `gradle/actions/setup-gradle@v4` PERTAMA KALI dipakai
  project ini (gantiin bootstrap wrapper manual Batch 19-21) — kandidat pertama
  dicurigai kalau CI gagal di step "Setup Gradle 8.7" (nama action/versi/input
  salah tidak bisa diverifikasi tanpa jalanin CI beneran, sandbox gak ada network).
  Kalau gagal: cek dulu apakah `gradle-version: '8.7'` didukung action versi
  terbaru (kadang action butuh Gradle >= versi minimum tertentu), fallback paling
  aman adalah balikin sementara ke pola bootstrap manual lama (ada di histori git
  commit sebelum batch ini). `org.gradle.configuration-cache=true` SENGAJA TIDAK
  diaktifkan di batch ini meski berpotensi mempercepat lebih jauh — kompatibilitas
  dengan kapt+Hilt (AGP 8.5.2/Kotlin 1.9.24) belum bisa diverifikasi tanpa
  compiler, resiko break lebih besar dari manfaat speed tambahannya. Kandidat
  lanjutan kalau user eksplisit mau coba & terima resikonya.
- **Insiden nyata (v1.40 → v1.41, build gagal di CI)**: `ic_qs_tile.xml` (drawable
  baru buat Quick Settings Tile) pakai `android:tint="?attr/colorControlNormal"`
  TANPA prefix `android:` di depan `attr`. Ini bikin AAPT2 nyari attr itu di
  namespace package sendiri (`com.audioenhancer.booster:attr/colorControlNormal`)
  yang emang gak pernah dideklarasikan, bukan attr framework yang dimaksud →
  `processDebugResources FAILED`, seluruh CI merah, gak ada APK ke-generate sama
  sekali. Root cause murni typo referensi attr, bukan salah logic. LESSON: kalau
  bikin drawable baru yang pakai `?attr/...`, WAJIB prefix `?android:attr/...`
  (kalau maksudnya attr framework) — jangan asal `?attr/...` tanpa dicek attr
  itu didefinisikan di mana. FIX yang dipakai: tint dihapus total dari
  `ic_qs_tile.xml` — gak masalah karena Quick Settings tile emang di-render
  sistem sebagai alpha-mask yang di-tint otomatis oleh Android sendiri sesuai
  state tile, tint manual di level drawable gak pernah kepake buat konteks ini.
- **Insiden nyata (v1.41, command Termux salah target extract)**: command
  "standar" lama pakai `unzip -o "$LATEST_ZIP" -d ~/projects/` (bukan
  `-d ~/projects/AudioEnhancerPro/`). Karena ZIP proyek ini SENGAJA gak
  dibungkus folder induk (`build.gradle.kts` dkk langsung di root ZIP, sesuai
  aturan user), hasil extract malah numpuk langsung di `~/projects/` (folder
  induk SEMUA project Termux), BUKAN di `~/projects/AudioEnhancerPro/`.
  Akibatnya circuit breaker (deteksi file turun >30%) salah trigger ABORT
  karena ngitung isi folder yang file barunya gak pernah nyampe situ — file
  baru nyasar ke folder yang salah. LESSON: kalau ZIP gak dibungkus folder
  induk (kasus proyek ini), target `unzip -d` HARUS folder project itu
  sendiri (`~/projects/AudioEnhancerPro/`), BUKAN parent-nya (`~/projects/`).
  Sudah diperbaiki di command "standar" di bawah — WAJIB pakai versi ini
  buat semua update berikutnya. User juga perlu bersihkan manual file nyasar
  di `~/projects/` root (app/, README.md, dll — bukan punya project lain).
- **Insiden nyata (pengiriman v1.46, packaging ZIP)**: command `zip -r -X out.zip
  . -x ".*"` yang dipakai buat bikin ZIP pengiriman TANPA SADAR ikut membuang
  SEMUA folder/file berawalan titik dari isi ZIP — termasuk `.github/workflows/
  build.yml` (CI) dan `.gitignore`. Validasi "FILE_MANIFEST.txt vs isi ZIP" waktu
  itu tetap "lolos" karena dua-duanya (manifest di dalam ZIP & isi ZIP) sama-sama
  gak lengkap dengan cara yang konsisten — validasi model itu gak nangkep bug
  packaging-nya sendiri. Efeknya baru ketauan setelah user push & gak ada GitHub
  Action yang jalan sama sekali (CI-nya sendiri udah kehapus dari repo). LESSON:
  (1) JANGAN PERNAH pakai pola exclude `-x ".*"` atau sejenisnya yang match semua
  dotfile/dotdir saat packaging ZIP proyek ini — exclude nama spesifik satu-satu
  kalau memang perlu. (2) WAJIB `unzip -l` pada ZIP HASIL AKHIR (bukan cuma cek isi
  folder sumber sebelum di-zip) dan cocokkan listing itu ke `FILE_MANIFEST.txt`
  SEBELUM present_files — supaya bug di command zip itu sendiri ketauan, bukan
  cuma bug di isi file. Detail lengkap insiden ada di CHANGELOG.md entry "v1.46
  (hotfix pengiriman)".
- **TIDAK ADA** kotlinc/gradle/Android SDK di sandbox Claude manapun (dicek
  eksplisit, network disabled). Artinya: Claude TIDAK BISA compile-check
  Kotlin sebelum ngirim zip. Verifikasi cuma bisa manual: baca ulang tiap
  nama class/icon yang dipakai, cek balance brace/paren via python.
- **Insiden nyata yang pernah kejadian**: sempat nulis
  `Icons.AutoMirrored.Filled.VolumeUp` (TIDAK EXIST di library icon) dan baru
  ketauan sebelum sempat ke-kirim — tapi ini nunjukkin resikonya nyata.
  Kalau ragu 1 nama icon/class ada atau nggak, mending pakai yang udah
  KONFIRMASI kepake di file lain, atau icon paling umum/basic.
- **Insiden nyata (v1.34)**: user lapor notifikasi "Audio Booster aktif" ikut
  hilang saat app di-swipe dari recent apps, padahal harusnya cuma hilang
  kalau tekan "Matikan". Sudah diaudit menyeluruh: implementasi Android-nya
  (`stopWithTask="false"`, service di-*start* DAN di-*bind* sekaligus,
  `START_STICKY`, `foregroundServiceType="mediaPlayback"`) semuanya SUDAH
  BENAR — bukan bug logika. Akar masalahnya adalah battery/task manager
  proprietary OEM (Xiaomi/MIUI, Oppo/ColorOS, Vivo, Huawei/EMUI, Samsung, dst)
  yang membunuh foreground service TANPA PEDULI `stopWithTask`/`START_STICKY`
  kecuali user manual mengizinkan "Autostart"/"No restriction" di pengaturan
  khusus tiap merk — ini keterbatasan platform, bukan sesuatu yang bisa
  di-fix murni dari kode app. Mitigasi yang ditambahkan: `OemAutostartHelper.kt`
  (deep-link ke pengaturan yang relevan). LESSON: kalau ada laporan
  "service/notifikasi mati sendiri" lagi di masa depan, JANGAN buru-buru
  curiga ke kode `AudioEnhancerService`/`onTaskRemoved` dulu — itu udah
  diverifikasi benar. Cek dulu apakah user sudah aktifkan Autostart di HP-nya.
- **Update v1.35**: kandidat Infinix/Tecno/itel (`com.transsion.phonemanager` /
  `AutoBootMgrActivity`) ditambah ke `OemAutostartHelper.kt`, tapi CATAT: ini
  PALING GAK TERVERIFIKASI dari semua kandidat OEM yang ada — bahkan library
  open-source populer sekelas `judemanutd/AutoStarter` (600+ stars) masih
  punya issue TERBUKA soal Infinix/Tecno sejak 2020, belum pernah keresolve.
  Kalau kandidat ini gagal di device tertentu, otomatis fallback ke App Info
  (gak crash), tapi user mungkin perlu cari manual: Settings → Apps → App
  Management → [nama app] → Autostart, DAN Settings → Battery → Power saving
  mode → Exceptions, DAN kunci app di recent apps (swipe-down kartu app →
  ikon gembok).
- **Insiden nyata (v1.32)**: `Surface`/`Card` dengan `color`/`containerColor`
  yang gak persis match salah satu slot di `ColorScheme` (contoh: warna
  `Color.Transparent`, atau `surface.copy(alpha=0.x)`) BIKIN Material3 gak
  bisa nentuin `contentColor` otomatis via `contentColorFor()` — fallback ke
  default library (hitam pekat). Semua `Text()` di dalamnya yang gak kasih
  `color=` eksplisit ikut kena, jadi nyaris invisible di dark theme. LESSON:
  kalau `Surface`/`Card` pakai warna custom/alpha-blend (bukan warna asli
  dari `MaterialTheme.colorScheme.*`), WAJIB kasih `contentColor` eksplisit
  juga — jangan andalkan auto-detect.
- Karena gak bisa compile-check, siklus troubleshooting yang efisien:
  1. Untuk perubahan VISUAL murni (warna/layout/shape) → update
     `docs/preview/current.html` DULU, biar user bisa validasi lewat browser
     HP dalam hitungan detik, BARU port ke Kotlin kalau udah oke.
  2. Untuk perubahan LOGIC/behavior → tetap harus lewat siklus penuh
     (zip → Termux → CI → install), gak ada jalan pintas.
  3. Repo ini PUBLIC → GitHub Actions minutes GRATIS/unlimited. Biaya
     sebenarnya bukan uang, tapi WAKTU per putaran (~5-10 menit all-in).

## Command Termux standar (update harian, bukan setup awal)
```
LATEST_ZIP=$(ls -t ~/storage/downloads/AudioEnhancerPro*.zip | head -1) && echo "Pakai ZIP: $LATEST_ZIP" && mkdir -p ~/projects/AudioEnhancerPro && cd ~/projects/AudioEnhancerPro && ( [ -d .git ] || git init ) && find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} + && unzip -o "$LATEST_ZIP" -d ~/projects/AudioEnhancerPro/ && git add -A && git commit -m "[ringkasan perubahan]" && git push
```

## Struktur proyek singkat
- `MainActivity.kt` — lifecycle Activity, permission launcher, shortcut Intent, glue ke ViewModel + `BoosterScreen()`. Dark theme dipaksa di sini (`AudioEnhancerTheme(useDynamicColor=..., themeStyle=...)`, tanpa `darkTheme` param lagi). Batch 36: state `appThemeStyleKey` (persisted) di-map ke `AppThemeStyle` enum, dipass ke tema + `BoosterScreen`.
- `BoosterScreen.kt` — layar utama Compose (BoosterScreen, FeatureControl caller, PowerToggleRow, ServiceStatusBadge, CrashBanner, EqualizerSection, Preset). Batch 36: kartu switch "Gaya Tampilan Radikal" (di bawah kartu Material You) + semua warna muted/glow di layar ini baca dari `LocalSkeuTokens.current`, bukan val hardcoded lagi.
- `SkeuomorphicComponents.kt` — atom UI reusable "Skeuomorphism-lite" (`SkeuCard`, `SkeuTintedCard`, `SkeuPowerButton`, `SkeuSwitch`, `SectionLabel`, `FeatureControl`, `NoRippleIndication`, `Modifier.skeuGlow`). Ganti total `NeumorphicComponents.kt` (dihapus, Batch 31). `skeuGlow`+`SkeuSwitch` baru Batch 32. Batch 36: semua komponen ini theme-aware lewat `LocalSkeuTokens.current` (2 sistem desain, 1 kode komponen) — kalau nambah komponen Skeu baru, WAJIB baca token dari sini, JANGAN reference `Glass*`/`Radical*` val langsung.
- `AudioEnhancerService.kt` — foreground service, attach BassBoost/Virtualizer/Equalizer/LoudnessEnhancer ke session 0.
- `Theme.kt` — palet warna (dark-only), typography, shape, token bevel/glow Skeuomorphism-lite (`SkeuBevelBrush`, `SkeuPrimaryGlow`, dst) buat tema AMOLED Glass. Accent color per-fitur ada di sini (`BassAccent`, `VirtualizerAccent`, dst + varian "2" buat gradient) — TIDAK terpengaruh switch tema (guide baru gak minta accent per-fitur diubah). Batch 36: tambahan token `Radical*` (tema ke-2, Radical Literal Skeuomorphism), `SkeuTokens` data class, `LocalSkeuTokens`/`LocalAppThemeStyle` CompositionLocal, `AudioEnhancerTheme(themeStyle=...)` param baru.
- `PrefsHelper.kt` — SharedPreferences wrapper, semua persistence lewat sini (termasuk preset custom & timestamp crash log). Method `getThemeMode`/`setThemeMode` masih ada (dead code, sengaja TIDAK dihapus biar `PrefsHelperTest.kt` gak perlu diubah) tapi TIDAK dipanggil lagi dari UI manapun sejak Batch 31 — BEDA dari `getAppThemeStyle`/`setAppThemeStyle` (Batch 36, AKTIF dipakai, soal 2 sistem desain bukan terang/gelap).
- `CrashLogger.kt` — tangkap uncaught exception, simpan ke `filesDir/crash_logs/` (rotasi maks 5 file).
- `AudioEnhancerApp.kt` — Application class, cuma buat `CrashLogger.install()` sedini mungkin.
- `OemAutostartHelper.kt` — deep-link ke pengaturan Autostart/battery manager per-OEM (Xiaomi/Oppo/Vivo/Huawei/Samsung/OnePlus/Asus/Infinix-Tecno-itel), fallback ke App Info bawaan Android kalau semua kandidat gagal.
- `ServiceWatchdogWorker.kt` — WorkManager periodic (15 menit), restart service kalau mati padahal `PrefsHelper.getUserWantsRunning()` true. Dijadwalkan sekali di `AudioEnhancerApp.onCreate()`.
- `OnboardingScreen.kt` — 6 halaman onboarding.
- `docs/preview/current.html` — mockup HTML standalone, HARUS di-update kalau ada perubahan arah visual besar.

## TODO / belum dikerjain (kalau user nanya "lanjut yang mana")
- Konfirmasi hasil tombol Autostart v1.35 di Infinix Note 50 Pro 4G & Note 40
  Pro 4G — **DIDEPRIORITASKAN oleh user** (lihat "Status saat ini"), gak perlu
  ditanya/dikerjain proaktif. Kalau user singgung lagi: gagal → opsi (a) cari
  kandidat ComponentName alternatif buat XOS versi device itu spesifik, atau
  (b) terima kenyataan gak ada kandidat reliable buat Infinix/Tecno (persis
  kayak yang dialami `AutoStarter` library) dan fokus ke instruksi manual yang
  jelas di UI aja.
- Rotasi layar/config change, font scaling besar, landscape phone, RTL,
  kontras tombol biru — user bilang eksplisit TIDAK urgent, jangan dikerjain
  duluan tanpa diminta.

## Cara update file ini
Tiap sesi yang bikin keputusan arsitektur/desain baru (bukan sekadar bugfix
kecil), WAJIB update bagian "Status saat ini", "Riwayat pivot", dan/atau
"Keputusan sadar" di atas — supaya sesi berikutnya gak mulai dari nol lagi.
