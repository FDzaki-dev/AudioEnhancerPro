package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 282 — Pure-Kotlin test, tanpa Robolectric: menjaga kurva slider Clarity (`ClarityCurve`) dan jalur tulisnya.
 * Yang dijaga: Clarity 0 = tambahan 0 dB di semua band (perilaku persis sebelum fitur ini, termasuk presence vokal B210),
 * band bass/badan (indeks 0) TIDAK PERNAH berubah, ujung 100% = +3 dB artikulasi / +5 dB udara, interpolasi linear,
 * gain gabungan dengan presence vokal dijepit `TOTAL_CAP_DB`, tangga chip cepat menaik dan searah langkah slider, serta
 * (tes teks sumber, karena `DynamicsProcessing`/ViewModel butuh Android) bahwa Clarity menulis HANYA band post-EQ lewat
 * pembangun band bersama `presenceEqBand` dan melewati jalur nilai-terbaru ViewModel (pola B279).
 */
class ClarityCurveTest {

    private val delta = 0.0001f

    @Test
    fun `clarity zero adds nothing on any band`() {
        for (i in 0 until CompressorCurve.PRESENCE_BAND_COUNT) {
            assertEquals(0f, ClarityCurve.gainDb(i, 0f), delta)
        }
    }

    @Test
    fun `bass and body band is never touched by clarity`() {
        for (step in 0..20) {
            assertEquals(0f, ClarityCurve.gainDb(0, step / 20f), delta)
        }
    }

    @Test
    fun `clarity 100 percent adds the definition and air lift`() {
        assertEquals(3f, ClarityCurve.gainDb(1, 1f), delta)
        assertEquals(5f, ClarityCurve.gainDb(2, 1f), delta)
    }

    @Test
    fun `clarity gain rises linearly with the slider`() {
        assertEquals(1.5f, ClarityCurve.gainDb(1, 0.5f), delta)
        assertEquals(2.5f, ClarityCurve.gainDb(2, 0.5f), delta)
        for (band in 1 until CompressorCurve.PRESENCE_BAND_COUNT) {
            var previous = ClarityCurve.gainDb(band, 0f)
            for (step in 1..20) {
                val now = ClarityCurve.gainDb(band, step / 20f)
                assertTrue("band $band harus naik monoton di langkah $step", now >= previous)
                previous = now
            }
        }
    }

    @Test
    fun `clarity input outside the slider range is clamped`() {
        assertEquals(0f, ClarityCurve.gainDb(2, -1f), delta)
        assertEquals(5f, ClarityCurve.gainDb(2, 2f), delta)
        assertEquals(3f, ClarityCurve.gainDb(1, 7f), delta)
    }

    @Test
    fun `band index outside the post eq range is rejected`() {
        var rejected = 0
        for (bad in listOf(-1, CompressorCurve.PRESENCE_BAND_COUNT)) {
            try {
                ClarityCurve.gainDb(bad, 1f)
            } catch (_: IllegalArgumentException) {
                rejected++
            }
        }
        assertEquals(2, rejected)
    }

    @Test
    fun `clarity zero leaves vocal presence exactly as before`() {
        val fractions = listOf(0f, 0.25f, 0.5f, 1f)
        val focuses = listOf(0f, 0.5f, 1f)
        for (i in 0 until CompressorCurve.PRESENCE_BAND_COUNT) {
            for (fraction in fractions) {
                for (focus in focuses) {
                    val presence = CompressorCurve.presenceGainDb(i, fraction, focus)
                    assertEquals(presence, ClarityCurve.combinedDb(presence, i, 0f), delta)
                }
            }
        }
    }

    @Test
    fun `combined gain never exceeds the cap`() {
        val clarities = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        for (i in 0 until CompressorCurve.PRESENCE_BAND_COUNT) {
            for (clarity in clarities) {
                val combined = ClarityCurve.combinedDb(CompressorCurve.presenceGainDb(i, 1f, 1f), i, clarity)
                assertTrue("band $i clarity $clarity = $combined melewati batas", combined <= ClarityCurve.TOTAL_CAP_DB + delta)
            }
        }
    }

