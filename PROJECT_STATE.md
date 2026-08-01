# 🧠 PROJECT_STATE.md — baca file ini PALING PERTAMA

File ini didesain buat dibaca AI (Claude) di awal sesi baru, bukan cuma manusia.
Isinya padat & langsung actionable — bukan riwayat lengkap (itu ada di
CHANGELOG.md). Kalau kamu Claude dan baru diminta lanjut project ini:
1. Baca file ini full.
2. Baca 2-3 entry TERATAS CHANGELOG.md aja (bukan semua) buat detail teknis terbaru.
3. Baru mulai kerja. Jangan ulang pertanyaan yang jawabannya udah ada di sini.

---

## Status saat ini
- **Versi**: v1.32
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

## Batasan sandbox Claude (PENTING — biar gak ngulang insiden yang sama)
- **TIDAK ADA** kotlinc/gradle/Android SDK di sandbox Claude manapun (dicek
  eksplisit, network disabled). Artinya: Claude TIDAK BISA compile-check
  Kotlin sebelum ngirim zip. Verifikasi cuma bisa manual: baca ulang tiap
  nama class/icon yang dipakai, cek balance brace/paren via python.
- **Insiden nyata yang pernah kejadian**: sempat nulis
  `Icons.AutoMirrored.Filled.VolumeUp` (TIDAK EXIST di library icon) dan baru
  ketauan sebelum sempat ke-kirim — tapi ini nunjukkin resikonya nyata.
  Kalau ragu 1 nama icon/class ada atau nggak, mending pakai yang udah
  KONFIRMASI kepake di file lain, atau icon paling umum/basic.
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
LATEST_ZIP=$(ls -t ~/storage/downloads/AudioEnhancerPro*.zip | head -1) && echo "Pakai ZIP: $LATEST_ZIP" && mkdir -p ~/projects/AudioEnhancerPro && cd ~/projects/AudioEnhancerPro && ( [ -d .git ] || git init ) && find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} + && cd ~/projects && unzip -o "$LATEST_ZIP" -d ~/projects/ && cd ~/projects/AudioEnhancerPro && git add -A && git commit -m "[ringkasan perubahan]" && git push
```

## Struktur proyek singkat
- `MainActivity.kt` — semua UI Compose (BoosterScreen, FeatureControl, GlassCard, dst) + lifecycle Activity + bind ke Service.
- `AudioEnhancerService.kt` — foreground service, attach BassBoost/Virtualizer/Equalizer/LoudnessEnhancer ke session 0.
- `Theme.kt` — palet warna, typography, shape. Accent color per-fitur ada di sini (`BassAccent`, `VirtualizerAccent`, dst + varian "2" buat gradient).
- `PrefsHelper.kt` — SharedPreferences wrapper, semua persistence lewat sini.
- `OnboardingScreen.kt` — 6 halaman onboarding.
- `docs/preview/current.html` — mockup HTML standalone, HARUS di-update kalau ada perubahan arah visual besar.

## TODO / belum dikerjain (kalau user nanya "lanjut yang mana")
- Rotasi layar/config change, font scaling besar, landscape phone, RTL,
  kontras tombol biru — user bilang eksplisit TIDAK urgent, jangan dikerjain
  duluan tanpa diminta.
- Icon launcher masih adaptive vector sederhana (v1.14) — belum di-refresh
  ikut palet gradient glassmorphism baru. Worth ditanyakan kalau relevan.

## Cara update file ini
Tiap sesi yang bikin keputusan arsitektur/desain baru (bukan sekadar bugfix
kecil), WAJIB update bagian "Status saat ini", "Riwayat pivot", dan/atau
"Keputusan sadar" di atas — supaya sesi berikutnya gak mulai dari nol lagi.
