package com.audioenhancer.booster

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Batch 250 (request user: "sangat dibutuhkan demi kemudahan akses reverb"): izin `DUMP` yang dibutuhkan Reverb per-session
 * (`DumpProbe`, B247) tak bisa diminta lewat dialog biasa, dulu hanya lewat `adb shell pm grant` dari komputer. Berkas ini
 * memberikannya lewat Shizuku dengan SATU ketukan di kartu Reverb: minta izin Shizuku -> jalankan `pm grant` di proses
 * shell (UserService) -> baca ulang izin. Tanpa Shizuku = perilaku lama (nol perubahan).
 *
 * Tiga bagian: [ReverbAccess]/[ReverbAccessPolicy] (JVM murni, dites `ShizukuGrantTest`), [ShizukuGrantService] (jalan di
 * proses shell milik Shizuku, tanpa Context), dan [ShizukuGrant] (pengendali status yang dibaca UI).
 */
internal enum class ReverbAccess {
    /** Izin DUMP sudah ada: Reverb per-session bisa bekerja. */
    GRANTED,

    /** Aplikasi Shizuku belum terpasang. */
    NOT_INSTALLED,

    /** Shizuku terpasang tapi server-nya belum berjalan (user perlu menjalankannya sekali). */
    NOT_RUNNING,

    /** Shizuku berjalan; satu ketukan cukup. */
    READY,

    /** Sedang meminta izin / menjalankan `pm grant`. */
    WORKING,

    /** User menolak Boomly di Shizuku. */
    DENIED,

    /** `pm grant` gagal / layanan tak tersambung / izin tak terbaca setelahnya. */
    FAILED
}

internal object ReverbAccessPolicy {
    const val APP_PACKAGE = "com.audioenhancer.booster"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    const val REQUEST_CODE = 2601

    /** `tag` UserService HARUS tetap: tanpa tag Shizuku memakai nama kelas, yang tak stabil setelah R8. */
    const val SERVICE_TAG = "boomly-reverb-grant"
    const val SERVICE_VERSION = 1
    const val PROCESS_SUFFIX = "grant"

    const val CONNECT_TIMEOUT_MS = 8_000L
    const val VERIFY_TRIES = 10
    const val VERIFY_DELAY_MS = 200L
    const val OUTPUT_MAX_CHARS = 400

    /** Satu-satunya perintah yang dijalankan di proses shell: izin DUMP untuk Boomly sendiri, tanpa parameter dari luar. */
    val GRANT_COMMAND: List<String> = listOf("pm", "grant", APP_PACKAGE, DumpProbe.PERMISSION)

    /** Batch 251: lawan GRANT_COMMAND, hanya untuk tombol uji ulang "Cabut izin" (juga tanpa argumen dari luar). */
    val REVOKE_COMMAND: List<String> = listOf("pm", "revoke", APP_PACKAGE, DumpProbe.PERMISSION)

    /** Urutan prioritas status: sedang bekerja > izin ada > Shizuku tak hidup > kegagalan terakhir > siap (B251: bekerja di atas, supaya cabut izin tak tampak GRANTED). */
    fun resolve(
        dumpGranted: Boolean,
        shizukuInstalled: Boolean,
        binderAlive: Boolean,
        busy: Boolean,
        failure: ReverbAccess?
    ): ReverbAccess = when {
        busy -> ReverbAccess.WORKING
        dumpGranted -> ReverbAccess.GRANTED
        !binderAlive -> if (shizukuInstalled) ReverbAccess.NOT_RUNNING else ReverbAccess.NOT_INSTALLED
        failure != null -> failure
        else -> ReverbAccess.READY
    }

    /** `pm grant`/`pm revoke` sukses hanya bila exit 0 DAN keluarannya tak memuat jejak pengecualian (pm mencetak `Exception occurred ...`). */
    fun commandSucceeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && !output.contains("Exception", ignoreCase = true) && !output.contains("Error", ignoreCase = true)

