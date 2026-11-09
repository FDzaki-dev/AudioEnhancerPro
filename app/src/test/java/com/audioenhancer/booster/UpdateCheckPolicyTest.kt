package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 299 — kebijakan cek update otomatis (`UpdateCheckPolicy`), pemecah penanda panah (`UpdateManager.splitAtArrow`)
 * dan penjaga sumber untuk dua perbaikan: banner update tak konsisten + glyph panah rusak. JVM murni, tanpa Robolectric.
 */
class UpdateCheckPolicyTest {

    private val minute = 60_000L

    @Test
    fun `belum pernah dicek selalu boleh cek`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(lastCheckAtMs = null, lastFailed = false, nowMs = 0L))
        assertTrue(UpdateCheckPolicy.shouldCheck(lastCheckAtMs = null, lastFailed = true, nowMs = 0L))
    }

    @Test
    fun `setelah cek sukses tunggu sampai jeda 15 menit`() {
        val last = 1_000_000L
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last))
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last + 15 * minute - 1))
        assertTrue(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last + 15 * minute))
    }

    @Test
    fun `setelah cek gagal boleh coba lagi dalam 60 detik`() {
        val last = 1_000_000L
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = true, nowMs = last + 59_999L))
        assertTrue(UpdateCheckPolicy.shouldCheck(last, lastFailed = true, nowMs = last + 60_000L))
    }

    @Test
    fun `jeda coba ulang bertambah dan batas percobaan tiga kali`() {
        assertEquals(20_000L, UpdateCheckPolicy.retryDelayMs(1))
        assertEquals(40_000L, UpdateCheckPolicy.retryDelayMs(2))
        assertEquals(20_000L, UpdateCheckPolicy.retryDelayMs(0))
        assertEquals(3, UpdateCheckPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun `frekuensi cek sukses jauh di bawah batas API GitHub tanpa token`() {
        val perHour = 60L * minute / UpdateCheckPolicy.RECHECK_INTERVAL_MS
        assertTrue("maks cek sukses per jam harus jauh di bawah 60", perHour <= 4L)
    }

    @Test
    fun `splitAtArrow memecah di penanda pertama`() {
        val parts = UpdateManager.splitAtArrow("Update ditemukan: v332 ${UpdateManager.ARROW_MARKER} v333")
        assertEquals("Update ditemukan: v332 " to " v333", parts)
    }

    @Test
    fun `splitAtArrow tanpa penanda mengembalikan null`() {
        assertNull(UpdateManager.splitAtArrow("Update ditemukan: v332 ke v333"))
    }

    @Test
    fun `splitAtArrow hanya memecah penanda pertama dan menerima penanda di tepi`() {
        val m = UpdateManager.ARROW_MARKER
        assertEquals("a " to " b $m c", UpdateManager.splitAtArrow("a $m b $m c"))
        assertEquals("" to "x", UpdateManager.splitAtArrow("${m}x"))
        assertEquals("x" to "", UpdateManager.splitAtArrow("x$m"))
    }

    @Test
    fun `string update ditemukan ID dan EN memakai penanda dan tanpa glyph panah Unicode`() {
        listOf("values", "values-en").forEach { dir ->
            val xml = readFile("src/main/res/$dir/strings.xml")
            val line = xml.lines().firstOrNull { it.contains("name=\"settings_update_found\"") }
            checkNotNull(line) { "settings_update_found tidak ada di $dir" }
            assertTrue("$dir: penanda ${UpdateManager.ARROW_MARKER} hilang", line.contains(UpdateManager.ARROW_MARKER))
            assertTrue("$dir: placeholder versi hilang", line.contains("%1\$s") && line.contains("%2\$s"))
            // Blok panah Unicode (U+2190..U+21FF) tak boleh ada di string mana pun: font sistem sebagian HP tak memilikinya.
            val arrows = xml.filter { it.code in 0x2190..0x21FF }
            assertTrue("$dir: glyph panah Unicode ditemukan di strings.xml: $arrows", arrows.isEmpty())
        }
    }

    @Test
    fun `cek update otomatis diulang saat UI terlihat dan tidak menimpa banner dengan null`() {
        val vm = readFile("src/main/java/com/audioenhancer/booster/BoosterViewModel.kt")
            .lines().joinToString("\n") { it.substringBefore("//") }
        val setUiActive = vm.substringAfter("fun setUiActive(").substringBefore("\n    }\n")
        assertTrue("UI terlihat lagi harus memicu cek update", setUiActive.contains("autoCheckForUpdate()"))
        assertTrue("ViewModel memakai kebijakan UpdateCheckPolicy", vm.contains("UpdateCheckPolicy.shouldCheck("))
        assertFalse(
            "hasil cek tak boleh langsung menimpa updateInfo (gagal = null = banner hilang)",
            vm.contains("updateInfo = UpdateManager.checkForUpdate(")
        )
        val record = vm.substringAfter("private fun recordUpdateCheck(").substringBefore("\n    }\n")
        assertTrue("Failed tidak boleh mengubah state", record.contains("UpdateManager.CheckResult.Failed -> Unit"))
    }

    @Test
    fun `banner update di layar utama menampilkan snapshot changelog`() {
        val screen = readFile("src/main/java/com/audioenhancer/booster/BoosterScreen.kt")
        val banner = screen.substringAfter("private fun UpdateBanner(").substringBefore("\n}\n")
        assertTrue("banner harus membaca releaseNotes", banner.contains("info.releaseNotes"))
        assertTrue("banner memakai label 'Yang baru'", banner.contains("R.string.settings_whats_new_label"))
    }

    private fun readFile(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }
}
