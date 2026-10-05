[BRANDING_NAME: Boomly]
[TERMUX_ROOT: AudioEnhancerPro]

# 🧠 PROJECT_STATE.md — baca PALING PERTAMA

Untuk Claude, bukan manusia. Padat & actionable. Lapisan:
1. 🔒 ATURAN PERMANEN — jarang berubah, WAJIB dibaca duluan.
2. 🧭 Status Terkini — state akhir (bukan histori).
3. 📅 LOG BATCH — riwayat ringkas per-batch, descending, BUKAN permanen.
4. Referensi: pivot desain, batasan sandbox, struktur, TODO/ROADMAP.
5. `[RESUME POINT]` — blok handoff deterministik di AKHIR file.

Urutan sesi baru: 2 tag di baris atas → ATURAN PERMANEN → Status Terkini →
5-10 entry teratas LOG BATCH → `[RESUME POINT]` (akhir file) → mulai kerja.
Jangan ulang pertanyaan yang jawabannya sudah ada di sini.

`[BRANDING_NAME]` = nama ZIP output (`Boomly_v<Batch>.zip`, Batch inkremen
dari state terbaru). `[TERMUX_ROOT]` = nama folder repo/Termux (isi
`[NamaFolderProyek]` di skrip Termux) — BUKAN sama dengan brand.

---

## 🔒 ATURAN PERMANEN & HIERARKI (PIN — hanya berubah via instruksi baru eksplisit user)

Hierarki (SOP terkini): P0 SOP (stabilitas & zero-regression) > P1 User
Intent & ZIP/Source > P2 file ini > P3 Guards > P4 Output. ZIP = sumber
kebenaran tunggal; remote Git cuma mirror/continuity.

Index Core Protocol (detail lengkap di instruksi custom user):
- STABILITY > Speed. STOP → tandai BLOKER kalau info kurang, jangan nebak.
- Eksekusi langsung & hasil SIAP PAKAI (zero-hesitation): jangan minta izin
  atau nunggu instruksi teknis step-by-step.
- ZERO-REFACTOR/Tunnel Vision: file tak relevan ke task DILARANG disentuh.
- Micro-Batch: maks 3 file SOURCE/TARGET per tugas. Dokumen VIP (file ini,
  README.md, CHANGELOG.md) + dok/`docs/archive/` kebal limit, WAJIB sync
  tiap sesi ada perubahan.
- Versioning Lock: versionCode DAN versionName otomatis dari
  `GITHUB_RUN_NUMBER` (sejak Batch 76). DILARANG bump manual.
- Format chat: HANYA 1 ZIP + skrip Termux relevan + summary 1 blok kode
  maks 5 baris. Tanpa sapaan/penjelasan alur — penjelasan detail → dokumen
  proyek.
- Skrip Termux Immutable: isi placeholder `[Nama...]` saja. DILARANG ubah
  logika Bash atau gabung Box A & B.
- **Dokumentasi WAJIB padat**: 1 entry LOG BATCH = maks 3-5 baris (file
  disentuh, apa yang berubah, status validasi). DILARANG tulis ulang
  root-cause/diff/rasional panjang di sini — itu tempatnya di komentar
  inline kode atau `CHANGELOG.md`. Kalau sebuah lesson masih actionable
  untuk sesi depan, taruh 1 baris di "Batasan sandbox" atau "Keputusan
  sadar" di bawah, bukan diulang di tiap entry log.

### Keputusan sadar (JANGAN diubah tanpa alasan baru dari user)
- **Android <12 (API <31) TIDAK diprioritaskan** (user, Batch 147: "idgaf about android <12"). JANGAN buka batch baru untuk guard/
  kompat/device-test API <31 atau warning lint khusus API lama (`UnusedAttribute`, `InlinedApi`, dst). `minSdk` TETAP 24 sampai user
  eksplisit minta naik (mis. ke 31) — opsi itu baru ditawarkan, BELUM diputuskan. Guard yang sudah ada (Batch 143/146) dibiarkan.
- **Brand kosmetik/user-facing = "Boomly"** (Batch 68). ZIP output:
  `Boomly_v<N>.zip`. String user-facing app (ID+EN): `app_name`,
  `app_title`, `notif_title`, `notif_channel_name`, `qs_tile_label`,
  `status_running`, `notif_perm_body`, `ob1_title` = "Boomly". String baru
  yang sebut nama app WAJIB "Boomly".
  - TETAP TIDAK ikut rebrand (vital/fungsional): `applicationId`/`namespace`,
    `rootProject.name`, nama workflow (`build.yml`), nama APK/artifact CI,
    `CrashLogger.APP_FOLDER` (ganti = fragmentasi log lama user). Repo
    GitHub/folder Termux TETAP "AudioEnhancerPro".
  - **HARD LOCK (pelanggaran pernah terjadi di Batch 97)**: `PROJ_DIR`/
    `find ~/projects ... -iname` di SEMUA skrip Termux (Box A, Box B, Daily
    Update) WAJIB tetap `-iname "AudioEnhancerPro"` — BUKAN "Boomly".
    Glob `LATEST_ZIP` BOLEH `Boomly_v*.zip`, tapi `PROJ_DIR` HARUS tetap cari
    folder "AudioEnhancerPro". Cek ulang tiap generate skrip baru.
- **`MODIFY_AUDIO_SETTINGS`**: tak terpakai di kode (grep nihil) tapi TIDAK
  dihapus — sebagian OEM/chipset dilaporkan butuh ini agar efek session-0
  nempel, tak bisa diverifikasi tanpa device fisik.
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`**: dipertahankan meski bukan media
  player asli — user tak berniat publish Play Store.
- **Dynamic color (Material You)**: default OFF, opt-in.
- **Equalizer band individual**: `wrapInCard=false` — sudah di card
  "Equalizer Manual", hindari kaca-di-atas-kaca.
- **Preset custom (v1.33, diperluas Batch 63)**: TIDAK reset equalizer manual
  saat diterapkan (beda dari 9 preset bawaan — 4 lama + 5 Batch 136, lihat
  LOG BATCH). Simpan bass/virtualizer/loudness + `eqBands` opsional
  (Batch 63) — preset lama tanpa `eqBands` TIDAK menyentuh EQ manual.
- **Layout layar utama (Batch 97)**: DEFAULT = vertikal (1 `Column`
  `.verticalScroll()` flat). Mode tab horizontal (`TabRow`+`HorizontalPager`)
  TETAP ADA di kode (`TabPageContent` di `BoosterScreen.kt`) tapi HANYA opsi
  custom opt-in via toggle "Mode Tab Horizontal" di `SettingsScreen.kt`
  (default `false`). JANGAN balikin default ke horizontal tanpa instruksi
  eksplisit baru dari user.
- **Swipe-antar-tab dalam Mode Tab Horizontal (Batch 103-105, DIREVISI Batch 141)**: pager DIHAPUS,
  ganti tap-tab biasa (`selectedTabIndex`, `rememberSaveable`) — 1
  scrollport, 0 clip ganda, TERVALIDASI stabil. Percobaan mengembalikan
  swipe via auto-height pager (Batch 104) TERBUKTI regresi UI parah di
  device fisik (klip & distorsi), sudah direvert total (Batch 105). JANGAN
  coba pendekatan auto-height/`onSizeChanged` dinamis-per-page lagi.
  Batch 141 (request eksplisit user): swipe lintas tab DIKEMBALIKAN TANPA pager —
  `detectHorizontalDragGestures` di Box induk (`BoosterScreen.kt`), kondisional hanya
  Mode Tab Horizontal, ambang `TAB_SWIPE_THRESHOLD_DP`; tap-tab tetap ada, 1 scrollport, 0
  re-layout. Kandidat "pager tinggi tetap" SENGAJA TIDAK dipakai (lebih berisiko). JANGAN balik
  ke `HorizontalPager`/auto-height; kalau swipe bentrok/kurang pas, tuning ambang/detektor saja.

- **Fast Recovery exact alarm (Batch 127 → DIREVISI Batch 128 → DIMATIKAN
  PERMANEN Batch 132, instruksi eksplisit user)**: `SCHEDULE_EXACT_ALARM` SEKARANG punya UI minta
  izin — kartu "Pemulihan
  Cepat" di `SettingsScreen.kt` (status + deep-link
  `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, fallback App Info), opt-in murni, TIDAK ada
  dialog otomatis/onboarding. Desain = HEARTBEAT PROAKTIF (bukan reaktif Batch 127):
  alarm ~1 mnt (Batch 130, turun dari ~5 mnt via 129 — instruksi eksplisit user, "mentok"
  = lantai praktis SENGAJA dipilih krn heartbeat jalan terus-menerus, bukan limit OS)
  dipasang saat service start, dipasang ulang tiap fire/tick sehat, dicabut di ACTION_STOP.
  Floor OS ~9 mnt/app non-exempt tetap berlaku pas Doze dalam (turunin request tak nembus
  floor itu). Root cause pemicu: targetSdk 34 → izin default DITOLAK di Android 14+
  (fast-recovery Batch 127 no-op total) + desain reaktif (pulih ≥ watchdog 15 mnt).
  SENGAJA TIDAK dipakai: `USE_EXACT_ALARM` (auto-grant tapi app hilang dari daftar
  "Alarms & reminders" → kontradiksi UI izin; kebijakan Play cuma alarm/kalender) dan
  `setAlarmClock()` (tanpa izin, tapi memunculkan ikon alarm/"next alarm" palsu di
  system UI). Kandidat kalau user mau zero-friction: `USE_EXACT_ALARM` (keputusan baru).
  **STATUS Batch 132: DIMATIKAN PERMANEN** (`scheduleExactRecovery()` no-op, kartu
  "Pemulihan Cepat" dihapus) — histori di atas = KONTEKS desain, BUKAN behavior aktif.
  Alasan: heartbeat = exact alarm terus-menerus = ongkos baterai nyata, user
  prioritaskan baterai di atas kecepatan pulih tambahan (~1-9mnt vs ~15mnt watchdog).
  `canUseExactAlarm()` dibiarkan (dead code, gampang diaktifkan lagi 1 baris).

- **Watchdog 15mnt + Fast Recovery heartbeat 1mnt (Batch 131, TOLAK instruksi
  hapus TOTAL keduanya)**: user minta hapus demi "0% background activity kustom",
  premis: keduanya cuma buat nutup celah widget basi. TIDAK AKURAT — fungsi
  UTAMA `ServiceWatchdogWorker`/`WatchdogAlarmReceiver` = restart otomatis
  `AudioEnhancerService` kalau dibunuh OS/OEM (hotfix URGENT Batch 124);
  resync widget/tile cuma efek SAMPINGAN siklus yang sama. Hapus TOTAL (watchdog
  IKUT dihapus) = regresi bug Batch 124, TIDAK ADA hubungan dengan target aslinya
  (widget staleness, sudah tertutup via `updatePeriodMillis` 30mnt, Batch 131).
  **JANGAN hapus/kurangi WATCHDOG 15mnt** dengan alasan "widget staleness" — kalau
  user ulang minta, tunjukkan entry ini dulu sebelum eksekusi. **Beda dgn Batch 132**:
  matikan HEARTBEAT SAJA (watchdog TETAP jalan) atas alasan BATERAI itu DIEKSEKUSI,
  bukan ditolak — target & alasan beda, watchdog tidak disentuh sama sekali.

- **Analisis statis NON-BLOCKING (Batch 142, request eksplisit user)**: detekt 1.23.6 mode WHITELIST
  (`buildUponDefaultConfig=false`, 35 rule potensi-bug tanpa type-resolution, `ignoreFailures=true`) +
  Android Lint `abortOnError=false`. DILARANG dijadikan blocking atau menambah severity `fatal`
  (mengubah `lintVitalRelease` di assembleRelease) tanpa instruksi baru user. Jalan di step CI PALING
  AKHIR (setelah release publish) → laporan di artifact `<Repo>_static_analysis_v*` (sejak Batch 162; sebelumnya `static_analysis_v*`) + `STATIC_ANALYSIS_MARKER.txt`. Pre-commit hook
  `./gradlew detekt lintDebug` SENGAJA TIDAK dipasang: repo tak punya `gradlew` (di-bootstrap CI) dan
  commit jalan di Termux tanpa Gradle → hook mematahkan commit; gantinya gerbang CI non-blocking.
  Rule baru HANYA kalau ada bukti temuan nyata; `TooGenericExceptionCaught`/`SwallowedException`
  SENGAJA mati (catch lebar di `AudioEnhancerService` disengaja, efek audio rapuh per-OEM).
  Suppress false positive lint = `@SuppressLint` PER-FUNGSI + komentar alasan (Batch 143), BUKAN ignore global
  di `lint.xml` (biar isu nyata di tempat lain tetap muncul).
- **targetSdk TETAP 34 (audit Batch 158; USER-CONFIRMED Batch 159)**: naik ke 35 merusak autostart boot — `BootReceiver` (BOOT_COMPLETED) memanggil `AudioEnhancerService.requestStart` → FGS `mediaPlayback`, dan Android 15 melarang
  receiver BOOT_COMPLETED meluncurkan FGS tipe itu untuk app target ≥35 (`ForegroundServiceStartNotAllowedException`; `BootReceiver` tanpa try/catch). Edge-to-edge juga jadi default di target 35. Naikkan
  HANYA setelah user memutuskan perilaku boot (mis. notifikasi tap-to-start) — itu perubahan perilaku, bukan housekeeping. Warning lint OldTargetApi dibiarkan sebagai sinyal (jangan di-suppress diam-diam). Batch 159: user memilih eksplisit "Biarkan targetSdk 34 (warning tetap, 0 risiko)" → warning DITERIMA; JANGAN ajukan
  kenaikan targetSdk/suppress lagi kecuali user yang meminta; target lint resmi proyek = 0E/1W (OldTargetApi)/0I.
- **Preset built-in SEMUA punya `eqBands` (Batch 160)**: 9 preset di tabel `presets` `BoosterScreen.kt` seragam menyetel Equalizer manual (5 band, ≤ ±800 mB; Flat = nol eksplisit). Efek samping DISENGAJA
  (permintaan user): menerapkan Bass Heavy/Vocal Boost/Treble Boost tidak lagi me-reset EQ ke flat. Jalur `eqBands` kosong di `applyPreset()` dipertahankan hanya sebagai cadangan — JANGAN dihapus/diubah tanpa diminta.
