# AudioEnhancerPro

> 🧠 **Lanjut development di sesi Claude baru?** Paste link repo ini di awal chat:
> `https://github.com/FDzaki-dev/AudioEnhancerPro` — lalu **suruh Claude baca
> `PROJECT_STATE.md` dulu** (bukan cuma README ini). File itu didesain khusus
> buat AI: padat, berisi keputusan desain & alasannya, batasan teknis, dan
> riwayat pivot — biar sesi baru gak mulai dari nol atau ngulang pertanyaan
> yang sama.

Aplikasi Android booster/penjernih audio sistem berbasis Kotlin + Jetpack Compose.

## 🎨 Preview UI Terkini (live, selalu update)

**[▶ Buka Preview UI Interaktif](https://htmlpreview.github.io/?https://github.com/FDzaki-dev/AudioEnhancerPro/blob/main/docs/preview/current.html)**

Link di atas render langsung file `docs/preview/current.html` di repo ini lewat
[htmlpreview.github.io](https://htmlpreview.github.io) — live, tanpa perlu install APK
apapun, cukup buka di browser (HP atau desktop). Setiap kali ada perubahan UI/UX yang
cukup besar untuk didiskusikan dulu sebelum di-build jadi APK, file mockup ini di-update
bareng commit-nya, jadi link ini SELALU mencerminkan arah desain TERBARU yang sedang
dikerjakan — bukan cuma preview sekali pakai yang hilang di riwayat chat.

> Catatan: mockup ini HTML/CSS murni untuk validasi warna/layout/shape secara cepat —
> bukan representasi 1:1 pixel-perfect dari Compose asli (terutama font & icon vector),
> tapi cukup akurat untuk memutuskan "arah ini cocok atau enggak" sebelum menghabiskan
> siklus build+install APK yang jauh lebih lambat.

## Fitur
- Bass Boost, Virtualizer, Equalizer, Loudness Enhancer — ditempel ke audio session 0 (output global sistem).
- Foreground service (`mediaPlayback`) dengan `START_STICKY` supaya bertahan dari low-memory kill.
- Bertahan saat task di-swipe (`stopWithTask="false"` + `START_STICKY`, TANPA restart manual via `onTaskRemoved` — trik itu sempat dicoba lalu dicabut di v1.34 karena tidak reliable di Android 12+) dan otomatis jalan lagi saat device boot ulang.
- Permintaan exemption battery optimization saat pertama dibuka.
- Quick Settings Tile — toggle on/off langsung dari notification shade, tanpa buka app.
- App Shortcuts (long-press ikon launcher) — toggle instan + akses langsung ke preset custom.
- Widget home screen — status real-time + toggle sekali tap, tanpa buka app sama sekali.

## Batasan jujur
- Efek pada session 0 tidak dijamin bekerja di semua device/OEM (tergantung implementasi HAL audio vendor).
- Di HP dengan manajemen baterai agresif (MIUI, ColorOS, EMUI, dll), user tetap perlu mengizinkan "Autostart" secara manual — tidak ada cara app mem-bypass ini tanpa izin user.

## Build
```
./gradlew assembleDebug
```

CI otomatis build APK debug setiap push ke `main`/`master` via GitHub Actions (`.github/workflows/build.yml`), job `build` ini HANYA verifikasi kompilasi (`assembleDebug`) — **tidak ada step upload-artifact**, jadi APK debug-nya TIDAK muncul di tab Actions > Artifacts. Yang muncul di Artifacts cuma APK release dari job `release` (lihat bagian "Versioning APK Release" di bawah), dan itu pun cuma jalan kalau secret keystore sudah diset.

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

4. **Push ke `main`** — job `release` di workflow otomatis decode keystore dari secret, build `assembleRelease` dengan signing config, lalu upload APK release yang sudah signed sebagai artifact bernama `AudioEnhancerPro-v{versionName}-release` (dinamis, ngikutin `versionName` saat itu — lihat bagian "Versioning APK Release" di atas).

Kalau secret belum diset, job release akan skip otomatis tanpa bikin build gagal — job `build` (debug) tetap jalan normal.

## Troubleshooting

**Notifikasi service tidak muncul / hilang sendiri**
- Cek permission notifikasi belum ditolak: Settings > Apps > Audio Booster > Notifications.
- Cek battery optimization: sebagian HP (Xiaomi/MIUI, Oppo/ColorOS, Vivo/FuntouchOS, Samsung) agresif membunuh background service. Matikan battery optimization untuk app ini lewat Settings > Battery > pilih app > "Tidak dibatasi" / "No restrictions".
- Kalau baru install ulang, buka app minimal sekali biar `BootReceiver` bisa daftar ulang service.

**Efek (Bass Boost / Virtualizer / Loudness) tidak kerasa sama sekali**
- Cek slider tidak dalam kondisi `disabled` (abu-abu) — kalau disabled berarti efek itu memang tidak didukung chipset HP tersebut, bukan bug.
- Efek berlaku ke *audio session* aplikasi lain yang sedang aktif, bukan ke semua suara sistem sekaligus di semua kondisi — pastikan app musik/media yang diputar sedang aktif memutar audio saat slider digeser.
- Beberapa HP (terutama custom ROM agresif) bisa mem-block akses `AudioEffect` API pihak ketiga demi baterai — cek apakah app di-restrict di pengaturan baterai (lihat poin di atas).

**Slider terlihat aktif tapi kadang tidak nyambung ke efeknya**
- Kalau slider digeser dalam waktu sangat singkat setelah app baru dibuka (sebelum service selesai konek), sejak v1.12 perubahan itu otomatis ditampung dan diterapkan begitu service siap — tidak lagi hilang diam-diam. Kalau masih terjadi di versi lebih baru, kemungkinan ada regresi baru, cek Logcat untuk error `AudioEnhancerService`.

**Preset yang dipilih hilang setelah app ditutup**
- Sejak v1.11 preset aktif ikut tersimpan. Kalau masih hilang, cek app tidak di-"force stop" manual (force stop menghapus semua state in-memory dan bisa memicu re-read prefs yang aneh di sebagian custom ROM).

**Equalizer manual tidak muncul**
- Kartu "Equalizer Manual" hanya muncul kalau chipset HP mendukung `android.media.audiofx.Equalizer` dengan jumlah band > 0. Sebagian chipset budget tidak menyediakan equalizer per-band sama sekali — ini batasan hardware, bukan bug app.

**Build gagal di GitHub Actions**
- Cek apakah 4 secrets keystore (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) sudah diset kalau butuh APK release yang signed — kalau belum diset, job `release` di-skip otomatis (bukan gagal), tapi job `build` (debug) tetap harus sukses. Cek log job `build` dulu untuk error compile murni.
