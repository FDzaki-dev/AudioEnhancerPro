package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 330: latar kosmos Space tak boleh kembali menggambar gradien layar-penuh tiap frame (penyebab berat/fps drop saat scroll cepat). */
class SpaceBackdropTest {
    private val themeSource: String = run {
        val rel = "src/main/java/com/audioenhancer/booster/Theme.kt"
        checkNotNull(listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.isFile }?.readText()) { "Theme.kt tidak ditemukan" }
    }

    private fun backdropBody(): String {
        val start = themeSource.indexOf("internal fun Modifier.spaceCosmosBackdrop()")
        val end = themeSource.indexOf("/** Token yang beda antar 5 varian desain")
        assertTrue(start in 0 until end)
        return themeSource.substring(start, end)
    }

    @Test
    fun `nebula and planet are rendered once into cached bitmaps`() {
        val body = backdropBody()
        assertTrue(body.contains("drawWithCache"))
        assertTrue(body.contains("renderLowRes { drawSpaceNebulae() }"))
        assertTrue(body.contains("renderLowRes { drawSpacePlanet() }"))
    }

    @Test
    fun `per frame drawing keeps layer order and never draws full screen gradients live`() {
        val live = backdropBody().substringAfter("onDrawBehind")
        assertFalse(live.contains("drawSpaceNebulae"))
        assertFalse(live.contains("drawSpacePlanet()"))
        val order = listOf("drawImage(nebula", "drawSpaceStars()", "drawImage(planet", "drawSpaceOrbit()").map { live.indexOf(it) }
        assertTrue(order.all { it >= 0 })
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `low resolution scale keeps the cached bitmaps small`() {
        assertTrue(SPACE_BACKDROP_SCALE > 0f && SPACE_BACKDROP_SCALE <= 0.5f)
    }
}
