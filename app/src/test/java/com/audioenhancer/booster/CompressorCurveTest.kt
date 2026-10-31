package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 208/209/210/260/261/262 — Pure-Kotlin test, tanpa Robolectric: menjaga kurva kompresor 3 band (`CompressorCurve`).
 * Yang dijaga: slider 0 = netral/bypass, band VOKAL (indeks 1) berambang tinggi (-25 dB) + makeup sedang (+13 dB)
 * supaya backsound di jeda tidak ikut terangkat (Batch 260), pada level masukan yang sama band vokal keluar
 * PALING keras (vokal menonjol dari backsound) TETAPI tidak lebih dari 10 dB di atas bass / 16 dB di atas treble
 * (Batch 262: B261 = 17 / 23 dB terdengar 'uncanny'), puncak band vokal tetap -12 dB, cutoff menaik, dan nilai interpolasi monoton.
 */
class CompressorCurveTest {

    private val delta = 0.0001f
    private val vocal = 1

    @Test
    fun `slider zero gives neutral values on every band`() {
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            val b = CompressorCurve.band(i, 0f)
            assertEquals(CompressorCurve.NEUTRAL_ATTACK_MS, b.attackMs, delta)
            assertEquals(CompressorCurve.NEUTRAL_RELEASE_MS, b.releaseMs, delta)
            assertEquals(1f, b.ratio, delta)
            assertEquals(-1f, b.thresholdDb, delta)
            assertEquals(0f, b.postGainDb, delta)
        }
    }

    @Test
    fun `vocal band at 100 percent uses the Batch 262 curve`() {
        val b = CompressorCurve.band(vocal, 1f)
        assertEquals(10f, b.attackMs, delta)
        assertEquals(80f, b.releaseMs, delta)
        assertEquals(20f, b.ratio, delta)
        assertEquals(-25f, b.thresholdDb, delta)
        assertEquals(13f, b.postGainDb, delta)
    }

    @Test
    fun `vocal band at 50 percent uses the Batch 262 curve`() {
        val b = CompressorCurve.band(vocal, 0.5f)
        assertEquals(10f, b.attackMs, delta)
        assertEquals(115f, b.releaseMs, delta)
        assertEquals(10.5f, b.ratio, delta)
        assertEquals(-13f, b.thresholdDb, delta)
        assertEquals(6.5f, b.postGainDb, delta)
    }

    /** Batch 260: ambang band tidak sama (vokal -22, luar -36), jadi makeup tak bisa dibandingkan langsung; yang dibandingkan
     *  = level KELUARAN keadaan-stabil (di bawah ambang +makeup, di atas ambang dikompresi) pada level masukan yang sama. */
    private fun steadyOutDb(b: CompressorBandSpec, inDb: Float): Float =
        if (inDb <= b.thresholdDb) inDb + b.postGainDb
        else b.thresholdDb + (inDb - b.thresholdDb) / b.ratio + b.postGainDb

    @Test
    fun `at the same input level the vocal band comes out loudest at default focus`() {
        val v = CompressorCurve.band(vocal, 1f)
        val bass = CompressorCurve.band(0, 1f)
        val treble = CompressorCurve.band(2, 1f)
        for (inDb in floatArrayOf(-24f, -18f, -12f, -6f)) {
            assertTrue(steadyOutDb(v, inDb) > steadyOutDb(bass, inDb))
            assertTrue(steadyOutDb(v, inDb) > steadyOutDb(treble, inDb))
        }
    }

    /** Batch 262 (request user: B261 'uncanny'): band 20:1 mengeluarkan level ~ ambang + makeup berapa pun masukannya, jadi selisih
     *  level keluar vokal vs bass/treble = bentuk spektrum tetap. B261 (vokal -8, bass -26, treble -32 dB) = 17 / 23 dB (spektrum 'radio');
     *  B260 = 8 / 14 dB. Sweet spot B262 = 7,5 / 13,5 dB di semua masukan di atas ambang bass/treble. */
    @Test
    fun `vocal band stays within a natural margin over bass and treble`() {
        val v = CompressorCurve.band(vocal, 1f)
        val bass = CompressorCurve.band(0, 1f)
        val treble = CompressorCurve.band(2, 1f)
        for (inDb in floatArrayOf(-24f, -18f, -12f, -6f)) {
            assertTrue(steadyOutDb(v, inDb) - steadyOutDb(bass, inDb) <= 10f)
            assertTrue(steadyOutDb(v, inDb) - steadyOutDb(treble, inDb) <= 16f)
        }
    }

    /** Batch 262: puncak keluaran band vokal (ambang + makeup, masukan keras) = -12 dB seperti B259/B260 -- konten keras tak naik
     *  dan limiter -1 dB tak terbebani (B261 = -8 dB, limiter bekerja 3,9 dB di model). Konten pelan/normal diangkat lewat ambang. */
    @Test
    fun `vocal band peak output level stays at minus 12 dB`() {
        val b = CompressorCurve.band(vocal, 1f)
        assertEquals(-12f, b.thresholdDb + b.postGainDb, delta)
    }

    /** Batch 260: penyebab "backsound makin keras, vokal kalah" = ambang -36 dB + makeup +24 dB meratakan semua isi band vokal. */
    @Test
    fun `vocal band does not lift quiet backsound like an automatic gain control`() {
        val b = CompressorCurve.band(vocal, 1f)
        assertTrue(b.thresholdDb >= -26f)
        assertTrue(b.postGainDb <= 16f) // B198-B259 = +24 dB berambang -36 dB (AGC); B262 = +13 dB berambang -25 dB
    }

    /** Batch 260: attack terlalu cepat menjepit transien suku kata; rilis terlalu cepat mengangkat backsound di antara suku kata. */
    @Test
    fun `vocal band timing lets syllable onsets through without pumping`() {
        val b = CompressorCurve.band(vocal, 1f)
        assertTrue(b.attackMs >= 6f)
        assertTrue(b.releaseMs >= 60f)
        assertTrue(b.releaseMs <= CompressorCurve.NEUTRAL_RELEASE_MS)
    }

    @Test
    fun `cutoffs strictly ascend and the last band covers full range`() {
        val cutoffs = (0 until CompressorCurve.BAND_COUNT).map { CompressorCurve.band(it, 1f).cutoffHz }
        for (i in 1 until cutoffs.size) assertTrue(cutoffs[i] > cutoffs[i - 1])
        assertEquals(20000f, cutoffs.last(), delta)
    }

    @Test
    fun `cutoff does not change with slider position`() {
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            assertEquals(CompressorCurve.band(i, 0f).cutoffHz, CompressorCurve.band(i, 1f).cutoffHz, delta)
        }
    }

    @Test
    fun `ratio and makeup never decrease as the slider moves up`() {
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            var prev = CompressorCurve.band(i, 0f)
            for (step in 1..10) {
                val cur = CompressorCurve.band(i, step / 10f)
                assertTrue(cur.ratio >= prev.ratio)
                assertTrue(cur.postGainDb >= prev.postGainDb)
                assertTrue(cur.thresholdDb <= prev.thresholdDb)
                prev = cur
            }
        }
    }

    @Test
    fun `default focus gives the Batch 262 makeup values`() {
        assertEquals(16f, CompressorCurve.band(0, 1f).postGainDb, delta)
        assertEquals(13f, CompressorCurve.band(vocal, 1f).postGainDb, delta)
        assertEquals(10f, CompressorCurve.band(2, 1f).postGainDb, delta)
    }

    @Test
    fun `focus zero gives bass and treble their maximum makeup`() {
        assertEquals(24f, CompressorCurve.band(0, 1f, 0f).postGainDb, delta)
        assertEquals(13f, CompressorCurve.band(vocal, 1f, 0f).postGainDb, delta)
        assertEquals(16f, CompressorCurve.band(2, 1f, 0f).postGainDb, delta)
    }

    @Test
    fun `focus one pulls bass and treble back furthest`() {
        assertEquals(8f, CompressorCurve.band(0, 1f, 1f).postGainDb, delta)
        assertEquals(13f, CompressorCurve.band(vocal, 1f, 1f).postGainDb, delta)
        assertEquals(4f, CompressorCurve.band(2, 1f, 1f).postGainDb, delta)
    }

    @Test
    fun `vocal band ignores focus and outer makeup falls as focus rises`() {
        var prevBass = Float.MAX_VALUE
        var prevTreble = Float.MAX_VALUE
        for (step in 0..10) {
            val g = step / 10f
            assertEquals(13f, CompressorCurve.band(vocal, 1f, g).postGainDb, delta)
            val bass = CompressorCurve.band(0, 1f, g).postGainDb
            val treble = CompressorCurve.band(2, 1f, g).postGainDb
            assertTrue(bass <= prevBass)
            assertTrue(treble <= prevTreble)
            prevBass = bass
            prevTreble = treble
        }
    }

    /** Batch 261/262: kedalaman Vocal Focus dipilih supaya makeup band luar tak pernah negatif (band tak ditekan di bawah level kering). */
    @Test
    fun `outer band makeup never goes negative for any focus`() {
        for (step in 0..10) {
            val g = step / 10f
            assertTrue(CompressorCurve.band(0, 1f, g).postGainDb >= 0f)
            assertTrue(CompressorCurve.band(2, 1f, g).postGainDb >= 0f)
        }
    }

    @Test
    fun `focus has no effect while the slider is at zero`() {
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            assertEquals(0f, CompressorCurve.band(i, 0f, 1f).postGainDb, delta)
        }
    }

    @Test
    fun `focus outside 0 to 1 is clamped`() {
        assertEquals(CompressorCurve.band(0, 1f, 0f).postGainDb, CompressorCurve.band(0, 1f, -2f).postGainDb, delta)
        assertEquals(CompressorCurve.band(0, 1f, 1f).postGainDb, CompressorCurve.band(0, 1f, 9f).postGainDb, delta)
    }

    @Test
    fun `vocal band is never gated`() {
        for (f in floatArrayOf(0f, 0.5f, 1f)) for (g in floatArrayOf(0f, 0.5f, 1f)) {
            val b = CompressorCurve.band(vocal, f, g)
            assertEquals(-90f, b.noiseGateDb, delta)
            assertEquals(1f, b.expanderRatio, delta)
        }
    }

    @Test
    fun `outer bands get a gentle expander that follows compressor and focus`() {
        for (i in intArrayOf(0, 2)) {
            val full = CompressorCurve.band(i, 1f)
            assertEquals(-60f, full.noiseGateDb, delta)
            assertEquals(2f, full.expanderRatio, delta)
            assertEquals(3f, CompressorCurve.band(i, 1f, 1f).expanderRatio, delta)
            assertEquals(1f, CompressorCurve.band(i, 1f, 0f).expanderRatio, delta)
            assertEquals(1f, CompressorCurve.band(i, 0f, 1f).expanderRatio, delta)
            assertEquals(-90f, CompressorCurve.band(i, 0f, 1f).noiseGateDb, delta)
        }
    }

    @Test
    fun `presence boost only lifts the middle post-EQ band`() {
        assertEquals(0f, CompressorCurve.presenceGainDb(0, 1f, 1f), delta)
        assertEquals(0f, CompressorCurve.presenceGainDb(2, 1f, 1f), delta)
        assertEquals(2f, CompressorCurve.presenceGainDb(1, 1f), delta)
        assertEquals(4f, CompressorCurve.presenceGainDb(1, 1f, 1f), delta)
        assertEquals(0f, CompressorCurve.presenceGainDb(1, 1f, 0f), delta)
        assertEquals(0f, CompressorCurve.presenceGainDb(1, 0f, 1f), delta)
    }

    @Test
    fun `presence cutoffs ascend and end at full range`() {
        val cutoffs = (0 until CompressorCurve.PRESENCE_BAND_COUNT).map { CompressorCurve.presenceCutoffHz(it) }
        for (i in 1 until cutoffs.size) assertTrue(cutoffs[i] > cutoffs[i - 1])
        assertEquals(20000f, cutoffs.last(), delta)
    }

    @Test
    fun `presence band sits inside the vocal band`() {
        val vocalLow = CompressorCurve.band(0, 1f).cutoffHz
        val vocalHigh = CompressorCurve.band(vocal, 1f).cutoffHz
        assertTrue(CompressorCurve.presenceCutoffHz(0) > vocalLow)
        assertTrue(CompressorCurve.presenceCutoffHz(1) < vocalHigh)
    }

    @Test
    fun `fraction outside 0 to 1 is clamped`() {
        assertEquals(CompressorCurve.band(vocal, 0f).postGainDb, CompressorCurve.band(vocal, -3f).postGainDb, delta)
        assertEquals(CompressorCurve.band(vocal, 1f).postGainDb, CompressorCurve.band(vocal, 7f).postGainDb, delta)
    }

    /** Batch 259/260: attack & rilis di ujung slider tak boleh lebih lambat dari nilai netral (lonjakan awal suara + "mendem"
     *  sesudahnya membesar bila lambat); batas bawah band vokal dijaga tes terpisah di atas. */
    @Test
    fun `attack and release at full slider are never slower than neutral`() {
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            val full = CompressorCurve.band(i, 1f)
            assertTrue(full.attackMs <= CompressorCurve.NEUTRAL_ATTACK_MS)
            assertTrue(full.releaseMs <= CompressorCurve.NEUTRAL_RELEASE_MS)
        }
    }
}
