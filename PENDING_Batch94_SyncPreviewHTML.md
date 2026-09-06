# PENDING — Sinkronkan docs/preview/current.html ke struktur tab (Batch 94)

Untuk Claude (sesi berikutnya) + user. Diekstrak terpisah dari Batch 94
(bukan bagian VIP inti) supaya micro-batch Batch 94 tetap 1 file kode
(`BoosterScreen.kt`) — sesuai aturan Micro-Batch (maks 3 file kode/task).

## Konteks

Batch 94 merombak `BoosterScreen.kt`: layar utama yang SEBELUMNYA 1 scroll
vertikal raksasa (Preset → Kontrol → Equalizer Manual → 4 toggle tema →
kartu baterai/autostart → tombol bantuan) SEKARANG dipecah jadi 3 tab
horizontal-scrollable via `ScrollableTabRow` + `HorizontalPager`: **Kontrol**
/ **Tampilan** / **Bantuan**. Detail lengkap: `PROJECT_STATE.md` entry
"Batch 94" & `CHANGELOG.md` entry "Batch 94".

`docs/preview/current.html` (mockup statis HTML, dipakai buat preview cepat
tanpa build APK) **BELUM** disentuh sama sekali di Batch 94 — masih
representasi 1-scroll-panjang yang lama.

## Yang perlu dikerjakan

1. **Cek dulu seberapa lengkap mockup saat ini** — per pengecekan Batch 94,
   `current.html` TERNYATA sudah lama tidak 1:1 lengkap dengan
   `BoosterScreen.kt`: cuma render sampai "Preset Cepat" → kartu "Kontrol"
   (Bass/Virtualizer/Loudness) → "Equalizer Manual" (collapsed) → 1 toggle
   "Warna ikut wallpaper" (dynamic color) lalu berhenti — kartu Aurora
   Glass/Skeuomorphism/Studio Equalizer/baterai/tombol bantuan **TIDAK ADA**
   di mockup ini sama sekali (footer-note-nya sendiri sudah bilang "3
   varian lain belum ada mockup terpisah di sini"). Jadi sync bukan cuma
   "tambah tab-bar", tapi keputusan lebih dulu: apakah mockup ini mau
   dilengkapi dulu isinya (biar representatif ke-3 tab) atau tab-bar cukup
   ditambahkan ke bagian yang SUDAH ada (Kontrol saja) dan sisanya tetap
   dianggap "belum di-mock" seperti sebelumnya. **Tanya user kalau ragu,
   jangan asumsi sepihak** (pola project ini: STOP kalau info kurang).
2. Kalau lanjut: tambah markup tab-bar (3 tab: Kontrol/Tampilan/Bantuan,
   styling ikutin `--primary` untuk selected, `--muted` untuk unselected —
   token CSS yang sudah ada, cek `:root{}` di atas file, JANGAN bikin token
   warna baru) + tab-panel per tab + sedikit JS vanilla buat toggle
   `display`/class `active` pas tab diklik (mockup ini statis HTML/CSS,
   TIDAK ada framework — cek dulu apakah sudah ada `<script>` block, kalau
   belum ada berarti ini bakal jadi elemen JS pertama di file, worth
   dicatat di footer-note).
3. Update `footer-note` di bawah (versi + deskripsi ringkas) — pola project
   ini SELALU update footer-note tiap kali struktur mockup berubah
   (lihat histori "v3.1 (Batch 88)" dst).
4. Cek statis akhir: brace CSS/JS balance (kalau nambah JS), HTML
   well-formed (bisa validasi kasar pakai parser HTML Python kalau perlu).

## Batasan

- Ini FILE MOCKUP PREVIEW, bukan bagian app yang di-build/di-ship — nol
  dampak ke APK. Prioritas rendah dibanding kode Kotlin, tapi tetap
  ditagih sebagai utang biar `docs/preview/current.html` gak makin jauh
  drift dari Kotlin (sudah drift duluan sebelum Batch 94, lihat poin 1).
- Micro-Batch: kerjakan sebagai 1 file kode tersendiri (file HTML ini
  dihitung sebagai 1 "file kode" kalau dikerjakan berbarengan dengan file
  Kotlin lain di batch yang sama — JANGAN gabung 3 file kode Kotlin + file
  ini di 1 batch yang sama, kena limit).
