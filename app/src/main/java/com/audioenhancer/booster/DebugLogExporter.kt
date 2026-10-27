package com.audioenhancer.booster

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Batch 240 (request user: "saya orangnya gak suka yang ribet-ribet"): ekspor log debug tanpa adb/PC/izin khusus.
 *
 * Sejak Android 4.1 sebuah aplikasi boleh membaca logcat MILIKNYA SENDIRI tanpa izin `READ_LOGS` (yang terbaca hanya
 * baris dari UID aplikasi ini). Jadi satu tombol di Pengaturan cukup: baca logcat sendiri -> simpan ke
 * `Documents/AudioEnhancerPro/debug/` lewat MediaStore (pola SAMA `CrashLogger`, tanpa izin storage), lalu user
 * mengirim file itu. Folder SENGAJA dipisah dari `.../logs/` milik `CrashLogger`: banner crash membaca folder itu
 * dan akan salah mengira log debug sebagai crash.
 *
 * Gagal aman: semua kegagalan -> `null` (tak melempar), jadi tombol ini tak pernah bisa membuat app crash. Hanya
 * API 29+ (MediaStore `RELATIVE_PATH`); di bawah itu mengembalikan `null` (prioritas proyek = Android 12+).
 */
object DebugLogExporter {
    private const val TAG = "DebugLogExporter"
    private const val APP_FOLDER = "AudioEnhancerPro"
    private const val MAX_FILES = 10

    /** Jumlah baris logcat terakhir yang diambil. Cukup untuk beberapa menit uji gapless, tetap kecil (~300 KB). */
    internal const val MAX_LINES = 3000

    // Bukan `const val`: bergantung `Environment.DIRECTORY_DOCUMENTS` (field runtime, bukan konstanta kompilasi).
    // MediaStore menormalisasi RELATIVE_PATH dengan trailing slash — WAJIB sama persis agar query prune cocok.
    private val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/debug/"

    /** Blocking (proses `logcat` + tulis file): panggil dari `Dispatchers.IO`.
     *  Return jalur tampilan file yang tersimpan, atau `null` bila gagal. */
    fun export(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val now = System.currentTimeMillis()
            val report = DebugLogFormat.buildReport(
                header = buildHeader(now),
                logLines = readOwnLogcat(),
                maxLines = MAX_LINES
            )
            val fileName = DebugLogFormat.fileName(now)
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            }
            val uri = resolver.insert(collection, values) ?: return null
            val written = resolver.openOutputStream(uri)?.use { it.write(report.toByteArray(Charsets.UTF_8)) }
            if (written == null) return null
            pruneOldFiles(context)
            "${RELATIVE_PATH}$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "Ekspor log debug gagal", e)
            null
        }
    }

    private fun buildHeader(nowMillis: Long): List<String> = listOf(
        "Boomly debug log",
        "waktu: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(nowMillis))}",
        "perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
        "layanan efek: ${if (AudioEnhancerService.isRunning) "aktif" else "mati"}",
        "catatan: baris banjir AudioEffect command()/commandExecuted() dan derau UI (VRI, DecorView, dst.) disaring"
    )

    /** `logcat -d` berhenti sendiri setelah mencetak buffer (tak menggantung). Batch 241: TANPA `-t` — penyaringan derau
     *  dilakukan sambil membaca dan hanya [MAX_LINES] baris BERMAKNA terakhir yang disimpan (sebelumnya `-t 3000` dihabiskan
     *  ~92% oleh banjir `AudioEffect command()` capture spektrum, jadi hanya ±3 menit log yang tersisa). */
    private fun readOwnLogcat(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-v", "time")
            .redirectErrorStream(true)
            .start()
        return try {
            val kept = ArrayDeque<String>()
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (!DebugLogFormat.isNoise(line)) {
                        if (kept.size >= MAX_LINES) kept.removeFirst()
                        kept.addLast(line)
                    }
                }
            }
            kept.toList()
        } finally {
            process.destroy()
        }
    }

    /** Simpan MAX_FILES log debug terbaru di folder kita, supaya tak menumpuk (pola SAMA `CrashLogger`).
     *  Dibungkus sendiri: gagal prune TIDAK boleh menggagalkan ekspor yang sudah tersimpan. */
    private fun pruneOldFiles(context: Context) {
        try {
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
                    if (index >= MAX_FILES) {
                        resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(idCol)), null, null)
                    }
                    index++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Prune log debug lama gagal (diabaikan)", e)
        }
    }
}

/** Bagian murni (tanpa Android) supaya bisa dites di JVM. */
internal object DebugLogFormat {

    private const val TS = """^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3} """

    /** Banjir capture Visualizer (`command() ... cmdCode = 65536` ~10x/dtk) dan balasannya `commandExecuted()`. */
    private val AUDIO_EFFECT_FLOOD = Regex(TS + """[VDIWEF]/AudioEffect\(\s*\d+\): (command\(\)|commandExecuted\(\))""")

    /** Derau jendela/UI sistem yang tak berguna untuk analisis audio (dan memuat koordinat sentuhan). */
    private val UI_NOISE = Regex(
        TS + """[VDIWEF]/(VRI\[[^\]]*\]|DecorView|InsetsController|ImeTracker|BLASTBufferQueue|""" +
            """os\.SingleLiceFactory|os\.LiceInfo|TranClassInfo|GraphicsEnvironment|WindowOnBackDispatcher)\("""
    )

    /** True untuk baris derau yang dibuang dari laporan; baris lain (efek, serah-terima, crash) selalu dipertahankan. */
    fun isNoise(line: String): Boolean = AUDIO_EFFECT_FLOOD.containsMatchIn(line) || UI_NOISE.containsMatchIn(line)

    /** `aep_debug_yyyyMMdd_HHmmss.txt` — mudah diurutkan dan dikenali di File Manager. */
    fun fileName(timestampMillis: Long): String =
        "aep_debug_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))}.txt"

    /** Header + baris logcat. Bila baris melebihi [maxLines], yang DIPERTAHANKAN adalah yang PALING BARU. */
    fun buildReport(header: List<String>, logLines: List<String>, maxLines: Int): String {
        val kept = if (logLines.size > maxLines) logLines.takeLast(maxLines) else logLines
        return buildString {
            header.forEach { appendLine(it) }
            appendLine("---- logcat Boomly (${kept.size} baris terakhir) ----")
            if (kept.isEmpty()) {
                appendLine("(kosong: belum ada log, atau buffer logcat sudah tertimpa. Ulangi skenario lalu ekspor segera.)")
            } else {
                kept.forEach { appendLine(it) }
            }
        }
    }
}
