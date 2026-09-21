package com.audioenhancer.booster

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
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
 * dipakai di sini karena worker ini singkat & jarang butuh eksekusi cepat-segera. Ini
 * TETAP jaring pengaman UTAMA, jalan apa pun kondisi izin exact alarm di bawah.
 *
 * Batch 127 (fast-recovery, OPPORTUNISTIC): kalau tick 15-menit ini mendeteksi
 * recovery masih dibutuhkan (`userWantsRunning && !isRunning`), selain coba restart
 * langsung, worker JUGA menjadwalkan 1x exact alarm (`WatchdogAlarmReceiver`) yang
 * retry lebih cepat (~5 menit, dibulatkan sistem ke atas kalau app belum di-exempt
 * battery optimization — lihat dok `AlarmManager.setExactAndAllowWhileIdle`, rate-limit
 * OS ±9 menit/app buat non-exempt app). Exact alarm fire = app dapat *temporary
 * background-start exemption* resmi dari OS, jadi `requestStart()` di titik itu TIDAK
 * kena blokir background-start restriction Android 12+ seperti yang dialami tick
 * WorkManager biasa (lihat komentar Batch 124 di bawah). Chain SELF-TERMINATING: tiap
 * fire cek ulang, kalau sudah pulih ATAU user matiin, TIDAK reschedule lagi — bukan
 * loop permanen.
 *
 * Butuh `SCHEDULE_EXACT_ALARM` (API 31+, lihat manifest) yang TIDAK di-request lewat
 * UI apa pun di app ini (di luar scope 3-file Batch 127). Kalau belum granted user
 * (default di banyak device Android 13+), `canScheduleExactAlarms()` false → seluruh
 * bagian ini diam total, NOL dampak ke behavior lama. **NOT VERIFIED** — potensi
 * manfaat (15 menit → ~5-9 menit best-effort) TIDAK terjamin lolos device fisik/OEM
 * restriction, lihat catatan RESUME POINT.
 */
class ServiceWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val stillNeedsRecovery = performWatchdogCheck(applicationContext)
        if (stillNeedsRecovery) {
            scheduleExactRecovery(applicationContext)
        }
        // Selalu SUCCESS (bukan RETRY) — kalau `requestStart` di atas ternyata gagal
        // (mis. attachEffects gagal di chipset tertentu), gak ada gunanya WorkManager
        // retry cepat-cepat; siklus periodic 15 menit berikutnya (+ fast-recovery
        // exact alarm di atas kalau tersedia) sudah cukup buat coba lagi tanpa bikin
        // WorkManager keliatan "gagal terus" di sistem.
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "audio_booster_service_watchdog"
        private const val FAST_RECOVERY_REQUEST_CODE = 9401
        private const val FAST_RECOVERY_INTERVAL_MS = 5 * 60 * 1000L

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

        /** Dipakai `doWork()` DAN `WatchdogAlarmReceiver` (shared, hindari duplikasi
         *  logika). Return true kalau saat dicek TERNYATA recovery masih/baru
         *  dibutuhkan (dipakai caller buat keputusan reschedule exact alarm). */
        suspend fun performWatchdogCheck(context: Context): Boolean {
            // Laporan user: kill via task-swipe/OEM (BUKAN force-stop biasa) -> widget
            // "aktif" (stale) tapi QS Tile "mati" (benar). Beda root cause dari Batch 44
            // (lihat komentar `requestTileUpdate`, itu nyambungin refresh WIDGET+TILE di
            // SATU hook `isRunning`-berubah, tapi hook itu HANYA jalan kalau ada kode yang
            // SEMPAT jalan). Kill keras (SIGKILL, onDestroy TIDAK terpanggil) = tidak ada
            // kode yang sempat jalan sama sekali -> hook itu tidak pernah terpicu. QS Tile
            // tetap "sembuh sendiri" karena `onStartListening()` dipanggil sistem TIAP kali
            // shade dibuka (proses baru, `isRunning` fresh default false = benar). Widget
            // TIDAK punya hook setara itu — RemoteViews cuma berubah kalau ADA yang push
            // (`refreshAll()`) atau `onUpdate()` (OS enforce minimum ~30 menit), jadi bisa
            // nyangkut stale TANPA BATAS WAKTU kalau tidak pernah di-tap. Fix: watchdog ini
            // (sudah jalan tiap 15 menit apa pun kondisinya) SELALU resync widget+tile ke
            // ground truth `isRunning` SEKARANG, bukan cuma pas mau restart service — ini
            // jadi jaring pengaman kedua surface itu balik konsisten dalam ≤15 menit (atau
            // lebih cepat kalau fast-recovery Batch 127 aktif), sama seperti recovery
            // notifikasi Batch 124. Resync instan tambahan on-demand (buka app/QS panel)
            // sudah ditangani terpisah di `MainActivity.onResume()`/
            // `QuickToggleTileService.onStartListening()` sejak Batch 126.
            BoosterWidgetProvider.refreshAll(context)
            QuickToggleTileService.requestTileUpdate(context)

            val userWantsRunning = PrefsHelper.getUserWantsRunning(context)
            val needsRecovery = userWantsRunning && !AudioEnhancerService.isRunning
            if (needsRecovery) {
                // Batch 124 hotfix (URGENT, laporan user - lihat komentar lengkap di
                // AudioEnhancerService.NOTIF_ID_RECOVERY): SEBELUMNYA baris ini 0 try-catch.
                // Worker ini jalan di background TANPA exemption Android 12+ background-start
                // restriction (beda dari BootReceiver/widget/QS Tile yang exempted, dan beda
                // dari fire exact-alarm Batch 127 yang JUGA exempted) - kalau battery
                // optimization belum di-exempt user, requestStart() lempar
                // ForegroundServiceStartNotAllowedException DIAM-DIAM, effect audio TIDAK
                // PERNAH balik sampai user ketemu widget/tile/app sendiri. Fallback sekarang:
                // notifikasi tap-to-restart (jalur exempted resmi), BUKAN cuma diam.
                try {
                    AudioEnhancerService.requestStart(context)
                } catch (e: Exception) {
                    android.util.Log.e("ServiceWatchdogWorker", "requestStart() diblokir sistem (kemungkinan battery optimization belum di-exempt) - fallback ke notifikasi recovery", e)
                    AudioEnhancerService.postRecoveryNotification(context)
                }
            }
            return needsRecovery
        }

        /** Batch 127. Opportunistic: no-op total kalau `SCHEDULE_EXACT_ALARM` belum
         *  granted (API < 31 selalu diizinkan, tidak butuh izin khusus). Dipanggil
         *  ulang oleh `WatchdogAlarmReceiver` sendiri tiap fire SELAMA masih perlu
         *  recovery — chain berhenti sendiri begitu `performWatchdogCheck` return
         *  false (pulih, atau user matiin). */
        fun scheduleExactRecovery(context: Context) {
            if (!canUseExactAlarm(context)) return
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, WatchdogAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    FAST_RECOVERY_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = SystemClock.elapsedRealtime() + FAST_RECOVERY_INTERVAL_MS
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } catch (e: SecurityException) {
                // Race: izin dicabut user tepat setelah canScheduleExactAlarms() true di
                // atas, atau OEM restriction lain saat runtime. Aman diam — watchdog 15
                // menit WorkManager TETAP jalan sebagai jaring pengaman utama.
                android.util.Log.w("ServiceWatchdogWorker", "setExactAndAllowWhileIdle ditolak sistem saat runtime, lanjut andalkan watchdog 15 menit", e)
            }
        }

        private fun canUseExactAlarm(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
    }
}
