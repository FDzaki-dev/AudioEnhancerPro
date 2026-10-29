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

    @Test
    fun `playback refresh only when the relevant player set changes`() {
        assertTrue(DumpProbe.shouldRefreshOnPlayback("", "483612325"))   // pemutar media mulai
        assertTrue(DumpProbe.shouldRefreshOnPlayback("483612325", ""))   // berhenti total (lepas sesi mati)
        assertTrue(DumpProbe.shouldRefreshOnPlayback("1,2", "1,3"))      // pemutar berganti
        assertTrue(DumpProbe.shouldRefreshOnPlayback("1", null))         // gagal membaca = tetap jadwalkan
        assertFalse(DumpProbe.shouldRefreshOnPlayback("", ""))           // alarm/sonifikasi naik-turun: himpunan relevan tetap kosong
        assertFalse(DumpProbe.shouldRefreshOnPlayback("483612325", "483612325"))
    }

    @Test
    fun `player usages cover every dump candidate and exclude alarms`() {
        assertTrue(DumpProbe.PLAYER_USAGES.containsAll(setOf(1, 14)))
        assertFalse(DumpProbe.PLAYER_USAGES.contains(4)) // USAGE_ALARM
        assertFalse(DumpProbe.PLAYER_USAGES.contains(13)) // USAGE_ASSISTANCE_SONIFICATION
        assertTrue("usage rebind (B237) harus subset", EffectGuardPolicy.isRebindUsage(0) && DumpProbe.PLAYER_USAGES.contains(0))
    }

    @Test
    fun `creation is retried a bounded number of times`() {
        assertTrue(DumpProbe.shouldTryCreate(0))
        assertTrue(DumpProbe.shouldTryCreate(DumpProbe.CREATE_ATTEMPTS - 1))
        assertFalse(DumpProbe.shouldTryCreate(DumpProbe.CREATE_ATTEMPTS))
        assertTrue(DumpProbe.CREATE_ATTEMPTS in 2..5)
    }

    @Test
    fun `failed creation is counted not blocked forever and success clears the count`() {
        val src = code(serviceSource)
        assertTrue(src.contains("private val sessionReverbFailed = HashMap<Int, Int>()"))
        val apply = between(src, "private fun applySessionReverbs(", "private fun hasDisabledSessionReverb(")
        assertTrue(apply.contains("sessionReverbFailed.keys.retainAll(wanted.toSet())"))
        assertTrue(apply.contains("if (!DumpProbe.shouldTryCreate(failures)) continue"))
        assertTrue(apply.contains("sessionReverbFailed.remove(session)"))
        assertTrue(apply.contains("sessionReverbFailed[session] = failures + 1"))
        assertFalse("tak boleh kembali ke set yang blokir selamanya", apply.contains("sessionReverbFailed.add("))
    }

    // ---- tes teks sumber ----

    @Test
    fun `slider does not schedule the generic session refresh and playback filters by relevant players`() {
        val src = code(serviceSource)
        val reverb = between(src, "private fun enableReverb(", "private fun attachEqualizer(")
        assertTrue(reverb.contains("if (active && refreshSessions) scheduleSessionReverbRefresh()"))
        assertTrue(between(src, "fun setReverbPreset(", "private fun compressorMbcBand(").contains("if (isRunning) enableReverb(refreshSessions = false)"))
        val callback = between(src, "private fun onPlaybackConfigsChanged(", "private fun scheduleSessionReverbRefresh(")
        assertTrue(callback.contains("DumpProbe.PLAYER_USAGES"))
        assertTrue(callback.contains("if (DumpProbe.shouldRefreshOnPlayback(lastReverbPlayerKey, reverbKey)) scheduleSessionReverbRefresh()"))
        assertTrue("kunci diperbarui SESUDAH keputusan", callback.indexOf("lastReverbPlayerKey = reverbKey") > callback.indexOf("shouldRefreshOnPlayback"))
        assertTrue("Effect Guard tetap jalan untuk SEMUA perubahan", callback.contains("requestEffectGuard(\"playback\")"))
    }

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