- **Standar headroom preset built-in (Batch 161; USER-CONFIRMED lewat tab opsi)**: tabel `presets` `BoosterScreen.kt` WAJIB lolos — (H1) tiap band `eqBands` |nilai| ≤ 800 mB (5 band); (H2) `max(0, max(eqBands)) + loudness
  ≤ 1800 mB`. Cek statis tiap batch yang menyentuh tabel itu (skrip hitung dari file). Koreksi bila melanggar: pangkas LOUDNESS sampai tepat di batas (BUKAN EQ/bass/virtualizer — itu karakter preset). Nilai B161 (mB):
  Flat 0, Acoustic 850, Bass Heavy 1450, Vocal Boost 1600, Treble Boost 1650, Gaming 1650, Podcast/Cinema/EDM 1800. KETERBATASAN JUJUR: 1800 = heuristik penjumlahan terburuk, dipilih karena 7 dari 9 preset sudah ≤ itu
  (BUKAN hasil uji dengar/klaim "perfect"); BassBoost (bass 1000 pada Bass Heavy & EDM) TIDAK dihitung → wajib uji dengar; timbre (desis Treble Boost +800 @14kHz, sibilance Vocal Boost +500 @3,6kHz) di luar standar;
  perilaku limiter `LoudnessEnhancer` BELUM diverifikasi. Ubah batas HANYA dari feedback uji dengar user — jangan menebak angka baru.
- **Penanda artifact static analysis (Batch 162)**: TRIASE artifact SELALU mulai dari `STATIC_ANALYSIS_MARKER.txt` — WAJIB `PROJECT=AudioEnhancerPro`, `APPLICATION_ID=com.audioenhancer.booster`,
  `GRADLE_ROOT_PROJECT=AudioEnhancerPro`; kalau beda → STOP, artifact proyek lain (jangan dipakai). `BATCH_PROJECT_STATE` = angka "Batch terakhir" di file ini saat CI jalan (cocokkan dgn ZIP yang di-push; selisih =
  ZIP lama/salah). Artifact SEBELUM Batch 162 (`static_analysis_v*`) tak punya marker → cocokkan lewat loc + path `AudioEnhancerPro` di log. Marker dibuat step non-blocking: kalau tak muncul, itu bug workflow, BUKAN
  alasan menyalahkan kode. Nama artifact memakai `github.event.repository.name` (belum pernah dijalankan CI → verifikasi di run berikutnya).
- **Loop polling UI/ViewModel WAJIB berhenti di background (Batch 162)**: `BoosterViewModel` memakai `uiActive`/`awaitUiActive()` (diisi `MainActivity.onStart/onStop`). SISA sengaja tak diubah: 3 loop 1 Hz di composable
  (`ServiceStatusBadge` & `PowerToggleRow` di `BoosterScreen.kt`, sleep timer di `SettingsScreen.kt`; baca field statis, beban kecil — butuh `repeatOnLifecycle`/`lifecycle-runtime-compose`, belum ada di dependency) dan
  capture Visualizer di `AudioEnhancerService` yang tetap jalan di background (perlu perubahan Service). Kerjakan HANYA bila user minta.
- **I/O crash log WAJIB di `Dispatchers.IO` (Batch 163)**: `CrashLogger.latestCrashLog`/`hasUnseenCrash`/`markCrashSeen`/`deleteAllLogs` & `CrashLogEntry.readText` blocking (MediaStore/File) — JANGAN dipanggil di komposisi/`onClick` (Main). Pola = `LaunchedEffect`/`scope.launch` + `withContext(Dispatchers.IO)`; tulis yang harus tuntas pakai `NonCancellable + Dispatchers.IO`. Contoh: `CrashBanner` di `BoosterScreen.kt`. Baca `SharedPreferences` di komposisi SENGAJA dibiarkan (kecil, ter-cache).

### Cara update file ini
Sesi dengan keputusan arsitektur baru (bukan bugfix kecil): (1) entry baru
di LOG BATCH (paling atas, maks 3-5 baris); (2) update Status Terkini
(state akhir, bukan histori); (3) update Keputusan Sadar kalau relevan;
(4) tulis ulang `[RESUME POINT]` di AKHIR file (wajib tiap sesi).
JANGAN taruh root-cause/diff/rasional panjang di mana pun di file ini.

### Kebijakan dokumentasi (PIN — Batch 106, diperbarui Batch 118)
HANYA 4 dokumen resmi diakui: konstitusi/SOP (di luar repo, instruksi custom
user), `PROJECT_STATE.md` (RAM instan — file ini, PADAT), `README.md`
(wajah proyek), `CHANGELOG.md` (arsip append-only rilis publik, BUKAN acuan
konteks utama). Entry CHANGELOG baru WAJIB paling atas: heading `## ...` +
paragraf pembuka ringkas (heading+paragraf ≤15 baris) SEBELUM baris pertama
berawalan `**` — CI mengambil bagian itu jadi body GitHub Release.
`FILE_MANIFEST.txt` DIKECUALIKAN (manifest teknis, bukan dokumentasi — tetap
di root). Dokumen usang → `docs/archive/` (bukan root; sesuai SOP).
Kebutuhan dokumen backlog terpisah baru: TANYA user dulu, jangan pecah lagi
sepihak.

---

## 🧭 Status Terkini (state akhir — BUKAN histori, detail batch ada di LOG BATCH)

