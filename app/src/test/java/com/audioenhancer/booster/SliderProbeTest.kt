package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 253 (hemat baterai, request user): menggeser slider Reverb tidak lagi menjalankan `dumpsys audio` tiap langkah.
 *
 * JVM murni: (1) keputusan `DumpProbe.sliderRefresh`/`needsDebouncedProbe` (tabel kebenaran), (2) tes teks sumber untuk kontrak yang tak terlihat
 * kompiler: jalur pembaruan efek terpasang tidak boleh membuat efek / memanggil dump, dan jalur dump tertunda harus dibatalkan tiap langkah.
 */
class SliderProbeTest {

    private val serviceSource: String by lazy {
        val rel = "src/main/java/com/audioenhancer/booster/AudioEnhancerService.kt"
        checkNotNull(listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.isFile }?.readText()) { "AudioEnhancerService.kt tidak ditemukan" }
    }

    private fun code(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        require(from >= 0) { "penanda awal tidak ada: $start" }
        val to = source.indexOf(end, from + start.length)
        require(to > from) { "penanda akhir tidak ada: $end" }
        return source.substring(from, to)
    }

    // ---- keputusan murni ----

    @Test
    fun `unchanged preset inactive slider or missing permission refresh immediately`() {
        // kirim ulang tanpa perubahan (mis. izin berubah)
        assertEquals(DumpProbe.SliderRefresh.NOW, DumpProbe.sliderRefresh(presetChanged = false, active = true, hasPermission = true))
        assertEquals(DumpProbe.SliderRefresh.NOW, DumpProbe.sliderRefresh(presetChanged = false, active = false, hasPermission = false))
        // slider Mati: lepas tanpa dump
        assertEquals(DumpProbe.SliderRefresh.NOW, DumpProbe.sliderRefresh(presetChanged = true, active = false, hasPermission = true))
        // izin tak ada: lepas + catat tanpa dump
        assertEquals(DumpProbe.SliderRefresh.NOW, DumpProbe.sliderRefresh(presetChanged = true, active = true, hasPermission = false))
    }

    @Test
    fun `only a real change to an active preset with permission takes the cheap path`() {
        assertEquals(DumpProbe.SliderRefresh.UPDATE_ATTACHED, DumpProbe.sliderRefresh(presetChanged = true, active = true, hasPermission = true))
    }

    @Test
    fun `a dump is needed only when nothing is attached`() {
        assertTrue(DumpProbe.needsDebouncedProbe(0))
        assertFalse(DumpProbe.needsDebouncedProbe(1))
        assertFalse(DumpProbe.needsDebouncedProbe(3))
    }

    @Test
    fun `debounce is long enough to merge a drag and short enough to feel instant`() {
        assertTrue(DumpProbe.SLIDER_DEBOUNCE_MS in 200L..800L)
        assertTrue("debounce harus lebih pendek dari jeda minimum dump", DumpProbe.SLIDER_DEBOUNCE_MS < DumpProbe.MIN_INTERVAL_MS)
    }

    // ---- tes teks sumber ----

    @Test
    fun `setReverbPreset saves first then uses the cheap refresh and no longer dumps directly`() {
        val body = code(between(serviceSource, "fun setReverbPreset(", "private fun compressorMbcBand("))
        val save = body.indexOf("PrefsHelper.setReverbPreset(this, clamped)")
        val changed = body.indexOf("val changed = ReverbPresets.clamp(PrefsHelper.getReverbPreset(this)) != clamped")
        val refresh = body.indexOf("refreshSessionReverbAfterPreset(changed)")
        assertTrue("perubahan dibaca SEBELUM nilai baru disimpan", changed in 0 until save)
        assertTrue("segar dipicu SESUDAH nilai disimpan", save in 0 until refresh)
        assertFalse("jalur lama (dump tiap langkah) harus hilang", body.contains("refreshSessionReverb(\"slider\""))
    }

    @Test
    fun `slider path cancels the pending probe before deciding and debounces on the main handler`() {
        val body = code(between(serviceSource, "private fun refreshSessionReverbAfterPreset(", "private fun updateAttachedSessionReverbs("))
        val cancel = body.indexOf("guardHandler.removeCallbacks(sliderProbeRunnable)")
        val decide = body.indexOf("DumpProbe.sliderRefresh(")
        assertTrue("dump tertunda lama dibatalkan SEBELUM keputusan", cancel in 0 until decide)
        assertTrue(body.contains("dumpProbeScope.launch"))
        assertTrue(body.contains("guardHandler.postDelayed(sliderProbeRunnable, DumpProbe.SLIDER_DEBOUNCE_MS)"))
        assertTrue("penjadwalan ulang lewat handler utama (satu antrean)", body.indexOf("guardHandler.post {") > body.indexOf("dumpProbeScope.launch"))
        assertFalse("jalur ini tak boleh menjalankan dump sendiri", body.contains("readPlayerSessions") || body.contains("readDump"))
    }

    @Test
    fun `updating attached reverbs never creates effects never dumps and holds the lock`() {
        val body = code(between(serviceSource, "private fun updateAttachedSessionReverbs(", "private fun refreshSessionReverb("))
        assertTrue(body.contains("synchronized(sessionReverbLock)"))
        assertTrue(body.contains("updateSessionReverb(entry.key, entry.value, index)"))
        assertTrue("gagal diperbarui = dilepas", body.contains("releaseSessionReverb(entry.key, entry.value, \"gagal diperbarui\")"))
        for (forbidden in listOf("createSessionReverb", "readPlayerSessions", "readDump", "ProcessBuilder", "presetReverb", "bassBoost", "virtualizer", "equalizer", "dynamicsProcessing", "loudnessEnhancer")) {
            assertFalse("pembaruan efek terpasang tak boleh menyentuh: $forbidden", body.contains(forbidden))
        }
    }

    @Test
    fun `the debounced runnable reuses the normal refresh and is cleared on shutdown`() {
        val src = code(serviceSource)
        assertTrue(src.contains("private val sliderProbeRunnable = Runnable { refreshSessionReverb(\"slider\", force = true) }"))
        // Matikan/destroy memakai removeCallbacksAndMessages(null) yang mencakup runnable ini (jangan diganti removeCallbacks satu-satu).
        assertTrue(src.contains("guardHandler.removeCallbacksAndMessages(null)"))
    }
}
