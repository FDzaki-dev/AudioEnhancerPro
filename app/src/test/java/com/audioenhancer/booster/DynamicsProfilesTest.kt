package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 264 — Pure-Kotlin test: menjaga tangga profil `DynamicsProcessing` (`DynamicsProfiles`). Latar: log Samsung SM-S711B menunjukkan
 * device menolak profil lengkap ("bad parameter value" di `setEngineArchitecture`), sehingga limiter + kompresor hilang dan galat yang sama
 * diulang tiap serah-terima efek. Yang dijaga: profil pertama = konfigurasi penuh Batch 210 (perilaku device lain TIDAK berubah), profil terakhir =
 * limiter saja, semua profil unik, dan hanya penolakan deterministik (IllegalArgumentException) yang menurunkan profil.
 */
class DynamicsProfilesTest {

    @Test
    fun `first profile is the full Batch 210 configuration`() {
        val p = DynamicsProfiles.ORDER.first()
        assertFalse(p.favorFrequency)
        assertTrue(p.mbc)
        assertTrue(p.postEq)
    }

    @Test
    fun `last profile is limiter only`() {
        val p = DynamicsProfiles.ORDER.last()
        assertFalse(p.mbc)
        assertFalse(p.postEq)
        assertEquals(DynamicsProfiles.ORDER.size - 1, DynamicsProfiles.LAST_INDEX)
    }

    @Test
    fun `profiles are unique and there is at least one fallback`() {
        assertTrue(DynamicsProfiles.ORDER.size >= 2)
        assertEquals(DynamicsProfiles.ORDER.size, DynamicsProfiles.ORDER.toSet().size)
    }

    @Test
    fun `later profiles never add compressor stages that earlier time-resolution profiles lack`() {
        // Tangga hanya MENGURANGI tahap (MBC / post-EQ) atau mengganti varian; tidak ada profil yang menambah tahap di luar profil penuh.
        for (p in DynamicsProfiles.ORDER) {
            assertTrue(!p.mbc || DynamicsProfiles.ORDER.first().mbc)
            assertTrue(!p.postEq || DynamicsProfiles.ORDER.first().postEq)
        }
    }

    @Test
    fun `normal device shows no marker and limiter only device marks both sliders`() {
        // Batch 276: profil pertama (device normal) TIDAK boleh memunculkan penanda; profil terakhir (limiter saja) menandai Compressor + Vocal Focus.
        assertEquals(CompressorReach.FULL, DynamicsProfiles.reachOf(0))
        assertEquals(CompressorReach.LIMITER_ONLY, DynamicsProfiles.reachOf(DynamicsProfiles.LAST_INDEX))
    }

    @Test
    fun `profile without post-EQ keeps the compressor but marks the missing presence`() {
        val index = DynamicsProfiles.ORDER.indexOfFirst { it.mbc && !it.postEq }
        assertTrue(index >= 0)
        assertEquals(CompressorReach.NO_PRESENCE, DynamicsProfiles.reachOf(index))
    }

    @Test
    fun `reach is limiter only exactly for profiles without the compressor stage`() {
        DynamicsProfiles.ORDER.forEachIndexed { index, p ->
            assertEquals(!p.mbc, DynamicsProfiles.reachOf(index) == CompressorReach.LIMITER_ONLY)
        }
    }

    @Test
    fun `out of range index is clamped to the ends of the ladder`() {
        assertEquals(DynamicsProfiles.reachOf(0), DynamicsProfiles.reachOf(-1))
        assertEquals(
            DynamicsProfiles.reachOf(DynamicsProfiles.LAST_INDEX),
            DynamicsProfiles.reachOf(DynamicsProfiles.LAST_INDEX + 1)
        )
    }

    @Test
    fun `only IllegalArgumentException counts as a deterministic rejection`() {
        assertTrue(DynamicsProfiles.isDeterministicRejection(IllegalArgumentException("AudioEffect: bad parameter value")))
        assertFalse(DynamicsProfiles.isDeterministicRejection(RuntimeException("sementara")))
        assertFalse(DynamicsProfiles.isDeterministicRejection(UnsupportedOperationException("library tidak termuat")))
        assertFalse(DynamicsProfiles.isDeterministicRejection(IllegalStateException("belum siap")))
    }
}
