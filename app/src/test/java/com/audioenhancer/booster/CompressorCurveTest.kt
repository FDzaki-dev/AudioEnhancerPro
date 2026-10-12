package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 208 — Pure-Kotlin test, tanpa Robolectric: menjaga kurva kompresor 3 band (`CompressorCurve`).
 * Yang dijaga: slider 0 = netral/bypass, band VOKAL (indeks 1) = kurva B198 persis (kekuatan tidak
 * berkurang), band bass/treble selalu makeup LEBIH KECIL dari band vokal (vokal menonjol dari backsound),
 * cutoff menaik, dan nilai interpolasi monoton.
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
    fun `vocal band at 100 percent equals the Batch 198 curve`() {
        val b = CompressorCurve.band(vocal, 1f)
        assertEquals(3f, b.attackMs, delta)
        assertEquals(100f, b.releaseMs, delta)
        assertEquals(20f, b.ratio, delta)
        assertEquals(-36f, b.thresholdDb, delta)
        assertEquals(24f, b.postGainDb, delta)
    }

    @Test
    fun `vocal band at 50 percent equals the Batch 198 curve`() {
        val b = CompressorCurve.band(vocal, 0.5f)
        assertEquals(6.5f, b.attackMs, delta)
        assertEquals(125f, b.releaseMs, delta)
        assertEquals(10.5f, b.ratio, delta)
        assertEquals(-18.5f, b.thresholdDb, delta)
        assertEquals(12f, b.postGainDb, delta)
    }

    @Test
    fun `outer bands get less makeup than the vocal band`() {
        for (f in floatArrayOf(0.25f, 0.5f, 0.75f, 1f)) {
            val vocalGain = CompressorCurve.band(vocal, f).postGainDb
            assertTrue(CompressorCurve.band(0, f).postGainDb < vocalGain)
            assertTrue(CompressorCurve.band(2, f).postGainDb < vocalGain)
        }
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
    fun `fraction outside 0 to 1 is clamped`() {
        assertEquals(CompressorCurve.band(vocal, 0f).postGainDb, CompressorCurve.band(vocal, -3f).postGainDb, delta)
        assertEquals(CompressorCurve.band(vocal, 1f).postGainDb, CompressorCurve.band(vocal, 7f).postGainDb, delta)
    }
}
