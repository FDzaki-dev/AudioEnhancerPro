package com.audioenhancer.booster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** `exported="true"` wajib supaya BOOT_COMPLETED/MY_PACKAGE_REPLACED (broadcast
 *  sistem, protected — app lain gak bisa spoof action ini) bisa nyampe ke receiver
 *  ini. TAPI exported=true juga berarti app lain bisa kirim explicit intent ke
 *  receiver ini dengan action APAPUN (explicit intent lewati pengecekan
 *  intent-filter). Makanya action divalidasi manual di bawah — kalau bukan salah
 *  satu dari 2 action yang memang kita tunggu, diabaikan, biar app lain gak bisa
 *  paksa service kita nyala pakai action bikinan sendiri. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        // Batch 11 fix: SEBELUMNYA start tanpa syarat di sini, kontradiksi sama
        // kontrak "hormati pilihan user" yang sudah eksplisit didesain buat
        // ServiceWatchdogWorker (Batch 9). Kalau user terakhir kali sengaja tekan
        // "Matikan" lewat notifikasi (PrefsHelper.getUserWantsRunning() == false),
        // lalu HP di-reboot, service TETAP nyala lagi sendiri tanpa consent user —
        // persis pola yang ingin dihindari watchdog. Sekarang BootReceiver baca
        // flag yang sama biar konsisten: cuma auto-start kalau user memang terakhir
        // mau service ini hidup.
        if (PrefsHelper.getUserWantsRunning(context)) {
            AudioEnhancerService.requestStart(context)
        }
    }
}
