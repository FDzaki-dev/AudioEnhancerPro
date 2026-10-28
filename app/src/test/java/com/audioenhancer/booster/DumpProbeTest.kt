package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 246 — penjaga PROBE `dumpsys` (izin DUMP; read-only).
 *
 * Dua bagian JVM murni: (1) aturan murni `DumpProbe` (pemilihan baris, pemotongan, jeda minimum), (2) tes teks sumber yang
 * menjaga bahwa probe HANYA membaca: tanpa izin = diam, tanpa menyentuh efek/suara, dan izin DUMP terdeklarasi di manifest
 * dengan penanda lint yang sempit. Format `sessionId` di dump BELUM terverifikasi, jadi tak ada tes parser.
 */
class DumpProbeTest {

    private fun readMain(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    private fun bodyBetween(source: String, start: String, end: String): String {
        val s = source.indexOf(start)
        assertTrue("Penanda awal tidak ditemukan: $start", s >= 0)
        val e = source.indexOf(end, s + start.length)
        assertTrue("Penanda akhir tidak ditemukan: $end", e > s)
        return source.substring(s, e).replace(Regex("//[^\n]*"), "")
    }

    // ---- aturan murni ----

    @Test
    fun `select keeps keyword lines and drops unrelated ones`() {
        val dump = sequenceOf(
            "Audio Service state",
            "  players:",
            "  AudioPlaybackConfiguration piid:42 type:MediaPlayer",
            "  mute master: false",
            "  sessionId:77 attached"
        )
        val picked = DumpProbe.select(dump)
        assertEquals(
            listOf("  players:", "  AudioPlaybackConfiguration piid:42 type:MediaPlayer", "  sessionId:77 attached"),
            picked
        )
    }

    @Test
    fun `select takes table rows after a Session header then returns to keyword mode`() {
        val rows = (1..20).map { "    ${it}  1 yes  1234  ${100 + it}  9" }
        val dump = sequenceOf("Tracks of 1 threads", "    Type  Id Active Client Session Port Id") + rows.asSequence() + sequenceOf("unrelated line")
        val picked = DumpProbe.select(dump)
        assertTrue(picked.contains("    Type  Id Active Client Session Port Id"))
        assertEquals("header + TABLE_ROWS baris tabel", 1 + DumpProbe.TABLE_ROWS, picked.size)
        assertEquals(rows.first(), picked[1])
        assertEquals(rows[DumpProbe.TABLE_ROWS - 1], picked.last())
        assertFalse(picked.contains("unrelated line"))
    }

    @Test
    fun `select skips blank table rows`() {
        val dump = sequenceOf("Type Id Active Client Session", "", "   ", "row 7")
        assertEquals(listOf("Type Id Active Client Session", "row 7"), DumpProbe.select(dump))
    }

    @Test
    fun `select caps lines and truncates long lines but still drains the whole stream`() {
        var consumed = 0
        val dump = (1..(DumpProbe.MAX_LINES + 50)).asSequence().onEach { consumed++ }.map { "session $it " + "x".repeat(500) }
        val picked = DumpProbe.select(dump)
        assertEquals(DumpProbe.MAX_LINES, picked.size)
        assertTrue(picked.all { it.length <= DumpProbe.MAX_LINE_CHARS })
        assertEquals("aliran dihabiskan supaya hitungan total baris akurat", DumpProbe.MAX_LINES + 50, consumed)
    }

    @Test
    fun `shouldRun respects first run and minimum interval`() {
        assertTrue(DumpProbe.shouldRun(EffectGuardPolicy.NEVER, 0L))
        assertFalse(DumpProbe.shouldRun(1_000L, 1_000L + DumpProbe.MIN_INTERVAL_MS - 1))
        assertTrue(DumpProbe.shouldRun(1_000L, 1_000L + DumpProbe.MIN_INTERVAL_MS))
    }

    @Test
    fun `chunk keeps order and never exceeds the chunk size`() {
        val lines = (1..100).map { "baris-$it " + "y".repeat(120) }
        val chunks = DumpProbe.chunk(lines)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= DumpProbe.CHUNK_CHARS })
        assertEquals(lines, chunks.flatMap { it.split('\n') })
        assertTrue(DumpProbe.chunk(emptyList()).isEmpty())
    }

    @Test
    fun `commands are read-only dumpsys calls`() {
        assertTrue(DumpProbe.COMMANDS.isNotEmpty())
        assertTrue(DumpProbe.COMMANDS.all { it.first() == "dumpsys" && it.size == 2 })
    }

    // ---- tes teks sumber ----

    @Test
    fun `probe runs only when running and permission is granted and never touches effects`() {
        val src = readMain("java/com/audioenhancer/booster/AudioEnhancerService.kt")
        val body = bodyBetween(src, "private fun probePlayerSessions(", "/** Batch 241")
        assertTrue(body.contains("if (!isRunning) return"))
        assertTrue(body.contains("ContextCompat.checkSelfPermission(this, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED"))
        assertTrue("jeda minimum", body.contains("DumpProbe.shouldRun(lastDumpProbeAtMs, now)"))
        assertTrue("satu proses sekaligus", body.contains("dumpProbeBusy.compareAndSet(false, true)"))
        assertTrue("berjalan di IO, bukan main thread", src.contains("kotlinx.coroutines.Dispatchers.IO"))
        for (forbidden in listOf("presetReverb", "bassBoost", "virtualizer", "equalizer", "dynamicsProcessing", "loudnessEnhancer", ".enabled =")) {
            assertFalse("probe tidak boleh menyentuh efek: $forbidden", body.contains(forbidden))
        }
    }

    @Test
    fun `probe is scheduled only from the rebind branch and its scope is cancelled on destroy`() {
        val src = readMain("java/com/audioenhancer/booster/AudioEnhancerService.kt")
        val callback = bodyBetween(src, "private fun onPlaybackConfigsChanged(", "private fun probePlayerSessions(")
        assertTrue(callback.contains("guardHandler.postDelayed(dumpProbeRunnable, DumpProbe.DELAY_MS)"))
        val onDestroy = bodyBetween(src, "override fun onDestroy()", "/** Menempel ke audio session 0")
        assertTrue(onDestroy.contains("dumpProbeScope.cancel()"))
        assertEquals("pemicu probe hanya satu", 1, Regex("""postDelayed\(dumpProbeRunnable""").findAll(src).count())
    }

    @Test
    fun `manifest declares DUMP with a narrow lint suppression`() {
        val manifest = readMain("AndroidManifest.xml")
        assertTrue(
            Regex("""<uses-permission\s+android:name="android\.permission\.DUMP"\s+tools:ignore="ProtectedPermissions"\s*/>""")
                .containsMatchIn(manifest)
        )
        assertTrue(manifest.contains("""xmlns:tools="http://schemas.android.com/tools""""))
        assertEquals("DUMP hanya berlaku bila user memberi lewat adb/Shizuku", "android.permission.DUMP", DumpProbe.PERMISSION)
    }
}
