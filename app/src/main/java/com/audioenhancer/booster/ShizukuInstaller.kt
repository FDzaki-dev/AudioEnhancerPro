package com.audioenhancer.booster

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Batch 344 (request user: "pop-up install update bisa langsung di skip via shizuku"): hasil pemasangan lewat Shizuku.
 * Semua selain [INSTALLED] berarti pemanggil memakai jalur LAMA (intent installer sistem), jadi update tak pernah buntu.
 */
internal enum class SilentInstallResult {
    /** `pm install` selesai dengan `Success`; sistem akan memulai ulang app (MY_PACKAGE_REPLACED). */
    INSTALLED,

    /** Shizuku tak hidup, atau berkas bukan APK Boomly: tidak mencoba. */
    UNAVAILABLE,

    /** User menolak (atau "Tolak dan jangan tanya lagi") izin Shizuku untuk Boomly. */
    DENIED,

    /** Layanan tak tersambung, `pm install` gagal, atau waktu habis. */
    FAILED
}

/** Aturan murni pemasangan senyap (JVM murni, dites `ShizukuInstallerTest`). */
internal object SilentInstallPolicy {
    /** Tag logcat (ikut Ekspor Log Debug); keluaran mentah `pm` dicatat apa adanya. */
    const val LOG_TAG = "UpdateInstall"

    /** Beda dari `ReverbAccessPolicy.REQUEST_CODE` (2601) supaya hasil dialog izin tak tertukar dengan tombol izin DUMP. */
    const val REQUEST_CODE = 2602

    /** Batas wajar ukuran APK (512 MB); di atasnya dianggap tidak valid, bukan dikirim ke `pm`. */
    const val MAX_APK_BYTES = 512L * 1024L * 1024L
    const val PERMISSION_TIMEOUT_MS = 60_000L
    const val INSTALL_TIMEOUT_MS = 180_000L

    /** Satu-satunya perintah yang dijalankan layanan shell untuk pemasangan; hanya ukuran (angka) yang ditambahkan. */
    private val INSTALL_PREFIX: List<String> = listOf("pm", "install", "-r", "-S")

    /** `null` bila [sizeBytes] di luar 1..[MAX_APK_BYTES] (dipakai layanan shell dan pemanggil, satu sumber). */
    fun installCommand(sizeBytes: Long): List<String>? =
        if (sizeBytes in 1L..MAX_APK_BYTES) INSTALL_PREFIX + sizeBytes.toString() else null

    /** `pm install` sukses hanya bila exit 0 DAN ada baris `Success` (gagal mencetak `Failure` diikuti kode `INSTALL_FAILED_...`). */
    fun installSucceeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.lineSequence().any { it.trim().equals("Success", ignoreCase = true) }

    /** Jalur istimewa hanya untuk APK Boomly sendiri: paket di dalam APK harus sama dengan paket app ini. */
    fun isOwnPackage(archivePackage: String?, ownPackage: String): Boolean = archivePackage != null && archivePackage == ownPackage
}

/**
 * Memasang APK update Boomly lewat Shizuku tanpa dialog pemasangan sistem: minta izin Shizuku (bila perlu) -> ikat UserService yang SAMA
 * dengan tombol izin DUMP ([ShizukuGrantService]) -> kirim APK sebagai ParcelFileDescriptor + ukuran (`CODE_INSTALL`) -> baca keluaran
 * `pm install`. Byte APK tak pernah dimuat ke memori aplikasi maupun proses shell (alirkan berkas -> stdin `pm` lewat buffer Okio).
 * Tanpa Shizuku = nol perubahan: pemanggil jatuh ke installer sistem. Sengaja berdiri sendiri (tanpa service audio, tanpa state Compose).
 */
internal object ShizukuInstaller {
    private const val TAG = SilentInstallPolicy.LOG_TAG

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Reply(val exitCode: Int, val output: String)

    private class Connected(val binder: IBinder, val connection: ServiceConnection)

