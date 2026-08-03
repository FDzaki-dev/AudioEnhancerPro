package com.audioenhancer.booster

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Lapisan kedua di luar `START_STICKY` + `stopWithTask="false"` (Batch 9). Kedua
 * mekanisme itu diverifikasi SUDAH BENAR (lihat insiden v1.34 di PROJECT_STATE.md),
 * tapi tetap bisa kalah lawan battery/task manager proprietary OEM (MIUI, ColorOS,
 * EMUI, XOS, dst) yang membunuh foreground service TANPA PEDULI kedua mekanisme itu.
 * Worker ini adalah jaring pengaman: dicek periodik, kalau ternyata service mati
 * padahal user TIDAK PERNAH minta dimatikan (lihat `PrefsHelper.getUserWantsRunning`),
 * restart lagi.
 *
 * PENTING — ini BUKAN solusi buat "menang lawan" OEM battery-killer (itu limitasi
 * platform yang gak bisa diakali sepenuhnya dari kode app manapun, sudah didokumentasikan
 * berkali-kali di PROJECT_STATE.md). ini cuma bikin app "sembuh sendiri" lebih cepat
 * kalau sempat kalah, TANPA mem-bypass consent user (kalau user sengaja matiin lewat
 * notifikasi/QS Tile/Widget, worker ini WAJIB diam, bukan restart paksa).
 *
 * Interval 15 menit = MINIMUM yang diizinkan WorkManager buat periodic work (batasan
 * OS, gak bisa lebih cepat dari itu). `setExpedited`/foreground-worker sengaja TIDAK
 * dipakai di sini karena worker ini singkat & jarang butuh eksekusi cepat-segera.
 */
class ServiceWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val userWantsRunning = PrefsHelper.getUserWantsRunning(context)
        if (userWantsRunning && !AudioEnhancerService.isRunning) {
            AudioEnhancerService.requestStart(context)
        }
        // Selalu SUCCESS (bukan RETRY) — kalau `requestStart` di atas ternyata gagal
        // (mis. attachEffects gagal di chipset tertentu), gak ada gunanya WorkManager
        // retry cepat-cepat; siklus periodic 15 menit berikutnya sudah cukup buat
        // coba lagi tanpa bikin WorkManager keliatan "gagal terus" di sistem.
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "audio_booster_service_watchdog"

        /** Panggil sekali di Application.onCreate(). `KEEP` supaya jadwal yang sudah
         *  ada TIDAK di-reset ulang tiap kali process app baru dibuat (app dibuka
         *  berkali-kali sehari) — cukup dijadwalkan sekali, WorkManager sendiri yang
         *  menjaga siklusnya tetap jalan lintas reboot/update app. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
