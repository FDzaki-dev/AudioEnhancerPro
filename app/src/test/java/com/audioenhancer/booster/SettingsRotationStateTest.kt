package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 239 — penjaga state rotasi `SettingsScreen` (Android Vital Guard: state wajib tahan rotasi).
 *
 * Activity di-recreate saat rotasi; state sementara yang hanya memakai `remember` hilang (pesan konflik jadwal,
 * peringatan minimal satu hari, status ekspor/impor). Tes JVM murni ini membaca sumber sebagai teks (Compose tak bisa
 * dijalankan di JVM murni) supaya regresi ke `remember` tertangkap di CI, bukan di HP pengguna.
 */
class SettingsRotationStateTest {

    private val transientStates = listOf(
        "scheduleTimeConflict",
        "scheduleDaysMinOne",
        "backupStatus",
        "backupStatusIsError",
    )

    private fun readSettingsSource(): String {
        val relative = "src/main/java/com/audioenhancer/booster/SettingsScreen.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = checkNotNull(candidates.firstOrNull { it.isFile }) {
            "Berkas tidak ditemukan: $relative (dicari di ${candidates.map { it.absolutePath }})"
        }
        return file.readText()
    }

    @Test
    fun transientStatesUseRememberSaveable() {
        val src = readSettingsSource()
        transientStates.forEach { name ->
            val saveable = Regex("""var\s+$name\s+by\s+rememberSaveable\s*\{""").containsMatchIn(src)
            assertTrue("State `$name` harus rememberSaveable agar tahan rotasi", saveable)
        }
    }

    @Test
    fun transientStatesAreNotPlainRemember() {
        val src = readSettingsSource()
        transientStates.forEach { name ->
            val plain = Regex("""var\s+$name\s+by\s+remember\s*\{""").containsMatchIn(src)
            assertTrue("State `$name` kembali ke `remember` biasa (hilang saat rotasi)", !plain)
        }
    }

    @Test
    fun rememberSaveableIsImported() {
        val src = readSettingsSource()
        assertTrue(
            "Impor androidx.compose.runtime.saveable.rememberSaveable hilang",
            src.contains("import androidx.compose.runtime.saveable.rememberSaveable"),
        )
    }
}
