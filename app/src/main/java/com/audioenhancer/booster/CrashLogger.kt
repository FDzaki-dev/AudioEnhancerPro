package com.audioenhancer.booster

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Logger crash lokal sederhana — sebelum ini, kalau service crash di background,
 *  satu-satunya jejak yang tersisa adalah notifikasi "Audio Booster aktif" yang
 *  tiba-tiba hilang, tanpa penjelasan kenapa. Sekarang stack trace disimpan ke file
 *  internal supaya bisa dilihat langsung dari dalam app saat troubleshooting. */
object CrashLogger {
    private const val CRASH_DIR = "crash_logs"
    private const val MAX_LOGS = 5

    /** Panggil sekali di Application.onCreate(), sebelum komponen lain jalan. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, throwable)
            } catch (_: Exception) {
                // Logger sendiri tidak boleh ikut bikin crash tambahan.
            }
            // Tetap teruskan ke handler default (sistem) supaya perilaku crash Android
            // yang biasa (dialog "App berhenti", restart, dsb) tidak berubah.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")
        file.writeText(throwable.stackTraceToString())

        // Rotasi: simpan cuma MAX_LOGS file terbaru, biar internal storage gak numpuk.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    /** File crash paling baru yang tersimpan, null kalau belum pernah ada crash. */
    fun latestCrashLog(context: Context): File? {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) return null
        return dir.listFiles()?.maxByOrNull { it.lastModified() }
    }

    /** True kalau ada crash log yang lebih baru dari terakhir kali user lihat. */
    fun hasUnseenCrash(context: Context): Boolean {
        val latest = latestCrashLog(context) ?: return false
        return latest.lastModified() > PrefsHelper.getLastSeenCrashTimestamp(context)
    }

    fun markCrashSeen(context: Context) {
        val latest = latestCrashLog(context) ?: return
        PrefsHelper.setLastSeenCrashTimestamp(context, latest.lastModified())
    }

    fun deleteAllLogs(context: Context) {
        File(context.filesDir, CRASH_DIR).listFiles()?.forEach { it.delete() }
    }
}
