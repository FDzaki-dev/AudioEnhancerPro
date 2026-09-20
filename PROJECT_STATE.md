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
  saat diterapkan (beda dari 4 preset bawaan). Simpan bass/virtualizer/
  loudness + `eqBands` opsional (Batch 63) — preset lama tanpa `eqBands`
  TIDAK menyentuh EQ manual.
- **Layout layar utama (Batch 97)**: DEFAULT = vertikal (1 `Column`
  `.verticalScroll()` flat). Mode tab horizontal (`TabRow`+`HorizontalPager`)
  TETAP ADA di kode (`TabPageContent` di `BoosterScreen.kt`) tapi HANYA opsi
  custom opt-in via toggle "Mode Tab Horizontal" di `SettingsScreen.kt`
  (default `false`). JANGAN balikin default ke horizontal tanpa instruksi
  eksplisit baru dari user.
- **Swipe-antar-tab dalam Mode Tab Horizontal (Batch 103-105)**: DIHAPUS,
  ganti tap-tab biasa (`selectedTabIndex`, `rememberSaveable`) — 1
  scrollport, 0 clip ganda, TERVALIDASI stabil. Percobaan mengembalikan
  swipe via auto-height pager (Batch 104) TERBUKTI regresi UI parah di
  device fisik (klip & distorsi), sudah direvert total (Batch 105). JANGAN
  coba pendekatan auto-height/`onSizeChanged` dinamis-per-page lagi.
  Kandidat lebih stabil kalau swipe diminta lagi: pager tinggi tetap =
  tinggi konten TERPANJANG dari ke-3 tab, dihitung SEKALI di awal (bukan
  dinamis tiap swipe, 0 re-layout saat gesture berlangsung).

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

- **Batch terakhir**: 124, hotfix URGENT — watchdog periodik gagal diam-diam
  restart service (Android 12+ background-start restriction, tidak ditangkap),
  diganti fallback notifikasi tap-to-restart (lihat LOG BATCH 124);
  **NOT VERIFIED**, tidak ada toolchain lokal, nunggu CI/device user.
  Sebelumnya: 123 hotfix speaker internal nempel preset Kustom (NOT VERIFIED);
  122 Auto-Profil per Output kode SELESAI (NOT VERIFIED); 121 Compressor
  (**USER-CONFIRMED WORKING** di device fisik); 120 Spectrum visualizer (NOT
  VERIFIED); 119 Sleep timer bag. 1 (NOT VERIFIED); logika terakhir sebelum
  itu: B115.
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
  Matikan). **NOT VERIFIED**. Sisa: fade-out volume, Scheduler jam/event.
- **Versioning**: `versionCode` DAN `versionName` OTOMATIS dari
  `GITHUB_RUN_NUMBER` (String=Int sama nilai) — DILARANG bump manual.
- **Layar utama**: default vertikal 1-scroll. Mode Tab Horizontal = opsi
  custom opt-in di Settings, tap-tab (bukan swipe), 1 scrollport, 0 clip
  ganda — lihat "Keputusan sadar" di atas untuk histori & batasan.
- **iOS Look Hybrid Rombak** (struktur/pola, independen dari warna/tema):
  grouped-list Kontrol + Settings selesai+tervalidasi, tipografi Large
  Title selesai+tervalidasi, styling pill Preset Cepat selesai (belum
  tervalidasi visual). Sisa: nav bar large-title-collapsing, audit
  `OnboardingScreen.kt`, SF Symbols-style icon — lihat TODO Fase 7.
- **Validasi runtime (default)**: perubahan baru = NOT VERIFIED sampai user
  konfirmasi eksplisit (sandbox tanpa compiler/emulator, lihat "Batasan
  sandbox"). Sisa validation debt NON-tema (pasif, non-blocking): Fase 1.

---

## ⚠️ Temuan terbuka (Batch 118 — BELUM diubah: SOP immutable / di luar scope dok)
- **Nama secret Box B vs CI**: Box B (SOP terkini) men-set
  `ANDROID_KEYSTORE_BASE64`/`_PASSWORD` + `ANDROID_KEY_ALIAS`/`_PASSWORD`,
  sedangkan `build.yml` + README membaca `KEYSTORE_BASE64`/
  `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` (tanpa prefix). Rilis
  produksi jalan normal (Batch 78-79) → secret repo aktif sudah cocok CI;
  risiko HANYA di setup repo BARU via Box B apa adanya (step release
  ke-skip diam-diam, cuma warning). Butuh keputusan user: samakan Box B
  atau `build.yml`.
- **Guard integritas Daily Update**: teks SOP tertulis
  `[ $NEW_COUNT -lt$((OLD_COUNT * 70 / 100)) ]` (tanpa spasi setelah `-lt`)
  → bash "unary operator expected", guard rollback tidak pernah trigger
  (jatuh ke commit+push). Diuji di sandbox Batch 118. Skrip immutable,
  tidak diubah — butuh keputusan user.

## 📅 LOG BATCH (descending, terbaru paling atas — BUKAN bagian permanen)
Format: **Batch N** (file disentuh) — apa yang berubah. Status validasi.
Root-cause/diff/rasional detail → `CHANGELOG.md`, BUKAN di sini.

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
  project ini SELALU 31+ jadi SEMUA device kena. Pola aman WAJIB dipakai tiap
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
  `appThemeStyleKey` (persisted) di-map ke `AppThemeStyle` enum.
- `BoosterScreen.kt` — layar utama Compose. Default: 1 `Column`
  `.verticalScroll()` flat berisi Preset Cepat → kartu Bass/Virtualizer/
  Loudness (grouped-list 1 card) → Equalizer Manual → toggle Material You
  (Android 12+) + 4 toggle varian tema eksklusif (semua mati = Midnight
  Glass) → kartu baterai/autostart. Opsi custom: Mode Tab Horizontal
  (`TabPageContent(page)`, tap-tab via `selectedTabIndex`). Termasuk
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
- `ServiceWatchdogWorker.kt` — WorkManager periodic 15 menit, restart
  service kalau mati padahal user tidak minta mati.
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
- `BoosterWidgetProvider.kt` (widget home), `QuickToggleTileService.kt` (QS
  Tile), `ShortcutHelper.kt` (App Shortcuts), `BootReceiver.kt` (start ulang
  setelah boot).
- Test (`app/src/test`): `AudioEnhancerServiceStateTest.kt` (13 test
  Robolectric, Batch 86), `PrefsHelperTest.kt`, `FormatFreqLabelTest.kt`.
- `docs/preview/current.html` — mockup HTML standalone (HANYA Midnight
  Glass), WAJIB update bareng perubahan visual besar. `docs/archive/` — dok
  usang, JANGAN jadi acuan konteks.

---
## 📋 TODO / ROADMAP — backlog aktif (konsolidasi Batch 106 dari `roadmap.md` +
2x `PENDING_*.md`, ketiganya diarsipkan ke `docs/archive/` — lihat "🔒 ATURAN
PERMANEN" soal kebijakan arsip. Ini SEKARANG satu-satunya sumber kebenaran
backlog, jangan biarkan pecah lagi ke file terpisah.)

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
(Lesson swipe-antar-tab Batch 104-105 ada di "Keputusan sadar", tidak
diulang di sini.)

