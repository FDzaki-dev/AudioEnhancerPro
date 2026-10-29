package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 244 — penjaga fitur Reverb/Spatial (preset `PresetReverb` di session 0).
 *
 * Dua bagian, keduanya JVM murni: (1) aturan murni `ReverbPresets` (rentang indeks, aktif/mati, pemetaan ke nilai
 * platform), (2) tes teks sumber yang menjaga ISOLASI efek ini dari serah-terima efek B233-B238 dan Effect Guard —
 * efek tersebut sengaja tanpa listener dan di luar `runEffectRebind`/`runEffectGuard`/`retryControlAcquisition`
 * (tes B233 menghitung tepat 5 `return@set...Listener`; menambah efek ke jalur itu = mengubah audio NOT VERIFIED).
 */
class ReverbPresetsTest {

    private fun readMain(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    private fun serviceSource(): String = readMain("java/com/audioenhancer/booster/AudioEnhancerService.kt")

    /** Potongan sumber dari [start] sampai [end], komentar `//` dibuang supaya tes menilai KODE, bukan catatan. */
    private fun bodyBetween(source: String, start: String, end: String): String {
        val s = source.indexOf(start)
        assertTrue("Penanda awal tidak ditemukan: $start", s >= 0)
        val e = source.indexOf(end, s + start.length)
        assertTrue("Penanda akhir tidak ditemukan: $end", e > s)
        return source.substring(s, e).replace(Regex("//[^\n]*"), "")
    }

    // ---- aturan murni ----

    @Test
    fun `preset count is seven and max index is six`() {
        assertEquals(7, ReverbPresets.COUNT)
        assertEquals(6, ReverbPresets.MAX_INDEX)
    }

    @Test
    fun `clamp keeps index inside range`() {
        assertEquals(0, ReverbPresets.clamp(-3))
        assertEquals(0, ReverbPresets.clamp(0))
        assertEquals(3, ReverbPresets.clamp(3))
        assertEquals(6, ReverbPresets.clamp(6))
        assertEquals(6, ReverbPresets.clamp(99))
    }

    @Test
    fun `only index zero is inactive`() {
        assertFalse(ReverbPresets.isActive(0))
        assertFalse("indeks negatif dijepit ke 0 = mati", ReverbPresets.isActive(-1))
        for (i in 1..ReverbPresets.MAX_INDEX) assertTrue("indeks $i harus aktif", ReverbPresets.isActive(i))
        assertTrue("indeks di atas batas dijepit ke 6 = aktif", ReverbPresets.isActive(99))
    }

    @Test
    fun `platform preset equals clamped index`() {
        for (i in 0..ReverbPresets.MAX_INDEX) assertEquals(i.toShort(), ReverbPresets.platformPreset(i))
        assertEquals(0.toShort(), ReverbPresets.platformPreset(-5))
        assertEquals(6.toShort(), ReverbPresets.platformPreset(100))
    }

    @Test
    fun `saved default is off`() {
        assertEquals(0, PrefsHelper.REVERB_PRESET_DEFAULT)
    }

    // ---- isolasi dari serah-terima efek & guard ----

    @Test
    fun `attachReverb has no status listeners`() {
        val body = bodyBetween(serviceSource(), "private fun attachReverb(", "private fun describeReverb(")
        assertFalse(body.contains("setControlStatusListener"))
        assertFalse(body.contains("setEnableStatusListener"))
        assertTrue("efek dibuat di session 0", body.contains("PresetReverb(EffectGuardPolicy.EFFECT_PRIORITY, 0)"))
    }

    @Test
    fun `handoff guard and retry never touch reverb`() {
        val src = serviceSource()
        val rebind = bodyBetween(src, "private fun runEffectRebind(", "private fun releaseReplaced(")
        val guard = bodyBetween(src, "private fun runEffectGuard(", "private fun hasControlLostEffect(")
        val retry = bodyBetween(src, "fun retryControlAcquisition(", "// ---- Batch 229")
        val mix = bodyBetween(src, "private fun enableMixEffects(", "// ---- Kontrol dari UI ----")
        listOf("runEffectRebind" to rebind, "runEffectGuard" to guard, "retryControlAcquisition" to retry, "enableMixEffects" to mix)
            .forEach { (name, body) ->
                assertFalse("$name tidak boleh menyentuh Reverb (efek di luar serah-terima)", body.contains("reverb", ignoreCase = true))
            }
    }

    @Test
    fun `reverb is wired into start enable disable and release`() {
        val src = serviceSource()
        assertTrue(bodyBetween(src, "private fun attachEffects(", "private fun attachBass(").contains("attachReverb()"))
        assertTrue(bodyBetween(src, "private fun enableEffects(", "private fun enableMixEffects(").contains("enableReverb()"))
        assertTrue(bodyBetween(src, "private fun disableEffects(", "private fun enableEffects(").contains("presetReverb?.enabled = false"))
        val release = bodyBetween(src, "private fun releaseEffects(", "private fun disableEffects(")
        assertTrue(release.contains("presetReverb?.release()"))
        assertTrue(release.contains("presetReverb = null"))
        assertTrue(release.contains("reverbState = EffectState.UNAVAILABLE"))
    }

    @Test
    fun `setReverbPreset saves first then applies only while running`() {
        val body = bodyBetween(serviceSource(), "fun setReverbPreset(", "private fun compressorMbcBand(")
        val save = body.indexOf("PrefsHelper.setReverbPreset(this, clamped)")
        val apply = body.indexOf("if (isRunning) enableReverb(refreshSessions = false)")
        assertTrue("nilai disimpan dulu", save >= 0)
        assertTrue("diterapkan hanya saat layanan menyala, SESUDAH disimpan", apply > save)
    }

    @Test
    fun `enableReverb applies saved preset before enabling`() {
        val body = bodyBetween(serviceSource(), "private fun enableReverb(", "private fun attachEqualizer(")
        val preset = body.indexOf("reverb.preset = ReverbPresets.platformPreset(index)")
        val enabled = body.indexOf("reverb.enabled = active")
        assertTrue(preset >= 0)
        assertTrue("preset ditulis SEBELUM efek dinyalakan", enabled > preset)
        assertTrue("kegagalan menandai FAILED, bukan crash", body.contains("reverbState = EffectState.FAILED"))
    }

    // ---- sumber daya ----

    private fun presetNames(resPath: String): List<String> {
        val xml = readMain(resPath)
        val block = Regex("""<string-array name="reverb_preset_names">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)
        assertTrue("string-array reverb_preset_names tidak ada di $resPath", block != null)
        return Regex("""<item>(.*?)</item>""").findAll(block!!).map { it.groupValues[1].trim() }.toList()
    }

    @Test
    fun `preset names exist in both locales with one entry per preset`() {
        listOf("res/values/strings.xml", "res/values-en/strings.xml").forEach { path ->
            val names = presetNames(path)
            assertEquals("jumlah nama preset di $path", ReverbPresets.COUNT, names.size)
            assertTrue("ada nama kosong di $path", names.none { it.isBlank() })
            assertEquals("nama preset harus unik di $path", names.size, names.toSet().size)
        }
    }
}
