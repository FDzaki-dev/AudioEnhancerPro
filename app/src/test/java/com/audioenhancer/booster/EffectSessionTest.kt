package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 257 (Batch 270: Reverb dicabut, probe hanya untuk sesi efek utama) — 5 efek utama diikat ke sesi pemutar yang sedang main (bukan hanya session 0) supaya mengikuti track ke thread keluaran mana pun
 * (log v256: efek global hanya di satu thread, track YouTube selalu di deep_buffer; "mendem" = efek tertinggal di output lama).
 *
 * JVM murni: pemilihan sesi (`DumpProbe.chooseEffectSession`/`startedSessions`) + tes teks sumber untuk kontrak yang tak terlihat kompiler.
 */
class EffectSessionTest {

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

    // ---- pemilihan sesi ----

    @Test
    fun `one started session is chosen`() {
        assertEquals(87705, DumpProbe.chooseEffectSession(listOf(87705)))
        assertEquals(87705, DumpProbe.chooseEffectSession(listOf(87705, 87705)))
    }

    @Test
    fun `nothing playing keeps the current binding`() {
        assertNull(DumpProbe.chooseEffectSession(emptyList()))
        assertNull(DumpProbe.chooseEffectSession(listOf(0, -1)))
    }

    @Test
    fun `several started sessions fall back to the global mix`() {
        assertEquals(DumpProbe.GLOBAL_EFFECT_SESSION, DumpProbe.chooseEffectSession(listOf(87705, 84241)))
        assertEquals(0, DumpProbe.GLOBAL_EFFECT_SESSION)
    }

    @Test
    fun `only started foreign media or game players count`() {
        val own = 10001
        val players = listOf(
            DumpProbe.Player(1, 10460, "started", "USAGE_MEDIA", 87705),      // dihitung
            DumpProbe.Player(2, 10553, "paused", "USAGE_MEDIA", 84241),       // jeda: tidak
            DumpProbe.Player(3, 10553, "idle", "USAGE_MEDIA", 84242),         // idle: tidak
            DumpProbe.Player(4, own, "started", "USAGE_MEDIA", 555),          // milik Boomly: tidak
            DumpProbe.Player(5, 10385, "started", "USAGE_ALARM", 777),        // alarm: tidak
            DumpProbe.Player(6, 1000, "started", "USAGE_ASSISTANCE_SONIFICATION", 888), // sonifikasi: tidak
            DumpProbe.Player(7, 10600, "started", "USAGE_GAME", 999),         // game: dihitung
            DumpProbe.Player(8, 10460, "started", "USAGE_MEDIA", 0)           // tanpa sesi: tidak
        )
        assertEquals(listOf(87705, 999), DumpProbe.startedSessions(players, own))
    }

    // ---- tes teks sumber ----

    @Test
    fun `main effects bind to effectSession while only the spectrum stays on session 0`() {
        val src = code(serviceSource)
        assertTrue(src.contains("BassBoost(EffectGuardPolicy.EFFECT_PRIORITY, effectSession)"))
        assertTrue(src.contains("Virtualizer(EffectGuardPolicy.EFFECT_PRIORITY, effectSession)"))
        assertTrue(src.contains("Equalizer(EffectGuardPolicy.EFFECT_PRIORITY, effectSession)"))
        assertTrue(src.contains("LoudnessEnhancer(effectSession)"))
        assertTrue(src.contains("DynamicsProcessing(EffectGuardPolicy.EFFECT_PRIORITY, effectSession, config)"))
        // Batch 270: Reverb DICABUT total (session 0 sejak B269, per-sesi sejak B270); tak boleh muncul lagi.
        assertFalse("tak ada lagi PresetReverb di session 0", src.contains("PresetReverb(EffectGuardPolicy.EFFECT_PRIORITY, 0)"))
        assertTrue("spektrum tetap di session 0", src.contains("Visualizer(0)"))
        assertFalse("tak ada lagi konstruktor efek utama yang terkunci ke 0", src.contains("BassBoost(EffectGuardPolicy.EFFECT_PRIORITY, 0)"))
    }

