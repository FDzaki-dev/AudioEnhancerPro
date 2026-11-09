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
 * Batch 301 — jeda cek diperpendek (banner tak muncul instan karena jeda 15 menit), penahan rate-limit GitHub
 * (`rateLimitWaitMs`) dan penjaga log diagnostik.
 */
class UpdateCheckPolicyTest {

    private val minute = 60_000L

    @Test
    fun `belum pernah dicek selalu boleh cek`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(lastCheckAtMs = null, lastFailed = false, nowMs = 0L))
        assertTrue(UpdateCheckPolicy.shouldCheck(lastCheckAtMs = null, lastFailed = true, nowMs = 0L))
    }

    @Test
    fun `setelah cek sukses tunggu sampai jeda 30 detik`() {
        val last = 1_000_000L
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last))
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last + 30_000L - 1))
        assertTrue(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last + 30_000L))
    }

    @Test
    fun `setelah cek gagal boleh coba lagi dalam 15 detik`() {
        val last = 1_000_000L
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = true, nowMs = last + 14_999L))
        assertTrue(UpdateCheckPolicy.shouldCheck(last, lastFailed = true, nowMs = last + 15_000L))
    }

    @Test
    fun `rilis yang terbit beberapa menit setelah cek terakhir terdeteksi saat app dibuka lagi`() {
        // Pola user: cek terakhir (UpToDate) lalu rilis baru terbit ~8 menit kemudian. Dulu (jeda 15 menit) dilewati.
        val last = 5_000_000L
        assertTrue(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = last + 8 * minute))
    }

    @Test
    fun `selama ditahan rate-limit tidak boleh cek walau jeda biasa sudah lewat`() {
        val last = 1_000_000L
        val now = last + 10 * minute
        assertFalse(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = now, backoffUntilMs = now + 1))
        assertTrue(UpdateCheckPolicy.shouldCheck(last, lastFailed = false, nowMs = now, backoffUntilMs = now))
        assertFalse(UpdateCheckPolicy.shouldCheck(lastCheckAtMs = null, lastFailed = false, nowMs = 100L, backoffUntilMs = 101L))
    }

    @Test
    fun `jeda coba ulang bertambah dan batas percobaan tiga kali`() {
        assertEquals(20_000L, UpdateCheckPolicy.retryDelayMs(1))
        assertEquals(40_000L, UpdateCheckPolicy.retryDelayMs(2))
        assertEquals(20_000L, UpdateCheckPolicy.retryDelayMs(0))
        assertEquals(3, UpdateCheckPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun `respons selain 403 dan 429 bukan rate-limit`() {
        listOf(200, 304, 404, 500, 503).forEach { code ->
            assertEquals("HTTP $code", 0L, UpdateCheckPolicy.rateLimitWaitMs(code, "0", "9999999999", "10", 1_000L))
        }
    }

    @Test
    fun `403 biasa tanpa tanda rate-limit tidak ditahan`() {
        assertEquals(0L, UpdateCheckPolicy.rateLimitWaitMs(403, remaining = "42", resetEpochSec = "2000", retryAfterSec = null, nowEpochSec = 1_000L))
        assertEquals(0L, UpdateCheckPolicy.rateLimitWaitMs(403, remaining = null, resetEpochSec = null, retryAfterSec = null, nowEpochSec = 1_000L))
        assertEquals(0L, UpdateCheckPolicy.rateLimitWaitMs(403, remaining = "abc", resetEpochSec = "2000", retryAfterSec = "xyz", nowEpochSec = 1_000L))
    }

    @Test
    fun `rate-limit dihitung dari Retry-After lalu X-RateLimit-Reset lalu bawaan`() {
        // Retry-After menang atas Reset.
        assertEquals(120_000L, UpdateCheckPolicy.rateLimitWaitMs(403, "0", "1500", "120", 1_000L))
        // Hanya Reset: (2_000 - 1_000) detik = 1000 dtk.
        assertEquals(1_000_000L, UpdateCheckPolicy.rateLimitWaitMs(403, "0", "2000", null, 1_000L))
        // 429 tanpa header apa pun = bawaan 60 dtk; remaining 0 tanpa Reset = bawaan juga.
        assertEquals(60_000L, UpdateCheckPolicy.rateLimitWaitMs(429, null, null, null, 1_000L))
        assertEquals(60_000L, UpdateCheckPolicy.rateLimitWaitMs(403, "0", null, null, 1_000L))
    }

    @Test
    fun `waktu tahan rate-limit dijepit 30 detik sampai 1 jam`() {
        assertEquals(30_000L, UpdateCheckPolicy.rateLimitWaitMs(429, null, null, "1", 1_000L))
        assertEquals(30_000L, UpdateCheckPolicy.rateLimitWaitMs(403, "0", "500", null, 1_000L)) // Reset sudah lewat
        assertEquals(3_600_000L, UpdateCheckPolicy.rateLimitWaitMs(403, "0", "99999999", null, 1_000L))
        assertEquals(3_600_000L, UpdateCheckPolicy.rateLimitWaitMs(429, null, null, "86400", 1_000L))
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
        assertTrue("UI terlihat lagi harus memicu cek update", setUiActive.contains("autoCheckForUpdate("))
        assertTrue("cek awal saat ViewModel dibuat", vm.contains("autoCheckForUpdate(\"init\")"))
        assertTrue("ViewModel memakai kebijakan UpdateCheckPolicy", vm.contains("UpdateCheckPolicy.shouldCheck("))
        assertFalse(
            "hasil cek tak boleh langsung menimpa updateInfo (gagal = null = banner hilang)",
            vm.contains("updateInfo = UpdateManager.checkForUpdate(")
        )
        val record = vm.substringAfter("private fun recordUpdateCheck(").substringBefore("\n    }\n")
        assertTrue("Failed/RateLimited tidak boleh mengubah state banner", record.contains("if (!failed) adoptUpdateResult("))
        assertTrue("RateLimited menahan cek otomatis", record.contains("updateBackoffUntilMs = now + result.waitMs"))
        assertTrue("hasil tiap cek dicatat di logcat", record.contains("UpdateCheckPolicy.LOG_TAG"))
        assertTrue("Available -> info baru, UpToDate -> null", record.contains("(result as? UpdateManager.CheckResult.Available)?.info"))
        assertFalse("tanpa cabang when kosong (SpotBugs UCF_USELESS_CONTROL_FLOW)", record.contains("-> Unit"))
    }

    @Test
    fun `cek otomatis berhenti mencoba ulang saat rate-limit dan mencatat alasan dilewati`() {
        val vm = readFile("src/main/java/com/audioenhancer/booster/BoosterViewModel.kt")
            .lines().joinToString("\n") { it.substringBefore("//") }
        val auto = vm.substringAfter("private fun autoCheckForUpdate(").substringBefore("\n    }\n")
        assertTrue("rate-limit tidak dicoba ulang", auto.contains("rateLimited"))
        assertTrue("alasan dilewati dicatat", auto.contains("dilewati (cek terakhir"))
        assertTrue("kebijakan menerima batas penahan", auto.contains("updateBackoffUntilMs)"))
        val manual = vm.substringAfter("fun checkForUpdateManually(").substringBefore("\n    }\n")
        assertFalse("cek manual tidak boleh diblokir penahan rate-limit", manual.contains("shouldCheck("))
    }

    @Test
    fun `UpdateManager mencatat kode HTTP dan alasan gagal di logcat`() {
        val src = readFile("src/main/java/com/audioenhancer/booster/UpdateManager.kt")
        assertTrue(src.contains("Log.w(UpdateCheckPolicy.LOG_TAG, \"Cek update: HTTP \$code"))
        assertTrue(src.contains("failedCheck("))
        assertTrue("hasil perbandingan versi dicatat", src.contains("release terbaru run \$runNumber, terpasang \$installedCode"))
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