- **Batch terakhir**: 163, `BoosterScreen.kt` (1 file) — guard Thread Safety: `CrashBanner` (L209-289) — query MediaStore, baca isi, tandai-dilihat & hapus crash log — pindah dari Main thread ke `Dispatchers.IO`. Triase artifact `AudioEnhancerPro_static_analysis_v204-run204`: marker OK (PROJECT=AudioEnhancerPro, APPLICATION_ID=com.audioenhancer.booster, BATCH_PROJECT_STATE=162), BUILD SUCCESSFUL 30s (compile UP-TO-DATE lolos), lint 0E/1W (OldTargetApi)/0I, detekt 0/21 file, loc 9.249 = TARGET B162 tepat → B162 **STATIC-VERIFIED** (device belum). B163 **NOT VERIFIED** (statis; nunggu CI + device). TARGET artifact berikut: marker BATCH_PROJECT_STATE=163, lint 0E/1W/0I, detekt 0, loc 9.272 (+23).
  Sebelumnya: 162, `build.yml`+`BoosterViewModel.kt`+`MainActivity.kt` — penanda artifact static analysis + 2 loop polling ViewModel SUSPEND saat UI tak terlihat (`MainActivity.onStart/onStop`).
  Sebelumnya: 161, `BoosterScreen.kt` — standar headroom preset (Cinema 1200/EDM 1050). Sebelumnya: 160, `BoosterScreen.kt`+`README.md` — 9 preset punya `eqBands`. Sebelumnya: 159, docs-only — targetSdk TETAP 34.
  Sebelumnya: 158, `BoosterScreen.kt`+`MainActivity.kt` — `spectrumLevels` di fase gambar (M7) + arsip LOG B1–142 (M8a). Sebelumnya: 157, `OemAutostartHelper.kt` — +1 kandidat Asus (M4).
  Sebelumnya: 154, FITUR statistik pemakaian lokal (Fase 9 M5, 5 file: `PrefsHelper.kt`+`AudioEnhancerService.kt`+`SettingsScreen.kt`+strings ID/EN) — total waktu aktif, jumlah
  dinyalakan, preset terpopuler (100% on-device, tanpa timer/loop). Sebelumnya: 153, `shortcuts.xml`+`widget_booster_info.xml`+`lint.xml` — UnusedAttribute 5 `tools:ignore` (0 perubahan atribut android:*), IconDuplicates 5 di-ignore di `lint.xml`
  (PNG bulat sengaja identik). CI run 196 (artifact diunggah user): BUILD SUCCESSFUL 26s, lint 0E/11W/1I = TARGET B152 tepat, detekt 0/21 file, loc 8.961 = source v152 → B151+B152
  **STATIC-VERIFIED** (device belum). TARGET artifact berikut: lint 0E/1W (OldTargetApi)/1I, detekt 0 (belum terbukti). Sebelumnya: 152, bersih warning lint (4 file: `BoosterViewModel.kt`+`SettingsScreen.kt`+strings ID/EN) — `StaticFieldLeak` 1 + `PluralsCandidate` 6 (export/import
  sukses jadi `plurals`, `preset_save_char_count` `tools:ignore`). Gerbang validasi = HANYA lintDebug+detekt. TARGET artifact berikut: lint 0E/≤11W/1I, detekt 0 (belum terbukti).
  Sebelumnya: 151, FITUR preset per jadwal (Fase 9 M3, 5 file: `PrefsHelper.kt`+`ScheduleWorker.kt`+`SettingsScreen.kt`+strings ID/EN) — event NYALA Scheduler
  menerapkan preset custom pilihan user (tulis prefs → Service start membaca lewat `restoreSavedSettings()`; 0 perubahan Service). **NOT VERIFIED** (statis: brace/paren
  seimbang, parity string 183=183, `R.string` ter-resolve; nunggu CI + device). Sebelumnya: 150, docs-only (0 file source) — M0 Fase 9: triase artifact `static_analysis_v194-run194` (BUILD SUCCESSFUL 17s): lint 0 Error/18 Warning/1 Info,
  detekt 0 temuan/21 file (loc 8.886 = source v149) → B146 + B149 **STATIC-VERIFIED** (device belum). Sebelumnya: 149, `MainActivity.kt` — M1 Fase 9: `showOnboarding`/`showSettings` `remember`→`rememberSaveable` (state layar tahan rotasi; 1 file, +1 import).
  **STATIC-VERIFIED** (CI run 194; test putar layar di device belum). Sebelumnya: 148, docs-only — planning embedded Fase 9 (konstitusi v3.5; 0 kode, status N/A). Sebelumnya: 147, sinkron komentar basi (KDoc `WatchdogAlarmReceiver.kt` + komentar `AndroidManifest.xml`; 0 logic) + catat keputusan
  "Android <12 tidak diprioritaskan". **STATIC-VERIFIED** (kode di luar komentar identik, elemen manifest identik, XML valid). Sebelumnya: 146, guard InlinedApi `MainActivity.kt` `openNotificationSettings` (SDK_INT>=O; else `ACTION_APPLICATION_DETAILS_SETTINGS`).
  **STATIC-VERIFIED** (CI run 194: InlinedApi 0; device Android 7.x belum). Sebelumnya: 145, hapus 7 string `settings_fast_recovery_*` ID+EN + 11
  `mutableStateOf` → `mutableFloat/Int/LongStateOf`. **STATIC-VERIFIED** (CI run 191: lint 0 Error/20 Warning, detekt 0, `compileDebugKotlin`
  lolos). Sebelumnya: 144, docs-only (0 file source) — verifikasi CI B143: artifact `static_analysis_v190-run190`
  lint 0 Error/38 Warning (sebelumnya 7 Error), detekt 0 temuan. **STATIC-VERIFIED** (device Android 7.x belum). Sebelumnya: 143,
  fix lint NewApi nyata (`getForegroundService` tanpa guard API 26) + suppress 6 false positive
  (`AudioEnhancerService.kt`+`ScheduleWorker.kt`). **STATIC-VERIFIED** (CI run 190). Sebelumnya: 142, analisis statis terfokus NON-BLOCKING — detekt 1.23.6 whitelist
  (`config/detekt/detekt.yml`) + Android Lint (`app/lint.xml`, `abortOnError=false`) + step CI terakhir
  `continue-on-error`; kode app 0 perubahan. **NOT VERIFIED** (nunggu CI). Sebelumnya: 141, swipe lintas tab Mode Tab Horizontal — `BoosterScreen.kt` saja,
  detektor `detectHorizontalDragGestures` di Box induk (BUKAN pager), `selectedTabIndex` dihoist
  (tetap `rememberSaveable`). **NOT VERIFIED** (statis; nunggu CI + device). Sebelumnya: 140,
  REVERT PENUH gatekeeper slider (Batch 138+139) —
  device test user: geser di track SELAIN thumb bukannya diblok malah LANGSUNG
  loncat ke value 0 (regresi, arah TERBALIK dari yang dimaksud). Root-cause
  pasti TIDAK dikonfirmasi (no device/compiler access di sandbox ini buat
  reproduksi — lihat "⚠️ Temuan terbuka"), TAPI dugaan kuat: `.pointerInput(
  value, ...)` pakai `value` SEBAGAI KEY → tiap `value` berubah (termasuk saat
  drag legit berjalan) → block RESTART paksa → interaksi aneh dgn
  `awaitFirstDown`+`consume()` lintas-pass yang TIDAK bisa dipastikan tanpa
  device nyata. `SkeuomorphicComponents.kt` dikembalikan PERSIS ke versi
  Batch 137 (sebelum gatekeeper ada sama sekali) — 0 sisa kode gatekeeper.
  Proteksi kurva EQ (Batch 137, `EqCurveEditor.kt`, TIDAK dilaporkan
  bermasalah) TETAP ADA, TIDAK ikut di-revert. **NOT VERIFIED** tapi INI
  REVERT ke state yang SUDAH pernah jalan (v137 identik), jadi risiko regresi
  BARU sangat rendah — cuma perlu CI run buat pastikan compile balik bersih.
  Sebelumnya: 139, HOTFIX compile — CI run 185 gagal, `awaitFirstDown`
  di-import dari paket SALAH (`androidx.compose.ui.input.pointer`, harusnya
  `androidx.compose.foundation.gestures` — fungsi itu extension di paket
  `foundation.gestures`, BUKAN `ui.input.pointer`, meski KDoc referensinya
  nempel di halaman `AwaitPointerEventScope`). `SkeuomorphicComponents.kt`
  baris import saja, 0 logic berubah. **NOT VERIFIED** (statis OK, brace/paren
  51/51; menunggu CI run berikutnya buat konfirmasi FIX). Sebelumnya: 138,
  gatekeeper anti-sentuhan-tak-sengaja di-EXTEND dari kurva (137) ke SEMUA slider — `SkeuomorphicComponents.kt` (`FeatureControl`,
  1 titik dipakai Bass/Virtualizer/Loudness/Compressor + ke-5 slider band EQ).
  **NOT VERIFIED**. Sebelumnya: 137, hit-test radius 2D di `EqCurveEditor.kt` (instruksi
  eksplisit user "preventing touch" — cegah band berubah cuma karena jari
  LEWAT/bergerak di kurva tanpa niat pegang titik). **NOT VERIFIED**.
  Sebelumnya: 136, MERGE cabang paralel — repo GitHub `main` ternyata
  berisi sesi LAIN yang diam-diam menyimpang dari Batch 127 (batch number sama,
  ISI beda; sesi lain stop di Batch 129-nya sendiri, TIDAK tahu Batch 130-135
  di sini pernah ada). Detail penuh + tabel rekonsiliasi → lihat
  "🔀 Rekonsiliasi cabang paralel" di bawah. Ringkas: diporting MASUK 5 preset
  baru (Gaming/Cinema/EDM/Podcast/Acoustic, `eqBands`) dari cabang lain ke
  `BoosterScreen.kt`+strings (0 file lain disentuh, 0 konflik region — preset
  ada di area kode berbeda dari EqCurveEditor/Reset Equalizer Batch 133/135).
  Fast-recovery heartbeat exact-alarm cabang lain (masih aktif di sana) SENGAJA
  DIABAIKAN/tidak di-reimport — Batch 132 di sini sudah mematikannya permanen
  atas instruksi eksplisit user demi baterai; itu keputusan LEBIH BARU, bukan
  regresi. **NOT VERIFIED** (statis: brace/paren BoosterScreen.kt 284/284,
  parity strings ID/EN 189=189, semua `R.string` ter-resolve; nunggu CI +
  device). Sebelumnya: 135, tombol "Reset Equalizer" (semua band → 0 mB) di
  kartu Equalizer Manual — `BoosterScreen.kt` + strings ID/EN. **NOT
  VERIFIED**. Sebelumnya: 134, Scheduler harian nyala/mati (Fase 8 B, ROI #7;
  fade-out Timer Tidur DILEWATI atas pilihan user) — `ScheduleWorker.kt` (baru) +
  `PrefsHelper.kt` + `SettingsScreen.kt` + strings ID/EN. **NOT VERIFIED**.
  Sebelumnya: 133, EQ curve editor drag-point (Fase 8 A, ROI #6) —
  `EqCurveEditor.kt` (baru) + `BoosterScreen.kt` (sisip ke `EqualizerSection`).
  0 perubahan backend. **NOT VERIFIED** device, review manual OK. Sebelumnya:
  132, Fast Recovery heartbeat (1mnt exact alarm) DIMATIKAN PERMANEN demi
  baterai — watchdog 15mnt TIDAK disentuh (`ServiceWatchdogWorker.kt` +
  `SettingsScreen.kt`, lihat "Keputusan sadar" & LOG BATCH 132). Sebelumnya:
  131, widget `updatePeriodMillis` 0→30 menit (Opsi B) —
  watchdog+heartbeat DITOLAK dihapus TOTAL (regresi bug Batch 124), lihat "Keputusan
  sadar" & LOG BATCH 131. Sebelumnya:
  130 tuning heartbeat Fast Recovery 2→1 menit — lantai
  praktis (user konfirmasi 129 <3 mnt di device, minta "mentokin"; lihat LOG
  BATCH 130); **NOT VERIFIED**, tidak ada toolchain lokal, nunggu CI/device
  user. Sebelumnya: 129 tuning 5→2 menit (NOT VERIFIED); 128
  fix Fast Recovery — kartu izin "Alarm & pengingat" di Settings + heartbeat
  exact alarm proaktif (NOT VERIFIED); 127
  fitur fast-recovery exact alarm (NOT VERIFIED, desain reaktif — direvisi 128);
  126 hotfix widget/QS
  Tile nunggu watchdog 15 menit kelamaan, ditambah resync cepat di
  `onStartListening()`+`onResume()` (NOT VERIFIED); 125 hotfix widget vs QS Tile desync setelah
  kill keras (NOT VERIFIED); 124 hotfix watchdog gagal diam-diam restart
  (NOT VERIFIED); 123 hotfix speaker internal nempel preset Kustom (NOT
  VERIFIED); 122 Auto-Profil per Output kode SELESAI (NOT VERIFIED); 121
  Compressor (**USER-CONFIRMED WORKING** di device fisik); logika terakhir
  sebelum itu: B115/B119/B120.
- **Tema**: 5 varian dark-only. Dipilih lewat 4 toggle eksklusif di layar
  utama (`BoosterScreen.kt`: Aurora/Neumorphism/Studio Eq/Serene; semua mati
  = Midnight Glass default). **SEMUA 5 varian USER-CONFIRMED BERHASIL (Batch
  118 — sudah lama jalan). Status "belum tervalidasi"/"menunggu user test"
  versi lama = BASI, JANGAN dimunculkan lagi kecuali user lapor bug baru.**
  (1) Midnight Glass — default, glass restrained, key `amoled_glass`.
  (2) Aurora Glass — glass vivid, key `radical_skeuo`.
  (3) Neumorphism — key `skeuomorphism`; base Deep Navy, shape near-flat
  4/3dp + tipografi tracked-out ala "Blade Runner" (Batch 108), aksen Misty
  Pine Forest (109), dual-shadow ambient pine-tinted (110).
  (4) Studio Equalizer — key `studio_eq`; neumorphism abu studio + neon-lime.
  (5) Serene M3 — key `serene_m3` (Batch 111-113); Material 3 flat-tonal,
  aksen sage+lavender, shape cut-corner organic-asymmetric.
  Key persist = Protected Asset (JANGAN rename). Pivot: "Riwayat pivot".
- **Export/Import preset** (Batch 115-116, Fase 8 D): USER-CONFIRMED WORKING
  (compile+runtime OK, implisit memvalidasi hotfix Batch 113). SELESAI.
- **Sleep timer** (Batch 119, Fase 8 B bagian 1): Pengaturan → "Timer Tidur"
  15/30/45/60/90/120 mnt; habis waktu = jalur `ACTION_STOP` (sama tombol
  Matikan). **NOT VERIFIED**. Sisa: fade-out volume (BLOCKED keputusan user;
  user pilih SKIP di Batch 134). Scheduler harian: lihat Batch 134.
- **Versioning**: `versionCode` DAN `versionName` OTOMATIS dari
  `GITHUB_RUN_NUMBER` (String=Int sama nilai) — DILARANG bump manual.
- **Layar utama**: default vertikal 1-scroll. Mode Tab Horizontal = opsi
  custom opt-in di Settings, tap-tab + swipe lintas tab (Batch 141, detektor gestur, bukan
  pager), 1 scrollport, 0 clip ganda — lihat "Keputusan sadar" di atas untuk histori & batasan.
- **iOS Look Hybrid Rombak** (struktur/pola, independen dari warna/tema):
  grouped-list Kontrol + Settings selesai+tervalidasi, tipografi Large
  Title selesai+tervalidasi, styling pill Preset Cepat selesai (belum
  tervalidasi visual). Sisa: nav bar large-title-collapsing, audit
  `OnboardingScreen.kt`, SF Symbols-style icon — lihat TODO Fase 7.
- **Validasi runtime (default)**: perubahan baru = NOT VERIFIED sampai user
  konfirmasi eksplisit (sandbox tanpa compiler/emulator, lihat "Batasan
  sandbox"). Sisa validation debt NON-tema (pasif, non-blocking): Fase 1.

---

## 🔀 Rekonsiliasi cabang paralel (Batch 136 — baca kalau ada divergensi lagi)
**Kejadian**: user upload `AudioEnhancerPro-main.zip` (pull GitHub `main`) yang
ISINYA BUKAN Batch 135 sesi ini — melainkan sesi Claude LAIN yang, tanpa
diketahui, sempat jalan paralel di project yang SAMA dan ikut increment nomor
Batch dari titik cabang yang sama, menghasilkan nomor batch KEMBAR dengan isi
BEDA. Root-cause pasti TIDAK diketahui (di luar visibilitas sesi ini — SOP
tidak pakai git branch, cuma ZIP linear; kemungkinan: 2 sesi device/akun
beda jalan bersamaan, atau `DAILY UPDATE` sempat pakai ZIP basi dari sesi
lain yang nyangkut di folder Download). **Titik cabang persis**: kedua
riwayat 100% identik kata-per-kata turun sampai **Batch 127** (exact-alarm
fast-recovery, `WatchdogAlarmReceiver.kt`) — dibuktikan `diff` PROJECT_STATE.md
kedua sisi byte-identical utk Batch 110-127. Batch 128 di kedua sisi
MENYIMPANG total meski nomor sama:
| Batch | Sesi INI (sebelum merge) | Sesi LAIN (GitHub main) |
|---|---|---|
| 128 | Kartu "Pemulihan Cepat" + heartbeat proaktif | 5 preset baru (Gaming/Cinema/EDM/Podcast/Acoustic) |
| 129 | `FAST_RECOVERY_INTERVAL_MS` 5→2 menit | Hotfix compile `applyPreset()` (`List<Short>`) |
| 130-135 | Tuning heartbeat, **matikan permanen (132)**, EqCurveEditor (133), Scheduler (134), Reset Equalizer (135) | (berhenti di 129 — tidak tahu 130-135 pernah ada) |

**Cara rekonsiliasi (Batch 136, dieksekusi otomatis, 0 konfirmasi manual per
SOP zero-hesitation)**: `diff` penuh tiap file source (bukan cuma baca
ringkasan LOG BATCH) → 2 kategori:
1. **File yang HANYA disentuh 1 sisi** (`PrefsHelper.kt`, `SettingsScreen.kt`,
   `AudioEnhancerService.kt`, `BoosterWidgetProvider.kt`,
   `widget_booster_info.xml` — sisi sesi ini SELALU superset verified 0 baris
   sisi lain hilang; `ServiceWatchdogWorker.kt` — lihat poin 2): pakai APA
   ADANYA dari sesi ini, 0 porting perlu.
2. **`ServiceWatchdogWorker.kt` — KEPUTUSAN SADAR, BUKAN kelalaian**: sisi
   lain MASIH punya heartbeat exact-alarm ±1 menit aktif (state Batch
   127-130). Sisi ini SUDAH mematikannya permanen di Batch 132 atas instruksi
   EKSPLISIT user ("alasan baterai" — lihat LOG BATCH 132). Versi sisi ini
   DIPERTAHANKAN (bukan revert ke versi sisi lain) — itu keputusan yang lebih
   BARU secara kronologis di rantai user INI dan sisi lain tidak pernah
   menerima instruksi itu. Kalau user mau nyalakan lagi, itu keputusan baru,
   bukan "kembalikan yang hilang".
3. **`BoosterScreen.kt` + `strings.xml` ID/EN — SATU-SATUNYA konflik region
   nyata**: `diff` isolasi 3 hunk unik sisi lain (`data class Preset.eqBands`,
   5 `Preset()` baru, logika `applyPreset()` yg sudah dibetulkan tipe
   `List<Short>`-nya oleh hotfix Batch 129 sisi lain) — 3 hunk ini di region
   kode BERBEDA dari hunk EqCurveEditor/Reset Equalizer (Batch 133/135) sisi
   ini, 0 overlap baris. Di-porting MASUK utuh (komentar diberi label
   "Batch 136 (merge...)" biar jelas asal-usulnya, isi logic 1:1 sama
   persis dgn sisi lain termasuk fix tipe Short-nya). Hasil: 9 preset total
   (4 lama + 5 baru), EqCurveEditor + tombol Reset Equalizer TETAP ada.
   Verifikasi post-merge: `diff` kedua sisi lagi → 0 baris sisi lain yang
   hilang dari sisi ini (selain wording komentar "Batch 128"→"Batch 136").
4. **Dok** (`PROJECT_STATE.md`/`CHANGELOG.md`/`README.md`): NOMOR BATCH TIDAK
   di-renumber ulang (128/129 tetap merujuk isi sisi INI di LOG BATCH existing
   — mengubahnya akan merusak histori yang sudah tertulis). Kerja sisi lain
   dicatat sebagai entri BARU "Batch 136" yang eksplisit bilang "merge, aslinya
   Batch 128 di cabang lain" — supaya jujur & telusur, bukan pura-pura sudah
   direncanakan dari awal (Objective Honesty).

**Pencegahan ke depan**: kalau ZIP yang di-upload user ternyata beda dari
`[RESUME POINT]` sesi terakhir (bukan pull biasa), WAJIB curigai kemungkinan
cabang paralel SEBELUM asumsi "ZIP ini pasti lanjutan linear" — `diff`
struktur file + baca `Batch terakhir` di ZIP baru dulu, baru putuskan alur
(lanjut normal vs rekonsiliasi seperti di atas).

## ⚠️ Temuan terbuka
- (Batch 140) Root-cause PASTI kenapa gatekeeper slider Batch 138 malah bikin
  value loncat ke 0 (bukan sekadar diblok) TIDAK dikonfirmasi — sandbox ini
  0 akses device/compiler Kotlin buat reproduksi nyata. Dugaan (BUKAN
  kepastian): `.pointerInput(value, valueRange, enabled)` pakai `value`
  sebagai key → restart paksa tiap value berubah (termasuk saat drag legit
  jalan) → kemungkinan race dgn `consume()` lintas-pass. Kalau proteksi
  slider mau dicoba lagi ke depan: WAJIB device-in-the-loop (bukan tebak dari
  sandbox), pertimbangkan key TANPA `value` (misal cuma `Unit`, baca `value`
  terkini via `rememberUpdatedState` di dalam block) buat hindari restart
  paksa itu SEBAGAI hipotesis pertama yang dicoba.
- (Batch 128) DITUTUP: 2 temuan lama Batch 118 (nama secret Box B vs `build.yml`;
  guard `-lt$((` Daily Update) — SOP terkini SUDAH sinkron (Box B pakai
  `KEYSTORE_*`/`KEY_*` tanpa prefix = sama `build.yml`; guard pakai spasi+kutip).
- (Batch 147) DITUTUP: doc-debt Batch 128 — KDoc `WatchdogAlarmReceiver.kt` + komentar `AndroidManifest.xml` sudah disinkron
  (receiver DORMAN sejak Batch 132, izin `SCHEDULE_EXACT_ALARM` tak dipakai & tanpa UI; komentar saja, 0 logic).

## 📅 LOG BATCH (descending, terbaru paling atas — BUKAN bagian permanen)
Format: **Batch N** (file disentuh) — apa yang berubah. Status validasi.
Root-cause/diff/rasional detail → `CHANGELOG.md`, BUKAN di sini.
Entri Batch 1–142 sudah DIPINDAH verbatim ke `docs/archive/PROJECT_STATE_LOG_B1-B142.md` (Batch 158, M8); rujukan "LOG BATCH N" untuk N ≤ 142 di file ini → baca arsip itu.

- **Batch 163** (`BoosterScreen.kt` — 1 file; request user "lanjutkan pengembangan yang tervalidasi ketat via lintdebug/detekt only"): triase `AudioEnhancerPro_static_analysis_v204-run204` (marker OK, compile UP-TO-DATE lolos, lint 0E/1W/0I, detekt 0/21 file, loc 9.249) → B162 STATIC-VERIFIED.
  Audit guard Thread Safety (sisa "BELUM DIAUDIT"): temuan nyata = `CrashBanner` melakukan 4 akses blocking di Main (`hasUnseenCrash`+`latestCrashLog` di `remember`, `readText` di komposisi dialog, `markCrashSeen` & `deleteAllLogs` di `onClick`) → semua ke `Dispatchers.IO` (`LaunchedEffect`/`scope.launch`; tulis pakai `NonCancellable`). Efek samping: banner muncul sepersekian detik setelah komposisi pertama.
  Sisa audit OK, tak diubah: `UpdateManager`/backup `SettingsScreen` sudah IO, `WatchdogAlarmReceiver` `goAsync`+Default, loop `BoosterViewModel` hanya baca field memori. Validasi statis (skrip): brace 309/309 paren 1094/1094, `!!` 0, diff = 4 import + `CrashBanner`, loc 9.272. Status: **NOT VERIFIED**.
- **Batch 162** (`build.yml`+`BoosterViewModel.kt`+`MainActivity.kt`; request user "skip, mending tambahkan penanda pada static analysis biar gak ketukar dengan project lain. lanjutkan pengembangan ... lintdebug/detekt only"):
  triase `static_analysis_v203-run203` (compile UP-TO-DATE lolos, lint 0E/1W/0I, detekt 0/21 file, loc 9.218) → B160+B161 STATIC-VERIFIED. (1) Penanda: step baru "Write static analysis marker" (non-blocking, setelah
  analisis, nilai DINAMIS dari repo/gradle/strings/PROJECT_STATE) + nama artifact berawalan nama repo + marker masuk `path`; YAML valid & skrip disimulasikan lokal. (2) Audit guard konstitusi: 0 GlobalScope/runBlocking/
  commit()/secret; temuan nyata = 2 `while (true)` di `BoosterViewModel` jalan terus di background → `uiActive: MutableStateFlow` (deklarasi SEBELUM `init`) + `awaitUiActive()` di awal tiap iterasi; `MainActivity`
  `onStart`→true/`onStop`→false. Validasi statis (skrip): brace/paren seimbang, `!!` 0, urutan init benar, loc 9.249. Status: **NOT VERIFIED**.
- **Batch 161** (`BoosterScreen.kt`; request user: pilih "Tetapkan batas headroom tertulis, koreksi preset yang melewatinya" setelah menanyakan apakah preset sudah "standar perfect"): jawaban jujur = BELUM terbukti (EQ
  subjektif; tak ada uji dengar). Dibuat standar tertulis H1 (|band| ≤ 800 mB) + H2 (puncak EQ ≥0 + loudness ≤ 1800 mB) di "Keputusan sadar". Audit 9 preset: hanya Cinema (2200) & EDM (2750) melewati H2 → loudness
  dipangkas tepat ke batas (1600→1200, 2000→1050); 7 lainnya sudah lolos. Validasi statis (skrip): brace 295/295 paren 813/813, `!!` 0, 9/9 lolos H1+H2, valueRange loudness UI 3000 (nilai tetap valid), loc 9.218. Status: **NOT VERIFIED**.
- **Batch 160** (`BoosterScreen.kt` + `README.md`; request user "lengkapi preset utama dengan konfigurasi preset equalizer manual yang belum merata"): audit tabel `presets` (satu-satunya definisi built-in; Schedule/
  Settings/Service hanya memakai preset CUSTOM) → 5 preset use-case sudah punya `eqBands`, 4 preset lama KOSONG (reset flat). Ditambah `eqBands` urutan [60/230/910/3600/14000 Hz], semua |nilai| ≤ 800 mB: Flat
  [0,0,0,0,0] (eksplisit, hasil sama), Bass Heavy [700,450,0,-100,0], Vocal Boost [-150,0,400,500,150], Treble Boost [-200,-100,150,450,800]. Komentar basi ("4 preset lama TIDAK diubah") + README disinkronkan.
  `applyPreset()` TIDAK diubah (jalur eqBands kosong tetap sebagai cadangan). Validasi statis (skrip): brace 295/295 paren 813/813, `!!` 0, 9/9 preset punya 5 band & |nilai| ≤ 800, loc 9.214. Status: **NOT VERIFIED**.
- **Batch 159** (docs-only: `PROJECT_STATE.md`+`CHANGELOG.md`; 0 file source; request user "sajikan opsinya" lalu pilih di tab opsi): triase `static_analysis_v202-run202` (compile UP-TO-DATE lolos, lint 0E/1W/0I,
  detekt 0/21 file, loc 9.197) → B158 STATIC-VERIFIED. Keputusan user: "Biarkan targetSdk 34 (warning tetap, 0 risiko)" → dicatat USER-CONFIRMED di "Keputusan sadar" + M9; tak ada item kode tersisa di roadmap
  tanpa input user (M2 DITURUNKAN, M6 BLOCKED, M8b hanya bila diminta). Validasi: N/A (0 kode). Status: kode = B158 STATIC-VERIFIED; device belum.
- **Batch 158** (`BoosterScreen.kt`+`MainActivity.kt` + `FILE_MANIFEST.txt` + arsip LOG; request user "1+2+3" lewat tab opsi): triase `static_analysis_v201-run201` (compile UP-TO-DATE lolos,
  lint 0E/1W/0I, detekt 0/21 file, loc 9.189) → B157 STATIC-VERIFIED. (1) targetSdk 34→35 **TIDAK dikerjakan — blokir nyata**: `BootReceiver` (BOOT_COMPLETED) → `requestStart` → FGS `mediaPlayback`; Android 15
  melarang itu untuk app target ≥35 (autostart boot rusak, `BootReceiver` tanpa try/catch) → lihat "Keputusan sadar". (2) M7: `spectrumLevels` (state ~20 Hz, loop 50 ms `BoosterViewModel`) dibaca di komposisi
  `MainActivity` → merekomposisi SELURUH `BoosterScreen` tiap 50 ms; fix = provider `() -> FloatArray` dibaca di lambda `Canvas` `SpectrumBars` (fase gambar). Sisa audit: 0 `LazyColumn`, tanpa operasi koleksi
  berat di komposisi → tak diubah. (3) M8: LOG Batch 1–142 (±39 KB) → `docs/archive/PROJECT_STATE_LOG_B1-B142.md` verbatim. Validasi statis (skrip): brace/paren seimbang, `!!` 0, loc 9.197. Status: **NOT VERIFIED**.
- **Batch 157** (`OemAutostartHelper.kt` — 1 file; request user "next"): triase `static_analysis_v200-run200` (compile UP-TO-DATE lolos, lint 0E/1W/0I, detekt 0/21 file, loc 9.187) → B156 STATIC-VERIFIED.
  M4 diaudit statis vs daftar publik AutoStarter (salinan gist 2020 + issue #84): Xiaomi/Oppo/Vivo/Huawei-ProtectActivity SUDAH identik; `SecurityException` Huawei `StartupNormalAppListActivity` (butuh
  `com.huawei.permission.external_app_settings.USE_COMPONENT`) sudah aman karena `catch (_: Exception)` lanjut ke kandidat berikut → fallback App Info. Perubahan HANYA: Asus +kandidat kedua
  `com.asus.mobilemanager.powersaver.PowerSaverSettings`. Nokia/Letv dari daftar itu SENGAJA tidak ditambah (tak ada bukti user butuh; label "Autostart" berlaku utk semua merk berkandidat).
  Validasi statis (skrip): diff = 1 kandidat + 1 komentar, brace 13/13, paren 64/64, `!!` 0, loc 9.189. Status: **NOT VERIFIED**.
- **Batch 156** (`MainActivity.kt` — 1 file; request user "next"): triase `static_analysis_v199-run199` (compile UP-TO-DATE lolos, lint 0E/1W/0I, detekt 0/21 file, loc 9.184) → B155 STATIC-VERIFIED.
  Bug nyata (baca statis, belum device): manifest tanpa `configChanges`, tak ada `removeExtra`/`replaceExtras` → recreate memutar ulang Intent peluncur: shortcut toggle membalik status lagi, shortcut preset
  custom meng-apply ulang preset (menimpa slider manual). Fix: `onCreate` → `if (savedInstanceState == null) handleShortcutIntent(intent)`; `onNewIntent` TIDAK disentuh (shortcut saat app terbuka tetap jalan).
  Validasi statis (skrip): diff = 1 baris call + 3 baris komentar, brace 63/63, paren 106/106, `!!` 0, 3 titik panggil `handleShortcutIntent` benar, loc 9.187. Status: **NOT VERIFIED**.
- **Batch 155** (`ShortcutHelper.kt`+`MainActivity.kt` — 2 file; request user "lanjutkan progress, validasi ketat via lintdebug/detekt only"): triase `static_analysis_v198-run198` (compile UP-TO-DATE
  lolos, lint 0E/1W/1I, detekt 0/21 file, loc 9.169 = v154) → B154 STATIC-VERIFIED. `ShortcutHelper`: +`ID_TOGGLE` (= `shortcutId` di `shortcuts.xml`), +`customPresetShortcutId()` (builder dinamis
  dipakai ulang, nilai ID identik), +`reportUsed()` → `ShortcutManagerCompat.reportShortcutUsed`; `MainActivity.handleShortcutIntent` memanggilnya setelah toggle & saat preset custom dititip.
  Validasi statis (skrip): diff = 2 file/+15 baris, brace/paren seimbang, `!!` 0, import terlarang detekt 0, ID xml==konstanta, 1 call `reportShortcutUsed`. Status: **NOT VERIFIED**.
- **Batch 154** (`PrefsHelper.kt`+`AudioEnhancerService.kt`+`SettingsScreen.kt`+`strings.xml` ID/EN — 5 file; request user "next"): triase `static_analysis_v197-run197` (compile UP-TO-DATE lolos,
  lint 0E/1W/1I, detekt 0, loc 8.961) → B153 STATIC-VERIFIED. M5 analytics lokal: Service `beginUsageSession()` hanya saat mati→hidup (`wasRunning`), `endUsageSession()` di ACTION_STOP + onDestroy
  (idempoten; patokan `SystemClock.elapsedRealtime()` di companion, `currentSessionMs()`/`restartSessionClock()`); `PrefsHelper` `getUsageTotalMs/StartCount`, `addUsageStart/Duration`,
  `getTopPreset`, `resetUsageStats`, hitung preset di `setActivePreset()` HANYA saat label berganti (JSON ≤50 entri); `SettingsScreen` kartu "Statistik Pemakaian" (snapshot saat dibuka + tombol reset),
  format via fungsi non-composable (plurals). Batas jujur: sesi yang mati paksa tanpa onDestroy tidak terhitung; simpan preset baru ikut terhitung 1x; label preset bawaan per bahasa terpisah.
  Validasi statis (skrip): XML/plurals valid, parity 189 string+5 plurals ID=EN, `R.*` ter-resolve & semua resource baru terpakai, brace/paren seimbang, `!!` 0, urutan onStartCommand benar.
  Status: **NOT VERIFIED**.
- **Batch 153** (`shortcuts.xml`+`widget_booster_info.xml`+`lint.xml` — 3 file; request user "next"): triase `static_analysis_v196-run196` (compile UP-TO-DATE lolos, lint 0E/11W/1I =
  OldTargetApi 1+UnusedAttribute 5+IconDuplicates 5+ReportShortcutUsage info; detekt 0; loc 8.961 = v152) → B151+B152 STATIC-VERIFIED. Kode: `tools:ignore="UnusedAttribute"` di `<shortcut>`
  & `<appwidget-provider>` (+`xmlns:tools`), `lint.xml` `IconDuplicates` ignore + alasan. Validasi statis skrip: XML valid, atribut android:* identik, id lint.xml unik. Status: **NOT VERIFIED**.
- **Batch 152** (`BoosterViewModel.kt`+`SettingsScreen.kt`+`strings.xml` ID/EN — 4 file; request user "validasi ketat modal lintdebug/detekt only"): `service` diberi
  `@SuppressLint("StaticFieldLeak")` + dilepas (`= null`) di `onServiceDisconnected()` & `onCleared()` (semua akses sudah digerbang `bound`); `settings_export/import_preset_success`
  string→`plurals` (one/other; ID & EN) dipanggil `context.resources.getQuantityString` di callback launcher; `preset_save_char_count` `tools:ignore="PluralsCandidate"` (+`xmlns:tools`).
  Validasi statis (skrip): XML valid, parity 181 string+2 plurals ID=EN, `R.string`/`R.plurals`/`@string` ter-resolve, brace/paren seimbang, `!!` 0, import terlarang detekt 0.
  Status: **NOT VERIFIED** sampai artifact CI (compile + lintDebug + detekt).
- **Batch 151** (`PrefsHelper.kt`+`ScheduleWorker.kt`+`SettingsScreen.kt`+`strings.xml` ID/EN — 5 file; request user "skip kalau docs-only. kerjakan yang nyata"):
  preset per jadwal (Fase 8 B sisa / M3). `PrefsHelper`: `get/setSchedulePreset`, `applyCustomPresetToPrefs()`; `ScheduleWorker.performStart()` tulis preset ke prefs
  SEBELUM `requestStart()` (juga sebelum fallback notifikasi); `SettingsScreen` chip preset di kartu Jadwal (reuse `AutoProfileRouteRow`, tampil kalau ada preset custom).
  Service sudah hidup pada jam nyala = event dilewati (preset tak menimpa efek jalan). Status: **NOT VERIFIED**.
- **Batch 150** (docs-only: `PROJECT_STATE.md`+`CHANGELOG.md`; 0 file source; request user "next" + upload artifact CI): M0 — triase
  `static_analysis_v194-run194` (BUILD SUCCESSFUL 17s, `compileDebugKotlin` UP-TO-DATE = lolos): lint 0 Error/18 Warning/1 Info (20→18, InlinedApi 0), detekt 0 temuan/21 file;
  loc detekt 8.886 = source v149 (v147/148 = 8.883). B146 + B149 STATIC-VERIFIED; sisa 18 warning TIDAK disentuh (daftar di RESUME POINT).
- **Batch 149** (`MainActivity.kt` — 1 file source; request user "next" = M1 RESUME POINT): `showOnboarding` & `showSettings` (blok `setContent`)
  `remember`→`rememberSaveable` + import `saveable.rememberSaveable`; `BackHandler` B74, pola tri-state, `useDynamicColor`/`appThemeStyleKey` (dibaca ulang
  dari `PrefsHelper`) TIDAK disentuh. Verifikasi statis: diff = 1 import + 2 deklarasi + 2 baris komentar, brace 63/63, paren 152/152. Status: **NOT VERIFIED**.
- **Batch 148** (`PROJECT_STATE.md`+`CHANGELOG.md` — docs-only, 0 file source; request user "buatkan planning embedded baru berdasarkan
  konstitusi yang berlaku"): tambah Fase 9 di TODO/ROADMAP — selisih teks↔konstitusi v3.5, audit guard statis ZIP v147 (1 dugaan GAP nyata: state
  rotasi `MainActivity.kt` L164/L171), milestone M0-M8 + track T1/T2 + daftar DI LUAR PLANNING. Status: N/A (planning; B146 tetap NOT VERIFIED).
- **Batch 147** (`WatchdogAlarmReceiver.kt`+`AndroidManifest.xml` — 2 file, KOMENTAR SAJA; request user "idgaf about android <12. lanjutkan
  progress yang ada"): lunasi doc-debt Batch 128 — KDoc receiver kini menyatakan jalur DORMAN (Batch 132: `scheduleExactRecovery()` no-op;
  class dipertahankan buat alarm sisa + `cancelExactRecovery()`); komentar manifest izin `SCHEDULE_EXACT_ALARM` & receiver disinkron.
  Keputusan baru dicatat di "Keputusan sadar" (Android <12 deprioritas, minSdk tetap 24). Verifikasi: kode di luar komentar identik,
  elemen manifest identik, XML valid. CI B146 belum ada artifact-nya (belum diupload).
- **Batch 146** (`MainActivity.kt` — 1 file; request user "next" = langkah (b) RESUME POINT): `openNotificationSettings()` — SDK_INT>=O
  tetap `ACTION_APP_NOTIFICATION_SETTINGS`+`EXTRA_APP_PACKAGE`; API 24-25 fallback `ACTION_APPLICATION_DETAILS_SETTINGS` + `package:` Uri
  (target lint InlinedApi 2x). Triase artifact `static_analysis_v191-run191` (CI B145): lint 0 Error/20 Warning (38→20; UnusedResources &
  AutoboxingStateCreation 0), detekt 0/21 file. NOT VERIFIED (statis: brace seimbang; nunggu CI, device Android 7.x belum).
- **Batch 145** (`strings.xml` ID+EN + `BoosterScreen.kt` + `BoosterViewModel.kt` + `SettingsScreen.kt` — 5 file; request user
  "kerjakan no. 1 sampai 3", no. 3 DITUNDA krn batas 5 file): dari artifact `static_analysis_v190-run190` (lint 0 Error/38 Warning).
  (1) hapus 7 string `settings_fast_recovery_*` ID+EN (0 referensi kode). (2) 11 `mutableStateOf` → `mutableFloatStateOf`(4)/
  `IntStateOf`(5)/`LongStateOf`(2); `selectedTabIndex` tetap `rememberSaveable`. Komentar basi `SettingsScreen.kt` disinkron.
  STATIC-VERIFIED (CI run 191: lint 0 Error, warning 38→20, detekt 0, `compileDebugKotlin` lolos; device belum).
- **Batch 144** (docs-only: `PROJECT_STATE.md`+`CHANGELOG.md`; 0 file source; request user "lanjutkan progress"):
  Triase artifact `static_analysis_v190-run190` (CI B143, BUILD SUCCESSFUL 26s): lint 0 Error/38 Warning/1 Info,
  detekt 0 temuan/21 file. 7 Error run 189 → 0 (fix `getForegroundService` + 6 suppress terbukti efektif). Sisa
  warning TIDAK disentuh (daftar di RESUME POINT). STATIC-VERIFIED; perilaku runtime Android 7.x belum diuji device.
- **Batch 143** (`AudioEnhancerService.kt`+`ScheduleWorker.kt` — 2 file; triase artifact CI `static_analysis_v189`):
  CI B142 TERKONFIRMASI jalan (BUILD SUCCESSFUL 32s; detekt 0 temuan/21 file, config valid; lint 46 isu: 7 Error,
  38 Warning, 1 Info). Fix 1 BUG NYATA: `postRecoveryNotification()` (`AudioEnhancerService.kt`) panggil
  `PendingIntent.getForegroundService` (API 26) tanpa guard, minSdk 24 → guard SDK_INT>=O, else `getService`
  (pola `ScheduleWorker`). 6 Error lain = false positive → `@SuppressLint` per-fungsi (NewApi: `setCompressorAmount`,
  `setEqualizerBand`; MissingPermission: `postRecoveryNotification`, `postStartNotification`). STATIC-VERIFIED
  (CI run 190: lint 0 Error, detekt 0; device belum).
---

## 🎨 Riwayat pivot arah desain (biar gak nyoba ulang hal yang sama)
1. **Apple-style minimalis** (v1.11-v1.23) — gagal, user gak ngerasa beda.
   Sebab: (a) HP user kemungkinan paksa Teks Tebal di Aksesibilitas,
   override semua font; (b) tint alpha tipis di atas dark background hitam
   pekat = nyaris invisible — LESSON: pakai solid color blend (`lerp()`),
   jangan alpha mentah di atas dark background; (c) app pakai EMOJI
   sebagai icon UI — iOS asli gak pernah begitu, ini akar utama kesan
   "gak pernah berubah".
2. **Neo-brutalist** — border tebal, sudut tajam, warna vivid. User:
   masih kurang "premium".
3. **Glassmorphism ultra premium, palet violet** (v1.29-v1.48) — struktur
   disukai, palet violet dianggap "neon ungu alay".
4. **Matte premium, palet graphite/bronze** (v1.49) — struktur #3
   dipertahankan, cuma palet violet→champagne-bronze. LESSON: komplain
   "alay/norak" bisa jadi cuma soal palet, bukan struktur — cek dulu
   sebelum redesign besar.
5. **Neumorphic Hybrid** (v1.51-v1.69, DICABUT Batch 31) — struktur ikut
   diganti, dual-shadow extruded/inset, translucency & gradient-clip text
   dibuang total.
6. **Skeuomorphism-lite (Tactile UI)** (v1.70, DICABUT Batch 37) —
   neumorphism dicabut, WAJIB dark-mode, tactile HANYA di physical
   controls (power button/slider knob), kartu flat minimal.
7. **iOS Glassmorphism + Midnight-Blue dominan** (Batch 37, v1.76.0,
   ARAH DASAR SEKARANG) — kartu genuine frosted-glass (4-stop+sheen
   kedua), radius besar ala iOS, background gradient Midnight-Blue→hitam,
   kontras teks readability-first. **5 varian tema** hidup berdampingan
   (arsitektur `SkeuTokens`/`AppThemeStyle` sejak Batch 36; semua
   USER-CONFIRMED berhasil, Batch 118): (1) Midnight Glass (default,
   restrained), (2) Aurora Glass (vivid), (3) Neumorphism (Batch 46, DIROMBAK
   berkali-kali: base Deep Navy Batch 52, kondisi sekarang lihat poin 9-11;
   key persist TETAP `SKEUOMORPHISM`), (4) Studio Equalizer (Batch 43,
   palet studio + neon-lime, low-contrast by design), (5) Serene M3 (Batch
   111, poin 12). Warna aksen per-fitur (Bass/Virtualizer/Loudness/
   Equalizer) TETAP independen dari kelima varian ini.
8. **iOS Look Hybrid Rombak** (Batch 88+, lapisan struktur/pola DI ATAS
   poin 7, TIDAK ganti warna/material) — grouped-list, tipografi Large
   Title, dst. Lihat TODO Fase 7 untuk progress.
9. **Neumorphism → "Blade Runner"** (Batch 108, HANYA varian ke-3) — shape
   near-flat/angular (radius 22/15dp→4/3dp), typography PERTAMA KALI
   per-varian (`NeumorphismTypography`, tracked-out/heavier, 0 font baru).
   Aksen awal Aurora (`NeumoAurora`) — DIGANTI poin 10. Base Deep Navy
   (Batch 52) & `SkeuomorphicComponents.kt` TIDAK disentuh. Detail:
   `CHANGELOG.md` Batch 108, komentar blok `Theme.kt`.
10. **Neumorphism aksen → "Misty Pine Forest"** (Batch 109, HANYA aksen) —
   `NeumoMistyPine` (`0xFF80A891`)/`NeumoMistyPineDeep` ganti `NeumoAurora*`
   (dihapus, 0 referensi eksternal); hijau pinus desaturasi, brightness
   setara Aurora lama (kontras `onPrimary` aman tanpa re-tune). Detail:
   `CHANGELOG.md` Batch 109.
11. **Neumorphism dual-shadow ambient → pine-tinted** (Batch 110, fix
   komplain user) — `NeumoEdgeHighlight`/`NeumoEdgeShadow` (tint
   `SkeuDualDirectionalShadow`, dulu navy `0x4A6690`) diturunkan dari
   `NeumoMistyPine` supaya shadow berulang tiap kartu ikut kebaca pine. Base
   Deep Navy & alpha depth Batch 56 TIDAK disentuh. Detail: `CHANGELOG.md`
   Batch 110.
12. **Varian ke-5 "Serene M3"** (Batch 111-113, TAMBAHAN sejajar poin 7, 4
   varian lain TIDAK berubah) — genuine Material 3 flat-tonal (bukan
   glass/skeuo/neumorphism), `SereneTypography` (tracking positif halus,
   weight ringan) & `SereneShapes` (cut-corner organic-asymmetric) zero
   baseline dishare, aksen "calm" sage+lavender desaturasi. Batch 112:
   `SkeuCard` baca `tokens.cardShape` supaya cut-corner benar-benar kepakai.
   Detail: `CHANGELOG.md` Batch 111-113.
   (Poin 9-12 USER-CONFIRMED berhasil, Batch 118.)

**Preview visual live**: `docs/preview/current.html` — mockup HANYA varian
Midnight Glass (4 varian lain tanpa mockup). WAJIB disinkron bareng tiap
perubahan Kotlin yang visual-related pada varian itu, SEBELUM kirim APK
(validasi arah desain jauh lebih murah lewat browser daripada build
penuh). Kalau ada guide desain baru yang KELIHATAN mirip tapi beda detail
dari yang dipakai batch terakhir, JANGAN asumsikan itu iterasi tambahan —
cek dulu apakah ini koreksi/ganti total (pernah kejadian 2x, Batch 33→34).

---

## 🚧 Batasan sandbox Claude (lesson permanen, biar gak ulang insiden sama)
- **TIDAK ADA** kotlinc/gradle/Android SDK di sandbox manapun (network
  disabled). Claude TIDAK BISA compile-check Kotlin — verifikasi cuma
  manual: baca ulang nama class/icon, cek balance brace/paren via python.
- detekt & Android Lint JUGA tidak bisa dijalankan di sandbox → `config/detekt/detekt.yml` cuma
  divalidasi parse YAML; nama rule ditulis dari ingatan detekt 1.23.x, bisa salah. Baca log artifact CI
  `static_analysis_v*` dulu sebelum menyimpulkan apa pun soal temuan.
- Constructor API Android yang jarang dipakai (`DynamicsProcessing.*` dkk)
  WAJIB dicek ke dokumentasi resmi `developer.android.com` dulu — jangan
  tebak urutan/nama parameter dari nama variabel yang "kedengaran masuk
  akal" (insiden nyata: Batch 85, param `inUse` disalah-isi literal `0`).
- API Compose yang PERTAMA KALI dipakai di project ini (belum ada
  precedent lokal) — turunkan confidence eksplisit, jangan asumsikan
  aman (insiden nyata: `LocalIndication` non-null Batch 25,
  `@OptIn` kurang Batch 22).
- `?attr/...` di drawable XML WAJIB prefix `?android:attr/...` kalau
  maksudnya attr framework — attr tanpa prefix di-resolve ke namespace
  package sendiri, kalau tidak terdefinisi → AAPT2 FAILED (insiden nyata:
  v1.40→v1.41, `ic_qs_tile.xml`).
- Kalau ZIP project TIDAK dibungkus folder induk (kasus project ini),
  target `unzip -d` HARUS folder project itu sendiri, BUKAN parent-nya —
  circuit breaker integritas bisa salah trigger kalau file nyasar ke
  folder induk (insiden nyata: v1.41).
- Value YAML yang isinya `#<...>` WAJIB di-quote — plain scalar
  memperlakukan spasi+`#` sebagai awal komentar bahkan di tengah baris,
  bisa diam-diam kepotong tanpa CI error apapun (insiden nyata: Batch 78,
  bug ini lolos 9 batch sebelum ketemu).
- Packaging ZIP DILARANG pakai pola exclude match-semua (`-x ".*"`) —
  bisa ikut membuang `.github/workflows/` dan `.gitignore` tanpa
  terdeteksi validasi manapun (insiden nyata: v1.46). WAJIB `unzip -l`
  pada ZIP HASIL AKHIR dan cocokkan ke `FILE_MANIFEST.txt` sebelum
  `present_files`.
- `Surface`/`Card` dengan warna custom/alpha-blend (bukan slot asli
  `ColorScheme`) butuh `contentColor` eksplisit — auto-detect
  `contentColorFor()` fallback ke hitam pekat, teks jadi nyaris invisible
  di dark theme (insiden nyata: v1.32).
- `const val` Kotlin CUMA valid untuk literal yang compiler bisa resolve
  tanpa runtime — field API Android manapun (`Environment.*`, `Build.*`)
  BUKAN compile-time constant walau terlihat "konstan" (insiden nyata:
  v1.67).
- Laporan "service/notifikasi mati sendiri" (v1.34): implementasi Android
  sudah diaudit BENAR (`stopWithTask=false`, `START_STICKY`,
  `foregroundServiceType`) — akar masalah SELALU battery/task manager
  OEM (MIUI/ColorOS/EMUI/dll), bukan bug kode. Cek dulu Autostart device
  sebelum curiga ke `AudioEnhancerService`.
- **Android 12+ background foreground-service-start restriction (Batch 124,
  terverifikasi developer.android.com/about/versions/12/foreground-services)**:
  `context.startForegroundService()` dari context latar belakang TANPA masuk
  daftar exemption resmi (activity transition, tap notifikasi/widget/QS Tile,
  broadcast `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`/timezone-locale, exact
  alarm, FCM high-priority, dll — WorkManager `CoroutineWorker` BIASA TIDAK
  termasuk) melempar `ForegroundServiceStartNotAllowedException`. minSdk
  project ini = 24, targetSdk 34 (`app/build.gradle.kts`, dikoreksi Batch 128) —
  restriction berlaku di semua device Android 12+ (API 31+). Pola aman WAJIB dipakai tiap
  ada pemanggil `AudioEnhancerService.requestStart()` baru dari context
  non-UI/non-exempted: bungkus try-catch, fallback notifikasi tap-to-restart
  (`postRecoveryNotification()`, sudah ada sejak Batch 124) — JANGAN asumsikan
  `requestStart()` selalu sukses tanpa try-catch di context background baru.
- Kandidat OEM Autostart Infinix/Tecno/itel (`OemAutostartHelper.kt`)
  PALING TIDAK TERVERIFIKASI dari semua kandidat — bahkan library
  populer sekelas `judemanutd/AutoStarter` (600+ stars) masih punya issue
  terbuka soal ini sejak 2020.
- Siklus troubleshooting efisien tanpa compiler: (1) perubahan VISUAL
  murni → update `docs/preview/current.html` dulu (validasi via browser
  HP dalam detik), baru port ke Kotlin; (2) perubahan LOGIC/behavior →
  tetap lewat siklus penuh zip→Termux→CI→install, tidak ada jalan pintas;
  (3) repo PUBLIC → GitHub Actions minutes gratis, biaya sebenarnya WAKTU
  per putaran (~5-10 menit all-in), bukan uang.

---

## 🗂️ Struktur proyek singkat (state saat ini, bukan histori per-batch)
- `MainActivity.kt` — lifecycle Activity, permission launcher, shortcut
  Intent, glue ke ViewModel + `BoosterScreen()`. Dark theme dipaksa. State
  `appThemeStyleKey` (persisted) di-map ke `AppThemeStyle` enum. `onResume()`
  juga resync paksa widget+tile (Batch 126, lihat `QuickToggleTileService.kt`).
- `BoosterScreen.kt` — layar utama Compose. Default: 1 `Column`
  `.verticalScroll()` flat berisi Preset Cepat → kartu Bass/Virtualizer/
  Loudness (grouped-list 1 card) → Equalizer Manual → toggle Material You
  (Android 12+) + 4 toggle varian tema eksklusif (semua mati = Midnight
  Glass) → kartu baterai/autostart. Opsi custom: Mode Tab Horizontal
  (`TabPageContent(page)`, tap-tab via `selectedTabIndex` + swipe lintas tab Batch 141:
  `detectHorizontalDragGestures` di Box induk, `tabSwipeDelta()`). Termasuk
  `PowerToggleRow`, `ServiceStatusBadge`, `CrashBanner`,
  `ControlRecoveryBanner`, `OutputRouteBanner` (Batch 107, info route
  audio), `UpdateBanner`, `EqualizerSection`, dialog preset.
- `SkeuomorphicComponents.kt` — atom UI reusable (`SkeuCard`,
  `SkeuTintedCard`, `SkeuPowerButton`, `SkeuSwitch`, `SkeuGroupDivider`,
  `SectionLabel`, `FeatureControl`, `Modifier.skeuGlow`). Semua
  theme-aware lewat `LocalSkeuTokens.current` — komponen baru WAJIB baca
  dari sini, JANGAN reference val hardcoded.
- `AudioEnhancerService.kt` — foreground service, attach BassBoost/
  Virtualizer/Equalizer/LoudnessEnhancer/DynamicsProcessing(limiter+PreEq
  fallback) ke audio session 0. Tiap effect punya `EffectState`
  (UNAVAILABLE/AVAILABLE/ENABLED/FAILED/CONTROL_LOST) via field
  `@Volatile`, dipoll `BoosterViewModel` tiap 1 detik, disurface penuh ke
  UI. `retryControlAcquisition()` publik (dipanggil `ControlRecoveryBanner`).
  `AudioDeviceCallback` terdaftar — nudge `enableEffects()` (bukan
  recreate) saat output route berubah, digate `isRunning`. Sleep timer
  (Batch 119): `requestSleepTimer()`/`cancelSleepTimer()` (companion), tick
  Handler ≤30 dtk baca `PrefsHelper.getSleepTimerEndAt()`, habis →
  `requestStop()`; `ACTION_SLEEP_TIMER_SYNC` = jadwalkan ulang tick.
  `postRecoveryNotification()` (companion, Batch 124) — fallback tap-to-restart
  (channel `CHANNEL_ID_RECOVERY`, HIGH) kalau `requestStart()` diblokir Android
  12+ background-start restriction, dipanggil `ServiceWatchdogWorker`.
- `Theme.kt` — palet dark-only, typography, shape, token bevel/glow untuk
  ke-5 varian tema (Batch 111: +Serene M3). Accent color per-fitur independen
  dari switch tema. `SkeuTokens` data class + `LocalSkeuTokens`/
  `LocalAppThemeStyle` CompositionLocal.
- `PrefsHelper.kt` — SharedPreferences wrapper, semua persistence.
  `CustomPreset` punya field `eqBands: List<Int>` (default `emptyList()`,
  backward-compat via `optJSONArray`). `getUseHorizontalTabLayout()`/
  `setUseHorizontalTabLayout()` — key `use_horizontal_tab_layout`.
  `exportCustomPresetsToJson()`/`importCustomPresetsFromJson()` (Batch 115,
  envelope JSON, parsing atomik per-entry, reuse `addCustomPreset`).
  `getSleepTimerEndAt()`/`setSleepTimerEndAt()` (Batch 119, epoch ms, 0 =
  tanpa timer).
- `CrashLogger.kt` — tangkap uncaught exception → MediaStore API 29+,
  rotasi FIFO maks 50 file.
- `AudioEnhancerApp.kt` — Application class, `CrashLogger.install()`.
- `OemAutostartHelper.kt` — deep-link Autostart/battery manager per-OEM,
  fallback ke App Info.
- `WatchdogAlarmReceiver.kt` — receiver internal (exported=false), fire heartbeat
  exact alarm (Batch 127); memanggil `performWatchdogCheck`.
- `ServiceWatchdogWorker.kt` (+ heartbeat exact alarm ~5 mnt, Batch 128:
  `scheduleExactRecovery`/`cancelExactRecovery`/`canUseExactAlarm` publik) —
  WorkManager periodic 15 menit, restart service kalau mati padahal user tidak minta mati (Batch 124: try-catch +
  `postRecoveryNotification()` fallback). Tiap tick JUGA selalu paksa resync
  `BoosterWidgetProvider.refreshAll()` + `QuickToggleTileService.requestTileUpdate()`
  ke `isRunning` ground truth (Batch 125 — widget gak punya hook on-demand
  setara `onStartListening()` tile, jadi bisa nyangkut basi kalau kill keras).
- `OnboardingScreen.kt` — 6 halaman onboarding (belum diaudit gaya iOS).
- `UpdateManager.kt` — cek Release GitHub terbaru vs `versionCode`
  runtime, unduh APK chunk-streaming Okio (`Source.read`/`Sink.write`
  DASAR, TANPA `.buffer()`, DILARANG `readBytes()`), install via intent
  `ACTION_VIEW`+FileProvider. `fetchLatestRelease()` privat return
  `CheckResult` sealed class (Available/UpToDate/Failed).
- `SettingsScreen.kt` — entry point cek-update manual (ikon ⚙️ di header),
  komparasi versi + release notes + tombol unduh inline, 100% reuse state
  `BoosterViewModel`. Section "Navigasi Layar Utama" — toggle Mode Tab
  Horizontal. Section "Cadangkan Preset" (Batch 115) — Export/Import `.json`
  via SAF (`CreateDocument`/`OpenDocument`, I/O di `Dispatchers.IO`). Section
  "Timer Tidur" (Batch 119) — tombol durasi 15-120 mnt + sisa waktu (poll 1 dtk).
  Section "Pemulihan Cepat" (Batch 128) — status izin "Alarm & pengingat" + tombol
  deep-link (poll 1,5 dtk) — DIHAPUS Batch 132. Section "Jadwal Otomatis" (Batch 134) —
  toggle + 2 tombol jam (`ScheduleTimeRow`), tulis `PrefsHelper` lalu `ScheduleWorker.reschedule()`.
- `ScheduleWorker.kt` (Batch 134) — rantai `OneTimeWork` unik `boomly_daily_schedule`,
  event terdekat nyala/mati; `requestStart()`/`requestStop()`; fallback notifikasi channel
  sendiri (id 1003). `nextOccurrence()` internal (belum ada unit test).
- `BoosterWidgetProvider.kt` (widget home), `QuickToggleTileService.kt` (QS
  Tile — `onStartListening()` juga resync widget tiap shade dibuka, Batch 126),
  `ShortcutHelper.kt` (App Shortcuts), `BootReceiver.kt` (start ulang
  setelah boot).
- Test (`app/src/test`): `AudioEnhancerServiceStateTest.kt` (13 test
  Robolectric, Batch 86), `PrefsHelperTest.kt`, `FormatFreqLabelTest.kt`.
- `docs/preview/current.html` — mockup HTML standalone (HANYA Midnight
  Glass), WAJIB update bareng perubahan visual besar. `docs/archive/` — dok
  usang, JANGAN jadi acuan konteks.
- `config/detekt/detekt.yml` — whitelist rule detekt (Batch 142); `app/lint.xml` — severity Android Lint
  (Batch 142). Keduanya NON-BLOCKING, lihat "Keputusan sadar".

---

## 📋 TODO / ROADMAP — backlog aktif (konsolidasi Batch 106 dari `roadmap.md` +
2x `PENDING_*.md`, ketiganya diarsipkan ke `docs/archive/` — lihat "🔒 ATURAN
PERMANEN" soal kebijakan arsip. Ini SEKARANG satu-satunya sumber kebenaran
backlog, jangan biarkan pecah lagi ke file terpisah.)

- [ ] Proteksi sentuh-jauh-dari-thumb di slider (Bass/Virtualizer/Loudness/
  Compressor/EQ band) — dicoba Batch 138, DI-REVERT Batch 140 (regresi:
  malah loncat ke 0, bukan diblok). Backlog, BUTUH device-in-the-loop kalau
  mau dicoba lagi — lihat "⚠️ Temuan terbuka" utk hipotesis root-cause &
  saran pendekatan pertama (key `pointerInput` tanpa `value`).

**Definisi "100%/Tamat"** (4 kondisi bareng): (1) Fungsional — semua fitur
README ada & jalan; (2) Runtime-verified — semua perubahan sejak Batch 1
terkonfirmasi jalan di device fisik, BUKAN cuma statis; (3) CI hijau stabil
berkali-turut; (4) 0 TODO Medium/High tersisa (Low boleh permanen pending
kalau sengaja dideprioritaskan user). Estimasi kasar: fungsional ~95%;
gap "terbukti benar di device" tinggal sisa NON-tema di Fase 1 (backlog
pasif, NON-BLOCKING — tema sudah tervalidasi, Batch 118). Kalau user bilang
"next"/"lanjut" tanpa fitur spesifik → lanjut item berikutnya urutan ROI
Fase 8, BUKAN ditahan buat validasi.

### Fase 0 — Audio Engine Robustness (audit eksternal Batch 57)
9 item dari audit eksternal, kerjakan SATU per satu (instruksi eksplisit
user, jangan sekaligus). Status: 5/9 selesai (#1 effect-state verification,
#4 control ownership/lifecycle, #7 preset+EQ, #8 automated test, #9
UI/error-state refinement — SELESAI Batch 107, lihat detail di bawah), 3/9
sebagian (#2 capability detection — LoudnessEnhancer secara teknis TIDAK
bisa diquery, bukan gap; #3 output routing — deteksi ada + SEKARANG
disurface UI (Batch 107, cek "Belum divalidasi runtime" di bawah); #5 gain
staging — limiter pasif ada, pipeline eksplisit TIDAK bisa dijamin urutannya
di API publik), 1 hybrid (#6 rebuild session-0 — Fase 1 dari rebuild
bertahap selesai, lihat "Batasan Fundamental" di bawah). Detail teknis
lengkap tiap item: `CHANGELOG.md` Batch 57-63, 83-87, 107.

**#9 detail (Batch 107)**: audit ulang confirm 4 dari 5 state target sudah
tersurface sejak Batch 57-59 via `helpText` per-FeatureControl
(`feature_help_unsupported`/`_strength_unsupported`/`_control_lost`/
`_failed`) — TIDAK ada gap di situ. Gap SATU-SATUNYA yang nyata:
"Output-changed" (`lastOutputRouteDescription`, Batch 83) write-only,
tidak pernah dibaca UI. Ditutup via `OutputRouteBanner` baru (BoosterScreen.kt) —
tint primary/info (BUKAN error), auto-dismiss-reset saat route ganti lagi.

**BATASAN FUNDAMENTAL #6 (baca sebelum lanjut Fase 2+ rebuild)**: TIDAK ADA
API publik Android yang beri app kontrol urutan insert effect di HAL chain
audio session 0 — berlaku untuk `AudioEffect` legacy MAUPUN
`DynamicsProcessing`. Pipeline eksplisit "Input→PreGain→EQ→Dynamics→
Loudness→Output" dari audit asli SECARA HARFIAH tidak bisa dicapai 100%
tanpa akses HAL vendor. Satu-satunya jalan kontrol penuh:
`AudioPlaybackCaptureConfiguration` (API 29+, capture+reprocess+re-output
manual) — ini **arsitektur & produk beda total** (effort bulanan, popup izin
tiap start, risiko echo/latency/baterai jauh lebih tinggi), **TIDAK
direkomendasikan diinisiasi tanpa user eksplisit minta & paham ini
pengganti total, bukan penyempurnaan**. Kandidat Fase 2 rebuild (belum
dikerjakan, tunggu arahan user pilih salah satu): (a) fallback shelving-gain
buat BassBoost/Virtualizer UNAVAILABLE lewat DynamicsProcessing PreEq/PostEq
— risiko karakter psychoacoustic beda dari BassBoost asli, perlu keputusan
desain dulu; (b) validasi device fisik Fase 1 — SECARA ALAMI jarang
ke-trigger (mayoritas device Equalizer legacy-nya matang), butuh device/
emulator API rendah atau chipset eksotis buat benar-benar uji; (c) tanya
user eksplisit apakah `AudioPlaybackCaptureConfiguration` worth dieksplorasi
sebagai proyek TERPISAH — TIDAK diinisiasi proaktif.

**Belum divalidasi runtime (Fase 1 rebuild, Batch 87)**: apakah
`needsEqFallback` ke-trigger cuma di device yang memang butuh (belum ada
device uji nyata yang Equalizer-nya UNAVAILABLE); apakah
`DynamicsProcessing.EqBand` preEqBandCount=5 construct sukses di device API
28+ nyata (variasi HAL); apakah konversi mB→dB (levelMb/100f, rentang ±12dB
konservatif) terdengar wajar dibanding Equalizer asli.

### Fase 1 — Runtime Validation Debt (backlog pasif, NON-BLOCKING)
Sisa perubahan lama yang dikirim "belum divalidasi runtime" (statis only).
Bukan gate untuk item baru. **Cara kerja**: tiap user install APK baru,
cocokkan ke daftar, centang yang confirmed OK, catat detail kalau gagal
(jadi bug baru, bukan "belum divalidasi" lagi):
- [x] **5 varian tema** (Midnight/Aurora/Neumorphism Batch 108-110/Studio
  Equalizer/Serene M3 Batch 111-113) — **USER-CONFIRMED BERHASIL (Batch 118,
  sudah lama jalan)**. Kandidat bug lama (aliasing cut-corner, kontras
  `onPrimary`, overflow tracking, persepsi pine-shadow) DITUTUP — JANGAN
  dicek/ditanyakan ulang kecuali ada laporan bug baru dari user.
- [x] Export/Import preset (Batch 115-116) — user-confirmed di device fisik
  (compile + skenario ekspor/impor OK).
- [ ] `BoosterViewModel` pasca-cabut Hilt (Batch 49) — pastikan gak ada crash
  `Cannot create an instance of BoosterViewModel`.
- [ ] `configuration-cache` (Batch 50) — CI tetap hijau, gak ada warning di
  tab Actions.
- [ ] Slider custom/SkeuSwitch/skeuGlow (Batch 22, 32) — render normal di
  device asli (bukan cuma preview HTML).
- [ ] Race condition `@Volatile isRunning` (Batch 45) — trigger skenario
  watchdog restart, cek widget/QS Tile sinkron balik.
- [ ] Crash Logger MediaStore (Batch 27/29) — trigger 1 crash sengaja, cek
  file muncul `Documents/AudioEnhancerPro/logs/`, retensi FIFO maks 50 file.
- [ ] Bind service via Application Context (Batch 17) — gak ada context-leak
  setelah rotasi/app di-background lama.
- [ ] `ControlRecoveryBanner`+`retryControlAcquisition()` (Batch 62) — kalau
  `CONTROL_LOST`/`FAILED` kejadian natural: banner muncul ≤1 detik, tombol
  gak crash, snackbar muncul, banner hilang sendiri saat state balik
  ENABLED/AVAILABLE.
- [ ] Preset custom simpan EQ (Batch 63) — atur EQ manual per-band, simpan
  preset, ubah lagi EQ, terapkan preset tadi → slider balik PERSIS ke nilai
  saat disimpan; preset LAMA (sebelum update ini) masih bisa diterapkan
  tanpa crash & tidak mengubah EQ manual aktif.
- [ ] `OutputRouteBanner` (Batch 107) — ganti output audio (colokin/cabut
  Bluetooth/wired/USB) di device fisik saat service jalan, cek banner biru
  muncul ≤1 detik dengan deskripsi device benar, dismiss via "Oke", ganti
  route LAGI ke device lain → banner muncul lagi (bukan permanen hilang).
  Kandidat gagal: `AudioDeviceCallback` tidak fire di device/OEM tertentu
  (risiko sejak Batch 83, belum ada data nyata).
- [ ] Sleep timer (Batch 119) — Pengaturan → Timer Tidur: nyalakan Boomly,
  pilih durasi, cek sisa waktu berjalan mundur; biarkan habis → Boomly mati
  seperti tombol Matikan (notif hilang, widget/QS Tile off, watchdog TIDAK
  menghidupkan lagi); batalkan di tengah → tidak mati; Matikan manual di
  tengah → timer bersih; layar mati lama → cek keterlambatan (Handler
  uptime, bukan alarm exact). Kandidat gagal: compile `SettingsScreen.kt`/
  `AudioEnhancerService.kt`, `startService` ke diri sendiri ditolak OEM.
- [ ] Fast Recovery (Batch 128) — Pengaturan → Pemulihan Cepat → tombol izin → aktifkan
  "Alarm & pengingat" → kembali: status jadi "Diizinkan" sendiri; nyalakan Boomly, kill
  via task-swipe/OEM tanpa menyentuh device → pulih ≤~9 mnt (tanpa izin: tetap 15 mnt).
(Lesson swipe-antar-tab Batch 104-105 ada di "Keputusan sadar", tidak
diulang di sini.)

### Fase 2 — Build & CI Maturity
- [x] Gabung job build+release jadi 1 (Batch 40) · Cache Gradle dependency+
  wrapper (Batch 40) · Cabut Hilt/kapt (Batch 49) · configuration-cache
  (Batch 50) · detekt + lintDebug terfokus NON-BLOCKING (Batch 142, CI jalan hijau run 189)
- [ ] Commit `gradlew`/`gradle-wrapper.jar` permanen ke repo — **TIDAK BISA
  dari sandbox** (butuh binary Gradle+network). User manual:
  `gradle wrapper --gradle-version 8.7`, commit 4 file hasilnya.
- [ ] Evaluasi upgrade AGP 8.5.2/Kotlin 1.9.24/compose-bom 2024.06.00 — versi
  lama sengaja dipertahankan (stabil, lolos banyak insiden kompatibilitas).
  Kandidat KALAU user eksplisit minta, bukan inisiatif proaktif (risiko
  regresi tanpa compiler).

### Fase 3 — Audit Polish (Medium/Low, pending sejak Batch 16)
Recomposition/reusable-component review menyeluruh · hierarki visual
(heading/body/caption konsisten) · white space/spacing audit lintas layar ·
micro-animation tambahan · loading/success/error state (sebagian selesai
Batch 51 — snackbar preset/crash-log; sisa gap: preset gagal simpan storage
penuh, ganti tema/toggle Material You masih silent, prioritas rendah) ·
empty state UI (selain `presets_empty_hint`) · tooltip/info icon fitur
lanjutan (Low, opsional).

### Fase 4 — Kompatibilitas Device (DIDEPRIORITASKAN user, JANGAN proaktif)
Murni biar gak hilang dari radar, BUKAN perintah segera kerjakan:
- Konfirmasi tombol Autostart (`OemAutostartHelper`) benar-benar buka
  halaman tepat di **Infinix Note 50 Pro 4G & Note 40 Pro 4G** (XOS) —
  kandidat Transsion (Batch 35) paling gak terverifikasi. Kalau disinggung
  lagi & gagal: (a) cari kandidat ComponentName alternatif versi XOS device
  itu spesifik, atau (b) terima gak ada kandidat reliable (persis
  `AutoStarter` library) & fokus instruksi manual jelas di UI.
- Rotasi layar/config change (portrait-lock de facto, belum test eksplisit)
  · font scaling besar · landscape phone · RTL · kontras tombol biru (lokasi
  belum dicatat ulang, perlu screenshot user).

### Fase 5 — Feature Backlog (maintenance mode, opsional, JANGAN proaktif)
Custom EQ curve editor (drag-point) — sudah diangkat ke Fase 8 A. [x]
Export/import preset (Batch 115-116) dan [x] in-app update checker (Batch
69/73) SELESAI — tidak ada sisa lain di fase ini.

### Fase 6 — Dokumentasi & Housekeeping
[x] Batch 106: 4 dokumen non-standar (`roadmap.md`, 2x `PENDING_*.md`, arsip
lama) dikonsolidasi ke sini + diarsipkan. [x] Batch 118: cleanup dok —
status tema dikoreksi, section basi dibuang, `archive/`→`docs/archive/`,
README (CI 1 job, tema, brand) & footer preview disinkron.
`docs/preview/current.html` = ground truth HANYA utk Midnight Glass; 4
varian lain TIDAK punya mockup HTML (disengaja, Low priority kosmetik).
Sisa: `PrefsHelperTest.kt` — cek apakah coverage masih relevan
pasca-ekstraksi ke ViewModel (Batch 17).

### Fase 7 — iOS Look Hybrid Rombak (inisiatif user Batch 88, HYBRID method)
**TIDAK ADA rencana ganti sistem 5-varian tema/warna signature** — semua
fase murni STRUKTUR/POLA INTERAKSI (grouping, tipografi, bentuk komponen),
BUKAN re-skin warna. Status: [x] Fase 1 grouped-list "Kontrol" (Batch 88-89,
tervalidasi screenshot) · [x] Tipografi Large Title 34sp (Batch 90+92,
tervalidasi) · [x] Grouped-list SettingsScreen (Batch 91-92, tervalidasi,
1 bug divider fixed) · [x] Styling pill Preset Cepat outline-only (Batch 93,
selesai kode, BELUM tervalidasi visual). **3 kandidat sisa** (urutan belum
final, tunggu arahan user): nav bar/header large-title-collapsing (invasif,
butuh koordinasi state scroll) · audit `OnboardingScreen.kt` (belum disentuh
sama sekali) · SF Symbols-style icon treatment (Compose gak punya SF Symbols
asli, ganti icon set berisiko besar kalau sekaligus, belum ada keputusan).

**Progress ringkas per fase**: 0 → 5/9 selesai + 3/9 sebagian + 1 hybrid
(#6). 1 → tema + Export/Import tervalidasi; sisa item non-tema (pasif,
non-blocking). 2 → 4/6 selesai. 3 → 1/7 mulai. 4 → sengaja ditunda. 5 →
selesai (editor pindah ke Fase 8 A). 6 → sebagian (Batch 106, 118). 7 →
Fase 1+2 opsi A/B tervalidasi, opsi C selesai kode belum tervalidasi, 3
kandidat sisa D/E/F. 8 → item D selesai; B Sleep timer bag. 1 (kode, NOT VERIFIED); sisanya nunggu instruksi. 9 → planning B148 (M0-M8); M0 CI B150 (lint 18W/detekt 0), M1 kode B149 (STATIC-VERIFIED), M3 kode B151 (NOT VERIFIED), M9 bersih lint SELESAI (B152+B153 STATIC-VERIFIED), M5 analytics B154 (NOT VERIFIED).

### Fase 8 — Powerful Upgrade Roadmap (usulan baru, Batch 114, request eksplisit user)
Backlog OPSIONAL — bukan perintah kerjakan sekaligus. Syarat P0: CI hijau
(terakhir hijau + runtime OK: Batch 115-116); Fase 1 = backlog pasif, BUKAN
gate. Eksekusi per-item saat user menginstruksikan (interpretasi Batch 118:
"next" tanpa nama fitur = lanjut item berikutnya urutan ROI di bawah).
Tunnel Vision, maks 3 file kode/batch.

**A. Audio Engine (nilai jual inti "Enhancer")**
- [x] Compressor/Limiter (`DynamicsProcessing`) — limiter pasif SUDAH ada
  sejak Batch 84; item ini = compressor yang bisa diatur user, pelengkap
  Loudness Enhancer. Kode SELESAI Batch 121. Status: **NOT VERIFIED** — no
  toolchain lokal buat compile-check, belum diuji device fisik (lihat LOG
  BATCH Batch 121).
- [x] Custom EQ curve editor drag-point (sudah lama di Fase 5, DIANGKAT
  prioritas — diferensiator vs app EQ generic). Kode SELESAI Batch 133
  (`EqCurveEditor.kt`, pelengkap slider `EqualizerSection` yang sudah ada,
  bukan pengganti). Status: **NOT VERIFIED** — belum diuji device fisik
  (lihat LOG BATCH 133).
- Reverb/Spatial toggle (`PresetReverb`/`EnvironmentalReverb`) — pelengkap
  Virtualizer, API sekelas efek yang sudah ada.
- Per-app profile (target session per package, bukan cuma session 0) —
  stretch, riset API dulu, risiko kompatibilitas tinggi.

**B. Automation & Intelligence**
- [x] Auto-profile per output device: `OutputRouteBanner` (Batch 107) sudah
  deteksi ganti device — extend jadi auto-apply preset custom (opt-in,
  default mati), bukan cuma banner info. Kode SELESAI Batch 122. Status:
  **NOT VERIFIED** — no toolchain lokal, belum diuji device fisik (lihat LOG
  BATCH Batch 122).
- Sleep timer — [x] auto-stop (Batch 119, NOT VERIFIED); sisa: fade-out
  volume (butuh keputusan user: fade STREAM_MUSIC + restore volume, atau
  fade kekuatan efek saja).
- [x] Scheduler harian nyala/mati via `WorkManager` (`ScheduleWorker.kt`, Batch
  134, NOT VERIFIED). Preset per jadwal: [x] Batch 151 (NOT VERIFIED, tanpa intent baru di Service).
  Sisa: hari tertentu, event non-jam.

**C. Reliability & Compat (extend Fase 1/4)**
- Fallback `Equalizer` via `DynamicsProcessing` (Batch 87) — validasi
  device fisik + expand ke 10-band kalau chipset support.
- Database OEM battery-whitelist auto-detect (Xiaomi/Oppo/Vivo/Samsung) →
  deep-link halaman autostart spesifik, ganti instruksi manual generik.

**D. Data & Ecosystem**
- [x] Export/Import preset via file `.json` share — kode Batch 115
  (`PrefsHelper.kt`+`SettingsScreen.kt`), EN string Batch 116. **USER-
  CONFIRMED WORKING di device fisik.** SELESAI+TERVALIDASI.
- Cloud backup preset (Google Drive AppData folder scope) — sync antar
  device tanpa backend custom.
- Android Auto / Wear OS companion tile — stretch, footprint besar.

**E. Observability**
- [x] Mini spectrum visualizer reaktif (`Visualizer` AudioEffect, session 0)
  — keputusan izin `RECORD_AUDIO` (Fase 8E, Batch 119) DIPUTUSKAN user:
  "Asli" (izin real, bukan sintetis). Kode SELESAI Batch 120. Status:
  **NOT VERIFIED** — belum diuji device fisik (lihat LOG BATCH Batch 120).
- Extend `CrashLogger` jadi analytics lokal ringan (durasi service ON,
  preset paling sering dipakai) — 100% on-device, tanpa cloud.

**Urutan rekomendasi (ROI tertinggi dulu)**: 1) ✅ Export/Import preset (D)
— SELESAI Batch 115-116. 2) ✅ Spectrum visualizer (E) — kode SELESAI Batch
120, NOT VERIFIED. 3) Sleep timer + Scheduler (B) —
Sleep timer auto-stop ✅ kode Batch 119 (NOT VERIFIED); sisa fade-out &
Scheduler. 4) ✅ Compressor (A) — kode SELESAI Batch 121, USER-CONFIRMED
WORKING. 5) ✅ Auto-profile per output device (B) — kode SELESAI Batch 122,
NOT VERIFIED. 6) ✅ EQ curve editor (A) — kode SELESAI Batch 133, NOT
VERIFIED. 7) ✅ Scheduler harian — kode SELESAI Batch 134, NOT VERIFIED
(fade-out Timer Tidur masih BLOCKED/di-skip user: fade STREAM_MUSIC vs kekuatan
efek). Berikutnya: 8) sesuai kebutuhan user.

