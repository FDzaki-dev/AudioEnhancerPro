# 🧠 PROJECT_STATE.md — baca file ini PALING PERTAMA

File ini didesain buat dibaca AI (Claude) di awal sesi baru, bukan cuma manusia.
Isinya padat & langsung actionable — bukan riwayat lengkap (itu ada di
CHANGELOG.md). Kalau kamu Claude dan baru diminta lanjut project ini:
1. Baca file ini full.
2. Baca 2-3 entry TERATAS CHANGELOG.md aja (bukan semua) buat detail teknis terbaru.
3. Baru mulai kerja. Jangan ulang pertanyaan yang jawabannya udah ada di sini.

---

## Status saat ini
- **Versi**: v1.41 (v1.40 sempat GAGAL BUILD di CI — sudah di-fix, lihat "Batasan sandbox Claude" di bawah)
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
- **Arah desain UI aktif**: "native ultra premium" — glassmorphism (kartu
  translucent + border gradient tipis + shadow lembut), background gradient
  violet-gelap→hitam, tiap fitur (Bass/Virtualizer/Loudness/Equalizer) punya
  pasangan warna sendiri buat icon-orb/border/teks gradient.
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
3. **Native ultra premium / glassmorphism** (v1.29-v1.30, ARAH SEKARANG) —
   kartu translucent + border gradient tipis + shadow lembut + background
   gradient dalam + waveform motif di header. Sudah di-port penuh ke Kotlin.

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
- `MainActivity.kt` — semua UI Compose (BoosterScreen, FeatureControl, GlassCard, CrashBanner, dst) + lifecycle Activity + bind ke Service.
- `AudioEnhancerService.kt` — foreground service, attach BassBoost/Virtualizer/Equalizer/LoudnessEnhancer ke session 0.
- `Theme.kt` — palet warna, typography, shape. Accent color per-fitur ada di sini (`BassAccent`, `VirtualizerAccent`, dst + varian "2" buat gradient).
- `PrefsHelper.kt` — SharedPreferences wrapper, semua persistence lewat sini (termasuk preset custom & timestamp crash log).
- `CrashLogger.kt` — tangkap uncaught exception, simpan ke `filesDir/crash_logs/` (rotasi maks 5 file).
- `AudioEnhancerApp.kt` — Application class, cuma buat `CrashLogger.install()` sedini mungkin.
- `OemAutostartHelper.kt` — deep-link ke pengaturan Autostart/battery manager per-OEM (Xiaomi/Oppo/Vivo/Huawei/Samsung/OnePlus/Asus/Infinix-Tecno-itel), fallback ke App Info bawaan Android kalau semua kandidat gagal.
- `OnboardingScreen.kt` — 6 halaman onboarding.
- `docs/preview/current.html` — mockup HTML standalone, HARUS di-update kalau ada perubahan arah visual besar.

## TODO / belum dikerjain (kalau user nanya "lanjut yang mana")
- **PRIORITAS**: tunggu/tanya konfirmasi user soal hasil tombol Autostart
  v1.35 di Infinix Note 50 Pro 4G & Note 40 Pro 4G. Kalau gagal, opsi
  selanjutnya: (a) cari kandidat ComponentName alternatif buat XOS versi
  device itu spesifik, atau (b) terima kenyataan gak ada kandidat reliable
  buat Infinix/Tecno (persis kayak yang dialami `AutoStarter` library) dan
  fokus ke instruksi manual yang jelas di UI aja.
- Rotasi layar/config change, font scaling besar, landscape phone, RTL,
  kontras tombol biru — user bilang eksplisit TIDAK urgent, jangan dikerjain
  duluan tanpa diminta.

## Cara update file ini
Tiap sesi yang bikin keputusan arsitektur/desain baru (bukan sekadar bugfix
kecil), WAJIB update bagian "Status saat ini", "Riwayat pivot", dan/atau
"Keputusan sadar" di atas — supaya sesi berikutnya gak mulai dari nol lagi.
