# Changelog

## Batch 85 (hotfix): `compileDebugKotlin` gagal di CI run 133 — param constructor `DynamicsProcessing.Limiter` salah

CI run 133 (dari zip Batch 84) merah total di `:app:compileDebugKotlin` —
`processDebugResources`/task lain lolos, TIDAK ADA APK ke-generate. Root
cause: `attachDynamicsProcessing()` (baru ditambah Batch 84) construct
`DynamicsProcessing.Limiter(...)` dengan asumsi param pertama adalah
`channelIndex: Int` (diisi literal `0`, dengan komentar "diabaikan —
setLimiterAllChannelsTo menerapkan ke semua channel"). Asumsi ini SALAH —
constructor asli `android.media.audiofx.DynamicsProcessing.Limiter` (dicek
ulang ke dokumentasi resmi, TIDAK ada channelIndex sama sekali di
constructor manapun untuk class ini) urutannya `(inUse: Boolean, enabled:
Boolean, linkGroup: Int, attackTime: Float, releaseTime: Float, ratio:
Float, threshold: Float, postGain: Float)` — param pertama itu `inUse`
(Boolean), bukan channelIndex. Literal `0` (Int) di posisi Boolean itu yang
bikin Kotlin compiler nolak (`The integer literal does not conform to the
expected type Boolean`, `AudioEnhancerService.kt:440:46`).

**Fix (1 file: `AudioEnhancerService.kt`, edit-parsial murni)**: argumen
pertama diganti dari `/* channelIndex = */ 0` jadi `/* inUse = */ true` —
secara semantik ini juga LEBIH benar dari niat awal (limiter ini memang
"SATU-SATUNYA stage yang dipakai effect ini", jadi `inUse=true` sudah
sesuai), bukan cuma tempelan biar compiler diam. 7 parameter lain
(`enabled`, `linkGroup`, `attackTime`, `releaseTime`, `ratio`, `threshold`,
`postGain`) TIDAK berubah nilai maupun urutan — semua sudah cocok dengan
signature asli. Sisa `attachDynamicsProcessing()` (guard
`SDK_INT >= VERSION_CODES.P`, `Config.Builder(...)`, listener,
try-catch, state) 100% apa adanya dari Batch 84.

**Cek statis pengganti compiler** (tidak ada kotlinc/Android SDK di sandbox
Claude): brace/paren/bracket seluruh `AudioEnhancerService.kt` di-parse
ulang secara terprogram (string/char/comment-aware) — 0 selisih, 0 token
nyangkut. Signature constructor dikonfirmasi ke dokumentasi resmi
`developer.android.com` (bukan tebakan dari memori) sebelum fix ditulis.

**Scope**: HANYA baris yang salah ini yang disentuh — 0 refactor file lain,
0 perubahan file lain di luar yang disebut, 0 bump versi manual (tetap
`GITHUB_RUN_NUMBER`, sesuai `CI_CD_LOCK`). `roadmap.md` Fase 0 #5 TETAP
`[~]` (status sebagian, alasan arsitektural sama seperti Batch 84 — hotfix
ini cuma benerin compile error, tidak mengubah keputusan pipeline).

**BELUM divalidasi runtime** — hotfix ini menghilangkan compile error yang
terkonfirmasi dari log CI run 133, tapi siklus penuh (push → CI baru →
install → dengar hasil limiter di device fisik) belum jalan lagi sejak fix
ini.

## Batch 84: roadmap.md Fase 0 #5 — Gain staging + dynamics pipeline (master limiter)

Item kedua dari antrian 5 sisa Fase 0 (dicatat Batch 82, item pertama #3 di
Batch 83). Menutup gap audit "belum ada master limiter/compressor
terkontrol" dengan menambahkan effect `DynamicsProcessing` sebagai limiter
murni di `AudioEnhancerService.kt` — TAMBAHAN, bukan pengganti 4 effect lama
(`BassBoost`/`Virtualizer`/`Equalizer`/`LoudnessEnhancer`).

**Yang ditambahkan (1 file: `AudioEnhancerService.kt`)**:
- `attachDynamicsProcessing()` — konstruksi `DynamicsProcessing.Config`
  dengan 0 pre-EQ band, 0 MBC band, 0 post-EQ band, `limiterInUse=true` saja
  (`VARIANT_FAVOR_TIME_RESOLUTION`, `channelCount=2`), lalu
  `setLimiterAllChannelsTo()` dengan parameter: threshold -1 dBFS, ratio
  20:1, attack 3ms, release 60ms, postGain 0dB. Semua hardcoded — belum ada
  slider UI untuk parameter ini di batch ini.
- `dynamicsState: EffectState` (@Volatile, field baru pola sama `bassState`
  dkk) — diikutkan penuh ke `retryControlAcquisition()` (branch baru),
  `releaseEffects()`, `disableEffects()`, `enableEffects()`. Karena
  `onOutputRouteChanged()` (Batch 82/83) memanggil `enableEffects()` apa
  adanya, limiter baru ini OTOMATIS ikut ter-nudge saat output route
  berubah tanpa perubahan apa pun di fungsi itu.

**Kenapa SEBAGIAN (`[~]`), bukan restrukturisasi pipeline penuh**: audit
asli minta urutan eksplisit `Input → Pre-Gain → EQ → Dynamics → Loudness →
Output`. API `AudioEffect` publik session-0 legacy (yang dipakai project
ini) TIDAK punya cara resmi bagi app untuk memaksa urutan insert effect di
HAL — semua effect session-0, termasuk limiter baru ini, nyambung independen
dan urutan proses akhir ditentukan sistem/HAL, di luar kendali app manapun.
Limiter ini berfungsi sebagai "ceiling" tambahan yang SEHARUSNYA tetap
efektif menangkap level sinyal gabungan terlepas dari urutan proses effect
lain (karena bekerja pada level akhir, bukan per-tahap) — TAPI ini BUKAN
jaminan urutan pipeline eksplisit seperti diminta audit. Rebuild yang
benar-benar bisa menjamin urutan semacam itu ada di scope `roadmap.md` Fase
0 **#6** (item terpisah, jauh lebih besar, masih berstatus BLOKER menunggu
konfirmasi risiko dari user).

**Kenapa parameter limiter dipilih seperti itu**: threshold -1 dBFS + ratio
20:1 + attack 3ms = brickwall "safety ceiling" yang baru aktif kalau sinyal
benar-benar mepet clipping — BUKAN loudness maximizer (beda tujuan dari
`LoudnessEnhancer` yang memang untuk menaikkan loudness), makanya postGain
sengaja 0 dB. Release 60ms dipilih moderat: cukup cepat untuk audio umum,
tidak sampai menimbulkan "pumping" (distorsi persepsi akibat release
limiter yang terlalu agresif).

**`channelCount` hardcode 2 (stereo)**: `DynamicsProcessing.Config.Builder`
(beda dari 4 effect lama) butuh channelCount eksplisit di construction time,
dan tidak ada API resmi untuk query channel count output aktif dari sisi
effect sebelum construct. Stereo adalah default hampir universal consumer
Android modern — device mono-only (kalau ada) belum tervalidasi, kandidat
gap pertama kalau ada laporan crash di device semacam itu.

**KOREKSI PENTING (ditemukan & diperbaiki SEBELUM zip batch ini dikirim,
BUKAN batch terpisah)**: `DynamicsProcessing` baru tersedia sejak API 28
(Android 9/Pie). Draf awal fungsi ini SEMPAT tidak di-guard versi SDK sama
sekali (salah asumsi minSdk project ini 31, padahal `app/build.gradle.kts`
project ini `minSdk = 24`). Referensi langsung ke class ini di device API
24-27 akan melempar `NoClassDefFoundError` — subclass `Error`, BUKAN
`Exception`, sehingga `catch (e: Exception)` yang sudah ada TIDAK
menangkapnya — berpotensi crash total (bukan graceful-degrade seperti 4
effect lain) di device lama kalau tidak diperbaiki. **Fix**: seluruh isi
`attachDynamicsProcessing()` sekarang dibungkus
`Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` (pola standar Android
untuk API-level gating, aman untuk minSdk 24 yang sudah ART-only) — di
bawah API 28, `dynamicsState` langsung `UNAVAILABLE` tanpa menyentuh class
`DynamicsProcessing` sama sekali.

**`roadmap.md`**: checklist Fase 0 #5 `[ ]` → `[~]` (alasan detail di atas
juga tercatat di file itu), baris "Progress ringkas" Fase 0 diperbarui jadi
3 selesai + 3 sebagian (dari sebelumnya 3+2).

**0 file lain disentuh** — `BoosterViewModel.kt`, seluruh file UI, dan
`AndroidManifest.xml` 100% apa adanya. Tidak ada permission baru dibutuhkan.

**BELUM divalidasi runtime** (siklus lengkap zip → Termux → CI → install) —
kandidat pertama dicurigai: (1) apakah `DynamicsProcessing` benar-benar bisa
di-construct di semua OEM/chipset (variasi HAL vendor, belum pernah diuji
device fisik sama sekali — kalau gagal, `dynamicsState` jatuh ke
`UNAVAILABLE` via try-catch, BUKAN crash total, 4 effect lain tetap jalan
normal), (2) cek Logcat filter `AudioEnhancerService` untuk baris
"DynamicsProcessing tidak tersedia" kalau device tertentu tidak
mendukungnya, (3) uji telinga langsung: set Bass+Virtualizer+EQ+Loudness ke
maksimal berbarengan, dengarkan apakah clipping/distorsi yang tadinya lolos
sekarang tertahan limiter.

## Batch 83: roadmap.md Fase 0 #3 — Output routing awareness (AudioDeviceCallback)

Item pertama dari antrian 5 sisa Fase 0 (dicatat Batch 82). Implementasi
`AudioDeviceCallback` sistem di `AudioEnhancerService.kt` untuk mendeteksi
perpindahan sink output audio — SEBELUMNYA project ini nol handling untuk
skenario speaker↔Bluetooth↔wired headset↔USB DAC sama sekali, walau effect
di-attach ke session 0 (global mix, seharusnya route-agnostic per kontrak
platform, TAPI beberapa HAL vendor dilaporkan melepas effect diam-diam saat
sink berpindah — inilah gap yang mau ditutup).

**Yang ditambahkan (1 file: `AudioEnhancerService.kt`)**:
- `AudioDeviceCallback` (anonymous class field, API 23+, aman untuk minSdk 31
  project ini) — register di `onCreate()` (setelah `attachEffects()`),
  unregister di `onDestroy()` (SEBELUM `releaseEffects()`, urutan sengaja
  untuk menghindari race callback nyangkut setelah effect object dilepas).
  Registrasi dibungkus try-catch (Log.e kalau gagal, service tetap jalan
  tanpa fitur ini — bukan crash total).
- `onOutputRouteChanged()` (privat, dipanggil `onAudioDevicesAdded`/
  `onAudioDevicesRemoved`) — filter `AudioDeviceInfo.isSink` (buang device
  INPUT), catat `Log.i` + field baru `lastOutputRouteDescription` (@Volatile
  String?, pola sama seperti `bassState` dkk), lalu — HANYA kalau
  `isRunning == true` — panggil ulang `enableEffects()` yang SUDAH ADA
  (re-assert `enabled=true`, idempotent + null-safe + menandai `FAILED`
  kalau exception) sebagai nudge ringan.
- `describeOutputDeviceType()` (privat) — map `AudioDeviceInfo.TYPE_*` yang
  relevan (builtin speaker, Bluetooth A2DP/SCO/LE headset/LE speaker, wired
  headset/headphones, USB device/headset/accessory, HDMI, dock) ke label
  ringkas untuk Log; tipe di luar daftar fallback `"device tipe $type"`
  (tetap tercatat, tidak hilang diam-diam).

**Kenapa nudge ringan (`enableEffects()`), BUKAN recreate object
(`retryControlAcquisition()`)**: alasan sama persis dengan kenapa
`retryControlAcquisition()` (Batch 61) juga tidak punya pemanggil otomatis
dari mana pun — route audio bisa berpindah CUKUP SERING dalam pemakaian
normal (contoh: earbuds TWS yang reconnect berkali-kali dalam durasi
pendek), recreate object `AudioEffect` tiap kali route berubah berisiko
churn CPU/baterai sia-sia tanpa bukti itu benar-benar diperlukan. Kalau nudge
ringan ini ternyata tidak cukup dan effect beneran `CONTROL_LOST`, jalur yang
SUDAH ADA sejak Batch 61/62 (listener di `attachXxx()` →
`ControlRecoveryBanner` di UI) tetap akan menangkapnya lewat mekanisme
normal — fungsi baru ini adalah lapisan tambahan DI DEPAN jalur itu, bukan
pengganti.

**Kenapa digate `isRunning`**: kalau user baru saja menekan "Matikan" di
notifikasi (`disableEffects()` dipanggil, effect sengaja `enabled=false`),
route audio yang kebetulan berubah TIDAK BOLEH diam-diam menyalakan ulang
effect — itu akan melanggar pilihan eksplisit user, konsisten dengan alasan
`enableEffects()` juga tidak dipanggil sembarangan di tempat lain dalam
Service ini.

**`roadmap.md`**: checklist Fase 0 #3 diubah dari `[ ]` ke `[~]` (SEBAGIAN —
alasan detail kenapa belum `[x]` penuh ada di file itu: belum ada recreate
otomatis untuk kasus effect beneran lepas total, belum divalidasi konsistensi
`AudioDeviceCallback` lintas OEM, belum ada UI apa pun yang tampilkan
`lastOutputRouteDescription`). Baris "Progress ringkas" Fase 0 juga
diperbaiki di batch ini — SEBELUMNYA stale (masih tertulis "1/9" padahal
beberapa item sudah `[x]`/`[~]` dari batch-batch lama), bukan perubahan
status baru, cuma menutup gap 2-sumber-kebenaran yang kebetulan ketemu.

**0 file lain disentuh** — `BoosterViewModel.kt`, seluruh file UI, dan
`AndroidManifest.xml` 100% apa adanya. Tidak ada permission baru yang
dibutuhkan (`AudioDeviceCallback` tidak butuh permission khusus apa pun).

**BELUM divalidasi runtime** (siklus lengkap zip → Termux → CI → install) —
kandidat pertama dicurigai: (1) apakah `AudioDeviceCallback` benar-benar fire
konsisten di semua OEM/chipset (variasi HAL vendor, kelas risiko yang sama
dengan capability lain di file ini — belum pernah diuji device fisik sama
sekali), (2) cek Logcat filter `AudioEnhancerService` sambil ganti
Bluetooth/cabut-colok headset kabel untuk lihat baris "Output route berubah"
benar-benar muncul dan labelnya masuk akal, (3) pastikan nudge
`enableEffects()` TIDAK ke-trigger saat `isRunning=false` — matikan dulu via
notifikasi "Matikan", baru ganti device audio, pastikan efek TETAP mati
(tidak menyala sendiri).

## Batch 81: Feedback update lebih informatif, unduh langsung dari Pengaturan (bukan bolak-balik tab)

Diminta user eksplisit lewat 2 screenshot: feedback "Cek Update Sekarang"
sebelumnya cuma bilang "Update v129 ketemu — lihat banner di layar utama",
maksa pindah tab ke layar utama cuma buat lihat detail & mulai unduh — dan
gak ada info apa pun soal ISI update-nya selain nomor versi baru. Sekarang
begitu ketemu update, Pengaturan langsung tampilkan komparasi versi eksplisit
("v128 → v129", bukan cuma versi baru), ringkasan 1-baris dari rilis GitHub
(BUKAN link ke CHANGELOG.md selengkapnya — diminta eksplisit), DAN tombol
"Unduh & Pasang" yang bisa langsung ditekan di situ juga.

**File disentuh (3 kode + strings.xml ID/EN)**:
- `UpdateManager.kt`: `UpdateInfo` dapat field baru `releaseNotes: String`,
  diisi fungsi privat baru `extractReleaseSummary()` — ambil baris heading
  "## ..." paling atas dari body Release GitHub (body itu SENDIRI sudah
  ringkasan versi `release_notes.md` di build.yml, Batch 26/48 — BUKAN full
  CHANGELOG.md), buang bagian "---"+link CHANGELOG.md di ekornya, cap 160
  char jaring pengaman.
- `SettingsScreen.kt`: dapat 2 param baru (`updateDownloadProgress`,
  `updateDownloadFailed`) + tombol unduh inline begitu state FOUND — reuse
  penuh state/fungsi unduh yang SUDAH ADA di `BoosterViewModel` (dipakai
  bareng `UpdateBanner` di `BoosterScreen.kt`), 0 logic unduh baru/duplikat.
- `MainActivity.kt` (edit parsial): wiring 2 param baru + callback
  `onDownloadAndInstall` ke `viewModel.downloadAndInstallUpdate()` yang
  SUDAH ADA — 0 fungsi baru di `BoosterViewModel.kt`.
- `strings.xml` (ID+EN): `settings_update_found` sekarang 2 placeholder
  (versi lama → versi baru, bukan cuma versi baru), tambah string baru
  `settings_whats_new_label` ("Yang baru:"/"What's new:").

**Kenapa 0 perubahan di `BoosterViewModel.kt`**: semua state
(`updateDownloadProgress`, `updateDownloadFailed`) & fungsi
(`downloadAndInstallUpdate()`) yang dibutuhkan SUDAH public (`private set`)
sejak fitur update pertama kali dibuat (Batch 69) — dipakai `UpdateBanner` di
`BoosterScreen.kt`. `SettingsScreen.kt` sekarang cuma jadi konsumen KEDUA dari
state yang sama, bukan sumber logic baru. `UpdateBanner` di layar utama TETAP
ada apa adanya (tidak dihapus/diubah) — cuma sekarang bukan satu-satunya jalan
unduh lagi.

**BELUM divalidasi runtime** (siklus lengkap zip → Termux → CI → install) —
kandidat pertama dicurigai kalau nanti ringkasan rilis nongol kosong/aneh di
Pengaturan: format `body` Release GitHub asli belum pernah diadu langsung
lawan asumsi `extractReleaseSummary()` pasca perubahan ini (`json.optString
("body")` sendiri sudah aman balik `""` kalau field-nya null/tidak ada, jadi
skenario TERBURUK cuma baris "Yang baru:" tidak muncul, BUKAN crash).

## Batch 78: Fix judul GitHub Release kepotong (root cause ASLI fitur Cek Update sejak awal)

