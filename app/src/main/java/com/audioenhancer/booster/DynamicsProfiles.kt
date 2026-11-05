package com.audioenhancer.booster

/**
 * Batch 264 (log aep_debug_20261101_190205, Samsung SM-S711B Android 14): `DynamicsProcessing` DITOLAK device saat konstruktor menulis
 * arsitektur mesin (`setEngineArchitecture` -> "AudioEffect: bad parameter value"), sehingga limiter + kompresor TIDAK ADA di device itu
 * (rantai efek hanya Reverb/Bass/EQ/Loudness/Virtualizer) dan percobaan ulang tiap serah-terima efek mengulang galat yang sama (109 kali / 25 menit).
 * Tangga profil: dicoba berurutan dari yang terlengkap (= konfigurasi Batch 210, satu-satunya yang dipakai sebelum Batch 264) sampai
 * limiter saja (konfigurasi Batch 84). Profil dipindah turun HANYA bila device menolak dengan [IllegalArgumentException] (penolakan
 * deterministik); galat lain (sumber daya, sementara) tidak menurunkan profil. Tidak ada kemampuan baru: profil lain = SUBSET fitur yang sama.
 */
internal data class DynamicsProfile(
    /** true = `VARIANT_FAVOR_FREQUENCY_RESOLUTION`, false = `VARIANT_FAVOR_TIME_RESOLUTION` (bawaan sejak Batch 84). */
    val favorFrequency: Boolean,
    /** MBC 3 band (kompresor, `CompressorCurve.BAND_COUNT`). */
    val mbc: Boolean,
    /** Post-EQ presence vokal (`CompressorCurve.PRESENCE_BAND_COUNT`). */
    val postEq: Boolean
)

/**
 * Batch 276: seberapa jauh slider Compressor / Vocal Focus berefek pada profil `DynamicsProcessing` yang AKTIF (dibaca UI untuk menandai
 * slider yang tak berefek di device yang menolak profil lengkap, lihat B264). `FULL` = MBC + presence (perilaku normal, TANPA penanda);
 * `NO_PRESENCE` = Compressor jalan, tetapi Vocal Focus kehilangan penguat kejernihan vokal (post-EQ) sehingga hanya penekanan bass/treble
 * (MBC) yang bekerja; `LIMITER_ONLY` = tanpa MBC: Compressor dan Vocal Focus sama sekali tidak berefek.
 */
enum class CompressorReach { FULL, NO_PRESENCE, LIMITER_ONLY }

internal object DynamicsProfiles {
    /** Urutan percobaan; indeks 0 = konfigurasi penuh Batch 210, indeks terakhir = limiter saja. */
    val ORDER: List<DynamicsProfile> = listOf(
        DynamicsProfile(favorFrequency = false, mbc = true, postEq = true),
        DynamicsProfile(favorFrequency = false, mbc = true, postEq = false),
        DynamicsProfile(favorFrequency = true, mbc = true, postEq = true),
        DynamicsProfile(favorFrequency = true, mbc = true, postEq = false),
        DynamicsProfile(favorFrequency = false, mbc = false, postEq = false)
    )

    val LAST_INDEX: Int = ORDER.size - 1

    /** Penolakan deterministik = argumen tak valid untuk mesin efek device (mengulang profil yang sama pasti gagal lagi). */
    fun isDeterministicRejection(e: Throwable): Boolean = e is IllegalArgumentException

    /** Batch 276: jangkauan slider Compressor/Vocal Focus untuk profil di [profileIndex] (di luar rentang dijepit ke ujung tangga). */
    fun reachOf(profileIndex: Int): CompressorReach {
        val profile = ORDER[profileIndex.coerceIn(0, LAST_INDEX)]
        return when {
            !profile.mbc -> CompressorReach.LIMITER_ONLY
            !profile.postEq -> CompressorReach.NO_PRESENCE
            else -> CompressorReach.FULL
        }
    }
}
