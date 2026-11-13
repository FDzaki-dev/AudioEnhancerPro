package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 318 — penjaga penyapu modul efek yatim (`OrphanEffects`, `AudioEnhancerService.startOrphanSweep`).
 *
 * Bagian 1 (JVM murni): parser diuji dengan bentuk blok ASLI dari `dumpsys media.audio_flinger` pada log device
 * `aep_debug_20261113_073815` (Infinix X6855, Android 16): rantai sesi 2457 milik proses 12175 yang sudah mati tetap ada dengan
 * `0 Clients` + `Enabled y`, berdampingan dengan rantai session 0 proses baru (`1 Clients`). Bagian 2 (tes teks sumber): penyapu hanya jalan
 * dengan izin DUMP, ikatan sesi menunggu penyapu, dan efek proses ini tak disentuh.
 */
class OrphanEffectsTest {

    private fun block(id: Int, session: Int, enabled: String, type: String, name: String, clients: Int): List<String> = listOf(
        "\tEffect ID $id:",
        "\t\tSession State Registered Internal Enabled Suspended:",
        "\t\t${session.toString().padStart(5, '0')}   003   n          y        $enabled       n",
        "\t\tDescriptor:",
        "\t\t- UUID: 8631f300-72e2-11df-b57e-0002a5d5c51b",
        "\t\t- TYPE: $type",
        "\t\t- apiVersion: 00020000",
        "\t\t- flags: 00000248 (conn. mode: insert, insert pref: first, volume mgmt: implements control)",
        "\t\t- name: $name",
        "\t\t- implementor: NXP Software Ltd.",
        "\t\t$clients Clients:",
        "\t\t\t  Pid Priority Ctrl Locked client server",
        "\t\tStatus Engine:",
        "\t\t000    0xb40000723be216c0",
        "\t\t- data: float",
        "\tEffect ID $id HAL dump:"
    )

    private val bass = "0634f220-ddd4-11db-a0fc-0002a5d5c51b"
    private val equalizer = "0bed4300-ddd6-11db-8f34-0002a5d5c51b"
    private val loudness = "fe3199be-aed0-413f-87bb-11260eb63cf1"
    private val dynamics = "7261676f-6d75-7369-6364-28e2fd3ac39e"
    private val dts = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    private val dump: List<String> =
        listOf("Output thread 0xb400007388b636b0, name AudioOut_D, tid 1693, type 0 (MIXER):", "  1 Effect Chains", "    2 effects for session 0",
            "\tIn buffer         Out buffer                                 Active tracks:") +
            block(4755, 0, "y", bass, "Dynamic Bass Boost", 1) +
            block(4787, 0, "y", dynamics, "DynamicsProcessing", 1) +
            listOf("Output thread 0xb40000738878a6b0, name AudioOut_15, tid 1719, type 0 (MIXER):", "  1 Effect Chains", "    5 effects for session 2457",
                "\tIn buffer         Out buffer                                 Active tracks:") +
            block(4635, 2457, "y", equalizer, "EqualizerBundle", 0) +
            block(4723, 2457, "y", bass, "Dynamic Bass Boost", 0) +
            block(4739, 2457, "y", loudness, "Loudness Enhancer", 0) +
            block(4747, 2457, "y", dynamics, "DynamicsProcessing", 0) +
            block(4800, 2457, "n", bass, "Dynamic Bass Boost", 0) +
            listOf("    1 effects for session 3001") +
            block(4900, 3001, "y", loudness, "Loudness Enhancer", 1) +
            listOf("    1 effects for session 3002") +
            block(4901, 3002, "y", dts, "DTS Audio", 0)

    @Test
    fun `parse hanya mengambil modul Boomly di sesi pemutar tanpa klien dan menyala`() {
        val orphans = OrphanEffects.parse(dump.asSequence())
        assertEquals(listOf(4635, 4723, 4739, 4747), orphans.map { it.effectId })
        assertTrue(orphans.all { it.session == 2457 })
        assertEquals(
            listOf(OrphanEffects.Kind.EQUALIZER, OrphanEffects.Kind.BASS, OrphanEffects.Kind.LOUDNESS, OrphanEffects.Kind.DYNAMICS),
            orphans.map { it.kind }
        )
    }

