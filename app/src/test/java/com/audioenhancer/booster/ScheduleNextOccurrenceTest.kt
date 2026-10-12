package com.audioenhancer.booster

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Batch 207: tes murni (tanpa Robolectric) untuk `ScheduleWorker.nextOccurrence` dengan hari aktif.
 * Zona waktu dikunci UTC supaya tak bergantung runner. Acuan kalender: 12 Okt 2026 = Senin.
 */
class ScheduleNextOccurrenceTest {

    private lateinit var originalZone: TimeZone

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    private fun at(day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        }.timeInMillis

    private val mondayMorning get() = at(12, 8, 0)

    @Test
    fun `semua hari berperilaku seperti jadwal harian lama`() {
        assertEquals(at(13, 7, 0), ScheduleWorker.nextOccurrence(mondayMorning, 7 * 60))
        assertEquals(at(12, 22, 0), ScheduleWorker.nextOccurrence(mondayMorning, 22 * 60))
    }

    @Test
    fun `hanya Rabu melompat ke Rabu terdekat`() {
        val wednesdayOnly = 1 shl 3
        assertEquals(at(14, 7, 0), ScheduleWorker.nextOccurrence(mondayMorning, 7 * 60, wednesdayOnly))
    }

    @Test
    fun `hanya Senin yang jamnya sudah lewat jatuh ke Senin pekan depan`() {
        val mondayOnly = 1 shl 1
        assertEquals(at(19, 7, 0), ScheduleWorker.nextOccurrence(mondayMorning, 7 * 60, mondayOnly))
    }

    @Test
    fun `hanya Minggu jatuh ke Minggu terdekat`() {
        val sundayOnly = 1 shl 0
        assertEquals(at(18, 7, 0), ScheduleWorker.nextOccurrence(mondayMorning, 7 * 60, sundayOnly))
    }

    @Test
    fun `event mati jendela lewat tengah malam mengikuti hari event nyala`() {
        val mondayOnly = 1 shl 1
        // Nyala Senin 22:00 -> mati Selasa 06:00: Selasa digeser -1 hari = Senin (aktif).
        assertEquals(at(13, 6, 0), ScheduleWorker.nextOccurrence(mondayMorning, 6 * 60, mondayOnly, dayShift = -1))
    }

    @Test
    fun `mask nol diperlakukan semua hari`() {
        assertEquals(at(13, 7, 0), ScheduleWorker.nextOccurrence(mondayMorning, 7 * 60, 0))
    }
}
