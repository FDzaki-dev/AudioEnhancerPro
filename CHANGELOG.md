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
