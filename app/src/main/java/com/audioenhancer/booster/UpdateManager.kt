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
        val fileName: String,
        val releaseNotes: String
    )

    /** Batch 81 (diminta user, feedback "Cek Update" dikeluhkan gak informatif +
     *  maksa bolak-balik tab ke layar utama cuma buat lihat ada apa di update-nya):
     *  ambil ringkasan 1-baris dari body Release GitHub. Body itu SENDIRI sudah
     *  ringkasan (`release_notes.md` di build.yml, Batch 26/48 — heading + paragraf
     *  pembuka entry CHANGELOG.md TERATAS, hard cap 15 baris), BUKAN full
     *  CHANGELOG.md — jadi TIDAK perlu potong berat di sini, cuma 2 hal: (1) buang
     *  baris "---" + link CHANGELOG.md di ekornya (user eksplisit: summary in-app
     *  TIDAK boleh nampilin link changelog selengkapnya, cukup teksnya), (2) ambil
     *  baris heading "## ..." paling atas SEBAGAI ringkasan 1-baris (sudah cukup
     *  deskriptif dari pengamatan format CHANGELOG.md project ini — lihat
     *  "Cara update file ini" varian CHANGELOG), bukan seluruh paragraf (biar gak
     *  bertele-tele di kartu Settings yang sempit). Cap 160 char jaring pengaman
     *  kalau ada judul entry yang meleset panjang. Return "" (bukan exception/null)
     *  kalau body kosong/format tak terduga — pemanggil cukup skip baris summary. */
    private fun extractReleaseSummary(body: String): String {
        if (body.isBlank()) return ""
        val beforeLink = body.substringBefore("\n---").trim()
        val heading = beforeLink.lineSequence().firstOrNull { it.isNotBlank() }
            ?.removePrefix("## ")?.trim().orEmpty()
        return if (heading.length > 160) heading.take(157) + "…" else heading
    }

    /** Batch 74 (bugfix): hasil `fetchLatestRelease()` dipecah 3 kondisi yang SEBELUMNYA
     *  digepyok jadi satu nilai `null` — root cause laporan user "app bilang sudah versi
     *  terbaru padahal jelas belum": HTTP non-200 (mis. rate-limit 403 GitHub API
     *  unauthenticated, 60 req/jam per-IP — gampang kena di jaringan seluler ber-NAT),
     *  judul Release gagal match regex, ATAU asset APK tidak ketemu — SEMUA sebelumnya
     *  balik `null` yang PERSIS SAMA nilainya dengan "memang sudah versi terbaru"
     *  (`runNumber <= currentVersionCode`). `checkForUpdate()` (silent, auto tiap app
     *  dibuka) sengaja tetap perlakukan ketiganya sama (diam saja, TIDAK ganggu user) —
     *  itu bukan bug. Tapi `checkForUpdateManual()` (tombol "Cek Update Sekarang") wajib
     *  bisa BEDAKAN "sudah dicek, betul terbaru" vs "gagal dicek" (itu justru ALASAN
     *  tombol ini dibikin, lihat komentar `checkForUpdateManual` di bawah) — nilai
     *  `null` yang ambigu bikin janji itu tidak pernah benar-benar terpenuhi. */
    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        data object UpToDate : CheckResult()
        data object Failed : CheckResult()
    }

    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    /** Inti logic cek Release GitHub terbaru — DIPAKAI ULANG oleh 2 fungsi publik di
     *  bawah (`checkForUpdate`/`checkForUpdateManual`, Batch 73). Return `CheckResult`
     *  (Batch 74, sebelumnya `UpdateInfo?` — lihat komentar `CheckResult` soal kenapa):
     *  `UpToDate` kalau versi yang lagi jalan SUDAH paling baru (atau lebih baru — mis.
     *  build lokal manual); `Failed` kalau HTTP non-200/regex tidak match/APK asset
     *  tidak ketemu di Release. Exception jaringan/parsing JSON (timeout, DNS, JSON
     *  invalid, dll) SENGAJA TIDAK ditelan di sini — soal telan-atau-lempar itu
     *  keputusan tiap PEMANGGIL (lihat masing-masing di bawah), bukan tanggung jawab
     *  fungsi inti ini. */
    private suspend fun fetchLatestRelease(context: Context): CheckResult = withContext(Dispatchers.IO) {
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
            // Batch 74: non-200 (mis. 403 rate-limit GitHub API unauthenticated, 404, 5xx)
            // BUKAN "sudah terbaru" — itu gagal cek. Lihat komentar CheckResult di atas.
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext CheckResult.Failed

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            // Judul Release gagal match regex = anomali/gagal parse, BUKAN "sudah terbaru".
            val runNumber = RUN_NUMBER_REGEX.find(json.optString("name"))
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@withContext CheckResult.Failed
            if (runNumber <= currentVersionCode(context)) return@withContext CheckResult.UpToDate

            val versionName = json.optString("tag_name").removePrefix("v").substringBefore("-run")

            // Release ketemu & lebih baru, tapi asset APK tidak ada = release rusak/belum
            // lengkap ter-upload — gagal cek, BUKAN "sudah terbaru".
            val assets = json.optJSONArray("assets") ?: return@withContext CheckResult.Failed
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
            if (apkUrl.isNullOrEmpty() || apkName == null) return@withContext CheckResult.Failed

            CheckResult.Available(
                UpdateInfo(
                    versionName = versionName,
                    runNumber = runNumber,
                    downloadUrl = apkUrl,
                    fileName = apkName,
                    releaseNotes = extractReleaseSummary(json.optString("body"))
                )
            )
        } finally {
            connection?.disconnect()
        }
    }

    /** Dipanggil OTOMATIS diam-diam tiap app dibuka (`BoosterViewModel.init`) — SEMUA
     *  exception (network/parsing) SENGAJA ditelan jadi `null` di sini (bukan
     *  dilempar), karena gagalnya TIDAK seharusnya mengganggu user dengan pesan error
     *  di luar aksi eksplisit apapun. WAJIB dipanggil dari coroutine (viewModelScope),
     *  bukan main thread langsung. */
    suspend fun checkForUpdate(context: Context): UpdateInfo? =
        try {
            (fetchLatestRelease(context) as? CheckResult.Available)?.info
        } catch (_: Exception) { null }

    /** Batch 73: dipicu EKSPLISIT oleh tombol "Cek Update Sekarang" (section Settings
     *  baru) — user tegur eksplisit tidak ada entry point manual sama sekali sebelum
     *  ini (banner `checkForUpdate()` di atas cuma nongol otomatis KALAU ada rilis
     *  baru, disembunyikan total kalau enggak, TIDAK ada cara user tahu "sudah dicek
     *  belum/gagal atau memang belum ada update"). BEDA dari `checkForUpdate()`:
     *  exception SENGAJA dilempar ulang (bukan ditelan) — pemanggil
     *  (`BoosterViewModel.checkForUpdateManually`) yang tangkap & surface pesan error
     *  ke UI, pola sama seperti `downloadApk()` di bawah (aksi eksplisit tap user,
     *  kegagalan WAJIB kelihatan). */
    suspend fun checkForUpdateManual(context: Context): CheckResult = fetchLatestRelease(context)

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