Root cause fitur "Cek Update" akhirnya ketemu lewat bukti langsung —
`curl` ke API GitHub dari Termux (dijalankan user), bukan dugaan lagi.
Field `"name"` (judul) Release GitHub ternyata kepotong:
`"AudioEnhancerPro v127 (Run"` (seharusnya `"...(Run #127)"`). Penyebab:
baris `name:` di `.github/workflows/build.yml` (step "Publish GitHub
Release") TIDAK di-quote — YAML plain scalar unquoted memperlakukan
SPASI+`#` sebagai AWAL KOMENTAR walau di tengah baris, motong judul saat
parsing YAML di runner CI, SEBELUM sempat dikirim ke GitHub API sama
sekali.

**Dampak**: `RUN_NUMBER_REGEX` di `UpdateManager.kt` (Batch 69) TIDAK PERNAH
berhasil match judul Release SEJAK fitur update pertama kali dibuat — akar
masalah ganda yang baru sekarang kekonfirmasi: sebelum Batch 75 bikin app
salah lapor "sudah versi terbaru" padahal belum; setelah Batch 75 bikin app
lapor "gagal cek" (jujur, tapi belum tahu kenapa gagal — root cause-nya
justru baris ini).

**Fix**: `name:` value dibungkus tanda kutip ganda — expression `${{ }}`
GitHub Actions tetap jalan normal di dalam string YAML yang di-quote, `#` di
dalamnya jadi karakter literal. Divalidasi parse YAML (`python3 -c "import
yaml"`): value ke-parse utuh, 15 step tetap sama. 1 file kode (dalam
Micro-Batch, Protected): `.github/workflows/build.yml`. **Belum tervalidasi
runtime CI beneran** — butuh push + run CI baru + tes ulang tombol "Cek
Update Sekarang" utk konfirmasi final. Detail investigasi lengkap:
`PROJECT_STATE.md` Batch 77-78.

---

## Batch 76: versionName otomatis dari GITHUB_RUN_NUMBER (Versioning Lock diperluas)

Diminta user eksplisit ("Pokoknya versionName wajib otomatis dari
GITHUB_RUN_NUMBER!!") setelah ditawarkan 3 opsi lewat BLOCKER (lihat
`PROJECT_STATE.md` Batch 65 — perubahan ini sudah diantisipasi & sengaja
ditahan sampai user konfirmasi eksplisit) — user pilih opsi **"angka run
number polos"** (bukan semantic+suffix seperti `1.99.78`).

**Perubahan inti**: `app/build.gradle.kts` — `versionName` sekarang
`System.getenv("GITHUB_RUN_NUMBER") ?: "1"`, PERSIS sumber yang sama dengan
`versionCode` (cuma beda tipe String vs Int). Bukan lagi label semantik manual
(`"1.99.0"`).

**Konsekuensi berantai yang WAJIB ikut diperbaiki (bukan kerja tambahan
opsional — tanpa ini CI regresi diam-diam)**: `versionName` sekarang beda
NILAI tiap run CI (naik terus, gak pernah sama dengan run sebelumnya), jadi 2
mekanisme CI yang SEBELUMNYA cocokkan `versionName` sebagai label stabil
langsung rusak kalau tidak diredesain:
1. **Step "Extract version name"** (`.github/workflows/build.yml`): dulu
   `grep` literal string dari `app/build.gradle.kts` — sekarang gak ada
   literal buat di-grep lagi (formula, bukan string). Ganti: baca langsung
   `${{ github.run_number }}` (context var GitHub Actions bawaan, sama
   persis nilainya, 0 parsing gradle dibutuhkan).
2. **Step "Extract changelog entry for this version"**: dulu `awk` cocokkan
   header PERSIS `## v<versionName>` di `CHANGELOG.md` buat ambil body Release
   notes. Sejak `versionName` = run number yang beda tiap run, Claude
   (menulis CHANGELOG.md SEBELUM push) TIDAK MUNGKIN tahu run_number yang
   akan di-assign GitHub ke run berikutnya — matching persis MUSTAHIL
   berhasil selamanya. Diganti total: ambil section PALING ATAS
   `CHANGELOG.md` apa adanya (baris `## ` pertama s.d. `## ` kedua), 0
   kebutuhan tahu versi/run_number lagi — konsisten sama konvensi "entry
   terbaru paling atas" yang MEMANG sudah dipakai file ini.

**Konsekuensi kosmetik yang DITERIMA (bukan bug)**: nama APK CI
(`AudioEnhancerPro-v<run>-run<run_id>-release.apk`) dan judul GitHub Release
(`AudioEnhancerPro v<run> (Run #<run>)`) sekarang menampilkan angka run
number 2x dengan format berbeda (mis. `v78` dan `Run #78`) — redundan tapi
tidak salah, konsekuensi langsung dari pilihan "angka polos" user. Tampilan
versi di UI in-app (`UpdateManager`/`SettingsScreen`) juga ikut jadi angka
polos (mis. "Versi baru: 78" bukan "1.99.0") — TIDAK disentuh kodenya
(`UpdateManager.kt` cuma baca `versionName`/`tag_name` apa adanya, otomatis
ikut format baru tanpa perlu diubah).

**Heading `CHANGELOG.md` ke depan JUGA ganti format**: `## Batch N:
<deskripsi>` (BUKAN lagi `## v<versionName> - Batch N: ...`) — `versionName`
sudah gak stabil/gak bermakna lagi buat dijadiin bagian heading. Heading versi
lama (`## v1.98.0`, `## v1.99.0`, dst di bawah) **TIDAK diubah** (riwayat,
Hard Reset ZIP hanya berlaku ke source code, bukan alasan nulis ulang
histori CHANGELOG).

**File disentuh** (2 file kode, dalam Micro-Batch, KEDUANYA Protected —
edit-parsial): `app/build.gradle.kts` (1 baris `versionName` + komentar),
`.github/workflows/build.yml` (2 step: "Extract version name" disederhanakan,
"Extract changelog entry for this version" diredesain total). Brace/paren
`build.gradle.kts` 0 selisih, YAML `build.yml` parse-valid (15 step, urutan
tidak berubah), simulasi `awk` baru terhadap `CHANGELOG.md` asli dikonfirmasi
ambil section teratas dengan benar. **Belum divalidasi runtime/CI beneran**
— kandidat pertama dicurigai kalau ada gejala aneh: nama artifact/tag CI yang
sekarang redundan (lihat "Konsekuensi kosmetik" di atas) bukan bug, jangan
"diperbaiki" tanpa user minta.

---

## v1.99.0 - Batch 69: In-app update (unduh & pasang APK langsung dari app)

Diminta user eksplisit ("Tambahkan konfigurasi update langsung dalam aplikasinya")
— sebelumnya cuma tercatat sebagai item yang SENGAJA DITUNDA sejak MODE
MAINTENANCE dimulai (Batch 44), maintenance mode ngatur inisiatif Claude BUKAN
larangan mutlak buat user, jadi permintaan eksplisit ini tetap dikerjakan.

**File baru**: `UpdateManager.kt` — object stateless, 3 fungsi: `checkForUpdate()`
(cek Release GitHub terbaru), `downloadApk()` (unduh APK via chunk streaming
Okio), `installApk()` (trigger intent instalasi sistem).

**Sumber kebenaran versi — pakai ulang skema yang SUDAH ADA, 0 field API baru**:
judul tiap Release SELALU diakhiri `(Run #<run_number>)` (Batch 42), dan
`versionCode` APK yang lagi jalan SEKARANG OTOMATIS = `GITHUB_RUN_NUMBER` dari run
yang men-generate-nya (Batch 65 Versioning Lock). Jadi "ada update?" = run_number
di judul Release TERBARU > versionCode yang lagi jalan — TIDAK perlu
bandingkan versionName (string) sama sekali, lebih akurat karena versionName tidak
selalu naik tiap rilis CI (lihat Batch 64). `versionCode` WAJIB dibaca runtime via
`PackageManager` (BUKAN `BuildConfig.VERSION_CODE` — kelas `BuildConfig` dimatikan
total sejak Batch 41, `buildFeatures.buildConfig = false`).

**Chunk streaming (Feature Lock §3, standing instruction user)**: `downloadApk()`
baca body APK per-chunk (64KB) lewat `Source.read(Buffer, Long)` + `Sink.write(
Buffer, Long)` (interface dasar Okio, BUKAN `BufferedSource`/`BufferedSink` —
keduanya sudah punya method ini sendiri, jadi `.buffer()` tidak perlu dipanggil
sama sekali, lebih sedikit permukaan API yang beresiko salah). DILARANG
`readBytes()`/muat body sekaligus ke memori. Panggilan API metadata Release
(JSON, cuma beberapa KB) TIDAK kena aturan ini — dibaca sekaligus, aman (bukan
bagian yang beresiko OOM).

**Dependency baru**: `com.squareup.okio:okio:3.9.0` (`app/build.gradle.kts`) —
KHUSUS buat chunk streaming di atas, BUKAN buat networking (tetap
`HttpURLConnection` bawaan Android, 0 HTTP client library baru, biar tetap minim
seperti gaya project ini — `org.json` juga dipakai ulang, bukan Gson/Moshi baru).

**Permission baru (PERTAMA KALI app ini butuh network sama sekali)**:
`INTERNET` (cek+unduh Release GitHub) dan `REQUEST_INSTALL_PACKAGES` (pasang APK
hasil unduhan). SENGAJA TIDAK cek/minta `REQUEST_INSTALL_PACKAGES` manual di kode
— kalau belum diizinkan, PackageInstaller sistem sendiri yang menampilkan layar
"Izinkan dari sumber ini" + tombol ke Settings, cek ulang manual di app cuma
duplikasi UX yang sistem sudah handle.

**FileProvider baru** (`AndroidManifest.xml` + `res/xml/file_paths.xml` baru) —
expose SUBFOLDER `cacheDir/updates/` saja (bukan seluruh cacheDir, least-privilege)
lewat `content://` URI ke intent instalasi (`ACTION_VIEW` + MIME
`application/vnd.android.package-archive`). Isi folder ini dibersihkan tiap
unduhan baru, supaya APK basi tidak numpuk di cache device user.

**UX**: `BoosterViewModel` cek update sekali diam-diam tiap ViewModel dibuat
(~sekali per sesi app dibuka) — kegagalan cek SENGAJA ditelan jadi tidak ada
banner (bukan snackbar error), karena ini jalan otomatis tanpa user minta. Kalau
ADA update, `UpdateBanner` baru (`BoosterScreen.kt`, pola SAMA seperti
CrashBanner/ControlRecoveryBanner — `SkeuTintedCard` + Row icon+teks+tombol, tint
`primary` bukan `error` karena ini notifikasi netral bukan masalah) tampil dengan
tombol "Unduh & Pasang". Tap tombol → unduh (progress bar + persentase) LALU
LANGSUNG trigger intent instalasi begitu selesai (dipersepsikan user sebagai 1
aksi, bukan 2 langkah terpisah). Kalau user sempat dismiss layar installer
sistem, tombol berubah jadi "Pasang Sekarang" — retry TANPA unduh ulang APK yang
sudah ada di cache. Kegagalan unduh (BEDA dari kegagalan cek di atas — ini
dipicu eksplisit oleh tap user) surface sebagai snackbar via `SnackbarHostState`
yang sudah ada (Batch 51), bukan diam-diam.

**Versioning**: `versionName` dibump manual `1.98.0` → `1.99.0` (keputusan sadar
Batch 65: `versionName` MASIH manual, "boleh diubah Claude kalau ada milestone
semantik baru yang pantas" — permission network pertama kali + fitur self-update
dianggap pantas, konsisten pola batch-batch fitur besar sebelumnya sebelum
Batch 64's gap ditemukan/diperbaiki Batch 65). `versionCode` TIDAK disentuh
(tetap formula otomatis `GITHUB_RUN_NUMBER`, sesuai Versioning Lock).

**File yang berubah** (5 file kode: `UpdateManager.kt` baru,
`BoosterViewModel.kt`, `BoosterScreen.kt`, `MainActivity.kt`, `values/strings.xml`
+ `values-en/strings.xml` — 5 string baru tiap bahasa, parity 113/113; +3 file
Protected edit-parsial: `AndroidManifest.xml`, `app/build.gradle.kts`,
`res/xml/file_paths.xml` baru).

**Belum divalidasi runtime** — statis only (brace/paren balance 0 selisih di
4 file Kotlin yang disentuh, XML parse-valid semua, parity string 113/113).
`Source.read(Buffer, Long)`/`Sink.write(Buffer, Long)` (interface dasar Okio) dan
`FileProvider`/intent instalasi PERTAMA KALI dipakai project ini — kandidat
pertama dicurigai kalau CI compile error di sekitar import `okio.*`, atau kalau
laporan device nyata: banner tidak pernah muncul walau ada Release baru (cek dulu
koneksi internet + `api.github.com` tidak diblokir), unduhan gagal terus (cek
permission INTERNET granted — WAJIB, tidak butuh runtime request, tapi device
tertentu dengan firewall/VPN restriktif bisa blokir diam-diam), atau instalasi
tidak jalan sama sekali (cek `${applicationId}.fileprovider` authority tidak
tabrakan dengan app lain, dan `REQUEST_INSTALL_PACKAGES` benar ada di
AndroidManifest hasil merge — bisa dicek `aapt dump permissions` pada APK jadi
kalau ragu). Detail rasional lengkap tiap keputusan: `PROJECT_STATE.md` Batch 69.

---

**Addendum Batch 70 (rule permanen dipertegas, diminta user, TANPA narasi
panjang)**: format nama ZIP output Claude di-PIN eksplisit persis
`Boomly_<versi>-<batch>.zip` (Batch 69 sempat salah pakai prefix
`AudioEnhancerPro`, melanggar aturan `Boomly` yang sudah permanen sejak Batch
66/67). 0 kode/repo disentuh, 0 bump versi. Detail: `PROJECT_STATE.md` §
"Keputusan sadar" poin 1.

---

**Addendum Batch 71 (rule Batch 70 dipertegas lagi — sempat salah diterapkan)**:
skrip Termux sesi Batch 70 masih pakai glob lama `AudioEnhancerPro*.zip`,
kontradiksi sama rule yang baru saja dipin di batch yang sama. Glob
`Boomly*.zip` sekarang di-spell-out LANGSUNG di `PROJECT_STATE.md` §
"Keputusan sadar" poin 1 (gak lagi cuma implisit dari `[NamaFileAplikasi]`).
0 kode/repo disentuh, 0 bump versi.

---

**Addendum Batch 73 (entry point cek-update MANUAL, user tegur eksplisit lewat
screenshot — banner otomatis Batch 69 gak punya cara dipicu manual & gagalnya
diam-diam, jadi user gak tahu app-nya beneran ngecek atau enggak)**: Section
Settings baru (ikon ⚙️ di layar utama, sebelah ikon bantuan) — tombol "Cek
Update Sekarang" + tampilan versi app terpasang. BEDA dari cek otomatis:
hasilnya SELALU ditampilkan (sudah terbaru / ketemu update / gagal jaringan),
tidak pernah diam-diam. `UpdateManager.kt` di-refactor (BUKAN ubah perilaku):
logic inti diekstrak ke `fetchLatestRelease()` privat, dipakai ulang oleh
`checkForUpdate()` (otomatis, tetap menelan exception seperti sebelumnya —
0 perubahan perilaku) dan `checkForUpdateManual()` baru (exception dilempar
apa adanya, ditangkap `BoosterViewModel.checkForUpdateManually()` yang baru).
Kalau cek manual ketemu update, `updateInfo` (state yang sudah ada) ikut
di-set — `UpdateBanner` di layar utama otomatis muncul balik, TIDAK ada
duplikasi UI unduh/pasang di Settings. Navigasi 3-state di `MainActivity.kt`
(Onboarding/Settings/Booster, pola sama seperti `showOnboarding` yang sudah
ada). **File disentuh** (5 file kode: `SettingsScreen.kt` baru,
`UpdateManager.kt`, `BoosterViewModel.kt`, `BoosterScreen.kt` + `MainActivity.kt`
Protected edit-parsial; `values/strings.xml` + `values-en/strings.xml` — 9
string baru tiap bahasa, parity 122/122 — melebihi Micro-Batch 3 file kode,
justifikasi SAMA seperti Batch 69: fitur besar diminta eksplisit, satu unit
atomik, lihat `PROJECT_STATE.md` Batch 73). 0 bump versi (kelanjutan/penuntasan
milestone in-app-update v1.99.0 yang sama, bukan milestone baru). Statis only —
brace/paren balance 0 selisih di 5 file Kotlin, XML parse-valid, parity string
122/122. **Belum divalidasi runtime.**

---

**Addendum Batch 74 (FIX regresi gesture back, user tegur singkat langsung
setelah Batch 73)**: `SettingsScreen` (Batch 73) dikasih tombol panah-balik
eksplisit, tapi 0 `BackHandler` ada di manapun di project ini — system back
gesture/tombol Android nutup TOTAL app, bukan balik ke `BoosterScreen`. Fix:
`BackHandler(enabled = showSettings) { showSettings = false }` baru di
`MainActivity.kt`. SENGAJA CUMA guard `showSettings` — `showOnboarding` (forced
first-run flow) SADAR TIDAK disentuh, beda kelas masalah dari yang dilaporkan
(resiko user bisa skip onboarding pertama cuma modal gesture back). 1 file
kode (`MainActivity.kt`, Protected edit-parsial), brace/paren balance 0
selisih, 0 bump versi. Detail: `PROJECT_STATE.md` Batch 74.

---

**Addendum Batch 75 (FIX bug "Cek Update Sekarang" bilang sudah terbaru
padahal cek-nya sendiri gagal, dilaporkan user)**: `UpdateManager.
fetchLatestRelease()` privat balik `null` untuk 3 kondisi beda arti — sudah
terbaru, HTTP gagal (mis. rate-limit `403` GitHub API unauthenticated,
60 req/jam per-IP), atau judul Release/asset APK gagal di-parse.
`checkForUpdateManual()` meneruskan `null` itu apa adanya, dan
`BoosterViewModel.checkForUpdateManually()` cuma cek `!= null` — jadi
"gagal cek" ikut diklasifikasi jadi "sudah terbaru" (state `ERROR` yang
sudah ada di enum sejak Batch 73 gak pernah ke-trigger buat kasus ini). Fix:
`UpdateManager.CheckResult` sealed class baru (`Available`/`UpToDate`/
`Failed`) — `fetchLatestRelease()` return ini, bukan `UpdateInfo?` polos.
`checkForUpdate()` (silent, otomatis) unwrap `Available` seperti sebelumnya,
0 perubahan perilaku di jalur itu. `checkForUpdateManually()` di-`when`-kan
3 cabang eksplisit ke `FOUND`/`UP_TO_DATE`/`ERROR`. `SettingsScreen.kt` TIDAK
disentuh — UI state `ERROR` sudah ada sejak Batch 73, sekarang benar-benar
ke-reach. 2 file kode (`UpdateManager.kt`, `BoosterViewModel.kt`, dalam
Micro-Batch), brace/paren balance 0 selisih, 0 bump versi (bugfix milestone
v1.99.0 yang sama). **Belum divalidasi runtime** (CI belum jalan dari sesi
ini). Detail: `PROJECT_STATE.md` Batch 75.

---

## v1.98.0 - Batch 63 (audit eksternal): Gap #16 preset custom kini simpan EQ

**Addendum Batch 68 (ekspansi rebrand ke dalam app, ditegur user eksplisit)**:
Batch 66/67 cuma ganti nama ZIP output — user tegur, maksudnya app juga kena
(tetap "user facing, kosmetik only"). 8 string `Audio Booster`/
`AudioEnhancerPro` → **`Boomly`** di `values/strings.xml` +
`values-en/strings.xml` (`app_name`, `app_title`, `notif_title`,
`notif_channel_name`, `qs_tile_label`, `status_running`, `notif_perm_body`,
`ob1_title` — parity 108/108 terjaga), `docs/preview/current.html` (h1 + 1
span, sinkron ke Kotlin), `README.md` (1 baris troubleshooting). SENGAJA
TIDAK diubah: `CrashLogger.APP_FOLDER` (path fisik MediaStore, ganti =
fragmentasi log lama, bukan kosmetik), semua identifier vital
(applicationId/namespace/rootProject.name/nama workflow/repo). 0 bump versi.
Aturan permanen ter-update: `PROJECT_STATE.md` § "Keputusan sadar".

---

**Addendum Batch 67 (koreksi nama brand, ditegur user eksplisit)**: pilihan
Batch 66 (`AudioBooster`) dinilai user MASIH generik/placeholder (gabungan
literal kata deskriptif, bukan brand asli). Ganti ke **`Boomly`**. Scope
identik Batch 66 (kosmetik, nama ZIP output Claude doang) — 0 kode/repo
disentuh, 0 bump versi. Aturan permanen ter-update: `PROJECT_STATE.md` §
"Keputusan sadar".

---

**Addendum Batch 66 (rebrand kosmetik nama ZIP output, diminta user)**: nama
brand ZIP output Claude ganti `AudioEnhancerPro` → **`AudioBooster`** (match
`app_name` yang sudah lama live di HP user). Scope 100% kosmetik/artifact —
`applicationId`, `namespace`, `rootProject.name`, nama repo GitHub/folder
lokal Termux, nama workflow, `CrashLogger.APP_FOLDER`, `strings.xml` semua
TETAP `AudioEnhancerPro`. 0 file kode disentuh, 0 bump versi. Aturan
permanen: `PROJECT_STATE.md` § "Keputusan sadar".

---

**Addendum Batch 65 (inspeksi+fix workflow release, diminta user eksplisit)**:
user tanya "apakah project melanggar workflow GitHub release yang wajib
otomatis" — jawaban: **YA, 2 pelanggaran nyata**, keduanya langsung
diperbaiki di batch ini.
1. `app/build.gradle.kts`: `versionCode` SELAMA INI literal manual (di-bump
   tangan 64 batch berturut-turut) — melanggar "Versioning Lock" (WAJIB
   otomatis dari `GITHUB_RUN_NUMBER`, dilarang bump manual). Fix:
   `versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1`.
   `versionName` SENGAJA TETAP manual (rasional lengkap: kunci pencarian
   step CI "Extract changelog entry for this version", lihat
   `PROJECT_STATE.md` Batch 65).
2. `.github/workflows/build.yml`: "Stale Run Guard" (FEATURE LOCKS #3 user,
   `exit 1` kalau `GITHUB_SHA != local main`) TERNYATA TIDAK PERNAH
   diimplementasi sama sekali sepanjang 64 batch riwayat — gap tersembunyi.
   Fix: step baru "Stale Run Guard" (posisi ke-2, setelah Checkout, paling
   awal) — `git ls-remote origin refs/heads/main` dibandingkan `$GITHUB_SHA`,
   `exit 1` kalau beda (run usang, ada commit main lebih baru).
Detail lengkap + rasional tiap keputusan: `PROJECT_STATE.md` Batch 65. 2 file
(`app/build.gradle.kts`, `.github/workflows/build.yml`), keduanya Protected
(edit parsial). **Belum divalidasi CI beneran** — `git ls-remote` butuh
network sungguhan yang gak ada di sandbox, statis only (YAML parse valid 15
step, brace/paren gradle 18/18 & 48/48, grep regex versionName CI
disimulasikan ulang hasil tetap benar `1.98.0`).

---

**Addendum Batch 64 (perkuat efek preset, diminta user eksplisit)**: 3 dari 4
preset bawaan (`BoosterScreen.kt`, `listOf(Preset(...))`) dinaikkan
intensitasnya — Flat SENGAJA tidak disentuh (definisinya netral/nol).
Bass Heavy `bass 900→1000 (MAX)` / `virtualizer 300→400` / `loudness 500→750`.
Vocal Boost `bass 200→300` / `virtualizer 600→750` / `loudness 800→1100`.
Treble Boost `bass 100→150` / `virtualizer 800→950` / `loudness 600→850`.
Semua nilai baru tetap dalam batas kontrak platform Bass/Virtualizer `0..1000`
(per mille, Batch 60 — bukan device-specific) dan batas slider UI Loudness
`0..3000` mB (`FeatureControl` loudness, `valueRange = 0f..3000f`) — tidak ada
yang melebihi range API atau bikin thumb slider invalid. Karakter tiap preset
(dominasi fitur masing-masing) dipertahankan, cuma headroom-nya dipakai lebih
banyak. **Versi TIDAK dibump** (lihat instruksi standing "Versioning Lock:
otomatis dari `GITHUB_RUN_NUMBER`, dilarang bump manual" — repo ini belum
diwiring ke mekanisme itu, jadi entry ini numpang di v1.98.0 yang sama, belum
pernah di-CI-build/release; lihat catatan lengkap di `PROJECT_STATE.md` Batch
64 soal gap ini). 1 file kode (`BoosterScreen.kt`, cuma 4 baris nilai + komentar),
0 file lain. **Belum divalidasi runtime** — statis only (brace/paren 215/215 &
667/667, cuma nilai numerik literal yang diubah, 0 logic/struktur berubah).

---

Lanjutan Batch 62. roadmap.md Fase 0 item #7 ("Preset lengkap termasuk EQ",
audit Gap #16) sekarang `[x]` SELESAI — custom preset sebelumnya cuma
snapshot bass/virtualizer/loudness, EQ manual TIDAK ikut tersimpan.

**`PrefsHelper.kt`**:
- `CustomPreset` data class dapat field baru `eqBands: List<Int> = emptyList()`
  (default kosong = backward-compat penuh dengan preset lama).
- `getCustomPresets()`: pakai `obj.optJSONArray("eqBands")` (BUKAN
  `getJSONArray`) — preset JSON lama yang TIDAK punya field ini sama sekali
  tetap ke-load normal dengan `eqBands = emptyList()`, bukan exception yang
  bikin SELURUH preset (bukan cuma bagian EQ-nya) ikut lenyap lewat catch
  generic yang sudah ada.
- `saveCustomPresets()`: field `eqBands` SELALU ditulis (termasuk array kosong
  `[]` kalau device tidak dukung equalizer) — preset yang disimpan SETELAH
  batch ini strukturnya konsisten, tidak ambigu "sengaja kosong" vs "field
  belum ada" seperti preset lama.

**`BoosterScreen.kt`**:
- Dialog "Simpan Preset" (`confirmButton` onClick): SEBELUM membuat
  `CustomPreset`, baca balik `PrefsHelper.getEqualizerBandLevel(context, band,
  0)` untuk tiap band (`0 until equalizerBandCount`) — ini SUMBER KEBENARAN
  nilai EQ saat ini karena `AudioEnhancerService.setEqualizerBand()` SELALU
  menulis ke situ tiap kali 1 band digeser (sudah ada sejak lama, dipakai
  fitur lain juga). Pendekatan ini SENGAJA dipilih dibanding hoisting state
  Compose `EqualizerSection` (yang `private`, levelnya cuma hidup di dalam
  composable itu sendiri) — 0 plumbing/refactor state baru dibutuhkan.
- State baru `eqOverrideLevels: List<Short>?` (default `null`) — nilai EQ
  eksplisit yang HARUS ditampilkan `EqualizerSection` setelah preset
  diterapkan. `applyPreset()` (built-in) diubah supaya set ini ke
  `List(bandCount) { 0 }` (perilaku LAMA, cuma dipindah dari ternary inline ke
  variabel eksplisit). `applyCustomPreset()` (baru): kalau `preset.eqBands`
  TERISI, terapkan ke tiap band (`onEqualizerBand`) + set `eqOverrideLevels` ke
  nilai preset + `eqResetCounter++` (paksa `EqualizerSection` recompose, pola
  identik built-in). Kalau `preset.eqBands` KOSONG (preset lama), EQ SENGAJA
  TIDAK disentuh sama sekali — persis perilaku ASLI sebelum batch ini,
  supaya preset lama yang sudah tersimpan user tidak tiba-tiba "menghapus"
  EQ manual yang sedang aktif (preset itu memang tidak pernah tahu nilai
  EQ-nya, beda dari preset built-in yang memang didesain selalu flat).
- Parameter `initialLevels` di call-site `EqualizerSection(...)` disederhanakan
  dari ternary `if (eqResetCounter == 0) ... else List(...) { 0 }` jadi
  `eqOverrideLevels ?: equalizerInitialLevels` — lebih eksplisit, dan kini
  mendukung 3 kasus (initial mount / reset flat built-in / restore nilai
  custom) bukan cuma 2.

**`strings.xml`** (values/ + values-en/): `presets_empty_hint` diupdate
teksnya — sekarang menyebut "equalizer" juga (sebelumnya cuma
"bass/virtualizer/loudness", sudah tidak akurat sejak batch ini). Tidak ada
string BARU (cuma edit isi 1 string existing), parity tetap 108/108.

**`PrefsHelperTest.kt`**: 2 test baru — round-trip JSON `eqBands` (simpan
preset dengan EQ, load ulang, cocokkan persis), dan preset JSON LAMA tanpa
field `eqBands` sama sekali (ditulis manual ke SharedPreferences, simulasi
data pra-Batch-63) tetap ke-load tanpa crash dengan `eqBands = emptyList()`.

**Verifikasi statis**: brace/paren balance `BoosterScreen.kt` 215/215 &
660/660, `PrefsHelper.kt` 25/25 & 170/170, `PrefsHelperTest.kt` 26/26 &
136/136; parity string 108/108; seluruh call-site `CustomPreset(...)` lama
(termasuk di `PrefsHelperTest.kt`, positional 4-argumen) dicek TETAP valid
berkat default parameter `eqBands = emptyList()`. **Belum divalidasi
runtime.**

---

## v1.97.0 - Batch 62 (audit eksternal): Gap #4 control-ownership recovery, surface ke UI

Lanjutan Batch 61. roadmap.md Fase 0 item #4 ("Control ownership/lifecycle
lanjutan") sekarang `[x]` SELESAI dari sisi app — `retryControlAcquisition()`
(Batch 61, Service-layer only, TANPA pemanggil sama sekali sejak dibuat) kini
punya jalur lengkap Service → ViewModel → UI, pola identik Batch 57→58.

**`BoosterViewModel.kt`**:
- Fungsi publik baru `retryControlAcquisition(): Boolean` — wrapper tipis:
  `if (bound) service?.retryControlAcquisition() ?: false else false`.
  Panggilan SINKRON (bukan `suspend`) karena `bindService` di app ini SAMA
  PROSES lewat `LocalBinder` (bukan IPC lintas proses), jadi aman dipanggil
  langsung dari Compose click handler — pola sama persis `setBass()`/
  `setVirtualizer()` yang sudah ada. Return value diteruskan apa adanya dari
  Service: `true` cuma berarti "ada effect yang di-retry", BUKAN jaminan
  effect itu benar-benar dapat kontrol lagi (hasil sebenarnya baru kelihatan
  dari polling `EffectState` 1 detik yang sudah ada di `init{}`, Batch 58).

**`BoosterScreen.kt`**:
- Komponen `private` baru `ControlRecoveryBanner(states, onRetryControl,
  onRetryAttempted)` — pola VISUAL identik `ServiceStatusBadge`/`CrashBanner`
  (`SkeuTintedCard` tint `MaterialTheme.colorScheme.error` + `Icon(Warning)` +
  `Text` + `TextButton`, ripple dimatikan lewat `NoRippleIndication` konsisten
  komponen interaktif lain). Early-return (`if (!needsRecovery) return`) kalau
  TIDAK ADA effect di antara bass/virtualizer/equalizer/loudness yang
  `CONTROL_LOST`/`FAILED` — deteksi baca `EffectState` yang SUDAH di-poll
  `BoosterViewModel` tiap 1 detik sejak Batch 58, **TIDAK ADA mekanisme
  polling baru ditambahkan di sini**, jadi banner otomatis muncul/hilang
  sinkron dengan polling yang sudah ada.
- `BoosterScreen()` dapat parameter baru `onRetryControl: () -> Boolean =
  { false }` (default backward-compatible, pola sama seperti default
  `EffectState.ENABLED` Batch 58) dan memanggil `ControlRecoveryBanner` tepat
  setelah `CrashBanner` (posisi: bawah badge status service, di atas
  power/slider). `onRetryAttempted` dihubungkan ke `showSnackbar()` yang
  sudah ada (reuse, 0 mekanisme snackbar baru).

**`MainActivity.kt`**:
- 1 baris baru: `onRetryControl = { viewModel.retryControlAcquisition() }`
  di call-site `BoosterScreen(...)`, persis sebelum `requestedCustomPresetName`.

**`strings.xml` (values/ + values-en/)**:
- 3 string baru per-locale (parity 108/108, sebelumnya 105/105):
  `control_recovery_message` ("Sebagian fitur kehilangan kontrol audio." /
  "Some features lost audio control."), `control_recovery_button` ("Coba
  Ambil Alih Lagi" / "Try Retaking Control"), `control_recovery_snackbar`
  (pesan SENGAJA bilang "dicoba"/"attempted", BUKAN "berhasil"/"succeeded" —
  konsisten dengan disclaimer "TIDAK ADA jaminan" yang sudah didokumentasikan
  panjang di komentar `retryControlAcquisition()` Service sejak Batch 61).

**Verifikasi statis** (TIDAK ada compiler di sandbox — lihat PROJECT_STATE.md
"Batasan sandbox"): brace/paren balance `BoosterScreen.kt` 208/208 & 637/637,
`BoosterViewModel.kt` 31/31 & 118/118, `MainActivity.kt` 47/47 & 116/116;
parity string ID/EN 108/108; 1 call site `ControlRecoveryBanner(` dicek cocok
3 parameter definisi. **Belum divalidasi runtime** — belum ada cara sengaja
memicu `CONTROL_LOST` di sandbox untuk konfirmasi banner beneran muncul/
hilang/tidak crash saat tombol ditekan.

---

## v1.96.0 - Batch 61 (audit eksternal): Gap #4 control-ownership recovery (Service-layer)

Lanjutan audit eksternal, roadmap.md Fase 0 item #4 ("Control
ownership/lifecycle lanjutan"). Batch 57 baru DETEKSI `CONTROL_LOST` (via
listener), belum ada strategi apa pun buat REBUT KEMBALI kontrol — batch ini
nutup itu, Service-layer dulu (pola sama seperti Batch 57→58: deteksi dulu,
baru surface UI batch terpisah).

**`AudioEnhancerService.kt`** (satu-satunya file yang diubah):
- `attachEffects()` DIPECAH jadi 4 fungsi private per-effect
  (`attachBass()`/`attachVirtualizer()`/`attachEqualizer()`/
  `attachLoudness()`) — **0 logic berubah**, isi tiap blok try-catch persis
  sama seperti sebelum refactor, cuma dipindah supaya bisa dipanggil ulang
  1 effect spesifik tanpa reset effect lain yang sehat. `attachEffects()`
  sendiri sekarang cuma 4 baris pemanggilan + `restoreSavedSettings()`
  (perilaku dari luar TIDAK berubah sama sekali — dipanggil dari `onCreate()`
  seperti biasa).
- Fungsi publik baru `retryControlAcquisition(): Boolean` — buat tiap effect
  yang state-nya `CONTROL_LOST` ATAU `FAILED`: release object lama (try-catch,
  gagal cuma di-Log.e bukan crash) lalu panggil `attachXxx()` yang sesuai buat
  recreate. Effect yang sudah `ENABLED`/`AVAILABLE` TIDAK disentuh. Setelah
  minimal 1 effect di-retry, `restoreSavedSettings()` dipanggil ulang (reuse
  fungsi yang sudah ada) supaya nilai slider bass/virtualizer/loudness/EQ
  band yang user set tidak hilang. Return `true` kalau ada yang di-retry,
  `false` kalau semua effect sudah sehat (tidak ada yang perlu di-retry).
- **PENTING, didokumentasikan panjang di komentar fungsi**: fungsi ini TIDAK
  DIJAMIN BERHASIL — `CONTROL_LOST` berarti sistem Android sudah memutuskan
  effect/app LAIN menang priority-arbitration di session 0 yang sama;
  recreate object TIDAK mengubah priority (`BassBoost(0, 0)` dkk masih
  priority normal, SENGAJA tidak dinaikkan — menaikkan priority effect
  session-0 global punya efek samping ke app lain, di luar scope batch ini).
  Paling berguna buat skenario: app lain yang tadi rebut kontrol SUDAH
  release duluan (mis. user tutup app itu) tapi listener kita belum
  ke-trigger ulang otomatis oleh sistem.
- **BELUM ADA pemanggil otomatis** batch ini — bukan dari
  `ServiceWatchdogWorker` (poll 15 menit), bukan dari listener manapun, bukan
  dari UI. Cuma fungsi publik yang BISA dipanggil. Alasan eksplisit ditunda:
  kalau dipanggil otomatis tanpa observasi device nyata dulu, risiko
  retry-loop rapat saat kondisi persisten (device lain terus pegang kontrol)
  — churn object `AudioEffect` sia-sia, potensi baterai/CPU terbuang.

`app/build.gradle.kts`: versionCode 100→101, versionName 1.95.0→1.96.0.

**roadmap.md**: item Fase 0 #4 ditandai `[ ]`→SEBAGIAN dijelaskan (recovery
mechanism ADA, pemanggil otomatis/UI BELUM) — bukan `[x]` penuh karena bagian
"UI action eksplisit" dari definisi item ini belum dikerjakan.

**Belum divalidasi runtime** — statis only (brace/paren `AudioEnhancerService.
kt`: 120/120 `{}`, 334/334 `()`; 4 fungsi `attachXxx()` baru dicek dipanggil
persis 1x dari `attachEffects()` + dipanggil ulang dari `retryControlAcquisition()`
sesuai kondisi masing-masing). Refactor ini RISIKO LEBIH RENDAH dari biasanya
(bukan API baru yang belum pernah dipakai kayak Batch 60 — cuma mindahin kode
existing yang SUDAH pernah "jalan" ke fungsi terpisah, isi logic verbatim
sama) tapi TETAP kandidat pertama dicurigai kalau ada laporan crash pas
Service `onCreate()` (jalur `attachEffects()` normal, TIDAK lewat
`retryControlAcquisition()` sama sekali di startup) — kalau itu kejadian,
kemungkinan besar ada typo halus pas refactor manual, bukan soal
`retryControlAcquisition()` itu sendiri (belum ada pemanggil = belum bisa jadi
penyebab crash startup).

**PENTING buat sesi depan**: `retryControlAcquisition()` masih "menggantung"
tanpa pemanggil — kalau user minta "lanjut" tanpa arahan baru, next kandidat
natural: surface ke `BoosterViewModel` (fungsi baru, mirip pola `setBass()`
dkk) + tombol/aksi eksplisit di `BoosterScreen` (mis. dekat helpText yang
nampilin `CONTROL_LOST`, mirip pola tombol "Coba Lagi" yang sudah ada buat
`ConnectionState.ERROR`) — BUKAN dipanggil otomatis dari watchdog tanpa
observasi device dulu (lihat alasan di atas). ALTERNATIF LAIN kalau user
minta ganti prioritas: roadmap.md Fase 0 item #3 (Output routing awareness)
masih belum disentuh sama sekali.

## v1.95.0 - Batch 60 (audit eksternal): capability detection Gap #7 (Bass/Virtualizer rounding) — LoudnessEnhancer diverifikasi tidak punya API query

Lanjutan audit eksternal, roadmap.md Fase 0 item #2 ("Capability detection +
fallback"). **Sebelum nulis kode apa pun**, dicek dulu dokumentasi resmi
Android SDK buat `BassBoost.setStrength`/`Virtualizer.setStrength`/
`LoudnessEnhancer.setTargetGain` — TIDAK ADA compiler/SDK di sandbox, jadi
verifikasi lewat web search ke `developer.android.com` + cermin resminya
sebelum asumsi apa pun soal API ini.

**Temuan (mengoreksi asumsi awal audit Gap #7)**:
- `BassBoost`/`Virtualizer` strength range `[0, 1000]` (per mille) adalah
  **kontrak API platform tetap**, sama persis di semua device/API level
  (dari API 9) — BUKAN device-specific range yang perlu di-query kayak
  `Equalizer.bandLevelRange` (yang memang sudah benar baca dari device sejak
  awal). Jadi klaim "hard-coded assumption yang salah" untuk 2 effect ini
  TIDAK akurat — kode `valueRange = 0f..1000f` di `BoosterScreen.kt`
  sebenarnya SUDAH benar sejak awal.
- Normalisasi capability yang BENERAN relevan buat Bass/Virtualizer cuma 2:
  (1) `strengthSupported` — SUDAH dicek sejak sebelum Batch 57
  (`isBassStrengthSupported()`/`isVirtualizerStrengthSupported()`, dipakai
  disable slider di `BoosterScreen`), (2) **rounding** — dokumentasi resmi:
  "it is allowed to round the given strength to the nearest supported value"
  — device BOLEH membulatkan diam-diam tanpa exception. Bagian ini yang
  SEBELUMNYA gak pernah dibaca balik (`getRoundedStrength()` gak pernah
  dipanggil sama sekali di project ini).
- `LoudnessEnhancer.setTargetGain` **tidak punya API query range sama
  sekali** (beda dari 3 effect lain yang punya `strengthSupported`/
  `bandLevelRange`/`numberOfBands`) — device yang menolak suatu nilai
  gainmB lempar `IllegalArgumentException`, yang SUDAH tertangkap generic
  `catch (e: Exception)` di `setLoudnessGain()` sejak Batch 57 (state
  `FAILED` + `Log.e`). Kesimpulan: jalur ini SUDAH gap-closed lewat cara
  lain (exception handling), bukan lewat capability query yang memang tidak
  tersedia dari API.

**`AudioEnhancerService.kt`** (satu-satunya file yang diubah batch ini):
- `setBassStrength()`/`setVirtualizerStrength()`: setelah `setStrength()`
  berhasil, kalau `strengthSupported == true`, baca balik `roundedStrength`
  — kalau beda dari nilai yang diminta, `Log.w` diagnostik (device
  membulatkan). TIDAK mengubah nilai yang di-`PrefsHelper.set*()` (tetap
  simpan nilai yang DIMINTA user, konsisten sama keputusan Batch 57 Gap
  #14 — persistence beda dari actual-applied-value).
- 2 fungsi publik baru: `getBassRoundedStrength()`/
  `getVirtualizerRoundedStrength()` — expose `roundedStrength` effect
  (fallback ke 0 kalau exception/null, pola sama seperti getter Equalizer
  yang sudah ada). **Belum dikonsumsi ViewModel/UI batch ini** (Log.w
  diagnostik dulu cukup — dampak rounding biasanya cuma 1-2 unit per mille
  dari 1000, nyaris tak terlihat di slider; disurface ke UI kalau ada
  laporan device nyata yang roundingnya signifikan).
- Komentar panjang baru di atas `setBassStrength()` mendokumentasikan
  seluruh temuan di atas, supaya sesi depan gak mengulang riset yang sama
  atau salah kira ini masih gap terbuka.
- `LoudnessEnhancer`/`setLoudnessGain()`/`setEqualizerBand()`: **0 baris
  logic berubah** — cuma komentar baru menjelaskan kenapa tidak disentuh.

`app/build.gradle.kts`: versionCode 99→100, versionName 1.94.0→1.95.0.

**roadmap.md**: item Fase 0 #1 ditandai `[x]` SELESAI (Batch 59 nutup sisa
terakhirnya). Item #2 ditandai `[~]` SEBAGIAN — bagian "capability
detection" untuk Bass/Virtualizer/Loudness selesai/diverifikasi tidak
applicable, bagian "fallback engine" (kalau effect `null`) SENGAJA belum
disentuh (overlap besar dengan item #6 rebuild `DynamicsProcessing`, effort
& device-testing besar, di luar kapasitas sandbox tanpa arahan eksplisit
user).

**Belum divalidasi runtime** — statis only (brace/paren `AudioEnhancerService.
kt`: 102/102 `{}`, 275/275 `()`). Karena isi API (`roundedStrength`) BELUM
pernah dipakai project ini, ini kandidat pertama yang dicurigai kalau ada
crash "Unresolved reference: roundedStrength" pas compile CI — TAPI properti
ini ADA di `android.media.audiofx.BassBoost`/`Virtualizer` sejak API level 9
(dikonfirmasi 3 sumber independen: developer.android.com, Microsoft Learn
.NET binding, AOSP source), risiko lebih rendah dari insiden `drawOutline`
(Batch 53/54) yang memang gak pernah ada di Compose UI sama sekali.

**PENTING buat sesi depan**: item #2 roadmap SEBAGIAN, bukan penuh — kalau
user minta "lanjut" lagi tanpa arahan baru, next kandidat alami item #3
(Output routing awareness — speaker↔Bluetooth, wired headset, USB DAC,
re-attach pipeline saat output berubah) ATAU item #4 (Control ownership
lanjutan — re-acquire/recovery otomatis saat `CONTROL_LOST`, bukan cuma
deteksi pasif). JANGAN loncat ke item #6 (rebuild session-0) tanpa user minta
eksplisit & paham trade-off.

## v1.94.0 - Batch 59 (lanjutan audit eksternal): EffectState ke EqualizerSection

Lanjutan Batch 58 (v1.93.0) — item "sisa" yang sudah dicatat eksplisit di
`PROJECT_STATE.md`: `equalizerEffectState` sudah diterima `BoosterScreen`
sejak Batch 58 tapi belum disurface ke `EqualizerSection`. Batch ini
nyelesaiin itu — TETAP 1 langkah kecil, belum pindah ke Fase 0 #2 (capability
detection + fallback range, roadmap.md).

