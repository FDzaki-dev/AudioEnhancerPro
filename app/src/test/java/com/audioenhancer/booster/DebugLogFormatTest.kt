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

    /** Batch 266 — daftar efek tersedia (jawaban "Environmental Reverb ada di device ini?"). */
    private val envReverb = DebugLogFormat.EffectInfo("c2e5d5f0-94bd-4763-9cac-4e234d06839e", "aaaa0000-0000-0000-0000-000000000001", "Auxiliary", "Env Reverb", "NXP Software Ltd.")
    private val presetReverb = DebugLogFormat.EffectInfo("47382d60-ddd8-11db-bf3a-0002a5d5c51b", "bbbb0000-0000-0000-0000-000000000002", "Insert", "Insert Preset Reverb", "NXP Software Ltd.")
    private val vendor = DebugLogFormat.EffectInfo("11111111-2222-3333-4444-555555555555", "cccc0000-0000-0000-0000-000000000003", "Insert", "Vendor FX", "Vendor")

    @Test
    fun `known effect types are labeled case-insensitively and unknown types are not guessed`() {
        assertEquals(DebugLogFormat.ENVIRONMENTAL_REVERB, DebugLogFormat.effectTypeLabel("C2E5D5F0-94BD-4763-9CAC-4E234D06839E"))
        assertEquals("Preset Reverb", DebugLogFormat.effectTypeLabel(" 47382d60-ddd8-11db-bf3a-0002a5d5c51b "))
        assertEquals(DebugLogFormat.UNKNOWN_EFFECT_TYPE, DebugLogFormat.effectTypeLabel("11111111-2222-3333-4444-555555555555"))
        assertEquals(DebugLogFormat.UNKNOWN_EFFECT_TYPE, DebugLogFormat.effectTypeLabel(""))
    }

    @Test
    fun `effects section lists every effect with raw uuids and reports environmental reverb as available`() {
        val section = DebugLogFormat.buildEffectsSection(listOf(presetReverb, envReverb, vendor), null)
        assertTrue(section.startsWith("---- efek audio tersedia (AudioEffect.queryEffects, 3 efek) ----"))
        assertTrue(section.contains("Environmental Reverb tersedia: ya (1 entri)"))
        assertTrue(section.contains("type=c2e5d5f0-94bd-4763-9cac-4e234d06839e | uuid=aaaa0000-0000-0000-0000-000000000001"))
        assertTrue(section.contains("tipe tak dikenal | Vendor FX | mode=Insert | implementor=Vendor | type=11111111-2222-3333-4444-555555555555"))
        assertEquals(3, section.lines().count { it.contains(" | mode=") })
    }

    @Test
    fun `effects section says environmental reverb is absent when the device does not report it`() {
        val section = DebugLogFormat.buildEffectsSection(listOf(presetReverb), null)
        assertTrue(section.contains("Environmental Reverb tersedia: TIDAK"))
        assertTrue(section.contains("ringkasan: Preset Reverb x1"))
    }

    @Test
    fun `effects section reports failure and null results instead of failing`() {
        assertTrue(DebugLogFormat.buildEffectsSection(null, "SecurityException: x").contains("(gagal dibaca: SecurityException: x)"))
        assertTrue(DebugLogFormat.buildEffectsSection(null, null).contains("queryEffects mengembalikan null"))
        assertTrue(DebugLogFormat.buildEffectsSection(emptyList(), null).contains("ringkasan: (kosong)"))
    }

    @Test
    fun `effects section keeps all effects up to the cap and says how many were left out`() {
        val many = (1..305).map { DebugLogFormat.EffectInfo("11111111-2222-3333-4444-555555555555", "u$it", "Insert", "FX$it", "V") }
        val section = DebugLogFormat.buildEffectsSection(many, null)
        assertEquals(300, section.lines().count { it.contains(" | mode=") })
        assertTrue(section.contains("(5 efek lain tak ditampilkan)"))
    }

    @Test
    fun `profile section prints raw verifier code, apk entries and oat files sorted by path`() {
        val section = DebugLogFormat.buildProfileSection(
            DebugLogFormat.ProfileInfo(
                processAgeSeconds = 83,
                verifier = DebugLogFormat.verifierLabel(1, true, false),
                apkProfileBytes = 12_345,
                apkProfileMetaBytes = 678,
                oatFiles = mapOf("arm64/base.vdex" to 3L, "arm64/base.odex" to 9_000_000L)
            )
        )
        assertTrue(section.startsWith("---- profil kompilasi (ProfileVerifier, isi APK, berkas oat) ----"))
        assertTrue(section.contains("umur proses: 83 dtk"))
        assertTrue(section.contains("ProfileVerifier: kode=1 compiledWithProfile=true enqueuedForCompilation=false"))
        assertTrue(section.contains("APK assets/dexopt/baseline.prof: 12345 byte"))
        assertTrue(section.contains("APK assets/dexopt/baseline.profm: 678 byte"))
        assertTrue(section.indexOf("oat/arm64/base.odex: 9000000 byte") in 0 until section.indexOf("oat/arm64/base.vdex: 3 byte"))
    }

    @Test
    fun `profile section reports missing entries, empty or unreadable oat and verifier failure instead of failing`() {
        val missing = DebugLogFormat.buildProfileSection(
            DebugLogFormat.ProfileInfo(5, "gagal: TimeoutException: ", -1, -1, null)
        )
        assertTrue(missing.contains("ProfileVerifier: gagal: TimeoutException: "))
        assertTrue(missing.contains("APK assets/dexopt/baseline.prof: TIDAK ADA / tak terbaca"))
        assertTrue(missing.contains("APK assets/dexopt/baseline.profm: TIDAK ADA / tak terbaca"))
        assertTrue(missing.contains("oat: (folder tak ada atau tak terbaca)"))
        val empty = DebugLogFormat.buildProfileSection(DebugLogFormat.ProfileInfo(5, "x", 0, 0, emptyMap()))
        assertTrue(empty.contains("APK assets/dexopt/baseline.prof: 0 byte"))
        assertTrue(empty.contains("oat: (folder kosong)"))
        assertEquals("kode=262144 compiledWithProfile=false enqueuedForCompilation=false", DebugLogFormat.verifierLabel(262_144, false, false))
    }

    @Test
    fun `lifecycle and spectrum gating lines are never noise`() {
        assertFalse(DebugLogFormat.isNoise("10-28 08:02:26.500 I/MainActivity(16993): LIFECYCLE ui aktif=false"))
        assertFalse(DebugLogFormat.isNoise("10-28 08:02:26.766 I/AudioEnhancerService(16993): Spektrum dijeda: UI tidak membaca > 3 dtk (hemat baterai)"))
    }
}
