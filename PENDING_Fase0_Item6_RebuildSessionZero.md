# PENDING — roadmap.md Fase 0 #6: Rebuild arsitektur session-0 ke API modern

Untuk Claude (sesi berikutnya) + user. File terisolasi (bukan bagian VIP inti)
karena item #6 terlalu besar buat 1 micro-batch (aturan Micro-Batch: maks 3
file kode/batch) — dipecah bertahap, TIAP FASE dikerjakan SATU per satu,
menunggu arahan/konfirmasi user di titik yang relevan (sama pola seperti
antrian roadmap.md Fase 0 lainnya sejak Batch 82).

## Status

- **FASE 1 (Batch 87, SELESAI dari sisi kode, BELUM divalidasi runtime)**:
  `DynamicsProcessing` (Batch 84, sebelumnya cuma limiter) sekarang JUGA
  dipasangi PreEq stage 5-band — aktif SEBAGAI FALLBACK `Equalizer` HANYA
  kalau `Equalizer` legacy device ini `UNAVAILABLE` total. 1 file kode
  disentuh: `AudioEnhancerService.kt`. Detail teknis penuh: CHANGELOG.md
  entry "Batch 87".
- **FASE 2+**: BELUM dikerjakan. Kandidat di bawah, urutan BUKAN final.

## BATASAN FUNDAMENTAL (baca dulu sebelum lanjut Fase 2+, supaya ekspektasi tidak salah)

TIDAK ADA API publik Android yang memberi app kontrol atas urutan insert
effect di HAL chain audio session 0. Ini berlaku untuk `AudioEffect` legacy
(`BassBoost`/`Virtualizer`/`Equalizer`/`LoudnessEnhancer`) MAUPUN
`DynamicsProcessing` — effect apa pun yang di-attach ke `audioSession = 0`
cuma jadi 1 node independen di chain itu, urutan proses akhir ditentukan
sistem/HAL vendor, bukan app. Artinya pipeline eksplisit yang diminta audit
asli ("Input → Pre-Gain → EQ → Dynamics → Loudness → Output") SECARA
HARFIAH **tidak bisa dicapai 100%** lewat API publik non-root manapun.

Satu-satunya cara Android beri app kendali PENUH atas urutan pemrosesan
sinyal adalah `AudioPlaybackCaptureConfiguration` (API 29+): capture audio
mentah dari app lain (butuh `RECORD_AUDIO` + consent `MediaProjection`
BERULANG tiap sesi, bukan sekali izin), proses sinyal SENDIRI di app (DSP
custom — bass/virtualizer/EQ/loudness yang SEKARANG gratis dari native
effect engine Android harus ditulis ulang dari nol), lalu re-output lewat
`AudioTrack` baru. Ini BUKAN "rebuild", ini **arsitektur & produk yang beda
total** — effort besar sekali (bulanan, bukan batch-an), UX beda (popup izin
tiap start), risiko echo/latency/battery jauh lebih tinggi. **TIDAK
direkomendasikan diinisiasi tanpa user eksplisit minta & paham ini
pengganti total, bukan penyempurnaan session-0.**

Kesimpulan: Fase 1-3 di bawah adalah upaya PALING REALISTIS dalam batasan
platform yang nyata — **perkuat robustness & fallback effect session-0 yang
sudah ada**, BUKAN restrukturisasi urutan proses sinyal sungguhan.

## Kandidat Fase 2 (belum dikerjakan, tunggu arahan user pilih yang mana)

1. **Fallback serupa buat BassBoost/Virtualizer UNAVAILABLE** — pakai
   shelving-band gain dari `DynamicsProcessing` PreEq/PostEq sebagai
   approksimasi. **Catatan risiko**: karakter psychoacoustic BassBoost API
   asli (headroom-aware, dirancang device-specific) beda dari EQ shelving
   biasa — approksimasi ini bisa terdengar beda dari ekspektasi user
   "Bass Boost", perlu keputusan desain dulu (worth it atau tidak) sebelum
   ditulis kode apa pun.
2. **Validasi device fisik Fase 1** — catatan penting: MAYORITAS device
   punya `Equalizer` legacy yang jalan normal, jadi jalur fallback Fase 1
   ini SECARA ALAMI jarang ke-trigger di device nyata manapun yang user
   kemungkinan punya (Equalizer legacy sudah sangat matang & lama, jarang
   `UNAVAILABLE`). Kalau user mau benar-benar uji jalur ini: opsi (a) cari
   device/emulator API rendah atau chipset eksotis yang benar-benar tidak
   expose `Equalizer`, atau (b) tambah 1 flag debug-only buat force-fail
   `attachEqualizer()` sengaja (keputusan baru, belum diinisiasi — risiko
   lupa di-revert kalau tidak hati-hati).
3. **Tanya user eksplisit** apakah `AudioPlaybackCaptureConfiguration`
   (lihat "Batasan Fundamental" di atas) worth dieksplorasi sebagai proyek
   TERPISAH (bukan lanjutan #6) — TIDAK diinisiasi proaktif.

## Belum divalidasi runtime (Fase 1, Batch 87)

- Apakah `needsEqFallback` benar-benar ke-trigger cuma di device yang
  memang butuh (belum ada device uji nyata yang `Equalizer`-nya
  `UNAVAILABLE` — lihat kandidat Fase 2 #2 di atas).
- Apakah `DynamicsProcessing.EqBand` dengan `preEqBandCount=5` benar-benar
  construct sukses di device API 28+ nyata (variasi HAL, sama kelas risiko
  capability lain di file ini, belum pernah diuji device fisik).
- Apakah konversi mB→dB (`levelMb / 100f`) menghasilkan gain yang terdengar
  wajar dibanding `Equalizer` asli (rentang ±12 dB dipilih konservatif,
  BELUM diadu telinga langsung vs rentang asli device manapun).