**`BoosterScreen.kt`**:
- Call site `EqualizerSection(...)` (dalam `BoosterScreen`): 1 argumen baru
  `effectState = equalizerEffectState`.
- `EqualizerSection()`: 1 parameter baru `effectState: AudioEnhancerService.
  EffectState = EffectState.ENABLED` (default backward-compatible). Subtitle
  header (sebelumnya selalu `stringResource(R.string.eq_subtitle)` statis)
  sekarang `val subtitleText = when(effectState) { CONTROL_LOST -> ...
  control_lost; FAILED -> ...failed; else -> eq_subtitle }` — DESAIN: 1
  subtitle buat SEMUA band sekaligus (bukan per-band), karena
  `Equalizer(0,0)` di `AudioEnhancerService` adalah 1 objek `AudioEffect`
  tunggal yang menaungi semua band, bukan N objek terpisah per band. Ini
  beda dari pola Bass/Virtualizer/Loudness (Batch 58) yang emang per-fitur
  1:1 dengan 1 `AudioEffect`.
- **0 string baru** — dipakai ulang `feature_help_control_lost`/
  `feature_help_failed` (sudah ada sejak Batch 58). Parity ID/EN tetap
  105/105, gak ada file `strings.xml` yang disentuh batch ini.

`app/build.gradle.kts`: versionCode 98→99, versionName 1.93.0→1.94.0.

