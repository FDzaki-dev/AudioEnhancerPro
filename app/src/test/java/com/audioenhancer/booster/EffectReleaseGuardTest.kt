package com.audioenhancer.booster

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 280: penjaga (tes teks sumber, AudioEffect tak bisa dijalankan di JVM murni) agar objek efek yang sudah dibuat tetapi gagal
 * diselesaikan TIDAK tertinggal di audioserver. Sebelumnya field baru diisi SESUDAH blok `apply`, jadi exception di dalam `apply`
 * (enable, listener, parameter) meninggalkan objek native tanpa referensi dan tanpa `release()` (jatah memori/CPU efek sistem terpakai
 * sia-sia; di device yang menolak profil DynamicsProcessing, tiap percobaan profil bisa meninggalkan satu objek).
 */
class EffectReleaseGuardTest {
    private fun readServiceSource(): String {
        val relative = "src/main/java/com/audioenhancer/booster/AudioEnhancerService.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readLines().joinToString("\n") { it.substringBefore("//") }
    }

    private fun functionBody(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("$marker tidak ditemukan", start >= 0)
        val end = source.indexOf("\n    }\n", start)
        assertTrue("akhir $marker tidak ditemukan", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `an effect that fails after its constructor is released by its own attach function`() {
        val source = readServiceSource()
        listOf(
            "private fun attachBass(" to "releaseQuietly(\"BassBoost\", created)",
            "private fun attachVirtualizer(" to "releaseQuietly(\"Virtualizer\", created)",
            "private fun attachEqualizer(" to "releaseQuietly(\"Equalizer\", created)",
            "private fun attachLoudness(" to "releaseQuietly(\"LoudnessEnhancer\", created)",
            "private fun attachDynamicsProcessing(" to "releaseQuietlyDynamics(created)",
            "private fun attachVisualizer(" to "releaseQuietlyVisualizer(created)"
        ).forEach { (marker, release) ->
            val body = functionBody(source, marker)
            assertTrue("$marker harus menyimpan objek yang baru dibuat sebelum blok apply", body.contains("created = it"))
            assertTrue("$marker harus melepas objek setengah jadi di catch", body.contains(release))
            assertTrue("$marker: release harus berada di catch", body.indexOf("catch (e: Exception)") in 0 until body.indexOf(release))
        }
    }

    @Test
    fun `release effects releases every effect separately and clears the references`() {
        val body = functionBody(readServiceSource(), "private fun releaseEffects(")
        listOf(
            "releaseQuietly(\"BassBoost\", bassBoost)",
            "releaseQuietly(\"Virtualizer\", virtualizer)",
            "releaseQuietly(\"Equalizer\", equalizer)",
            "releaseQuietly(\"LoudnessEnhancer\", loudnessEnhancer)",
            "releaseQuietlyDynamics(dynamicsProcessing)",
            "releaseQuietlyVisualizer(visualizer)"
        ).forEach { assertTrue("$it harus ada", body.contains(it)) }
        assertTrue("DynamicsProcessing hanya di balik SDK_INT >= P", body.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) releaseQuietlyDynamics("))
        assertFalse("tak boleh ada release() mentah yang bisa menghentikan pelepasan efek lain", body.contains("?.release()"))
        listOf("bassBoost", "virtualizer", "equalizer", "loudnessEnhancer", "dynamicsProcessing", "visualizer").forEach {
            assertTrue("$it harus dikosongkan sesudah dilepas", body.contains("$it = null"))
        }
    }

    @Test
    fun `quiet release helpers skip null and never throw`() {
        val source = readServiceSource()
        val effect = functionBody(source, "private fun releaseQuietly(")
        assertTrue(effect.contains("if (effect == null) return"))
        assertTrue(effect.contains("catch (e: Exception)"))
        val visualizer = functionBody(source, "private fun releaseQuietlyVisualizer(")
        assertTrue(visualizer.contains("if (effect == null) return"))
        assertTrue(visualizer.contains("catch (e: Exception)"))
        assertTrue(
            "pembungkus DynamicsProcessing harus menyaring NewApi seperti releaseReplacedDynamics",
            source.contains("@android.annotation.SuppressLint(\"NewApi\")\n    private fun releaseQuietlyDynamics(")
        )
    }
}
