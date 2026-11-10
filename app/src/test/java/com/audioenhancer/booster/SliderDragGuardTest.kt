package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 303 — penjaga sumber untuk laporan "slider digeser lalu jari menjauh dari lintasan langsung terhempas ke 0%".
 * Penyebab pasti belum terbukti; tes ini mengunci tiga pengaman (geseran per-event, bendera `touching`, log lonjakan)
 * supaya tidak hilang diam-diam. JVM murni (membaca sumber), pola sama dengan penjaga sumber lain di proyek ini.
 */
class SliderDragGuardTest {

    private val source: String by lazy {
        val relative = "src/main/java/com/audioenhancer/booster/SkeuomorphicComponents.kt"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
        checkNotNull(file) { "SkeuomorphicComponents.kt tidak ditemukan" }.readText()
            .lines().joinToString("\n") { it.substringBefore("//") }
    }

    private val dragBody: String
        get() = source.substringAfter("private fun Modifier.thumbOnlyDrag(").substringBefore("\n/** Batch 169")

    private val featureBody: String
        get() = source.substringAfter("internal fun FeatureControl(").substringBefore("\n/** Batch 88")

    @Test
    fun `geseran jari dijumlahkan dari perubahan posisi per event`() {
        assertTrue(dragBody.contains("positionChangeIgnoreConsumed()"))
        assertTrue(dragBody.contains("totalX += moved.x"))
        assertFalse("tidak boleh kembali ke posisi - posisi-down", dragBody.contains("change.position - down.position"))
    }

    @Test
    fun `bendera touching diset selama sentuhan dan selalu dibersihkan`() {
        assertTrue(dragBody.contains("touching.value = true"))
        val finallyBlock = dragBody.substringAfter("} finally {").substringBefore("\n        }\n")
        assertTrue("touching harus direset di finally", finallyBlock.contains("touching.value = false"))
    }

    @Test
    fun `onValueChange internal Slider M3 diabaikan selagi jari menyentuh`() {
        val slider = featureBody.substringAfter("Slider(").substringBefore("valueRange = valueRange")
        assertTrue(slider.contains("if (!touching.value)"))
        assertTrue("onValueChange luar hanya dipanggil dalam penjaga touching", slider.indexOf("onValueChange(snapped)") > slider.indexOf("if (!touching.value)"))
        assertFalse("haptic akhir drag hanya dari thumbOnlyDrag", slider.contains("onValueChangeFinished"))
        assertTrue(featureBody.contains("latestOnDragEnd, touching)"))
    }

    @Test
    fun `lonjakan nilai lebih dari separuh rentang dicatat di logcat`() {
        assertTrue(dragBody.contains("android.util.Log.w("))
        assertTrue(dragBody.contains("\"SliderDrag\""))
        assertTrue(dragBody.contains("span * 0.5f"))
    }

    @Test
    fun `drag relatif tetap dibatasi 0 sampai 1 dan dimulai hanya dekat thumb`() {
        assertTrue(dragBody.contains("(startFraction + sign * totalX / trackWidthPx).coerceIn(0f, 1f)"))
        assertTrue(dragBody.contains("dragging = horizontal && nearThumb"))
    }
}