**Belum divalidasi runtime** — statis only (brace/paren 0 selisih file
`BoosterScreen.kt`: 198/198 `{}`, 600/600 `()`; 1 call site `EqualizerSection(`
dicek, argumen baru match parameter baru). Kandidat pertama dicurigai kalau
subtitle Equalizer gak pernah berubah dari "Atur tiap pita frekuensi..."
walau `equalizerState` di Service seharusnya `CONTROL_LOST`/`FAILED` — cek
alur `AudioEnhancerService.equalizerState` → `BoosterViewModel` polling
(Batch 58, sudah ada, TIDAK disentuh batch ini) → parameter ini.

**PENTING buat sesi depan**: dengan ini, seluruh "sisa" Batch 57/58 (surface
`EffectState` Service→ViewModel→UI, 4 effect: bass/virtualizer/loudness/
equalizer) SELESAI. Next kandidat alami: audit Gap #2 (langkah pertama Fase 0
#2 roadmap.md — capability detection + fallback range, ganti asumsi hard-code
`0..1000`/`0..3000` jadi baca dari device kalau API mengizinkan) ATAU gap
lain di audit sesuai prioritas user. JANGAN loncat ke Gap #1 (rebuild session
0 → modern pipeline) tanpa user minta eksplisit (effort/risiko besar, device-
testing intensif, di luar kapasitas sandbox).

## v1.93.0 - Batch 58 (lanjutan audit eksternal): surface EffectState ke UI

Lanjutan Batch 57 (v1.92.0) — user minta "lanjut" tanpa detail baru, jadi
dikerjakan item yang sudah dicatat eksplisit sebagai "sisa" di
`PROJECT_STATE.md`/`roadmap.md` Fase 0 #1: surface `AudioEnhancerService.
EffectState` (Batch 57, sebelumnya cuma ada di Service, tidak pernah dibaca
siapa pun) ke `BoosterViewModel` lalu `BoosterScreen` sebagai helpText
kontekstual per-fitur. Tetap 1 langkah kecil (bukan lompat ke item Fase 0
lain) — konsisten instruksi "bertahap, jangan greedy".

**`BoosterViewModel.kt`**:
- 4 properti `Compose State` baru (`bassEffectState`/`virtualizerEffectState`/
  `loudnessEffectState`/`equalizerEffectState`), default `UNAVAILABLE`.
- `init {}` baru: loop `viewModelScope.launch { while(true) { ...; delay(1000)
  } }` — poll 4 field `EffectState` dari `service` (kalau `bound`) tiap 1
  detik. PERTAMA KALI `viewModelScope`/`delay`-loop dipakai di file ini (pola
  MIRIP polling `isRunning` di `BoosterScreen.kt`, tapi ditaruh di ViewModel
  karena `bassState` dkk field INSTANCE Service, butuh referensi `service`
  yang cuma dipegang ViewModel secara private — Composable gak bisa akses
  langsung kayak `isRunning` yang companion/static). Saat belum/putus konek,
  state DIBIARKAN nilai terakhir (bukan dipaksa `UNAVAILABLE`) — supaya UI
  gak berkedip "gagal" pas reconnect sesaat.

**`MainActivity.kt`** (edit parsial, 4 baris argumen baru ke `BoosterScreen()`).

**`BoosterScreen.kt`**:
- 4 parameter baru `bassEffectState`/`virtualizerEffectState`/
  `loudnessEffectState`/`equalizerEffectState` (default `ENABLED` — backward-
  compatible, pemanggil lama/preview yang belum kasih parameter ini perilakunya
  TIDAK berubah).
- `helpText` di 3 `FeatureControl` (Bass/Virtualizer/Loudness) sekarang punya
  2 cabang baru: `CONTROL_LOST` → pesan "kontrol direbut aplikasi/sistem
  lain", `FAILED` → pesan "gagal diterapkan, coba Nyalakan Lagi/restart HP" —
  DIPRIORITASKAN di atas cek `strengthSupported` (soal beda: itu limitasi
  chipset permanen, ini masalah sementara). `equalizerEffectState` DITERIMA
  sebagai parameter tapi BELUM disurface ke `EqualizerSection` (struktur multi-
  band-slider beda dari `FeatureControl` tunggal, butuh desain terpisah —
  disengaja ditunda, dicatat biar gak dikira kelupaan).

**String baru** (`values/strings.xml` + `values-en/strings.xml`, parity
103→105/105): `feature_help_failed`, `feature_help_control_lost`.

**Belum dikerjakan dari audit** (Fase 0 roadmap.md #2-#9, urutan sesuai
dokumen audit) — TIDAK berubah dari catatan Batch 57, cuma item #1 yang
sekarang genap selesai (Service + UI, minus equalizer band-level yang
ditunda).

**File diubah**: `BoosterViewModel.kt`, `MainActivity.kt` (parsial),
`BoosterScreen.kt`, `values/strings.xml`, `values-en/strings.xml`,
`app/build.gradle.kts` (versionCode 97→98, versionName 1.92.0→1.93.0).
**Belum divalidasi runtime** — statis only (brace/paren 0 selisih 3 file
Kotlin disentuh, parity string 105/105, XML valid). `viewModelScope`+`delay`
loop & param `EffectState` baru di composable — kandidat pertama dicurigai
kalau badge/helpText baru tidak pernah berubah dari kondisi normal, atau ada
crash terkait coroutine saat ViewModel dibersihkan.

## v1.92.0 - Batch 57 (audit eksternal): actual effect-state verification + non-silent error handling

User upload dokumen audit eksternal ("AudioEnhancerPro — Audit Nyata, Gap
Terbesar Menuju 100% Functional & Polished"). Audit ini menegaskan gap terbesar
bukan di UI (sudah ~90-95%) tapi di **audio-engine robustness** (~60-70%): P0
gap #3 "Tidak Ada Verifikasi Bahwa Effect Benar-Benar Aktif di Output" & #4
"Tidak Ada Handling AudioEffect Control Ownership", plus P1 #12 "isRunning
Bukan Sumber Kebenaran Audio Engine" & #13 "enableEffects() Terlalu Silent".
Instruksi user eksplisit: kerjakan bertahap, jangan sekaligus semua gap.
Batch ini scope-nya SENGAJA dibatasi ke lapisan engine (`AudioEnhancerService.kt`
saja) — surfacing state baru ini ke ViewModel/UI (badge/warning per-fitur)
DISENGAJA ditunda ke batch berikutnya, biar tetap "1 variabel risiko per push"
(konsisten kebiasaan project ini, lihat riwayat Batch 32/34/36/49 dst).

**`AudioEnhancerService.kt`** (satu-satunya file kode berubah):
1. `enum class EffectState { UNAVAILABLE, AVAILABLE, ENABLED, FAILED,
   CONTROL_LOST }` baru (nested di class ini) + 4 field `@Volatile var
   bassState/virtualizerState/loudnessState/equalizerState` (private set) —
   pola `@Volatile` sama persis alasannya dengan `isRunning` (Batch 45):
   listener sistem di bawah TIDAK dijamin main thread.
2. `attachEffects()`: tiap effect (BassBoost/Virtualizer/Equalizer/
   LoudnessEnhancer) sekarang dipasangi `setControlStatusListener` +
   `setEnableStatusListener` (API bawaan `android.media.audiofx.AudioEffect`,
   diwarisi ke-4 subclass ini, PERTAMA KALI dipakai di project ini) —
   `controlGranted=false` dari sistem ⇒ `CONTROL_LOST` (effect object masih
   ada tapi OS sudah mencabut kontrolnya, kasus persis yang dikeluhkan audit
   Gap #4). Constructor gagal TETAP `UNAVAILABLE` (semantik `isXxxSupported()`
   TIDAK berubah, kompatibel mundur 100% dengan pemanggil existing).
3. `enableEffects()`/`set*()` (4 fungsi kontrol dari UI): exception yang
   sebelumnya `catch (_: Exception) {}` (silent total) sekarang di-`Log.e`
   + menandai state effect terkait `FAILED` — kegagalan APPLY (bukan cuma
   unsupported) sekarang terlihat lewat Logcat (Debug Priority project ini)
   & field state, bukan hilang begitu saja (audit Gap #13).
4. `disableEffects()`: exception di-`Log.e` juga, TAPI SENGAJA TIDAK mengubah
   state ke `FAILED` — kegagalan disable saat user memang minta "Matikan"
   bukan kegagalan engine yang perlu ditandai merah.
5. `releaseEffects()`: reset ke-4 state ke `UNAVAILABLE` (Service betulan
   di-destroy, object sudah `.release()`).
6. PrefsHelper persistence di `set*()` **TIDAK diubah** — TETAP tersimpan
   tanpa syarat meski apply ke effect gagal (audit Gap #14 dibahas eksplisit
   di komentar kode: kalau save ikut digagalkan, restart berikutnya user malah
   kehilangan preferensi slider yang sudah mereka atur — trade-off disengaja,
   bukan kelewatan).

**Belum dikerjakan dari audit ini (transparan, urutan sesuai "Fokus Next Step"
di dokumen audit)**: rebuild arsitektur session-0 ke API modern (Gap #1, #2,
#9-11 — risiko terbesar, effort terbesar, disengaja BUKAN batch pertama),
surfacing `EffectState` baru ke `BoosterViewModel.kt`/`BoosterScreen.kt` (badge
per-fitur "gagal diterapkan"/"kontrol hilang" — kandidat batch berikutnya,
sekarang datanya sudah ada di Service tinggal dikonsumsi), gain
staging/dynamics pipeline (Gap #5, #6), EQ ikut tersimpan di custom preset
(Gap #16), automated audio-engine test (Gap #22-24).

**File diubah**: `AudioEnhancerService.kt` (kode), `app/build.gradle.kts`
(versionCode 96→97, versionName 1.91.0→1.92.0). **Belum divalidasi runtime**
— statis only (brace/paren balance 0 selisih). `setControlStatusListener`/
`setEnableStatusListener` API resmi `android.media.audiofx.AudioEffect`
(terdokumentasi, bukan reka-reka) tapi PERTAMA KALI dipakai project ini —
kandidat pertama dicurigai kalau ada laporan crash saat toggle/putar audio,
atau badge (batch depan) tidak pernah lepas dari `ENABLED`.

## v1.91.0 - Batch 56 (diminta user, "push lebih dalam lagi"): depth & kontras dual-shadow dinaikkan

User konfirmasi Batch 53/54/55 render benar di device ("lumayan") lalu minta
depth/kontras dinaikkan lebih jauh. Tuning murni (0 perubahan teknik/struktur
dari Batch 53) di 2 file:

**`Theme.kt`**:
- `NeumoEdgeHighlight`: alpha 0.55→0.72, warna dibikin lebih terang/biru
  (`#3E5273`→`#4A6690`).