### Fase 9 — Planning Embedded Konstitusi v3.5 (Batch 148, request user: "planning embedded baru berdasarkan konstitusi yang berlaku")
Docs-only, 0 kode. "Embedded" = backlog HANYA di file ini (tanpa `PENDING_*.md`/dok baru). Konstitusi v3.5 LOCKED menang atas teks PIN
yang bentrok. Audit di bawah = grep statis ZIP v147, BUKAN verifikasi build/device.

**Aturan eksekusi tiap milestone (v3.5)**: 3-5 file source/target per batch (dok VIP kebal) · Min Scope: UI=UI saja, Logic=logic minimum,
Bug=root-cause minimum · tanpa rombak file stabil · jangan menumpuk fitur di atas fitur NOT VERIFIED di area sama · validasi: Syntax → Build (CI)
→ Affected behavior → Regression → Package → Docs · tiap output tulis ulang `[RESUME POINT]` skema `[FITUR/BUG] -> [STATUS] -> [LANGKAH]`, bug WAJIB
sebut baris/fungsi · AUTO-HALT: token <20%, error loop 3 iterasi, ancaman OOM.

**Selisih teks file ini ↔ v3.5** (v3.5 berlaku; teks PIN BELUM diedit — sinkron di M8 atas persetujuan user)
- Format chat: PIN "summary 1 blok kode ≤5 baris" → v3.5: summary bullet TANPA backticks, lalu ZIP, lalu skrip bash.
- Limit micro-batch: PIN "maks 3 file" → v3.5 "3-5 file".
- Skrip Termux: HARD LOCK lama bicara `find ~/projects -iname` → skrip v3.5 pakai `PROJ_DIR=~/projects/[TERMUX_ROOT]` langsung
  (`[TERMUX_ROOT]` = AudioEnhancerPro, BUKAN Boomly). Pakai skrip v3.5 apa adanya, `||` literal.
