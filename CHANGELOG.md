# Changelog

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
