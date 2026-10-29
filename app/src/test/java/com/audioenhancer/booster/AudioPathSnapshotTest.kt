package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 255 — cuplikan jalur audio (`dumpsys media.audio_flinger`) di Ekspor Log Debug. JVM murni: penyaring baris + bentuk bagian laporan, dan
 * tes teks sumber untuk kontrak yang tak terlihat kompiler (hanya dengan izin DUMP, hanya saat ekspor, aliran ditutup, dibatasi).
 */
class AudioPathSnapshotTest {

    private val exporterSource: String by lazy {
        val rel = "src/main/java/com/audioenhancer/booster/DebugLogExporter.kt"
        checkNotNull(listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.isFile }?.readText()) { "DebugLogExporter.kt tidak ditemukan" }
    }

    @Test
    fun `relevant audio flinger lines are kept and noise is dropped`() {
        assertTrue(DebugLogFormat.isAudioPathLine("Output thread 0xb4000071a0c2e000, name AudioOut_1D, tid 1234, type 0 (MIXER):"))
        assertTrue(DebugLogFormat.isAudioPathLine("Effect chains:"))
        assertTrue(DebugLogFormat.isAudioPathLine("    Session: 0 (global effects)"))
        assertTrue(DebugLogFormat.isAudioPathLine("Output thread 0xb4000071a0c2f000, name AudioOut_D, type 4 (OFFLOAD):"))
        assertTrue(DebugLogFormat.isAudioPathLine("  Sample rate: 48000 Hz"))
        assertFalse(DebugLogFormat.isAudioPathLine(""))
        assertFalse(DebugLogFormat.isAudioPathLine("    "))
        assertFalse(DebugLogFormat.isAudioPathLine("Hal frame count: 512"))
    }

    @Test
    fun `section reports counts and shows an explicit empty marker`() {
        val some = DebugLogFormat.buildAudioPathSection(listOf("a", "b"), 120)
        assertTrue(some.startsWith("---- jalur audio (dumpsys media.audio_flinger, disaring 2 dari 120 baris) ----"))
        assertTrue(some.contains("a\nb\n"))
        val empty = DebugLogFormat.buildAudioPathSection(emptyList(), 0)
        assertTrue(empty.contains("disaring 0 dari 0 baris"))
        assertTrue(empty.contains("(kosong:"))
    }

    @Test
    fun `limits keep the report small`() {
        assertTrue(DebugLogFormat.PATH_MAX_LINES in 200..1_000)
        assertTrue(DebugLogFormat.PATH_MAX_READ >= DebugLogFormat.PATH_MAX_LINES)
        assertEquals("batas baca tak boleh tak terbatas", true, DebugLogFormat.PATH_MAX_READ <= 200_000)
    }

    @Test
    fun `snapshot runs only on export with the DUMP permission and closes its stream`() {
        val src = exporterSource.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val body = src.substring(src.indexOf("private fun readAudioPathSnapshot("), src.indexOf("private fun buildHeader("))
        val permission = body.indexOf("ContextCompat.checkSelfPermission(context, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED) return \"\"")
        val start = body.indexOf("ProcessBuilder(\"dumpsys\", \"media.audio_flinger\")")
        assertTrue("izin diperiksa SEBELUM proses dijalankan", permission in 0 until start)
        assertTrue("aliran ditutup (use)", body.contains(".bufferedReader().use"))
        assertTrue("proses dimatikan di finally", body.contains("process.destroy()"))
        assertTrue(body.contains("DebugLogFormat.PATH_MAX_READ") && body.contains("DebugLogFormat.PATH_MAX_LINES"))
        // hanya dipanggil dari export(), bukan dari jalur pemutaran/probe
        assertEquals(1, Regex("readAudioPathSnapshot\\(context\\)").findAll(src).count())
        assertFalse(File("src/main/java/com/audioenhancer/booster/AudioEnhancerService.kt").takeIf { it.isFile }?.readText()?.contains("media.audio_flinger") ?: false)
    }
}
