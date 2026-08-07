package com.audioenhancer.booster

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Logger crash lokal — sebelum ini, kalau service crash di background, satu-satunya
 *  jejak yang tersisa adalah notifikasi "Audio Booster aktif" yang tiba-tiba hilang,
 *  tanpa penjelasan kenapa.
 *
 *  Batch 27: dipindah dari `filesDir` internal (tersembunyi, cuma bisa dilihat lewat
 *  dialog in-app) ke `Documents/AudioEnhancerPro/logs/` via MediaStore (API 29+) — file
 *  log sekarang JUGA bisa diakses langsung dari app File Manager/Files by Google mana pun
 *  tanpa root, TANPA minta permission storage legacy apapun (`WRITE_EXTERNAL_STORAGE`),
 *  murni `ContentResolver.insert()` ke koleksi `MediaStore.Files` — ini yang dimaksud
 *  scoped storage: app bisa nulis ke folder publik miliknya sendiri tanpa permission,
 *  asal lewat MediaStore API, bukan `java.io.File` path absolut.
 *  Di API < 29 (minSdk 24 project ini, MediaStore.Files RELATIVE_PATH belum ada), fallback
 *  otomatis ke `filesDir/crash_logs/` seperti sebelumnya (tetap tanpa permission apapun,
 *  cuma gak nongol di File Manager — keterbatasan versi Android, bukan bug).
 */
object CrashLogger {
    private const val LEGACY_DIR = "crash_logs"
    private const val APP_FOLDER = "AudioEnhancerPro"
    // MediaStore selalu normalisasi RELATIVE_PATH pakai trailing slash — WAJIB disertakan
    // di sini juga, biar query SELECT pas prune/list match persis apa yang tersimpan.
    // Bukan `const val` — nilainya bergantung `Environment.DIRECTORY_DOCUMENTS`, yaitu
    // field Android runtime (String biasa), BUKAN compile-time constant Kotlin. `const`
    // WAJIB nilai yang bisa di-resolve compiler saat kompilasi (literal/const lain), kalau
    // dipaksa `const` di sini compiler nolak ("Const 'val' initializer should be a constant
    // value") — ini yang bikin CI v1.66 gagal di compileDebugKotlin.
    private val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/logs/"
    private const val MAX_LOGS = 50

    /** Referensi 1 crash log, menyatukan 2 sumber (MediaStore Uri di API 29+, File legacy
     *  di API lama) di balik 1 tipe yang sama — supaya pemanggil (CrashBanner) gak perlu
     *  tahu/peduli sumbernya dari mana. */
    data class CrashLogEntry(
        val displayName: String,
        val lastModifiedMillis: Long,
        private val uri: Uri? = null,
        private val file: File? = null
    ) {
        fun readText(context: Context): String = runCatching {
            when {
                uri != null -> context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                file != null -> file.readText()
                else -> ""
            }
        }.getOrDefault("")
    }

