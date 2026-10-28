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
        // Batch 243: 3000 baris hanya ±15 siklus serah-terima (±1 menit geser video) -> ambang bawah dinaikkan.
        assertTrue(DebugLogExporter.MAX_LINES >= 8_000)
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

    @Test
    fun `visualizer capture flood is noise`() {
        assertTrue(DebugLogFormat.isNoise("10-28 07:46:10.026 D/AudioEffect(24162): command() 0xb400007c1c2127b0, cmdCode = 65536"))
        assertTrue(DebugLogFormat.isNoise("10-28 07:49:35.076 D/AudioEffect( 9903): commandExecuted(): 0xb400007c1c214370, cmdCode = 5"))
    }

    @Test
    fun `effect creation and handoff lines are never noise`() {
        assertFalse(DebugLogFormat.isNoise("10-28 07:48:18.262 D/AudioEffect(24162): set() 0xb400007c1c228e20 OK effect: DynamicsProcessing id: 47139 status 0 enabled 0 pid 24162"))
        assertFalse(DebugLogFormat.isNoise("10-28 07:49:11.968 W/AudioEnhancerService( 9903): EffectRebind[playback-start]: efek diserahterimakan tanpa celah; serah-terima 126 ms"))
        assertFalse(DebugLogFormat.isNoise("10-28 07:48:18.273 W/AudioEnhancerService(24162): retryControlAcquisition(): recreate effect yang CONTROL_LOST/FAILED"))
        assertFalse(DebugLogFormat.isNoise("10-28 07:49:11.642 D/AudioEffect( 9903): setParameter(): 0xb400007c1c2171b0, param: 85, param2: -1"))
    }

    @Test
    fun `window and touch noise is dropped`() {
        assertTrue(DebugLogFormat.isNoise("10-28 07:48:18.145 I/VRI[MainActivity](24162): enqueueInputEvent: event = MotionEvent { action=ACTION_DOWN, x[0]=682.5 }"))
        assertTrue(DebugLogFormat.isNoise("10-28 07:48:15.832 I/DecorView(24162): dispatchKeyEvent:KeyEvent { action=ACTION_UP }"))
        assertTrue(DebugLogFormat.isNoise("10-28 07:48:15.841 D/InsetsController(24162): hide(ime(), fromIme=false)"))
    }

    @Test
    fun `crash lines are never noise`() {
        assertFalse(DebugLogFormat.isNoise("10-28 07:50:00.000 E/AndroidRuntime(9903): FATAL EXCEPTION: main"))
    }

    @Test
    fun `lines without the logcat time prefix are kept`() {
        assertFalse(DebugLogFormat.isNoise("--------- beginning of main"))
        assertFalse(DebugLogFormat.isNoise(""))
    }

    @Test
    fun `guard parameter read chatter is noise`() {
        assertTrue(DebugLogFormat.isNoise("10-28 03:55:01.559 D/AudioEffect(24162): getParameter: param: 1, param2: -1"))
        assertTrue(DebugLogFormat.isNoise("10-28 08:03:40.577 D/AudioEffect(16993): getParameter: param: 2, param2: 4"))
        assertTrue(DebugLogFormat.isNoise("10-28 07:49:35.100 D/AudioEffect( 9903): getParameter: param: 0, param2: -1"))
    }

    @Test
    fun `guard verdict and parameter writes stay when parameter reads are dropped`() {
        assertFalse(DebugLogFormat.isNoise("10-28 03:55:01.571 I/AudioEnhancerService(24162): EffectGuard[playback]: efek sehat (bass=ENABLED virt=ENABLED eq=ENABLED loud=ENABLED dyn=FAILED)"))
        assertFalse(DebugLogFormat.isNoise("10-28 07:48:18.262 D/AudioEffect(24162): setParameter(): 0xb400007c1c228e20, param: 48, param2: -1"))
        assertFalse(DebugLogFormat.isNoise("10-28 08:02:54.098 D/AudioEffect(16993): controlStatusChanged 0xb400007c1c2024b0 control 0 callback 0xb400007acc240810"))
    }

    @Test
    fun `lifecycle and spectrum gating lines are never noise`() {
        assertFalse(DebugLogFormat.isNoise("10-28 08:02:26.500 I/MainActivity(16993): LIFECYCLE ui aktif=false"))
        assertFalse(DebugLogFormat.isNoise("10-28 08:02:26.766 I/AudioEnhancerService(16993): Spektrum dijeda: UI tidak membaca > 3 dtk (hemat baterai)"))
    }
}
