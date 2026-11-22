package com.audioenhancer.booster

import kotlin.math.abs

/**
 * Batch 336 (audit baterai B334 -> "Batch penghemat"): keputusan MURNI (tanpa Android) kapan kelima efek mixer boleh dibiarkan mati.
 * Netral = efek tak mengubah suara: Bass/Virtualizer 0, Loudness 0 mB, Compressor 0, Clarity 0 (Vocal Focus tak berefek tanpa Compressor)
 * dan SEMUA pita EQ 0 mB. Semua-atau-tidak-sama-sekali: satu pengaturan tak netral = kelima efek menyala (tak ada nyala-mati per efek,
 * supaya tak ada klik berulang dan logika Effect Guard/serah-terima tetap melihat himpunan efek yang sama).
 */
internal object IdleBypassPolicy {
    /** Batas atas jumlah pita EQ yang dibaca dari prefs (Equalizer asli 5-10 pita; cadangan PreEq lebih sedikit). Pita yang tak pernah disimpan bernilai 0. */
    const val MAX_EQ_BANDS = 16

    /** Toleransi pembulatan Loudness (mB) untuk dianggap 0. */
    private const val LOUDNESS_EPSILON_MB = 0.5f

    fun isAllNeutral(bass: Int, virtualizer: Int, loudnessMb: Float, compressor: Int, clarity: Int, eqLevelsMb: List<Int>): Boolean =
        bass == 0 && virtualizer == 0 && abs(loudnessMb) < LOUDNESS_EPSILON_MB &&
            compressor == 0 && clarity == 0 && eqLevelsMb.all { it == 0 }

    /**
     * Batch 339 (laporan user v373 = B338: "Masih klik"; log: Loudness 350 -> 0 -> 600 diulang 4x = 4 kali mati-nyala kelima efek dalam 11 dtk):
     * menyalakan efek di tengah audio adalah sumber klik, jadi peralihan sengaja dibuat SEDIKIT mungkin. Bypass hanya boleh dimulai saat UI
     * TIDAK terlihat (tujuan hemat baterai: musik berjam-jam dengan layar mati). Selama UI terlihat status bypass DITAHAN apa adanya:
     * efek yang sudah menyala tidak dimatikan saat slider kembali ke 0 (geser bolak-balik tak memicu klik berulang), dan efek yang masih
     * bypass tidak dinyalakan hanya karena UI dibuka (tanpa klik saat sekadar melihat). Satu-satunya penyalaan = pengaturan tak netral.
     * [allNeutral] = hasil [isAllNeutral]; saklar Pengaturan dicek pemanggil (OFF = tak pernah bypass).
     */
    fun shouldBypass(allNeutral: Boolean, uiVisible: Boolean, currentlyBypassed: Boolean): Boolean = when {
        !allNeutral -> false
        uiVisible -> currentlyBypassed
        else -> true
    }
}