    suspend fun install(context: Context, apk: File): SilentInstallResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val size = apk.length()
        if (SilentInstallPolicy.installCommand(size) == null || !isOwnPackageArchive(app, apk)) {
            Log.i(TAG, "Shizuku dilewati: berkas bukan APK Boomly yang valid (ukuran=$size)")
            return@withContext SilentInstallResult.UNAVAILABLE
        }
        if (!shizukuAlive()) {
            Log.i(TAG, "Shizuku dilewati: server Shizuku tidak berjalan, memakai installer sistem")
            return@withContext SilentInstallResult.UNAVAILABLE
        }
        if (!ensurePermission()) {
            Log.i(TAG, "Shizuku dilewati: izin Shizuku untuk Boomly tidak diberikan")
            return@withContext SilentInstallResult.DENIED
        }
        val args = userServiceArgs(app)
        val connected = connect(args)
        if (connected == null) {
            Log.w(TAG, "layanan Shizuku tak tersambung dalam ${ReverbAccessPolicy.CONNECT_TIMEOUT_MS} ms")
            return@withContext SilentInstallResult.FAILED
        }
        try {
            val call = scope.async { transactInstall(connected.binder, apk, size) }
            val reply = withTimeoutOrNull(SilentInstallPolicy.INSTALL_TIMEOUT_MS) { call.await() }
            if (reply == null) {
                Log.w(TAG, "pm install tanpa jawaban (gagal/waktu habis ${SilentInstallPolicy.INSTALL_TIMEOUT_MS} ms)")
                SilentInstallResult.FAILED
            } else {
                Log.i(TAG, "pm install: exit=${reply.exitCode} keluaran='${ReverbAccessPolicy.trimOutput(reply.output)}'")
                if (SilentInstallPolicy.installSucceeded(reply.exitCode, reply.output)) SilentInstallResult.INSTALLED else SilentInstallResult.FAILED
            }
        } finally {
            unbind(args, connected.connection)
        }
    }

    private fun shizukuAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        Log.w(TAG, "pingBinder Shizuku gagal", e)
        false
    }

    private fun isOwnPackageArchive(app: Context, apk: File): Boolean = try {
        val manager = app.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageArchiveInfo(apk.absolutePath, 0)
        }
        SilentInstallPolicy.isOwnPackage(info?.packageName, app.packageName)
    } catch (e: Exception) {
        Log.w(TAG, "membaca isi APK gagal", e)
        false
    }

    /** true bila izin Shizuku sudah ada atau baru diberikan lewat dialog; "Tolak dan jangan tanya lagi" = false tanpa memunculkan dialog. */
    private suspend fun ensurePermission(): Boolean {
        if (shizukuPermissionGranted()) return true
        if (permissionBlocked()) return false
        return withTimeoutOrNull(SilentInstallPolicy.PERMISSION_TIMEOUT_MS) { requestPermission() } ?: false
    }

    private fun shizukuPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        Log.w(TAG, "checkSelfPermission Shizuku gagal", e)
        false
    }

    private fun permissionBlocked(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (e: Exception) {
        Log.w(TAG, "shouldShowRequestPermissionRationale gagal", e)
        false
    }

    private suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        // Batch 346: object expression (bukan `lateinit var` + lambda): pendengar melepas dirinya lewat `this` yang pasti terisi, tanpa slot
        // `Ref.ObjectRef` yang di bytecode bernilai null saat dibuat (SpotBugs NP_NULL_PARAM_DEREF di tiga pemanggilan `remove...`, CI run 142).
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == SilentInstallPolicy.REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        try {
            Shizuku.requestPermission(SilentInstallPolicy.REQUEST_CODE)
        } catch (e: Exception) {
            Log.w(TAG, "requestPermission gagal", e)
            Shizuku.removeRequestPermissionResultListener(listener)
            if (cont.isActive) cont.resume(false)
        }
    }

    /** UserService yang sama dengan tombol izin DUMP (tag/sufiks/versi dari `ReverbAccessPolicy`): satu proses shell, dua kegunaan. */
    private fun userServiceArgs(app: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(app.packageName, ShizukuGrantService::class.java.name))
            .tag(ReverbAccessPolicy.SERVICE_TAG)
            .processNameSuffix(ReverbAccessPolicy.PROCESS_SUFFIX)
            .daemon(false)
            .version(ReverbAccessPolicy.SERVICE_VERSION)

    private suspend fun connect(args: Shizuku.UserServiceArgs): Connected? {
        val ready = CompletableDeferred<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                ready.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                ready.complete(null)
            }
        }
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            Log.w(TAG, "bindUserService gagal", e)
            return null
        }
        val binder = withTimeoutOrNull(ReverbAccessPolicy.CONNECT_TIMEOUT_MS) { ready.await() }
        if (binder == null) {
            unbind(args, connection)
            return null
        }
        return Connected(binder, connection)
    }

    private fun unbind(args: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (e: Exception) {
            Log.w(TAG, "unbindUserService gagal", e)
        }
    }

    /** Kirim APK ke layanan shell sebagai file descriptor (bukan byte) + ukuran; null = transaksi gagal (alasannya dicatat mentah). */
    private fun transactInstall(service: IBinder, apk: File, size: Long): Reply? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                data.writeInterfaceToken(ShizukuGrantService.DESCRIPTOR)
                data.writeLong(size)
                pfd.writeToParcel(data, 0)
                if (!service.transact(ShizukuGrantService.CODE_INSTALL, data, reply, 0)) {
                    Log.w(TAG, "transact CODE_INSTALL ditolak layanan Shizuku")
                    null
                } else {
                    reply.readException()
                    Reply(reply.readInt(), reply.readString().orEmpty())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "transact CODE_INSTALL gagal", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
