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

## Versioning APK Release (Otomatis)

Nama file APK dan nama artifact di GitHub Actions **mengikuti `versionName` di `app/build.gradle.kts` secara otomatis** — tidak perlu diubah manual di workflow. Setiap kali mau rilis versi baru:

1. Ubah `versionName` (misal `"1.5"` → `"1.6"`) dan naikkan `versionCode` (+1) di `app/build.gradle.kts`.
2. Push ke `main`.
3. Artifact hasil build otomatis bernama `AudioEnhancerPro-v1.6-release`, isinya `AudioEnhancerPro-v1.6-release.apk`.

Hanya ada **1 artifact** yang dihasilkan tiap build: APK release yang sudah signed. Tidak ada lagi APK debug terpisah.

## Setup Release Signing (APK release, bukan debug)

1. **Buat keystore** (sekali saja, simpan file `.jks` ini baik-baik, jangan hilang/expose):
   ```
   keytool -genkeypair -v -keystore release.keystore -alias audioenhancerpro \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Ikuti prompt-nya (isi password keystore, password key, nama, dll).

2. **Encode keystore ke base64** supaya bisa disimpan sebagai GitHub Secret:
   ```
   base64 -w0 release.keystore > release.keystore.b64
   cat release.keystore.b64
   ```
   Copy seluruh isi output-nya.

3. **Tambahkan 4 secrets** di GitHub repo: Settings > Secrets and variables > Actions > New repository secret:
   | Name | Value |
   |---|---|
   | `KEYSTORE_BASE64` | isi dari `release.keystore.b64` |
   | `KEYSTORE_PASSWORD` | password keystore yang dibuat di langkah 1 |
   | `KEY_ALIAS` | `audioenhancerpro` (atau alias yang kamu pakai) |
   | `KEY_PASSWORD` | password key yang dibuat di langkah 1 |

4. **Push ke `main`** — job `release` di workflow otomatis decode keystore dari secret, build `assembleRelease` dengan signing config, lalu upload APK release yang sudah signed sebagai artifact bernama `audio-enhancer-pro-release-apk-signed`.

Kalau secret belum diset, job release akan skip otomatis tanpa bikin build gagal — job `build` (debug) tetap jalan normal.
