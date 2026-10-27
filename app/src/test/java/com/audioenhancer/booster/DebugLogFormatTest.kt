package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 240 — bagian murni ekspor log debug (nama file + susunan laporan). Proses `logcat` & MediaStore tak bisa dites di JVM. */
class DebugLogFormatTest {

    @Test
    fun `file name follows the aep_debug timestamp pattern`() {
        val name = DebugLogFormat.fileName(1_700_000_000_000L)
        assertTrue("nama tak sesuai pola: $name", Regex("""aep_debug_\d{8}_\d{6}\.txt""").matches(name))
    }

    @Test
    fun `file name never collides with crash log names`() {
        assertFalse(DebugLogFormat.fileName(0L).startsWith("crash_"))
    }

    @Test
    fun `report starts with the header lines in order`() {
        val report = DebugLogFormat.buildReport(listOf("H1", "H2"), listOf("a"), maxLines = 10)
        val lines = report.lines()
        assertEquals("H1", lines[0])
        assertEquals("H2", lines[1])
    }

    @Test
    fun `report keeps every line when under the cap`() {
        val report = DebugLogFormat.buildReport(listOf("H"), listOf("l1", "l2", "l3"), maxLines = 10)
        assertTrue(report.contains("(3 baris terakhir)"))
        assertTrue(report.contains("l1") && report.contains("l2") && report.contains("l3"))
    }

    @Test
    fun `report keeps only the newest lines when over the cap`() {
        val report = DebugLogFormat.buildReport(listOf("H"), listOf("old1", "old2", "new1", "new2"), maxLines = 2)
        assertFalse(report.contains("old1"))
        assertFalse(report.contains("old2"))
        assertTrue(report.contains("new1"))
        assertTrue(report.contains("new2"))
        assertTrue(report.contains("(2 baris terakhir)"))
    }

    @Test
    fun `empty logcat is explained instead of leaving a blank file`() {
        val report = DebugLogFormat.buildReport(listOf("H"), emptyList(), maxLines = 10)
        assertTrue(report.contains("kosong"))
        assertTrue(report.contains("(0 baris terakhir)"))
    }

    @Test
    fun `line cap is large enough for a gapless test but bounded`() {
        assertTrue(DebugLogExporter.MAX_LINES >= 1000)
        assertTrue(DebugLogExporter.MAX_LINES <= 10_000)
    }

    @Test
    fun `debug log status states in Settings survive rotation`() {
        val relative = "src/main/java/com/audioenhancer/booster/SettingsScreen.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        val src = file.readText()
        listOf("debugLogStatus", "debugLogStatusIsError").forEach { name ->
            val saveable = Regex("""var\s+$name\s+by\s+rememberSaveable\s*\{""").containsMatchIn(src)
            assertTrue("State `$name` harus rememberSaveable agar tahan rotasi", saveable)
        }
    }
}
