package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 229 — Pure-Kotlin test, tanpa Robolectric: menjaga aturan Effect Guard (`EffectGuardPolicy`) yang
 * memulihkan efek session-0 saat user pindah ke aplikasi lain (mis. YouTube). Yang dijaga: backoff rebut-ulang
 * kontrol naik bertahap dan berhenti di batas, pembatas intervensi per jendela waktu (tidak adu tarik tanpa akhir),
 * toleransi pembulatan device saat membandingkan parameter, jendela "user sedang menggeser slider", dan tanda
 * tangan pemutaran yang stabil.
 */
class EffectGuardPolicyTest {

    @Test
    fun `reacquire interval doubles per failure and stops at the cap`() {
        assertEquals(5_000L, EffectGuardPolicy.reacquireIntervalMs(0))
        assertEquals(10_000L, EffectGuardPolicy.reacquireIntervalMs(1))
        assertEquals(20_000L, EffectGuardPolicy.reacquireIntervalMs(2))
        assertEquals(40_000L, EffectGuardPolicy.reacquireIntervalMs(3))
        assertEquals(80_000L, EffectGuardPolicy.reacquireIntervalMs(4))
        assertEquals(EffectGuardPolicy.MAX_REACQUIRE_INTERVAL_MS, EffectGuardPolicy.reacquireIntervalMs(5))
        assertEquals(EffectGuardPolicy.MAX_REACQUIRE_INTERVAL_MS, EffectGuardPolicy.reacquireIntervalMs(50))
    }

    @Test
    fun `negative failure count is treated as zero`() {
        assertEquals(EffectGuardPolicy.MIN_REACQUIRE_INTERVAL_MS, EffectGuardPolicy.reacquireIntervalMs(-3))
    }

    @Test
    fun `first reacquire is always allowed`() {
        assertTrue(EffectGuardPolicy.mayReacquire(nowMs = 0L, lastAttemptMs = EffectGuardPolicy.NEVER, consecutiveFailures = 9))
    }

    @Test
    fun `reacquire waits for the backoff interval`() {
        val last = 10_000L
        assertFalse(EffectGuardPolicy.mayReacquire(last + 4_999L, last, 0))
        assertTrue(EffectGuardPolicy.mayReacquire(last + 5_000L, last, 0))
        assertFalse(EffectGuardPolicy.mayReacquire(last + 9_999L, last, 1))
        assertTrue(EffectGuardPolicy.mayReacquire(last + 10_000L, last, 1))
    }

    @Test
    fun `interventions are allowed up to the cap inside one window`() {
        var start = EffectGuardPolicy.NEVER
        var count = 0
        val t0 = 1_000L
        for (i in 1..EffectGuardPolicy.REASSERT_MAX_PER_WINDOW) {
            val d = EffectGuardPolicy.reassertDecision(t0 + i, start, count)
            assertTrue("intervensi ke-$i harus diizinkan", d.allowed)
            assertEquals(i, d.count)
            start = d.windowStartMs
            count = d.count
        }
        val denied = EffectGuardPolicy.reassertDecision(t0 + 100L, start, count)
        assertFalse(denied.allowed)
        assertEquals(EffectGuardPolicy.REASSERT_MAX_PER_WINDOW, denied.count)
        assertEquals(start, denied.windowStartMs)
    }

    @Test
    fun `window resets after it expires`() {
        val start = 5_000L
        val atExpiry = start + EffectGuardPolicy.REASSERT_WINDOW_MS
        val d = EffectGuardPolicy.reassertDecision(atExpiry, start, EffectGuardPolicy.REASSERT_MAX_PER_WINDOW)
        assertTrue(d.allowed)
        assertEquals(1, d.count)
        assertEquals(atExpiry, d.windowStartMs)
    }

    @Test
    fun `first decision opens a window at the current time`() {
        val d = EffectGuardPolicy.reassertDecision(42_000L, EffectGuardPolicy.NEVER, 0)
        assertTrue(d.allowed)
        assertEquals(1, d.count)
        assertEquals(42_000L, d.windowStartMs)
    }