- Pre-commit hook `./gradlew detekt lintDebug`: v3.5 wajibkan; Batch 142 tidak memasang (repo tanpa `gradlew`, Termux tanpa Gradle) → DEVIASI
  OBJEKTIF, gerbang CI non-blocking tetap berlaku; kunci pembuka = T1.

**Audit guard v3.5 vs ZIP v147 (statis)**
- OK Crash handling: `CrashLogger.kt:67` `setDefaultUncaughtExceptionHandler`; log ke `Documents` via MediaStore (API 29+).
- OK Streaming/OOM: `UpdateManager.kt` unduh APK per-chunk Okio di `Dispatchers.IO`; `source`/`sink` ditutup di `finally` (setara `use {}`).
- OK Security: tak ada secret/`buildConfigField` di kode & `app/build.gradle.kts`; keystore via GitHub Secrets (Box B).
- OK Insets: root `safeDrawingPadding()` (`MainActivity.kt:160`, termasuk IME) + `enableEdgeToEdge()` (L104).
- OK Flow: tak ada `StateFlow`/`collectAsState*` di `BoosterViewModel`/`BoosterScreen`/`SettingsScreen`/`MainActivity` (state = Compose
  `mutableStateOf`) → aturan lifecycle-aware N/A selagi belum ada Flow.