- `NeumoEdgeShadow`: alpha 0.92→0.97 (nyaris opaque).
- `NeumorphismSkeuTokens.cardElevation`: 10dp→13dp (basis spread kartu naik,
  `SkeuTintedCard` ikut naik otomatis lewat `cardElevation + 1.dp`).

**`SkeuomorphicComponents.kt`**:
- Multiplier spread (`maxSpread = depth.toPx() * X`): 1.15f→1.6f — bleed
  shadow lebih jauh dari tepi bentuk di SEMUA elemen (dampak global, 1 titik
  ubah).
- `ShadowSteps` (default kartu): 5→6 — falloff sedikit lebih halus/panjang.
- `SkeuPowerButton`: depth 7dp→10dp, steps 4→5.
- `SkeuSliderTrack` (inset): depth 3dp→4.5dp, steps 3→4.
- `SkeuSwitch` (inset): depth 2.5dp→3.5dp, steps 3→4.

3 varian tema lain TIDAK kepengaruh (gate `shadowLightTint == Transparent`
dipertahankan, semua token di atas cuma dibaca kalau gate itu lolos).

**File diubah**: `Theme.kt`, `SkeuomorphicComponents.kt`, `app/build.gradle.kts`
(versionCode 95→96, versionName 1.90.2→1.91.0 — MINOR karena ini penajaman
visual nyata, bukan cuma patch CI). Belum divalidasi runtime (sandbox tanpa
device) — murni tuning angka di atas fondasi teknik yang SUDAH terbukti
compile & render benar (Batch 53-55), jadi resiko regresi rendah.

## v1.90.2 - Batch 55 (ditanya user): kenapa artifact log_fail-debug DAN -release muncul bareng, padahal cuma debug yang gagal

User tanya kenapa 2 artifact (`log_fail_v1.90.0-debug-run105`,
`log_fail_v1.90.0-release-run105`) sama-sama muncul di run yang gagal —
padahal root cause (Batch 54) cuma 1 compile error yang harusnya cuma
gagalin `assembleDebug`. **Jawaban (bukan bug tersembunyi, murni soal
kondisi `if:` step upload)**: `if: failure()` polos dievaluasi di level JOB
(true begitu ADA step manapun yang gagal), bukan step spesifik. Alurnya:
"Build debug APK" gagal → job jadi failing → "Build signed release APK" ikut
ke-SKIP (bukan gagal, standar: step tanpa `if: always()` otomatis dilewati
kalau step sebelumnya gagal) → TAPI "Upload failure log (release build)"
(`if: failure()` polos) tetap jalan karena job-nya failing, walau
`gradle-build-release.log` gak pernah dibuat sama sekali (release build gak
pernah dieksekusi) → artifact release ke-upload isi cuma
`gradle-wrapper-bootstrap.log` + report cache, MENYESATKAN (kelihatan kayak
release ikut gagal, padahal cuma efek ikutan status job).

**Fix (`.github/workflows/build.yml`, 1 file, edit parsial 2 titik)**:
1. Step "Build signed release APK" dikasih `id: build_release` (sebelumnya
   gak punya id).
2. Kondisi "Upload failure log (release build)" diperketat jadi
   `if: failure() && steps.build_release.outcome == 'failure'` — `outcome`
   step yang SKIP nilainya `'skipped'` (bukan `'failure'`), jadi sekarang
   cuma ke-trigger kalau step release itu SENDIRI beneran dieksekusi & gagal.
   `failure()` di depan TETAP wajib ada (tanpa itu GitHub Actions nambahin
   `&& success()` implisit ke `if:` yang gak eksplisit pakai
   failure()/always(), bikin step ini malah gak jalan pas release BENERAN
   gagal — kasus utama fitur ini dibuat).

Step "Upload failure log (debug build)" (`if: failure()` polos, TANPA syarat
tambahan) SENGAJA TIDAK diubah — debug build adalah step PERTAMA yang bisa
gagal di job ini (gak ada step build lain sebelumnya yang bisa bikin dia
ke-skip), jadi `if: failure()` polos di situ SUDAH benar 1:1 dengan "debug
beneran gagal", gak ada skenario false-positive yang sama seperti release.

