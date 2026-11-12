package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 312 — bagian murni FirstDrawGate (jaminan sekali jalan). `View`/`Handler` tak bisa dites di JVM. */
class FirstDrawGateTest {

    @Test
    fun `once runs the action only for the first fire and reports it`() {
        val reasons = mutableListOf<String>()
        val once = FirstDrawGate.Once { reasons += it }
        assertTrue(once.fire(FirstDrawGate.REASON_AFTER_FIRST_DRAW))
        assertFalse(once.fire(FirstDrawGate.REASON_FALLBACK))
        assertFalse(once.fire(FirstDrawGate.REASON_AFTER_FIRST_DRAW))
        assertEquals(listOf("sesudah-frame-pertama"), reasons)
    }

    @Test
    fun `fallback fire runs the action when the draw never happened`() {
        val reasons = mutableListOf<String>()
        val once = FirstDrawGate.Once { reasons += it }
        assertTrue(once.fire(FirstDrawGate.REASON_FALLBACK))
        assertEquals(listOf("cadangan"), reasons)
    }
}
