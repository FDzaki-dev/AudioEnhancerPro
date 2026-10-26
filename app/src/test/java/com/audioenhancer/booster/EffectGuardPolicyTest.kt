package com.audioenhancer.booster

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
}
