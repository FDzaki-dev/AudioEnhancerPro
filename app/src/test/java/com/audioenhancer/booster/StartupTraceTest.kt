package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Test

/** Batch 310 — bagian murni penanda waktu startup (format baris log). `Log`/`SystemClock` tak bisa dites di JVM. */
class StartupTraceTest {

    @Test
    fun `mark line carries name, time since process start and thread`() {
        assertEquals("service-onCreate-mulai t=+420ms thread=main", StartupTrace.formatLine("service-onCreate-mulai", 420, null, "main"))
    }

    @Test
    fun `span line also carries the duration`() {
        assertEquals(
            "service-attachEffects t=+431ms durasi=62ms thread=main",
            StartupTrace.formatLine("service-attachEffects", 431, 62, "main")
        )
    }
}
