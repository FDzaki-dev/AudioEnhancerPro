package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 344 — JVM murni (tanpa Robolectric/Shizuku asli): menjaga (1) aturan murni `SilentInstallPolicy` (perintah `pm install` tetap, hanya
 * ukuran angka yang bisa berubah dan dibatasi; kriteria sukses = exit 0 + baris `Success`; jalur istimewa hanya untuk APK Boomly), dan
 * (2) tes teks sumber untuk kontrak yang TIDAK terlihat kompiler: transaksi `CODE_INSTALL` di layanan shell mengalirkan berkas lewat buffer
 * Okio (bukan memuat byte), versi layanan dinaikkan, update memakai Shizuku dulu lalu installer sistem sebagai cadangan, dan MainActivity
 * menerima aksi pengaturan ubin Quick Settings (tahan ubin membuka aplikasi, bukan Info Aplikasi).
 */
class ShizukuInstallerTest {

    private fun readFirst(candidates: List<String>): String {
        val text = candidates.map { File(it) }.firstOrNull { it.isFile }?.readText()
        return checkNotNull(text) { "Berkas tidak ditemukan di: $candidates" }
    }

    private fun module(relative: String): String = readFirst(listOf(relative, "app/$relative"))

    private fun java(name: String): String = module("src/main/java/com/audioenhancer/booster/$name")