    fun trimOutput(text: String): String = text.trim().take(OUTPUT_MAX_CHARS)
}

/**
 * Layanan yang dijalankan Shizuku di proses shell (UID 2000) atau root. Proses ini BUKAN proses aplikasi biasa: tanpa
 * Context/Compose, hanya `Binder` mentah (tanpa AIDL). Hanya dua perintah yang dikenal ([CODE_GRANT], [CODE_REVOKE]) dan tak menerima
 * argumen, jadi pemegang binder tak bisa memberi izin ke paket atau izin lain. Kelas ini dipertahankan R8 lewat
 * `proguard-rules.pro` (dibuat lewat refleksi oleh Shizuku, bukan dipanggil dari kode).
 */
class ShizukuGrantService : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            CODE_GRANT, CODE_REVOKE -> {
                data.enforceInterface(DESCRIPTOR)
                val command = if (code == CODE_GRANT) ReverbAccessPolicy.GRANT_COMMAND else ReverbAccessPolicy.REVOKE_COMMAND
                val (exitCode, output) = runCommand(command)
                reply?.writeNoException()
                reply?.writeInt(exitCode)
                reply?.writeString(output)
                return true
            }
            CODE_DESTROY -> {
                // Kontrak UserService Shizuku: pada unbind(remove=true) server memanggil transaksi ini; proses harus menutup dirinya.
                reply?.writeNoException()
                android.os.Process.killProcess(android.os.Process.myPid())
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun runCommand(command: List<String>): Pair<Int, String> = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() to ReverbAccessPolicy.trimOutput(output)
    } catch (e: Exception) {
        Log.w("ShizukuGrantService", "menjalankan perintah shell gagal", e)
        -1 to ReverbAccessPolicy.trimOutput(e.toString())
    }

    companion object {
        const val DESCRIPTOR = "com.audioenhancer.booster.ShizukuGrantService"
        const val CODE_GRANT = IBinder.FIRST_CALL_TRANSACTION
        const val CODE_REVOKE = 2

        /** Nomor transaksi "destroy" yang ditetapkan Shizuku (16777115). */
        const val CODE_DESTROY = 16_777_115
    }
}

/** Pengendali status akses Reverb. Berdiri sendiri (di luar service audio); UI hanya membaca [access] dan memanggil [enable]/[revoke]. */
internal object ShizukuGrant {
    private const val TAG = "ShizukuGrant"

    /** Status untuk UI (state Compose: aman ditulis dari thread mana pun). */
    var access: ReverbAccess by mutableStateOf(ReverbAccess.NOT_INSTALLED)
        private set

    /** Batch 251: server Shizuku hidup. Syarat tampilnya tombol "Cabut izin (uji ulang)" saat izin sedang aktif. */
    var shizukuAlive: Boolean by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val attempt = AtomicInteger(0)
    private var starts = 0

    // Lint StaticFieldLeak (preseden Batch 152): yang disimpan HANYA `applicationContext` (umur = umur proses), bukan Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    @Volatile private var appContext: Context? = null

    @Volatile private var busy = false

    @Volatile private var failure: ReverbAccess? = null

    /** Kode transaksi (grant/revoke) yang menunggu hasil dialog izin Shizuku; 0 = tak ada. */
    @Volatile private var pending = 0

    private val binderReceived = Shizuku.OnBinderReceivedListener { onShizukuChanged() }
    private val binderDead = Shizuku.OnBinderDeadListener { onShizukuChanged() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        onPermissionResult(requestCode, grantResult)
    }

