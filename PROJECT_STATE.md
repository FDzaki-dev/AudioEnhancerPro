[BRANDING_NAME: Boomly]
[TERMUX_ROOT: AudioEnhancerPro]
> B176 melampaui 5 file source (6 file): menambah varian tema ke-6 mengubah kontrak enum `AppThemeStyle` + key persist `PrefsHelper`, sehingga WAJIB menyentuh `Theme.kt`, `PrefsHelper.kt`, `MainActivity.kt`, `BoosterScreen.kt`, `strings.xml` ID+EN.

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
- **Panduan `Tinjauan_Solusi_AudioEnhancer.md` (Batch 175)**: DIAUDIT, TIDAK diterapkan. JANGAN timpa Manifest, `file_paths.xml`, `WatchdogAlarmReceiver`, `PrefsHelper`, `EqCurveEditor`, `AudioEnhancerService` dgn versi panduan (alasan per modul: LOG BATCH 175). Satu-satunya kandidat nyata: `android:allowBackup="false"` (sekarang `true`) — trade-off backup/restore prefs, BELUM diputuskan; tawarkan ke user HANYA bila ia minta.
- **Tema ke-6 "Old Money" (Batch 176)**: `AppThemeStyle.OLD_MONEY`, key persist `old_money`. Palet burgundy terang kalem `0xFFB4455A` (BUKAN burgundy murni `0x800020`: kontras ~1.7:1 di latar gelap) + sage-hunter `0xFF8FA58E` (B178: emas antik `0xFFC8A96A` DITOLAK user — terlalu ramai/kurang calm; JANGAN kembali ke emas) + gading; permukaan charcoal-cokelat NETRAL (B177: JANGAN kembali ke latar kemerahan — itu sumber keluhan "primary mendominasi"; B179: base `1E1A17`, latar `1E1A17→1A1613→171310`, kartu gradien `2A241F→1F1B18` (B180: sengaja LEBIH TERANG dari latar agar kedalaman terbaca dari luminansi)); `primaryGlow`, `sliderKnobHighlight`, `surfaceTint` = champagne `0xFFD2C6A8` (bukan sage: burgundy+hijau di komponen kecil = kesan Natal), bevel kartu = FACET 8 sisi per arah cahaya kiri-atas (B180, menggantikan hairline miring B179); font = `FontFamily.Serif` sistem (0 file font/dependency); kartu chamfer 8dp = PELAT TIMBUL FISIK (B180, request user "cekungan & timbul ultra hyper realistic, tanpa lighting murahan yang nyaru dgn background"; menggantikan dual-shadow alpha-tipis B179 yang dikeluhkan "nyaru"): mesin `DepthStyle`/`OldMoneyDepth` (`Theme.kt`, field `SkeuTokens.depth`, default null = 5 tema lain 0 berubah) dibaca `SkeuomorphicComponents.kt` (`depthCastShadow` bayangan Gaussian 3 lapis di-render sekali ke `ImageBitmap` perangkat lunak + cache per call-site, `depthPlateSurface` facet+grain, `depthWellInner`/`depthWellLip` sumur cekung, `depthKnobFace`/`depthRingRim` knob & power button); kartu = timbul, kotak ikon/track slider/groove switch/power button ON = CEKUNG, divider = alur ukir; permukaan pelat `2A241F→1F1B18` > latar `1A1613` > lantai sumur `14100D`. Alpha/offset = hasil simulasi statis, BELUM di-tuning di device; JANGAN kembali ke tint/alpha tipis sehue latar (= keluhan "nyaru"), JANGAN hitam/putih MURNI pekat (`050302`/`F2EADB` tetap hue hangat); tuning = angka `OldMoneyDepth` (`Theme.kt`) atau `DepthPlateShadow`/`DepthKnobShadow`/`DepthButtonShadow` (`SkeuomorphicComponents.kt`); `error` tetap merah standar. B187: 5 tema lain KINI punya profil depth sendiri (atas permintaan user) — larangan "JANGAN ubah 5 tema lain" = jangan campur blok `OldMoney*` dgn profil tema lain; tuning Old Money = ubah HANYA blok `OldMoney*` di `Theme.kt`, tema lain = profil `GlassDepth`/`AuroraDepth`/`NeumoDepth`/`StudioEqDepth`/`SereneDepth`.
- **Mesin depth di SEMUA tema (Batch 187, request user)**: `depth != null` di keenam `*SkeuTokens`; bentuk sudut tema TETAP (Glass/Aurora/StudioEq/Neumo = `ROUND`, Old Money/Serene = `CHAMFER`), `frameInset` 0.dp di luar Old Money. Cabang `depth == null` (kode lama) dibiarkan sbg fallback. Profil di `Theme.kt` didefinisikan DI ATAS `*SkeuTokens` (inisialisasi top-level berurutan; di bawah = `null` diam-diam). Tuning = angka profil, BUKAN menyentuh mesin.
- **Bilah tab = 1 pill meluncur (Batch 191, request user + video referensi)**: JANGAN kembali ke 3 kunci per-tab (`DepthKeyBox` per tab, B184-B190) & JANGAN beda bobot font antar lapisan label (clip reveal butuh tata letak IDENTIK). Kurva gerak DIUKUR dari video (spring kritis stiffness 100; drag = stiffness 1200); mau lebih cepat/lambat = ubah HANYA `DepthTabSettleSpec` (`SkeuomorphicComponents.kt`).
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
  commit jalan di Termux tanpa Gradle → hook mematahkan commit; gantinya gerbang CI non-blocking. **Batch 164 (request eksplisit user: "tambahkan jaring validasi ketat yang belum terpasang")**: jaring DITAMBAH, tetap NON-BLOCKING — `config/detekt/detekt-typed.yml` hanya untuk `detektDebug` (step CI sendiri; salah ketik di sana TIDAK mematikan `detekt` polos/lintDebug), lint SARIF + `checkTestSources` + `StopShip` warning. Pesan lama di header `detekt.yml` "tambah rule HANYA kalau ada bukti temuan" DIGANTIKAN permintaan ini untuk rule bernama pasti; rule bernama tak pasti SENGAJA tidak ditambah. Rule redundan dgn lint (DefaultLocale dkk) tidak diduplikasi.
  Rule baru HANYA kalau ada bukti temuan nyata; `TooGenericExceptionCaught`/`SwallowedException`
  SENGAJA mati (catch lebar di `AudioEnhancerService` disengaja, efek audio rapuh per-OEM).
  Suppress false positive lint = `@SuppressLint` PER-FUNGSI + komentar alasan (Batch 143), BUKAN ignore global
  di `lint.xml` (biar isu nyata di tempat lain tetap muncul).
- **targetSdk TETAP 34 (audit Batch 158; USER-CONFIRMED Batch 159)**: naik ke 35 merusak autostart boot — `BootReceiver` (BOOT_COMPLETED) memanggil `AudioEnhancerService.requestStart` → FGS `mediaPlayback`, dan Android 15 melarang
  receiver BOOT_COMPLETED meluncurkan FGS tipe itu untuk app target ≥35 (`ForegroundServiceStartNotAllowedException`; `BootReceiver` tanpa try/catch). Edge-to-edge juga jadi default di target 35. Naikkan
  HANYA setelah user memutuskan perilaku boot (mis. notifikasi tap-to-start) — itu perubahan perilaku, bukan housekeeping. Warning lint OldTargetApi dibiarkan sebagai sinyal (jangan di-suppress diam-diam). Batch 159: user memilih eksplisit "Biarkan targetSdk 34 (warning tetap, 0 risiko)" → warning DITERIMA; JANGAN ajukan
  kenaikan targetSdk/suppress lagi kecuali user yang meminta; target lint resmi proyek = 0E/1W (OldTargetApi)/0I.
- **Slider snap kelipatan (Batch 169, request eksplisit user)**: `FeatureControl(step=…)` — Bass/Virtualizer 50, Loudness 50 mB, Compressor 5 %, band EQ 50 mB. Snap hanya di callback (nilai programatik/preset TIDAK di-snap); pilihan angka = keputusan Claude karena user bilang "kelipatan berapa kek" → kalau terasa kasar/halus, ubah angka `step =` di call site (`BoosterScreen.kt`), JANGAN ganti mekanisme ke `steps=`/`pointerInput`. Beda dengan gatekeeper (B138 revert B140; retry B172, NOT VERIFIED device).
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

- **Recents exclusion saat Boomly menyala (Batch 171, request eksplisit user; port LagFix v133)**: selama `AudioEnhancerService.isRunning`, task app disembunyikan dari Recents lewat `AppTask.setExcludeFromRecents` (`AudioEnhancerService.syncExcludeFromRecents`, dipanggil di transisi start (`onStartCommand`) & stop (`ACTION_STOP`) Service + `MainActivity.onResume`) — DINAMIS, BUKAN atribut manifest `excludeFromRecents` permanen. Alasan: di LagFix, menggeser kartu dari Recents TERBUKTI di log memicu SIGKILL oleh modul OEM Transsion `TranManualCleanMgr`; kartu hilang = tak ada yang bisa digeser. Regresi UX DITERIMA (Boomly tak tampil di Recents saat menyala; buka lewat ikon/widget/tile/notifikasi). Efektivitas BELUM terbukti di device (di LagFix pun belum). JANGAN ganti jadi atribut manifest permanen & JANGAN tambah timer/loop untuk ini tanpa perintah user. Kartu baterai (`BoosterScreen.kt`) menampilkan status exemption (✓ / tombol izin) dan notice `isBackgroundRestricted` (API 28+) TERPISAH — keduanya dibaca ulang tiap `onResume`. SENGAJA TIDAK diport dari LagFix: `VendorSettings.kt` (kalah lengkap dari `OemAutostartHelper`), `setAlarmClock`/channel hantu (ditolak, ongkos baterai), `PersistentTrimService` (Boomly sudah punya FGS sendiri).

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

