# 🗺️ ROADMAP.md — panduan menuju 100% "sempurna/tamat"

Dibuat Batch 50 (v1.87.0). Isi file ini disintesis dari seluruh backlog yang
sudah tercatat tersebar di `PROJECT_STATE.md` (bagian "TODO", "Status saat
ini", tiap catatan "Belum divalidasi runtime") sejak Batch 1 — bukan daftar
baru, cuma dirapikan jadi 1 checklist actionable dengan definisi "selesai"
yang jelas per item.

**Cara pakai**: kalau user tanya "lanjut yang mana?" atau "berapa persen lagi
menuju selesai?", baca file ini duluan sebelum `PROJECT_STATE.md` bagian TODO
(file itu sekarang jadi ringkasan historis, roadmap ini yang jadi acuan aktif
prioritas ke depan). Update checklist di sini tiap kali 1 item pindah status —
jangan biarkan 2 sumber kebenaran soal "apa yang belum kelar" saling beda.

---

## Definisi "100% / Tamat"

Aplikasi dianggap tamat kalau **4 kondisi** di bawah terpenuhi bersamaan —
bukan cuma "fitur lengkap", tapi juga "sudah TERBUKTI benar", karena
mayoritas riwayat project ini dikerjakan **tanpa compiler** (sandbox Claude
gak punya Gradle/Android SDK):

1. **Fungsional** — semua fitur di README ada & bekerja sesuai deskripsi.
2. **Runtime-verified** — SEMUA perubahan sejak Batch 1 sudah dikonfirmasi
   jalan beneran di device (bukan cuma "statis only, brace/paren balance
   OK"). Ini kondisi PALING BELUM terpenuhi — lihat Fase 1 di bawah.
3. **CI hijau stabil** — build+release APK jalan tanpa campur tangan manual
   berkali-turut, bukan cuma "pernah hijau sekali".
4. **Tidak ada TODO Medium/High tersisa** — item Low boleh permanen pending
   kalau memang sengaja didepri­oritaskan user (dicatat, bukan dihapus).

---

## Fase 0 — Audio Engine Robustness (audit eksternal Batch 57, PRIORITAS BARU TERTINGGI)

Ditambahkan Batch 57 (v1.92.0) setelah user upload audit eksternal yang
menegaskan gap terbesar project ini BUKAN di UI (~90-95%, sudah matang),
tapi di **audio-engine robustness (~60-70%)** — arsitektur `AudioEffect(...,
0)` legacy, tidak ada verifikasi effect benar-benar aktif, tidak ada handling
kehilangan kontrol, tidak ada output-route awareness. Urutan di bawah persis
mengikuti "Fokus Next Step" dokumen audit (10 langkah, paling bernilai
duluan) — **kerjakan SATU per satu, jangan sekaligus** (instruksi eksplisit
user). Detail gap lengkap: lihat file audit asli yang di-upload user /
`CHANGELOG.md` v1.92.0.

- [x] **1. Actual effect-state verification + non-silent errors** (Batch 57
      v1.92.0 + Batch 58 v1.93.0 + Batch 59 v1.94.0, SELESAI) — `EffectState`
      enum + listener di Service (Batch 57), di-poll `BoosterViewModel` &
      disurface ke `helpText` Bass/Virtualizer/Loudness (Batch 58) DAN
      subtitle `EqualizerSection` (Batch 59). Seluruh 4 effect sudah
      tersurface Service→ViewModel→UI, tidak ada sisa.
- [~] **2. Capability detection + fallback** (Batch 60 v1.95.0, SEBAGIAN —
      lihat catatan) — dicek ulang lewat dokumentasi resmi Android SDK
      SEBELUM nulis kode: range Bass/Virtualizer `[0, 1000]` (per mille)
      TERNYATA kontrak API platform TETAP (sama di semua device), BUKAN
      device-specific range yang perlu di-query kayak `Equalizer.
      bandLevelRange` — jadi bukan "hard-coded assumption yang salah" seperti
      dugaan awal audit. Normalisasi capability yang beneran relevan buat 2
      effect ini: `strengthSupported` (sudah ada sejak sebelum Batch 57) +
      *rounding* (device boleh membulatkan strength ke nilai terdekat yang
      didukung tanpa lapor exception) — bagian rounding ini yang SEBELUMNYA
      gak pernah dibaca balik, sekarang ada `getBassRoundedStrength()`/
      `getVirtualizerRoundedStrength()` + `Log.w` diagnostik di
      `AudioEnhancerService.kt` (Service-layer only, belum disurface ke
      ViewModel/UI — pola sama seperti Batch 57→58). LoudnessEnhancer TIDAK
      disentuh: dicek juga, effect ini TIDAK punya API query range sama
      sekali (beda dari 3 effect lain), jadi "capability detection"-nya
      secara teknis tidak bisa diimplementasikan dari sisi app — jalur
      exception-nya sendiri (`IllegalArgumentException`) sudah tertangkap
      generic catch sejak Batch 57 (gap-closed lewat jalur lain, bukan lewat
      capability query). **Belum ada fallback engine** kalau effect null
      (bagian "fallback" dari item ini) — itu overlap besar dengan item #6
      (rebuild ke `DynamicsProcessing`), SENGAJA tidak diinisiasi di sini,
      effort/risiko besar & butuh device-testing intensif di luar kapasitas
      sandbox. Detail lengkap: `CHANGELOG.md` v1.95.0.
- [~] **3. Output routing awareness** (Batch 83, SEBAGIAN — lihat catatan) —
      `AudioEnhancerService.kt` sekarang register `AudioDeviceCallback` sistem
      (API 23+, tersedia penuh di minSdk 31 project ini) buat DETEKSI kapan
      sink output berpindah (speaker↔Bluetooth klasik/LE, wired headset,
      wired headphones, USB DAC/headset/accessory, HDMI, dock) — SEBELUMNYA
      nol handling sama sekali, gap ini yang paling mendasar sekarang
      tertutup. Tiap perubahan dicatat `Log.i` + field baru
      `lastOutputRouteDescription` (Service-layer, belum disurface
      ViewModel/UI — kandidat kuat item #9). Aksi "re-attach" yang diambil
      SENGAJA ringan: nudge `enableEffects()` (re-assert `enabled=true`,
      idempotent) begitu route berubah SELAGI `isRunning=true` — BUKAN
      recreate object penuh via `retryControlAcquisition()`, alasan SAMA
      PERSIS kenapa fungsi itu juga belum ada pemanggil otomatis (churn
      CPU/baterai sia-sia kalau route sering ganti, mis. TWS reconnect
      berkali-kali). Kalau nudge ringan ini TIDAK cukup dan effect beneran
      `CONTROL_LOST`, jalur `ControlRecoveryBanner` (Batch 61/62) yang SUDAH
      ADA tetap menangkap lewat mekanisme normal. **Kenapa masih SEBAGIAN
      bukan SELESAI**: (a) belum ada recreate/full re-attach otomatis untuk
      kasus effect beneran lepas total dari route baru (deliberately
      deferred, overlap dengan #6), (b) belum divalidasi APAKAH
      `AudioDeviceCallback` benar-benar fire konsisten lintas OEM (variasi
      HAL, sama kelas risiko capability lain di file ini), (c) belum ada UI
      apa pun yang tampilkan `lastOutputRouteDescription` ke user (item #9).
      Detail lengkap: `CHANGELOG.md` Batch 83.
- [x] **4. Control ownership/lifecycle lanjutan** (Batch 61 v1.96.0 + Batch 62
      v1.97.0, SELESAI dari sisi app — sisa risiko cuma runtime-validation,
      bukan gap desain) — `AudioEnhancerService.kt` (Batch 61): `attachEffects()`
      dipecah jadi 4 fungsi per-effect + `retryControlAcquisition()` (release+
      recreate per-effect yang `CONTROL_LOST`/`FAILED`). Batch 62: fungsi itu
      DISURFACE penuh ke UI — `BoosterViewModel.retryControlAcquisition()`
      (wrapper tipis, sinkron, sama-proses lewat `LocalBinder`) dipanggil dari
      `ControlRecoveryBanner` (`BoosterScreen.kt`, komponen baru, pola identik
      `ServiceStatusBadge`/`CrashBanner`) — banner ini HANYA muncul kalau ada
      effect `CONTROL_LOST`/`FAILED`, hilang otomatis lewat polling `EffectState`
      1 detik yang sudah ada (Batch 58), TIDAK ada mekanisme polling baru.
      **TIDAK ADA jaminan berhasil** tetap berlaku (arbitration priority Android
      di luar kendali app) — snackbar sengaja bilang "dicoba", bukan "berhasil".
      Detail lengkap: `CHANGELOG.md` v1.97.0.
- [ ] **5. Gain staging + dynamics pipeline** — belum ada master limiter/
      compressor terkontrol; pipeline konseptual saat ini `BassBoost →
      Virtualizer → EQ → LoudnessEnhancer` tanpa tahapan gain staging yang
      jelas (`Input → Pre-Gain → EQ → Dynamics → Loudness → Output`).
- [ ] **6. Rebuild arsitektur session-0 ke API modern** — gap PALING besar &
      PALING berisiko dari semua (audit Gap #1/#2). Legacy `AudioEffect(...,
      0)` diasumsikan global session, deprecated & tidak portable lintas
      device/ROM/HAL. Perlu strategi modern (`DynamicsProcessing`/post-
      processing) sebagai fallback. **BUTUH testing device intensif lintas
      OEM** — sandbox Claude TIDAK bisa validasi ini sendirian, effort besar,
      JANGAN diinisiasi tanpa user paham & setuju trade-off/risikonya.
- [x] **7. Preset lengkap termasuk EQ** (Batch 63 v1.98.0, SELESAI) —
      `PrefsHelper.CustomPreset` dapat field baru `eqBands: List<Int>` (default
      `emptyList()`, backward-compat penuh dengan preset lama). Simpan preset
      sekarang snapshot EQ SAAT ITU (dibaca balik dari `PrefsHelper.
      getEqualizerBandLevel()` per-band, sumber kebenaran yang sudah ada sejak
      lama — 0 plumbing state Compose baru). Terapkan preset custom yang PUNYA
      `eqBands` sekarang ikut menerapkan EQ; preset lama TANPA `eqBands`
      (disimpan sebelum batch ini) sengaja TIDAK menyentuh EQ manual user
      (perilaku asli dipertahankan, tidak ada data EQ buat preset itu). 2 test
      unit baru (`PrefsHelperTest.kt`): round-trip JSON `eqBands`, dan preset
      JSON lama tanpa field ini tetap load tanpa crash. Detail lengkap:
      `CHANGELOG.md` v1.98.0.
- [ ] **8. Automated audio-engine test** — `PrefsHelperTest`/
      `FormatFreqLabelTest` yang ada belum menyentuh audio engine sama sekali
      (effect creation failure, control loss, state reconciliation, dst).
- [ ] **9. UI/error-state refinement** — bedakan Unsupported vs Temporarily
      unavailable vs Control lost vs Effect failed vs Output changed secara
      eksplisit di UI (bukan cuma 1 helpText generik "tidak didukung").
- [ ] Item ke-10 audit ("polish visual minor") SUDAH tercakup Fase 3 di
      bawah, tidak diduplikasi di sini.

## Fase 1 — Runtime Validation Debt (PRIORITAS TERTINGGI sebelum Fase 0 ditambahkan)

Ini backlog paling besar & paling berisiko: banyak perubahan besar (terutama
tema/UI Batch 31-49 dan arsitektur Batch 16-18) dikirim dengan status
**"belum divalidasi runtime"** — statis only. Selama daftar ini belum
kosong, app TIDAK BISA disebut 100% walau fitur-nya kelihatan lengkap.

- [ ] **4 varian tema** (Midnight Glass, Aurora Glass, Neumorphism, Studio
      Equalizer) — install APK asli, cek visual tiap varian bergantian di
      Settings. Kandidat bug: glow/sheen gak muncul, radius salah, kontras
      teks kurang di salah satu varian.
- [ ] **`BoosterViewModel` pasca-cabut Hilt (Batch 49)** — pastikan gak ada
      crash `Cannot create an instance of BoosterViewModel` di Logcat.
- [ ] **`configuration-cache` (Batch 50)** — pastikan run CI berikutnya
      tetap hijau, cek tab Actions gak ada warning "configuration cache
      problems".
- [ ] **Slider custom, SkeuSwitch, skeuGlow** (Batch 22, 32) — render normal
      di device asli (bukan cuma preview HTML).
- [ ] **Race condition fix `@Volatile isRunning`** (Batch 45) — coba
      trigger skenario watchdog restart, cek widget/QS Tile sinkron balik.
- [ ] **Crash Logger MediaStore** (Batch 27/29) — trigger 1 crash sengaja,
      cek file muncul di `Documents/AudioEnhancerPro/logs/` via file
      manager, cek retensi FIFO maks 50 file jalan.
- [ ] **Bind service via Application Context** (Batch 17) — pastikan gak
      ada context-leak/crash aneh setelah rotasi atau app di-background lama.
- [ ] **`ControlRecoveryBanner` + `retryControlAcquisition()` (Batch 62)** —
      belum ada cara sengaja memicu `CONTROL_LOST`/`FAILED` di device asli buat
      lihat banner ini beneran muncul/hilang. Kalau kejadian natural (app audio
      lain rebut kontrol session 0), cek: banner muncul dalam ≤1 detik, tombol
      "Coba Ambil Alih Lagi" gak crash, snackbar muncul, banner hilang sendiri
      begitu `EffectState` balik `ENABLED`/`AVAILABLE`.
- [ ] **Preset custom simpan EQ (Batch 63)** — install APK asli: atur EQ manual
      beda-beda per band, simpan preset baru, ubah lagi EQ-nya, lalu terapkan
      preset yang barusan disimpan → pastikan slider EQ balik PERSIS ke nilai
      saat disimpan (bukan cuma bass/virtualizer/loudness). Juga cek preset
      LAMA (kalau ada, disimpan sebelum update ini) masih bisa diterapkan tanpa
      crash & tidak mengubah EQ manual yang sedang aktif.

**Cara kerja disarankan**: JANGAN coba validasi semua sekaligus. Tiap kali
user install APK baru, cocokkan ke daftar ini — centang yang sudah dicoba +
confirmed OK, catat detail kalau ada yang gagal (jadi bug baru, bukan
"belum divalidasi" lagi).

## Fase 2 — Build & CI Maturity

- [x] ~~Gabung job build+release jadi 1~~ (Batch 40)
- [x] ~~Cache Gradle dependency + wrapper dist~~ (Batch 40)
- [x] ~~Cabut Hilt/kapt~~ (Batch 49, CI confirmed hijau)
- [x] ~~`org.gradle.configuration-cache=true`~~ (Batch 50)
- [ ] **Commit `gradlew`/`gradle-wrapper.jar` permanen ke repo** — hilangkan
      overhead bootstrap wrapper sepenuhnya (bukan cuma di-cache). **TIDAK
      BISA dikerjakan dari sandbox Claude** (butuh binary Gradle + network,
      keduanya gak ada di sandbox ini). Perlu dikerjakan user manual dari
      mesin dev mana pun yang punya Gradle terinstal:
      `gradle wrapper --gradle-version 8.7`, lalu commit 4 file hasilnya.
- [ ] **Evaluasi upgrade AGP 8.5.2 / Kotlin 1.9.24 / compose-bom 2024.06.00**
      ke versi lebih baru — ditunda sengaja selama ini (versi lama tapi
      stabil & sudah lolos banyak insiden kompatibilitas, lihat "Batasan
      sandbox" di `PROJECT_STATE.md`). Kandidat kalau user eksplisit minta,
      BUKAN inisiatif proaktif Claude (risiko regresi tanpa compiler).

## Fase 3 — Audit Polish (Medium/Low, pending sejak Batch 16)

Item ini eksplisit di-daftar di audit awal (Batch 16) tapi belum pernah
disentuh karena project masuk fase fitur lalu maintenance mode duluan:

- [ ] Recomposition review / reusable component review menyeluruh
- [ ] Hierarki visual (heading/body/caption size & weight konsisten)
- [ ] White space / spacing audit lintas layar
- [ ] Micro-animation tambahan (selain yang sudah ada: scale power button,
      animateColorAsState tema)
- [ ] Loading / success / error state feedback — **sebagian selesai Batch 51
      (v1.88.0)**: Snackbar sukses simpan/hapus preset + hapus log crash.
      Connection loading/error (bind service) SUDAH ADA lebih dulu (Batch 17,
      `BoosterViewModel.ConnectionState`). Sisa gap: preset custom yang
      GAGAL tersimpan (mis. storage penuh — kasus ekstrem, belum ditangani
      eksplisit), operasi lain yang masih silent (ganti tema, toggle Material
      You — dampak kecil, cuma flag lokal instan, prioritas rendah).
- [ ] Empty state UI (selain `presets_empty_hint` yang sudah ada, Batch 30)
- [ ] Penjelasan fitur lanjutan buat user awam (tooltip/info icon di
      Bass/Virtualizer/Loudness — Low priority, opsional)

## Fase 4 — Kompatibilitas Device (sengaja didepri­oritaskan user, TETAP dicatat)

**Jangan dikerjakan proaktif** — daftar ini murni biar gak hilang dari
radar, bukan perintah untuk segera dikerjakan:

- [ ] Konfirmasi tombol Autostart (`OemAutostartHelper`) benar-benar buka
      halaman yang tepat di **Infinix Note 50 Pro 4G & Note 40 Pro 4G**
      (XOS). Kandidat Transsion (Batch 35) paling gak terverifikasi dari
      semua OEM yang didukung.
- [ ] Rotasi layar / config change (app saat ini portrait-lock secara de
      facto, belum ditest handling config change eksplisit)
- [ ] Font scaling besar (Aksesibilitas → Ukuran font)
- [ ] Landscape phone
- [ ] RTL (Right-to-Left) layout
- [ ] Kontras tombol biru (item lama dari audit awal, belum pernah detail
      lokasinya dicatat ulang — perlu screenshot user kalau mau dikerjakan)

## Fase 5 — Feature Backlog (di luar "maintenance mode", opsional)

Item yang eksplisit DITUNDA saat masuk maintenance mode (setelah Batch 44).
**Bukan ditolak selamanya** — cuma gak diinisiasi sendiri oleh Claude tanpa
user minta eksplisit:

- [ ] Custom EQ curve editor (drag-point, bukan cuma slider per-band)
- [ ] Export/import preset (share ke device lain / backup)
- [ ] In-app update checker (cek rilis GitHub terbaru dari dalam app)

## Fase 6 — Dokumentasi & Housekeeping

- [ ] Sinkronkan paragraf "Arah desain UI aktif" di `PROJECT_STATE.md` —
      catatan sendiri di file itu bilang belum di-update lengkap ke "4
      varian" sejak Batch 43 (Studio Equalizer).
- [ ] `docs/preview/current.html` — pastikan tetap ground-truth utk 2 varian
      glass (Midnight/Aurora); Neumorphism & Studio Eq TIDAK punya mockup
      HTML terpisah (disengaja, dicatat di Batch 38/43/46) — kalau mau
      lengkap 100%, ini bisa ditambahkan (Low priority, kosmetik dev-tool).
- [ ] `PrefsHelperTest.kt` — cek apakah test coverage masih relevan setelah
      berbagai perubahan state sejak Batch 17 (ekstraksi ke ViewModel).

---

## Progress ringkas (isi ulang tiap update besar)

| Fase | Status |
|---|---|
| 0. Audio Engine Robustness (audit eksternal) | 🟡 3/9 selesai (dari sisi app) + 2/9 sebagian (#2, #3) — Batch 57-63, 83 |
| 1. Runtime Validation Debt | 🔴 Belum mulai — backlog terbesar |
| 2. Build & CI Maturity | 🟡 4/6 selesai, 2 sisa (di luar kendali sandbox / opsional) |
| 3. Audit Polish (Medium/Low) | 🟡 1/7 item mulai (Batch 51, sebagian) |
| 4. Kompatibilitas Device | ⚪ Sengaja ditunda user |
| 5. Feature Backlog | ⚪ Sengaja ditunda (maintenance mode) |
| 6. Dokumentasi & Housekeeping | 🟡 Sebagian kecil |

**Estimasi kasar menuju 100%** (bobot ke fungsi real, bukan jumlah baris
kode): app **fungsional ~95%** (fitur lengkap, dipakai harian tanpa
komplain besar), tapi **"terbukti benar" jauh lebih rendah** karena Fase 1
kosong — mayoritas kode "kelihatan benar secara statis" belum
"terkonfirmasi benar di device". Prioritas realistis paling tinggi buat
"sempurna": mulai centang Fase 1 satu-satu tiap kali ada APK baru di-install,
bukan nambah fitur baru dulu.
