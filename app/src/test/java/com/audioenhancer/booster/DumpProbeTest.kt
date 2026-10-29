package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 247 — penjaga pembacaan `dumpsys audio` (izin DUMP) dan Reverb per-session.
 *
 * Bagian 1 (JVM murni): parser `DumpProbe` diuji dengan baris ASLI dari log device B246 (Infinix X6855, Android 16),
 * dipotong persis seperti tercatat di log. Bagian 2 (tes teks sumber): Reverb per-session hanya aktif bila slider > Mati dan
 * izin DUMP ada, tidak menyentuh efek session 0 / serah-terima efek / Effect Guard, dan selalu dilepas saat Matikan/destroy.
 */
class DumpProbeTest {

    private fun readMain(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    private fun serviceSource(): String = readMain("java/com/audioenhancer/booster/AudioEnhancerService.kt")

    private fun bodyBetween(source: String, start: String, end: String): String {
        val s = source.indexOf(start)
        assertTrue("Penanda awal tidak ditemukan: $start", s >= 0)
        val e = source.indexOf(end, s + start.length)
        assertTrue("Penanda akhir tidak ditemukan: $end", e > s)
        return source.substring(s, e).replace(Regex("//[^\n]*"), "")
    }

    // Baris asli dari `aep_debug_20261028_143047.txt` (bagian `players:` pada `dumpsys audio`).
    private val soundPoolSystem =
        "  AudioPlaybackConfiguration piid:519 deviceIds:[] type:android.media.SoundPool u/pid:1000/3772 state:idle attr:AudioAttributes: " +
            "usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:0 mutedState:none  FormatInfo{isSpatializ"
    private val soundPoolMedia =
        "  AudioPlaybackConfiguration piid:106999 deviceIds:[] type:android.media.SoundPool u/pid:10385/3843 state:idle attr:AudioAttributes: " +
            "usage=USAGE_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x800 tags= bundle=null sessionId:0 mutedState:none  FormatInfo{isSpatialized=false, channelMa"
    private val pausedMusic =
        "  AudioPlaybackConfiguration piid:107095 deviceIds:[3] type:android.media.AudioTrack u/pid:10553/32467 state:paused attr:AudioAttributes: " +
            "usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null sessionId:47009 mutedState:none  FormatInfo{isSpatialized=false, ch"
    private val stoppedSonification =
        "  AudioPlaybackConfiguration piid:109335 deviceIds:[3] type:android.media.MediaPlayer u/pid:1000/2960 state:stopped attr:AudioAttributes: " +
            "usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:75529 mutedState:none  FormatInf"
    private val startedMusic =
        "  AudioPlaybackConfiguration piid:111935 deviceIds:[3] type:android.media.AudioTrack u/pid:10460/31403 state:started attr:AudioAttributes: " +
            "usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null sessionId:76081 mutedState:none  FormatInfo{isSpatialized=false, c"
    private val noiseLines = listOf(
        "  players:",
        "  ducked players piids:",
        "  piid: 107095 portId: 16010",
        "10-28 14:19:03:688 new player piid:111839 uid/pid:10170/3810 package:com.android.systemui type:android.media.MediaPlayer " +
            "attr:AudioAttributes: usage=USAGE_UNKNOWN content=CONTENT_TYPE_UNKNOWN flags=0x800 tags= bundle=null session:77225"
    )

    private fun realDump(): Sequence<String> =
        (listOf(noiseLines[0], soundPoolSystem, soundPoolMedia, pausedMusic, stoppedSonification, startedMusic) + noiseLines.drop(1)).asSequence()

    // ---- parser ----

    @Test
    fun `parsePlayers reads every player line and ignores noise`() {
        val players = DumpProbe.parsePlayers(realDump())
        assertEquals(listOf(519, 106999, 107095, 109335, 111935), players.map { it.piid })
    }

    @Test
    fun `parsePlayers reads all fields and does not confuse mutedState with state`() {
        val player = DumpProbe.parsePlayers(sequenceOf(startedMusic)).single()
        assertEquals(DumpProbe.Player(piid = 111935, uid = 10460, state = "started", usage = "USAGE_MEDIA", sessionId = 76081), player)
    }

    @Test
    fun `parsePlayers tolerates a different field order`() {
        val line = "AudioPlaybackConfiguration piid:7 sessionId:99 usage=USAGE_GAME u/pid:10001/5 state:started"
        assertEquals(
            DumpProbe.Player(piid = 7, uid = 10001, state = "started", usage = "USAGE_GAME", sessionId = 99),
            DumpProbe.parsePlayers(sequenceOf(line)).single()
        )
    }

    @Test
    fun `parsePlayers skips a player line that lacks a session id`() {
        val line = "AudioPlaybackConfiguration piid:7 u/pid:10001/5 state:started attr:AudioAttributes: usage=USAGE_MEDIA"
        assertTrue(DumpProbe.parsePlayers(sequenceOf(line)).isEmpty())
    }

    // ---- pemilihan session ----

    @Test
    fun `pickSessions keeps media sessions with started first and drops system and sessionless players`() {
        val players = DumpProbe.parsePlayers(realDump())
        assertEquals(listOf(76081, 47009), DumpProbe.pickSessions(players, ownUid = 1, max = DumpProbe.MAX_SESSIONS))
    }

    @Test
    fun `pickSessions skips the players of the own uid`() {
        val players = DumpProbe.parsePlayers(realDump())
        assertEquals(listOf(47009), DumpProbe.pickSessions(players, ownUid = 10460, max = DumpProbe.MAX_SESSIONS))
    }

    @Test
    fun `pickSessions de-duplicates and caps the result`() {
        val players = listOf(11, 12, 12, 13, 14).mapIndexed { i, session ->
            DumpProbe.Player(piid = i, uid = 10001, state = "started", usage = "USAGE_MEDIA", sessionId = session)
        }
        assertEquals(listOf(11, 12, 13), DumpProbe.pickSessions(players, ownUid = 1, max = 3))
    }

    @Test
    fun `pickSessions ignores stopped and released players`() {
        val players = listOf("stopped", "released").mapIndexed { i, state ->
            DumpProbe.Player(piid = i, uid = 10001, state = state, usage = "USAGE_MEDIA", sessionId = 20 + i)
        }
        assertTrue(DumpProbe.pickSessions(players, ownUid = 1, max = 3).isEmpty())
    }

    // ---- aturan lain ----

    @Test
    fun `shouldRun respects first run and minimum interval`() {
        assertTrue(DumpProbe.shouldRun(EffectGuardPolicy.NEVER, 0L))
        assertFalse(DumpProbe.shouldRun(1_000L, 1_000L + DumpProbe.MIN_INTERVAL_MS - 1))
        assertTrue(DumpProbe.shouldRun(1_000L, 1_000L + DumpProbe.MIN_INTERVAL_MS))
    }

    @Test
    fun `command is a read-only dumpsys audio call and describe is compact`() {
        assertEquals(listOf("dumpsys", "audio"), DumpProbe.COMMAND)
        assertEquals(
            "piid=1 uid=2 state=started usage=USAGE_MEDIA sesi=3",
            DumpProbe.describe(DumpProbe.Player(1, 2, "started", "USAGE_MEDIA", 3))
        )
    }

    // ---- diagnostik B248 ----

    @Test
    fun `statusName maps the AudioEffect status codes`() {
        assertEquals("SUCCESS", DumpProbe.statusName(0))
        assertEquals("INVALID_OPERATION", DumpProbe.statusName(-5))
        assertEquals("NO_MEMORY", DumpProbe.statusName(-6))
        assertEquals("DEAD_OBJECT", DumpProbe.statusName(-7))
        assertEquals("UNKNOWN", DumpProbe.statusName(-99))
    }

    @Test
    fun `enable retries are bounded and quick`() {
        assertTrue(DumpProbe.ENABLE_RETRIES in 1..10)
        assertTrue(DumpProbe.ENABLE_RETRY_DELAY_MS in 100L..1_000L)
    }

    // ---- tes teks sumber ----

    @Test
    fun `refresh does nothing unless running, slider is above Off, permission is granted and interval allows`() {
        val body = bodyBetween(serviceSource(), "private fun refreshSessionReverb(", "private fun readPlayerSessions(")
        val running = body.indexOf("if (!isRunning) return")
        val slider = body.indexOf("if (!ReverbPresets.isActive(index))")
        val release = body.indexOf("releaseSessionReverbs(")
        val permission = body.indexOf("ContextCompat.checkSelfPermission(this, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED")
        val interval = body.indexOf("if (!force && !DumpProbe.shouldRun(lastDumpProbeAtMs, now)) return")
        val busy = body.indexOf("dumpProbeBusy.compareAndSet(false, true)")
        assertTrue(running in 0 until slider)
        assertTrue("slider Mati melepas tanpa dump", slider < release && release < permission)
        assertTrue("izin dicek sebelum jeda & proses", permission < interval && interval < busy)
        assertTrue("dump berjalan di IO, bukan main thread", serviceSource().contains("kotlinx.coroutines.Dispatchers.IO"))
    }

    @Test
    fun `apply works only while running and active and never touches the session 0 effects`() {
        val src = serviceSource()
        val body = bodyBetween(src, "private fun applySessionReverbs(", "private fun createSessionReverb(")
        assertTrue(body.contains("synchronized(sessionReverbLock)"))
        assertTrue(body.contains("isRunning && dumpProbeScope.isActive && ReverbPresets.isActive(index)"))
        val perSession = bodyBetween(src, "private fun applySessionReverbs(", "/** Lepas SEMUA Reverb per-session")
        for (forbidden in listOf("presetReverb", "bassBoost", "virtualizer", "equalizer", "dynamicsProcessing", "loudnessEnhancer", "setControlStatusListener", "setEnableStatusListener")) {
            assertFalse("Reverb per-session tidak boleh menyentuh: $forbidden", perSession.contains(forbidden))
        }
        assertTrue("dibuat di session pemutar, BUKAN session 0", perSession.contains("PresetReverb(EffectGuardPolicy.EFFECT_PRIORITY, session)"))
    }

    @Test
    fun `slider change saves first then refreshes while running`() {
        val body = bodyBetween(serviceSource(), "fun setReverbPreset(", "private fun compressorMbcBand(")
        val save = body.indexOf("PrefsHelper.setReverbPreset(this, clamped)")
        val refresh = body.indexOf("if (isRunning) refreshSessionReverbAfterPreset(changed)")
        assertTrue(save >= 0)
        assertTrue("segar dipicu SESUDAH nilai disimpan", refresh > save)
    }

    @Test
    fun `per-session reverbs are released on Matikan and on destroy and scheduled from player changes`() {
        val src = serviceSource()
        assertTrue(bodyBetween(src, "private fun disableEffects(", "private fun enableEffects(").contains("releaseSessionReverbs("))
        assertTrue(bodyBetween(src, "private fun releaseEffects(", "private fun disableEffects(").contains("releaseSessionReverbs("))
        val onDestroy = bodyBetween(src, "override fun onDestroy()", "/** Menempel ke audio session 0")
        assertTrue("scope dibatalkan SEBELUM efek dilepas", onDestroy.indexOf("dumpProbeScope.cancel()") in 0 until onDestroy.indexOf("releaseEffects()"))
        val callback = bodyBetween(src, "private fun onPlaybackConfigsChanged(", "// ---- Batch 247")
        assertTrue(callback.contains("scheduleSessionReverbRefresh()"))
        assertEquals("satu-satunya penjadwal runnable", 1, Regex("""postDelayed\(dumpProbeRunnable""").findAll(src).count())
    }

    @Test
    fun `enable status is logged and a failed enable is retried under the lock`() {
        val src = serviceSource()
        val create = bodyBetween(src, "private fun createSessionReverb(", "private fun updateSessionReverb(")
        assertTrue("status setEnabled dicatat", create.contains("created.setEnabled(true)") && create.contains("DumpProbe.statusName(status)"))
        val apply = bodyBetween(src, "private fun applySessionReverbs(", "/** Batch 249")
        assertTrue("jatah percobaan ulang di-reset tiap penyegaran", apply.contains("sessionReverbRetries = 0"))
        assertTrue("percobaan ulang dijadwalkan bila ada yang belum menyala", apply.contains("if (hasDisabledSessionReverb()) {") && apply.contains("scheduleEnableRetry()"))
        val retry = bodyBetween(src, "private fun scheduleEnableRetry(", "private fun createSessionReverb(")
        assertTrue("jumlah percobaan dibatasi", retry.contains("sessionReverbRetries >= DumpProbe.ENABLE_RETRIES"))
        assertTrue("jeda singkat sebelum mencoba lagi", retry.contains("kotlinx.coroutines.delay(DumpProbe.ENABLE_RETRY_DELAY_MS)"))
        assertTrue("di bawah kunci", retry.contains("synchronized(sessionReverbLock)"))
        assertTrue("hanya saat menyala dan scope hidup", retry.contains("if (!isRunning || !dumpProbeScope.isActive) return"))
        for (forbidden in listOf("presetReverb", "bassBoost", "virtualizer", "equalizer", "dynamicsProcessing", "loudnessEnhancer")) {
            assertFalse("percobaan ulang tidak boleh menyentuh: $forbidden", retry.contains(forbidden))
        }
    }

    @Test
    fun `manifest declares DUMP with a narrow lint suppression`() {
        val manifest = readMain("AndroidManifest.xml")
        assertTrue(
            Regex("""<uses-permission\s+android:name="android\.permission\.DUMP"\s+tools:ignore="ProtectedPermissions"\s*/>""")
                .containsMatchIn(manifest)
        )
        assertTrue(manifest.contains("""xmlns:tools="http://schemas.android.com/tools""""))
        assertEquals("android.permission.DUMP", DumpProbe.PERMISSION)
    }
}
