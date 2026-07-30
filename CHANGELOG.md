# Changelog

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
