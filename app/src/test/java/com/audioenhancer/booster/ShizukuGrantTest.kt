package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 250 — penjaga akses Reverb lewat Shizuku (izin `DUMP` dengan satu tombol, tanpa komputer).
 *
 * JVM murni (tanpa Robolectric/Shizuku asli): (1) aturan murni `ReverbAccessPolicy` (urutan status, kriteria sukses `pm grant`),
 * (2) tes teks sumber untuk kontrak yang TIDAK terlihat kompiler: manifest (provider + queries), aturan R8 untuk layanan yang
 * dibuat lewat refleksi, `tag` UserService, dan paket `pm grant` yang harus sama dengan `applicationId`.
 */
class ShizukuGrantTest {

    private fun readFirst(candidates: List<String>, accept: (String) -> Boolean = { true }): String {
        val text = candidates.map { File(it) }.filter { it.isFile }.map { it.readText() }.firstOrNull(accept)
        return checkNotNull(text) { "Berkas tidak ditemukan/cocok di: $candidates" }
    }

    private fun module(relative: String): String = readFirst(listOf(relative, "app/$relative"))

    private val grantSource: String by lazy { module("src/main/java/com/audioenhancer/booster/ShizukuGrant.kt") }
    private val manifest: String by lazy { module("src/main/AndroidManifest.xml") }