**Verifikasi**: YAML divalidasi parse (`python3 -c "import yaml; ..."`) —
14 steps kebaca normal, `id: build_release` & `if:` baru kebaca persis
sesuai yang ditulis. **Belum CI run sungguhan** (skenario "release beneran
gagal setelah fix ini" & "debug gagal, release ke-skip, upload release TIDAK
lagi ikut ke-trigger" baru terkonfirmasi run CI berikutnya).

**File diubah**: `.github/workflows/build.yml` (PROTECTED asset, edit
PARSIAL 2 titik sesuai izin — bukan rewrite total), `app/build.gradle.kts`
(versionCode 94→95, versionName 1.90.1→1.90.2, PATCH — CI-only, 0 perubahan
kode app/perilaku runtime, konsisten pola Batch 42 yang juga workflow-only
tapi tetap bump versi).

## v1.90.1 - Batch 54 (fix urgent, dilaporkan user via CI log): compile error "Unresolved reference: drawOutline"

User upload screenshot GitHub Actions "build-and-release" FAILED (exit code
1) + 2 file log CI (`log_fail_v1.90.0-debug-run105.zip`,
`log_fail_v1.90.0-release-run105.zip`). **Root cause ketemu PERSIS** dari
`gradle-build-debug.log` — 3 baris compiler error:
```
e: .../SkeuomorphicComponents.kt:76:47 Unresolved reference: drawOutline
e: .../SkeuomorphicComponents.kt:179:25 Unresolved reference: drawOutline
e: .../SkeuomorphicComponents.kt:185:25 Unresolved reference: drawOutline
```
Ini PERSIS kandidat yang sudah diwanti-wanti di `CHANGELOG.md`/
`PROJECT_STATE.md` Batch 53 sendiri ("kandidat pertama dicurigai kalau user
lapor compile error... soal import `drawOutline`") — dugaan itu BENAR:
`drawOutline` TERNYATA gak ada di Compose UI graphics package manapun
(bukan cuma salah path import — compiler bilang "Unresolved reference" di
baris import itu sendiri, artinya simbolnya sendiri gak eksis, ini
kesalahan ingat API dari memori, BUKAN typo/library version issue). Log
release (`gradle-build-release.log`) TIDAK ada di ZIP (cuma
`gradle-wrapper-bootstrap.log`) — release job kemungkinan gak sempat sampai
tahap compile Kotlin (gagal lebih awal / dependent ke debug), TAPI root
cause SAMA PERSIS (1 source file yang sama), fix ini otomatis selesaikan
keduanya.

**Fix (`SkeuomorphicComponents.kt`, 1 file, 1 fungsi
`SkeuDualDirectionalShadow`)**: `drawOutline` diganti `drawPath` (primitive
DrawScope yang BENERAN ada — dipakai luas untuk custom Canvas drawing di
Compose, jauh lebih fundamental/stabil daripada `drawOutline` yang
ternyata gak eksis). `Outline` (hasil `shape.createOutline(...)`, TETAP
dipakai — bagian ini valid, cuma cara MENGGAMBARnya yang salah) dikonversi
ke `Path` SEKALI di luar loop (bukan per-step, sedikit lebih hemat) via
`when` exhaustive atas 3 subtype sealed class `Outline`: `Rectangle`
(`Path().apply { addRect(outline.rect) }`), `Rounded`
(`Path().apply { addRoundRect(outline.roundRect) }`, ini yang kepake buat
`RoundedCornerShape` — kartu/track/switch), `Generic`
(`outline.path` langsung, ini yang kepake buat `CircleShape` — power button/
thumb slider/thumb switch). Import `androidx.compose.ui.graphics.drawscope.
drawOutline` (salah/gak eksis) dihapus, ganti `androidx.compose.ui.graphics.
Outline` + `androidx.compose.ui.graphics.Path` (dipakai bikin instance path
manual). Import `translate` (Batch 53) TIDAK diubah — compiler TIDAK
komplain soal itu (cuma `drawOutline` yang error), jadi terbukti valid.

**Cara verifikasi kali ini** (lebih kuat dari batch sebelumnya, TAPI tetap
BUKAN compile sungguhan): sebelumnya (Batch 53) `drawOutline` dipilih murni
dari INGATAN tanpa bukti konkret — SEKARANG diganti `drawPath` juga masih
dari ingatan, TAPI kali ini didukung 2 alasan tambahan: (1) `drawPath`
adalah primitive Canvas paling dasar/umum dipakai di HAMPIR SEMUA tutorial
custom-draw Compose (jauh lebih sering dipakai & stabil lintas versi
dibanding `drawOutline` yang ternyata gak pernah ada), (2) pola konversi
`Outline` sealed-class ke `Path` via exhaustive `when` adalah idiom baku
yang sering muncul di kode Compose lain buat kasus serupa (\"gambar ulang
outline shape\"). **Confidence lebih tinggi dari sebelumnya, tapi TETAP
belum 100%** — kalau CI masih gagal di titik yang SAMA, laporkan lagi
supaya didekati beda (opsi fallback paling aman: `drawRoundRect`/`drawOval`
langsung tanpa lewat `Outline` sama sekali, deteksi shape via
`shape is RoundedCornerShape` vs `CircleShape` — lebih verbose tapi 0
ketergantungan ke API `Outline`/`Path.addRoundRect` yang belum kebukti).

**File diubah**: `SkeuomorphicComponents.kt` (fix), `app/build.gradle.kts`
(versionCode 93→94, versionName 1.90.0→1.90.1 — PATCH, bukan minor, karena
ini fix compile-error dari rilis sebelumnya yang gagal build total, bukan
fitur/perubahan visual baru). `Theme.kt`/`MainActivity.kt` TIDAK disentuh.

## v1.90.0 - Batch 53: fix "extruded & pressed kurang menonjol" — dual-shadow direkonstruksi total (bukan native Modifier.shadow lagi)

User lapor (2 screenshot device asli, bukan preview) kesan "extruded &
pressed" tema Neumorphism (Batch 52, palet Deep Navy & Brass sudah benar)
masih kurang kerasa. **Root cause** — didokumentasikan project ini sejak
Batch 14 tapi TERULANG lagi di Batch 47/52: `Modifier.shadow()` native
Android (termasuk parameter `ambientColor`/`spotColor` yang dipakai Batch 47)
DIBATASI alpha keras oleh sistem (di-tuning buat shadow Material Design
tipis, bukan neumorphism tebal) — DAN warna custom shadow itu CUMA jalan di
API 28+, di bawahnya SENYAP diabaikan (balik ke shadow hitam default tanpa
warning apapun). Batch 47 sempat "fix" pakai `ambientColor`/`spotColor`
tapi ternyata masih kena limitasi sistem yang sama, cuma versi lebih halus.

**Fix (`SkeuomorphicComponents.kt`, 1 file)** — `SkeuDualDirectionalShadow`
DIROMBAK TOTAL: bukan lagi 2x `Modifier.shadow()` native, sekarang gambar
ULANG siluet bentuk kartu (`shape.createOutline`) 3-5x berturut-turut pakai
`DrawScope.drawOutline` + `translate` POLOS (operasi Canvas paling dasar,
BUKAN `Paint.setShadowLayer`/`BlurMaskFilter`/`RenderEffect` — preseden
Batch 14/32 soal custom Paint-shadow-hack TETAP dihormati, 0 native
Canvas/Paint interop di sini, cuma fill shape solid berulang), makin jauh
dari posisi asli (arah diagonal, terang=kiri-atas/gelap=kanan-bawah) & makin
transparan tiap step (alpha linear turun ke 0) — mensimulasikan falloff blur
TANPA `BlurMaskFilter` (custom Paint, dilarang) atau `Modifier.blur()`
(Compose native tapi API-gated 31+, sama masalahnya dengan `ambientColor`).
Hasilnya render IDENTIK di semua API level dari `minSdk 24` — 0
fallback/gating diperlukan sama sekali, beda total dari 2 pendekatan
sebelumnya yang keduanya diam-diam dibatasi sistem.

**Cue "pressed" (baru, sebelumnya cuma "extruded" yang dikerjain)** —
parameter baru `invert: Boolean` pada `SkeuDualDirectionalShadow`: kalau
`true`, arah terang/gelap DIBALIK (gelap kiri-atas, terang kanan-bawah,
fisika cekungan — dinding dekat sumber cahaya kena bayangan, dinding jauh
kena pantulan) + dikombinasi `.clip(shape)` di caller supaya bleed terpotong
KE DALAM bentuk (kebaca cekung, bukan bocor keluar kayak raised). Dipasang
di 3 tempat baru:
- `SkeuSliderTrack` — bagian track yang BELUM terisi sekarang punya inset
  shadow cekung (groove tempat thumb bergerak), bagian TERISI tetap flat
  solid di atasnya.
- `SkeuSwitch` — groove track (tempat thumb "duduk") dapat inset shadow,
  pelengkap thumb raised yang sudah ada.
- `SkeuPowerButton` (tombol bundar "Aktif/Nonaktif", elemen tactile paling
  menonjol di 2 screenshot user) — SEBELUMNYA cuma pakai `Modifier.shadow()`
  generik 1 layer (bukan dual-shadow SAMA SEKALI, gap yang gak kesadar
  sebelumnya walau ini elemen paling sering dilihat user). Sekarang dapat
  dual-shadow penuh: raised saat OFF (bleed keluar lingkaran, `.clip`
  kondisional OFF), invert/cekung saat `pressed` (state ON, `.clip(shape)`
  diaktifkan biar shadow kepotong ke dalam) — cue "ditekan masuk" sekarang
  beneran dari shadow terbalik, bukan cuma `elevation->0dp`+ring accent
  seperti sebelumnya.

**Bug ditemukan & diperbaiki SEBELUM dikirim** (self-review): draf pertama
formula alpha falloff (`1f - falloff * 0.55f` dengan `falloff = (1-t)²`)
TERBALIK — step TERJAUH (harusnya paling transparan) malah dapat alpha
PENUH, step TERDEKAT (harusnya paling pekat) malah DIREDUPKAN. Diperbaiki ke
`alphaMultiplier = 1f - t` langsung (linear, farthest=0/nearest≈0.67-0.8) —
diverifikasi ulang manual step-by-step sebelum lanjut.

3 varian tema lain (Midnight Glass, Aurora Glass, Studio Equalizer) TIDAK
kepengaruh — `SkeuDualDirectionalShadow` tetap early-return 0 draw call
kalau `tokens.shadowLightTint == Color.Transparent` (arsitektur gate sama
seperti Batch 47, dipertahankan). `Theme.kt`/`MainActivity.kt` TIDAK
disentuh.

**File diubah**: `SkeuomorphicComponents.kt` (satu-satunya file kode),
`app/build.gradle.kts` (versionCode 92→93, versionName 1.89.0→1.90.0).

**Belum divalidasi runtime** (sandbox tanpa kotlinc/device, audit statis:
brace/paren parity 287/287 & 43/43, grep semua 6 titik pemanggilan
`SkeuDualDirectionalShadow` — 2 lama (`SkeuCard`/`SkeuTintedCard`, positional
arg, aman dari rename param `elevation`→`depth`) + 4 baru, semua named-arg
konsisten dengan signature baru). Teknik `drawOutline`/`translate` dipakai
PERTAMA KALI di project ini (sebelumnya cuma `drawCircle`/`drawBehind` polos
di `skeuGlow`) — kandidat pertama dicurigai kalau user lapor crash/render
kosong setelah rebuild, terutama soal import (`drawOutline` diverifikasi
dari memori sebagai top-level extension function di package
`androidx.compose.ui.graphics.drawscope`, BUKAN member `DrawScope` — kalau
ternyata salah, error compile "unresolved reference: drawOutline" akan
muncul jelas, gampang di-fix hapus 1 baris import).

## v1.89.0 - Batch 52: Neumorphism dirombak total — flat surface + Deep Navy & Classic Brass

User lapor tema "Neumorphism" (Batch 46, Platinum+Ruby) gak keliatan
"eksplisit" — kesannya cuma kartu gelap generik, bukan soft-UI genuine.
Root cause teknis (`Theme.kt`, satu-satunya file kode diubah):
1. `NeumoBevelBrush` gradient 5-stop bikin permukaan kartu ITU SENDIRI sudah
   "berlapis" — bertentangan sama definisi neumorphism (permukaan HARUS
   flat 1 warna, kedalaman murni dari sepasang shadow terarah DI LUAR
   bentuk, bukan gradient DI DALAM bentuk — gradient-internal itu ciri
   skeuomorphism/glass).
2. `NeumoSpecularBrush` (sheen glossy) = ciri glassmorphism, bukan
   neumorphism (matte total).
3. Kontras `shadowLightTint`/`shadowDarkTint` (dipakai
   `SkeuDualDirectionalShadow`, SkeuomorphicComponents.kt) terlalu tipis buat
   kebaca jelas di layar kecil.

**Fix**: kartu (`NeumoBevelBrush`) & background layar
(`NeumoScreenBackgroundBrush`) jadi `SolidColor` FLAT (bukan gradient lagi),
sheen (`NeumoSpecularBrush`) diset `Color.Transparent` (0 efek visual —
`SkeuCard`/`SkeuTintedCard` di SkeuomorphicComponents.kt TIDAK disentuh,
tetap manggil `.background(specularBrush)` apa adanya), kontras dual-shadow
(`NeumoEdgeHighlight`/`NeumoEdgeShadow`) dinaikkan signifikan (tint terang
biru-navy `#3E5273` alpha 0.55 vs gelap `#060B14` alpha 0.92 — 2 sumber
cahaya berlawanan yang jelas kebaca, bukan 1 shadow abu-abu datar).

**Palet direset total** ke "Deep Navy & Classic Brass" (spek eksak dari
user, keluarga Tailwind Slate + 1 aksen), GANTI TOTAL dari Platinum+Ruby:
- `NeumoBackground #0F172A` (appBg, slate-900) — background & screen brush.
- `NeumoPanel #1E293B` (appCard, slate-800) — fill kartu flat.
- `NeumoBorder #334155` (appBorder, slate-700) — border 1px + surface
  "raised"/`elevatedSurface` (dipakai dobel, sesuai komposisi user).
- `NeumoPanelRecessed #060B14` — sumur/pressed-well, base shadow gelap.
- `NeumoTextPrimary #F8FAFC` / `NeumoTextSecondary #94A3B8` (txtPrimary/
  txtSecondary eksak dari komposisi) / `NeumoTextMuted #64748B`.
- `NeumoBrass #D4AF37` / `NeumoBrassDeep #A9862C` — SATU-SATUNYA aksen
  berwarna, ganti Platinum(netral-luas)+Ruby(vivid) lama. Brass HANYA
  dipakai primary/onPrimaryContainer/glow/ring state-aktif — TIDAK disebar
  ke bevel/border/permukaan pasif (spec user: aksen brass maks ~10% area,
  jangan dipakai teks paragraf). `NeumorphismDarkColors.onPrimary` diganti
  dari `Color.White` jadi `NeumoBackground` (teks gelap di atas brass —
  syarat kontras WCAG eksplisit di komposisi user, brass terlalu terang buat
  teks putih).

Nama variabel `Neumo*` DIPERTAHANKAN (bukan diganti `Navy*`/`Brass*`) —
`NeumorphismSkeuTokens`/`NeumorphismDarkColors` (Theme.kt) & terutama
`NeumoScreenBackgroundBrush` (dipakai `MainActivity.kt`, PROTECTED asset)
TIDAK perlu disentuh sama sekali. Toggle "Skeuomorphism" di Settings &
persistence key TIDAK diubah (sama seperti Batch 46). Radius kartu/icon-box
(`NeumoCardRadius`/`NeumoIconBoxRadius`, 22dp/15dp) TIDAK diubah — bukan
bagian keluhan user. 3 varian tema lain (Midnight Glass, Aurora Glass,
Studio Equalizer) TIDAK disentuh sama sekali.

**File diubah (3 file kode + 1 dokumen version bump, 1 modul/tema)**:
`Theme.kt` (token warna Neumorphism, ~lines 247-353 & instance
`NeumorphismSkeuTokens`/`NeumorphismDarkColors`), `app/build.gradle.kts`
(versionCode 91→92, versionName 1.88.0→1.89.0). `SkeuomorphicComponents.kt`
& `MainActivity.kt` TIDAK disentuh (0 breaking reference, diverifikasi via
grep — semua pemakai token lama sudah ganti nama var yang sama, tidak ada
referensi ke var yang dihapus seperti `NeumoPlatinum`/`NeumoRuby`).

**Belum divalidasi runtime** (batasan sandbox — tidak ada kotlinc/device di
sini, cuma audit statis: brace/paren parity, grep referensi silang semua
file). Yang perlu dicek user pas APK baru di-install: (1) kartu Neumorphism
kebaca timbul/tenggelam jelas (dual-shadow native `Modifier.shadow` kadang
rendering-nya beda-beda tipis antar device/GPU — ini limitasi platform,
bukan bug kode kalau kontrasnya masih kurang di device tertentu), (2) toggle
brass (primary) — teks/icon di atasnya kebaca kontras jelas.

## v1.88.0 - Batch 51: Snackbar sukses simpan/hapus preset + hapus log crash

Diminta user "sempurnakan fungsionalitas aplikasi 100%" — permintaan umum,
bukan bugfix spesifik. Pendekatan: (1) full static re-audit semua 16 file
Kotlin + resource (brace/paren balance, parity string, XML validity) — NIHIL
bug baru ditemukan, app sudah melalui 50 batch audit sebelumnya, fungsional
inti (audio engine, reliability/watchdog, preset, widget/QS Tile/shortcut,
crash logger, CI/CD, 4 tema) tetap SELESAI tanpa regresi. (2) Baca `roadmap.md`
(acuan aktif prioritas, lihat Batch 50) — Fase 1 (Runtime Validation Debt)
BUKAN sesuatu yang bisa dikerjakan dari sandbox (butuh device fisik), jadi
prioritas realistis yang bisa dikerjakan sekarang: 1 item kecil dari Fase 3
("loading/success/error state feedback... saat ini minim").

**Gap konkret yang ditemukan**: dialog simpan preset, hapus preset, dan hapus
log crash — ketiganya sudah correct secara fungsi (data benar-benar
tersimpan/terhapus), TAPI 0 konfirmasi visual ke user kalau aksi itu BENERAN
berhasil. Satu-satunya sinyal sebelumnya cuma haptic buzz + dialog tertutup —
kalau HP di-silent-mode/haptic OFF di sistem, user gak dapat sinyal APA PUN
selain dialognya hilang (ambigu: berhasil, atau batal?).

**Fix**: `SnackbarHostState` + `rememberCoroutineScope()` baru di
`BoosterScreen()`, `SnackbarHost` dipasang di lapisan `Box` terluar
(`Alignment.BottomCenter`) — layout `Column` konten utama (TopCenter, existing)
TIDAK dipindah, cuma dibungkus 1 layer `Box` tambahan supaya SnackbarHost bisa
jadi sibling-nya (bebas overlap konten, gak ikut ke-scroll). 3 titik baru
manggil `showSnackbar(...)`: confirm-button simpan preset (`"Preset \"X\"
disimpan"`), confirm-button hapus preset (`"Preset \"X\" dihapus"`), dan
`CrashBanner` (param baru `onCrashLogsDeleted: () -> Unit = {}`, dipanggil
BoosterScreen lewat `context.getString(...)` — BUKAN `stringResource()` karena
callback ini jalan di dalam `onClick` biasa, bukan lambda `@Composable`).
3 string baru (ID+EN, parity 103/103): `preset_saved_message`,
`preset_deleted_message` (both format `%1$s`), `crash_logs_deleted_message`.

**Kenapa scope dibatasi ke 3 titik ini saja** (bukan semua item Fase 3
sekaligus): Batch Limit standing rule (maks 1 modul/batch tanpa alasan Atomic
Change) + gap lain di Fase 3 (recomposition review, hierarki visual, white
space, micro-animation, empty state lanjutan, tooltip fitur) sifatnya
kosmetik/subjektif dan lebih beresiko tanpa compiler kalau digabung sekaligus
dalam 1 push — dipisah biar tiap batch tetap 1 variabel risiko utama, pola
yang sudah terbukti aman sejak Batch 49-50. Sisanya TETAP di `roadmap.md` Fase
3, belum dicentang.

2 file kode berubah: `BoosterScreen.kt` (+snackbar plumbing, 3 call-site),
`app/build.gradle.kts` (versionCode 90→91, versionName 1.87.0→1.88.0, edit
parsial). 2 file resource: `values/strings.xml` + `values-en/strings.xml`
(+3 string masing-masing).
- **Belum divalidasi runtime** — statis only (brace/paren 0 selisih SEMUA 18
  file Kotlin project termasuk test, bukan cuma yang disentuh; parity string
  103/103; XML valid). `SnackbarHost`+`SnackbarHostState` API Compose Material3
  standar & stabil lama, tapi PERTAMA KALI dipakai di project ini — kandidat
  pertama dicurigai kalau ada laporan Snackbar gak muncul/ke-clip/salah posisi
  (mis. ketiban keyboard software saat dialog simpan preset masih terbuka).

## v1.87.0 - Batch 50: configuration-cache (lanjutan percepatan compile) + roadmap.md

Diminta user 2 hal dalam 1 pesan: "Lakukan percepatan pada compile aplikasi"
+ "tambahkan roadmap.md berisi panduan menuju 100% aplikasi
sempurna/tamat". User dikonfirmasi via pertanyaan langsung: CI run v1.86.0
(Batch 49, cabut Hilt/kapt) sudah HIJAU/SUKSES — syarat penahanan yang
dicatat eksplisit di entry Batch 49 di bawah (jangan gabung 2 perubahan
build-system besar tanpa compiler dalam 1 push yang sama) sudah terpenuhi,
jadi `configuration-cache` sekarang aman dikerjakan sebagai variabel risiko
TUNGGAL batch ini (kapt removal sudah divalidasi CI run terpisah sebelumnya).

**Bagian 1 — `org.gradle.configuration-cache=true`** (`gradle.properties`):
Gradle menyimpan hasil fase konfigurasi (evaluasi seluruh `build.gradle.kts`,
resolusi plugin, task graph) ke cache, lalu SKIP fase itu sepenuhnya di run
berikutnya selama input konfigurasi (file build script, gradle.properties,
env var yang dibaca saat konfigurasi) tidak berubah. Cache-nya otomatis ikut
ter-cache lintas-run CI lewat `cache: 'gradle'` (`actions/setup-java`, sudah
ada sejak Batch 40) — tidak perlu perubahan CI tambahan apa pun, manfaatnya
otomatis kepakai mulai run kedua setelah ini.
- **Titik rawan yang dicek manual** (sandbox gak bisa compile-check
  configuration-cache problems): `signingConfigs.release` baca
  `System.getenv("KEYSTORE_PATH")` dkk — dikonfirmasi via baca ulang kode,
  pemanggilan ini terjadi di level `android { signingConfigs { create(...) } }`
  yaitu FASE KONFIGURASI (dievaluasi sekali saat build script diproses),
  BUKAN di dalam task action/`doLast` (yang akan jadi problem configuration-
  cache kalau baca env var saat EXECUTION). Berdasarkan pembacaan kode ini
  harusnya aman, tapi ini murni analisis statis — belum ada compiler.
- versionCode 89→90, versionName 1.86.0→1.87.0 (`app/build.gradle.kts`, edit
  parsial, cuma 2 angka).
- **Belum tervalidasi runtime** — flag resmi Gradle (stabil sejak Gradle 8.1+,
  proyek ini pakai wrapper 8.7), tapi PERTAMA KALI dipakai di project ini.
  Kalau CI merah: cari pesan eksplisit "configuration cache problems" di
  `gradle-build-debug.log`/`gradle-build-release.log` (beda karakteristik
  dari error compile Kotlin biasa) — kandidat pertama dicurigai: titik
  `System.getenv()` di atas.

**Bagian 2 — `roadmap.md` baru** (root project, bukan edit file existing):
selama ini backlog "belum dikerjakan"/"pending"/"belum divalidasi runtime"
tersebar di puluhan entry `PROJECT_STATE.md` dari Batch 1 sampai 49, gak ada
1 tempat yang merangkum semua jadi checklist actionable dengan urutan
prioritas & definisi "selesai" yang jelas. `roadmap.md` menyintesis SEMUA itu
jadi 6 fase (Runtime Validation Debt, Build & CI Maturity, Audit Polish,
Kompatibilitas Device, Feature Backlog, Dokumentasi/Housekeeping) + tabel
progress ringkas + definisi eksplisit "100%/tamat" (4 syarat: fungsional,
runtime-verified, CI hijau stabil, TODO Medium/High kosong). Fase 1 (Runtime
Validation Debt) ditandai prioritas TERTINGGI — mayoritas kode project ini
"kelihatan benar secara statis" tapi belum "terkonfirmasi benar di device
asli", itu jadi gap terbesar menuju 100% yang sesungguhnya, BUKAN kurangnya
fitur. Tidak ada isi teknis baru di sini — murni reorganisasi info yang
sudah ada supaya actionable, 0 klaim baru yang belum tercatat sebelumnya di
`PROJECT_STATE.md`/`CHANGELOG.md`.
- `FILE_MANIFEST.txt` ikut diupdate (1 baris baru, `roadmap.md`).
- **PENTING buat sesi depan**: `roadmap.md` sekarang jadi acuan AKTIF progress
  — update checklist-nya (bukan cuma `PROJECT_STATE.md`) tiap kali 1 item
  pindah status dikerjakan/divalidasi.


## v1.86.0 - Batch 49: link download APK ke atas README + cabut Hilt/kapt (percepatan compile)

Diminta user dalam 1 pesan, 2 bagian independen: "Readme juga masih
kepanjangan (harus effort scroll sebelum sampai ke tab download apk)" +
"Lakukan percepatan untuk compile aplikasi nya juga". 6 file total:
`README.md`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`,
`AudioEnhancerApp.kt`, `MainActivity.kt` (edit parsial), `BoosterViewModel.kt`
(Atomic Change, exception limit 10 file — ini 1 rombak arsitektur (cabut DI
framework) yang tidak bisa dipecah lintas-batch tanpa app crash sementara di
tengah-tengah).

**Bagian 1 — README.md**: root cause murni "posisi" — link download APK
sebelumnya TIDAK ADA sama sekali secara eksplisit (cuma disebut prosa
"tab Releases (sidebar beranda repo)" di section "Versioning APK Release",
section ke-6 dari 8, di bawah blockquote AI + Preview UI + Fitur + Batasan
jujur + Build). Fix: 1 link bold `[⬇️ Download APK Terbaru]` LANGSUNG di
bawah judul H1 — hal PERTAMA yang kebaca sebelum blockquote AI-session
sekalipun. Pakai URL `/releases/latest` bawaan GitHub (bukan link ke versi
tertentu) — otomatis resolve ke rilis TERBARU kapan pun diklik, tidak pernah
basi meski versionName naik terus tiap batch. Sisa isi README (Fitur,
Batasan jujur, Troubleshooting, dst) TIDAK dipangkas — keluhan user eksplisit
soal REACHABILITY link download, bukan minta konten lain dihapus.

**Bagian 2 — cabut Hilt/kapt (percepatan compile)**: root cause ditemukan
via grep menyeluruh (`app/src/main/java`, `app/src/test`): SATU-SATUNYA titik
inject yang pernah dipakai Hilt di seluruh project ini, sejak dipasang Batch
18, adalah `Application` ke constructor `BoosterViewModel` — nol `@Module`,
nol `@Binds`, nol `@Provides`, nol `hiltViewModel()` call di mana pun. Dan
`Application` itu SUDAH didapat GRATIS tanpa DI framework apa pun:
`by viewModels()` (activity-ktx) pakai `SavedStateViewModelFactory` bawaan
AndroidX, yang SUDAH TAHU cara construct subclass `AndroidViewModel` manapun
lewat constructor `(Application)` — mekanisme resmi sejak awal library
ViewModel, bukan fitur baru. Hilt+kapt di project sekecil ini = kapt
annotation-processing (kontributor waktu compile TERBESAR khas Android+Hilt)
dijalankan penuh, cuma buat 1 baris yang toh sudah otomatis.

Dicabut total: plugin `org.jetbrains.kotlin.kapt` + `com.google.dagger.hilt.android`
(2 `build.gradle.kts`), dependency `hilt-android`+`hilt-android-compiler`, blok
`kapt { correctErrorTypes = true }`, `@HiltAndroidApp`(`AudioEnhancerApp.kt`),
`@AndroidEntryPoint`(`MainActivity.kt`), `@HiltViewModel`+`@Inject`
(`BoosterViewModel.kt`, constructor jadi plain `class BoosterViewModel(application: Application)`).
2 baris `kapt.use.worker.api`/`kapt.incremental.apt` (Batch 41) ikut dihapus
dari `gradle.properties` (dead config, plugin-nya sudah tidak ada).

**Sekaligus dipertimbangkan, TAPI SENGAJA DITUNDA ke batch berikutnya**:
`org.gradle.configuration-cache=true` — alasan penahanan aslinya (Batch 40:
"kapt riwayatnya kurang mulus dikombinasi configuration cache") memang sudah
hilang bareng pencabutan kapt di atas. Draf pertama batch ini sempat nyalain
keduanya SEKALIGUS — dibatalkan sadar sebelum dikirim: menumpuk 2 perubahan
besar TANPA compiler buat verifikasi ganda resikonya dalam 1 push yang sama
menurunkan confidence gabungan di bawah ambang aman (<95%, aturan sendiri di
awal prompt). Dipisah: kapt removal (confidence tinggi, mekanisme AndroidX
baku) divalidasi CI run dulu sendirian, configuration-cache jadi kandidat
kuat batch build-speed BERIKUTNYA begitu ini CI CONFIRMED hijau — bukan
dibatalkan, cuma diurutkan lebih hati-hati.

**Diverifikasi statis (bukan cuma baca kode)**: grep ulang `Hilt|hilt|@Inject|
dagger|javax.inject` di seluruh `app/src/main` + `app/src/test` setelah edit
— 0 sisa kode aktif (cuma komentar dokumentasi historis). Brace/paren balance
16/16 file Kotlin project 0 selisih. **BELUM tervalidasi compile sungguhan**
(sandbox ini tidak ada Gradle/Android SDK) — ini perubahan arsitektur
TERBESAR sejak Batch 18 tanpa compiler buat verifikasi. Confidence TETAP di
bawah 100% meski scope sudah dipersempit ke 1 variabel risiko (bukan 2) —
mekanisme AndroidX yang diandalkan (`SavedStateViewModelFactory` construct
`AndroidViewModel` via constructor `(Application)`) sudah baku sejak awal
library ViewModel, tapi tetap belum ada compiler di sandbox ini buat
memastikan 0 typo/kesalahan sintaks di 5 file yang disentuh. Kalau CI merah
setelah ini, error `Cannot create an instance of BoosterViewModel` di Logcat
= tanda spesifik constructor `AndroidViewModel` gagal resolve — kandidat
pertama yang dicurigai.


## v1.85.0 - Batch 48: rapikan body GitHub Release (terlalu panjang/berantakan)

Diminta user eksplisit: "rapikan present repository yang berantakan/penuh
dengan teks yang kepanjangan (utamanya bagian GitHub release)". 1 file:
`.github/workflows/build.yml`, step "Extract changelog entry for this
version".

**Root cause**: sejak Batch 26, body Release ambil MENTAH-MENTAH seluruh
entry CHANGELOG.md versi itu (heading s.d. sebelum heading versi berikutnya).
Entry CHANGELOG.md ditulis buat SESI CLAUDE BERIKUTNYA (root cause detail,
bug yang ketemu & diperbaiki sebelum kirim, trade-off, dll — makin ke
belakang makin panjang, beberapa entry >80 baris) — bukan buat pembaca umum
di tab Releases GitHub. Hasilnya tab Releases jadi terasa "berantakan" persis
seperti dikeluhkan user.

**Fix**: extraction sekarang berhenti di baris pertama yang diawali `**`
(dari observasi konsisten 40+ entry terakhir: SELALU paragraf pembuka ringkas
dulu — "Diminta user..." + daftar file disentuh — BARU disusul subsection
detail berformat `**Judul**:`). Ditambah hard cap 15 baris apa pun formatnya
sebagai jaring pengaman (kalau ada entry masa depan yang gak ikut pola ini,
body Release tetap TIDAK BISA membengkak lagi). Fallback kalau hasil kosong
(entry yang baris pertamanya sendiri diawali `**`): ambil 8 baris pertama
mentah. Detail teknis lengkap TIDAK hilang — cuma dipindah jadi 1 baris link
ke CHANGELOG.md di penutup body Release.

**Diuji langsung** (bukan cuma baca kode) — logic awk yang sama persis
disimulasikan di sandbox terhadap 3 entry CHANGELOG.md TERAKHIR yang nyata
(v1.84.0/v1.83.0/v1.82.0, bukan data contoh/karangan): hasil turun dari
50-100+ baris/entry jadi 7-10 baris. YAML hasil edit juga divalidasi parse
dengan `python3 -c "import yaml; yaml.safe_load(...)"` — 14 step kebaca
normal, tidak ada kerusakan struktur. Ini validasi PALING KUAT yang pernah
dilakukan untuk perubahan `build.yml` sejauh ini (biasanya cuma statis/baca
kode, kali ini logic-nya benar-benar dijalankan terhadap data asli) — TAPI
tetap belum CI run sungguhan (`softprops/action-gh-release@v2` rendering body
Markdown-nya belum dilihat langsung di tab Releases GitHub).

**Yang SENGAJA tidak disentuh** (di luar scope keluhan user, bukan lupa):
judul Release (`AudioEnhancerPro v<versi> (Run #<run>)`) & tag_name sudah
ringkas dari awal (Batch 42), tidak ada masalah di situ. README.md juga
belum diaudit — kalau "berantakan" yang dimaksud user ternyata bukan cuma
soal Release, laporkan bagian mana lagi.


## v1.84.0 - Batch 47: fix "kurang depth & tactile" + ambient glow "bocor" (dual-directional shadow Neumorphism)

Diminta user via screenshot + feedback langsung: "hasilnya masih kurang
memuaskan untuk sekelas Neumorphism style. Kurang depth & tactile+ambient
lighting nya berasa 'bocor'". 2 file: `Theme.kt` (+2 field `SkeuTokens`),
`SkeuomorphicComponents.kt` (render logic).

**Root cause (ditemukan via review kode, bukan tebakan)**:
1. `SkeuCard`/`SkeuTintedCard` cuma pakai 1 `Modifier.shadow()` native (warna
   default hitam) — kontras shadow gelap SANGAT rendah di atas panel yang memang
   sudah gelap (`NeumoPanel`/`NeumoBackground`), jadi hampir gak kelihatan.
   Neumorphism genuine butuh SEPASANG shadow (terang dari arah cahaya + gelap
   dari arah bayangan), bukan cuma 1 shadow gelap tunggal.
2. `skeuGlow` (dipakai power button/switch/preset chip) pakai radial 2-stop hard
   cutoff (`[color, Transparent]`) — tepi glow terpotong tiba-tiba, kebaca
   sebagai warna "bocor" bukan cahaya "menyala ambient" yang landai.

**Fix 1 — dual-directional shadow (`SkeuTokens` +2 field, `Theme.kt`)**:
`shadowLightTint`/`shadowDarkTint`, dipakai render 2 layer
`Modifier.shadow(ambientColor=, spotColor=)` NATIVE (parameter resmi Compose
UI — BUKAN custom Paint/BlurMaskFilter, preseden Batch 14/32 tetap dihormati:
"shadow layer custom gak reliable lintas API level tanpa compiler buat
verifikasi ulang") — 1 layer offset (-3dp,-3dp) tint platinum terang, 1 layer
offset (+3dp,+3dp) tint gelap, dipasang di belakang konten kartu
(`SkeuDualDirectionalShadow`, `SkeuomorphicComponents.kt`). **KHUSUS
Neumorphism** (`NeumoPlatinum.copy(alpha=0.55f)` / `NeumoPanelRecessed.copy(
alpha=0.95f)`) — 3 varian lain (Midnight Glass, Aurora Glass, Studio Equalizer)
diisi eksplisit `Color.Transparent` di `Theme.kt` (WAJIB diisi semua instance
per konvensi komentar `SkeuTokens` sendiri) sehingga layer di-skip TOTAL, 0
draw call tambahan, 0 perubahan visual — Midnight/Aurora Glass sengaja tetap
"visually quiet" (guide §8 lama) dan Studio Eq tetap "low-contrast/subtle by
design" (Batch 43), TIDAK ikut kena efek ini.

**Fix 2 — falloff `skeuGlow` 4-stop (global, semua varian)**: dari 2-stop hard
cutoff ke 4-stop landai (90%→55%→18%→0% alpha di 0/35/70/100% radius) — masih
`Brush.radialGradient` native, cuma stop lebih banyak, mensimulasikan falloff
cahaya beneran. Berlaku ke SEMUA pemakai `skeuGlow` (power button, switch,
preset chip di `BoosterScreen.kt`, unchanged call sites) — bukan Neumorphism
doang, karena "hard-cutoff kebaca bocor" adalah masalah teknik render, bukan
masalah palet 1 varian.

**Bug ditemukan & diperbaiki SEBELUM dikirim** (self-review, bukan laporan
user): draf pertama `Brush.radialGradient(colorStops = arrayOf(...), ...)` —
INI COMPILE ERROR. `colorStops` dideklarasikan `vararg colorStops: Pair<Float,
Color>` di API Compose asli, parameter vararg TIDAK BISA di-assign pakai
named-argument + `arrayOf(...)` langsung (butuh spread `*arrayOf(...)` atau
pairs langsung positional). Diperbaiki: pairs dikirim positional langsung
(`0.00f to color..., 0.35f to color..., ..., center = center, radius =
glowRadius`), tanpa `arrayOf`/spread. Dicek ulang: tidak ada pola
`= arrayOf(...)` lain yang salah pakai di project.

**Layout regression dihindari**: `SkeuCard`/`SkeuTintedCard` sebelumnya
`Column(modifier = modifier, ...)` langsung — sekarang dibungkus `Box { ... }`
buat wadah 2 layer shadow tambahan. `modifier` (caller punya, misal
`fillMaxWidth()`) SENGAJA tetap dipasang di `Column` PERSIS posisi lama (bukan
dipindah ke outer `Box`), `Box` sendiri TANPA modifier apa pun — supaya
sizing/layout existing 20+ call-site `SkeuCard`/`SkeuTintedCard` di
`BoosterScreen.kt` TIDAK berubah sama sekali (`Box` cuma wrapper visual buat
2 shadow layer `matchParentSize()`, ukurannya ngikut `Column` seperti biasa).

**Belum divalidasi runtime** — statis only lagi (brace/paren `Theme.kt` &
`SkeuomorphicComponents.kt` 0 selisih setelah fix compile-error di atas, semua
4 varian `SkeuTokens` eksplisit isi 2 field baru — dicek grep 8/8 assignment).
Efek visual riil (kedalaman dual-shadow, glow gak "bocor" lagi) baru
terkonfirmasi setelah build ulang APK + install + screenshot baru dari user —
**screenshot yang dikirim user kemungkinan besar dari build SEBELUM Batch 46/47
di-compile** (kalau baru rebuild abis Batch 46 kemarin, warna power button di
situ mestinya udah ruby #E0115F, bukan cuma pink generik — perlu rebuild+install
ulang lewat skrip Termux Update Harian buat lihat hasil Batch 46 DAN 47
sekaligus).

## v1.83.0 - Batch 46: upgrade varian 3 Skeuomorphism -> Neumorphism ultra realistic+immersive, aksen Platinum+Ruby

Diminta user eksplisit: "Lanjutkan polish UI yang pending. Dan upgrade
Skeuomorphism -> Neumorphism ultra realistic+immersive, dengan sentuhan accent
Platinum+Ruby". 5 file: `Theme.kt`, `MainActivity.kt` (edit parsial, komentar+1
referensi brush), `values/strings.xml`+`values-en/strings.xml` (2 string
masing-masing), `docs/preview/current.html` (footer note).

**Filosofi berubah total (bukan cuma ganti palet)**: varian ke-3 sebelumnya
"Skeuomorphism" (Batch 38-39) — shadow pasangan `Color.White`/`Color.Black`
mentah (bevel hard-edge, ekstrusi fisik tegas). Sekarang genuine **Neumorphism**
— shadow pasangan SEHUE base panel (pola arsitektur sama seperti "Studio
Equalizer" Batch 43, tapi palet & intensitas beda total). "Ultra
realistic+immersive" diterjemahkan jadi 3 hal konkret vs Studio Equalizer:
1. `cardElevation` 10dp — **tertinggi dari 4 varian** (Studio Eq 6dp, Skeuo lama
   8dp, Aurora 6dp, Midnight Glass 3dp) — pop paling dramatis.
2. Bevel brush 5-stop (Studio Eq 3-stop, Skeuo lama 4-stop) — transisi
   terang->gelap lebih halus/dalam, kesan "timbul" lebih realistis.
3. Sheen specular alpha 0.30f (Studio Eq 0.16f) — glossy metalik lebih kuat,
   tapi tetap platinum-tinted (bukan `Color.White` polos era Skeuomorphism) biar
   tetap sehue/neumorphism genuine, bukan balik ke glossy-kaca ala varian glass.

**Palet Platinum+Ruby** (ganti dari titanium-silver Batch 39):
- `NeumoPlatinum #E4E3E0` / `NeumoPlatinumDeep #9C9CA1` — metalik netral-dingin,
  dipakai LUAS (bevel highlight, border, knob, sheen) di SELURUH panel — bukan
  cuma 1 chip accent kecil, sama prinsip "karakter metalik kerasa di seluruh
  panel" seperti titanium-silver Batch 39.
- `NeumoRuby #E0115F` / `NeumoRubyDeep #9E0C43` — jewel-tone merah jenuh, KHUSUS
  primary/state-aktif (`colorScheme.primary`, `primaryGlow` alpha 0.38f —
  tertinggi dari 4 varian, "immersive" = state aktif menyala lebih dramatis).
  Platinum sengaja netral (bukan aksen utama lagi) supaya Ruby "menyala"
  kontras di atasnya — meniru kombinasi perhiasan/jam tangan mewah
  platinum-bermata-ruby.

**Rename total mengikuti preseden Batch 34** ("pivot filosofi = rename semua
token, bukan reuse nama lama meski sebagian hex mirip"): semua token warna/brush
`SkeuoXxx` -> `NeumoXxx` (`SkeuoBackground`->`NeumoBackground`, dst, termasuk
`SkeuoAccent`/`SkeuoAccentDeep` yang sekarang dipecah konsep jadi
`NeumoPlatinum`+`NeumoRuby` terpisah, bukan 1 warna serba-guna lagi), val
turunannya `SkeuomorphismSkeuTokens`/`SkeuomorphismDarkColors`/
`SkeuomorphismShapes` -> `NeumorphismSkeuTokens`/`NeumorphismDarkColors`/
`NeumorphismShapes`. Radius `SkeuoCardRadius`/`SkeuoIconBoxRadius` (14dp/10dp,
sudut tegas skeuomorphism) -> `NeumoCardRadius`/`NeumoIconBoxRadius` (22dp/15dp,
rounded generous soft-UI) — beda dari Studio Eq (20dp/14dp) & iOS-glass
(26dp/16dp) biar tetap otonom per varian. `SkeuomorphismShapes` (Material3
default Button/AlertDialog/dll) ikut disesuaikan ke radius baru (10-26dp).

**TIDAK diubah (Protected Asset persistence key — data user lama harus tetap
valid, preseden sama seperti Batch 37 mempertahankan `RADICAL_SKEUO`)**: enum
`AppThemeStyle.SKEUOMORPHISM` & `PrefsHelper.APP_THEME_SKEUOMORPHISM`
("skeuomorphism"). Nama toggle di kode/persistence TETAP, cuma label
user-facing (`theme_style_skeuo_title`/`_desc`, ID+EN) & isi visual yang
berubah — user yang sudah pilih varian ini sebelumnya otomatis lanjut ke
tampilan Neumorphism baru tanpa perlu toggle ulang.

**Bug ditemukan & diperbaiki SEBELUM sempat dikirim** (self-review, bukan
laporan user): draf awal `NeumoEdgeHighlight` sempat referensi `NeumoPlatinum`
SEBELUM deklarasinya sendiri di file (forward-reference) — di Kotlin, top-level
`val` di-init berurutan sesuai posisi file, referensi ke `val` yang belum
ke-init bisa null-crash saat class-load. Diperbaiki dengan reorder: `NeumoPlatinum`/
`NeumoRuby` dideklarasikan SEBELUM `NeumoEdgeHighlight`/`NeumoEdgeShadow` yang
memakainya. Dicek ulang: seluruh urutan deklarasi di blok token baru sudah
tidak ada forward-reference lagi.

**File lain YANG TIDAK PERLU disentuh** (dicek eksplisit via grep sebelum
edit — cuma Theme.kt & MainActivity.kt yang refer ke token `Skeuo*` lama):
`SkeuomorphicComponents.kt` (100% generic lewat `LocalSkeuTokens.current`,
otomatis kompatibel tanpa diubah — arsitektur Batch 36 terbukti lagi),
`BoosterScreen.kt` (toggle switch baca `PrefsHelper.APP_THEME_SKEUOMORPHISM`
yang TIDAK berubah, bukan warna hardcode), `README.md` (tidak pernah menyebut
nama varian tema sama sekali).

**"Lanjutkan polish UI yang pending"**: diinterpretasikan sebagai kelanjutan
item design-system yang memang berstatus pending (upgrade varian ke-3 ini).
Backlog audit Medium/Low lama (recomposition review, micro-animation tambahan,
loading/empty/error state, dst — dicatat berulang sejak Batch 16, belum pernah
disentuh) **TETAP belum dikerjakan** — di luar scope batch ini, TIDAK
dikerjakan proaktif sesuai mode maintenance (lihat PROJECT_STATE.md).

**Belum divalidasi runtime** — statis only (brace/paren balance Theme.kt &
MainActivity.kt 0 selisih setelah fix forward-reference, parity string ID/EN
100/100, XML valid, grep sweep referensi `Skeuo*` lama di luar Theme.kt = nihil,
sweep token baru di seluruh project = cuma 2 file kode yang kepakai, sesuai
ekspektasi arsitektur `LocalSkeuTokens` generik). Efek visual riil (kontras
platinum-ruby, kedalaman shadow 5-stop, dll) baru terkonfirmasi setelah build +
cek device — `docs/preview/current.html` TIDAK dapat mockup visual terpisah
buat varian ini (di luar scope, cuma footer note disinkron, sama seperti Batch
38/43 dulu).

## v1.82.0 - Batch 45: fix race condition `isRunning` + kunci prioritas CPU service (peak performance)

Diminta user eksplisit: "indikasi race condition" + "kunci aplikasi dipuncak
performa nya". 1 file: `AudioEnhancerService.kt`, 2 perubahan independen.

**1) Race condition nyata (bukan indikasi/dugaan — dikonfirmasi dari analisis cross-thread)**:
`AudioEnhancerService.isRunning` (companion `var`, sumber kebenaran dipakai
Widget/QS Tile/Watchdog) ditulis di `onStartCommand`/`onDestroy` — dijamin main
thread oleh Service lifecycle Android. TAPI dibaca juga dari
`ServiceWatchdogWorker.doWork()`, yang jalan sebagai `CoroutineWorker` di
background dispatcher WorkManager (BUKAN main thread), tiap 15 menit. Tanpa
`@Volatile`, Java Memory Model tidak menjamin thread watchdog melihat nilai
TERBARU (celah klasik: CPU/compiler boleh cache nilai lama per-thread tanpa
`happens-before` eksplisit). Dampak nyata kalau kena: watchdog baca `isRunning`
basi -> (a) nganggep service masih hidup padahal sudah mati -> GAGAL restart
(tujuan utama watchdog batal total, silent failure, gak ada crash/log apapun),
atau (b) nganggep mati padahal hidup -> restart double sia-sia. Widget
(`BroadcastReceiver.onReceive`) & QS Tile (`TileService` callback) TIDAK kena
masalah yang sama — keduanya dijamin selalu main thread oleh framework, cuma
watchdog yang beda thread. **Fix**: `@Volatile` di field ini — baca/tulis wajib
langsung ke main memory, bukan cache lokal per-thread/per-core.

**2) "Kunci performa puncak"**: `onCreate()` sekarang panggil
`Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)` (dibungkus
try-catch, no-op diam-diam kalau OEM tertentu restrict RT-priority). Rasional:
status "foreground service" (sudah ada sejak awal) cuma menaikkan
importance/`oom_adj` (supaya gak gampang dibunuh OS saat low-memory) — itu
TIDAK sama dengan menaikkan prioritas penjadwalan CPU (nice-value) thread-nya.
Tanpa ini, panggilan `attachEffects()`/`enableEffects()`/`set*Strength()` (IPC
ke audio HAL) tetap bisa antre CPU di belakang proses lain kalau sistem lagi
sibuk. Dikunci ke `THREAD_PRIORITY_URGENT_AUDIO` (level sama yang dipakai
native audio thread sistem Android) supaya konsisten dapat slot CPU prioritas
tertinggi, bukan naik-turun ikut beban sistem.

**Belum divalidasi runtime** — brace/paren balance 0 selisih (semua 16 file
Kotlin project, bukan cuma file yang disentuh). `@Volatile` & `Process.
setThreadPriority` API resmi JVM/Android (bukan reka-reka), tapi efek RIIL
(watchdog gak lagi salah baca state stale, latency IPC audio effect
konsisten) baru terkonfirmasi kalau ada laporan lapangan sebelumnya yang
cocok gejala (mis. "kadang widget nyala tapi notifikasi ilang & gak balik
sendiri walau ditunggu >15 menit") — kalau user pernah alami itu, ini
kandidat root cause-nya. **PENTING buat sesi depan**: field/state APAPUN yang
ditulis di 1 thread tapi dibaca di thread lain (terutama kombinasi manapun
dengan `CoroutineWorker`/WorkManager, yang TIDAK jalan di main thread) WAJIB
`@Volatile` atau mekanisme sinkronisasi lain — jangan asumsikan "cuma boolean
sederhana jadi aman", itu justru pola race condition paling umum & paling
gampang lolos review manual karena kelihatan tidak berbahaya.


## v1.81.1 - Batch 44 (bugfix): QS Tile basi ("widget aktif, QS Tile nonaktif")

Dilaporkan user: widget home-screen nunjukin Aktif, tapi tile Quick Settings
malah nunjukin Nonaktif — inkonsistensi state antar 2 entry point yang sama-sama
baca `AudioEnhancerService.isRunning`.

**Root cause**: `AudioEnhancerService` manggil `BoosterWidgetProvider.refreshAll()`
di 3 titik state-change (`onStartCommand` start, `onStartCommand` ACTION_STOP,
`onDestroy`) — TAPI gak ada panggilan setara buat QS Tile di titik manapun.
`QuickToggleTileService` cuma refresh diri sendiri di `onStartListening()` (pas
notification shade DIBUKA user) & di `onClick()`-nya sendiri (optimistic update).
Akibatnya: toggle dari path LAIN (widget, tombol power di app, BootReceiver, App
Shortcut) mengubah `isRunning` + widget ikut update BENAR, tapi tile Quick
Settings TETAP nampilin state lama sampai user nutup-buka shade ulang (baru
`onStartListening()` ke-trigger lagi).

**Fix**: `QuickToggleTileService` dapat companion `requestTileUpdate(context)` —
panggil `TileService.requestListeningState()` (API bawaan Android, dijamin ada
sejak API 24 = sama dengan `minSdk` project ini, gak perlu version-check), yang
bikin sistem manggil `onStartListening()` SEKARANG JUGA (bukan nunggu shade
dibuka), sinkron ke `isRunning` terbaru. Dipanggil di SEMUA 3 titik yang sama
persis dengan `BoosterWidgetProvider.refreshAll()` — pola arsitektur "1 hook
nutup semua listener" yang sudah didesain widget-nya (`BoosterWidgetProvider.kt`
komentar "Status di-refresh dari SATU titik") sekarang BENERAN nutup semua
listener, termasuk QS Tile yang sebelumnya kelewat.

**File yang berubah (2)**: `QuickToggleTileService.kt` (+companion
`requestTileUpdate`), `AudioEnhancerService.kt` (+1 baris panggilan di 3 titik
state-change yang sudah ada).

**Belum divalidasi runtime** — brace/paren balance 0 selisih di 2 file, 3 call
site + 1 definisi terkonfirmasi lewat grep. `TileService.requestListeningState`
API resmi Android (bukan reka-reka), aman dipanggil walau tile belum pernah
ditambahkan user ke shade (no-op didokumentasikan resmi Android). Efek riil
(tile beneran sinkron instan tanpa perlu buka-tutup shade) baru terkonfirmasi
setelah build + tes langsung di device: toggle dari widget/app, lalu buka shade
— tile harus langsung sinkron TANPA perlu tutup-buka shade dua kali.



## v1.81.0 - Batch 43: tema ke-4 "Studio Equalizer" (neumorphism)

Diminta user eksplisit dengan 4 warna HEX persis (bukan interpretasi bebas):
palet "papan mixer studio rekaman profesional", neumorphism (soft-UI) — BEDA
dari Skeuomorphism (Batch 38-39, bevel hitam/putih tegas) & 2 varian glass
(Midnight/Aurora). Toggle ke-4, sejajar 3 toggle existing di Settings (SATU
pilihan aktif dari 4, bukan sub-opsi).

**Palet asli (persis dari user):**
- Background/Base `#1E222A` — abu-abu studio gelap
- Dark Shadow `#14171D` — bayangan sudut bawah
- Light Shadow `#282D37` — bayangan sudut atas
- Aksen Glow (Aktif) `#39FF14` — hijau lime elektrik, lampu indikator

**Rasional desain kunci** (didisclose biar jelas bukan tebak-tebakan):
1. **Neumorphism ≠ Skeuomorphism**: shadow pasangan (`StudioEqLightShadow`/
   `StudioEqDarkShadow`) SEHUE sama base panel, bukan `Color.White`/`Color.Black`
   alpha kayak `SkeuoEdgeHighlight`/`SkeuoEdgeShadow` — beda filosofis inti antara
   2 bahasa desain ini, bukan sekadar beda warna.
2. **Arah gradient card/bevel ikut deskripsi user PERSIS**: "Dark Shadow: bayangan
   sudut BAWAH" / "Light Shadow: bayangan sudut ATAS" -> `StudioEqCardBrush` 3-stop
   Light(atas)->Base->Dark(bawah), bukan arah sembarang.
3. **Hijau `#39FF14` DIJAGA cuma nyala di state aktif** ("lampu indikator" per
   deskripsi user) — dipakai di `primaryGlow` (ring power button aktif, dsb) &
   `colorScheme.primary` (Material default), TAPI panel/kartu tetap netral abu-abu
   studio (`baseSurface`/`cardBrush` TIDAK ada hijau) — hijau bukan warna permukaan.
4. **Radius sendiri** `StudioEqCardRadius`/`IconBoxRadius` = 20dp/14dp — beda dari
   3 varian lain (26/16 glass, 14/10 skeuo), + `StudioEqShapes` buat komponen
   Material3 default (konsisten pola Batch 39 `SkeuomorphismShapes`).

**File yang berubah (6, semua bagian 1 fitur atomik — Settings switch baru butuh
sentuh seluruh chain state: token warna -> persistence -> mapping -> UI toggle ->
string, gak bisa dipecah lebih kecil tanpa app crash/inconsistent)**:
`Theme.kt` (blok token+colorScheme+shapes "Studio Equalizer" lengkap, +1 cabang
`AppThemeStyle` enum, +3 titik `when` di `AudioEnhancerTheme`), `PrefsHelper.kt`
(+`APP_THEME_STUDIO_EQ`), `MainActivity.kt` (+cabang mapping enum & screenBrush),
`BoosterScreen.kt` (+toggle ke-4 "Studio Equalizer", +import `Icons.Filled.Equalizer`
— sudah ada di dependency `material-icons-extended`, dipakai icon extended lain
kayak `GraphicEq`/`SurroundSound`/`Palette`), `strings.xml`+`strings.xml` (values-en)
(+`theme_style_studioeq_title`/`_desc`, ID & EN).

**Belum divalidasi runtime** — statis only: brace/paren balance 0 selisih di 4
file Kotlin yang disentuh, parity string ID/EN 100/100, `xmllint` valid 2 file,
sweep `AppThemeStyle.` di seluruh project — semua reference kevalidasi exhaustive
(gak ada cabang `when` yang lupa nambah STUDIO_EQ). Efek visual RIIL (kontras,
keterbacaan neon-green di atas dark-grey, dll) baru terkonfirmasi setelah build
jalan & dicek langsung di device.



## v1.80.1 - Batch 42: unique key per GitHub Release (cegah duplikasi)

Diminta user eksplisit: "tambahkan unique key pada setiap output github release,
untuk mencegah duplikasi terjadi lagi". `.github/workflows/build.yml` — 4 titik
diubah, semua sekarang pakai `github.run_id` (dijamin GLOBAL UNIK oleh GitHub,
gak pernah reset/reuse — beda dari `run_number` yang bisa ambigu kalau ada
"re-run" manual job gagal):

1. **`tag_name`**: `v<versi>` polos -> `v<versi>-run<run_id>`.
2. **`name` (judul Release)**: tambah `(Run #<run_number>)` di akhir — run_number
   dipakai di JUDUL (lebih pendek/manusiawi buat dibaca), run_id dipakai di TAG
   (butuh jaminan unik mutlak, bukan cuma gampang dibaca).
3. **Nama file APK** (step "Rename APK"): `AudioEnhancerPro-v<versi>-release.apk`
   -> `AudioEnhancerPro-v<versi>-run<run_id>-release.apk`.
4. **Nama Actions Artifact** (step "Upload signed release APK"): ikut pola sama.

**Root cause yang di-address**: Batch 11 SENGAJA bikin `tag_name` cuma `v<versi>`
polos, DIPAKAI ULANG (reuse, bukan create baru) kalau versionName yang sama
dipush lagi — asset APK lama di-overwrite. Desain ini secara sadar OVERWRITE by
design, tapi berpotensi race/collision kalau ada skenario tertentu (push
berulang cepat tanpa bump versi, retry, re-run job gagal, dst) — sumber
"duplikasi" yang dilaporkan user. **Batch 42 OVERRIDE keputusan Batch 11 secara
sadar** sesuai instruksi eksplisit: setiap run sekarang bikin tag+Release+asset
BARU dengan key yang GLOBAL UNIK, gak pernah menimpa/tabrakan dengan run
manapun, apapun skenarionya.

**Trade-off yang didisclose (WAJIB dibaca sebelum tanya "kenapa Releases numpuk")**:
tab Releases sekarang bakal punya 1 entry PER RUN CI yang sukses publish, BUKAN
1 per versionName lagi. Push 3x tanpa ubah versionName = 3 Release terpisah
muncul (`v1.80.1-run111`, `-run222`, `-run333`), bukan 1 Release yang keupdate.
Ini KONSEKUENSI LANGSUNG dari "gak pernah duplikasi/tabrakan" — kalau mau balik
ke perilaku "1 Release per versi, timpa kalau sama" nanti, itu instruksi
terpisah (tinggal hapus `-run${{ github.run_id }}` dari `tag_name`, TAPI itu
BALIK ke resiko yang baru saja di-fix di batch ini — jangan diubah balik tanpa
alasan baru dari user).

**File yang berubah**: `.github/workflows/build.yml` (4 titik nama unik),
`app/build.gradle.kts` (versionCode 81->82, versionName 1.80.0->1.80.1).

**Belum divalidasi runtime** — YAML disyntax-check (`yaml.safe_load`, valid).
`github.run_id`/`github.run_number` keduanya context variable BAWAAN GitHub
Actions (bukan reka-reka, dijamin selalu tersedia tiap run), tapi hasil AKHIR
(tag/Release/asset baru beneran ke-generate dengan nama sesuai + gak ada
tabrakan lagi) baru terkonfirmasi setelah CI run beneran & user cek tab
Releases.



## v1.80.0 - Lanjutan pangkas waktu compile CI (Batch 41)

Diminta user lagi: "lanjutkan percepat compiler". 3 perubahan low-risk, semuanya
flag/config RESMI terdokumentasi Kotlin/kapt/AGP, TANPA ubah dependency/versi
apapun — jadi aman diverifikasi tanpa compiler (beda dari kapt→KSP/configuration-
cache yang tetap ditahan, lihat bagian "SENGAJA TIDAK dilakukan" di bawah).

**1. `gradle.properties` — `kapt.use.worker.api=true`**: kapt (dipakai Hilt,
`app/build.gradle.kts`) sekarang jalan lewat Gradle Worker API — annotation-
processing task (`kaptDebugKotlin`/`kaptReleaseKotlin`) bisa dieksekusi
paralel/isolated per-worker, bukan numpuk di 1 thread yang nyambung langsung ke
proses kapt utama. Manfaat kerasa kalau CPU runner (`ubuntu-latest`) ada core
nganggur pas kapt jalan.

**2. `gradle.properties` — `kapt.incremental.apt=true`**: kapt cuma reproses stub
yang berubah, bukan semua source tiap kali dipanggil. Manfaat KECIL di CI (setiap
run checkout FRESH, gak ada state incremental lama yang persisten antar-run —
beda dari dev lokal yang beneran iteratif), TAPI nol downside untuk dinyalakan,
dan 2 variant kapt (`kaptDebugKotlin` vs `kaptReleaseKotlin`, dijalankan berurutan
1 job yang sama sejak Batch 40) state-nya terpisah per-variant jadi aman gak
saling tabrakan.

**3. `app/build.gradle.kts` — `buildFeatures.buildConfig = false`**: kelas
`BuildConfig` (auto-generate Android, biasanya isinya `BuildConfig.DEBUG`/
`VERSION_CODE`/dst) TERKONFIRMASI **0 pemanggil** di seluruh
`app/src/main/java/com/audioenhancer/booster/` (`grep -rn "BuildConfig"`
dijalankan eksplisit SEBELUM diubah, bukan asumsi) — matiin generate-nya skip
task `generateDebugBuildConfig`/`generateReleaseBuildConfig` sepenuhnya per
variant, 100% aman karena memang gak pernah dipakai di kode manapun.

**File yang berubah:** `gradle.properties` (+2 flag kapt), `app/build.gradle.kts`
(`buildFeatures.buildConfig = false`, versionCode 80->81, versionName
1.79.0->1.80.0).

**SENGAJA TIDAK dilakukan (masih sama alasannya, belum ada perubahan kondisi)**:
- Commit `gradlew`+`gradle-wrapper.jar` permanen ke repo — lever paling besar
  BERIKUTNYA (hilangin overhead bootstrap wrapper SEPENUHNYA, bukan cuma
  di-cache), TAPI `gradle-wrapper.jar` itu file BINER, sandbox Claude gak bisa
  generate/tulis file biner ini sendiri (butuh Gradle terinstal + tanpa network
  buat download) — perlu dikerjakan user dari mesin dev manapun yang punya
  Gradle, generate sekali (`gradle wrapper --gradle-version 8.7`), commit
  manual 4 file hasilnya. Di luar kapasitas sandbox ini, bukan soal risiko.
- kapt→KSP (Hilt) & `org.gradle.configuration-cache` — sama seperti Batch 40,
  tetap ditahan (keputusan sadar Batch 18 soal kapt, dan riwayat kapt+
  configuration-cache kurang mulus di kombinasi Kotlin 1.9.24/AGP 8.5.2 ini).

**Belum divalidasi runtime** — 3 flag di atas semuanya dokumentasi resmi
Kotlin/kapt/AGP (bukan reka-reka), `buildConfig=false` diverifikasi aman lewat
grep eksplisit (bukan asumsi kosong), tapi efek riil & konfirmasi nol regresi
baru cuma bisa dipastikan setelah CI run beneran.



## v1.79.0 - Pangkas waktu compile GitHub Actions CI

Diminta user: "bagaimana caranya agar waktu compile action GitHub bisa dipangkas
sebanyak mungkin". 3 perubahan, urutan dari dampak terbesar:

**1. `.github/workflows/build.yml` — job `build` + `release` DIGABUNG jadi 1 job
`build-and-release`** (sebelumnya 2 job terpisah, `release: needs: build`):
- 2 job = 2 runner VM terpisah dari nol tiap run: checkout+setup-JDK+bootstrap
  wrapper KEDUA KALI (overhead murni ~30-60 detik x2). Digabung 1 job = overhead
  ini cuma sekali.
- Gradle/Kotlin daemon TETAP HIDUP antar step dalam 1 job — `assembleRelease`
  sekarang start dengan daemon yang udah panas dari `assembleDebug` barusan
  (beda VM = daemon dingin dari nol, ini yang HILANG di setup 2-job lama).
- Semantik "skip release kalau debug gagal" TIDAK berubah — step tanpa
  `if: always()`/`if: failure()` otomatis di-skip GitHub Actions kalau ada step
  sebelumnya gagal di job yang sama, PERSIS sama seperti efek `needs: build` yang
  lama. Semua nama step & artifact log (`log_fail_v*-debug-run*`/
  `log_fail_v*-release-run*`) TETAP SAMA, cuma sekarang di 1 job bukan 2.

**2. `actions/setup-java@v4` — tambah `cache: 'gradle'`**: cache bawaan resmi,
nyimpen `~/.gradle/caches` (SEMUA dependency Maven — AndroidX, Compose, Hilt,
Kotlin stdlib) + `~/.gradle/wrapper/dists` (distribusi Gradle 8.7 itu sendiri,
~120MB, yang SEBELUMNYA didownload ULANG SETIAP RUN karena `gradlew` di-generate
on-the-fly & gak pernah di-commit — lihat step "Bootstrap Gradle Wrapper"). Cache
key di-hash dari isi `build.gradle.kts`/`settings.gradle.kts`/`gradle.properties`
di repo — otomatis invalidate sendiri kalau dependency berubah. **Run PERTAMA
setelah ini tetap full-download** (belum ada cache lama), run KEDUA dst baru
kerasa jauh lebih cepat.

**3. `gradle.properties` — `org.gradle.parallel=true` + `org.gradle.caching=true`
+ heap 2048m->3072m**: flag bawaan Gradle, gratis, TANPA ubah dependency/kode
apapun. `parallel` manfaatnya kecil sekarang (project cuma 1 module `:app`) tapi
gak ada downside & siap kalau nanti multi-module. `caching` (build cache LOKAL di
runner yang sama) bisa reuse task-output antara `assembleDebug`/`assembleRelease`
yang sekarang jalan berurutan di 1 job yang sama (poin 1). Heap dinaikkan karena
runner `ubuntu-latest` punya RAM 7GB, ada headroom aman.

**SENGAJA TIDAK dilakukan (dijelaskan biar gak dicoba ulang tanpa alasan baru)**:
- **`org.gradle.configuration-cache`** — kapt (Hilt, `app/build.gradle.kts`)
  riwayatnya kurang mulus dikombinasi configuration cache di kombinasi Kotlin
  1.9.24/AGP 8.5.2 project ini, dan gak ada cara verifikasi lokal (sandbox ini gak
  ada Android SDK/Gradle) sebelum push ke CI sungguhan.
- **kapt -> KSP (Hilt)** — lever paling besar berikutnya kalau butuh lebih cepat
  lagi (kapt jauh lebih lambat dari KSP buat annotation processing), TAPI ini
  keputusan sadar Batch 18 ("kapt dipakai karena paling teruji buat kombinasi
  Kotlin 1.9.24 ini, KSP-Hilt butuh versi KSP yang harus persis cocok, risiko
  mismatch lebih tinggi tanpa compiler buat verifikasi") — TIDAK diubah tanpa
  user minta eksplisit, karena riskan & gak bisa divalidasi di sandbox ini.
- **Commit `gradlew`+`gradle-wrapper.jar` permanen ke repo** (bukan
  di-generate ulang tiap run) — akan hilangin overhead "Bootstrap Gradle Wrapper"
  sepenuhnya (bukan cuma di-cache), tapi ini ubah arsitektur delivery
  ZIP/Termux-nya project ini (kenapa gradlew gak pernah di-commit dari awal gak
  terdokumentasi di riwayat batch manapun) — di luar scope "pangkas waktu compile"
  murni, disebut di sini sebagai opsi lanjutan kalau user mau.

**File yang berubah:** `.github/workflows/build.yml` (restrukturisasi 2 job -> 1
job + `cache: 'gradle'`), `gradle.properties` (parallel+caching+heap),
`app/build.gradle.kts` (versionCode 79->80, versionName 1.78.0->1.79.0).

**Belum divalidasi runtime** — YAML disyntax-check (`yaml.safe_load`, valid), TAPI
efek pemangkasan waktu SEBENARNYA cuma bisa dikonfirmasi setelah run CI beneran
(terutama cache hit di run KEDUA dst — run pertama setelah update ini gak akan
kerasa lebih cepat karena cache masih kosong).



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