    @Test
    fun `stacked vocal presence and full clarity is capped on the definition band`() {
        val defaultFocus = CompressorCurve.presenceGainDb(1, 1f)
        assertEquals(5f, ClarityCurve.combinedDb(defaultFocus, 1, 1f), delta)
        val maxFocus = CompressorCurve.presenceGainDb(1, 1f, 1f)
        assertEquals(ClarityCurve.TOTAL_CAP_DB, ClarityCurve.combinedDb(maxFocus, 1, 1f), delta)
    }

    @Test
    fun `preset ladder ascends ends at 100 and follows the slider step`() {
        val ladder = listOf(0, ClarityCurve.PRESET_NATURAL, ClarityCurve.PRESET_CRYSTAL, ClarityCurve.PRESET_ULTRA)
        for (i in 1 until ladder.size) assertTrue("tangga harus menaik", ladder[i] > ladder[i - 1])
        assertEquals(100, ClarityCurve.PRESET_ULTRA)
        ladder.forEach { assertEquals("kelipatan langkah slider (5)", 0, it % 5) }
        assertTrue(ClarityCurve.gainDb(2, ClarityCurve.PRESET_ULTRA / 100f) > ClarityCurve.gainDb(2, ClarityCurve.PRESET_CRYSTAL / 100f))
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
    fun `presence band builder reads saved clarity for every write path`() {
        val body = bodyBetween(readSource("AudioEnhancerService.kt"), "private fun presenceEqBand(", "private fun masterLimiter(")
        assertTrue("Clarity dibaca dari nilai tersimpan", body.contains("PrefsHelper.getClarity(this)"))
        assertTrue("band menyala bila Compressor ATAU Clarity > 0", body.contains("amount > 0 || clarity > 0f"))
        assertTrue("gain digabung lewat ClarityCurve", body.contains("ClarityCurve.combinedDb("))
    }

    @Test
    fun `clarity setter rewrites only the post eq bands`() {
        val source = readSource("AudioEnhancerService.kt")
        val setter = bodyBetween(source, "fun setClarity(", "private fun applyClarityBands(")
        assertTrue(setter.contains("PrefsHelper.setClarity(this, clamped)"))
        assertTrue(setter.contains("applyClarityBands()"))
        assertFalse("Clarity tidak boleh menulis ulang band kompresor", setter.contains("applyCompressorBands("))
        val apply = bodyBetween(source, "private fun applyClarityBands(", "private fun compressorMbcBand(")
        assertTrue("profil tanpa post-EQ dilewati", apply.contains("DynamicsProfiles.ORDER[dynamicsProfileIndex].postEq"))
        assertTrue(apply.contains("target?.setPostEqBandAllChannelsTo("))
        assertTrue(apply.contains("presenceEqBand(presenceIndex, amount, fraction, focus)"))
        assertFalse("band MBC tidak boleh disentuh Clarity", apply.contains("setMbcBandAllChannelsTo"))
    }

    @Test
    fun `view model applies clarity through the shared latest value path`() {
        val source = readSource("BoosterViewModel.kt").lines().joinToString("\n") { it.substringBefore("//") }
        val setter = bodyBetween(source, "fun setClarity(", "\n    }\n")
        assertTrue(setter.contains("latestClarity.set(value)"))
        assertTrue(setter.contains("eqApplyKick.trySend(Unit)"))
        assertTrue("tetap tersimpan bila belum konek", setter.contains("else pendingClarity = value"))
        assertFalse("tak boleh memanggil service langsung di Main", setter.contains("service?.setClarity("))
        val apply = bodyBetween(source, "private fun applyLatestSliders(", "private fun <T : Any> applyLatest(")
        assertTrue(apply.contains("applyLatest(latestClarity,") && apply.contains("svc.setClarity(it)"))
        val has = bodyBetween(source, "private fun hasLatestSliderValues(", "\n    /**")
        assertTrue(has.contains("latestClarity.get() != null"))
        assertTrue("nilai tertahan diterapkan saat konek", source.contains("pendingClarity?.let { service?.setClarity(it) }; pendingClarity = null"))
    }
}
