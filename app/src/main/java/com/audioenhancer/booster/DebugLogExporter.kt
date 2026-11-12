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
    private const val VERIFIER_TIMEOUT_SECONDS = 3L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val PROFILE_ENTRY = "assets/dexopt/baseline.prof"
    private const val PROFILE_META_ENTRY = "assets/dexopt/baseline.profm"

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
            ) + readAvailableEffects() + readProfileStatus(context) + readAudioPathSnapshot(context)
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

    /** Batch 266 (user: "Ada lagi gak konfigurasi audio yang mirip reverb namun belum kepasang?" -> "Mulai dari langkah yang konkrit!!"): daftar SEMUA
     *  efek audio yang dilaporkan device lewat `AudioEffect.queryEffects()` (API publik, TANPA izin khusus, tanpa dump). Dump `audio_flinger` hanya
     *  menampilkan efek yang SEDANG dipakai, jadi tak bisa menjawab apakah efek lain (mis. Environmental Reverb) tersedia di device ini. Gagal aman:
     *  galat -> bagian berisi alasan, laporan tetap terbit. */
    private fun readAvailableEffects(): String = try {
        val infos = android.media.audiofx.AudioEffect.queryEffects()?.map { d ->
            DebugLogFormat.EffectInfo(
                typeUuid = d.type?.toString().orEmpty(),
                uuid = d.uuid?.toString().orEmpty(),
                connectMode = d.connectMode.orEmpty(),
                name = d.name.orEmpty(),
                implementor = d.implementor.orEmpty()
            )
        }
        DebugLogFormat.buildEffectsSection(infos, null)
    } catch (e: Exception) {
        Log.w(TAG, "queryEffects gagal", e)
        DebugLogFormat.buildEffectsSection(null, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }

    /** Batch 309 (request user: pilih "Diagnostik ProfileVerifier di Log Debug" setelah `dumpsys package dexopt` berstatus speed-profile
     *  [reason=cmdline]: dipaksa perintah, belum membuktikan ProfileInstaller memasang Baseline Profile di Android 16): bukti MENTAH dari tiga sisi,
     *  tanpa tafsir: (1) `ProfileVerifier` (kode hasil ProfileInstaller), (2) ukuran entri profil di dalam APK (bukti build AGP/R8 benar-benar
     *  membuat profil), (3) ukuran berkas `oat/` (odex/vdex/art; kecil = hanya diverifikasi, besar = terkompilasi). Gagal aman per bagian:
     *  galat -> teks alasan / `-1` / `null`, laporan tetap terbit (`LinkageError` ikut ditangkap supaya ekspor tak bisa crash). */
    private fun readProfileStatus(context: Context): String {
        val sourceDir = context.applicationInfo.sourceDir
        return DebugLogFormat.buildProfileSection(
            DebugLogFormat.ProfileInfo(
                processAgeSeconds = (android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / MILLIS_PER_SECOND,
                verifier = readProfileVerifier(),
                apkProfileBytes = apkEntryBytes(sourceDir, PROFILE_ENTRY),
                apkProfileMetaBytes = apkEntryBytes(sourceDir, PROFILE_META_ENTRY),
                oatFiles = readOatFiles(sourceDir)
            )
        )
    }

    private fun readProfileVerifier(): String = try {
        val status = androidx.profileinstaller.ProfileVerifier.getCompilationStatusAsync()
            .get(VERIFIER_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        DebugLogFormat.verifierLabel(status.profileInstallResultCode, status.isCompiledWithProfile, status.hasProfileEnqueuedForCompilation())
    } catch (e: Exception) {
        Log.w(TAG, "ProfileVerifier gagal", e)
        "gagal: ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
    } catch (e: LinkageError) {
        Log.w(TAG, "ProfileVerifier gagal (LinkageError)", e)
        "gagal: ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
    }

    private fun apkEntryBytes(apkPath: String, entryName: String): Long = try {
        java.util.zip.ZipFile(apkPath).use { zip -> zip.getEntry(entryName)?.size ?: -1L }
    } catch (e: Exception) {
        Log.w(TAG, "Baca entri APK $entryName gagal", e)
        -1L
    }

    /** `oat/` ada di sebelah `base.apk` (`/data/app/.../oat/<isa>/base.odex|vdex|art`). Kunci = jalur relatif terhadap `oat/`. */
    private fun readOatFiles(apkPath: String): Map<String, Long>? = try {
        val oatDir = java.io.File(java.io.File(apkPath).parentFile, "oat")
        if (!oatDir.isDirectory) null else oatDir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(oatDir).path to it.length() }
    } catch (e: Exception) {
        Log.w(TAG, "Baca folder oat gagal", e)
        null
    }

    /** Batch 255 (komplikasi user: efek Boomly tak terdengar walau semua efek "sehat" di log): log aplikasi hanya melihat status efek di sisi
     *  aplikasi, bukan jalur keluaran yang dipilih sistem. Bila izin DUMP ada, tambahkan cuplikan `dumpsys media.audio_flinger` (thread keluaran +
     *  rantai efek + baris track) dan `dumpsys media.audio_policy` (output + flag + efek). Batch 256: TANPA saringan kata kunci (v255 membuang baris
     *  track/efek yang justru dibutuhkan), hanya baris kosong/derau timestamp dibuang dan jumlah baris dibatasi. HANYA saat user menekan Ekspor
     *  (tanpa biaya baterai); tanpa izin = kosong. */
    private fun readAudioPathSnapshot(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, DumpProbe.PERMISSION) != PackageManager.PERMISSION_GRANTED) return ""
        return DebugLogFormat.AUDIO_DUMPS.joinToString("") { dump -> readDump(dump) }
    }

    private fun readDump(dump: DebugLogFormat.AudioDump): String = try {
        val process = ProcessBuilder("dumpsys", dump.service).redirectErrorStream(true).start()
        try {
            val kept = ArrayList<String>()
            var read = 0
            process.inputStream.bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    if (++read > DebugLogFormat.PATH_MAX_READ) break
                    if (DebugLogFormat.isAudioPathLine(line) && kept.size < dump.maxLines) kept.add(line.trimEnd())
                }
            }
            DebugLogFormat.buildAudioPathSection(dump.service, kept, read)
        } finally {
            process.destroy()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cuplikan ${dump.service} gagal", e)
        DebugLogFormat.buildAudioPathSection(dump.service, emptyList(), 0)
    }

    private fun buildHeader(nowMillis: Long): List<String> = listOf(
        "Boomly debug log",
        "waktu: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(nowMillis))}",
        "perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
        "layanan efek: ${if (AudioEnhancerService.isRunning) "aktif" else "mati"}",
        "catatan: baris banjir AudioEffect command()/commandExecuted()/getParameter dan derau UI (VRI, DecorView, dst.) disaring; " +
            "blok galat pembuatan efek -3 yang SUDAH ditangani EffectRebind (efek lama dipertahankan) diringkas: baris RuntimeException mentah " +
            "dan baris EffectRebind tetap, gema framework/baris \"tidak tersedia\"/baris \"at\" dibuang"
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

    /** Batch 317 (request user: pilih "Saring baris E efek -3 di Log Debug" setelah triase v316): saat efek utama pindah ke sesi pemutar yang
     *  sedang memutar, AudioFlinger kadang menolak membuat efek baru (status -3), sehingga satu kegagalan menulis ±17 baris `E`
     *  (gema framework `AudioEffects-JNI` + `AudioEffect-JAVA`, baris "X tidak tersedia di device ini", `RuntimeException`, ±13 baris `at`) padahal
     *  jalur gagal-aman bekerja ("EffectRebind: X baru gagal dibuat, efek lama dipertahankan") dan efek sehat ±1 dtk kemudian (log v315/v316).
     *
     *  Penyaringan BERBASIS BUKTI, bukan tebakan kata kunci: satu blok dibuang HANYA bila tepat sesudahnya (pid sama) ada baris `W` EffectRebind
     *  di atas, DAN blok itu berbentuk persis: [gema framework -3] + ["X tidak tersedia di device ini", X = nama di baris EffectRebind] +
     *  `java.lang.RuntimeException: Cannot initialize effect engine for type: <uuid> Error: -3` + baris `at`. Dibuang: gema framework, baris
     *  "tidak tersedia" (menyesatkan di jalur ini: efeknya ada, hanya gagal pindah), dan baris `at`. DIPERTAHANKAN: baris RuntimeException
     *  MENTAH (tipe efek + kode -3) dan baris EffectRebind. Kegagalan pembuatan di luar jalur ini (mis. efek memang tak ada di device saat
     *  `attachEffects`, kode selain -3, blok yang bentuknya berbeda) TIDAK punya baris EffectRebind -> tetap utuh dengan stacktrace penuh.
     *  Hanya berlaku pada ekspor; logcat sistem dan `CrashLogger` tak disentuh. */
    fun dropHandledEffectFailures(lines: List<String>): List<String> {
        val dropped = HashSet<Int>()
        for (index in lines.indices) {
            val block = handledFailureBlockAt(lines, index) ?: continue
            dropped.addAll(block)
        }
        return if (dropped.isEmpty()) lines else lines.filterIndexed { index, _ -> index !in dropped }
    }

    private const val FAILURE_ECHO_LOOKBACK = 6
    private const val REBIND_KEPT_OLD_HINT = "baru gagal dibuat"
    private const val UNAVAILABLE_SUFFIX = " tidak tersedia di device ini"
    private const val STACK_FRAME_PREFIX = "\tat "

    private val APP_LOG = Regex(TS + """([VDIWEF])/AudioEnhancerService\(\s*(\d+)\): (.*)""")
    private val REBIND_KEPT_OLD = Regex("""EffectRebind: (.+) baru gagal dibuat, efek lama dipertahankan""")
    private val EFFECT_INIT_FAILURE = Regex("""java\.lang\.RuntimeException: Cannot initialize effect engine for type: \S+ Error: -3""")
    private val FRAMEWORK_ECHO = Regex(
        TS + """E/(?:AudioEffects-JNI|AudioEffect-JAVA)\(\s*(\d+)\): """ +
            """(?:AudioEffect initCheck failed -3|Error code -3 when initializing AudioEffect\.)"""
    )

    /** Satu baris log milik layanan ini, dipecah: level (V/D/I/W/E/F), pid (tanpa spasi), pesan. */
    private class AppLog(val level: String, val pid: String, val message: String)

    private fun parseAppLog(line: String): AppLog? {
        val match = APP_LOG.find(line) ?: return null
        return AppLog(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }

    /** Pesan baris [index] bila itu baris layanan ini dengan [pid] dan [level] yang diminta; selain itu `null`. */
    private fun appMessage(lines: List<String>, index: Int, pid: String, level: String): String? {
        val log = if (index >= 0) parseAppLog(lines[index]) else null
        return if (log != null && log.pid == pid && log.level == level) log.message else null
    }

    /** Indeks baris yang dibuang bila [markerIndex] adalah baris EffectRebind "efek lama dipertahankan" dengan blok galat -3 yang berbentuk
     *  persis seperti di [dropHandledEffectFailures]; `null` (tak ada yang dibuang) untuk baris lain atau bentuk yang berbeda. */
    private fun handledFailureBlockAt(lines: List<String>, markerIndex: Int): List<Int>? {
        if (!lines[markerIndex].contains(REBIND_KEPT_OLD_HINT)) return null
        val marker = parseAppLog(lines[markerIndex]) ?: return null
        if (marker.level != "W") return null
        val name = REBIND_KEPT_OLD.matchEntire(marker.message)?.groupValues?.get(1) ?: return null
        val dropped = ArrayList<Int>()
        var index = markerIndex - 1
        while (appMessage(lines, index, marker.pid, "E")?.startsWith(STACK_FRAME_PREFIX) == true) {
            dropped.add(index)
            index--
        }
        val exception = appMessage(lines, index, marker.pid, "E")
        if (exception == null || !EFFECT_INIT_FAILURE.matches(exception)) return null
        index--
        val unavailable = appMessage(lines, index, marker.pid, "E")
        if (unavailable != null && unavailable.endsWith(UNAVAILABLE_SUFFIX)) {
            if (!unavailable.startsWith(name)) return null
            dropped.add(index)
            index--
        }
        var steps = 0
        while (index >= 0 && steps < FAILURE_ECHO_LOOKBACK && parseAppLog(lines[index]) == null) {
            if (FRAMEWORK_ECHO.find(lines[index])?.groupValues?.get(1) == marker.pid) dropped.add(index)
            index--
            steps++
        }
        return dropped
    }

    /** Batch 256: dump sistem yang dilampirkan ke ekspor; [maxLines] membatasi ukuran laporan per dump. */
    data class AudioDump(val service: String, val maxLines: Int)

    val AUDIO_DUMPS: List<AudioDump> = listOf(
        AudioDump("media.audio_flinger", 2_500),
        AudioDump("media.audio_policy", 1_500)
    )

    const val PATH_MAX_READ = 60_000

    /** Derau timestamp dump thread (panjang, tak berguna); sisanya dipertahankan. */
    private val DUMP_NOISE = Regex("ExtendedTimestamp|BOOTTIME offset")

    fun isAudioPathLine(line: String): Boolean = line.isNotBlank() && !DUMP_NOISE.containsMatchIn(line)

    /** Satu bagian laporan. [readLines] = jumlah baris dump yang dibaca (0 + kosong = gagal/tak terbaca). */
    fun buildAudioPathSection(title: String, lines: List<String>, readLines: Int): String = buildString {
        appendLine("---- jalur audio (dumpsys $title, ${lines.size} dari $readLines baris) ----")
        if (lines.isEmpty()) appendLine("(kosong: dump tak terbaca atau tak ada baris)") else lines.forEach { appendLine(it) }
    }

    /** Batch 266: satu efek dari `AudioEffect.queryEffects()` (nilai sudah jadi teks supaya bisa dites di JVM). */
    data class EffectInfo(val typeUuid: String, val uuid: String, val connectMode: String, val name: String, val implementor: String)

    const val ENVIRONMENTAL_REVERB = "Environmental Reverb"
    const val UNKNOWN_EFFECT_TYPE = "tipe tak dikenal"
    private const val MAX_EFFECT_LINES = 300

    /** UUID tipe efek publik AOSP (`AudioEffect.EFFECT_TYPE_*`) -> nama. Tipe lain (vendor, Spatializer, Haptic, dst.) TIDAK ditebak: dicetak
     *  sebagai [UNKNOWN_EFFECT_TYPE] bersama UUID mentahnya, supaya log tetap lengkap tanpa label yang salah. */
    private val KNOWN_EFFECT_TYPES: Map<String, String> = mapOf(
        "0bed4300-ddd6-11db-8f34-0002a5d5c51b" to "Equalizer",
        "0634f220-ddd4-11db-a0fc-0002a5d5c51b" to "Bass Boost",
        "37cc2c00-dddd-11db-8577-0002a5d5c51b" to "Virtualizer",
        "47382d60-ddd8-11db-bf3a-0002a5d5c51b" to "Preset Reverb",
        "c2e5d5f0-94bd-4763-9cac-4e234d06839e" to ENVIRONMENTAL_REVERB,
        "fe3199be-aed0-413f-87bb-11260eb63cf1" to "Loudness Enhancer",
        "7261676f-6d75-7369-6364-28e2fd3ac39e" to "Dynamics Processing",
        "e46b26a0-dddd-11db-8afd-0002a5d5c51b" to "Visualizer",
        "7b491460-8d4d-11e0-bd61-0002a5d5c51b" to "Acoustic Echo Canceler",
        "0a8abfe0-654c-11e0-ba26-0002a5d5c51b" to "Automatic Gain Control",
        "58b4b260-8e06-11e0-aa8e-0002a5d5c51b" to "Noise Suppressor"
    )

    fun effectTypeLabel(typeUuid: String): String = KNOWN_EFFECT_TYPES[typeUuid.trim().lowercase(Locale.US)] ?: UNKNOWN_EFFECT_TYPE

    /** Bagian laporan "efek audio tersedia". [effects] `null` = `queryEffects()` mengembalikan null atau gagal ([error] berisi alasan). */
    fun buildEffectsSection(effects: List<EffectInfo>?, error: String?): String = buildString {
        if (effects == null) {
            appendLine("---- efek audio tersedia (AudioEffect.queryEffects) ----")
            appendLine(if (error != null) "(gagal dibaca: $error)" else "(queryEffects mengembalikan null: device tak melaporkan daftar efek)")
            return@buildString
        }
        val rows = effects.map { it to effectTypeLabel(it.typeUuid) }.sortedWith(compareBy({ it.second }, { it.first.name }))
        appendLine("---- efek audio tersedia (AudioEffect.queryEffects, ${effects.size} efek) ----")
        val counts = rows.groupingBy { it.second }.eachCount()
        appendLine("ringkasan: " + if (counts.isEmpty()) "(kosong)" else counts.entries.joinToString(", ") { "${it.key} x${it.value}" })
        val envCount = counts[ENVIRONMENTAL_REVERB] ?: 0
        appendLine("$ENVIRONMENTAL_REVERB tersedia: " + if (envCount > 0) "ya ($envCount entri)" else "TIDAK (device tak melaporkan tipe ini)")
        rows.take(MAX_EFFECT_LINES).forEach { (e, label) ->
            appendLine("$label | ${e.name} | mode=${e.connectMode} | implementor=${e.implementor} | type=${e.typeUuid} | uuid=${e.uuid}")
        }
        if (rows.size > MAX_EFFECT_LINES) appendLine("(${rows.size - MAX_EFFECT_LINES} efek lain tak ditampilkan)")
    }

    /** Batch 309: bahan bagian "profil kompilasi", semua nilai mentah. Ukuran `-1` = entri tak ada/tak terbaca; [oatFiles] `null` = folder `oat` tak ada/tak terbaca. */
    data class ProfileInfo(
        val processAgeSeconds: Long,
        val verifier: String,
        val apkProfileBytes: Long,
        val apkProfileMetaBytes: Long,
        val oatFiles: Map<String, Long>?
    )

    /** Kode hasil `ProfileVerifier` dicetak MENTAH (tanpa tafsir buatan); tafsir dilakukan saat log dibaca. */
    fun verifierLabel(resultCode: Int, compiledWithProfile: Boolean, enqueuedForCompilation: Boolean): String =
        "kode=$resultCode compiledWithProfile=$compiledWithProfile enqueuedForCompilation=$enqueuedForCompilation"

    private fun sizeLabel(bytes: Long): String = if (bytes < 0) "TIDAK ADA / tak terbaca" else "$bytes byte"

    /** Bagian laporan "profil kompilasi" (Batch 309): umur proses menunjukkan apakah jeda ProfileInstaller (sekitar 5 dtk setelah start) sudah lewat. */
    fun buildProfileSection(info: ProfileInfo): String = buildString {
        appendLine("---- profil kompilasi (ProfileVerifier, isi APK, berkas oat) ----")
        appendLine("umur proses: ${info.processAgeSeconds} dtk")
        appendLine("ProfileVerifier: ${info.verifier}")
        appendLine("APK assets/dexopt/baseline.prof: ${sizeLabel(info.apkProfileBytes)}")
        appendLine("APK assets/dexopt/baseline.profm: ${sizeLabel(info.apkProfileMetaBytes)}")
        val oat = info.oatFiles
        when {
            oat == null -> appendLine("oat: (folder tak ada atau tak terbaca)")
            oat.isEmpty() -> appendLine("oat: (folder kosong)")
            else -> oat.toSortedMap().forEach { (path, bytes) -> appendLine("oat/$path: $bytes byte") }
        }
    }

    /** `aep_debug_yyyyMMdd_HHmmss.txt` — mudah diurutkan dan dikenali di File Manager. */
    fun fileName(timestampMillis: Long): String =
        "aep_debug_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))}.txt"

    /** Header + baris logcat. Blok galat efek yang sudah ditangani diringkas dulu ([dropHandledEffectFailures], Batch 317), lalu bila baris
     *  melebihi [maxLines] yang DIPERTAHANKAN adalah yang PALING BARU. */
    fun buildReport(header: List<String>, logLines: List<String>, maxLines: Int): String {
        val compact = dropHandledEffectFailures(logLines)
        val kept = if (compact.size > maxLines) compact.takeLast(maxLines) else compact
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