    @Test
    fun `effectSession is only written by the follower the declaration and release`() {
        val src = code(serviceSource)
        val writes = Regex("""\beffectSession\s*=\s*[^=]""").findAll(src).map { it.range.first }.toList()
        assertEquals("deklarasi + followEffectSession + serah-terima gagal-pindah + releaseEffects", 4, writes.size)
        val follower = between(src, "private fun followEffectSession(", "private fun <T> readDump(")
        assertTrue(follower.contains("guardHandler.post {"))
        assertTrue("hanya saat berjalan dan sesi benar-benar berubah", follower.contains("if (!isRunning || desired == effectSession) return@post"))
        assertTrue(follower.contains("effectSession = desired"))
        assertTrue("memakai serah-terima tanpa celah yang sama", follower.indexOf("runEffectRebind(\"sesi-pemutar\")") > follower.indexOf("effectSession = desired"))
        val release = between(src, "private fun releaseEffects()", "private fun disableEffects()")
        assertTrue(release.contains("effectSession = DumpProbe.GLOBAL_EFFECT_SESSION"))
    }

    @Test
    fun `rebind is skipped for session bound effects except the session move itself`() {
        val src = code(serviceSource)
        val rebind = between(src, "private fun runEffectRebind(", "private fun releaseReplaced(")
        val skip = rebind.indexOf("if (effectSession != DumpProbe.GLOBAL_EFFECT_SESSION && boundEffectSession == effectSession)")
        assertTrue("pelewatan ada di awal serah-terima", skip in 0 until rebind.indexOf("val runnable ="))
        assertTrue(skip < rebind.indexOf("attachBass(enableNow = false)"))
        assertTrue(rebind.contains("EffectRebind[\$reason]: dilewati"))
        // perpindahan yang tertunda lewat runnable lain TIDAK boleh ikut terlewati: skip hanya bila objek sudah terikat ke sesi tujuan
        assertFalse(rebind.contains("reason != \"sesi-pemutar\""))
        assertTrue("sesi terikat dicatat setelah semua efek pindah", rebind.contains("boundEffectSession = effectSession"))
        assertTrue("gagal pindah mengembalikan tujuan", rebind.contains("effectSession = boundEffectSession"))
    }

    @Test
    fun `probe feeds the follower and has no reverb branch`() {
        val src = code(serviceSource)
        val read = between(src, "private fun readPlayerSessions(", "private fun followEffectSession(")
        assertTrue(read.contains("followEffectSession(started)"))
        assertTrue(read.contains("DumpProbe.startedSessions(players, android.os.Process.myUid())"))
        val refresh = between(src, "private fun refreshEffectSession(", "private fun readPlayerSessions(")
        assertFalse("probe tak lagi mengenal Reverb/slider", refresh.contains("reverb", ignoreCase = true) || refresh.contains("slider"))
        assertTrue("pesan izin-tidak-ada dicatat sekali", refresh.contains("if (!dumpProbeDeniedLogged)"))
        assertTrue("probe sesi dijadwalkan dari perubahan pemutar", code(serviceSource).contains("scheduleEffectSessionProbe()"))
    }

    @Test
    fun `no reverb effect code is left in the service`() {
        val src = code(serviceSource)
        for (token in listOf("PresetReverb", "reverbState", "reverbCapable", "sessionReverb", "ReverbPresets", "setReverbPreset", "enableReverb", "attachReverb")) {
            assertFalse("sisa kode Reverb: $token", src.contains(token))
        }
    }

