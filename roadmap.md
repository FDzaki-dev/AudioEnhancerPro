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

## Fase 1 — Runtime Validation Debt (PRIORITAS TERTINGGI)

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
