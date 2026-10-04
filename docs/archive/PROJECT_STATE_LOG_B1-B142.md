# Arsip LOG BATCH `PROJECT_STATE.md` — Batch 1–142 (descending)

Dipindah VERBATIM dari `PROJECT_STATE.md` pada Batch 158 (M8, request user). Isi TIDAK diubah. Rujukan "LOG BATCH N" (N ≤ 142)
di `PROJECT_STATE.md` merujuk ke file ini; root-cause/diff/rasional detail tetap di `CHANGELOG.md`.

- **Batch 142** (`app/build.gradle.kts`+`.github/workflows/build.yml`+`config/detekt/detekt.yml` (baru)+
  `app/lint.xml` (baru)+`FILE_MANIFEST.txt`; request eksplisit user "lintDebug/detekt terfokus,
  non-blocking"): detekt 1.23.6 mode WHITELIST (35 rule potensi-bug, `ignoreFailures=true`) + `lint {}`
  `abortOnError=false` + 3 step CI PALING AKHIR (`continue-on-error`, upload artifact `static_analysis_v*`).
  Kode app 0 perubahan. NOT VERIFIED (YAML/XML parse OK, brace/paren gradle seimbang; nunggu CI).
- **Batch 141** (`BoosterScreen.kt` — 1 file; request eksplisit user "gesture swipe lintas tab
  + sesuai Android vital guards"): Mode Tab Horizontal dapat swipe kiri/kanan pindah tab TANPA
  pager — `.then(pointerInput(Unit){ detectHorizontalDragGestures })` kondisional di Box induk,
  `tabSwipeDelta()` ambang 56dp, handler `rememberUpdatedState` + haptic sama dgn tap-tab,
  `selectedTabIndex` dihoist (tetap `rememberSaveable`). Mode vertikal 0 perubahan. NOT VERIFIED
  (brace/paren 294/294 & 806/806; nunggu CI + device).
- **Batch 140** (`SkeuomorphicComponents.kt` — 1 file, REVERT PENUH; user
  device-test: "geser di area track selain thumb malah langsung berubah jadi
  0 konfigurasinya" — regresi TERBALIK dari tujuan Batch 138): tanpa akses
  device/compiler di sandbox ini buat debug pointer-event multi-pass secara
  aman (2 kali sudah salah tebak: Batch 138 salah asumsi gesture-arbitration,
  Batch 139 salah paket import — pola yang sama BERISIKO kalau ditebak
  KETIGA kalinya tanpa verifikasi nyata), keputusan: REVERT PENUH ke
  `SkeuomorphicComponents.kt` versi Batch 137 (byte-identical, diambil dari
  `Boomly_v137.zip` — bukan ditulis ulang manual, 0 risiko salah ketik balik).
  Proteksi slider individual jadi backlog TODO lagi (lihat ROADMAP) — BUTUH
  device-in-the-loop utk iterasi aman, bukan tebak-tebak-tempel lagi.
  Proteksi kurva EQ (Batch 137) TIDAK disentuh, TIDAK dilaporkan bermasalah.
  NOT VERIFIED tapi risiko rendah (revert ke state yang sudah pernah eksis).
- **Batch 139** (`SkeuomorphicComponents.kt` — 1 file, HOTFIX; user upload log
  CI run 185 gagal, "fix it immediately"): root-cause `e: ...
  SkeuomorphicComponents.kt:84:42 Unresolved reference: awaitFirstDown` +
  `:540:36` sama — import Batch 138 salah paket. `awaitFirstDown` DIDOKUMENTASI
  di halaman `AwaitPointerEventScope` (`androidx.compose.ui.input.pointer`)
  TAPI fisiknya extension function di `androidx.compose.foundation.gestures`
  (satu paket sama `awaitEachGesture`/`detectDragGestures` yang sudah dipakai
  duluan) — dikonfirmasi via web search (dokumentasi resmi + contoh kode nyata)
  sebelum commit, BUKAN tebakan kedua tanpa verifikasi. Fix: pindah baris
  import, `PointerEventPass`+`pointerInput` (paket `ui.input.pointer`, sudah
  benar sejak awal) TIDAK disentuh. 0 perubahan logic/behavior Batch 138.
  NOT VERIFIED (brace/paren 51/51; nunggu CI run berikutnya).
- **Batch 138** (`SkeuomorphicComponents.kt` — 1 file, TAPI berdampak ke SEMUA
  slider app krn `FeatureControl` satu-satunya titik `Slider(` di codebase —
  verified via grep; instruksi eksplisit user "kenapa cuma kurva doang"):
  M3 `Slider` default tap-di-mana-pun-di-track-langsung-loncat — sentuhan
  casual/numpang-lewat yang kebetulan turun DI ATAS slider (scroll halaman
  berisi banyak slider) bisa langsung ubah nilai. Gatekeeper
  `pointerInput`+`awaitFirstDown(pass=Initial)`: turun >32dp dari X-thumb
  SAAT INI → `consume()`, Slider internal tidak pernah anggap valid, 0 nilai
  berubah. Dekat thumb: 0 disentuh, drag normal. `thumbX=w*fraction` linear —
  aproksimasi (M3 Slider sisip inset radius-thumb di 2 ujung track, tidak
  dihitung), aman karena toleransi 32dp > inset (~10-14dp). Trade-off sadar
  sama seperti Batch 137: sentuhan ditolak TIDAK diteruskan ke scroll parent.
  NOT VERIFIED (brace/paren SkeuomorphicComponents.kt 51/51 seimbang; device
  test WAJIB — ini titik paling sering disentuh di seluruh app, geser SEMUA
  jenis slider harus tetap responsif normal).
- **Batch 137** (`EqCurveEditor.kt` — 1 file; instruksi eksplisit user
  "preventing touch"): `onDragStart` sebelumnya pilih band ke-TERDEKAT semata
  dari X sentuhan-turun, ABAIKAN jarak Y ke titik asli — sentuhan DI MANA PUN
  di kanvas 150dp langsung ubah band ke Y sentuhan (termasuk swipe yang cuma
  lewat). Fix: hit-test jarak-kuadrat 2D (x+y) radius 28dp dari posisi RENDER
  titik terdekat — di luar radius, `draggedBand` tetap -1 (0 band berubah, 0
  haptic) sepanjang gesture walau jari terus bergerak. 0 perubahan API/
  pemanggil (`BoosterScreen.kt` tidak disentuh). NOT VERIFIED (brace/paren
  23/23 seimbang; device test: sentuh ringan LUAR titik + swipe scroll di
  atas kurva harus 0 efek, drag TEPAT di titik harus tetap responsif).
- **Batch 136** (`BoosterScreen.kt`, `values/strings.xml`, `values-en/strings.xml`
  — 1 file+strings; MERGE cabang paralel, 0 instruksi fitur baru dari user —
  murni rekonsiliasi non-destruktif, lihat "🔀 Rekonsiliasi cabang paralel"):
  import 5 preset baru dari sesi lain (Gaming/Cinema/EDM/Podcast/Acoustic,
  `Preset.eqBands: List<Int>`, `applyPreset()` if/else type-safe) — Preset
  Cepat 4→9. `ServiceWatchdogWorker.kt`+`SettingsScreen.kt`+`PrefsHelper.kt`+
  `AudioEnhancerService.kt`+`BoosterWidgetProvider.kt`+`widget_booster_info.xml`
  DIPERTAHANKAN persis punya sesi ini (verified 0 baris unik sisi lain hilang,
  KECUALI heartbeat exact-alarm `ServiceWatchdogWorker` — sengaja TETAP mati
  permanen, keputusan Batch 132 lebih baru). EqCurveEditor (133) + tombol
  Reset Equalizer (135) TETAP utuh, 0 overlap region kode dgn preset baru.
  NOT VERIFIED (brace/paren `BoosterScreen.kt` 284/284, parity strings ID/EN
  189=189, semua `R.string` ter-resolve, diff post-merge vs kedua sisi asal
  = 0 baris kode hilang).
- **Batch 135** (`BoosterScreen.kt` + strings ID/EN — 1 file+strings; user
  "urgent"): `EqualizerSection` (saat expanded) dapat `OutlinedButton` "Reset
  Equalizer" → tiap band `levels[band]=0` + `onBandChange` (jalur sama slider/kurva
  & reset preset Flat; 0 sentuh Service/ViewModel). Nonaktif kalau sudah flat.
  NOT VERIFIED (brace/paren seimbang, parity strings 184=184).
- **Batch 134** (`ScheduleWorker.kt` baru, `PrefsHelper.kt`, `SettingsScreen.kt` +
  strings ID/EN — 3 file+strings; Fase 8 B ROI #7, user pilih "Skip fade-out,
  kerjakan Scheduler dulu"): Pengaturan → "Jadwal Otomatis": toggle + jam nyala/
  mati harian (TimePickerDialog). Rantai `OneTimeWork` unik (event terdekat),
  persist lintas reboot; event telat >60 mnt dilewati. Start diblokir Android
  12+ → notifikasi ketuk-untuk-nyalakan. 0 sentuh Service/Manifest/BootReceiver.
  NOT VERIFIED (review manual: brace/paren 3 file seimbang, parity strings
  183=183, R.string ter-resolve). Screenshot user (device): kurva↔slider EQ
  terlihat sinkron; label "14 kHz" terpotong tepi kanan (belum diperbaiki).
- **Batch 133** (`EqCurveEditor.kt` baru, `BoosterScreen.kt` — 2 file; Fase 8
  A ROI #6, instruksi user pilih dari backlog ROI): kurva EQ drag-point,
  pelengkap slider `EqualizerSection` (share state `levels`/callback yang
  sama, 0 duplikasi). 0 perubahan backend (`AudioEnhancerService.kt`/
  `BoosterViewModel.kt` tidak disentuh — API band get/set sudah lengkap
  sejak Batch 87). NOT VERIFIED device, review manual OK (brace/paren
  seimbang, API diverifikasi terhadap pola yang sudah terbukti di file lain).
- **Batch 132** (`ServiceWatchdogWorker.kt`, `SettingsScreen.kt` — 2 file;
  instruksi eksplisit user, alasan baterai): `scheduleExactRecovery()` jadi
  no-op permanen (heartbeat 1mnt exact alarm dimatikan), kartu "Pemulihan
  Cepat" dihapus dari Settings. Watchdog 15mnt TIDAK disentuh — auto-recovery
  OS-kill tetap ada, cuma lebih lambat (~15mnt vs ~1-9mnt). Beda dgn
  penolakan Batch 131 (lihat "Keputusan sadar"): target & alasan beda, bukan
  kontradiksi. NOT VERIFIED device, review manual OK (brace/paren seimbang,
  0 import orphan).
- **Batch 131** (`widget_booster_info.xml` — 1 file; + README/CHANGELOG;
  instruksi eksplisit user "Opsi A+B Hybrid"): `updatePeriodMillis` 0→30mnt
  (Opsi B, native, 0 izin/wakelock baru). Opsi A: 0 kode — `onReceive()`
  toggle SUDAH baca `isRunning` live sejak awal, diverifikasi bukan diubah.
  **TOLAK** instruksi hapus watchdog+heartbeat total — premis user (keduanya
  cuma buat widget) tidak akurat, fungsi utama = auto-recovery service dari
  OS-kill (Batch 124); hapus = regresi. Lihat "Keputusan sadar". NOT VERIFIED
  device, review manual OK (XML well-formed, 1 atribut).
- **Batch 130** (`ServiceWatchdogWorker.kt`, strings ID/EN — 2 file; user
  konfirmasi Batch 129 pulih <3 menit di device, minta "mentokin"):
  `FAST_RECOVERY_INTERVAL_MS` 2→1 menit + sinkron komentar/KDoc +
  `settings_fast_recovery_desc` ID/EN "±2 menit"→"±1 menit". 1 menit = lantai
  praktis yang SENGAJA dipilih (bukan limit OS) — heartbeat ini jalan
  terus-menerus selama service nyala, di bawah 1 mnt mulai murni ongkos
  wake-up/baterai tanpa manfaat pulih tambahan yang terasa; floor Doze dalam
  ~9 mnt/app non-exempt tetap berlaku sama seperti Batch 129. Status:
  **NOT VERIFIED** (statis: brace/paren 19/19+124/124 tetap balance, XML
  well-formed, parity ID/EN 173=173; nunggu CI + device fisik).
- **Batch 129** (`ServiceWatchdogWorker.kt`, strings ID/EN — 2 file; user
  konfirmasi Batch 128 jalan "~5 menit", minta lebih cepat kalau bisa):
  `FAST_RECOVERY_INTERVAL_MS` 5→2 menit + sinkron komentar kelas/KDoc +
  `settings_fast_recovery_desc` ID/EN "±5 menit"→"±2 menit". Catatan jujur:
  floor OS ~9 mnt/app non-exempt di Doze DALAM tetap berlaku (turunin request
  gak nembus floor itu) — benefit nyata di kondisi non-Doze/Doze awal. Status:
  **NOT VERIFIED** (statis: brace/paren 19/19+123/123 tetap balance, XML
  well-formed, parity ID/EN 173=173; nunggu CI + device fisik).
- **Batch 128** (`ServiceWatchdogWorker.kt`, `AudioEnhancerService.kt`,
  `SettingsScreen.kt`, strings ID/EN; laporan user "izin alarm tak ada di
  pengaturan app + waktu pulih sama dgn watchdog"): 2 root cause — izin default
  ditolak Android 14+ tanpa UI minta, + desain reaktif. Fix: kartu "Pemulihan Cepat"
  (status+deep-link, poll 1,5 dtk) + heartbeat proaktif (pasang di `onStartCommand`,
  cabut di ACTION_STOP). Status: **NOT VERIFIED** (statis: brace/paren balance 3 file,
  XML well-formed, parity ID/EN 173=173; nunggu CI + device fisik).
- **Batch 127** (`ServiceWatchdogWorker.kt`, `WatchdogAlarmReceiver.kt` [baru],
  `AndroidManifest.xml` — 3 file; jawab pertanyaan user "recovery otomatis di
  bawah 15 menit"): tambah exact-alarm fast-recovery OPPORTUNISTIC — kalau tick
  watchdog deteksi recovery masih perlu, jadwalkan 1x `setExactAndAllowWhileIdle`
  (~5 menit, rate-limit OS ~9 menit kalau belum battery-exempt) via
  `WatchdogAlarmReceiver` baru, self-chaining sampai pulih/user matiin. Perlu
  `SCHEDULE_EXACT_ALARM` — TIDAK ada UI/deep-link request izin ini (di luar
  scope 3-file); kalau user belum grant manual, fitur diam total, 0 dampak ke
  watchdog 15 menit lama. Status: **NOT VERIFIED** (statis only: brace/paren
  0/0 x2 .kt + XML well-formed; nunggu CI + device fisik dengan izin granted).
- **Batch 126** (`QuickToggleTileService.kt`, `MainActivity.kt` — 2 file; hotfix,
  laporan user susulan Batch 125: "kok harus nunggu lama"): tambah 2 titik resync
  widget+tile yang jauh lebih sering dari watchdog 15 menit —
  `onStartListening()` (tiap shade dibuka) & `onResume()` (tiap app dibuka).
  Watchdog Batch 125 TETAP jadi jaring pengaman terakhir. Status: **NOT
  VERIFIED** (statis only: brace/paren 0/0 x2 file; nunggu device fisik).
- **Batch 125** (`ServiceWatchdogWorker.kt` HANYA — 1 file; hotfix URGENT, laporan
  user dari uji device Batch 124): widget "Aktif" (basi) vs QS Tile "Nonaktif"
  (benar) tidak sinkron setelah kill keras (SIGKILL, `onDestroy()` tidak
  terpanggil, hook refresh Batch 44 tidak sempat jalan). Fix: watchdog tiap
  tick SELALU paksa resync widget+tile ke `isRunning` ground truth, terlepas
  perlu restart atau tidak. Status: **NOT VERIFIED** (statis only: brace/paren
  0/0; nunggu device fisik — skenario di RESUME POINT).
- **Batch 124** (`AudioEnhancerService.kt`, `ServiceWatchdogWorker.kt`, strings ID/EN;
  hotfix URGENT, laporan user): watchdog gagal diam-diam restart service karena
  Android 12+ background-start restriction (`ForegroundServiceStartNotAllowedException`
  tidak ditangkap) — root cause SAMA PERSIS kenapa widget/QS Tile "selalu berhasil"
  (exempted) sementara watchdog tidak. Fix: try-catch + fallback notifikasi
  tap-to-restart channel terpisah (`CHANNEL_ID_RECOVERY`, HIGH). 0 permission baru.
  Status: **NOT VERIFIED** (static lolos: brace/paren balance, XML well-formed, parity
  string ID/EN 166=166; nunggu CI + device fisik — skenario di RESUME POINT).
- **Batch 123** (`AudioEnhancerService.kt`; hotfix, laporan user pasca-122):
  regresi "speaker internal ikut pakai preset Kustom padahal tidak disetel
  kesitu". Root cause: `onOutputRouteChanged()` hanya menangani device BARU
  tersambung, jadi saat device ber-preset LEPAS, Bass/Virtualizer/Loudness/EQ
  yang sudah ditimpa preset itu tidak pernah direvert. Fix: snapshot
  (`autoProfileBaseline`) sebelum preset pertama diterapkan (di
  `applyCustomPresetByName()`), restore (`restoreAutoProfileBaseline()`) saat
  route balik ke kategori tanpa preset — termasuk speaker, dideteksi via
  `hasNoExternalOutputDeviceLeft()` (cek `getDevices()`, bukan cuma payload
  callback). Device lepas dengan device eksternal LAIN masih nyambung tetap
  di-skip (ambigu, sama seperti sebelumnya — lihat CHANGELOG). 1 file
  disentuh, 0 API publik/UI berubah. Status: **NOT VERIFIED** (no toolchain
  lokal; static manual lolos: brace 215=215, paren 906=906; nunggu CI +
  device fisik — skenario test di RESUME POINT).
- **Batch 122** (`AudioEnhancerService.kt`, `PrefsHelper.kt`, `SettingsScreen.kt`,
  strings ID/EN; user "next" → Fase 8 ROI #5): Auto-Profil per Output — extend
  `onOutputRouteChanged()` (Batch 82/83, dulu cuma banner info) jadi auto-apply
  preset CUSTOM (bukan built-in — definisinya cuma ada di UI, di luar scope)
  saat device output BARU tersambung (bukan saat lepas — tidak andal tebak
  device pengganti). Opt-in default MATI (`PrefsHelper.getAutoProfileEnabled`).
  4 kategori (`ROUTE_CATEGORY_*`): speaker/kabel/bluetooth/usb, UI chip picker
  di Settings (0 hoist ViewModel/MainActivity, baca/tulis prefs langsung, pola
  `useHorizontalLayout`). `applyCustomPresetByName()` baru di Service (reuse
  100% setter lama) — reusable buat Scheduler (Fase 8 B) nanti. Keterbatasan
  didokumentasikan: slider UI TIDAK live-refresh kalau app terbuka pas route
  berubah (suara tetap benar berubah). Status: **NOT VERIFIED** (no toolchain
  lokal — sandbox tanpa Android SDK/Gradle/network; static manual lolos:
  brace/paren balance + XML well-formed + parity string ID/EN 162=162; nunggu
  CI + device fisik).
- **Batch 121** (`AudioEnhancerService.kt`, `PrefsHelper.kt`, `BoosterViewModel.kt`,
  `MainActivity.kt`, `BoosterScreen.kt`, `Theme.kt`, strings ID/EN; user "next" →
  Fase 8 ROI #4): Compressor — 1 band `DynamicsProcessing.MbcBand` full-range
  DITAMBAHKAN ke objek `DynamicsProcessing` yang SAMA dengan master limiter
  (`mbcInUse=true, mbcBandCount=1`), bukan effect terpisah. Slider "Amount"
  0-100% (`setCompressorAmount()`) menyetir ratio 1:1→6:1 + threshold -1→-24dB
  + postGain makeup 0→+6dB sekaligus. State ikut `dynamicsState` (pola sama
  `equalizerFallbackActive`), masuk `ControlRecoveryBanner`. SENGAJA TIDAK ikut
  sistem preset (Tunnel Vision — di luar scope). Status: **USER-CONFIRMED
  WORKING di device fisik** (user: "makin dinaikin compressor nya makin
  jelas" — compile OK + kompresi kerasa sesuai desain kurva 0-100%; SEBELUMNYA
  NOT VERIFIED, dikoreksi Batch 122). Kurva threshold/ratio/makeup persis
  belum di-fine-tune/diukur (subjektif "kerasa jelas" ≠ terukur) — biarkan
  apa adanya kecuali user lapor masalah spesifik.
- **Batch 120** (`AndroidManifest.xml`, `AudioEnhancerService.kt`,
  `BoosterViewModel.kt`, `MainActivity.kt`, `BoosterScreen.kt`, strings
  ID/EN; user pilih "Asli" atas keputusan tertunda Fase 8E): Spectrum
  Visualizer — `Visualizer` AudioEffect session 0 (permission-gated,
  gagal-aman kalau `RECORD_AUDIO` belum granted), FFT→24-band di
  `computeSpectrumBands()`, poll 50ms terpisah dari loop EffectState 1dtk,
  kartu UI (minta izin/gagal/bar live) + `SpectrumBars` Canvas. Koreksi
  diri saat coding: sempat salah asumsi `setDataCaptureListener` punya
  overload `Handler` (TIDAK ADA di API resmi) — diperbaiki sebelum commit,
  balik ke callback thread default (main, murah, non-blocking). Status:
  **NOT VERIFIED** (static lolos: brace/paren balance + XML well-formed +
  parity string ID/EN 151=151; belum diuji device fisik — dialog izin,
  capture rate riil, kalibrasi `/90f` di `computeSpectrumBands()` kandidat
  pertama kalau bar "terlalu pendek/mentok atas").
- **Batch 119** (`AudioEnhancerService.kt`, `PrefsHelper.kt`, `SettingsScreen.kt`,
  strings ID/EN; user "next"): Fase 8 B Sleep timer bag. 1 (auto-stop) —
  section "Timer Tidur" (15-120 mnt), waktu berakhir absolut di prefs, tick
  Handler ≤30 dtk di Service, habis → `requestStop()`. #2 Visualizer dilewati
  (izin `RECORD_AUDIO` terverifikasi). Status: **NOT VERIFIED** (static lolos).
- **Batch 118** (0 kode, dok-only — instruksi eksplisit user): cleanup
  `PROJECT_STATE`/`README`/`CHANGELOG`/`FILE_MANIFEST`/preview HTML,
  `archive/`→`docs/archive/`. Status tema dikoreksi → 5 varian USER-CONFIRMED
  (gate "next→validasi" Batch 117 dicabut); varian 4→5 & CI 1-job
  disinkron; section basi dibuang; log 107-117 dipangkas; +tag & RESUME POINT.
- **Batch 117** (0 kode, gate check): user "next" generik → sempat ditahan
  buat validasi 2 tema (Serene M3, Neumorphism pine-tint). SUPERSEDED Batch
  118: kedua tema sudah lama berhasil (konfirmasi user) — gate itu basi.
- **Batch 116** (`values-en/strings.xml`, `values/strings.xml`): 9 string EN
  Export/Import preset (tertunda dari Batch 115), parity ID/EN 0 selisih.
  Status: **VERIFIED** (resource murni, 0 logic Kotlin).
- **Batch 115** (`PrefsHelper.kt`, `SettingsScreen.kt`, `values/strings.xml`):
  Export/Import preset `.json` (Fase 8 D) — envelope JSON, parsing atomik
  per-entry, reuse `addCustomPreset`; UI "Cadangkan Preset" (SAF, I/O di
  `Dispatchers.IO`). Status: **VERIFIED** (user: compile+runtime OK, B116).
- **Batch 114** (`PROJECT_STATE.md`, planning, request user "planning biar
  lebih powerful"): tambah Fase 8 — Powerful Upgrade Roadmap (5 kategori +
  urutan ROI). 0 kode. Status: N/A.
- **Batch 113** (`Theme.kt`, hotfix build, input `log_fail_v161-debug-run161`):
  `SereneCardShape` `Shape`→`CornerBasedShape` (+1 import) — fix
  `Theme.kt:1074` type mismatch, regresi Batch 112; 0 nilai/logic diubah.
  Status: **VERIFIED** (implisit: app compile+jalan di Batch 115-116).
- **Batch 112** (`Theme.kt`, `SkeuomorphicComponents.kt`): fix komplain user
  (screenshot) — kartu Serene M3 masih rounded biasa. `SkeuTokens` +`cardShape`;
  `SkeuCard`/`SkeuTintedCard` baca `tokens.cardShape` (4 varian lama =
  `RoundedCornerShape` radius sama, 0 perubahan visual). Status: **VERIFIED**
  (user, Batch 118).
- **Batch 111** (`Theme.kt`, `PrefsHelper.kt`, `MainActivity.kt`,
  `BoosterScreen.kt`, strings ID/EN): varian ke-5 "Serene M3" — Material 3
  flat-tonal, `SereneTypography`/`SereneShapes` (cut-corner) sendiri, aksen
  sage+lavender; enum `+SERENE_M3`, toggle ke-5. Preview HTML tetap Midnight
  Glass saja. Status: **VERIFIED** (user, Batch 118).
- **Batch 110** (`Theme.kt`): komplain user — aksen Misty Pine kalah dominan
  dari tint navy di `SkeuDualDirectionalShadow` (berulang tiap kartu).
  `NeumoEdgeHighlight`/`NeumoEdgeShadow` diturunkan dari `NeumoMistyPine`;
  alpha Batch 56 & base Deep Navy TIDAK diubah. Status: **VERIFIED** (user,
  Batch 118).
- **Batch 109** (`Theme.kt`, strings ID/EN): aksen Neumorphism Aurora→"Misty
  Pine Forest" (`NeumoMistyPine` `0xFF80A891`/`NeumoMistyPineDeep`; instruksi
  user); shape/typography Batch 108 & base Deep Navy TIDAK disentuh. Status:
  **VERIFIED** (user, Batch 118).
- **Batch 108** (`Theme.kt`, strings ID/EN): Neumorphism dirombak ala "Blade
  Runner" (instruksi user) — shape near-flat 22/15dp→4/3dp,
  `NeumorphismTypography` per-varian (pertama kali), aksen Brass→Aurora
  (lalu Batch 109). Base Deep Navy & `SkeuomorphicComponents.kt` 0 disentuh.
  Status: **VERIFIED** (user, Batch 118).
- **Batch 107** (`BoosterViewModel.kt`, `MainActivity.kt`, `BoosterScreen.kt`,
  strings ID/EN): Fase 0 #9 ditutup — `lastOutputRouteDescription` (Batch 83)
  dipoll ViewModel → `OutputRouteBanner` baru (tint info, beda dari
  `ControlRecoveryBanner`). Fase 0 5/9. Belum tervalidasi runtime (butuh
  ganti route BT/wired di device fisik).
- **Batch 106** (0 kode, dok-only): konsolidasi `roadmap.md` + 2x
  `PENDING_*.md` → `PROJECT_STATE.md` § TODO/ROADMAP, sumber lama dipindah
  ke `archive/` (Batch 118: dipindah lagi ke `docs/archive/` sesuai SOP).
  README diaudit, "0 info usang" — audit Batch 118 nemu klaim CI 2-job basi.
- **Batch 105** (`BoosterScreen.kt`, REVERT): swipe-antar-tab Batch 104
  (auto-height pager) regresi UI parah di device fisik (klip/distorsi) —
  direvert total, identik byte-per-byte ke Batch 103. Lihat "Keputusan
  sadar" untuk lesson permanen.
- **Batch 104** (`BoosterScreen.kt`, DIREVERT Batch 105): percobaan
  kembalikan swipe-antar-tab via `HorizontalPager` auto-height
  (`onSizeChanged`). GAGAL di runtime — jangan diulang cara sama.
- **Batch 103** (`BoosterScreen.kt`): hapus swipe-antar-tab, ganti tap-tab
  (`selectedTabIndex` + `rememberSaveable`). Hasil: 1 clip fisik sisa
  (wajar, tepi layar), 0 clip ganda. Belum tervalidasi runtime saat itu
  (sekarang TERVALIDASI stabil, lihat "Keputusan sadar").
- **Batch 102** (0 kode, keputusan scope): user tanya kenapa clip fisik
  gak dihilangkan total. Dijawab 2 opsi arsitektur (auto-height pager vs
  hapus swipe) — 0 dipilih sesi itu, dieksekusi Batch 103.
- **Batch 101** (`BoosterScreen.kt`): fix clip ganda vertikal Mode Tab
  Horizontal — `padding(horizontal=16.dp)` → `padding(16.dp)` di Column
  per-halaman pager.
- **Batch 100** (`BoosterScreen.kt`): fix ruang tab terasa sempit — Column
  pembungkus utama scroll di kedua mode, `HorizontalPager` tinggi eksplisit
  62% layar (dikunci 360-640dp) ganti `weight(1f)`.
- **Batch 99** (`BoosterScreen.kt`): 2 fix Mode Tab Horizontal —
  `TabRow`→`ScrollableTabRow`(`edgePadding=0`) biar gak sempit; Column
  per-halaman pager dapat `padding(horizontal=16.dp)` biar shadow gak
  kepotong clip pager.
- **Batch 98** (`SettingsScreen.kt`, hotfix build): hapus 1 baris import
  `weight` yang salah tabrak symbol internal Compose → fix compile error.
- **Batch 97** (`BoosterScreen.kt`/`SettingsScreen.kt`/`PrefsHelper.kt`):
  revert layout utama vertikal→default, Mode Tab Horizontal (struktur
  Batch 94-96) jadi opsi opt-in toggle Settings. Lihat "Keputusan sadar".
- **Batch 96** (`BoosterScreen.kt`): `.navigationBarsPadding()` ditambah
  di Column tab pager — cegah konten bawah ketutup nav/gesture bar.
- **Batch 95** (`BoosterScreen.kt`, fix hasil screenshot): tab label
  kepotong → `ScrollableTabRow`→`TabRow` evenly-divided; panah "→" di
  tombol bantuan wrap aneh → ganti `Icon` vector.
- **Batch 94** (`BoosterScreen.kt`): kelompokkan layar utama jadi 3 tab
  (Kontrol/Tampilan/Bantuan) via `ScrollableTabRow`+`HorizontalPager`.
  Header/banner status TETAP di luar tab. Belum tervalidasi visual sama
  sekali saat itu (kemudian di-revert-default Batch 97).
- **Batch 93** (`BoosterScreen.kt`): styling pill Preset Cepat — unselected
  jadi outline-only (transparent+border), selected tetap filled+glow.
  `docs/preview/current.html` disinkron sekalian (ditemukan sudah lama beda
  dari Kotlin).
- **Batch 92** (`SettingsScreen.kt`, fix hasil screenshot): divider row
  "Versi Aplikasi" salah indent (warisan default 50dp buat baris ber-icon)
  → `startIndent=0.dp` khusus baris ini.
- **Batch 91** (`SettingsScreen.kt`+strings ID/EN): grouped-list ala iOS —
  baris versi app dipisah dari blok aksi cek-update via `SkeuGroupDivider`.
- **Batch 90** (`Theme.kt`+`SettingsScreen.kt`): Large Title 34sp Bold ala
  iOS HIG (`headlineMedium`, dari 28sp ExtraBold lama). Title inline
  `SettingsScreen` turun ke `titleMedium` (17sp) biar gak kegedean.
- **Batch 89** (0 kode, validasi): screenshot user konfirmasi Batch 88
  (grouped-list Kontrol) render benar — Fase 7 Fase 1 SELESAI penuh.
- **Batch 88** (`SkeuomorphicComponents.kt`+`BoosterScreen.kt`): mulai iOS
  Look Hybrid Rombak — section Kontrol (Bass/Virtualizer/Loudness) jadi 1
  `SkeuCard` grouped-list ala iOS Settings (`SkeuGroupDivider` baru), ganti
  3 card terpisah. Warna/shadow tiap tema TIDAK disentuh.
- **Batch 87** (`AudioEnhancerService.kt`): Fase 1 rebuild session-0 —
  `DynamicsProcessing` dapat PreEq 5-band fallback, HANYA aktif kalau
  `Equalizer` legacy device `UNAVAILABLE` total. 0 perubahan perilaku di
  device dengan Equalizer legacy normal (mayoritas).
- **Batch 86** (test baru `AudioEnhancerServiceStateTest.kt`): 13 test
  Robolectric — Fase 0 audit item #8 selesai.
- **Batch 85** (`AudioEnhancerService.kt`, hotfix): fix compile error
  `DynamicsProcessing.Limiter` param pertama salah (`0` bukan Boolean) →
  `inUse=true`. Lesson: constructor API effect jarang pakai WAJIB dicek ke
  dokumentasi resmi, bukan tebak dari nama variabel.
- **Batch 84** (`AudioEnhancerService.kt`): effect baru `DynamicsProcessing`
  sebagai limiter murni (ceiling tambahan, bukan pipeline gain-staging
  penuh — API publik gak bisa jamin urutan insert effect). Digated
  `SDK_INT >= P` (guard yang sempat kelewat di draf awal, self-corrected).
- **Batch 83** (`AudioEnhancerService.kt`): `AudioDeviceCallback` register
  — deteksi perpindahan output route, nudge `enableEffects()` (bukan
  recreate), digate `isRunning`.
- **Batch 82** (0 kode): antrian Fase 0 audit sisa 5 item dicatat, tunggu
  user pilih urutan (BLOKER #6 butuh konfirmasi risiko eksplisit).
- **Batch 81** (3 file+strings): Settings cek-update tampilkan komparasi
  versi eksplisit + ringkasan rilis + tombol unduh inline, 0 logic unduh
  baru (reuse `BoosterViewModel`).
- **Batch 80** (0 kode): pangkas ATURAN PERMANEN 98→63 baris (housekeeping
  serupa yang sedang dilakukan sekarang di seluruh file).
- **Batch 79** (0 kode): user konfirmasi fix Batch 78 (quote YAML) jalan
  di produksi — caveat ditutup.
- **Batch 78** (`.github/workflows/build.yml`, DIKONFIRMASI JALAN): root
  cause update-checker gagal total sejak Batch 69 — `name:` Release YAML
  tidak di-quote, ` #<run_number>)` kepotong jadi komentar YAML saat parse.
  Fix: bungkus quote. Lesson: value YAML yang mengandung `#` WAJIB di-quote.
- **Batch 77** (0 kode): investigasi laporan "gagal cek update" → BUKAN
  bug, user tes dengan Mode Pesawat ON (WiFi tanpa rute internet bersih).
- **Batch 76** (`app/build.gradle.kts`+`.github/workflows/build.yml`):
  `versionName` ikut otomatis dari `GITHUB_RUN_NUMBER` (sebelumnya cuma
  `versionCode`). Step CI "Extract changelog" diredesain ambil section
  teratas CHANGELOG.md (bukan match versi lagi).
- **Batch 75** (`UpdateManager.kt`+`BoosterViewModel.kt`): fix "app bilang
  sudah terbaru padahal belum" — `fetchLatestRelease()` return sealed class
  `CheckResult` (Available/UpToDate/Failed) ganti `null` ambigu.
- **Batch 74** (`MainActivity.kt`): fix regresi gesture-back nutup app
  (bukan balik ke BoosterScreen) — `BackHandler` baru khusus `showSettings`.
- **Batch 73** (5 file+strings): `SettingsScreen.kt` baru — entry point
  cek-update manual (ikon ⚙️ di header), status IDLE/CHECKING/UP_TO_DATE/
  FOUND/ERROR.
- **Batch 72** (dok-only): restrukturisasi `PROJECT_STATE.md` jadi 2 lapis
  (Aturan Permanen vs Log Harian) — 0 konten historis dihapus saat itu.
- **Batch 70-71** (dok-only): PIN format ZIP `Boomly_<versi>-<batch>.zip` +
  glob Termux `Boomly*.zip` eksplisit — format itu OBSOLETE (direvisi Batch
  76/94), lihat "Keputusan sadar".
- **Batch 69** (5 file+3 parsial, v1.99.0): fitur in-app update — cek versi
  via judul GitHub Release `(Run #N)`, unduh APK chunk-streaming Okio
  (DILARANG `readBytes()`), install via FileProvider. Permission
  `INTERNET`+`REQUEST_INSTALL_PACKAGES` pertama kali di project ini.
- **Batch 68** (`strings.xml` ID/EN+preview html+README): ekspansi rebrand
  — semua string user-facing app jadi "Boomly". Path fungsional
  (`CrashLogger.APP_FOLDER`, applicationId, dll) TETAP tidak berubah.
- **Batch 66-67** (dok-only): iterasi nama ZIP output `AudioEnhancerPro`→
  `AudioBooster`→`Boomly` (0 kode); format final: "Keputusan sadar".
- **Batch 65** (`app/build.gradle.kts`+`.github/workflows/build.yml`):
  inspeksi penuh Feature Lock CI/CD — 2 pelanggaran ketemu & fix:
  `versionCode` yang selama ini manual → otomatis `GITHUB_RUN_NUMBER`;
  "Stale Run Guard" (spek asli, gak pernah diimplementasi 64 batch) —
  step baru exit 1 kalau `GITHUB_SHA` != HEAD main terkini.
- **Batch 64** (`BoosterScreen.kt`): perkuat intensitas 3 dari 4 preset
  bawaan (Flat sengaja tidak disentuh), tetap dalam batas kontrak platform.
- **Batch 63** (`BoosterScreen.kt`+`PrefsHelper.kt`+test): preset custom
  sekarang ikut simpan state Equalizer manual (`eqBands`), backward-compat
  penuh ke preset lama (field kosong = EQ manual tak disentuh).
- **Batch 62** (`BoosterScreen.kt`+`BoosterViewModel.kt`+`MainActivity.kt`):
  `retryControlAcquisition()` (Batch 61) disurface ke UI — `ControlRecoveryBanner`
  baru, tampil saat effect `CONTROL_LOST`/`FAILED`.
- **Batch 61** (`AudioEnhancerService.kt`): `attachEffects()` dipecah per-
  effect + fungsi publik `retryControlAcquisition()` (belum ada pemanggil
  otomatis, sengaja — hindari retry-loop tanpa data device nyata).
- **Batch 60** (`AudioEnhancerService.kt`): riset dokumentasi resmi
  konfirmasi range Bass/Virtualizer `0..1000` adalah kontrak platform
  (bukan device-specific seperti diasumsikan audit awal). Tambah
  `getBassRoundedStrength()`/`getVirtualizerRoundedStrength()` diagnostik.
- **Batch 59** (`BoosterScreen.kt`): `equalizerEffectState` disurface ke
  `EqualizerSection` sebagai subtitle pesan `CONTROL_LOST`/`FAILED`.
- **Batch 58** (3 file): `EffectState` (Batch 57) disurface Service→
  ViewModel (poll 1 detik)→UI (`helpText` FeatureControl berubah saat
  gagal).
- **Batch 57** (`AudioEnhancerService.kt`): user upload audit eksternal gap
  audio-engine robustness. `EffectState` enum baru per-effect + listener
  `CONTROL_LOST` asli + exception dulu silent sekarang `Log.e`+`FAILED`.
  Mulai 9-item roadmap Fase 0, dikerjakan satu-satu sesuai instruksi user.
- **Batch 56** (`Theme.kt`+`SkeuomorphicComponents.kt`): tuning kontras
  dual-shadow Neumorphism dinaikkan lagi (alpha, elevation, spread).
- **Batch 55** (`.github/workflows/build.yml`): fix artifact log salah
  ke-upload untuk job yang di-skip (bukan gagal) — kondisi `if:` diperketat
  ke `outcome=='failure'`.
- **Batch 54** (`SkeuomorphicComponents.kt`, hotfix build): Batch 53 gagal
  CI — `drawOutline` tidak eksis di Compose UI graphics, ganti `drawPath`+
  konversi `Outline`→`Path` manual.
- **Batch 53** (`SkeuomorphicComponents.kt`, GAGAL BUILD — lihat Batch 54):
  `SkeuDualDirectionalShadow` dirombak ke teknik gambar-ulang-siluet manual
  (bukan `Modifier.shadow()` native, dibatasi alpha keras oleh sistem).
- **Batch 52** (`Theme.kt`): reset total palet Neumorphism ke "Deep Navy &
  Classic Brass" sesuai spek eksak user — root cause versi lama gak
  genuine neumorphism (gradient+sheen di permukaan kartu, ciri
  skeuomorphism/glass, bukan neumorphism flat+shadow-only).
- **Batch 51** (`BoosterScreen.kt`): `SnackbarHost` pertama kali dipakai —
  3 titik feedback sukses baru (simpan/hapus preset, hapus log crash).
- **Batch 50** (`gradle.properties`+`roadmap.md` baru): configuration-cache
  dinyalakan (setelah CI Batch 49 dikonfirmasi hijau). `roadmap.md` baru —
  sintesis semua backlog jadi 1 checklist 6-fase (kemudian dikonsolidasi
  balik ke file ini di Batch 106).
- **Batch 49** (6 file, PERUBAHAN ARSITEKTUR TERBESAR sejak Batch 18):
  Hilt+kapt DICABUT TOTAL — satu-satunya titik inject sudah gratis dari
  `AndroidViewModel` bawaan. README: link download APK dipindah ke atas.
- **Batch 48** (`.github/workflows/build.yml`): body GitHub Release
  dipotong ke paragraf pembuka + cap 15 baris (sebelumnya ambil mentah
  seluruh entry CHANGELOG, bisa >80 baris).
- **Batch 47** (`Theme.kt`+`SkeuomorphicComponents.kt`): fix "kurang
  depth/ambient bocor" — `SkeuTokens` dapat `shadowLightTint`/`shadowDarkTint`,
  2-layer `Modifier.shadow` native terarah KHUSUS Neumorphism.
- **Batch 46** (5 file): tema ke-3 jadi genuine Neumorphism "ultra
  realistic+immersive" (dari Skeuomorphism bevel-hard lama), palet
  Platinum+Ruby. Persistence key kode TETAP `SKEUOMORPHISM` (Protected).
- **Batch 45** (`AudioEnhancerService.kt`): fix race condition nyata
  (`isRunning` tanpa `@Volatile`, dibaca thread beda oleh watchdog) +
  `THREAD_PRIORITY_URGENT_AUDIO` di `onCreate()`.
- **MODE MAINTENANCE dimulai setelah Batch 44** — fitur inti dianggap
  selesai. Implikasi: Claude JANGAN proaktif nawarin fitur besar baru;
  prioritas bugfix/crash/regresi/permintaan kecil. Permintaan eksplisit
  user untuk fitur besar TETAP dikerjakan (maintenance mode = larangan
  inisiatif Claude, bukan larangan mutlak buat user).
- **Batch 44** (2 file): fix "widget aktif, QS Tile nonaktif" — QS Tile
  gak pernah diberi tahu state-change dari path lain, tambah
  `requestTileUpdate()` di 3 titik yang sama dengan widget refresh.
- **Batch 43** (6 file): tema ke-4 "Studio Equalizer" — neumorphism palet
  studio abu-abu + aksen neon-lime `#39FF14` (khusus state aktif).
- **Batch 42** (`.github/workflows/build.yml`): tag/nama Release/APK/artifact
  pakai `github.run_id` (unik permanen) — override sadar keputusan Batch 11
  (tag polos yang bisa tabrakan). Trade-off: Releases numpuk 1 per run CI.
- **Batch 41** (3 perubahan `gradle.properties`+`build.gradle.kts`): kapt
  worker API + incremental + `buildConfig=false` (0 pemanggil terkonfirmasi
  grep) — percepat compile CI, tanpa ubah dependency.
- **Batch 40** (`.github/workflows/build.yml`+`gradle.properties`): job
  build+release digabung 1 job, cache Gradle, parallel+build-cache+heap naik.
- **Batch 39** (`Theme.kt`+`SkeuomorphicComponents.kt`+strings): varian
  Skeuomorphism jadi 100% otonom — radius/shape sendiri, aksen
  tembaga→titanium-silver metalik.
- **Batch 38** (5 file+strings): tambah varian tema ke-3 "Skeuomorphism"
  (bevel/fisik, bukan glass) — toggle baru di Settings, 3 opsi eksklusif.
- **Batch 37** (banyak file, REWRITE TOTAL): "iOS Glassmorphism +
  Midnight-Blue dominan" jadi arah desain utama — kartu genuine frosted-
  glass 4-stop+sheen kedua, radius naik 20→26dp, kontras teks dinaikkan.
  Arsitektur 2-varian (Batch 36) dipertahankan, isi ditulis ulang total.
- **Batch 36 Fix** (`Theme.kt`, hotfix): CI gagal kapt — `Dp` type tanpa
  import class-nya (cuma extension `dp` yang di-import). Tambah 1 import.
- **Batch 36** (banyak file, v1.75): fitur switch tema custom — arsitektur
  `SkeuTokens`/`LocalSkeuTokens`/`AppThemeStyle` baru, 2 tema hidup
  berdampingan (AMOLED Glass + Radical Skeuomorphism) dipilih via toggle
  Settings. Semua komponen WAJIB baca `LocalSkeuTokens.current`, jangan
  reference val hardcoded lagi.
- **Batch 35** (v1.74): `TextMuted` (dead code sejak Batch 34) dipakai di
  semua teks caption-tier.
- **Batch 34** (v1.73): KOREKSI Batch 33 — guide referensi salah upload,
  diganti "AMOLED Hybrid Glassmorphism + Subtle Midnight Blue +
  Micro-Skeuomorphism" yang benar. Filosofi geser: glass jadi material
  utama, skeuomorphism turun jadi "micro" (physical controls saja).
- **Batch 33** (v1.72, REFERENSI SALAH — lihat Batch 34): palet AMOLED +
  glass surfaces + Midnight Blue sebagai tint subtle. Kartu jadi
  frosted-glass gradient (dari solid flat Batch 31).
- **Batch 32** (v1.71): glow state-aktif (`Modifier.skeuGlow` baru) +
  switch tactile (`SkeuSwitch` baru) sesuai guide detail yang di-upload
  ulang user.
- **Batch 31** (v1.70): DESIGN LANGUAGE PIVOT TOTAL — "Neumorphic Hybrid"
  (Batch 12-26) DICABUT, ganti "Skeuomorphism-lite (Tactile UI)", WAJIB
  dark-mode (toggle terang/sistem dihapus total).
- **Batch 30** (v1.69): empty state hint preset custom, polish kecil.
- **Batch 28** (v1.67, hotfix): `const val` pakai `Environment.DIRECTORY_DOCUMENTS`
  (bukan compile-time constant) → hapus `const`. Lesson: field API Android
  BUKAN compile-time constant walau terlihat "konstan".
- **Batch 27** (v1.66): rewrite `CrashLogger.kt` ke MediaStore API 29+.
- **Batch 26** (v1.65): body GitHub Release dinamis dari CHANGELOG.md +
  polish kecil (batas karakter nama preset, haptic di 5 titik).
- **Batch 25** (v1.64, hotfix): `LocalIndication` non-null di versi Compose
  project ini → `NoRippleIndication` object custom ganti `provides null`.
- **Batch 24** (v1.63): ripple removal 4 titik — desain benar, tipe Kotlin
  yang salah (fixed Batch 25).
- **Batch 22** (v1.61→v1.62): slider custom (`NeumorphicSliderTrack`/`Thumb`)
  — desain benar, kurang 1 `@OptIn` (fixed run berikutnya).
- **Batch 19-21** (v1.58-v1.60, 3 iterasi CI): root cause Gradle sistem
  runner naik ke 9.6.1 (gak kompatibel KGP 1.9.24) — fix bertahap: pin
  `--gradle-version 8.7`, generate wrapper di direktori terisolasi
  (`mktemp -d`+`settings.gradle.kts` kosong), reorder step "Extract version
  name" ke paling awal+unconditional. Artifact `log_fail_*` otomatis
  ter-upload saat build gagal (fitur baru diminta user).
- **Batch 18** (v1.57): Hilt DI ditambahkan (kemudian DICABUT TOTAL Batch
  49 — 1 titik inject ternyata gratis dari `AndroidViewModel`).
- **Batch 17**: state+business logic koneksi Service diekstrak
  `MainActivity`→`BoosterViewModel` (`AndroidViewModel` polos, tanpa DI).
  Bind/unbind pindah pakai Application Context (hindari context-leak).
- **Batch 1-16**: scaffold awal, fitur inti (BassBoost/Virtualizer/
  Equalizer/LoudnessEnhancer di session 0, foreground service anti-kill,
  QS Tile, App Shortcuts, Widget home screen, CrashLogger, CI/CD signing),
  beberapa putaran audit kecacatan logika (resource orphan, dead code,
  splash screen stale, parity string, validasi tabrakan nama preset).
  Detail lengkap tiap fitur/audit: `CHANGELOG.md` v1.0-v1.45.
