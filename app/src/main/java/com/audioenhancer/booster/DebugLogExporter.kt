package com.audioenhancer.booster

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
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

    /** Jumlah baris BERMAKNA terakhir yang disimpan. Batch 243 (log device ketiga): satu siklus serah-terima efek ±200 baris
     *  (`setParameter():` 63%) -> 3000 baris hanya ±15 siklus (±1 menit geser video) dan penanda awal uji
     *  (`LIFECYCLE ui aktif=false`) bisa terpotong; 10.000 ±50 siklus, berkas ±1 MB (masih mudah dikirim). Batas atas dijaga tes. */
    internal const val MAX_LINES = 10_000

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
            ) + readAudioPathSnapshot(context)
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

    /** Batch 255 (komplikasi user: efek Boomly tak terdengar walau semua efek "sehat" di log): log aplikasi hanya melihat status efek di sisi
     *  aplikasi, bukan jalur keluaran yang dipilih sistem. Bila izin DUMP ada, tambahkan cuplikan `dumpsys media.audio_flinger` (thread keluaran
     *  + rantai efek + sesi), disaring ke baris relevan dan dibatasi. HANYA saat user menekan Ekspor (tanpa biaya baterai); tanpa izin = kosong. */
    private fun readAudioPathSnapshot(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED) return ""
        return try {
            val process = ProcessBuilder("dumpsys", "media.audio_flinger").redirectErrorStream(true).start()
            try {
                val kept = ArrayList<String>()
                var read = 0
                process.inputStream.bufferedReader().use { reader ->
                    for (line in reader.lineSequence()) {
                        if (++read > DebugLogFormat.PATH_MAX_READ) break
                        if (DebugLogFormat.isAudioPathLine(line) && kept.size < DebugLogFormat.PATH_MAX_LINES) kept.add(line.trimEnd())
                    }
                }
                DebugLogFormat.buildAudioPathSection(kept, read)
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cuplikan jalur audio gagal", e)
            DebugLogFormat.buildAudioPathSection(emptyList(), 0)
        }
    }

    private fun buildHeader(nowMillis: Long): List<String> = listOf(
        "Boomly debug log",
        "waktu: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(nowMillis))}",
        "perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
        "layanan efek: ${if (AudioEnhancerService.isRunning) "aktif" else "mati"}",
        "catatan: baris banjir AudioEffect command()/commandExecuted()/getParameter dan derau UI (VRI, DecorView, dst.) disaring"
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

    /** Banjir capture Visualizer (`command() ... cmdCode = 65536` ~10x/dtk) dan balasannya `commandExecuted()`.
     *  Batch 242 (log device kedua: `getParameter:` = 884 dari 3000 baris, 29%): +`getParameter: param: X, param2: Y` — gema
     *  baca-ulang parameter oleh Effect Guard (8 baris per pemeriksaan) yang HANYA memuat nomor parameter, tanpa nilai,
     *  jadi nol informasi; hasil pemeriksaannya tetap tercatat di baris `EffectGuard[...]` milik layanan. `setParameter():`
     *  SENGAJA tetap disimpan (bukti penulisan parameter saat serah-terima). */
    private val AUDIO_EFFECT_FLOOD =
        Regex(TS + """[VDIWEF]/AudioEffect\(\s*\d+\): (command\(\)|commandExecuted\(\)|getParameter:)""")

    /** Derau jendela/UI sistem yang tak berguna untuk analisis audio (dan memuat koordinat sentuhan). */
    private val UI_NOISE = Regex(
        TS + """[VDIWEF]/(VRI\[[^\]]*\]|DecorView|InsetsController|ImeTracker|BLASTBufferQueue|""" +
            """os\.SingleLiceFactory|os\.LiceInfo|TranClassInfo|GraphicsEnvironment|WindowOnBackDispatcher)\("""
    )

    /** True untuk baris derau yang dibuang dari laporan; baris lain (efek, serah-terima, crash) selalu dipertahankan. */
    fun isNoise(line: String): Boolean = AUDIO_EFFECT_FLOOD.containsMatchIn(line) || UI_NOISE.containsMatchIn(line)

    /** Batch 255: baris `dumpsys media.audio_flinger` yang menjelaskan jalur keluaran: thread keluaran (tipe MIXER/OFFLOAD/DIRECT/MMAP), rantai efek,
     *  efek, sesi. Disaring supaya laporan tak membengkak. */
    private val AUDIO_PATH_KEYS = Regex(
        "output thread|input thread|thread type|effect chain|effect|chain|session|offload|direct|mmap|standby|sample rate|devices?:",
        RegexOption.IGNORE_CASE
    )

    const val PATH_MAX_LINES = 600
    const val PATH_MAX_READ = 60_000

    fun isAudioPathLine(line: String): Boolean = line.isNotBlank() && AUDIO_PATH_KEYS.containsMatchIn(line)

    /** Bagian akhir laporan. [readLines] = jumlah baris dump yang dibaca (0 + kosong = gagal/tak terbaca). */
    fun buildAudioPathSection(lines: List<String>, readLines: Int): String = buildString {
        appendLine("---- jalur audio (dumpsys media.audio_flinger, disaring ${lines.size} dari $readLines baris) ----")
        if (lines.isEmpty()) appendLine("(kosong: dump tak terbaca atau tak ada baris relevan)") else lines.forEach { appendLine(it) }
    }

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