- GAP rotasi (`MainActivity.kt` DITUTUP kode B149, STATIC-VERIFIED; sisa `SettingsScreen.kt` transient prioritas rendah): `MainActivity.kt` `showOnboarding` (L164) & `showSettings` (L171) = `remember`, BUKAN `rememberSaveable`;
  manifest `MainActivity` tanpa kunci orientasi/`configChanges` (klaim Fase 4 "portrait-lock de facto" tak didukung manifest) → rotasi saat di
  Settings kemungkinan melempar ke layar utama. `SettingsScreen.kt` 0 `rememberSaveable`; mayoritas state dibaca ulang dari `PrefsHelper` (aman),
  transient: `scheduleTimeConflict` (L391), `backupStatus`/`backupStatusIsError` (L590-591) → prioritas rendah.
- Recomposition DIAUDIT Batch 158 (M7); dispatcher/Thread Safety DIAUDIT Batch 163 (temuan `CrashBanner` → `Dispatchers.IO`, sisanya OK) — tidak ada item audit guard yang tersisa.
- DEVIASI SADAR (JANGAN disentuh — keputusan user > Guard): `AudioEnhancerService` FGS permanen (`stopWithTask=false`, inti produk) +
  `ServiceWatchdogWorker` (WorkManager 15 mnt, B124/B131). v3.5 "dilarang custom watchdog/FGS abadi" BUKAN alasan menghapusnya (regresi B124
  = melanggar P0). Sisa dorman: `WatchdogAlarmReceiver`, izin `SCHEDULE_EXACT_ALARM`, `canUseExactAlarm()`.