    @Test
    fun `pindah sesi pemutar tidak dibatalkan hanya karena tanda tangan pemutaran kosong`() {
        // Batch 323, log `aep_debug_20261113_190208`: fresh start saat musik sudah main -> probe memindah tujuan ke sesi pemutar, tetapi serah-terima
        // dibatalkan ("tak ada pemutar media aktif") karena callback pemutar belum pernah terpicu; efek tertinggal di sesi 0.
        val src = code(serviceSource)
        val rebind = between(src, "private fun runEffectRebind(", "private fun releaseReplaced(")
        val decl = rebind.indexOf("val pendingPlayerSessionMove = effectSession != DumpProbe.GLOBAL_EFFECT_SESSION && effectSession != boundEffectSession")
        val cancel = rebind.indexOf("if (!pendingPlayerSessionMove && !EffectGuardPolicy.hasMediaPlayer(lastPlaybackSignature))")
        assertTrue("penanda pindah-sesi dihitung sebelum pembatalan", decl >= 0 && cancel > decl)
        assertTrue("pembatalan tetap ada untuk kasus lain", rebind.contains("EffectRebind[\$reason]: dibatalkan (tak ada pemutar media aktif)"))
        assertTrue("pembatalan sesudah pelewatan sesi terikat", rebind.indexOf("dilewati (efek utama terikat sesi") in 0 until cancel)
    }

    // ---- Batch 326: semai tanda tangan pemutaran saat fresh start ----

    @Test
    fun `playback state is seeded only while both callback fields are still empty`() {
        assertTrue(EffectGuardPolicy.shouldSeedPlayback("", ""))
        assertFalse(EffectGuardPolicy.shouldSeedPlayback("1/2", ""))
        assertFalse(EffectGuardPolicy.shouldSeedPlayback("", "123"))
        assertFalse(EffectGuardPolicy.shouldSeedPlayback("1/2", "123"))
    }

    @Test
    fun `seeded media state keeps guard tick alive and ignores an identical first callback`() {
        val seeded = EffectGuardPolicy.playbackSignature(listOf(1 to 2))
        val key = EffectGuardPolicy.playerKey(listOf(4711))
        assertTrue(EffectGuardPolicy.hasMediaPlayer(seeded))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback(seeded, seeded))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayers(key, key))
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayers(key, EffectGuardPolicy.playerKey(listOf(4711, 4712))))
        assertFalse(EffectGuardPolicy.hasMediaPlayer(EffectGuardPolicy.playbackSignature(emptyList())))
    }

    @Test
    fun `onCreate seeds playback state right after registering the playback guard`() {
        assertTrue(Regex("""registerPlaybackGuard\(\)\s*\n\s*seedPlaybackState\(\)""").containsMatchIn(code(serviceSource)))
    }

    @Test
    fun `seed only fills the two playback fields without scheduling any guard work`() {
        val body = between(code(serviceSource), "private fun seedPlaybackState()", "private fun onPlaybackConfigsChanged")
        assertTrue(body.contains("activePlaybackConfigurations"))
        assertTrue(body.contains("lastPlaybackSignature = signature"))
        assertTrue(body.contains("lastPlayerKey = playerKey"))
        for (forbidden in listOf("requestEffectGuard", "runEffectRebind", "rebindRunnable", "scheduleEffectSessionProbe", "updateGuardTick", "postDelayed")) {
            assertFalse("seed tak boleh memanggil $forbidden", body.contains(forbidden))
        }
    }

    // ---- Batch 328: bukti tick hidup ----

    @Test
    fun `only the first tick of a run is logged`() {
        assertTrue(EffectGuardPolicy.shouldLogTick(1))
        assertFalse(EffectGuardPolicy.shouldLogTick(0))
        assertFalse(EffectGuardPolicy.shouldLogTick(2))
        assertFalse(EffectGuardPolicy.shouldLogTick(21))
    }

    @Test
    fun `tick counter is counted logged and reset where the run ends or restarts`() {
        val src = code(serviceSource)
        assertEquals(1, Regex("""guardTickCount\+\+""").findAll(src).count())
        assertTrue(src.contains("EffectGuardPolicy.shouldLogTick(guardTickCount)"))
        assertTrue("reset: sesi baru + rangkaian berhenti + tick tanpa pemutar", Regex("""guardTickCount = 0""").findAll(src).count() >= 4)
    }
}
