package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 308 — penjaga Baseline Profile (jank buka UI, temuan B279; uji B307: `cmd package compile -m speed` menurunkan
 * skipped frames 43 -> 35 dan terasa satset di HP user).
 *
 * APK Boomly di-sideload (tanpa Play), jadi profil hanya sampai ke ART lewat ProfileInstaller. Tes JVM murni ini (tanpa
 * Robolectric, ikut `testDebugUnitTest`) menjaga 3 hal supaya regresi tertangkap di CI, bukan di HP pengguna:
 *  1. dependensi `androidx.profileinstaller` ada dan versinya >= 1.4.0 (versi lama TIDAK mendukung API 35+; HP user Android 16),
 *  2. `src/main/baseline-prof.txt` memuat aturan wildcard untuk seluruh paket app,
 *  3. setiap baris aturan berbentuk sintaks profil (baris salah bentuk bisa menggagalkan task profil di build release).
 *
 * Berkas dibaca sebagai teks dari direktori kerja modul (`app/`), dengan cadangan jalur dari akar proyek.
 */
class BaselineProfileGuardTest {

    private fun sourceFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
    }

    private val buildScript: String by lazy { sourceFile("build.gradle.kts").readText() }

    private val profileRules: List<String> by lazy {
        sourceFile("src/main/baseline-prof.txt").readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    @Test
    fun profileInstallerDependencyIsAtLeastVersion14() {
        val match = checkNotNull(
            Regex("""implementation\("androidx\.profileinstaller:profileinstaller:(\d+)\.(\d+)\.(\d+)[^"]*"\)""").find(buildScript)
        ) {
            "dependensi androidx.profileinstaller tidak ditemukan di app/build.gradle.kts (APK sideload butuh ProfileInstaller)"
        }
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        assertTrue(
            "androidx.profileinstaller harus >= 1.4.0 (dukungan API 35+), ditemukan: ${match.groupValues[1]}.${match.groupValues[2]}.${match.groupValues[3]}",
            major > 1 || (major == 1 && minor >= 4)
        )
    }

    @Test
    fun wildcardRuleCoversWholeAppPackage() {
        assertTrue(
            "baseline-prof.txt harus memuat `HSPLcom/audioenhancer/booster/**->**(**)**`, ditemukan: $profileRules",
            profileRules.contains("HSPLcom/audioenhancer/booster/**->**(**)**")
        )
    }

    @Test
    fun everyRuleHasProfileSyntaxShape() {
        val shape = Regex("""[HSP]{0,3}L\S+""")
        val bad = profileRules.filter { !shape.matches(it) }
        assertTrue("Baris baseline-prof.txt berbentuk salah (bisa menggagalkan build release): $bad", bad.isEmpty())
    }
}
