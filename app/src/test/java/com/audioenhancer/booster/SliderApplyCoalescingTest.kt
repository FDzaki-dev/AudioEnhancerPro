package com.audioenhancer.booster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 279: penjaga (tes teks sumber, ViewModel butuh Android) agar slider Bass/Virtualizer/Loudness/Compressor/Vocal Focus TIDAK kembali
 * memanggil service langsung di thread pemanggil (Main) tiap notch drag. Pola B189 (EQ): nilai terbaru ditaruh di slot, worker tunggal
 * Dispatchers.IO menerapkannya. Kembali ke panggilan langsung = UI tersendat saat slider digeser (6 panggilan binder per notch Compressor).
 */
class SliderApplyCoalescingTest {
    private val sliders = listOf(
        Triple("setBass", "setBassStrength", "latestBass"),
        Triple("setVirtualizer", "setVirtualizerStrength", "latestVirtualizer"),
        Triple("setLoudness", "setLoudnessGain", "latestLoudness"),
        Triple("setCompressor", "setCompressorAmount", "latestCompressor"),
        Triple("setVocalFocus", "setVocalFocus", "latestVocalFocus")
    )

    private fun readViewModelSource(): String {
        val relative = "src/main/java/com/audioenhancer/booster/BoosterViewModel.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    private fun bodyOf(source: String, marker: String, end: String): String {
        val start = source.indexOf(marker)
        assertTrue("$marker tidak ditemukan", start >= 0)
        val stop = source.indexOf(end, start + marker.length)
        assertTrue("akhir $marker tidak ditemukan", stop > start)
        return source.substring(start, stop)
    }

    @Test
    fun `slider setters only store the latest value and wake the worker`() {
        val source = readViewModelSource()
        sliders.forEach { (vmName, serviceName, slot) ->
            val body = bodyOf(source, "fun $vmName(", "\n    }\n")
            assertTrue("$vmName harus menaruh nilai di slot $slot", body.contains("$slot.set(value)"))
            assertTrue("$vmName harus membangunkan worker", body.contains("eqApplyKick.trySend(Unit)"))
            assertTrue("$vmName harus tetap menyimpan nilai bila belum konek", body.contains("else pending"))
            assertFalse("$vmName tidak boleh memanggil service langsung di Main", body.contains("service?.$serviceName("))
        }
    }

    @Test
    fun `worker applies every slider through the shared latest value path`() {
        val source = readViewModelSource()
        val init = bodyOf(source, "init {", "private fun applyLatestEqualizerBands(")
        assertTrue(init.contains("applyLatestEqualizerBands()"))
        assertTrue("worker harus ikut menerapkan slider", init.contains("applyLatestSliders()"))
        assertTrue("worker berjalan di Dispatchers.IO", init.contains("Dispatchers.IO"))

        val apply = bodyOf(source, "private fun applyLatestSliders(", "private fun <T : Any> applyLatest(")
        assertTrue("service putus: slot tidak boleh diambil", apply.contains("val svc = service ?: return"))
        sliders.forEach { (_, serviceName, slot) ->
            assertTrue("$serviceName harus diterapkan dari $slot", apply.contains("applyLatest($slot,") && apply.contains("svc.$serviceName(it)"))
        }

        val one = bodyOf(source, "private fun <T : Any> applyLatest(", "\n    }\n")
        assertTrue("last-write-wins: ambil lalu kosongkan slot", one.contains("slot.getAndSet(null) ?: return"))
        assertTrue("satu slider gagal tak boleh menghentikan yang lain", one.contains("catch (e: Exception)"))
    }

    @Test
    fun `values stored while the service is away are applied after it connects`() {
        val source = readViewModelSource()
        assertTrue(source.contains("eqLatestMb.isNotEmpty() || hasLatestSliderValues()) eqApplyKick.trySend(Unit)"))
        val has = bodyOf(source, "private fun hasLatestSliderValues(", "\n    /**")
        sliders.forEach { (_, _, slot) -> assertTrue("$slot harus diperiksa", has.contains("$slot.get() != null")) }
    }
}