    /** Panggil sekali di Application.onCreate(), sebelum komponen lain jalan. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Fail-safe: logger sendiri TIDAK BOLEH ikut bikin crash tambahan atau
            // menelan crash asli — apapun yang terjadi di sini, selalu diteruskan ke
            // handler default (sistem) di baris paling akhir lambda ini.
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (_: Exception) {
                // Diamkan — penulisan log gagal (mis. MediaStore lagi bermasalah) TIDAK
                // BOLEH mencegah proses crash normal Android (dialog "App berhenti", dst).
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildLogContent(context: Context, thread: Thread, throwable: Throwable, timestamp: Long): String {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("unknown")
        val header = buildString {
            appendLine("Version: $versionName")
            appendLine("OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))}")
            appendLine("Thread: ${thread.name}")
            appendLine("---")
        }
        return header + throwable.stackTraceToString()
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val content = buildLogContent(context, thread, throwable, timestamp)
        // UUID di nama file (bukan cuma timestamp) — jaga-jaga 2 crash beruntun di
        // milidetik yang sama (mis. crash A memicu crash B saat unwind) supaya TIDAK
        // saling menimpa nama file yang identik.
        val fileNameTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val fileName = "crash_${fileNameTimestamp}_${UUID.randomUUID()}.txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeCrashLogMediaStore(context, fileName, content)
        } else {
            writeCrashLogLegacy(context, fileName, content)
        }
    }

    private fun writeCrashLogMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        pruneMediaStoreLogs(context)
    }

    private fun writeCrashLogLegacy(context: Context, fileName: String, content: String) {
        val dir = File(context.filesDir, LEGACY_DIR)
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeText(content)
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    /** FIFO retention: simpan cuma MAX_LOGS file terbaru di koleksi MediaStore kita,
     *  biar Documents/AudioEnhancerPro/logs/ gak numpuk selamanya. */
    private fun pruneMediaStoreLogs(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(RELATIVE_PATH)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        resolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            var index = 0
            while (cursor.moveToNext()) {
                if (index >= MAX_LOGS) {
                    val id = cursor.getLong(idCol)
                    resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
                }
                index++
            }
        }
    }

    /** File crash paling baru yang tersimpan, null kalau belum pernah ada crash.
     *  Batch 29: SELURUH isi fungsi ini (termasuk turunan MediaStore/legacy) dibungkus
     *  `runCatching` — sebelumnya TIDAK, dan itu jadi penyebab crash-loop nyata di startup
     *  (lihat detail insiden "Invalid token LIMIT" di CHANGELOG.md Batch 29). Fungsi baca
     *  dipanggil LANGSUNG dari inisialisasi state Composable (`CrashBanner`), di luar
     *  try-catch `install()` yang cuma melindungi jalur TULIS — kalau ContentProvider OEM
     *  manapun nolak query dengan cara yang gak terduga lagi di masa depan, sekarang app
     *  TIDAK ikut crash, cuma banner crash gak muncul (gagal aman, bukan gagal total). */
    fun latestCrashLog(context: Context): CrashLogEntry? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            latestFromMediaStore(context)
        } else {
            latestFromLegacy(context)
        }
    }.getOrNull()

    private fun latestFromMediaStore(context: Context): CrashLogEntry? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(RELATIVE_PATH)
        // Batch 29 fix (insiden nyata, lihat CHANGELOG): SEBELUMNYA sortOrder di sini
        // ditempeli "LIMIT 1" mentah (`"$DATE_MODIFIED DESC LIMIT 1"`) — trik ini KADANG
        // diterima ContentProvider AOSP standar, tapi provider OEM tertentu (kejadian nyata:
        // Infinix, Android 16/SDK 36) MENOLAKNYA dengan `IllegalArgumentException: Invalid
        // token LIMIT`, dan itu terjadi SINKRON di main thread saat Compose attach → app
        // crash total di startup. `sortOrder` bukan tempat yang valid buat clause SQL bebas
        // di semua ContentProvider — dihapus, cukup `moveToFirst()` dari hasil DESC (data
        // maks 50 baris karena retensi FIFO, jadi tanpa LIMIT pun query tetap murah).
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        resolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                // DATE_MODIFIED MediaStore tersimpan dalam DETIK (Unix epoch), BUKAN milis —
                // WAJIB dikali 1000 supaya sebanding dengan File.lastModified() (milis) yang
                // dipakai jalur legacy & dibandingkan ke PrefsHelper.getLastSeenCrashTimestamp.
                val dateModifiedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                val uri = ContentUris.withAppendedId(collection, id)
                return CrashLogEntry(displayName = name, lastModifiedMillis = dateModifiedSeconds * 1000, uri = uri)
            }
        }
        return null
    }

    private fun latestFromLegacy(context: Context): CrashLogEntry? {
        val dir = File(context.filesDir, LEGACY_DIR)
        if (!dir.exists()) return null
        val file = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
        return CrashLogEntry(displayName = file.name, lastModifiedMillis = file.lastModified(), file = file)
    }

    /** True kalau ada crash log yang lebih baru dari terakhir kali user lihat. */
    fun hasUnseenCrash(context: Context): Boolean {
        val latest = latestCrashLog(context) ?: return false
        return latest.lastModifiedMillis > PrefsHelper.getLastSeenCrashTimestamp(context)
    }

    fun markCrashSeen(context: Context) {
        val latest = latestCrashLog(context) ?: return
        PrefsHelper.setLastSeenCrashTimestamp(context, latest.lastModifiedMillis)
    }

    fun deleteAllLogs(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                val args = arrayOf(RELATIVE_PATH)
                resolver.delete(collection, selection, args)
            } else {
                File(context.filesDir, LEGACY_DIR).listFiles()?.forEach { it.delete() }
            }
        }
    }
}
