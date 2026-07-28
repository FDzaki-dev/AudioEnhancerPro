# AudioEnhancerPro

Aplikasi Android booster/penjernih audio sistem berbasis Kotlin + Jetpack Compose.

## Fitur
- Bass Boost, Virtualizer, Equalizer, Loudness Enhancer — ditempel ke audio session 0 (output global sistem).
- Foreground service (`mediaPlayback`) dengan `START_STICKY` supaya bertahan dari low-memory kill.
- Restart otomatis saat task di-swipe (`onTaskRemoved`) dan saat device boot ulang.
- Permintaan exemption battery optimization saat pertama dibuka.

## Batasan jujur
- Efek pada session 0 tidak dijamin bekerja di semua device/OEM (tergantung implementasi HAL audio vendor).
- Di HP dengan manajemen baterai agresif (MIUI, ColorOS, EMUI, dll), user tetap perlu mengizinkan "Autostart" secara manual — tidak ada cara app mem-bypass ini tanpa izin user.
- Belum ada icon asli — ganti placeholder di `res/mipmap-mdpi` sebelum build release.

## Build
```
./gradlew assembleDebug
```

CI otomatis build APK debug setiap push ke `main`/`master` via GitHub Actions (`.github/workflows/build.yml`), hasil APK ada di tab Actions > Artifacts.
