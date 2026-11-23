package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Batch 343 — Pure-Kotlin test, tanpa Robolectric: menjaga pembatas per band Gain Keluaran (`OutputGainGuard`) dan jalur tulisnya.
 * Yang dijaga: pelindung aktif HANYA bila Compressor mati dan Gain Keluaran > 0 (Gain 0 = perilaku persis B339, Compressor menyala =
 * kurva B262 tak disentuh), batas band memakai satu sumber dengan `CompressorCurve` (250 / 5000 / 20000 Hz), angka band B343
 * (bass -4 / vokal -1 / treble -3 dB, 20:1, tanpa makeup, gate mati) berada di rentang yang SUDAH diterima device (attack >= 0,5 ms,
 * rilis >= 40 ms, ambang >= -36 dB), serta (tes teks sumber, karena `DynamicsProcessing` butuh Android) bahwa band dibangun lewat
 * pembangun bersama `compressorMbcBand`, setter menulis ulang band hanya saat Compressor mati, dan `Config` konstruktor hanya memuat
 * Gain Keluaran sampai batas yang terbukti (+24 dB; sisanya ditulis sesudah objek dibuat).
 */
class OutputGainGuardTest {

    private val delta = 0.0001f

    @Test
    fun `guard is active only when compressor is off and output gain is above zero`() {
        assertTrue(OutputGainGuard.isActive(compressorAmount = 0, outputGainDb = 1))
        assertTrue(OutputGainGuard.isActive(compressorAmount = 0, outputGainDb = PrefsHelper.OUTPUT_GAIN_MAX_DB))
        assertFalse("Gain 0 = perilaku B339", OutputGainGuard.isActive(compressorAmount = 0, outputGainDb = 0))
        assertFalse("Compressor menyala = kurva B262", OutputGainGuard.isActive(compressorAmount = 1, outputGainDb = 12))
        assertFalse(OutputGainGuard.isActive(compressorAmount = 100, outputGainDb = 24))
        assertFalse(OutputGainGuard.isActive(compressorAmount = 50, outputGainDb = 0))
    }

    @Test
    fun `slider range ends at plus 30 dB`() {
        assertEquals(30, PrefsHelper.OUTPUT_GAIN_MAX_DB)
    }

    @Test
    fun `guard bands share cutoffs with the compressor curve and ascend`() {
        var previous = 0f
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            val cutoff = OutputGainGuard.band(i).cutoffHz
            assertEquals(CompressorCurve.band(i, 0f).cutoffHz, cutoff, delta)
            assertTrue("batas band harus menaik", cutoff > previous)
            previous = cutoff
        }
    }

    @Test
    fun `guard bands use the Batch 343 curve`() {
        val bass = OutputGainGuard.band(0)
        val vocal = OutputGainGuard.band(1)
        val treble = OutputGainGuard.band(2)
        assertEquals(-4f, bass.thresholdDb, delta)
        assertEquals(-1f, vocal.thresholdDb, delta)
        assertEquals(-3f, treble.thresholdDb, delta)
        assertEquals(4f, bass.attackMs, delta)
        assertEquals(1.5f, vocal.attackMs, delta)
        assertEquals(0.5f, treble.attackMs, delta)
        assertEquals(120f, bass.releaseMs, delta)
        assertEquals(60f, vocal.releaseMs, delta)
        assertEquals(40f, treble.releaseMs, delta)
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            val b = OutputGainGuard.band(i)
            assertEquals(20f, b.ratio, delta)
            assertEquals("tanpa makeup: kekerasan datang dari gain input, bukan makeup band", 0f, b.postGainDb, delta)
            assertEquals("noise-gate mati", -90f, b.noiseGateDb, delta)
            assertEquals("expander mati", 1f, b.expanderRatio, delta)
        }
    }

    @Test
    fun `bass is limited earlier than the vocal band so kicks cannot duck the vocal`() {
        assertTrue(OutputGainGuard.band(0).thresholdDb < OutputGainGuard.band(1).thresholdDb)
        assertTrue(OutputGainGuard.band(0).releaseMs > OutputGainGuard.band(1).releaseMs)
    }

    @Test
    fun `guard values stay inside ranges devices already accept`() {
        for (i in 0 until CompressorCurve.BAND_COUNT) {
            val b = OutputGainGuard.band(i)
            assertTrue("attack >= 0,5 ms (nilai limiter B259)", b.attackMs >= 0.5f)
            assertTrue("rilis >= 40 ms (nilai band treble B262)", b.releaseMs >= 40f)
            assertTrue("rasio <= 20:1 (ujung kurva B262)", b.ratio in 1f..20f)
            assertTrue("ambang antara -36 dB (ujung B262) dan -1 dB (netral)", b.thresholdDb in -36f..-1f)
        }
    }

    @Test
    fun `band index out of range is rejected`() {
        try {
            OutputGainGuard.band(CompressorCurve.BAND_COUNT)
            fail("indeks di luar rentang harus ditolak")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("di luar"))
        }
    }

    // ---- penjaga sumber (jalur tulis) ----

    private fun readSource(name: String): String {
        val relative = "src/main/java/com/audioenhancer/booster/$name"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    private fun bodyBetween(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        assertTrue("$startMarker tidak ditemukan", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("$endMarker tidak ditemukan sesudah $startMarker", end > start)
        return source.substring(start, end).lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `band builder switches to the guard only while compressor is off`() {
        val body = bodyBetween(readSource("AudioEnhancerService.kt"), "private fun compressorMbcBand(", "private fun presenceEqBand(")
        assertTrue("Gain Keluaran dibaca dari nilai tersimpan", body.contains("OutputGainGuard.isActive(amount, PrefsHelper.getOutputGain(this))"))
        assertTrue("kurva Compressor B262 tetap dipakai bila pelindung mati", body.contains("CompressorCurve.band(bandIndex, fraction, focus)"))
        assertTrue("band menyala bila Compressor ATAU pelindung aktif", body.contains("amount > 0 || guard"))
    }

    @Test
    fun `output gain setter rewrites bands only while compressor is off`() {
        val setter = bodyBetween(readSource("AudioEnhancerService.kt"), "fun setOutputGain(", "private fun applyOutputGain(")
        assertTrue(setter.contains("PrefsHelper.setOutputGain(this, clamped)"))
        assertTrue(setter.contains("applyOutputGain()"))
        assertTrue(
            "band ditulis ulang hanya saat Compressor mati",
            setter.contains("if (compressorAmount <= 0) applyCompressorBands(compressorAmount, PrefsHelper.getVocalFocus(this))")
        )
        assertTrue(setter.indexOf("PrefsHelper.setOutputGain(this, clamped)") < setter.indexOf("applyCompressorBands("))
    }

    @Test
    fun `config prefill caps output gain at the proven value and attach rewrites the full value`() {
        val source = readSource("AudioEnhancerService.kt")
        val config = bodyBetween(source, "private fun buildDynamicsConfig(", "private fun applyCompressorBands(")
        assertTrue("Config hanya memuat gain sampai batas terbukti", config.contains(".coerceAtMost(CONFIG_OUTPUT_GAIN_PROVEN_DB)"))
        assertTrue(source.contains("private const val CONFIG_OUTPUT_GAIN_PROVEN_DB = 24"))
        assertTrue(
            "nilai penuh ditulis sesudah objek dibuat",
            source.contains("if (PrefsHelper.getOutputGain(this@AudioEnhancerService) > 0) applyOutputGain(this)")
        )
    }
}
