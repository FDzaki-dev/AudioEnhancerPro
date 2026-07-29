package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-Kotlin test, tanpa Robolectric — untuk fungsi murni tanpa dependency Android.
 */
class FormatFreqLabelTest {

    @Test
    fun `frequencies under 1000 Hz show as plain Hz`() {
        assertEquals("60 Hz", formatFreqLabel(60))
        assertEquals("250 Hz", formatFreqLabel(250))
        assertEquals("999 Hz", formatFreqLabel(999))
    }

    @Test
    fun `frequencies at or above 1000 Hz show as kHz`() {
        assertEquals("1 kHz", formatFreqLabel(1000))
        assertEquals("4 kHz", formatFreqLabel(4000))
        assertEquals("16 kHz", formatFreqLabel(16000))
    }
}