    /** Daftarkan pendengar Shizuku (hitung referensi: layar boleh muncul/hilang berkali-kali) lalu segarkan status. */
    fun start(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            if (starts++ == 0) {
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addBinderDeadListener(binderDead)
                Shizuku.addRequestPermissionResultListener(permissionResult)
            }
        }
        recompute()
    }

    fun stop() {
        synchronized(lock) {
            if (starts > 0 && --starts == 0) {
                Shizuku.removeBinderReceivedListener(binderReceived)
                Shizuku.removeBinderDeadListener(binderDead)
                Shizuku.removeRequestPermissionResultListener(permissionResult)
            }
        }
    }

    /** Dipanggil tiap layar kembali tampil: user mungkin baru memasang/menjalankan Shizuku atau memberi izin lewat adb.
     *  Kegagalan lama dilepas supaya tombol kembali ke "siap" setelah user selesai di aplikasi Shizuku. */
    fun onResume(context: Context) {
        appContext = context.applicationContext
        if (!busy) failure = null
        recompute()
    }

    /** Aksi tombol utama saat Shizuku hidup: minta izin Shizuku bila perlu, lalu beri izin DUMP. */
    fun enable(context: Context) = begin(context, ShizukuGrantService.CODE_GRANT)

    /** Batch 251 (request user): cabut izin DUMP Boomly sendiri supaya jalur "Aktifkan Reverb" bisa diuji ulang tanpa uninstall. */
    fun revoke(context: Context) = begin(context, ShizukuGrantService.CODE_REVOKE)

    /** Buka aplikasi Shizuku bila terpasang; selain itu halaman unduhnya (alamat resmi dari README Shizuku-API). */
    fun openShizukuOrDownload(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(ReverbAccessPolicy.SHIZUKU_PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(ReverbAccessPolicy.SHIZUKU_DOWNLOAD_URL))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "tidak bisa membuka Shizuku/halaman unduhnya", e)
        }
    }

    // ---- internal ----

    /** Mulai satu aksi ([ShizukuGrantService.CODE_GRANT] atau [ShizukuGrantService.CODE_REVOKE]); aksi sia-sia (sudah/belum punya izin) diabaikan. */
    private fun begin(context: Context, code: Int) {
        val app = context.applicationContext
        appContext = app
        if (busy) return
        failure = null
        val pointless = if (code == ShizukuGrantService.CODE_GRANT) hasDump(app) else !hasDump(app)
        if (pointless || !Shizuku.pingBinder()) {
            recompute()
            return
        }
        if (shizukuPermissionGranted()) {
            runShell(app, code)
            return
        }
        if (shouldOpenShizukuForPermission()) {
            // User pernah memilih "Tolak dan jangan tanya lagi": dialog tak akan muncul, jadi arahkan ke aplikasi Shizuku.
            failure = ReverbAccess.DENIED
            recompute()
            openShizukuOrDownload(context)
            return
        }
        pending = code
        try {
            Shizuku.requestPermission(ReverbAccessPolicy.REQUEST_CODE)
        } catch (e: Exception) {
            Log.w(TAG, "requestPermission gagal", e)
            pending = 0
            failure = ReverbAccess.FAILED
            recompute()
        }
    }

    private fun onShizukuChanged() {
        failure = null
        if (!Shizuku.pingBinder() && busy) {
            attempt.incrementAndGet() // pekerjaan yang sedang jalan dianggap basi; hasilnya diabaikan
            busy = false
        }
        recompute()
    }

    private fun onPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode != ReverbAccessPolicy.REQUEST_CODE) return
        val app = appContext ?: return
        val code = if (pending != 0) pending else ShizukuGrantService.CODE_GRANT
        pending = 0
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            runShell(app, code)
        } else {
            failure = ReverbAccess.DENIED
            recompute()
        }
    }

    private fun recompute() {
        val app = appContext ?: return
        val alive = Shizuku.pingBinder()
        shizukuAlive = alive
        access = ReverbAccessPolicy.resolve(
            dumpGranted = hasDump(app),
            shizukuInstalled = app.packageManager.getLaunchIntentForPackage(ReverbAccessPolicy.SHIZUKU_PACKAGE) != null,
            binderAlive = alive,
            busy = busy,
            failure = failure
        )
    }

    private fun hasDump(app: Context): Boolean =
        ContextCompat.checkSelfPermission(app, DumpProbe.PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun shizukuPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        Log.w(TAG, "checkSelfPermission Shizuku gagal", e)
        false
    }

    private fun shouldOpenShizukuForPermission(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (e: Exception) {
        Log.w(TAG, "shouldShowRequestPermissionRationale gagal", e)
        false
    }

    /** Ikat UserService di proses shell, jalankan `pm grant`/`pm revoke` sekali, lepas layanan, lalu BACA ULANG izin (kebenaran akhir). */
    private fun runShell(app: Context, code: Int) {
        val mine = attempt.incrementAndGet()
        val expectGranted = code == ShizukuGrantService.CODE_GRANT
        busy = true
        failure = null
        recompute()

        val finished = AtomicBoolean(false)
        val args = Shizuku.UserServiceArgs(ComponentName(app.packageName, ShizukuGrantService::class.java.name))
            .tag(ReverbAccessPolicy.SERVICE_TAG)
            .processNameSuffix(ReverbAccessPolicy.PROCESS_SUFFIX)
            .daemon(false)
            .version(ReverbAccessPolicy.SERVICE_VERSION)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val self = this
                if (!finished.compareAndSet(false, true)) return
                scope.launch {
                    val ok = service != null && callCommand(service, code)
                    unbind(args, self)
                    finish(app, mine, ok, expectGranted)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            Log.w(TAG, "bindUserService gagal", e)
            finished.set(true)
            scope.launch { finish(app, mine, false, expectGranted) }
            return
        }
        scope.launch {
            delay(ReverbAccessPolicy.CONNECT_TIMEOUT_MS)
            if (finished.compareAndSet(false, true)) {
                Log.w(TAG, "layanan Shizuku tak tersambung dalam ${ReverbAccessPolicy.CONNECT_TIMEOUT_MS} ms")
                unbind(args, connection)
                finish(app, mine, false, expectGranted)
            }
        }
    }

    private fun unbind(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (e: Exception) {
            Log.w(TAG, "unbindUserService gagal", e)
        }
    }

    /** Kirim perintah ke layanan shell. true = `pm grant`/`pm revoke` selesai tanpa galat. Keluaran mentah dicatat ke log. */
    private fun callCommand(service: IBinder, code: Int): Boolean {
        val name = if (code == ShizukuGrantService.CODE_GRANT) "grant" else "revoke"
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ShizukuGrantService.DESCRIPTOR)
            if (!service.transact(code, data, reply, 0)) {
                Log.w(TAG, "transact ke layanan Shizuku ditolak")
                false
            } else {
                reply.readException()
                val exitCode = reply.readInt()
                val output = reply.readString().orEmpty()
                Log.i(TAG, "pm $name ${DumpProbe.PERMISSION}: exit=$exitCode keluaran='${ReverbAccessPolicy.trimOutput(output)}'")
                ReverbAccessPolicy.commandSucceeded(exitCode, output)
            }
        } catch (e: Exception) {
            Log.w(TAG, "transact ke layanan Shizuku gagal", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Akhiri satu percobaan: pada sukses, tunggu sebentar sampai OS benar-benar menunjukkan izin sesuai harapan; hasil basi diabaikan. */
    private suspend fun finish(app: Context, mine: Int, commandOk: Boolean, expectGranted: Boolean) {
        var reached = false
        if (commandOk) {
            repeat(ReverbAccessPolicy.VERIFY_TRIES) {
                if (!reached) {
                    reached = hasDump(app) == expectGranted
                    if (!reached) delay(ReverbAccessPolicy.VERIFY_DELAY_MS)
                }
            }
            if (!reached) Log.w(TAG, "perintah sukses tetapi izin DUMP belum ${if (expectGranted) "terbaca aktif" else "tercabut"} setelah verifikasi")
        }
        if (attempt.get() != mine) return
        busy = false
        failure = if (reached) null else ReverbAccess.FAILED
        recompute()
    }
}
