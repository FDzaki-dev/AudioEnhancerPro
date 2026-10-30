package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 257 — 5 efek utama diikat ke sesi pemutar yang sedang main (bukan hanya session 0) supaya mengikuti track ke thread keluaran mana pun
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
    fun `main effects bind to effectSession while reverb aux and spectrum stay on session 0`() {
        val src = code(serviceSource)
        assertTrue(src.contains("BassBoost(EffectGuardPolicy.EFFECT_PRIORITY, effectSession)"))
        assertTrue(src.contains("Virtualizer(EffectGuardPolicy.EFFECT_PRIORITY, effectSession)"))
        assertTrue(src.contains("Equalizer(EffectGuardPolicy.EFFECT_PRIORITY, effectSession)"))
        assertTrue(src.contains("LoudnessEnhancer(effectSession)"))
        assertTrue(src.contains("DynamicsProcessing(EffectGuardPolicy.EFFECT_PRIORITY, effectSession, config)"))
        assertTrue("Reverb aux tetap di session 0", src.contains("PresetReverb(EffectGuardPolicy.EFFECT_PRIORITY, 0)"))
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
    fun `probe feeds the follower and runs for main effects even when reverb is off except for slider`() {
        val src = code(serviceSource)
        val read = between(src, "private fun readPlayerSessions(", "private fun followEffectSession(")
        assertTrue(read.contains("followEffectSession(DumpProbe.startedSessions(players, android.os.Process.myUid()))"))
        val refresh = between(src, "private fun refreshSessionReverb(", "private fun readPlayerSessions(")
        assertTrue(refresh.contains("val reverbActive = ReverbPresets.isActive(index)"))
        assertTrue("Mati dari geser slider tidak memicu dump", refresh.contains("if (reason == \"slider\") return"))
        assertTrue("Reverb hanya dipasang bila aktif", refresh.contains("if (sessions != null && reverbActive) applySessionReverbs(reason, sessions)"))
        assertTrue("pesan 'Reverb nonaktif' hanya bila Reverb memang aktif", refresh.contains("if (reverbActive && !dumpProbeDeniedLogged)"))
    }
}