**Milestone** (urut eksekusi; "next" tanpa nama fitur = nomor terkecil yang tidak BLOCKED/butuh-pilihan-user)
- [x] M0 · Gerbang CI (0 file) — SELESAI Batch 150 (artifact run 194: InlinedApi 0, warning 20→18, compile lolos): kalau user upload artifact `static_analysis_v*` terbaru → cek InlinedApi 0 & warning 20→18; B146 naik
  STATIC-VERIFIED bila `compileDebugKotlin` hijau. Tanpa artifact = lewati, JANGAN menunggu.
- [x] M1 · UI-state rotasi (Bug, 1 file) — kode SELESAI Batch 149, STATIC-VERIFIED (CI run 194; device = test rotasi di bawah belum): `MainActivity.kt` L164/L171 `remember`→`rememberSaveable` (Boolean). `BackHandler` Batch 74 & pola
  tri-state onboarding/settings TIDAK disentuh. File ke-2 HANYA bila device test menunjukkan state hilang: `SettingsScreen.kt` L391/L590-591.
  Validasi: CI build → rotasi di Settings/Bantuan tetap di layar itu → back gesture tutup Settings tetap benar. Target STATIC-VERIFIED; device = user.
- [ ] M2 · Reverb/Spatial toggle (Fase 8 A) — DITURUNKAN (Batch 151): `PresetReverb`/`EnvironmentalReverb` = efek AUXILIARY (butuh aux-send dari player), di session 0
  kemungkinan besar TIDAK mengubah suara app musik lain (belum diuji device) → jangan bangun UI sebelum ada bukti device. Rencana lama, 2 batch: M2a `AudioEnhancerService.kt`+`PrefsHelper.kt`+`BoosterViewModel.kt` (`PresetReverb`, pola
  `*Supported`/`EffectState` sama Bass/Virtualizer, gagal = UNAVAILABLE bukan crash); M2b `BoosterScreen.kt`+strings ID/EN (UI saja, parity ID=EN).
  Regresi wajib: Bass/Virtualizer/Loudness/Compressor/EQ/Auto-Profil tak berubah. Tanpa thread/logger baru.