    /** Sumber tanpa komentar `//` dan KDoc supaya tes menilai KODE, bukan catatan. */
    private fun code(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    // ---- aturan murni ----

    @Test
    fun `install command is fixed and carries only the size`() {
        assertEquals(listOf("pm", "install", "-r", "-S", "12345"), SilentInstallPolicy.installCommand(12_345L))
        assertEquals(listOf("pm", "install", "-r", "-S", "1"), SilentInstallPolicy.installCommand(1L))
        assertEquals(
            listOf("pm", "install", "-r", "-S", SilentInstallPolicy.MAX_APK_BYTES.toString()),
            SilentInstallPolicy.installCommand(SilentInstallPolicy.MAX_APK_BYTES)
        )
    }

    @Test
    fun `install command rejects sizes outside the valid range`() {
        assertNull(SilentInstallPolicy.installCommand(0L))
        assertNull(SilentInstallPolicy.installCommand(-1L))
        assertNull(SilentInstallPolicy.installCommand(Long.MIN_VALUE))
        assertNull(SilentInstallPolicy.installCommand(SilentInstallPolicy.MAX_APK_BYTES + 1L))
        assertNull(SilentInstallPolicy.installCommand(Long.MAX_VALUE))
    }

    @Test
    fun `install succeeds only on exit zero with a Success line`() {
        assertTrue(SilentInstallPolicy.installSucceeded(0, "Success"))
        assertTrue(SilentInstallPolicy.installSucceeded(0, "Success\n"))
        assertTrue(SilentInstallPolicy.installSucceeded(0, "Streamed 41234567 bytes\nSuccess\n"))
        assertFalse("exit bukan 0", SilentInstallPolicy.installSucceeded(1, "Success"))
        assertFalse(SilentInstallPolicy.installSucceeded(0, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]"))
        assertFalse(SilentInstallPolicy.installSucceeded(0, "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertFalse("keluaran kosong bukan sukses", SilentInstallPolicy.installSucceeded(0, ""))
        assertFalse(SilentInstallPolicy.installSucceeded(-1, "java.io.IOException: gagal"))
    }

    @Test
    fun `privileged path is only for the own package`() {
        assertTrue(SilentInstallPolicy.isOwnPackage(ReverbAccessPolicy.APP_PACKAGE, ReverbAccessPolicy.APP_PACKAGE))
        assertFalse(SilentInstallPolicy.isOwnPackage("com.example.other", ReverbAccessPolicy.APP_PACKAGE))
        assertFalse("APK tak terbaca = tak dikirim ke jalur istimewa", SilentInstallPolicy.isOwnPackage(null, ReverbAccessPolicy.APP_PACKAGE))
    }

    @Test
    fun `constants stay distinct and the service version was bumped`() {
        assertTrue("dialog izin DUMP dan pemasangan tak boleh tertukar", ReverbAccessPolicy.REQUEST_CODE != SilentInstallPolicy.REQUEST_CODE)
        assertEquals(3, ShizukuGrantService.CODE_INSTALL)
        val codes = listOf(
            ShizukuGrantService.CODE_GRANT,
            ShizukuGrantService.CODE_REVOKE,
            ShizukuGrantService.CODE_INSTALL,
            ShizukuGrantService.CODE_DESTROY
        )
        assertEquals("kode transaksi harus unik", codes.size, codes.toSet().size)
        assertEquals("kode layanan berubah: versi UserService harus naik", 2, ReverbAccessPolicy.SERVICE_VERSION)
        assertTrue(SilentInstallPolicy.INSTALL_TIMEOUT_MS > ReverbAccessPolicy.CONNECT_TIMEOUT_MS)
    }

    // ---- tes teks sumber ----

    @Test
    fun `shell service streams the apk through okio buffers and never loads bytes`() {
        val src = code(java("ShizukuGrant.kt"))
        val service = src.substring(src.indexOf("class ShizukuGrantService"), src.indexOf("internal object ShizukuGrant "))
        assertTrue(service.contains("CODE_INSTALL ->"))
        assertTrue(service.contains("SilentInstallPolicy.installCommand(sizeBytes)"))
        assertTrue(service.contains("ParcelFileDescriptor.AutoCloseInputStream(pfd).source().buffer().use"))
        assertTrue(service.contains("process.outputStream.sink().buffer().use"))
        assertTrue(service.contains("writeAll(source)"))
        assertFalse("tak boleh memuat byte APK utuh", service.contains("readBytes"))
        assertFalse(service.contains("readByteArray"))
        assertFalse("tak boleh membaca teks/argumen perintah dari pemanggil", service.contains("data.readString"))
        assertFalse(service.contains("System.exit"))
    }

    @Test
    fun `installer sends a file descriptor and reuses the user service identity`() {
        val src = code(java("ShizukuInstaller.kt"))
        assertTrue(src.contains("pfd.writeToParcel(data, 0)"))
        assertTrue(src.contains("ShizukuGrantService.CODE_INSTALL"))
        assertTrue(src.contains(".tag(ReverbAccessPolicy.SERVICE_TAG)"))
        assertTrue(src.contains(".processNameSuffix(ReverbAccessPolicy.PROCESS_SUFFIX)"))
        assertTrue(src.contains(".version(ReverbAccessPolicy.SERVICE_VERSION)"))
        assertTrue(src.contains("Shizuku.unbindUserService(args, connection, true)"))
        assertTrue("hanya APK Boomly yang dikirim", src.contains("SilentInstallPolicy.isOwnPackage(info?.packageName, app.packageName)"))
        assertFalse(src.contains("readBytes"))
        assertFalse(src.contains("GlobalScope"))
        assertFalse(src.contains("runBlocking"))
        assertFalse(src.contains("AudioEnhancerService"))
    }

    @Test
    fun `update tries shizuku first and falls back to the system installer`() {
        val src = code(java("BoosterViewModel.kt"))
        val helper = src.substring(src.indexOf("private suspend fun installUpdateApk("), src.indexOf("fun dismissUpdateDownloadFailed"))
        assertTrue(helper.contains("ShizukuInstaller.install(getApplication(), file) != SilentInstallResult.INSTALLED"))
        assertTrue("cadangan: intent installer sistem", helper.contains("UpdateManager.installApk(getApplication(), file)"))
        val download = src.substring(src.indexOf("fun downloadAndInstallUpdate("), src.indexOf("fun reinstallDownloadedUpdate("))
        assertTrue(download.contains("installUpdateApk(file)"))
        assertFalse("unduh tak boleh langsung memanggil installer sistem", download.contains("UpdateManager.installApk("))
        val reinstall = src.substring(src.indexOf("fun reinstallDownloadedUpdate("), src.indexOf("private suspend fun installUpdateApk("))
        assertTrue(reinstall.contains("viewModelScope.launch { installUpdateApk(file) }"))
        assertFalse(reinstall.contains("UpdateManager.installApk("))
    }

    @Test
    fun `main activity accepts the quick settings tile preferences action`() {
        val manifest = module("src/main/AndroidManifest.xml")
        val start = manifest.indexOf("""android:name=".MainActivity"""")
        assertTrue("MainActivity tidak ada di manifest", start >= 0)
        val end = manifest.indexOf("</activity>", start)
        assertTrue(end > start)
        val activity = manifest.substring(start, end)
        assertTrue("SystemUI meluncurkan activity ini: harus exported", activity.contains("""android:exported="true""""))
        assertTrue(activity.contains("android.service.quicksettings.action.QS_TILE_PREFERENCES"))
        val filter = Regex("""<intent-filter>(?:(?!</intent-filter>).)*QS_TILE_PREFERENCES(?:(?!</intent-filter>).)*</intent-filter>""", RegexOption.DOT_MATCHES_ALL)
            .find(activity)
        assertTrue("aksi harus berada dalam intent-filter sendiri", filter != null)
        assertTrue(checkNotNull(filter).value.contains("android.intent.category.DEFAULT"))
        assertTrue("filter peluncur MAIN/LAUNCHER tetap ada", activity.contains("android.intent.category.LAUNCHER"))
    }
}
