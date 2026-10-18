package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 221 — adaptasi AUDIO_BOOSTER_ARCHITECTURE_GUIDE §1 + §4 (Android 14+): kontrak foreground service.
 *
 * Android 14 (targetSdk 34) melempar `SecurityException` bila tipe foreground service tak dideklarasikan di manifest
 * atau izin `FOREGROUND_SERVICE_<TIPE>` hilang. Tes JVM murni ini (tanpa Robolectric/emulator, ikut `testDebugUnitTest`)
 * menjaga 3 hal supaya regresi tertangkap di CI, bukan di HP pengguna:
 *  1. izin `FOREGROUND_SERVICE` dan `FOREGROUND_SERVICE_MEDIA_PLAYBACK` ada,
 *  2. `AudioEnhancerService` mendeklarasikan `foregroundServiceType` yang memuat `mediaPlayback`,
 *  3. kode runtime `AudioEnhancerService` memanggil `startForeground` dengan tipe `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
 *
 * Berkas dibaca sebagai teks dari direktori kerja modul (`app/`), dengan cadangan jalur dari akar proyek.
 */
class ForegroundServiceManifestTest {

    private fun readSource(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    private val manifest: String by lazy { readSource("src/main/AndroidManifest.xml") }

    @Test
    fun manifestDeclaresForegroundServicePermissions() {
        listOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK").forEach { name ->
            val declared = Regex("""<uses-permission\s+android:name="android\.permission\.$name"""").containsMatchIn(manifest)
            assertTrue("Izin $name hilang dari manifest (Android 14+ butuh izin per-tipe)", declared)
        }
    }

    @Test
    fun serviceDeclaresMediaPlaybackType() {
        val service = checkNotNull(Regex("""<service\b[^>]*android:name="\.AudioEnhancerService"[^>]*>""").find(manifest)) {
            "<service .AudioEnhancerService> tidak ditemukan di manifest"
        }
        val type = Regex("""android:foregroundServiceType="([^"]*)"""").find(service.value)?.groupValues?.get(1).orEmpty()
        assertTrue("foregroundServiceType harus memuat mediaPlayback, ditemukan: '$type'", type.split("|").contains("mediaPlayback"))
    }

    @Test
    fun runtimeStartForegroundUsesSameType() {
        val source = readSource("src/main/java/com/audioenhancer/booster/AudioEnhancerService.kt")
        assertTrue(
            "startForeground(...) harus memakai ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK agar cocok dengan manifest",
            source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK")
        )
    }
}
