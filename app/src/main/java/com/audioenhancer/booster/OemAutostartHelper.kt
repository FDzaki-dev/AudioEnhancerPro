package com.audioenhancer.booster

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Beberapa OEM (Xiaomi/MIUI, Oppo/ColorOS, Vivo, Huawei/EMUI, Samsung, OnePlus, dst)
 * punya battery/task manager sendiri yang bisa MEMBUNUH foreground service walau
 * `stopWithTask="false"` + START_STICKY sudah benar secara Android standar — inilah
 * kenapa notifikasi "Audio Booster aktif" bisa ikut hilang saat app di-swipe dari
 * recent apps, padahal seharusnya cuma hilang kalau user tekan "Matikan".
 *
 * TIDAK ADA API publik Android buat app minta izin ini secara otomatis — satu-satunya
 * cara adalah user manual mengizinkan "Autostart"/"No restriction" di halaman
 * pengaturan khusus tiap merk. Nama package/Activity-nya beda-beda per versi ROM dan
 * TIDAK didokumentasikan resmi oleh OEM manapun, jadi ini best-effort: coba beberapa
 * kandidat Intent berurutan, dan kalau semua gagal (ROM versi baru yang pindah nama
 * package, atau device di luar daftar), fallback ke halaman App Info bawaan Android
 * yang DIJAMIN selalu ada di semua device.
 */
object OemAutostartHelper {

    private fun componentIntent(pkgName: String, className: String) = Intent().apply {
        component = ComponentName(pkgName, className)
    }

    private fun candidateIntents(): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi") || brand.contains("poco") -> listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                componentIntent("com.miui.securitycenter", "com.miui.appmanager.AppManagerMainActivity")
            )
            manufacturer.contains("oppo") -> listOf(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            manufacturer.contains("vivo") -> listOf(
                componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            manufacturer.contains("samsung") -> listOf(
                componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            )
            manufacturer.contains("infinix") || brand.contains("infinix") ||
                manufacturer.contains("tecno") || brand.contains("tecno") ||
                manufacturer.contains("itel") || brand.contains("itel") -> listOf(
                // Infinix (XOS)/Tecno (HiOS)/itel OS satu grup (Transsion Holdings), berbagi
                // app "Phone Manager". CATATAN: berbeda dari Xiaomi/Huawei/Oppo/Vivo, kandidat
                // ini kurang terverifikasi luas (bahkan library open-source populer sekelas
                // AutoStarter masih punya issue terbuka soal Infinix/Tecno sejak 2020) — kalau
                // gagal, otomatis fallback ke App Info, gak bikin crash.
                componentIntent("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity")
            )
            manufacturer.contains("oneplus") -> listOf(
                componentIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            manufacturer.contains("asus") -> listOf(
                componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
            )
            else -> emptyList()
        }
    }

    /** True kalau merk device ini dikenal punya battery manager agresif — dipakai UI
     *  buat kasih label tombol yang lebih spesifik ("Buka Pengaturan Autostart" vs
     *  fallback generik "Buka Pengaturan Aplikasi"). */
    fun deviceLikelyNeedsAutostart(): Boolean = candidateIntents().isNotEmpty()

    /**
     * Coba buka halaman Autostart/battery manager OEM yang relevan, coba kandidat
     * satu-satu sampai ada yang berhasil di-resolve & di-launch. Kalau semua kandidat
     * gagal (atau device tidak dikenal), otomatis fallback ke halaman App Info bawaan
     * Android — jadi pemanggil TIDAK perlu menangani kegagalan secara terpisah,
     * fungsi ini selalu membuka sesuatu yang berguna.
     */
    fun openAutostartSettings(context: Context) {
        for (intent in candidateIntents()) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Nama package/Activity ini tidak ada di ROM device ini, coba kandidat berikutnya.
            } catch (_: Exception) {
                // Gagal karena alasan lain (mis. SecurityException) — tetap lanjut coba kandidat lain.
            }
        }
        openAppInfoFallback(context)
    }

    /** Halaman App Info bawaan Android — dijamin selalu ada di semua device Android manapun. */
    private fun openAppInfoFallback(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Kalau ini pun gagal, device-nya sudah sangat tidak biasa — tidak ada lagi yang
            // bisa dilakukan secara aman dari sisi app.
        }
    }
}