- **Batch terakhir**: 191, 1 file source (`SkeuomorphicComponents.kt`; request user \"ubah slider antar ke-3 tab agar mulus dan mirip persis dengan contoh video saya!! [Berlaku ke semua theme]\" + video `Screen_Recording_20261009_062025.mp4` + screenshot tab Old Money) — `SkeuTabBar` DIROMBAK: 3 `DepthKeyBox` per tab diganti SATU pill timbul yang meluncur (`Animatable` posisi dlm \"indeks tab\", spring kritis stiffness 100 = hasil fit video, tanpa overshoot), warna label berganti lewat clip di tepi pill (2 lapisan label identik, `depthTabReveal`), tap = pilih tab, drag horizontal = pill ikut jari + snap saat lepas, geser vertikal lolos ke scroll (detail LOG BATCH 191). **NOT VERIFIED**: belum dikompilasi/lint/detekt (sandbox tanpa Gradle); perilaku di HP belum diuji. Sebelumnya: 190, 2 file source (`SkeuomorphicComponents.kt`, `BoosterScreen.kt`; request user \"dibeberapa tema itu lancar jaya, sebagian lagi malah kurang optimal tab equalizer nya!!\" + jawaban: SEMUA 6 tema kurang optimal, terparah Aurora; gejala scroll tab patah-patah + buka kartu EQ/ganti tab lambat) — `depthDraw` tanpa `composed` (`DepthDrawBlock`, equals berdasar kunci), `clip` layer track/thumb slider depth dibuang, `EqualizerSection` bisa di-skip via `@Immutable EqualizerBands` (detail LOG BATCH 190). **NOT VERIFIED**: belum dikompilasi/lint/detekt; device belum diuji. Sebelumnya: 189, 4 file source (`BoosterViewModel.kt`, `SkeuomorphicComponents.kt`, `BoosterScreen.kt`, `EqCurveEditor.kt`; request user "gejala masih kambuh. Perluas optimalisasi!!" — gejala spesifik BELUM dirinci) — apply `Equalizer.setBandLevel` pindah ke worker `Dispatchers.IO` (last-write-wins per band), isian track slider digambar di fase gambar (0 rekomposisi track per tick), layer render terpisah per band/kurva/spectrum (detail LOG BATCH 189). **NOT VERIFIED**: belum dikompilasi/lint/detekt; device belum diuji. Sebelumnya: 188, 2 file source (`EqCurveEditor.kt`, `BoosterScreen.kt` blok `EqualizerSection` + `EqBandSlider` baru; request user "kali ini yang dibutuhkan itu optimalisasi. Especially dibagian tab equalizer manual!!") — kurva EQ snap 50 mB + dedupe, `EqualizerSection` tak lagi recompose penuh tiap tick drag, 0 alokasi per frame draw kurva, fix hit-test kurva basi setelah preset/reset (detail LOG BATCH 188). **NOT VERIFIED**: belum dikompilasi/lint/detekt; device belum diuji. Sebelumnya: 187, 3 file source (`Theme.kt`, `SkeuomorphicComponents.kt`, `MainActivity.kt`; request user "terapkan konfigurasi ini ke semua theme yang masih flat!!" setelah konfirmasi tidak crash) — mesin depth (timbul/cekung) kini aktif di SEMUA 6 tema: 5 profil baru `GlassDepth`/`AuroraDepth`/`NeumoDepth`/`StudioEqDepth`/`SereneDepth` (palet & bentuk sudut per tema via `DepthStyle.corner` ROUND/CHAMFER) (detail LOG BATCH 187). **NOT VERIFIED**: belum dikompilasi/lint/detekt; tampilan di HP belum diuji. Sebelumnya: 186, 1 file source (`SkeuomorphicComponents.kt`; request user "Again?!!" + crash log) — log baru = crash v224 yg sama (build B184, bukan fix B185); `depthDraw` ditambah `key(tag)` sbg pengaman kedua (detail LOG BATCH 186). **NOT VERIFIED**. Sebelumnya: 185, 1 file source (`SkeuomorphicComponents.kt`; request user "Fix it!!" + 3 crash log) — perbaikan crash `ClassCastException` v224 (slot `remember(*keys)` bentrok di `composed` bersama `depthDraw`); 2 log `LIMIT` v1.67 sudah diperbaiki Batch 29 (detail LOG BATCH 185). **NOT VERIFIED**: belum dibuild/direproduksi. Sebelumnya: 184, 4 file source (`SkeuomorphicComponents.kt`, `BoosterScreen.kt`, `SettingsScreen.kt`, `OnboardingScreen.kt`; request user "Tuntas kan yang belum terpoles + optimalisasi rendering") — tab/tombol/tombol ikon/dialog/kolom isian/chip Settings kini fisik di Old Money + optimasi (9-slice bersama, cache global, lambda stabil, permukaan ter-bake) (detail LOG BATCH 184). **NOT VERIFIED**: belum dibuild; beban belum diukur di device. Sebelumnya: 183, 2 file source (`SkeuomorphicComponents.kt`, `BoosterScreen.kt`; request user "kenapa pil preset cepat masih flat?!!") — pil Preset Cepat (FilterChip/AssistChip) disambungkan ke mesin kedalaman: diam = timbul, terpilih/ditekan = cekung enamel (detail LOG BATCH 183). **NOT VERIFIED**: belum dibuild. Sebelumnya: 182, 3 file source (`SkeuomorphicComponents.kt`, `Theme.kt`, `MainActivity.kt`; request user "dipoles lagi udah jadi masterpiece") — polish efek timbul/cekung Old Money: kubah halus pelat, alur ukir bingkai, oklusi ambien sumur, specular knob, isian slider enamel, butiran latar layar, bayangan lebih tajam (detail LOG BATCH 182). **NOT VERIFIED**: belum dibuild/lint. Sebelumnya: 181, 1 file source (`SkeuomorphicComponents.kt`; request user "Fix it immediately!!" + `log_fail_v220-debug-run220`) — hapus import salah `asFrameworkPaint` (member `Paint`, bukan top-level) yang membuat `compileDebugKotlin` run 220 gagal (detail LOG BATCH 181). **NOT VERIFIED**: belum dibuild ulang. Sebelumnya: 180, 2 file source (`Theme.kt` blok `OldMoney*` + `DepthStyle`; `SkeuomorphicComponents.kt` mesin kedalaman; +`README.md`; request user "effect cekungan dan timbul ultra hyper realistic pada tema old money, tanpa permainan lighting murahan yang nyaru dengan warna background") — pelat timbul (permukaan lebih terang dari latar, bevel facet per sisi, bayangan jatuh Gaussian 3 lapis, grain) + sumur cekung (track slider, groove switch, kotak ikon, power button ON, divider ukir); 5 tema lain 0 berubah (cabang `depth == null` = kode lama). **NOT VERIFIED**: kompilasi/lint/detekt belum jalan + tampilan di HP belum diuji (detail LOG BATCH 180). Sebelumnya: 179, 1 file source (`Theme.kt` blok `OldMoney*`; +1 frasa `README.md`; request user "Adaptasi panduan neumorphism dark mode pada tema old money!!") — kartu timbul (gradien diagonal + dual-shadow lembut sehue + bevel miring), track slider/groove switch/power button cekung-timbul otomatis, latar dinaikkan ke rentang panduan; 0 file komponen & 0 tema lain disentuh (detail LOG BATCH 179). **NOT VERIFIED**: kompilasi/lint/detekt belum jalan + tampilan di HP belum diuji. Sebelumnya: 178, 1 file source (`Theme.kt` blok `OldMoney*`; +1 frasa `README.md`; request user "warna sekunder diubah ke yang lebih calm namun tetap kuat aura old money") — secondary emas `0xFFC8A96A` → sage-hunter `0xFF8FA58E`, hairline kartu sage 40%, kilau knob/glow/`surfaceTint` champagne `0xFFD2C6A8`. **NOT VERIFIED**: kompilasi/lint/detekt belum jalan + tampilan di HP belum diuji. Sebelumnya: 177, 1 file source (`Theme.kt`, blok `OldMoney*` saja; request user "primary terlalu mendominasi, kasih balance") — permukaan jadi charcoal-cokelat netral (bukan espresso-burgundy), burgundy `0xFFB4455A` lebih kalem, emas naik porsi (`primaryGlow`, garis tepi 50%, teks champagne, `surfaceTint`). **NOT VERIFIED**: kompilasi/lint/detekt belum jalan + tampilan di HP belum diuji. Sebelumnya: 176, 6 file source (`Theme.kt`, `PrefsHelper.kt`, `MainActivity.kt`, `BoosterScreen.kt`, `values/strings.xml`, `values-en/strings.xml`; request user "opsi tema baru, typography shape bernuansa old money, primary burgundy + secondary matching") — varian tema ke-6 **Old Money** (serif sistem, chamfer bersiku, burgundy terang + emas antik + gading). **NOT VERIFIED**: kompilasi/lint/detekt belum jalan (sandbox tanpa Gradle) + tampilan di HP belum diuji. Sebelumnya: 175, 0 file source (docs saja; request user "Terapkan panduan konfigurasi tersebut!!" = `Tinjauan_Solusi_AudioEnhancer.md`) — panduan DIAUDIT vs kode v174, 0 modul diterapkan (sudah ada/bentrok keputusan permanen/regresi; detail LOG BATCH 175); kode IDENTIK v174, B174 tetap NOT VERIFIED. Sebelumnya: 174, 1 file source (`SkeuomorphicComponents.kt`; request user "Saat slider di tap bukan di drag itu malah ngikut. Fix regresi tersebut!!") — REGRESI B173: tap jauh dibuat melompat saat angkat = slider "ngikut" titik tap → DIHAPUS. `Modifier.ignoreTouchFarFromThumb`/`fractionAtX`/`onTapFar` diganti `Modifier.thumbOnlyDrag(enabled, value, range, step, isRtl, onDrag, onDragEnd)`: SEMUA down dikonsumsi (pass Initial, onPress/onTap Slider M3 tak jalan → tak ada `pressOffset`/lompatan awal drag), TAP tak pernah mengubah nilai, nilai berubah HANYA lewat drag horizontal yang mulai ≤32dp dari thumb (RELATIF: nilai awal + geseran jari, `snapToStep` B169 di dalam gate, dedupe lewat variabel lokal), drag jauh = diblok, vertikal-dominan = scroll lolos; haptic di akhir drag. **NOT VERIFIED**: kompilasi/lint/detekt belum jalan + device WAJIB. Sebelumnya: 173, 1 file source (`SkeuomorphicComponents.kt`; request user "perlu disempurnakan: bug di HP (loncat, macet, scroll) + tap track jauh boleh, drag jauh diblok") — gate `ignoreTouchFarFromThumb` DITULIS ULANG: down jauh (>32dp) tetap dikonsumsi di pass Initial, TAPI jari diangkat tanpa lewat `touchSlop` & sebelum long-press = TAP → lompat ke titik sentuh SAAT ANGKAT (`onTapFar` → `snapToStep` B169 + haptic); gerak horizontal-dominan = diblok; vertikal-dominan = scroll lolos; tak ada lompatan saat jari baru mendarat. Fungsi baru `fractionAtX`. **NOT VERIFIED**: kompilasi/lint/detekt belum jalan + device WAJIB; gejala bug HP user BELUM dirinci (lihat RESUME POINT). Sebelumnya: 172, 1 file source (`SkeuomorphicComponents.kt`; request user "Target Batch 172? Slider sentuh-jauh-dari-thumb") — `FeatureControl` + `Modifier.ignoreTouchFarFromThumb` (private; `pointerInput(Unit)` pass `Initial`): down yang mendarat >32dp (horizontal) dari thumb dikonsumsi → tap/onPress Slider M3 mengabaikan (tak ada lompatan); gerak jauh dominan horizontal (setelah `touchSlop`) ikut dikonsumsi, dominan vertikal dibiarkan (scroll halaman jalan). Retry gatekeeper B138 (di-revert B140) dengan hipotesis B140 (key tanpa `value`, `rememberUpdatedState`). **NOT VERIFIED**: kompilasi/lint/detekt belum jalan (sandbox tanpa Gradle) + device WAJIB (user device-in-the-loop). Revert = kembalikan file ke `Boomly_v171.zip`. Sebelumnya: 171, 5 file source (`AudioEnhancerService.kt`, `MainActivity.kt`, `BoosterScreen.kt`, `values/strings.xml`, `values-en/strings.xml`; request user "Terapkan konfigurasi baterai mutakhir dari project lagfix ke Boomly Including fitur recent exclusion!!") — port LagFix: Recents exclusion dinamis (`AudioEnhancerService.syncExcludeFromRecents`) + kartu baterai (status exemption ✓/tombol izin, notice `isBackgroundRestricted` terpisah, catatan Recents). **NOT VERIFIED**: kompilasi/lint/detekt/tes belum jalan (sandbox tanpa Gradle), device belum diuji. Lihat "Keputusan sadar" & RESUME POINT.
  Sebelumnya: 170, 0 file source (docs saja; request user "sudah saya test device, tidak ada keluhan. next") — B169 slider snap kelipatan **DEVICE-VERIFIED oleh user** (tanpa keluhan); laporan static analysis B169 BELUM dibaca (sisi statis B169 belum terverifikasi). Kode TIDAK berubah. Roadmap: 0 item tak-terblokir yang sudah dijadwalkan (Fase 8 "Berikutnya: 8) sesuai kebutuhan user"; semua milestone M0–M9 selesai/BLOCKED/DITURUNKAN) → "next" tak punya target otomatis; 3 kandidat dicatat di RESUME POINT, tunggu pilihan user.
  Sebelumnya: 169, `SkeuomorphicComponents.kt`+`BoosterScreen.kt` (2 file; request user "slider terlalu licin/susah presisi. seharusnya dikasih kelipatan berapa kek tiap digeser!!") — `FeatureControl` dapat param `step: Float = 0f` + helper `private fun snapToStep` (bulatkan ke kelipatan terdekat dari 0, `coerceIn(range)`; `step<=0` = perilaku lama); snap di callback `onValueChange` Slider, callback luar dilewati kalau `snapped == value` (jari di dalam 1 notch). Kelipatan 5 call site: Bass 50 (0..1000), Virtualizer 50 (0..1000), Loudness 50 mB (0..3000), Compressor 5 % (0..100), tiap band EQ 50 mB (levelMin..levelMax) — semua nilai 9 preset built-in (bass/virt/loudness/eqBands) kelipatan 50, jadi preset menempel tepat di notch. SENGAJA BUKAN `Slider(steps=)` (nilai preset/custom off-grid tampil beda dari label) dan BUKAN `pointerInput` (gatekeeper B138→B140 TIDAK disentuh). Kurva EQ (`EqCurveEditor.kt`) TIDAK diubah. Cek statis: brace/paren seimbang, `!!` 0, 5 call site, simulasi rumus snap OK (0 mB EQ kena, ujung range kena). B169 **DEVICE-VERIFIED** (user, Batch 170); statis (lint/detekt) belum dibaca.
  Sebelumnya: 168, 0 file source (docs saja: `PROJECT_STATE.md`, `CHANGELOG.md`; request user "lanjutkan progress!!" = RESUME POINT (a)(i)) — triase `AudioEnhancerPro_static_analysis_v209-run209`: marker OK (BATCH_PROJECT_STATE=167, RUN_NUMBER=209, STEP_DETEKT_LINT/TYPED=success), diag `DETEKT_TYPED_DIAG config=detekt.yml,detekt-typed.yml classpathEntries=56 jvmTarget=17` (benar), `detektDebug` **0 temuan** (21 file/loc 9.271, 0 code smell; `debug.sarif` 0 result), detekt polos 0 (`detekt.sarif` 0 result), lint 0E/1W (hanya `OldTargetApi`; 1 baris `0 errors, 1 warnings`). → B167 **STATIC-VERIFIED** (canary `PagerState` hilang, loc 9.272→9.271 sesuai) = **TRACK JARING VALIDASI SELESAI** (jangan buka lagi tanpa diminta; diag `doFirst` tetap sebagai sentinel). Kode app TIDAK berubah di B168. Device belum diuji.
  Sebelumnya: 167, `OnboardingScreen.kt` (1 file, hapus 1 baris import mati; request user "lanjut" = RESUME POINT (a)(i)) — triase `AudioEnhancerPro_static_analysis_v208-run208`: marker OK (BATCH_PROJECT_STATE=166, RUN_NUMBER=208, STEP_DETEKT_LINT/TYPED=success), diag `DETEKT_TYPED_DIAG config=detekt.yml,detekt-typed.yml classpathEntries=56 jvmTarget=17` → config typed TERPASANG; `detektDebug` **1 temuan = canary** `style UnusedImports` `OnboardingScreen.kt:7:1` (`androidx.compose.foundation.pager.PagerState`), 21 file/loc 9.272, 0 temuan lain (16 rule typed pertama kali jalan, `getValue`/`setValue` TIDAK ter-flag → bukan false positive); detekt polos 0; lint 0E/1W (OldTargetApi). → B166 **STATIC-VERIFIED**, jaring typed terbukti hidup (sisi `detektDebug` B164 ikut STATIC-VERIFIED). Perbaikan B167: hapus `import androidx.compose.foundation.pager.PagerState` (L7); kata `PagerState` utuh tak muncul lagi di seluruh `app/src` (tipe dipakai hanya lewat `rememberPagerState` → inferensi, tanpa import); kode lain/perilaku tak berubah. Diag `doFirst` SENGAJA DIPERTAHANKAN sebagai sentinel (membedakan "0 temuan = bersih" dari "config tak terbaca"). B167 **STATIC-VERIFIED** (run 209: target tercapai persis — diag kedua file, `detektDebug` 0, polos 0, lint 0E/1W, loc 9.271).
  Sebelumnya: 166, `app/build.gradle.kts` (1 file, 0 source app; request user "lanjut" = RESUME POINT (a)) — triase `AudioEnhancerPro_static_analysis_v207-run207`: marker OK (BATCH_PROJECT_STATE=165, RUN_NUMBER=207, STEP_DETEKT_LINT/TYPED=success), detekt polos 0, lint **0E/1W** (hanya `OldTargetApi`; `ApplySharedPref` HILANG) → B165 lint-fix **STATIC-VERIFIED**; `lint-results-debug.sarif` ada → sisi lint B164 STATIC-VERIFIED. **ROOT-CAUSE jaring typed mati**: log `DETEKT_TYPED_DIAG config=detekt.yml classpathEntries=56 jvmTarget=17` → `detekt-typed.yml` TIDAK PERNAH terbaca (type-resolution aktif, classpath 56) — `config.setFrom` di blok `tasks.withType<Detekt>` (B164) tak berefek; "detekt typed 0 temuan" run 206/207 = BUKAN bukti kode bersih. Perbaikan B166: file typed disertakan di `detekt { config.setFrom(listOfNotNull(detekt.yml, typed.yml bila `detektTypedRequested`)) }` (`val detektTypedRequested` = `gradle.startParameter.taskNames` memuat `detektDebug`); `config.setFrom` di blok task DIHAPUS; diag `doFirst` DIPERTAHANKAN. Penyebab pasti override tak dikonfirmasi (DUGAAN: config task mengikuti extension). Cek statis: 16 nama rule `detekt-typed.yml` + 35 rule `detekt.yml` SEMUA ada di daftar 214 rule detekt 1.23.6 (SARIF run 207), 0 overlap. B166 **STATIC-VERIFIED** (run 208: config typed terbaca, canary ter-flag).
  Sebelumnya: 165, `app/src/test/.../PrefsHelperTest.kt` + `app/build.gradle.kts` (2 file, 0 source app; request user "lanjutkan progress!!" = RESUME POINT (a)) — triase `AudioEnhancerPro_static_analysis_v206-run206`: marker OK (BATCH_PROJECT_STATE=164, RUN_NUMBER=206, STEP_DETEKT_LINT=success, STEP_DETEKT_TYPED=success), detekt polos 0, `detektDebug` BUILD SUCCESSFUL 21 file/loc 9.272/0 temuan, `lint-results-debug.sarif` ada, lint 0E/**2W** = `OldTargetApi` (diketahui) + BARU `ApplySharedPref` `PrefsHelperTest.kt:27` (efek `checkTestSources`). **TEMUAN: canary `UnusedImports` `PagerState` (`OnboardingScreen.kt` L7, satu-satunya kemunculan kata itu di source) TIDAK ter-flag → jaring typed BELUM terbukti hidup → B164 tetap NOT VERIFIED.** Perbaikan B165: (1) `@Suppress("ApplySharedPref")` di `clearPrefs()` (commit sengaja sinkron; perilaku tes identik); (2) `doFirst` diagnostik di blok `tasks.withType<Detekt>` → log `DETEKT_TYPED_DIAG config=… classpathEntries=… jvmTarget=…` di `gradle-static-analysis-typed.log`. Import `PagerState` SENGAJA TIDAK dihapus (canary dipakai sampai jaring terbukti). B165 **STATIC-VERIFIED** (run 207: lint 0E/1W; diag mengungkap root-cause B166).
  Sebelumnya: 164, `config/detekt/detekt-typed.yml` (baru) + `app/build.gradle.kts` + `app/lint.xml` + `.github/workflows/build.yml` (4 file, 0 source Kotlin; request user "lanjut tambahkan jaring validasi ketat yang belum terpasang") — jaring validasi BARU, semua NON-BLOCKING: (1) task `detektDebug` (type-resolution) membaca `detekt.yml`+`detekt-typed.yml` (jvmTarget 17), step CI TERPISAH `static_analysis_typed`; task `detekt` polos TIDAK disentuh; (2) lint `sarifReport`+`checkTestSources`; (3) lint.xml `StopShip`=warning; (4) step summary: hitung detekt+typed+lint error, anotasi `::warning::` (tripwire, tidak gagalkan job); marker +`STEP_DETEKT_LINT`/`STEP_DETEKT_TYPED`. Triase `AudioEnhancerPro_static_analysis_v205-run205`: marker OK (PROJECT=AudioEnhancerPro, APPLICATION_ID=com.audioenhancer.booster, BATCH_PROJECT_STATE=163), BUILD SUCCESSFUL 28s, lint 0E/1W (OldTargetApi)/0I, detekt 0/21 file, loc 9.272 = TARGET B163 tepat → B163 **STATIC-VERIFIED** (device belum). B164 **NOT VERIFIED** — run 206: konfigurasi jalan (marker OK, kedua step success) TAPI canary tak ter-flag → jaring typed belum terbukti hidup (lihat Batch 165).
  Sebelumnya: 163, `BoosterScreen.kt` — I/O crash log `CrashBanner` ke `Dispatchers.IO`.
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
- (Batch 174) Perilaku target TERBARU (user, menggantikan B173): tap (bukan drag) TIDAK boleh membuat slider ngikut; drag jauh diblok; scroll tak terganggu; nilai hanya via drag dari thumb. JANGAN mengembalikan tap-melompat tanpa permintaan user baru. B173 (superseded): tap jauh = lompat saat angkat. Gejala bug HP B172 (loncat/macet/scroll) tidak dirinci user → JANGAN menebak perbaikan lain; minta gejala PERSIS (slider mana, posisi thumb, arah jari) bila masih ada.
- (Batch 172) Gatekeeper slider DICOBA LAGI dgn hipotesis di bawah (key `Unit`, `rememberUpdatedState`, konsumsi di pass Initial) — NOT VERIFIED device; kalau malah loncat/blok lagi → REVERT `SkeuomorphicComponents.kt` ke v171, JANGAN tebak ketiga kali tanpa log device.
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

- **Batch 191** (1 file source: `SkeuomorphicComponents.kt`; request user \"ubah slider antar ke-3 tab agar mulus dan mirip persis dengan contoh video saya!! [Berlaku ke semua theme]\"; video referensi = bilah tab app Jam 4 tab, direkam 24 fps): hasil ukur video — lebar pill KONSTAN (tak melar), gerak ease-out tanpa overshoot (fit spring kritis omega ~9-10 = stiffness ~100, ~0,65 dtk/tab), warna label berubah DI TEPI pill (label separuh warna aktif saat pill setengah menutup), pill mengikuti x jari saat drag (selisih <=~20 px) dan jari boleh keluar bar secara vertikal. Implementasi: `SkeuTabBar` = palung + 1 pill timbul (`graphicsLayer.translationX` dari `Animatable` posisi "indeks tab") + `DepthTabLabelRow` x2 (redup di luar pill / `primary` di dalam pill; `depthTabReveal` = `clipPath` Intersect/Difference), `pointerInput` tap/drag (`VelocityTracker`, proyeksi 0,12 dtk, tab tujuan = terdekat), `LaunchedEffect(selectedIndex)` menggerakkan pill utk perubahan dari luar (swipe lintas tab B141). Bobot label SAMA (Bold) di kedua lapisan agar clip pas. Semantik Tab/selected/onClick tetap utk TalkBack. `BoosterScreen.kt` TIDAK berubah (kontrak `SkeuTabBar` sama; `legacy` utuh). Berlaku di keenam tema (semua `depth != null` sejak B187). NOT VERIFIED — belum dikompilasi/lint/detekt (sandbox tanpa Gradle) + device WAJIB. Tuning = `DepthTabSettleSpec` (stiffness 100), `DepthTabFollowSpec` (1200), `DepthTabFlingProjectionSec` (0,12). Revert = `Boomly_v190.zip`.
- **Batch 190** (2 file source: `SkeuomorphicComponents.kt`, `BoosterScreen.kt`; request user \"dibeberapa tema itu lancar jaya, sebagian lagi malah kurang optimal tab equalizer nya!!\" setelah v189; jawaban user: semua 6 tema, terparah Aurora, gejala = scroll patah-patah + buka kartu EQ/ganti tab lambat): (1) `depthDraw` tak lagi `composed` — `DepthDrawBlock` (equals = kunci) dipasang langsung ke `drawWithCache` (Compose UI 1.6.8 = `ModifierNodeElement`), 0 materialisasi/slot `remember`; (2) `SkeuSliderTrack` `background(floor, shape)` + `SkeuSliderThumb` tanpa `clip` (−2 layer per slider, visual sama); (3) `EqualizerSection(bands: EqualizerBands)` — `@Immutable data class` membungkus `List` agar kartu EQ di-skip saat `TabPageContent` merekomposisi (drag Bass/Virtualizer/Loudness/Compressor). Tidak ada kode khusus Aurora: profil depth Aurora = Midnight (beda warna saja). NOT VERIFIED — belum dikompilasi/lint/detekt (sandbox tanpa Gradle) + device WAJIB.
- **Batch 189** (4 file source: `BoosterViewModel.kt`, `SkeuomorphicComponents.kt`, `BoosterScreen.kt`, `EqCurveEditor.kt`; request user "Nope, gejala masih kambuh. Perluas optimalisasi!!" setelah v188 — gejala spesifik BELUM dirinci, jadi diserang kandidat biaya terbesar): (1) `BoosterViewModel`: `setEqualizerBand` tak lagi memanggil Service di Main — nilai terbaru per band ke `eqLatestMb` (`ConcurrentHashMap`) + kick `Channel.CONFLATED`, 1 worker `Dispatchers.IO` mengosongkan map (`applyLatestEqualizerBands`; Service putus = nilai tertahan, di-kick lagi di `onServiceConnected`); `service` jadi `@Volatile`; `Service`/`PrefsHelper` 0 berubah (apply binder + tulis prefs kini di IO); (2) `SkeuSliderTrack` jalur depth: isian digambar `drawBehind` membaca `sliderState.value` di fase gambar (`sliderFraction`), `floor`/`fillBrush` di-`remember` → 0 rekomposisi/re-layout track per tick (jalur legacy tak berubah); (3) `graphicsLayer()` = layer render sendiri untuk tiap band (`EqBandSlider` dibungkus `Box`), kanvas `EqCurveEditor`, dan `SpectrumBars` (update ~20x/detik tak lagi merekam ulang layer kartu). NOT VERIFIED — belum dikompilasi/lint/detekt (sandbox tanpa Gradle) + device WAJIB.
- **Batch 188** (2 file source: `EqCurveEditor.kt`, `BoosterScreen.kt` blok `EqualizerSection` + `EqBandSlider` baru; request user "kali ini yang dibutuhkan itu optimalisasi. Especially dibagian tab equalizer manual!!"): (1) `EqCurveEditor` snap `EQ_CURVE_STEP_MB`=50 (= `step` slider B169) + dedupe (lewati bila notch sama) → `Equalizer.setBandLevel` (binder, Main) + tulis prefs `eq_band_N` + `onActivePresetChange` turun dari per-1 mB ke per-notch; (2) `EqualizerSection`: `allFlat` = `derivedStateOf`, slider band diekstrak `EqBandSlider` (scope recompose sendiri) → drag tak recompose seluruh kartu; (3) `EqCurveEditor`: Path/Paint/Brush/PathEffect/Stroke/label/array titik di-`remember` (0 alokasi per frame draw); (4) BUG: `pointerInput(bandCount)` menangkap `levels` basi setelah reset/preset (list baru per `resetKey`) → hit-test titik meleset; kini via `rememberUpdatedState`. 0 file Service/Prefs/ViewModel berubah. NOT VERIFIED — belum dikompilasi/lint/detekt (sandbox tanpa Gradle) + device WAJIB.
- **Batch 187** (3 file source: `Theme.kt`, `SkeuomorphicComponents.kt`, `MainActivity.kt`; request user "Alhamdulillah, gak nge crash lagi. Next terapkan konfigurasi ini ke semua theme yang masih flat!!" — "konfigurasi" = mesin depth Old Money B180-B186): `DepthStyle` +`corner` (`DepthCorner` CHAMFER/ROUND), `keyCorner`, `panelCorner`, `backdropGrain` (default = nilai Old Money → `OldMoneyDepth` tak berubah); 5 profil baru diisi ke `depth` token Midnight/Aurora/Neumorphism/StudioEq/Serene (definisi WAJIB di atas `*SkeuTokens`). Mesin: bevel pelat bulat `depthRoundFacets`/`depthRoundFrame`, kunci/tab/dialog lewat `depthCornerShape` (Old Money & Serene tetap chamfer), `skeuBackdropGrain(alpha)` + `MainActivity` ikut `depth.backdropGrain`. `frameInset` 0.dp di 5 tema baru (alur ukir = ciri Old Money). **NOT VERIFIED**: belum dikompilasi/lint/detekt; warna/alpha profil baru hasil simulasi statis, belum dilihat di HP; Serene `topEnd` 20dp vs bevel chamfer 18dp = selisih 2dp (diterima). Revert = `Boomly_v186.zip`.
- **Batch 186** (1 file source: `SkeuomorphicComponents.kt`, `depthDraw`; request user "Again?!!" + `crash_20261009_042820_*.txt`): log baru = `ClassCastException E1.i1 → S1.c` IDENTIK dgn B185 dan bertanda **Version: 224** = build B184 yg SAMA (urutan run: B180=220 … B184=224; fix B185 akan jadi run >= 225) → BUKAN regresi fix B185, user masih menjalankan build 224. Karena crash berulang, ditambah lapis pengaman: `key(tag) { remember(keyList) { block } }` di `depthDraw` (grup compose terpisah per jenis modifier). Tak ada perubahan visual. NOT VERIFIED — belum dikompilasi/direproduksi; konfirmasi: crash log baru harus bertanda versi >= 225. Catatan: CI membangun debug dgn `isMinifyEnabled = true` → trace ter-obfuscate; sertakan `mapping.txt` bila crash berlanjut.
- **Batch 185** (1 file source: `SkeuomorphicComponents.kt`, fungsi `depthDraw` + 7 pemanggilnya; request user "Fix it!!" + 3 file `crash_*.txt`): (1) crash v224 `ClassCastException: E1.i1 cannot be cast to S1.c` (main thread, saat composition, tumpukan `materialize`/`foldIn` → lambda `composed`): AKAR = B184 memakai `remember(*keys)` (vararg = 1 slot PER kunci) di dalam SATU lambda `composed` yang dibagi SEMUA modifier `depth*` → grup compose sama; saat rantai modifier berganti varian di posisi yang sama (kunci ditekan: `depthCastShadow` ↔ `depthWellLip`, tab/pil terpilih, dst) jumlah slot beda → slot lambda terbaca sebagai kunci lama → cast `as T` gagal. FIX: kunci digabung jadi SATU `List` (`remember(keyList)`, slot tetap) + kunci pertama = `tag` unik per jenis modifier ("castShadow", "wellInner", "wellLip", "plateSurface", "dome", "ringRim", "pillRim"). Hanya jalur `composed` B184 yang berubah; tampilan sama. (2) 2 log `Invalid token LIMIT` (versi 1.67, 7 Agu) = bug LAMA yang SUDAH diperbaiki Batch 29 (v1.68; `sortOrder` tanpa `LIMIT`, lihat CHANGELOG) — grep kode sekarang: 0 pemakaian `LIMIT` di `ContentResolver.query`; TIDAK ada perubahan. NOT VERIFIED — belum dikompilasi; stack R8 tak ter-deobfuscate (tak ada mapping.txt) → akar disimpulkan dari struktur tumpukan + diff B184, belum direproduksi.
- **Batch 184** (4 file source: `SkeuomorphicComponents.kt`, `BoosterScreen.kt`, `SettingsScreen.kt`, `OnboardingScreen.kt`; request user "Tuntas kan yang belum terpoles. Dan jangan lupakan optimalisasi biar rendering efek gak ngebebanin prosesor device!!"): (A) TUNTAS: komponen yang masih datar kini fisik di Old Money lewat wrapper `SkeuButton`/`SkeuOutlinedButton`/`SkeuTextButton`/`SkeuIconButton`/`SkeuAlertDialog`/`SkeuTabBar`/`SkeuPresetPill` + `skeuFieldColors()` (kunci `DepthKeyBox`: diam timbul, ditekan cekung; tombol utama = enamel burgundy; tab = palung cekung + tab terpilih timbul; dialog = pelat chamfer + bayangan + bevel/grain/alur ukir di atas permukaan; kolom isian = wadah lantai sumur); call site `Button(`/`TextButton(`/`OutlinedButton(`/`IconButton(`/`AlertDialog(` di Booster/Settings/Onboarding di-rename mekanis (hanya pakai `onClick`/`modifier`/`enabled`), `FilterChip` Settings & `ScrollableTabRow` & chip Preset dibungkus `legacy` lambda; semua wrapper `depth == null` → komponen Material lama persis (5 tema lain 0 berubah). (B) OPTIMASI: (1) template bayangan 9-slice dipakai BERSAMA semua kartu besar (8 `drawImage`, tengah meregang; simulasi: selisih alpha maks 3% / rata-rata 0.2% vs render langsung) → 0 blur per kartu & 0 render ulang saat ukuran berubah; elemen kecil/pendek (< 88x104dp) bitmap per-ukuran; (2) cache GLOBAL `DepthBitmapStore` LRU 48 entri (elemen berukuran sama berbagi bitmap, memori terbatas); (3) `depthDraw` = `remember(keys){block}` → lambda `drawWithCache` stabil, TIDAK dibangun ulang/invalidate tiap rekomposisi (drag slider merekomposisi seluruh layar); (4) permukaan pelat (gradien+kilau+peredupan) di-bake ke tekstur 48x48 bersama → 1 `drawImage` ganti 4 fill gradien layar penuh per kartu; (5) tanpa alokasi di lambda gambar. Perubahan visual yg disengaja: gradien permukaan kini aspect-independent (bake), `DepthStyle`/token tak berubah. NOT VERIFIED — belum dikompilasi/lint/detekt; beban CPU/GPU belum diukur di device (hanya hitungan desain + simulasi 9-slice).
- **Batch 183** (2 file source: `SkeuomorphicComponents.kt`, `BoosterScreen.kt`; request user "kenapa pil preset cepat masih flat?!!" + `Screen_Recording_20261008_172913.mp4`): root cause = pil "Preset Cepat" adalah `FilterChip` (+ `AssistChip` "Simpan") Material3 yang TIDAK PERNAH disambungkan ke mesin `depth*` B180-B182 (hanya `SkeuCard`/track/switch/knob/power button) → unselected = outline tipis datar, selected = fill datar + glow. Fix: `SkeuPresetPill` (+`depthPillRim`, `DepthPillShadow`) — `depth != null`: pil diam = TIMBUL (bayangan jatuh + permukaan pelat + rim stadium: garis lurus atas terang/bawah gelap + busur ujung bergradien searah cahaya); terpilih = CEKUNG enamel burgundy bergradasi (lerp(`wellFloor`, primary, 0.72) + bayangan dalam + bibir), tanpa `skeuGlow`; ditekan = cekung sesaat; semantik `selectable`/`Role.RadioButton`; `Row` pil diberi `padding(vertical = 7.dp)` HANYA saat `depth != null` (horizontalScroll meng-clip bayangan). `depth == null` (5 tema lain) → `legacy()` = blok FilterChip/AssistChip lama dibungkus apa adanya. Tab Kontrol/Tampilan/Bantuan TIDAK disentuh (di luar request). NOT VERIFIED — belum dikompilasi; tampilan belum diuji.
- **Batch 182** (3 file source: `SkeuomorphicComponents.kt`, `Theme.kt`, `MainActivity.kt`; request user "Excellent, kalau bisa dipoles lagi udah jadi masterpiece!!" — user menilai hasil B181 excellent; log CI/static-analysis TIDAK dilampirkan): polish mesin depth Old Money: (1) pelat: kubah halus (vignette kanan-bawah + kilau radial kiri-atas) + alur ukir bingkai cekung `DepthStyle.frameInset` 5dp (garis gelap + garis gading tergeser kanan-bawah, `depthOctagon`); (2) sumur: lapis oklusi ambien merata di `renderDepthWellInner`; (3) knob: titik kilau specular di `depthDome`; (4) isian slider = gradien vertikal enamel; (5) latar layar Old Money diberi butiran halus (`skeuBackdropGrain`, dipanggil di `MainActivity.kt` hanya untuk `OLD_MONEY`); (6) resolusi bitmap bayangan naik (pelat 0.25→0.3333, sumur/knob 0.5→0.75) = kontak lebih tajam. `DepthStyle` +field `frameInset` (hanya `OldMoneyDepth` yang memakai). NOT VERIFIED — belum dikompilasi/lint/detekt; tampilan hanya simulasi statis.
- **Batch 181** (1 file source: `SkeuomorphicComponents.kt` — hapus 1 baris import; request user "Fix it immediately!!" + `log_fail_v220-debug-run220`): CI run 220 `compileDebugKotlin` GAGAL dgn 1 error: `SkeuomorphicComponents.kt:96:37 Unresolved reference: asFrameworkPaint` — `asFrameworkPaint()` adalah MEMBER interface `Paint` (dipanggil `paint.asFrameworkPaint()` tanpa import), BUKAN fungsi top-level `androidx.compose.ui.graphics.asFrameworkPaint`. Fix: hapus `import androidx.compose.ui.graphics.asFrameworkPaint`; 3 pemanggilan (`renderDepthCastShadow`, `renderDepthWellInner` x2) tak diubah. Log hanya memuat 1 error & 0 warning → sisa kode B180 lolos fase resolve. NOT VERIFIED sampai CI hijau (build ulang).
- **Batch 180** (2 file source: `Theme.kt` — `data class DepthStyle`, `SkeuTokens.depth: DepthStyle? = null`, `OldMoneyDepth`, palet pelat `2A241F→1F1B18`, `OldMoneyWellFloor` `14100D`; `SkeuomorphicComponents.kt` — mesin `depth*` + cabang `depth != null` di `SkeuCard`/`SkeuTintedCard`/`SkeuPowerButton`/`SkeuSliderTrack`/`SkeuSliderThumb`/`SkeuSwitch`/`SkeuGroupDivider`; +`README.md`): root cause keluhan "nyaru" = sorot 2% + bayangan 32% sehue latar + permukaan ±3 level (B179). Fix: kedalaman dari luminansi (pelat > latar > lantai sumur) + bevel facet 8 sisi (dot normal·cahaya) + bayangan Gaussian software-bitmap 1/4 res (cache per call-site, bukan per rekomposisi) + grain 128px; sumur = bayangan dalam + bibir terang `DstOut`-mask. Backward-compat: field `depth` default null, semua cabang lama utuh. NOT VERIFIED — Gradle/kotlinc tak ada; tampilan hanya disimulasikan statis (Python), BELUM di HP.
- **Batch 179** (1 file source: `Theme.kt` blok `OldMoney*`; +1 frasa `README.md`; request user "Adaptasi panduan neumorphism dark mode pada tema old money!!"): panduan diadaptasi lewat mesin Neumorphism yang SUDAH ada (`SkeuDualDirectionalShadow` membaca token) — 0 file komponen disentuh, 5 tema lain 0 berubah. `OldMoneyCardBrush` = gradien diagonal `211D1A→1B1714` (±3 dari base `1E1A17`); `shadowLightTint` gading `0x05F2EADB` / `shadowDarkTint` `0x52080605`, alpha dikalibrasi thd akumulasi 5 lapis concentric-fade (tepi ≈59% gelap / ≈5% terang, ≈29% di 8dp, ≈5% di 14dp ≈ kurva blur panduan); `cardElevation` 3→12dp; `OldMoneyBorderBrush` bevel miring; latar naik (stop bawah `0C0A09`→`171310`, panduan: 121212–22252d). Kontras burgundy di kartu 3.14–3.34:1 (B178: 3.06). Statis: kurung/komentar seimbang, urutan init `val` OK. Kompilasi/lint/detekt/tampilan **NOT VERIFIED**; revert = `Boomly_v178.zip`.
- **Batch 178** (1 file source: `Theme.kt` blok `OldMoney*`; +1 frasa `README.md`; request user "setelah balance malah kurang, ubah warna sekunder ke yang lebih calm tapi tetap kuat aura old money"): emas antik `0xFFC8A96A` (kuning terang, ramai) diganti hijau sage-hunter kalem `0xFF8FA58E` (6.17:1 di kartu; pasangan klasik burgundy ala klub/Ivy); rename `OldMoneyGold/GoldDeep` → `OldMoneySecondary/SecondaryDeep`; container sekunder hunter gelap `0xFF243A2D`, `onSecondary` `0xFF121C16`, `onSecondaryContainer` `0xFFD6E4D4`; hairline kartu sage 40% (`0x668FA58E`); `primaryGlow`, `sliderKnobHighlight`, `surfaceTint` = champagne `OldMoneyTextSecondary` (kilau mutiara/glow hangat, hindari burgundy+hijau bentrok di komponen kecil). Permukaan netral & primary `0xFFB4455A` B177 TIDAK diubah. Validasi sandbox: kurung seimbang, 0 sisa nama `OldMoneyGold`, def/ref identifier konsisten; kompilasi/lint/detekt **NOT VERIFIED**; HP belum diuji. Alternatif bila sage kurang cocok: camel/pewter netral (1 batch, ubah HANYA `OldMoneySecondary*` + hairline).
- **Batch 177** (1 file source: `Theme.kt` blok `OldMoney*`; request user "primary terlalu mendominasi, kasih balance"): root cause — latar/kartu espresso-BURGUNDY (kemerahan) + 32 pemakaian `colorScheme.primary` (ikon/switch/slider/label) + `secondary`/`surfaceTint` TIDAK dipakai kode app manapun, jadi emas nyaris tak terlihat. Fix tema-only (5 tema lain & komponen 0 berubah): permukaan `0xFF14110F/1A1613/241F1B` netral, primary `0xFFB4455A` (3.06:1 di kartu, onPrimary 4.66:1), `primaryContainer` `0xFF52202C`, `primaryGlow` = emas (glow tekan/terpilih), `cardBorder` emas 50%, teks sekunder champagne `0xFFD2C6A8`, muted `0xFFA29579`, `surfaceTint` emas, outline `0xFF4A3F36`. Validasi sandbox: kurung seimbang, semua nilai palet terpasang; kompilasi/lint/detekt **NOT VERIFIED**; HP belum diuji. Opsi lanjutan bila masih dominan: token baru agar switch/slider/ikon aktif bisa emas (ubah `SkeuTokens` semua instance + `SkeuomorphicComponents.kt` — butuh keputusan user).
- **Batch 176** (6 file source: `Theme.kt`, `PrefsHelper.kt`, `MainActivity.kt`, `BoosterScreen.kt`, `strings.xml` ID+EN; +`README.md`; request user "opsi tema baru ... old money, primary burgundy + secondary matching"): varian ke-6 `OLD_MONEY` mengikuti pola Serene (B111): palet `OldMoney*` (burgundy `0xFFB83F57`, emas `0xFFC8A96A`, gading, latar espresso), `OldMoneySkeuTokens` (kartu solid + hairline emas + knob emas, `cardShape` chamfer 8dp), `OldMoneyDarkColors`, `OldMoneyTypography` (8 slot, `FontFamily.Serif`, headlineSmall italic, label tracking 1.0-1.2sp), `OldMoneyShapes`; wiring 4 `when` di `AudioEnhancerTheme` + `MainActivity` (mapping + brush) + const `APP_THEME_OLD_MONEY` + 1 kartu toggle di `BoosterScreen` (ikon `WorkspacePremium`) + string ID/EN. Kontras WCAG dihitung statis (primary/kartu 3.15, onPrimary 4.73, emas/kartu 7.57, muted/kartu 5.43). Validasi sandbox: kurung seimbang 4 kt, XML valid, paritas string 197/197, semua `R.string` ada, urutan init top-level OK. Gradle/kotlinc TIDAK ada → kompilasi/lint/detekt **NOT VERIFIED**; HP belum diuji.
- **Batch 175** (0 file source; docs `PROJECT_STATE.md`+`CHANGELOG.md`; request user "Terapkan panduan konfigurasi tersebut!!" = `Tinjauan_Solusi_AudioEnhancer.md`): audit 7 modul vs kode v174 → 0 diterapkan. Manifest panduan (FGS `mediaProcessing`, buang INTERNET/QS tile/widget/BootReceiver) & `file_paths.xml` (`audio_exports/` menimpa `updates/` → in-app update `UpdateManager` gagal) = regresi; Service: tak ada `ACTION_HEADSET_PLUG`, leak sudah ditangani `AudioDeviceCallback` (B82, unregister di `onDestroy`), respawn `onTaskRemoved` sengaja dihapus; `WatchdogAlarmReceiver` backoff alarm = bentrok SOP (dilarang custom watchdog; hanya WorkManager) + B132 (exact alarm OFF); `PrefsHelper` panduan tak kompilasi (`context` bukan properti) & kurva EQ sudah per-band `eq_band_$band`; `EqCurveEditor` panduan buang gradient/label/hit-test B137; `SkeuomorphicKnob` tak ada di Boomly (UI = slider `thumbOnlyDrag`, B174). Status: dokumen saja, kode identik v174.
- **Batch 174** (`SkeuomorphicComponents.kt` — 1 file; request user "di tap bukan di drag itu malah ngikut. Fix regresi"): tap TIDAK lagi menggeser slider (hapus tap-jauh-melompat B173, termasuk tap dekat thumb yang di M3 menggeser via `pressOffset`); `thumbOnlyDrag` menangani semua sentuhan: drag relatif dari thumb (tanpa lompatan awal), jauh diblok, vertikal lolos. Wiring `FeatureControl`: `latestStep`/`latestOnDrag`/`latestOnDragEnd`. `Slider` M3 tetap dirender (visual/semantics/keyboard), callback `onValueChange` Slider tak diubah. Statis: kurung seimbang, tak ada sisa nama lama. Kompilasi/lint/detekt/device **NOT VERIFIED**. Keputusan B173 "tap track jauh boleh" DIGANTI oleh keluhan terbaru user.
- **Batch 173** (`SkeuomorphicComponents.kt` — 1 file; request user "bug di HP (loncat, macet, scroll)" + "tap track jauh boleh, tapi drag jauh diblok"): gate B172 ditulis ulang — far-down dikonsumsi (Initial), tap = up sebelum `touchSlop`/long-press → `onTapFar(start + fraksi*span)` (snap B169 + haptic), horizontal-dominan dikonsumsi, vertikal lolos; posisi/jarak dihitung di ruang fraksi (RTL otomatis). `FeatureControl` + `tapFarHandler`/`latestTapFar`. Statis: kurung seimbang, tak ada key `value`. Kompilasi/lint/detekt/device **NOT VERIFIED**; user belum merinci gejala bug.
- **Batch 172** (`SkeuomorphicComponents.kt` — 1 file; request user "slider sentuh-jauh-dari-thumb"): `ignoreTouchFarFromThumb` dipasang di `Slider` `FeatureControl` (setelah `.padding`, sebelum `.semantics`); konstanta `SkeuSliderThumbSize` 22dp (dipakai thumb + hitung posisi thumb) & `SliderGrabRadius` 32dp; thumb dekat = tak disentuh, jauh = down dikonsumsi di pass Initial; RTL dihitung. B169 snap & `EqCurveEditor.kt` tak disentuh. Statis: kurung 55/55 & 244/244, API Compose BOM 2024.06 (`awaitEachGesture`, `awaitFirstDown(pass=)`, `consume()`) valid. Kompilasi/lint/detekt/device **NOT VERIFIED**.
- **Batch 171** (`AudioEnhancerService.kt`+`MainActivity.kt`+`BoosterScreen.kt`+`strings.xml` ID/EN — 5 file; request user "Terapkan konfigurasi baterai mutakhir dari project lagfix ke Boomly Including fitur recent exclusion!!"): port LagFix v133 (`syncExcludeFromRecents`, exclude = `isRunning`, dipanggil di start/stop Service + `onResume`) + kartu baterai (status exemption, notice pembatasan latar belakang, catatan Recents). 0 dependency/izin/manifest/file baru; `build.yml` & versi TAK disentuh.
  Validasi sandbox: kurung seimbang 3 file kt, XML valid, paritas string ID/EN, semua `R.string` terpakai ada. Gradle/kotlinc TIDAK ada → kompilasi/lint/detekt/tes **NOT VERIFIED**; device belum diuji.
- **Batch 170** (0 file source; docs `PROJECT_STATE.md`+`CHANGELOG.md`; request user "sudah saya test device, tidak ada keluhan. next"): catat B169 DEVICE-VERIFIED (user). Cek roadmap: tak ada item tak-terblokir terjadwal → 3 kandidat di RESUME POINT, menunggu pilihan user. Status: dokumen saja.
- **Batch 169** (`SkeuomorphicComponents.kt`+`BoosterScreen.kt` — 2 file; request user "slider terlalu licin/susah presisi... kelipatan berapa kek tiap digeser"): `FeatureControl` + `step` & `snapToStep` (callback, bukan `steps=`/`pointerInput`); `step` Bass 50, Virtualizer 50, Loudness 50 mB, Compressor 5, EQ band 50 mB di 5 call site `BoosterScreen.kt`. Statis: brace/paren seimbang, `!!` 0, simulasi snap OK, kurva EQ/gatekeeper tak disentuh. Status: **DEVICE-VERIFIED** (user, B170); statis belum dibaca.
- **Batch 168** (0 file source; docs `PROJECT_STATE.md`+`CHANGELOG.md`; request user "lanjutkan progress!!" = RESUME POINT (a)(i)): triase `AudioEnhancerPro_static_analysis_v209-run209` (marker OK BATCH=167 RUN=209, kedua step success, diag `config=detekt.yml,detekt-typed.yml` classpath 56, `detektDebug` 0 temuan 21 file loc 9.271, detekt polos 0, lint 0E/1W = hanya OldTargetApi) → B167 STATIC-VERIFIED, track jaring validasi SELESAI. Tak ada perubahan kode/build/CI. Status: **STATIC-VERIFIED** (laporan CI); device belum.
- **Batch 167** (`OnboardingScreen.kt` — 1 file; request user "lanjut"): triase `AudioEnhancerPro_static_analysis_v208-run208` (marker OK BATCH=166, kedua step success, diag `config=detekt.yml,detekt-typed.yml`, classpath 56, lint 0E/1W, detekt polos 0). `detektDebug` tepat 1 temuan = canary `UnusedImports` `OnboardingScreen.kt:7` → jaring typed terbukti hidup, B166 STATIC-VERIFIED. Perbaikan: hapus import mati `androidx.compose.foundation.pager.PagerState` (1 baris). Cek statis: `PagerState` (kata utuh) 0 kemunculan tersisa di `app/src`; hanya 1 baris berubah di file itu; file lain selain docs tak berubah. Diag `doFirst` dipertahankan (sentinel). Status: **STATIC-VERIFIED** (run 209, Batch 168).
- **Batch 166** (`build.gradle.kts` — 1 file; request user "lanjut"): triase `AudioEnhancerPro_static_analysis_v207-run207` (marker OK BATCH=165, kedua step success, detekt polos 0, lint 0E/1W = hanya OldTargetApi) → B165 STATIC-VERIFIED. Diag `DETEKT_TYPED_DIAG config=detekt.yml` → `detekt-typed.yml` tak pernah terbaca sejak B164 (typed 0 temuan = tak terukur, bukan bersih). Perbaikan: file typed ikut `detekt { config }` hanya saat invokasi meminta `detektDebug`; `config.setFrom` di blok task dihapus. Cek statis: 51 nama rule (16 typed + 35 lama) semua ada di 214 rule SARIF, 0 overlap; brace/paren seimbang, `!!` 0, `app/src/main` identik dengan v165. Status: **NOT VERIFIED**.
- **Batch 165** (`PrefsHelperTest.kt`+`build.gradle.kts` — 2 file; request user "lanjutkan progress!!"): triase `AudioEnhancerPro_static_analysis_v206-run206` (marker OK BATCH=164, STEP_DETEKT_LINT/TYPED=success, detekt polos 0, `detektDebug` 0/21 file loc 9.272, lint 0E/2W = OldTargetApi + baru `ApplySharedPref` `PrefsHelperTest.kt:27` dari `checkTestSources`). Canary `PagerState` (`OnboardingScreen.kt` L7) TIDAK ter-flag → jaring typed BELUM terbukti hidup, B164 tetap **NOT VERIFIED**; import TIDAK dihapus. Perbaikan: `@Suppress("ApplySharedPref")` di `clearPrefs()` (commit sinkron disengaja) + `doFirst` diagnostik di blok `tasks.withType<Detekt>` (log `DETEKT_TYPED_DIAG`). Validasi statis (skrip): brace/paren seimbang di 2 file, `!!` 0 baru, source app tak berubah (loc 9.272). Status: **NOT VERIFIED**.
- **Batch 164** (`detekt-typed.yml`+`build.gradle.kts`+`lint.xml`+`build.yml` — 4 file target, 0 source Kotlin; request user "lanjut tambahkan jaring validasi ketat yang belum terpasang"): triase `AudioEnhancerPro_static_analysis_v205-run205` (marker OK, compile UP-TO-DATE lolos, lint 0E/1W/0I, detekt 0/21 file, loc 9.272) → B163 STATIC-VERIFIED. Celah jaring (audit): detekt polos tanpa type-resolution (rule `[TR]` diam-diam mati), tanpa SARIF lint, `src/test` tak dilint, `StopShip` mati. Dipasang: `detektDebug` + 16 rule baru (potential-bugs 10, coroutines 1, performance 3, style 2; lihat file), lint `sarifReport`/`checkTestSources`/`StopShip`=warning, summary+tripwire, marker +2 baris outcome. Tidak ditambah (tak pasti nama/perilaku, tak bisa dicek tanpa Gradle): `ForbiddenMethodCall`, `CastNullableToNonNullableType`, plugin detekt pihak ketiga. Validasi statis (skrip): YAML `build.yml`+`detekt-typed.yml` valid, step summary disimulasikan `bash -e` di 4 skenario (rc=0), sumber Kotlin tak berubah (loc 9.272). Status: **NOT VERIFIED**.
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
- **Compose API: jangan import dari ingatan.** `Paint.asFrameworkPaint()` = MEMBER (tanpa import; B180 salah
  meng-import-nya → CI run 220 gagal "Unresolved reference"). Sebelum menambah `import androidx.compose...` baru,
  pastikan simbolnya benar-benar top-level (bukan member/extension di tempat lain) — atau pakai simbol yang SUDAH
  diimpor & terbukti compile di file lain proyek ini.
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

- [x] Slider snap kelipatan (Batch 169, kode SELESAI, DEVICE-VERIFIED user B170 tanpa keluhan; statis belum dibaca). Item di bawah (gatekeeper) BEDA & tetap backlog.
- [~] Proteksi sentuh-jauh-dari-thumb di slider (Bass/Virtualizer/Loudness/
  Compressor/EQ band) — dicoba Batch 138, DI-REVERT Batch 140 (regresi:
  malah loncat ke 0, bukan diblok); DICOBA LAGI Batch 172, DISEMPURNAKAN Batch 173 (tap jauh melompat → REGRESI "ngikut"), DIPERBAIKI Batch 174 (tap tak mengubah nilai; hanya drag dari thumb; NOT VERIFIED, menunggu device user). Backlog, BUTUH device-in-the-loop kalau
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
  (Batch 50) · detekt + lintDebug terfokus NON-BLOCKING (Batch 142, CI jalan hijau run 189) · detektDebug typed + lint SARIF (Batch 164, NOT VERIFIED)
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
(B131/B132) · `HorizontalPager`/auto-height (B105) · retry gatekeeper slider tanpa device-in-the-loop (B140; B172 = percobaan atas permintaan user, hasil device user menentukan) · fade-out Timer Tidur (BLOCKED, skip user)
· Fase 4 & kandidat Fase 7 D/E/F tanpa arahan user · per-app profile, Android Auto/Wear · upgrade AGP/Kotlin/BOM.

---

[RESUME POINT: [Slider antar 3 tab meluncur mulus (mengikuti video user), SEMUA 6 tema, Batch 191; ZIP `Boomly_v191.zip`, 1 file source `SkeuomorphicComponents.kt` (blok `SkeuTabBar`, `DepthTabLabelRow`, `depthTabReveal`, `DepthTabSettleSpec`/`DepthTabFollowSpec`/`DepthTabFlingProjectionSec`)] -> [kode selesai; NOT VERIFIED: belum dikompilasi/lint/detekt (sandbox tanpa Gradle), perilaku & tampilan di HP belum diuji; `BoosterScreen.kt` tak berubah] -> [(1) DAILY UPDATE v191 lalu build CI; bila MERAH cek kandidat B191 DULU di `SkeuomorphicComponents.kt`: import baru (`Animatable`, `spring`, `selectableGroup`, `ClipOp`, `graphicsLayer`, `withTransform`, `VelocityTracker`, `semantics.role/selected/onClick/clearAndSetSemantics`, `kotlinx.coroutines.launch`), `depthTabReveal` (`drawWithCache` + `onDrawWithContent` + `withTransform { translate; clipPath(path, ClipOp) }`, `Outline.toDepthPath()`), `DepthTabLabelRow` (`Modifier.semantics(mergeDescendants = true) { role; selected; onClick { …; true } }`, `Modifier.weight` di `RowScope`), `SkeuTabBar` (`by rememberUpdatedState` dipanggil sbg fungsi, `Modifier.matchParentSize()`, `fillMaxWidth(1f / count)`, `graphicsLayer { translationX = position.value * size.width }`, `try/finally` di `awaitEachGesture`); 1 temuan = 1 batch, ubah HANYA baris yang dilaporkan; (2) uji device B191 (user, SEMUA 6 tema): tap tab lain → pill MELUNCUR mulus ~0,65 dtk tanpa mental/overshoot, lebar pill tetap; warna label berganti DI TEPI pill (label separuh warna saat pill setengah menutup), bukan sekali ganti; drag pill kiri/kanan → pill ikut jari, lepas → snap ke tab terdekat (flick pendek pindah tab); jari boleh keluar bar secara vertikal saat drag; geser VERTIKAL dari bar → halaman tetap scroll & tab tak berubah; swipe lintas tab di konten (B141) → pill ikut meluncur; ganti tab via Pengaturan/rotasi → pill langsung di tab benar (tanpa animasi liar); tinggi bilah SAMA seperti sebelumnya (42dp + palung 3dp), label tak kepotong (Tampilan/Bantuan) di layar sempit; konten tab berganti saat pill mulai bergerak (tanpa patah). GEJALA GAGAL → catat PERSIS (tema, HP, aksi) lalu REVERT `SkeuomorphicComponents.kt` ke `Boomly_v190.zip`; jangan tebak. Tuning: lebih cepat/lambat = `DepthTabSettleSpec.stiffness` (100; naik = cepat), ikut jari kurang/terlalu licin = `DepthTabFollowSpec` (1200), flick = `DepthTabFlingProjectionSec` (0,12); jangan ubah bobot font antar lapisan label & jangan kembali ke kunci per-tab. Kandidat lanjutan (HANYA bila diminta user): haptic tick saat pill melewati batas tab, RTL (translasi dibalik), efek tekan/skala pill saat disentuh]. SEBELUMNYA (B190): [Optimalisasi tab Kontrol/Equalizer lintas SEMUA tema, Batch 190; ZIP `Boomly_v190.zip`, 2 file source] -> [kode selesai; NOT VERIFIED: belum dikompilasi/lint/detekt, perilaku di HP belum diuji; gejala user: kurang optimal di SEMUA 6 tema, terparah Aurora; scroll tab patah-patah + buka kartu EQ/ganti tab lambat; TIDAK ada kode khusus Aurora] -> [(1) DAILY UPDATE v190 lalu build CI; bila MERAH cek kandidat B190 DULU: `SkeuomorphicComponents.kt` `DepthDrawBlock` (kelas `: (CacheDrawScope) -> DrawResult` dipakai sbg argumen `drawWithCache`; bila tipe ditolak kompiler → ganti jadi lambda `{ scope -> scope.block() }` dan bungkus equals lewat `ModifierNodeElement` sendiri), `.background(floor, shape)` di `SkeuSliderTrack`, `BoosterScreen.kt` `EqualizerBands` + parameter `bands` di `EqualizerSection` (anotasi `@Immutable`); (1b) uji device B190 (user): scroll tab Kontrol mulus?, buka kartu EQ & ganti tab lebih cepat?, thumb/track slider TAMPIL SAMA (bulat, tanpa tepi aneh) di 6 tema, EQ tetap sinkron dgn preset/Reset/slider lain; bila MASIH patah: user cek apakah menguji APK DEBUG (debug Compose jauh lebih lambat; bandingkan APK release) + sebut tema & HP; kandidat berikutnya (1 per batch): (i) `TabPageContent` merekomposisi SELURUH tab tiap drag Bass/Virtualizer/Loudness/Compressor (state dibaca langsung di scope tab — pecah per `FeatureControl`), (ii) biaya gambar pelat per frame: bevel ROUND 20 `drawPath` vs chamfer 8 (`depthPlateSurface`), (iii) `Column.clip(shape)` kartu besar (Outline.Generic di Serene/Old Money); lalu kandidat B189: `BoosterViewModel.kt` (`Channel.receiveCatching().isClosed`, `ConcurrentHashMap<Int, Short>`, `@Volatile private var service`, import `Dispatchers`/`Channel`), `SkeuomorphicComponents.kt` `sliderFraction` + `drawBehind` di `SkeuSliderTrack` (import `CornerRadius`, `Size(fillW, size.height)`), `BoosterScreen.kt`/`EqCurveEditor.kt` import `graphicsLayer`; 1 temuan = 1 batch, ubah HANYA baris yang dilaporkan; (2) uji device EQ manual (user): drag kurva & slider band mulus tanpa tersendat, suara berubah mengikuti band (apply di IO tidak boleh membuat band tak berubah / lambat), isian track slider mengikuti thumb tanpa celah/kedip di SEMUA 6 tema, tampilan kartu EQ & Spectrum tak berubah (tak ada tepi terpotong akibat layer), buka-tutup kartu EQ & ganti tab tak patah, rotasi saat kartu terbuka tak mereset nilai, preset/Reset tetap menyetel band; matikan Boomly lalu nyalakan → nilai EQ tersimpan benar. Bila masih tersendat: user WAJIB menyebut gejala PERSIS (saat drag kurva / slider / buka kartu / pindah tab / suara putus, tema apa, HP apa) — JANGAN tebak lagi; kandidat berikutnya (1 per batch): (i) `depthPlateSurface` biaya gambar kartu tinggi (`SkeuomorphicComponents.kt` ±L799) / `DepthBitmapStore` miss saat kartu EQ dibuka (ukuran berubah); (ii) rekomposisi `FeatureControl` per tick (lambda/semantics); (iii) tulis prefs `eq_band_N` hanya saat drag selesai. GEJALA GAGAL baru (band tak berubah/terlambat/hilang setelah restart, isian slider salah) → REVERT 4 file ke `Boomly_v188.zip`]. SEBELUMNYA (B188): [Optimalisasi tab Equalizer Manual, Batch 188; ZIP `Boomly_v188.zip`, 2 file source] -> [kode selesai; NOT VERIFIED: belum dikompilasi/lint/detekt, perilaku di HP belum diuji] -> [(1) DAILY UPDATE v188 lalu build CI; bila MERAH cek kandidat B188 DULU: `EqCurveEditor.kt` `eqLevelToY`/`eqYToLevel` (aritmetika `Short`/`Float`, `EQ_CURVE_STEP_MB`), `rememberUpdatedState` + delegasi `by` (`latestLevels`/`latestOnBandChange`/`latestMin`/`latestMax`), `remember(density)` `Stroke`, `with(density) { 10.sp.toPx() }`; `BoosterScreen.kt` `EqualizerSection` (`allFlat by remember(bandCount, resetKey, resetLevel) { derivedStateOf {…} }`) + `EqBandSlider(levels: MutableList<Short>)`; 1 temuan = 1 batch, ubah HANYA baris yang dilaporkan; (2) uji device EQ manual (user): drag titik kurva → nilai loncat per 50 mB (sama dgn slider), tak ada tersendat; slider band tetap sinkron dgn kurva (dua arah); terapkan preset / tombol Reset lalu raih titik kurva di posisi BARU → harus bisa digeser (fix hit-test basi); band lain tak berkedip saat 1 band digeser; Reset Equalizer aktif/nonaktif benar (nonaktif saat semua 0 mB); status Equalizer CONTROL_LOST/FAILED tetap tampil di subtitle; rotasi layar saat kartu terbuka tak mereset nilai. GEJALA GAGAL → REVERT `EqCurveEditor.kt` + `BoosterScreen.kt` ke `Boomly_v187.zip`, catat gejala PERSIS (band mana, arah jari). Tuning: kelipatan kurva = `EQ_CURVE_STEP_MB` (50 mB, harus = `step = 50f` di `EqBandSlider`); radius sentuh titik = `hitRadiusPx` 28.dp. Ide lanjutan: tulis prefs `eq_band_N` hanya saat drag selesai (butuh sentuh `AudioEnhancerService.setEqualizerBand`/`PrefsHelper`; sejak B189 sudah di thread IO → prioritas rendah); apply non-Main SUDAH dikerjakan B189]. SEBELUMNYA (B187): [Mesin depth timbul/cekung di SEMUA 6 tema, Batch 187; ZIP `Boomly_v187.zip`, 3 file source] -> [kode selesai; NOT VERIFIED: belum dikompilasi/lint/detekt, tampilan 5 tema baru belum dilihat di HP] -> [(1) DAILY UPDATE v187 lalu build CI; bila MERAH cek kandidat B187 DULU: `SkeuomorphicComponents.kt` `depthRoundFacets` (`Path.arcTo(Rect, Float, Float, Boolean)`, `Rect(...)`, `kotlin.math.cos/sin`), `depthRoundFrame` (`RoundRect(l,t,r,b,rx,ry)` + import `RoundRect`), `depthCornerShape` (tipe `Shape`), `skeuBackdropGrain(alpha)`; `Theme.kt` `DepthStyle` default param + 5 profil di atas `*SkeuTokens`; `MainActivity.kt` blok `LocalSkeuTokens.current.depth.let`; 1 temuan = 1 batch, ubah HANYA baris yang dilaporkan; (2) uji device per tema (user): kartu terbaca TIMBUL dari latar, sudut bulat tidak bergerigi/tidak ada sliver bevel di sudut, tombol/pil/tab/dialog tidak bergeser ukuran & bentuknya tetap sesuai tema (Glass/Aurora/StudioEq bulat, Neumorphism 4dp, Serene cut-corner), scroll & drag slider tetap mulus; tampilan kurang/kebanyakan = ubah HANYA angka profil (`faceTop`/`faceBottom`/`rimLightAlpha`/`rimShadeAlpha`/`bevelWidth`/`wellFloor`/`knobShade`/`grainAlpha`/`backdropGrain`/`keyCorner`/`panelCorner`) di `GlassDepth`/`AuroraDepth`/`NeumoDepth`/`StudioEqDepth`/`SereneDepth`; bila jank: cek `DepthBitmapStore` miss (kartu bulat 20 facet/kartu vs 8 di chamfer) / `MAX_ENTRIES`; revert = `Boomly_v186.zip`; B187 MENCABUT larangan "JANGAN sentuh 5 tema lain" di bawah (hanya untuk mesin depth, atas permintaan user)]. SEBELUMNYA (B180-B186): tema Old Money — efek timbul/cekung fisik (Batch 180) + FIX compile (Batch 181: hapus import `asFrameworkPaint`, CI run 220) + POLISH (Batch 182) + PENGAMAN `key(tag)` (Batch 186; ZIP `Boomly_v186.zip`, 1 file) + FIX CRASH v224 (Batch 185; ZIP `Boomly_v185.zip`, 1 file `SkeuomorphicComponents.kt`: `depthDraw` kunci = 1 `List` + `tag`) + TUNTAS KOMPONEN + OPTIMASI (Batch 184; ZIP `Boomly_v184.zip`; 4 file source: `SkeuomorphicComponents.kt` (mesin dirombak: `DepthBitmapStore`, `depthDraw`, template 9-slice `drawShadowSlices`, `renderDepthFace`, `DepthKeyBox`, wrapper `Skeu*`), `BoosterScreen.kt`/`SettingsScreen.kt`/`OnboardingScreen.kt` (rename mekanis + pembungkus `legacy`)) + PIL PRESET (Batch 183; ZIP `Boomly_v183.zip`; 2 file source: `SkeuomorphicComponents.kt` (`SkeuPresetPill`, `depthPillRim`, `DepthPillShadow`, import `selectable`) + `BoosterScreen.kt` (3 pemanggilan `SkeuPresetPill` di baris Preset Cepat: built-in, custom, "Simpan"; `Row` +padding vertikal bersyarat); isi B182: (ZIP `Boomly_v182.zip`; 3 file source: `SkeuomorphicComponents.kt` (`depthPlateSurface` vignette+sheen+alur ukir via `depthOctagon`, `renderDepthWellInner` lapis AO, `depthDome` specular, isian `SkeuSliderTrack` gradien, `skeuBackdropGrain`, skala bitmap), `Theme.kt` (`DepthStyle.frameInset`, `OldMoneyDepth`), `MainActivity.kt` (satu `.then(...skeuBackdropGrain())` hanya `OLD_MONEY`)); isi B180: 2 file source: `Theme.kt` (`DepthStyle`, `SkeuTokens.depth`, `OldMoneyDepth`, `OldMoneyCardLight/Dark`, `OldMoneyWellFloor`) + `SkeuomorphicComponents.kt` (mesin `renderDepthCastShadow`, `renderDepthWellInner`, `depthCastShadow`, `depthPlateSurface`, `depthWellInner`, `depthWellLip`, `depthDome`, `depthRingRim`, `DepthSocket`, `DepthGrainTile`; cabang `depth != null` di `SkeuCard`, `SkeuTintedCard`, `SkeuPowerButton`, `SkeuSliderTrack`, `SkeuSliderThumb`, `SkeuSwitch`, `SkeuGroupDivider`); +1 frasa `README.md`) -> STATUS: NOT VERIFIED — user melaporkan hasil B181 "Excellent" (tanpa log CI/static-analysis); B182 BELUM dikompilasi; kompilasi/lint/detekt belum terbukti hijau, tampilan di HP BELUM diuji (hanya simulasi statis); B179..B171 tetap NOT VERIFIED -> Langkah berikutnya: (T) DAILY UPDATE v186, tunggu CI (cek nomor versi di crash log baru: harus >= 225 untuk bukti build sudah baru), lalu coba tekan tombol/pil/tab bergantian (pemicu crash v224 = pergantian timbul↔cekung di rantai modifier) dan kirim `crash_*.txt` baru bila masih ada; tunggu CI `compileDebugKotlin` hijau (kalau gagal: kirim `log_fail_*` baru, 1 temuan = 1 batch), lalu uji device Old Money (user): kartu terlihat TIMBUL jelas terpisah dari latar (sorot gading tegas di tepi atas/kiri, tepi bawah/kanan gelap, bayangan jatuh kanan-bawah — BUKAN nyaru), kotak ikon/track slider/groove switch terlihat CEKUNG (bibir terang tipis di kanan-bawah luar), power button timbul lalu cekung saat ON, knob = kubah champagne dgn cincin aksen tipis, divider = alur ukir; cek: scroll tetap mulus (tiap kartu = 1 `drawImage` + 8 facet + 1 `drawRect` grain; kalau tersendat naikkan `DepthPlateBitmapScale` ke lebih kecil/kurangi layer `DepthPlateShadow`), frame pertama tak tersendat (render bitmap sekali per kartu), bayangan tak terpotong di tepi layar, teks/burgundy tetap terbaca di pelat lebih terang (burgundy ≈2.9–3.2:1), tema lain TIDAK berubah, tema tetap setelah rotasi. Bila COMPILE gagal lagi: BACA log dulu (jangan menebak); kandidat B184: `remember(*keys) { block }` + `drawWithCache(stable)` di `depthDraw` (import `CacheDrawScope`/`DrawResult`), `LinkedHashMap.removeEldestEntry` override di `DepthBitmapStore`, `Color.toArgb()` + `Bitmap.createBitmap(IntArray,...)` di `renderDepthFace`, `AlertDialog(..., modifier, shape, containerColor)` di `SkeuAlertDialog`, `OutlinedTextFieldDefaults.colors(focusedContainerColor, unfocusedContainerColor)` di `skeuFieldColors`, `ProvideTextStyle`, `Arrangement.spacedBy(Dp, Alignment.Horizontal)`; kandidat B183: `Modifier.selectable(selected, interactionSource, indication = null, role, onClick)` di `SkeuPresetPill`, `drawArc(brush, startAngle, sweepAngle, useCenter, topLeft, size, style)` + `drawLine` di `depthPillRim`, `legacy: @Composable () -> Unit` sebagai lambda terakhir; kandidat B182: `drawRect(brush = ...)` tanpa alpha di `depthPlateSurface`, `translate(left=, top=)` + `drawPath(style = Stroke)`, `Brush.verticalGradient(list)` di isian slider, `skeuBackdropGrain` (internal, dipanggil MainActivity); kandidat B180 di `SkeuomorphicComponents.kt` — `GfxCanvas(ImageBitmap)` (alias `Canvas as GfxCanvas`), `paint.asFrameworkPaint().maskFilter` (member, sudah tanpa import), `ShaderBrush(ImageShader(..., TileMode.Repeated, TileMode.Repeated))` di `DepthGrainBrush`, `Brush.linearGradient(vararg stops, start, end)` di `depthRingRim`, `drawImage(image, dstOffset, dstSize)` di `depthCastShadow`, `composed {}` + `drawWithCache`, `Bitmap.createBitmap(IntArray, w, h, Config)` di `DepthGrainTile`; lalu `Theme.kt` — `DepthStyle`, param baru `depth` di `OldMoneySkeuTokens`; 1 temuan = 1 batch. Bila UKURAN tombol/tab bergeser: `minHeight`/`contentPadding`/`touchPad` di wrapper `Skeu*Button` & `SkeuTabBar` (tinggi tab 42dp+palung 3dp). Bila jank: cek dulu apakah `DepthBitmapStore` terlalu sering miss (ukuran berubah tiap frame) / turunkan `MAX_ENTRIES`; ukur dgn Profile GPU Rendering. Bila tampilan kurang/kebanyakan: ubah HANYA angka — pil B183: `DepthPillShadow`, `1.2.dp` rim, `0.72f` enamel terpilih, `heightIn(min = 32.dp)`, padding `14.dp`/`6.dp`, `padding(vertical = 7.dp)` di Row; kekuatan polish B182: vignette `0.14`, kilau `0.07`, alur ukir `0.40`/`0.09` + `frameInset`, AO sumur `0.40`, specular knob `0.55`, butiran latar `0.05` (`skeuBackdropGrain`); `OldMoneyDepth` (`rimLightAlpha` 0.50, `rimShadeAlpha` 0.60, `bevelWidth` 1.7dp, `grainAlpha` 0.07), `OldMoneyCardLight/Dark`, `OldMoneyWellFloor`, layer `DepthPlateShadow` (dx,dy,blur,alpha), alpha bayangan dalam/bibir di `renderDepthWellInner` (0.85/0.20), `0.22` di `depthWellLip`; JANGAN sentuh 5 tema lain; revert = `Boomly_v179.zip` (neumorphism B179, kode lama tanpa mesin depth) / `Boomly_v175.zip` (tanpa Old Money); JANGAN kembalikan ke alpha tipis sehue latar & JANGAN tambah emas (B178); (0) JANGAN terapkan ulang panduan `Tinjauan_Solusi_AudioEnhancer.md` (B175, lihat "Keputusan sadar"); panduan neumorphism B179 SUDAH diterapkan hanya ke Old Money (tema Neumorphism lama tak disentuh); bila user minta bagian tertentu, 1 temuan = 1 batch, ubah HANYA bagian itu, mis. `allowBackup` (butuh keputusan user); (a) DAILY UPDATE v179, lalu uji device (user, WAJIB — ini jalur sentuh paling sering dipakai; Bass/Virtualizer/Loudness/Compressor/tiap band EQ): TAP di mana pun (jauh maupun dekat thumb) → nilai TIDAK berubah; drag dimulai di thumb → thumb ikut jari tanpa lompatan awal, snap B169 kelipatan tetap, haptic di akhir drag; tap/drag mulai di thumb → normal & snap B169 tetap (tanpa batas jarak setelah drag mulai); scroll halaman dgn jari mendarat di slider → halaman tetap scroll & nilai tak berubah; geser horizontal dari titik jauh (>32dp dari thumb) → nilai tak berubah; thumb di ujung (0/max) tetap bisa digeser balik; preset diterapkan → slider ikut ke posisi preset; slider disabled tetap abu-abu & tak responsif. GEJALA GAGAL: nilai loncat ke 0/ke titik sentuh, thumb tak bisa digeser, drag tersendat/berhenti di tengah, atau scroll terblokir → REVERT `SkeuomorphicComponents.kt` ke `Boomly_v171.zip`, catat gejala PERSIS (slider mana, posisi thumb, arah jari); jangan tebak lagi; (b) user upload artifact BARU `AudioEnhancerPro_static_analysis_v*-run*.zip`: BACA `STATIC_ANALYSIS_MARKER.txt` DULU (HARUS BATCH_PROJECT_STATE>=174, STEP_DETEKT_LINT/TYPED=success) lalu `DETEKT_TYPED_DIAG` (HARUS `config=detekt.yml,detekt-typed.yml`); target = `detektDebug` 0 + polos 0 + lint 0E/1W (OldTargetApi). Bila MERAH/gagal kompilasi, cek DULU kandidat B172-B174: `SkeuomorphicComponents.kt` `thumbOnlyDrag` (`awaitFirstDown(pass=)`, `viewConfiguration.touchSlop`, `size.width` di `AwaitPointerEventScope`, `change.consume()`, `snapToStep(…, step.value, r)`) dan kandidat B171: `AudioEnhancerService.kt` `syncExcludeFromRecents`, `MainActivity.kt` `refreshBatteryStatus` (guard `Build.VERSION_CODES.P`), `BoosterScreen.kt` param baru + import `CheckCircle`; 1 temuan 1 batch, ubah HANYA baris yang dilaporkan; (c) uji device B171 (user): nyalakan Boomly → Boomly TIDAK tampil di Recents & logcat tag `AudioEnhancerService` baris `LIFECYCLE recentsExclusion exclude=true appTasks=1` (`appTasks=0` atau tetap tampil = tak jalan); matikan → tampil lagi & `exclude=false`; kartu baterai ✓ atau tombol izin; baris merah HANYA bila Android melapor; Boomly menyala → Home (tanpa swipe) → service tetap hidup (bila TETAP mati = pembersih OEM lain, JANGAN buat penangkal tanpa perintah user); (d) "next" TANPA nama fitur: JANGAN mengarang fitur — tawarkan 2 kandidat dan tunggu pilihan: (1) Scheduler hari tertentu (sisa Fase 8 B; `ScheduleWorker.kt`+`PrefsHelper.kt`+`SettingsScreen.kt`+strings ID/EN; default semua hari = perilaku lama; hanya WorkManager, tanpa sentuh Service); (2) snap kurva EQ (`EqCurveEditor.kt`, kelipatan 50 mB agar konsisten dgn slider band); (e) device test lama (Track T2): B163 crash banner, B162 background→buka ulang UI hidup, B160/B161 preset, B156 shortcut→rotasi, B157 Autostart OEM `asus`, B158 spectrum halus, Scheduler B134/B151; (f) targetSdk=34 USER-CONFIRMED (jangan ajukan lagi); M2 DITURUNKAN, M6 BLOCKED, M8b HANYA bila diminta, fade-out Timer Tidur BLOCKED (skip user); (g) Android <12 guard/test, watchdog/heartbeat, `HorizontalPager`, upgrade AGP/Kotlin/BOM, ktlint/ArchUnit/CodeQL/gerbang BLOCKING, per-app profile, Android Auto/Wear = DI LUAR PLANNING; JANGAN ubah Recents exclusion jadi atribut manifest permanen & JANGAN port `setAlarmClock`/channel hantu dari LagFix. Batch berikutnya = 192.]
