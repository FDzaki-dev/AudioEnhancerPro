package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 247 — penjaga pembacaan `dumpsys audio` (izin DUMP). Batch 270: Reverb per-session DICABUT; probe hanya untuk mengikat efek utama
 * ke sesi pemutar (B257).
 *
 * Bagian 1 (JVM murni): parser `DumpProbe` diuji dengan baris ASLI dari log device B246 (Infinix X6855, Android 16),
 * dipotong persis seperti tercatat di log. Bagian 2 (tes teks sumber): probe hanya jalan bila layanan menyala, izin DUMP ada, dan jeda
 * minimum terpenuhi; dijadwalkan dari perubahan pemutar; scope dibatalkan sebelum efek dilepas; tak ada kode Reverb tersisa.
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
    fun `startedSessions keeps only started foreign media sessions from the real dump`() {
        val players = DumpProbe.parsePlayers(realDump())
        assertEquals(listOf(76081), DumpProbe.startedSessions(players, ownUid = 1))
    }

    @Test
    fun `startedSessions skips the players of the own uid`() {
        val players = DumpProbe.parsePlayers(realDump())
        assertTrue(DumpProbe.startedSessions(players, ownUid = 10460).isEmpty())
    }

    @Test
    fun `startedSessions de-duplicates and ignores paused idle stopped and released players`() {
        val players = listOf("started" to 11, "started" to 11, "paused" to 12, "idle" to 13, "stopped" to 14, "released" to 15).mapIndexed { i, (state, session) ->
            DumpProbe.Player(piid = i, uid = 10001, state = state, usage = "USAGE_MEDIA", sessionId = session)
        }
        assertEquals(listOf(11), DumpProbe.startedSessions(players, ownUid = 1))
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

    // ---- tes teks sumber ----

    @Test
    fun `refresh does nothing unless running, permission is granted and interval allows`() {
        val body = bodyBetween(serviceSource(), "private fun refreshEffectSession(", "private fun readPlayerSessions(")
        val running = body.indexOf("if (!isRunning) return")
        val permission = body.indexOf("ContextCompat.checkSelfPermission(this, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED")
        val interval = body.indexOf("if (!force && !DumpProbe.shouldRun(lastDumpProbeAtMs, now)) return")
        val busy = body.indexOf("dumpProbeBusy.compareAndSet(false, true)")
        assertTrue(running in 0 until permission)
        assertTrue("izin dicek sebelum jeda & proses", permission < interval && interval < busy)
        assertTrue("dump berjalan di IO, bukan main thread", serviceSource().contains("kotlinx.coroutines.Dispatchers.IO"))
    }

    @Test
    fun `probe only reads players and feeds the effect session follower`() {
        val body = bodyBetween(serviceSource(), "private fun readPlayerSessions(", "private fun followEffectSession(")
        assertTrue(body.contains("followEffectSession(started)"))
        for (forbidden in listOf("bassBoost", "virtualizer", "equalizer", "dynamicsProcessing", "loudnessEnhancer", "PresetReverb")) {
            assertFalse("probe tidak boleh menyentuh: $forbidden", body.contains(forbidden))
        }
    }

    @Test
    fun `probe is scheduled from player changes and cancelled before effects are released`() {
        val src = serviceSource()
        val onDestroy = bodyBetween(src, "override fun onDestroy()", "/** Menempel ke audio session 0")
        assertTrue("scope dibatalkan SEBELUM efek dilepas", onDestroy.indexOf("dumpProbeScope.cancel()") in 0 until onDestroy.indexOf("releaseEffects()"))
        val callback = bodyBetween(src, "private fun onPlaybackConfigsChanged(", "// ---- Batch 247")
        assertTrue(callback.contains("scheduleEffectSessionProbe()"))
        assertEquals("satu-satunya penjadwal runnable", 1, Regex("""postDelayed\(dumpProbeRunnable""").findAll(src).count())
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
