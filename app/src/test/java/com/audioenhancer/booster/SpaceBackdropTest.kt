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

    private fun sourceOf(rel: String): String =
        checkNotNull(listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.isFile }?.readText()) { "$rel tidak ditemukan" }

    /** Batch 347: komentar baris dibuang supaya penyebutan nama fungsi di komentar tidak ikut terhitung. */
    private fun code(text: String): String = text.lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `cosmic orange keeps the stored theme id so saved choices stay valid`() {
        assertEquals("space", PrefsHelper.APP_THEME_SPACE)
    }

    @Test
    fun `cosmic orange name is shown in both languages`() {
        listOf("src/main/res/values/strings.xml", "src/main/res/values-en/strings.xml").forEach { rel ->
            assertTrue(rel, sourceOf(rel).contains("<string name=\"theme_style_space_title\">Cosmic Orange</string>"))
        }
    }

    @Test
    fun `live ornaments stay a handful of draw calls and never animate`() {
        val orbit = code(themeSource.substringAfter("private fun DrawScope.drawSpaceOrbit()").substringBefore("internal const val SPACE_BACKDROP_SCALE"))
        val orbitCalls = Regex("""draw(Oval|Circle|Line|Rect|Path|Image)\(""").findAll(orbit).count()
        assertTrue("panggilan gambar orbit = $orbitCalls", orbitCalls in 1..6)
        val stars = code(themeSource.substringAfter("private val SpaceStars: List<SpaceStar> = run {").substringBefore("private val SpaceStarEmber"))
        val count = checkNotNull(Regex("""List\((\d+)\)""").find(stars)) { "List(n) bintang tidak ditemukan" }.groupValues[1].toInt()
        assertTrue("jumlah bintang = $count", count in 1..150)
        val region = code(themeSource.substringAfter("// ===== Batch 324: varian ke-7").substringBefore("/** Token yang beda antar 5 varian desain"))
        assertFalse(region.contains("InfiniteTransition"))
        assertFalse(region.contains("withFrameNanos"))
        assertFalse(region.contains("animate"))
    }
}
