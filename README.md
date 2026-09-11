# AudioEnhancerPro

### [⬇️ Download APK Terbaru](https://github.com/FDzaki-dev/AudioEnhancerPro/releases/latest)
_Selalu resolve ke APK signed terbaru di tab Releases (URL `/releases/latest` bawaan GitHub)._

> 🧠 **Lanjut development di sesi Claude baru?** Paste link repo ini, lalu
> **suruh Claude baca `PROJECT_STATE.md` dulu** (bukan cuma README ini) —
> file itu berisi keputusan desain, batasan teknis, dan riwayat pivot,
> didesain khusus untuk AI biar sesi baru gak mulai dari nol.

Aplikasi Android booster/penjernih audio sistem berbasis Kotlin + Jetpack Compose.

## 🎨 Preview UI Terkini (live, selalu update)

**[▶ Buka Preview UI Interaktif](https://htmlpreview.github.io/?https://github.com/FDzaki-dev/AudioEnhancerPro/blob/main/docs/preview/current.html)**

Render langsung `docs/preview/current.html` lewat [htmlpreview.github.io](https://htmlpreview.github.io) — tanpa install APK, cukup buka di browser. Diupdate bareng tiap perubahan UI/UX besar, jadi selalu mencerminkan arah desain terbaru.

> Catatan: mockup HTML/CSS murni untuk validasi warna/layout/shape cepat — bukan 1:1 pixel-perfect dari Compose asli (terutama font & icon vector), tapi cukup akurat untuk keputusan "arah ini cocok atau enggak" sebelum build+install APK penuh.

## Fitur
- Bass Boost, Virtualizer, Equalizer, Loudness Enhancer — ditempel ke audio session 0 (output global sistem).
- Foreground service (`mediaPlayback`) dengan `START_STICKY` supaya bertahan dari low-memory kill.
- Bertahan saat task di-swipe (`stopWithTask="false"` + `START_STICKY`, TANPA restart manual via `onTaskRemoved` — trik itu sempat dicoba lalu dicabut di v1.34 karena tidak reliable di Android 12+) dan otomatis jalan lagi saat device boot ulang.
- Permintaan exemption battery optimization saat pertama dibuka.
- Quick Settings Tile — toggle on/off langsung dari notification shade, tanpa buka app.
- App Shortcuts (long-press ikon launcher) — toggle instan + akses langsung ke preset custom.
- Widget home screen — status real-time + toggle sekali tap, tanpa buka app sama sekali.
- Watchdog periodik (`WorkManager`, tiap 15 menit) — restart service otomatis kalau
  ternyata mati padahal user tidak pernah minta dimatikan. Menghormati pilihan user:
  kalau user sengaja tekan "Matikan", watchdog TIDAK menghidupkan paksa lagi.
- Update langsung dari dalam app — dicek otomatis tiap app dibuka, muncul banner "Unduh & Pasang" kalau ada versi baru. Tombol "Cek Update Sekarang" di Pengaturan (ikon ⚙️) untuk trigger manual — hasilnya selalu ditampilkan (sudah terbaru / ketemu update dengan komparasi versi + ringkasan rilis + tombol unduh / gagal), beda dari cek otomatis yang diam-diam kalau gagal.

## Batasan jujur
- Efek pada session 0 tidak dijamin bekerja di semua device/OEM (tergantung implementasi HAL audio vendor).
- Di HP dengan manajemen baterai agresif (MIUI, ColorOS, EMUI, dll), user tetap perlu mengizinkan "Autostart" secara manual — tidak ada cara app mem-bypass ini tanpa izin user.
- Watchdog periodik (di atas) mempercepat "sembuh sendiri" kalau service sempat dibunuh OS/OEM, TAPI bukan jaminan 100% service selalu hidup — di device dengan battery manager sangat agresif, OS tetap bisa menang berkali-kali dalam sehari.

## Build
```
./gradlew assembleDebug
```

CI otomatis build APK debug setiap push ke `main`/`master` via GitHub Actions (`.github/workflows/build.yml`), job `build` ini HANYA verifikasi kompilasi (`assembleDebug`) — tidak ada APK debug yang dipublikasikan di mana pun. APK yang beneran dirilis (signed) datang dari job `release` (lihat bagian "Versioning APK Release" di bawah), dan itu pun cuma jalan kalau secret keystore sudah diset.

## Versioning APK Release (Otomatis)

`versionCode` DAN `versionName` **keduanya otomatis dari `GITHUB_RUN_NUMBER`** — **JANGAN** diubah manual di `app/build.gradle.kts`. `versionName` adalah angka run number CI apa adanya (String, mis. `"78"`), sama persis dengan `versionCode` (Int) — bukan format semantik seperti `"1.5"`.

Cara rilis versi baru:

1. Push ke `main` — TIDAK ADA langkah edit `app/build.gradle.kts` lagi.
2. APK signed otomatis muncul di tab **Releases** (sidebar beranda repo)
   sebagai rilis `v{run_number}` (mis. `v78`), siap diunduh langsung — tanpa
   dibungkus `.zip`, tanpa perlu buka tab Actions. Nama filenya
   `AudioEnhancerPro-v{run_number}-run{run_id}-release.apk` (`run_id` beda
   dari `run_number` — angka internal GitHub yang jauh lebih besar, dipakai
   biar nama file/tag selalu unik walau rilis ulang commit yang sama).

APK yang sama juga tetap di-upload sebagai Actions Artifact (retensi lebih
pendek) buat akses cepat dari histori run kalau dibutuhkan, tapi **Releases**
adalah cara utama distribusi APK.

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

4. **Push ke `main`** — job `release` di workflow otomatis decode keystore dari secret, build `assembleRelease` dengan signing config, lalu publish sebagai GitHub Release `v{run_number}` (APK-nya jadi asset yang bisa diunduh langsung dari sidebar repo) + tetap upload artifact tambahan bernama `AudioEnhancerPro-v{run_number}-run{run_id}-release` (dinamis, ngikutin run CI saat itu — lihat bagian "Versioning APK Release" di atas).

Kalau secret belum diset, job release akan skip otomatis tanpa bikin build gagal — job `build` (debug) tetap jalan normal.

## Troubleshooting

**Notifikasi service tidak muncul / hilang sendiri**
- Cek permission notifikasi belum ditolak: Settings > Apps > Boomly > Notifications.
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
- Kartu "Equalizer Manual" muncul kalau chipset HP mendukung
  `android.media.audiofx.Equalizer` dengan jumlah band > 0, **ATAU** (sejak
  Batch 87, lihat `PROJECT_STATE.md` bagian "TODO / ROADMAP" Fase 0 #6)
  lewat fallback 5-band berbasis
  `DynamicsProcessing` kalau `Equalizer` asli tidak tersedia sama sekali di
  chipset ini (butuh Android 9/API 28+ untuk fallback ini — di bawah itu,
  atau kalau kedua jalur sama-sama gagal, kartu tetap tidak muncul, ini
  batasan hardware, bukan bug app). Fallback ini BELUM diuji di device fisik
  manapun (jarang ke-trigger karena mayoritas chipset punya Equalizer asli
  yang berfungsi) — kalau kartu tetap tidak muncul di chipset yang memang
  tidak punya Equalizer asli, laporkan model HP + versi Android supaya bisa
  ditelusuri.

**Banner "Update tersedia" tidak pernah muncul walau sudah ada Release baru**
- Buka Pengaturan (ikon ⚙️ di layar utama) → "Cek Update Sekarang" buat trigger
  cek manual — beda dari cek otomatis, hasilnya SELALU ditampilkan (sudah
  terbaru / ketemu update / gagal, bukan diam-diam) jadi langsung ketahuan
  apakah masalahnya jaringan atau memang belum ada Release baru.
- Cek app punya izin "Install unknown apps" untuk Boomly (Settings > Apps > Boomly
  > Install unknown apps) — kalau ditolak saat instalasi, tap lagi tombol "Pasang
  Sekarang" di banner setelah izin diaktifkan (APK yang sudah terunduh dipakai
  ulang, tidak diunduh dobel).
- Cek koneksi internet perangkat — pengecekan update butuh akses ke
  `api.github.com`, cek otomatis gagal diam-diam (tanpa pesan error) kalau
  offline (pakai cek manual di atas buat lihat pesan errornya).
- Pengecekan hanya menganggap ada update kalau nomor run CI Release terbaru lebih
  besar dari versi yang sedang jalan — build APK yang di-*sideload* manual (bukan
  dari CI) bisa saja tidak terdeteksi sesuai urutan ini.

**Build gagal di GitHub Actions**
- Cek apakah 4 secrets keystore (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) sudah diset kalau butuh APK release yang signed — kalau belum diset, job `release` di-skip otomatis (bukan gagal), tapi job `build` (debug) tetap harus sukses. Cek log job `build` dulu untuk error compile murni.