    @Test
    fun `strength tolerance absorbs device rounding but catches a reset`() {
        assertFalse(EffectGuardPolicy.strengthDiffers(800, 800))
        assertFalse(EffectGuardPolicy.strengthDiffers(800, 800 - EffectGuardPolicy.STRENGTH_TOLERANCE))
        assertTrue(EffectGuardPolicy.strengthDiffers(800, 800 - EffectGuardPolicy.STRENGTH_TOLERANCE - 1))
        assertTrue(EffectGuardPolicy.strengthDiffers(800, 0))
    }

    @Test
    fun `eq tolerance absorbs rounding but catches a flat reset`() {
        assertFalse(EffectGuardPolicy.eqLevelDiffers(300, 250))
        assertFalse(EffectGuardPolicy.eqLevelDiffers(300, 300 - EffectGuardPolicy.EQ_TOLERANCE_MB))
        assertTrue(EffectGuardPolicy.eqLevelDiffers(300, 300 - EffectGuardPolicy.EQ_TOLERANCE_MB - 1))
        assertTrue(EffectGuardPolicy.eqLevelDiffers(300, 0))
        assertTrue(EffectGuardPolicy.eqLevelDiffers(-600, 0))
    }

    @Test
    fun `loudness tolerance is tight and catches a reset`() {
        assertFalse(EffectGuardPolicy.loudnessDiffers(1500f, 1500f))
        assertFalse(EffectGuardPolicy.loudnessDiffers(1500f, 1499.5f))
        assertTrue(EffectGuardPolicy.loudnessDiffers(1500f, 0f))
    }

    @Test
    fun `quiet period covers a short span after the last user adjustment only`() {
        val last = 10_000L
        assertFalse(EffectGuardPolicy.isQuietPeriod(last + 100L, EffectGuardPolicy.NEVER))
        assertTrue(EffectGuardPolicy.isQuietPeriod(last, last))
        assertTrue(EffectGuardPolicy.isQuietPeriod(last + EffectGuardPolicy.USER_ADJUST_QUIET_MS - 1L, last))
        assertFalse(EffectGuardPolicy.isQuietPeriod(last + EffectGuardPolicy.USER_ADJUST_QUIET_MS, last))
    }

    @Test
    fun `playback signature is empty when nothing plays`() {
        assertEquals("", EffectGuardPolicy.playbackSignature(emptyList()))
    }

    @Test
    fun `playback signature ignores player order`() {
        val a = EffectGuardPolicy.playbackSignature(listOf(1 to 2, 1 to 3))
        val b = EffectGuardPolicy.playbackSignature(listOf(1 to 3, 1 to 2))
        assertEquals(a, b)
        assertEquals("1/2,1/3", a)
    }

    @Test
    fun `switching from music to video changes the signature`() {
        val music = EffectGuardPolicy.playbackSignature(listOf(1 to 2))
        val video = EffectGuardPolicy.playbackSignature(listOf(1 to 3))
        assertFalse(music == video)
    }

    // ---- Batch 230: pasang ulang efek saat pemutar media baru mulai ----

