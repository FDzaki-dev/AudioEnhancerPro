package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 255/256 — cuplikan jalur audio (`dumpsys media.audio_flinger` + `media.audio_policy`) di Ekspor Log Debug. JVM murni: penyaring derau + bentuk
 * bagian laporan, dan tes teks sumber untuk kontrak yang tak terlihat kompiler (hanya dengan izin DUMP, hanya saat ekspor, aliran ditutup, dibatasi).
 */
class AudioPathSnapshotTest {

    private val exporterSource: String by lazy {
        val rel = "src/main/java/com/audioenhancer/booster/DebugLogExporter.kt"
        checkNotNull(listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.isFile }?.readText()) { "DebugLogExporter.kt tidak ditemukan" }
    }

    @Test
    fun `track effect and thread rows are kept and only blank or timestamp noise is dropped`() {
        // Batch 256: v255 membuang baris-baris ini (tanpa kata kunci) padahal justru itu bukti jalur track.
        assertTrue(DebugLogFormat.isAudioPathLine("Output thread 0xb4000071a0c2e000, name AudioOut_1D, tid 1234, type 0 (MIXER):"))
        assertTrue(DebugLogFormat.isAudioPathLine("  2 effects for session 84241"))
        assertTrue(DebugLogFormat.isAudioPathLine("   1     1   yes 10460/10460    87705    123 A  0x0600 00000001 00000003  48000  3  1  2   0.0   0.0   0.0"))
        assertTrue(DebugLogFormat.isAudioPathLine("Hal frame count: 512"))
        assertFalse(DebugLogFormat.isAudioPathLine(""))
        assertFalse(DebugLogFormat.isAudioPathLine("    "))
        assertFalse(DebugLogFormat.isAudioPathLine("   10-30 16:31:06.896 threadLoop_standby: BOOTTIME offset 640541651208768: ExtendedTimestamp: ([0] position: 0)"))
    }

    @Test
    fun `section reports title counts and shows an explicit empty marker`() {
        val some = DebugLogFormat.buildAudioPathSection("media.audio_flinger", listOf("a", "b"), 120)
        assertTrue(some.startsWith("---- jalur audio (dumpsys media.audio_flinger, 2 dari 120 baris) ----"))
        assertTrue(some.contains("a\nb\n"))
        val empty = DebugLogFormat.buildAudioPathSection("media.audio_policy", emptyList(), 0)
        assertTrue(empty.contains("dumpsys media.audio_policy, 0 dari 0 baris"))
        assertTrue(empty.contains("(kosong:"))
    }

    @Test
    fun `both dumps are attached with bounded sizes`() {
        assertEquals(listOf("media.audio_flinger", "media.audio_policy"), DebugLogFormat.AUDIO_DUMPS.map { it.service })
        for (dump in DebugLogFormat.AUDIO_DUMPS) {
            assertTrue(dump.maxLines in 500..4_000)
            assertTrue(DebugLogFormat.PATH_MAX_READ >= dump.maxLines)
        }
        assertTrue("batas baca tak boleh tak terbatas", DebugLogFormat.PATH_MAX_READ <= 200_000)
    }

    @Test
    fun `snapshot runs only on export with the DUMP permission and closes its stream`() {
        val src = exporterSource.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val snapshot = src.substring(src.indexOf("private fun readAudioPathSnapshot("), src.indexOf("private fun readDump("))
        assertTrue("izin diperiksa SEBELUM dump dibaca", snapshot.contains("ContextCompat.checkSelfPermission(context, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED) return \"\""))
        val dump = src.substring(src.indexOf("private fun readDump("), src.indexOf("private fun buildHeader("))
        assertTrue(dump.contains("ProcessBuilder(\"dumpsys\", dump.service)"))
        assertTrue("aliran ditutup (use)", dump.contains(".bufferedReader().use"))
        assertTrue("proses dimatikan di finally", dump.contains("process.destroy()"))
        assertTrue(dump.contains("DebugLogFormat.PATH_MAX_READ") && dump.contains("dump.maxLines"))
        // hanya dipanggil dari export(), bukan dari jalur pemutaran/probe
        assertEquals(1, Regex("readAudioPathSnapshot\\(context\\)").findAll(src).count())
        assertFalse(File("src/main/java/com/audioenhancer/booster/AudioEnhancerService.kt").takeIf { it.isFile }?.readText()?.contains("media.audio_") ?: false)
    }
}