- [x] M3 · Preset per jadwal Scheduler — kode SELESAI Batch 151 (1 batch, 5 file; jalur prefs, BUKAN intent Service), NOT VERIFIED. Test device: buat preset custom → Pengaturan →
  Jadwal → pilih chip preset → set jam nyala ±2 mnt ke depan, matikan Boomly → cek preset aktif & suara saat jadwal menyala. Hanya `WorkManager`.
- [x] M4 · DB OEM autostart (Fase 8 C) — kode SELESAI Batch 157 (1 file), NOT VERIFIED (butuh device OEM): DB sudah mencakup Xiaomi/Oppo/Vivo/Huawei/Samsung/Transsion/OnePlus/Asus dengan try/catch +
  fallback App Info; B157 hanya +1 kandidat Asus. Tidak ada kandidat baru tanpa bukti — jangan menambah nama Activity dari ingatan.
- [x] M5 · Analytics lokal (Fase 8 E) — kode SELESAI Batch 154 (5 file incl. strings), NOT VERIFIED: durasi service ON & preset terpopuler 100% on-device lewat `PrefsHelper` (BUKAN logger/kelas baru), ditulis di
  jalur start/stop yang sudah ada (tanpa timer/loop). Max 3 file: `AudioEnhancerService.kt`+`PrefsHelper.kt`+`SettingsScreen.kt`. Prioritas rendah.
- [ ] M6 · Cloud backup preset (Fase 8 D) — BLOCKED/tidak direkomendasikan: butuh OAuth Google/client config, berisiko bentrok Guard Security (tanpa
  hardcode secret) & user tak publish Play Store. Buka hanya bila user eksplisit minta + putuskan skema kredensial.
- [x] M7 · Audit recomposition — SELESAI Batch 158 (2 file), NOT VERIFIED: temuan nyata = `spectrumLevels` ~20 Hz dibaca di komposisi `MainActivity` (merekomposisi seluruh `BoosterScreen`); fix = provider
  dibaca di `Canvas` `SpectrumBars`. Sisa `BoosterScreen.kt` (0 `LazyColumn`, 18 `remember {`, 0 `derivedStateOf`) tak ada temuan terbukti → TIDAK diubah. Device: bar spectrum tetap bergerak halus.
- [x] M8a · Pangkas LOG BATCH — SELESAI Batch 158 (user memilih eksplisit): Batch 1–142 → `docs/archive/PROJECT_STATE_LOG_B1-B142.md` verbatim; file ini ±118 KB → ±80 KB.
- [ ] M8b · Sinkronkan PIN "Format chat"/"maks 3 file" ke v3.5 — BELUM (HANYA bila user minta); tanpa file backlog baru.
- [x] M9 · Bersih warning lint (gerbang = lintDebug+detekt SAJA) — SELESAI: artifact run 197 = 0E/1W/1I (18→11→1). SISA SENGAJA: OldTargetApi 1 (`targetSdk=34` — butuh compileSdk/AGP,
  DI LUAR PLANNING; diaudit Batch 158 = BLOKIR nyata, lihat "Keputusan sadar" targetSdk; DITERIMA user Batch 159), ReportShortcutUsage info 1 → SELESAI Batch 155 (`reportShortcutUsed` di `MainActivity.handleShortcutIntent`; artifact run 199 = 0E/1W/0I, STATIC-VERIFIED).
- Track T1 (manual user, non-source): commit `gradlew`+`gradle-wrapper.jar` permanen (`gradle wrapper --gradle-version 8.7`, commit 4 file) →
  membuka pre-commit hook v3.5. Track T2 (user): device test swipe lintas tab B141 (JANGAN balik ke `HorizontalPager`) & Scheduler B134.

**DI LUAR PLANNING (jangan dikerjakan proaktif)**: guard/test Android <12 (B147) · hapus/kurangi watchdog 15 mnt atau hidupkan lagi heartbeat
(B131/B132) · `HorizontalPager`/auto-height (B105) · retry gatekeeper slider tanpa device-in-the-loop (B140) · fade-out Timer Tidur (BLOCKED, skip user)
· Fase 4 & kandidat Fase 7 D/E/F tanpa arahan user · per-app profile, Android Auto/Wear · upgrade AGP/Kotlin/BOM.

---

[RESUME POINT: I/O crash log `CrashBanner` pindah ke `Dispatchers.IO` (Batch 163; `BoosterScreen.kt` `CrashBanner` L209-289: `LaunchedEffect(Unit)` baca entri awal, `LaunchedEffect(entry)` baca isi dialog, `dismiss()` & tombol hapus via `scope.launch` + `NonCancellable + Dispatchers.IO`; impor L72-75; ZIP `Boomly_v163.zip`) -> kode SELESAI, NOT VERIFIED (statis: brace 309/309 paren 1094/1094, `!!` 0, 0 akses MediaStore/File blocking tersisa di Main untuk crash log, diff = 4 import + `CrashBanner`; belum CI & device); B162 STATIC-VERIFIED via artifact run 204 (marker OK, compile lolos, lint 0E/1W (OldTargetApi)/0I, detekt 0, loc 9.249; perilaku device belum)
-> Langkah berikutnya: (a) user upload artifact BARU `AudioEnhancerPro_static_analysis_v*-run*.zip`: BACA `STATIC_ANALYSIS_MARKER.txt` DULU (PROJECT=AudioEnhancerPro, APPLICATION_ID=com.audioenhancer.booster, BATCH_PROJECT_STATE=163); TARGET lint 0E/1W (OldTargetApi)/0I, detekt 0, loc 9.272 → B163 STATIC-VERIFIED. Kalau compile/lint/detekt error → debug HANYA `BoosterScreen.kt` `CrashBanner` L209-289 (dugaan awal: impor `Dispatchers`/`NonCancellable`/`withContext` L72-75, tipe `CrashLogger.CrashLogEntry?` pada `mutableStateOf`); (b) device test user (Track T2): B163 — ada crash log baru → banner muncul, "Lihat" tampilkan isi, "Hapus" tutup + snackbar, banner tak muncul lagi saat app dibuka ulang; B162 — kirim app ke background beberapa menit lalu buka lagi → UI status/spectrum tetap hidup & akurat; B160/B161 uji dengar preset (Bass Heavy & EDM cek pecah, Treble Boost desis, Vocal Boost sibilance, Cinema/EDM tidak terlalu pelan); B156 shortcut toggle → rotasi (`MainActivity.kt` `onCreate`); B157 tombol Autostart OEM (`OemAutostartHelper.kt` cabang `asus`); B158 bar spectrum halus; (c) targetSdk=34 USER-CONFIRMED (jangan ajukan lagi); standar headroom preset H1/H2 berlaku; M2 DITURUNKAN, M6 BLOCKED, M8b HANYA bila diminta; audit guard v3.5 SELESAI (tak ada sisa); backlog kode tanpa input user HABIS — kalau user bilang "lanjut" lagi, ajukan pilihan lewat tab opsi (mis. `SettingsScreen.kt` `rememberSaveable` L391/L590-591 hanya bila device test tunjukkan state hilang; Fase 3 state silent: ganti tema/Material You); sisa loop 1 Hz composable & capture Visualizer Service = lihat "Keputusan sadar" (HANYA bila diminta); (d) Android <12 guard/test, watchdog/heartbeat, `HorizontalPager`, upgrade AGP/Kotlin/BOM = DI LUAR PLANNING. Batch berikutnya = 164.]
