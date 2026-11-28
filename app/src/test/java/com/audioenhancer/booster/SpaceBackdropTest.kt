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

    private val componentsSource: String by lazy { sourceOf("src/main/java/com/audioenhancer/booster/SkeuomorphicComponents.kt") }

    /** Batch 348: isi fungsi top-level [marker] sampai penutup `}` di awal baris pertama sesudahnya. */
    private fun bodyOf(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("$marker tidak ditemukan", start >= 0)
        val end = source.indexOf("\n}\n", start)
        assertTrue("penutup $marker tidak ditemukan", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `only cosmic orange enables the card ornament`() {
        val theme = code(themeSource)
        val assignments = Regex("""\bornament\s*=\s*""").findAll(theme).count()
        assertEquals("hanya SpaceDepth yang mengaktifkan ornamen", 1, assignments)
        val spaceDepth = theme.substringAfter("val SpaceDepth = DepthStyle(").substringBefore("\n)\n")
        assertTrue(spaceDepth.contains("ornament = SpaceCardOrnament"))
        assertTrue("SpaceCardOrnament wajib di atas SpaceDepth", theme.indexOf("val SpaceCardOrnament") in 0 until theme.indexOf("val SpaceDepth = DepthStyle("))
    }

    @Test
    fun `card ornament is drawn only on plates and behind the content`() {
        val code = code(componentsSource)
        assertTrue(code.contains("DepthPartsPlate = DepthParts(face = true, grain = true, frame = true, front = false, ornament = true)"))
        assertTrue(code.contains("DepthPartsKey = DepthParts(face = false, grain = false, frame = false, front = false)"))
        assertTrue(code.contains("DepthPartsDialog = DepthParts(face = false, grain = true, frame = true, front = true)"))
        val ops = code.substringAfter("val ops: DrawScope.() -> Unit = {").substringBefore("if (parts.front) {")
        val order = listOf("drawImage(face", "drawRect(brush = DepthGrainBrush", "drawCardOrnament(ornament)", "drawPath(framePath", "for (f in facets)").map { ops.indexOf(it) }
        assertTrue("urutan lapisan pelat: $order", order.all { it >= 0 } && order == order.sorted())
        assertFalse(ops.contains("buildCardOrnament"))
    }

    @Test
    fun `card ornament stays light and never animates`() {
        val draw = code(bodyOf(componentsSource, "private fun DrawScope.drawCardOrnament("))
        val calls = Regex("""\bdraw(Arc|Path|Circle|Line|Rect|Image|Oval|Points)\(""").findAll(draw).count()
        assertTrue("panggilan gambar ornamen kartu = $calls", calls in 1..12)
        val region = code(bodyOf(componentsSource, "private fun buildCardOrnament(") + draw)
        listOf("InfiniteTransition", "withFrameNanos", "animate", "BlurMaskFilter", "graphicsLayer", "saveLayer").forEach {
            assertFalse("ornamen kartu tidak boleh memuat $it", region.contains(it))
        }
        assertTrue("pelat kecil (snackbar) dilewati", region.contains("if (w < 150f * d || h < 64f * d) return null"))
    }

    @Test
    fun `section label and power knob get ornaments only when the depth style has one`() {
        val src = code(componentsSource)
        val label = bodyOf(src, "internal fun SectionLabel(")
        assertTrue(label.contains("LocalSkeuTokens.current.depth?.ornament"))
        assertTrue("tema tanpa ornamen = Text polos", label.contains("if (ornament == null) {"))
        val power = bodyOf(src, "internal fun SkeuPowerButton(")
        assertTrue(power.contains("val knobOrnament = depth?.ornament"))
        assertTrue(power.contains("if (knobOrnament != null) {"))
        listOf("private fun Modifier.depthKnobOrbit(", "private fun SectionFlourish(").forEach { marker ->
            val fn = bodyOf(src, marker)
            val calls = Regex("""\bdraw(Arc|Path|Circle|Line|Rect|Image|Oval|Points)\(""").findAll(fn).count()
            assertTrue("$marker = $calls panggilan gambar", calls in 1..4)
            listOf("InfiniteTransition", "withFrameNanos", "animate", "BlurMaskFilter", "graphicsLayer", "saveLayer").forEach {
                assertFalse("$marker tidak boleh memuat $it", fn.contains(it))
            }
        }
    }

    @Test
    fun `card ornament stays sparse and varied instead of dense and uniform`() {
        val build = code(bodyOf(componentsSource, "private fun buildCardOrnament("))
        listOf("midCount", "motif", "OrnamentRng(", "prevMotif").forEach {
            assertTrue("buildCardOrnament wajib memuat $it", build.contains(it))
        }
        listOf("railStep", "moduleStep", "glows", "drawPoints").forEach {
            assertFalse("pola rapat/seragam $it sudah dibuang (B351)", build.contains(it))
        }
        assertTrue("aksen tengah dibatasi", build.contains("coerceIn(0, 6)"))
        assertTrue("debu jarang dan dibatasi", build.contains("coerceIn(3, 22)"))
        val draw = code(bodyOf(componentsSource, "private fun DrawScope.drawCardOrnament("))
        assertEquals("gambar ornamen tanpa loop", 0, Regex("""\bfor \(|\bwhile \(""").findAll(draw).count())
        assertFalse(draw.contains("drawPoints"))
    }
}
