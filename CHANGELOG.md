# Changelog

> 🧠 **Sesi Claude baru?** Baca `PROJECT_STATE.md` dulu (bukan file ini) — didesain khusus buat konteks AI: keputusan desain+alasannya, batasan teknis, riwayat pivot. File ini (CHANGELOG) cuma buat detail teknis per-versi.

> 🎨 **Preview UI/UX terkini (live, selalu update)**: [buka di sini](https://htmlpreview.github.io/?https://github.com/FDzaki-dev/AudioEnhancerPro/blob/main/docs/preview/current.html) — render langsung dari `docs/preview/current.html` di repo ini, jadi selalu mencerminkan arah desain yang lagi didiskusikan sebelum di-build jadi APK.

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