    /** Sumber tanpa komentar `//` dan KDoc supaya tes menilai KODE, bukan catatan. */
    private fun code(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    // ---- aturan murni ----

    @Test
    fun `granted wins over everything except busy`() {
        for (installed in listOf(true, false)) for (alive in listOf(true, false)) {
            assertEquals(
                ReverbAccess.GRANTED,
                ReverbAccessPolicy.resolve(true, installed, alive, false, ReverbAccess.FAILED)
            )
        }
    }

    @Test
    fun `busy shows working before shizuku state`() {
        assertEquals(ReverbAccess.WORKING, ReverbAccessPolicy.resolve(false, true, true, true, null))
        assertEquals(ReverbAccess.WORKING, ReverbAccessPolicy.resolve(false, false, false, true, ReverbAccess.DENIED))
        // Batch 251: saat mencabut izin (izin masih ada) status harus WORKING, bukan GRANTED.
        assertEquals(ReverbAccess.WORKING, ReverbAccessPolicy.resolve(true, true, true, true, null))
    }

    @Test
    fun `dead binder splits installed from not installed`() {
        assertEquals(ReverbAccess.NOT_RUNNING, ReverbAccessPolicy.resolve(false, true, false, false, null))
        assertEquals(ReverbAccess.NOT_INSTALLED, ReverbAccessPolicy.resolve(false, false, false, false, null))
    }

    @Test
    fun `dead binder hides a stale failure`() {
        assertEquals(ReverbAccess.NOT_RUNNING, ReverbAccessPolicy.resolve(false, true, false, false, ReverbAccess.FAILED))
        assertEquals(ReverbAccess.NOT_INSTALLED, ReverbAccessPolicy.resolve(false, false, false, false, ReverbAccess.DENIED))
    }

    @Test
    fun `alive binder shows failure then ready`() {
        assertEquals(ReverbAccess.DENIED, ReverbAccessPolicy.resolve(false, true, true, false, ReverbAccess.DENIED))
        assertEquals(ReverbAccess.FAILED, ReverbAccessPolicy.resolve(false, true, true, false, ReverbAccess.FAILED))
        assertEquals(ReverbAccess.READY, ReverbAccessPolicy.resolve(false, true, true, false, null))
    }

    @Test
    fun `pm grant success needs exit zero and clean output`() {
        assertTrue(ReverbAccessPolicy.commandSucceeded(0, ""))
        assertFalse(ReverbAccessPolicy.commandSucceeded(255, ""))
        assertFalse(ReverbAccessPolicy.commandSucceeded(-1, ""))
        assertFalse(ReverbAccessPolicy.commandSucceeded(0, "Exception occurred while executing 'grant':\njava.lang.SecurityException: x"))
        assertFalse(ReverbAccessPolicy.commandSucceeded(0, "Error: Unknown permission"))
    }

    @Test
    fun `output is trimmed and capped`() {
        assertEquals("abc", ReverbAccessPolicy.trimOutput("  abc \n"))
        assertEquals(ReverbAccessPolicy.OUTPUT_MAX_CHARS, ReverbAccessPolicy.trimOutput("x".repeat(5000)).length)
    }

    @Test
    fun `grant command is exactly pm grant of DUMP to this app`() {
        assertEquals(
            listOf("pm", "grant", "com.audioenhancer.booster", "android.permission.DUMP"),
            ReverbAccessPolicy.GRANT_COMMAND
        )
        assertEquals(DumpProbe.PERMISSION, ReverbAccessPolicy.GRANT_COMMAND.last())
    }

    @Test
    fun `revoke command is exactly pm revoke of DUMP from this app`() {
        assertEquals(
            listOf("pm", "revoke", "com.audioenhancer.booster", "android.permission.DUMP"),
            ReverbAccessPolicy.REVOKE_COMMAND
        )
        assertEquals(ReverbAccessPolicy.GRANT_COMMAND.drop(2), ReverbAccessPolicy.REVOKE_COMMAND.drop(2))
    }

    @Test
    fun `service transaction codes follow the shizuku contract`() {
        assertEquals(1, ShizukuGrantService.CODE_GRANT)
        assertEquals(2, ShizukuGrantService.CODE_REVOKE)
        assertEquals(16_777_115, ShizukuGrantService.CODE_DESTROY)
    }

    @Test
    fun `timing constants stay sane`() {
        assertTrue(ReverbAccessPolicy.CONNECT_TIMEOUT_MS in 3_000L..20_000L)
        assertTrue(ReverbAccessPolicy.VERIFY_TRIES * ReverbAccessPolicy.VERIFY_DELAY_MS in 1_000L..5_000L)
    }

    // ---- tes teks sumber ----

    @Test
    fun `grant package equals applicationId`() {
        val gradle = readFirst(listOf("build.gradle.kts", "app/build.gradle.kts")) { it.contains("applicationId") }
        val id = checkNotNull(Regex("""applicationId\s*=\s*"([^"]+)"""").find(gradle)).groupValues[1]
        assertEquals("pm grant harus menarget applicationId app ini", id, ReverbAccessPolicy.APP_PACKAGE)
    }

    @Test
    fun `both shizuku artifacts are declared with the same version`() {
        val gradle = readFirst(listOf("build.gradle.kts", "app/build.gradle.kts")) { it.contains("applicationId") }
        val api = Regex("""dev\.rikka\.shizuku:api:([\w.]+)""").find(gradle)?.groupValues?.get(1)
        val provider = Regex("""dev\.rikka\.shizuku:provider:([\w.]+)""").find(gradle)?.groupValues?.get(1)
        assertTrue("dependensi api Shizuku hilang", api != null)
        assertEquals("api & provider harus satu versi", api, provider)
    }

    @Test
    fun `manifest declares the shizuku provider and queries`() {
        val provider = checkNotNull(Regex("""<provider\b[^>]*rikka\.shizuku\.ShizukuProvider[^>]*>""", RegexOption.DOT_MATCHES_ALL).find(manifest)) {
            "ShizukuProvider tidak ada di manifest"
        }.value
        assertTrue(provider.contains("\${applicationId}.shizuku"))
        assertTrue("provider harus dilindungi izin", provider.contains("android.permission.INTERACT_ACROSS_USERS_FULL"))
        assertTrue(provider.contains("""android:exported="true""""))
        assertTrue(provider.contains("""android:multiprocess="false""""))
        assertTrue(
            "<queries> harus memuat paket Shizuku (Android 11+)",
            Regex("""<queries>.*?<package\s+android:name="${Regex.escape(ReverbAccessPolicy.SHIZUKU_PACKAGE)}"""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(manifest)
        )
    }

    @Test
    fun `service class is kept for r8`() {
        val rules = readFirst(listOf("proguard-rules.pro", "app/proguard-rules.pro"))
        assertTrue(
            "ShizukuGrantService dibuat lewat refleksi: wajib di-keep",
            Regex("""-keep\s+class\s+com\.audioenhancer\.booster\.ShizukuGrantService\b""").containsMatchIn(rules)
        )
    }

    @Test
    fun `user service uses a stable tag and a no argument binder`() {
        val src = code(grantSource)
        assertTrue(src.contains(".tag(ReverbAccessPolicy.SERVICE_TAG)"))
        assertTrue(src.contains("class ShizukuGrantService : Binder()"))
        assertTrue(src.contains("Shizuku.unbindUserService(args, connection, true)"))
    }

    @Test
    fun `shell service knows only grant and destroy and never exits via System`() {
        val src = code(grantSource)
        val service = src.substring(src.indexOf("class ShizukuGrantService"), src.indexOf("internal object ShizukuGrant "))
        assertTrue(service.contains("CODE_GRANT, CODE_REVOKE ->"))
        assertTrue(service.contains("CODE_DESTROY ->"))
        assertFalse("tak boleh membaca argumen dari pemanggil", service.contains("data.readString"))
        assertFalse("detekt ExitOutsideMain / ArchUnit: pakai Process.killProcess", service.contains("System.exit"))
        assertTrue(service.contains("android.os.Process.killProcess"))
    }

    @Test
    fun `controller never touches the audio service paths`() {
        val src = code(grantSource)
        assertFalse(src.contains("AudioEnhancerService"))
        assertFalse(src.contains("runEffectRebind"))
        assertFalse(src.contains("GlobalScope"))
        assertFalse(src.contains("runBlocking"))
    }

    @Test
    fun `listeners are registered and removed in pairs`() {
        val src = code(grantSource)
        for (name in listOf("BinderReceivedListener", "BinderDeadListener", "RequestPermissionResultListener")) {
            assertTrue("add$name", src.contains("Shizuku.add$name") || src.contains("Shizuku.add${name}Sticky"))
            assertTrue("remove$name", src.contains("Shizuku.remove$name"))
        }
    }

    @Test
    fun `reverb card hosts the access block and pairs start with stop`() {
        val screen = code(module("src/main/java/com/audioenhancer/booster/BoosterScreen.kt"))
        assertTrue(screen.contains("ReverbAccessBlock(onGranted = { onReverb(reverbPreset.toInt()) })"))
        val body = screen.substring(screen.indexOf("private fun ReverbAccessBlock"))
        assertTrue(body.contains("ShizukuGrant.start(context)"))
        assertTrue(body.contains("ShizukuGrant.stop()"))
        assertTrue("segarkan status saat RESUME", body.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue("tombol uji ulang hanya saat Shizuku hidup", body.contains("if (ShizukuGrant.shizukuAlive)"))
        assertTrue(body.contains("ShizukuGrant.revoke(context)"))
    }

    @Test
    fun `access strings exist in both languages`() {
        val keys = listOf(
            "reverb_access_granted", "reverb_access_need_install", "reverb_access_need_start", "reverb_access_ready",
            "reverb_access_working", "reverb_access_denied", "reverb_access_failed",
            "reverb_access_button_enable", "reverb_access_button_open", "reverb_access_button_get", "reverb_access_button_revoke"
        )
        for (file in listOf("src/main/res/values/strings.xml", "src/main/res/values-en/strings.xml")) {
            val xml = module(file)
            for (key in keys) assertTrue("$key hilang di $file", xml.contains("name=\"$key\""))
        }
    }
}
