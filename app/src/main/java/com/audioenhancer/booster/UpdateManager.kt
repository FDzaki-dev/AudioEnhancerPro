package com.audioenhancer.booster

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.sink
import okio.source
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update — diminta user eksplisit ("Tambahkan konfigurasi update langsung
 * dalam aplikasinya"). Sebelumnya cuma tercatat sebagai item yang SENGAJA DITUNDA
 * sejak MODE MAINTENANCE dimulai (lihat PROJECT_STATE.md, daftar fitur besar yang
 * "gak diinisiasi sendiri lagi" oleh Claude) — maintenance mode ngatur inisiatif
 * Claude, BUKAN larangan mutlak buat user, jadi permintaan eksplisit ini tetap
 * dikerjakan (aturan §4 di PROJECT_STATE.md).
 *
 * Sumber kebenaran versi: PAKAI ULANG skema versioning yang SUDAH ADA (Batch 42/65),
 * 0 field API baru dibutuhkan dari sisi GitHub:
 * - Judul tiap Release SELALU diakhiri "(Run #<run_number>)" — lihat step "Publish
 *   GitHub Release" di .github/workflows/build.yml.
 * - `versionCode` APK yang lagi jalan SEKARANG OTOMATIS = GITHUB_RUN_NUMBER dari run
 *   yang men-generate-nya (Batch 65, Versioning Lock). WAJIB dibaca runtime via
 *   PackageManager — BUKAN BuildConfig.VERSION_CODE (kelas BuildConfig DIMATIKAN
 *   total sejak Batch 41, `buildFeatures.buildConfig = false`).
 * Jadi "ada update?" = run_number di judul Release TERBARU > versionCode yang lagi
 * jalan. TIDAK perlu parse/bandingkan versionName (string) sama sekali — lebih
 * akurat, karena versionName TIDAK selalu naik tiap rilis CI (lihat catatan Batch 64
 * di PROJECT_STATE.md soal ini).
 *
 * Chunk streaming (Feature Lock §3, standing instruction user): body unduhan APK
 * (puluhan MB) WAJIB dibaca per-chunk lewat Okio (`Source.read(Buffer, Long)` loop),
 * DILARANG `readBytes()`/muat body sekaligus ke memori (resiko OOM di device
 * low-end). Panggilan API metadata Release (JSON, cuma beberapa KB) TIDAK kena
 * aturan ini — baca sekaligus di situ aman, bukan bagian yang beresiko OOM.
 */
object UpdateManager {

    private const val REPO_API_LATEST_RELEASE =
        "https://api.github.com/repos/FDzaki-dev/AudioEnhancerPro/releases/latest"
    private val RUN_NUMBER_REGEX = "Run #(\\d+)".toRegex()
    private const val DOWNLOAD_CHUNK_BYTES = 64L * 1024

    data class UpdateInfo(
        val versionName: String,
        val runNumber: Int,
        val downloadUrl: String,
        val fileName: String
    )

    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    /** Cek Release GitHub terbaru. Return `null` kalau versi yang lagi jalan SUDAH
     *  paling baru (atau lebih baru — mis. build lokal manual), APK asset tidak
     *  ketemu di Release itu, atau request gagal (network/parsing) — SEMUA exception
     *  SENGAJA ditelan jadi `null` di sini (bukan dilempar), karena check ini jalan
     *  otomatis diam-diam tiap app dibuka (lihat BoosterViewModel.init), gagalnya
     *  TIDAK seharusnya mengganggu user dengan pesan error. Beda dari `downloadApk()`
     *  di bawah yang dipicu eksplisit oleh tap user — itu WAJIB melempar exception
     *  biar kegagalannya kelihatan. WAJIB dipanggil dari coroutine (viewModelScope),
     *  bukan main thread langsung. */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(REPO_API_LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                // WAJIB: GitHub REST API menolak (403) request tanpa header User-Agent
                // sama sekali — beda dari kebanyakan API lain, ini persyaratan eksplisit
                // dokumentasi GitHub, bukan asumsi.
                setRequestProperty("User-Agent", "AudioEnhancerPro-UpdateChecker")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val runNumber = RUN_NUMBER_REGEX.find(json.optString("name"))
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@withContext null
            if (runNumber <= currentVersionCode(context)) return@withContext null

            val versionName = json.optString("tag_name").removePrefix("v").substringBefore("-run")

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            var apkName: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    apkName = name
                    break
                }
            }
            if (apkUrl.isNullOrEmpty() || apkName == null) return@withContext null

            UpdateInfo(versionName = versionName, runNumber = runNumber, downloadUrl = apkUrl, fileName = apkName)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Unduh APK Release via chunk streaming Okio ke `context.cacheDir/updates/` —
     *  folder ini yang diekspos FileProvider (`res/xml/file_paths.xml`, `cache-path
     *  name="updates"`) buat intent instalasi di `installApk()`. Isi folder lama
     *  dibersihkan dulu tiap unduhan baru, supaya APK basi tidak numpuk di cache.
     *  BEDA dari `checkForUpdate()`: exception di sini SENGAJA dilempar ulang (bukan
     *  ditelan), dipicu eksplisit oleh tap user, jadi kegagalannya wajib kelihatan
     *  (pemanggil — BoosterViewModel — yang tangkap & surface ke UI). */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val destFile = File(updatesDir, info.fileName)
        val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Unduhan gagal: HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong

            val source = connection.inputStream.source()
            val sink = destFile.sink()
            try {
                val buffer = Buffer()
                var readSoFar = 0L
                while (true) {
                    val read = source.read(buffer, DOWNLOAD_CHUNK_BYTES)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    readSoFar += read
                    if (totalBytes > 0) {
                        onProgress((readSoFar.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                    }
                }
                sink.flush()
            } finally {
                source.close()
                sink.close()
            }
            destFile
        } finally {
            connection.disconnect()
        }
    }

    /** Trigger instalasi via intent sistem standar (`ACTION_VIEW` + MIME APK).
     *  SENGAJA TIDAK cek/minta permission `REQUEST_INSTALL_PACKAGES` manual di sini
     *  — kalau belum diizinkan, PackageInstaller sistem sendiri yang menampilkan
     *  layar "Izinkan dari sumber ini" + tombol ke Settings, cek ulang manual di app
     *  cuma duplikasi UX yang sistem sudah handle. Context yang dipakai pemanggil
     *  (`getApplication()` dari ViewModel) BUKAN Activity Context, jadi WAJIB
     *  `FLAG_ACTIVITY_NEW_TASK`. */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