    @Test
    fun `rantai session 0 modul berklien modul mati dan jenis asing tidak dianggap yatim`() {
        val orphans = OrphanEffects.parse(dump.asSequence())
        assertFalse("session 0 (global) bukan yatim", orphans.any { it.session == 0 })
        assertFalse("modul berklien bukan yatim", orphans.any { it.effectId == 4900 })
        assertFalse("modul mati (Enabled n) dilewati", orphans.any { it.effectId == 4800 })
        assertFalse("jenis efek asing dilewati", orphans.any { it.effectId == 4901 })
    }

    @Test
    fun `dump kosong atau tanpa rantai efek menghasilkan daftar kosong`() {
        assertTrue(OrphanEffects.parse(emptySequence()).isEmpty())
        assertTrue(OrphanEffects.parse(sequenceOf("Output thread x", "  0 Effect Chains")).isEmpty())
    }

    @Test
    fun `kindOf tidak peka huruf besar dan menolak uuid asing`() {
        assertEquals(OrphanEffects.Kind.BASS, OrphanEffects.kindOf(bass.uppercase()))
        assertEquals(null, OrphanEffects.kindOf(dts))
    }

    @Test
    fun `describe memuat sesi nama dan id`() {
        assertEquals("sesi=2457 BassBoost id=4723", OrphanEffects.describe(OrphanEffects.Orphan(2457, 4723, OrphanEffects.Kind.BASS)))
    }

    // ---- Bagian 2: tes teks sumber ----

    private fun serviceSource(): String {
        val relative = "java/com/audioenhancer/booster/AudioEnhancerService.kt"
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        return checkNotNull(candidates.firstOrNull { it.isFile }) { "Berkas tidak ditemukan: $relative" }.readText()
    }

    private fun bodyBetween(source: String, start: String, end: String): String {
        val s = source.indexOf(start)
        assertTrue("Penanda awal tidak ditemukan: $start", s >= 0)
        val e = source.indexOf(end, s + start.length)
        assertTrue("Penanda akhir tidak ditemukan: $end", e > s)
        return source.substring(s, e).replace(Regex("//[^\n]*"), "")
    }

    @Test
    fun `penyapu dimulai dari onCreate setelah penjaga pemutaran dan hanya dengan izin DUMP`() {
        val source = serviceSource()
        val create = bodyBetween(source, "override fun onCreate()", "override fun onStartCommand(")
        assertTrue(create.indexOf("registerPlaybackGuard()") in 0 until create.indexOf("startOrphanSweep("))
        val start = bodyBetween(source, "private fun startOrphanSweep(", "private suspend fun runOrphanSweep(")
        assertTrue(start.contains("DumpProbe.PERMISSION"))
        assertTrue(start.contains("orphanSweepActive.compareAndSet(false, true)"))
        assertTrue(start.contains("orphanSweepActive.set(false)"))
        assertTrue("probe sesi diulang sesudah penyapu bila ikatan sempat tertunda", start.contains("refreshEffectSession(\"pasca-sapu\", force = true)"))
    }

    @Test
    fun `ikatan sesi menunggu penyapu dan tidak berebut modul`() {
        val follow = bodyBetween(serviceSource(), "private fun followEffectSession(", "private fun <T> readDump(")
        assertTrue(follow.indexOf("orphanSweepActive.get()") in 0 until follow.indexOf("DumpProbe.chooseEffectSession(started)"))
        assertTrue(follow.contains("orphanSweepDeferredFollow = true"))
    }

    @Test
    fun `objek sementara selalu dilepas dan efek proses ini tidak disentuh`() {
        val neutralize = bodyBetween(serviceSource(), "private fun neutralizeOrphan(", "private fun <T> readDump(")
        assertTrue(neutralize.contains("finally"))
        assertTrue(neutralize.contains("fx?.release()"))
        assertFalse("penyapu tak boleh menulis field efek milik proses ini", Regex("""\b(bassBoost|virtualizer|equalizer|loudnessEnhancer|dynamicsProcessing)\b\s*=""").containsMatchIn(neutralize))
        val run = bodyBetween(serviceSource(), "private suspend fun runOrphanSweep(", "private fun neutralizeOrphan(")
        assertTrue("hanya mulai dari percobaan terbatas", run.contains("OrphanEffects.MAX_ATTEMPTS"))
        assertTrue("verifikasi ulang dump setelah menyapu", run.contains("sisa ${'$'}{rest.size} modul yatim"))
    }
}