### Fase 2 — Build & CI Maturity
- [x] Gabung job build+release jadi 1 (Batch 40) · Cache Gradle dependency+
  wrapper (Batch 40) · Cabut Hilt/kapt (Batch 49) · configuration-cache
  (Batch 50)
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
kandidat sisa D/E/F. 8 → item D selesai; B Sleep timer bag. 1 (kode, NOT VERIFIED); sisanya nunggu instruksi.

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
- Custom EQ curve editor drag-point (sudah lama di Fase 5, DIANGKAT
  prioritas — diferensiator vs app EQ generic).
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
- Scheduler jam/event tertentu via `WorkManager` (pola sudah ada di
  `ServiceWatchdogWorker`, tinggal extend).

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
NOT VERIFIED. Berikutnya: 6) EQ curve editor (A) 7) Sleep timer fade-out +
Scheduler (sisa poin 3) 8) sisanya sesuai kebutuhan user.

---

[RESUME POINT: Hotfix URGENT watchdog auto-restart (Batch 124; kode:
`AudioEnhancerService.kt`, `ServiceWatchdogWorker.kt`, strings ID/EN — 2 file+strings;
ZIP `Boomly_v124.zip`) → SELESAI kode LENGKAP: root cause Android 12+
background-start restriction (`ForegroundServiceStartNotAllowedException` di
`ServiceWatchdogWorker` tidak ditangkap) diverifikasi ke dokumentasi resmi
developer.android.com; fix try-catch + `postRecoveryNotification()`
(channel `CHANNEL_ID_RECOVERY` HIGH, `PendingIntent.getForegroundService`
tap-to-restart, jalur exempted resmi). **NOT VERIFIED** (sandbox TANPA
toolchain lokal — HANYA lolos review manual: brace/paren balance kedua file
0/0, XML well-formed, parity string ID/EN 166=166; belum lolos CI ataupun
device fisik) → Remaining: (a) validasi CI compile Batch 124; (b) kalau
compile OK, uji device fisik: force-stop Boomly (atau biarkan OEM battery
manager membunuhnya) SAAT battery optimization BELUM di-exempt → tunggu
watchdog jalan (≤15 menit) → cek notifikasi "Boomly berhenti" muncul → tap →
cek booster nyala lagi dengan Bass/Virtualizer/Loudness/EQ/Compressor SAMA
seperti sebelum dibunuh (bukan reset default); ulangi test yang sama dengan
battery optimization SUDAH di-exempt → pastikan watchdog restart langsung
tanpa notifikasi sama sekali (jalur lama, harus tetap non-regresi); (c)
backlog lama masih terbuka (belum tersentuh batch ini): Auto-Profil per
Output (Batch 122/123) masih NOT VERIFIED di device fisik (skenario lengkap
di histori LOG BATCH 123 sebelum overwrite ini); Compressor Batch 121
USER-CONFIRMED WORKING; Spectrum Visualizer Batch 120 NOT VERIFIED; Sleep
timer fade-out & Scheduler belum dikerjakan; 2 temuan lama (secret Box B vs
CI; guard `-lt$((`) → Next Action: kalau CI/user lapor Batch 124 gagal
compile atau (b) gagal → hotfix lanjutan di 2 file yang sama; kalau (b)
lolos/user OK → lanjut validasi Auto-Profil (b) dari Batch 123 yang masih
menggantung, ATAU Fase 8 ROI #6 EQ curve editor / Sleep timer fade-out
kalau user pilih itu duluan. Batch berikutnya = 125.]
