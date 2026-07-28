# Changelog

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
