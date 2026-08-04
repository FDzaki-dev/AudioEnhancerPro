# 🧠 PROJECT_STATE.md — baca file ini PALING PERTAMA

File ini didesain buat dibaca AI (Claude) di awal sesi baru, bukan cuma manusia.
Isinya padat & langsung actionable — bukan riwayat lengkap (itu ada di
CHANGELOG.md). Kalau kamu Claude dan baru diminta lanjut project ini:
1. Baca file ini full.
2. Baca 2-3 entry TERATAS CHANGELOG.md aja (bukan semua) buat detail teknis terbaru.
3. Baru mulai kerja. Jangan ulang pertanyaan yang jawabannya udah ada di sini.

---

## Status saat ini
- **Versi**: v1.52
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
- **Arah desain UI aktif**: "Neumorphic Hybrid" (Batch 12, v1.51, ARAH SEKARANG) —
  glassmorphism (translucent + border gradient + blur, v1.29-v1.49) DICABUT TOTAL,
  bukan cuma palet lagi. Kartu sekarang SOLID dengan dual-shadow extruded/pressed,
  teks selalu warna solid (gradient-clip text dibuang). Detail lengkap: lihat poin 5
  di "Riwayat pivot" bawah + `CHANGELOG.md` v1.51. Warna aksen per-fitur (Bass/
  Virtualizer/Loudness/Equalizer) & primary champagne-bronze TETAP dipertahankan.
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
5. **Neumorphic Hybrid** (Batch 12, v1.51, **ARAH SEKARANG** — sudah di-port penuh ke
   Kotlin) — kali ini STRUKTUR ikut diganti, bukan cuma palet,
   diminta user eksplisit sambil minta legibility ditingkatkan. Translucency/
   backdrop-blur & gradient-clip text (dua-duanya sumber inkonsistensi kontras di
   struktur glassmorphism #3/#4) dibuang total. Kedalaman visual sekarang dari
   dual-shadow neumorphic (extruded = "timbul", inset = "ditekan"), background
   base dinaikkan ke abu graphite medium `#232220` (bukan hitam pekat) supaya sisi
   highlight shadow-nya kelihatan. Warna aksen per-fitur & primary bronze TETAP,
   cuma dipakai lebih hemat (icon/slider/ring, bukan teks).

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
- `MainActivity.kt` — semua UI Compose (BoosterScreen, FeatureControl, GlassCard, CrashBanner, dst) + lifecycle Activity + bind ke Service.
- `AudioEnhancerService.kt` — foreground service, attach BassBoost/Virtualizer/Equalizer/LoudnessEnhancer ke session 0.
- `Theme.kt` — palet warna, typography, shape. Accent color per-fitur ada di sini (`BassAccent`, `VirtualizerAccent`, dst + varian "2" buat gradient).
- `PrefsHelper.kt` — SharedPreferences wrapper, semua persistence lewat sini (termasuk preset custom & timestamp crash log).
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