    @Test
    fun `rebind when a media player starts after silence`() {
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("", "1/3"))
    }

    @Test
    fun `rebind when a new media player joins one that is still listed`() {
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("1/2", "1/2,1/3"))
    }

    @Test
    fun `rebind when a media player swaps for a different one in one step`() {
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("1/2", "1/3"))
    }

    @Test
    fun `rebind when a second player with identical attributes appears`() {
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("1/3", "1/3,1/3"))
    }

    @Test
    fun `no rebind when playback ends completely`() {
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/3", ""))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/2,1/3", ""))
    }

    @Test
    fun `no rebind for notification alarm ringtone or game sounds`() {
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("", "5/4"))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("", "4/4"))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("", "6/4"))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("", "14/2"))
    }

    @Test
    fun `unknown usage counts as media for rebind`() {
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("", "0/0"))
    }

    @Test
    fun `malformed signature entries are ignored without crashing`() {
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("", "abc,/3,n=2"))
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("abc", "abc,1/3"))
    }

    @Test
    fun `no rebind when media players are unchanged`() {
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/3", "1/3"))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/3", "1/3,5/4"))
    }

    @Test
    fun `rebind is allowed when effects were never bound`() {
        assertTrue(EffectGuardPolicy.mayRebind(nowMs = 0L, lastBindMs = EffectGuardPolicy.NEVER))
    }

    @Test
    fun `rebind waits for the minimum interval since the last bind`() {
        val last = 10_000L
        assertFalse(EffectGuardPolicy.mayRebind(last, last))
        assertFalse(EffectGuardPolicy.mayRebind(last + EffectGuardPolicy.REBIND_MIN_INTERVAL_MS - 1, last))
        assertTrue(EffectGuardPolicy.mayRebind(last + EffectGuardPolicy.REBIND_MIN_INTERVAL_MS, last))
    }

    @Test
    fun `rebind delay is shorter than the settle recheck so the recheck verifies the new effects`() {
        assertTrue(EffectGuardPolicy.REBIND_DELAY_MS > EffectGuardPolicy.DEBOUNCE_MS)
        assertTrue(EffectGuardPolicy.REBIND_DELAY_MS < EffectGuardPolicy.SETTLE_RECHECK_MS)
    }

    // ---- Batch 231: priority efek + pemeriksaan berkala ----

    @Test
    fun `media player is detected from the playback signature`() {
        assertTrue(EffectGuardPolicy.hasMediaPlayer("1/3"))
        assertTrue(EffectGuardPolicy.hasMediaPlayer("5/4,1/2"))
        assertTrue(EffectGuardPolicy.hasMediaPlayer("0/0"))
    }

    @Test
    fun `no media player for silence or non media usages`() {
        assertFalse(EffectGuardPolicy.hasMediaPlayer(""))
        assertFalse(EffectGuardPolicy.hasMediaPlayer("5/4"))
        assertFalse(EffectGuardPolicy.hasMediaPlayer("14/2,6/4"))
        assertFalse(EffectGuardPolicy.hasMediaPlayer("n=2"))
    }

    @Test
    fun `effect priority is above the normal priority of zero`() {
        assertTrue(EffectGuardPolicy.EFFECT_PRIORITY > 0)
    }

    @Test
    fun `periodic guard tick is slower than the event driven checks`() {
        assertTrue(EffectGuardPolicy.GUARD_TICK_MS > EffectGuardPolicy.SETTLE_RECHECK_MS)
        assertTrue(EffectGuardPolicy.GUARD_TICK_MS > EffectGuardPolicy.USER_ADJUST_QUIET_MS)
    }

    @Test
    fun `tick reason is stable so healthy ticks can stay silent`() {
        assertEquals("tick", EffectGuardPolicy.TICK_REASON)
    }

    // ---- Batch 232: pasang ulang ikut perubahan pemutar & ditunda (bukan dibuang) ----

    @Test
    fun `rebind when one media player stops but another is still playing`() {
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("1/2,1/3", "1/2"))
        assertTrue(EffectGuardPolicy.shouldRebindOnPlayback("1/3,1/3", "1/3"))
    }

    @Test
    fun `no rebind when only a non media sound leaves or joins`() {
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/3,5/4", "1/3"))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/3", "1/3,5/4"))
        assertFalse(EffectGuardPolicy.shouldRebindOnPlayback("1/3,5/4", "1/3,6/4"))
    }

    @Test
    fun `no rebind wait when effects were never bound`() {
        assertEquals(0L, EffectGuardPolicy.rebindWaitMs(nowMs = 0L, lastBindMs = EffectGuardPolicy.NEVER))
    }

    @Test
    fun `rebind wait is the remainder of the minimum interval`() {
        val last = 10_000L
        assertEquals(EffectGuardPolicy.REBIND_MIN_INTERVAL_MS, EffectGuardPolicy.rebindWaitMs(last, last))
        assertEquals(
            EffectGuardPolicy.REBIND_MIN_INTERVAL_MS - 1_000L,
            EffectGuardPolicy.rebindWaitMs(last + 1_000L, last)
        )
        assertEquals(1L, EffectGuardPolicy.rebindWaitMs(last + EffectGuardPolicy.REBIND_MIN_INTERVAL_MS - 1L, last))
    }

    @Test
    fun `rebind wait is zero once the interval has passed and never negative`() {
        val last = 10_000L
        assertEquals(0L, EffectGuardPolicy.rebindWaitMs(last + EffectGuardPolicy.REBIND_MIN_INTERVAL_MS, last))
        assertEquals(0L, EffectGuardPolicy.rebindWaitMs(last + EffectGuardPolicy.REBIND_MIN_INTERVAL_MS * 10, last))
    }

    @Test
    fun `rebind wait agrees with mayRebind`() {
        val last = 10_000L
        for (offset in listOf(0L, 1L, 999L, 4_999L, 5_000L, 5_001L, 30_000L)) {
            val now = last + offset
            assertEquals(
                "offset=$offset",
                EffectGuardPolicy.mayRebind(now, last),
                EffectGuardPolicy.rebindWaitMs(now, last) == 0L
            )
        }
    }

    @Test
    fun `minimum rebind interval is short enough to follow social media churn but longer than the settle delay`() {
        assertTrue(EffectGuardPolicy.REBIND_MIN_INTERVAL_MS > EffectGuardPolicy.REBIND_DELAY_MS)
        assertTrue(EffectGuardPolicy.REBIND_MIN_INTERVAL_MS < EffectGuardPolicy.GUARD_TICK_MS)
    }

    // ---- Batch 233: serah-terima efek tanpa celah ----

    @Test
    fun `handoff adopts the new effect whenever it was created`() {
        assertEquals(EffectGuardPolicy.HandoffOutcome.ADOPT_NEW, EffectGuardPolicy.handoffOutcome(hadOld = true, createdNew = true))
        assertEquals(EffectGuardPolicy.HandoffOutcome.ADOPT_NEW, EffectGuardPolicy.handoffOutcome(hadOld = false, createdNew = true))
    }

    @Test
    fun `handoff keeps the old effect when the new one failed to build`() {
        assertEquals(EffectGuardPolicy.HandoffOutcome.KEEP_OLD, EffectGuardPolicy.handoffOutcome(hadOld = true, createdNew = false))
    }

    @Test
    fun `handoff has nothing to do when the device has no such effect`() {
        assertEquals(EffectGuardPolicy.HandoffOutcome.NONE, EffectGuardPolicy.handoffOutcome(hadOld = false, createdNew = false))
    }

    private fun readServiceSource(): String {
        val relative = "src/main/java/com/audioenhancer/booster/AudioEnhancerService.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    /** Penjaga urutan (tes teks sumber, karena AudioEffect tak bisa dijalankan di JVM murni): efek BARU dibuat tanpa
     *  menyala, nilai tersimpan diterapkan, baru dinyalakan, dan efek LAMA dilepas paling akhir. Urutan terbalik =
     *  kembali ke celah "mendem" + lonjakan B230-B232. */
    @Test
    fun `rebind builds and enables new effects before releasing old ones`() {
        val source = readServiceSource()
        val start = source.indexOf("private fun runEffectRebind(")
        assertTrue("runEffectRebind tidak ditemukan", start >= 0)
        val end = source.indexOf("private fun releaseReplaced(", start)
        assertTrue("releaseReplaced tidak ditemukan sesudah runEffectRebind", end > start)
        val body = withoutLineComments(source.substring(start, end))

        val attach = listOf(
            "attachBass(enableNow = false)",
            "attachVirtualizer(enableNow = false)",
            "attachEqualizer(enableNow = false)",
            "attachLoudness(enableNow = false)",
            "attachDynamicsProcessing(enableNow = false)"
        ).map { body.indexOf(it) }
        assertTrue("kelima efek baru harus dibuat tanpa menyala: $attach", attach.all { it >= 0 })
        assertEquals("urutan attach harus tetap (Equalizer sebelum DynamicsProcessing)", attach.sorted(), attach)

        val restore = body.indexOf("applySavedToEffects()") // Batch 234: dulu restoreSavedSettings()
        val enable = body.indexOf("enableMixEffects()")
        val release = body.indexOf("releaseReplaced(")
        assertTrue("urutan: attach < restore < enable < release (attach=$attach restore=$restore enable=$enable release=$release)",
            attach.last() < restore && restore < enable && enable < release)
        assertFalse("efek lama tidak boleh dilepas langsung sebelum pembuatan efek baru", body.contains(".release()"))
    }

    @Test
    fun `every effect listener ignores callbacks from stale instances`() {
        val source = readServiceSource()
        assertEquals(5, Regex("return@setControlStatusListener").findAll(source).count())
        assertEquals(5, Regex("return@setEnableStatusListener").findAll(source).count())
    }

    // ---- Batch 234: penutupan celah sisa serah-terima efek ----

    /** Buang komentar baris (`// ...`): penjaga teks sumber harus menilai KODE, bukan komentar yang kebetulan menyebut
     *  nama fungsi lama (mis. alasan kenapa `restoreSavedSettings()` tidak dipakai lagi). Badan yang diperiksa tidak memuat
     *  `//` di dalam string. */
    private fun withoutLineComments(text: String): String =
        text.lines().joinToString("\n") { it.substringBefore("//") }

    /** Potong badan fungsi dari penanda awal sampai penanda akhir pada teks sumber Service (tes teks sumber), tanpa komentar baris. */
    private fun bodyBetween(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        assertTrue("$startMarker tidak ditemukan", start >= 0)
        val end = source.indexOf(endMarker, start + startMarker.length)
        assertTrue("$endMarker tidak ditemukan sesudah $startMarker", end > start)
        return withoutLineComments(source.substring(start, end))
    }

    /** Serah-terima efek hanya boleh menulis ke efek. `restoreSavedSettings()` memanggil setter publik yang menulis ulang
     *  SharedPreferences dan menandai "user sedang menggeser" (`markSettingAdjusted`) — itu menunda pasang-ulang berikutnya
     *  dan pemeriksaan drift selama jendela tenang, tepat saat perpindahan pemutar beruntun butuh respons cepat. */
    @Test
    fun `handoff paths apply saved values to effects only`() {
        val source = readServiceSource()
        val apply = bodyBetween(source, "private fun applySavedToEffects(", "private fun releaseEffects(")
        assertFalse("applySavedToEffects tidak boleh menulis prefs", apply.contains("PrefsHelper.set"))
        assertFalse("applySavedToEffects tidak boleh menandai penyesuaian user", apply.contains("markSettingAdjusted("))

        val rebind = bodyBetween(source, "private fun runEffectRebind(", "private fun releaseReplaced(")
        assertTrue(rebind.contains("applySavedToEffects()"))
        assertFalse("runEffectRebind tidak boleh kembali ke restoreSavedSettings()", rebind.contains("restoreSavedSettings()"))

        val retry = bodyBetween(source, "fun retryControlAcquisition(", "// ---- Batch 229")
        assertTrue(retry.contains("applySavedToEffects()"))
        assertFalse("retryControlAcquisition tidak boleh kembali ke restoreSavedSettings()", retry.contains("restoreSavedSettings()"))
    }

    /** Objek DynamicsProcessing baru bisa memakai modul efek yang sama dgn yang lama (output sama). Menulis band kompresor
     *  netral dulu lalu memulihkannya belakangan = kompresor mati sesaat di tiap serah-terima ("mendem" + lonjakan). */
    @Test
    fun `new dynamics processing is written with saved compressor values and never neutral ones`() {
        val source = readServiceSource()
        val attach = bodyBetween(source, "private fun attachDynamicsProcessing(", "fun retryControlAcquisition(")
        assertTrue(
            "nilai kompresor tersimpan harus ditulis ke objek BARU (this), bukan field yang masih menunjuk objek lama",
            attach.contains("applyCompressorBands(savedCompressor, savedVocalFocus, this)")
        )
        assertFalse("band kompresor netral tidak boleh ditulis lagi", attach.contains("CompressorCurve.band(bandIndex, 0f)"))
        assertFalse("band MBC enabled=false tidak boleh ditulis lagi", attach.contains("/* enabled            = */ false"))
        assertFalse("band presence netral tidak boleh ditulis lagi", attach.contains("DynamicsProcessing.EqBand(false"))
    }

    @Test
    fun `compressor bands are written to the explicit target object`() {
        val source = readServiceSource()
        val body = bodyBetween(source, "private fun applyCompressorBands(", "fun setEqualizerBand(")
        assertTrue(body.contains("target: DynamicsProcessing? = dynamicsProcessing"))
        assertTrue(body.contains("target?.setMbcBandAllChannelsTo("))
        assertTrue(body.contains("target?.setPostEqBandAllChannelsTo("))
        assertFalse("tulis lewat field akan mengenai objek LAMA saat serah-terima", body.contains("dynamicsProcessing?.set"))
    }

    @Test
    fun `rebind interval is short now that the handoff has no gap`() {
        assertTrue(EffectGuardPolicy.REBIND_MIN_INTERVAL_MS <= 3_000L)
        assertTrue(EffectGuardPolicy.REBIND_MIN_INTERVAL_MS > EffectGuardPolicy.REBIND_DELAY_MS)
    }
}
