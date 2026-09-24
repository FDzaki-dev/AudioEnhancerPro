package com.audioenhancer.booster

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * **STATUS (Batch 132): heartbeat Fast Recovery (exact alarm ±1 menit, histori Batch
 * 127-130 di bawah) DINONAKTIFKAN permanen — `scheduleExactRecovery()` sekarang no-op,
 * instruksi eksplisit user demi baterai (alarm ini bangun CPU terus-menerus selama
 * service hidup). Pemulihan dari OS/OEM-kill SEKARANG HANYA lewat watchdog 15 menit di
 * bawah (TIDAK berubah) — histori Batch 127-130 dipertahankan sebagai KONTEKS desain,
 * BUKAN deskripsi behavior aktif saat ini.**
 *
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
 * WorkManager biasa (lihat komentar Batch 124 di bawah). (Batch 128: chain BUKAN lagi
 * self-terminating saat pulih — jadi heartbeat selama user mau service hidup; berhenti
 * HANYA kalau user matiin. Lihat paragraf Batch 128 di bawah.)
 *
 * Butuh `SCHEDULE_EXACT_ALARM` (API 31+, lihat manifest). Kalau belum granted user,
 * `canScheduleExactAlarms()` false → seluruh bagian exact-alarm diam total, NOL dampak
 * ke behavior lama (15 menit murni).
 *
 * Batch 128 (laporan user: "izin alarm gak ada di pengaturan app, waktu pulih sama
 * saja dengan watchdog") — 2 root cause NYATA di desain Batch 127, dua-duanya dibetulkan:
 * (1) targetSdk 34 → di Android 14+ izin `SCHEDULE_EXACT_ALARM` DEFAULT DITOLAK untuk app
 * baru (bukan pre-granted seperti Android 12/13), dan Batch 127 sengaja tanpa UI minta izin
 * → `canUseExactAlarm()` false terus → fast-recovery no-op 100% → waktu pulih IDENTIK
 * watchdog 15 menit. Fix: kartu "Pemulihan Cepat" di `SettingsScreen.kt` (status +
 * deep-link `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). (2) Desain REAKTIF: exact alarm baru
 * dijadwalkan SETELAH tick watchdog 15-menit mendeteksi service mati → walau izin granted,
 * waktu pulih = deteksi (≤15 mnt) + 5 mnt, TIDAK PERNAH lebih cepat dari watchdog. Fix:
 * HEARTBEAT PROAKTIF — selama user ingin service hidup, exact alarm SELALU terpasang
 * (dipasang `AudioEnhancerService.onStartCommand`, dipasang ulang tiap fire/tick sehat,
 * dicabut `cancelExactRecovery()` di jalur ACTION_STOP). Service dibunuh OS → alarm tetap
 * hidup (AlarmManager di luar proses app) → fire ≤~1 mnt (≤~9 mnt di Doze non-exempt,
 * OS enforce floor ini utk app non-exempt battery — request di bawah ini TIDAK ngaruh
 * pas Doze dalam) →
 * `performWatchdogCheck` nemu `!isRunning` → restart via jalur exempted. Batas jujur:
 * force-stop OEM/pengguna menghapus semua alarm app (tidak ada API yang bisa mencegah).
 * **NOT VERIFIED** — lihat catatan RESUME POINT.
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
        // Batch 132: FAST_RECOVERY_INTERVAL_MS (dulu 60*1000L) dihapus — heartbeat
        // dimatikan (lihat scheduleExactRecovery()), konstanta interval tak lagi
        // dipakai. REQUEST_CODE tetap perlu, dipakai cancelExactRecovery() buat
        // mencabut alarm lama yang mungkin masih nyangkut di device existing.
        private const val FAST_RECOVERY_REQUEST_CODE = 9401

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
            if (!userWantsRunning) {
                // Batch 128: user sudah matikan — pastikan tak ada heartbeat sisa (jalur
                // ACTION_STOP sudah mencabut, ini jaring pengaman kalau ada yang lolos).
                cancelExactRecovery(context)
            } else if (!needsRecovery) {
                // Batch 128: kondisi SEHAT (user mau hidup + service jalan) → pasang
                // heartbeat berikutnya. Cabang needsRecovery TIDAK dipasang di sini: caller
                // (`doWork`/`WatchdogAlarmReceiver`) sudah memasang ulang kalau return true.
                scheduleExactRecovery(context)
            }
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

        /** Batch 127/128/129/130. Pasang (atau ganti — PendingIntent identik = alarm yang sama
         *  ter-replace) 1 exact alarm ~1 menit ke depan (Batch 130, turun dari ~2 menit —
         *  user konfirmasi <3 mnt jalan di device, minta "mentok". 1 mnt = lantai praktis
         *  yang sengaja DIPILIH, bukan limit OS: heartbeat ini jalan TERUS-MENERUS selama
         *  service nyala (bukan sekali tembak), jadi di bawah 1 mnt mulai murni ongkos
         *  wake-up/baterai tanpa tambahan manfaat pulih yang terasa — lihat catatan kelas
         *  & PROJECT_STATE "Keputusan sadar" kalau mau turun lagi). Dipakai sebagai HEARTBEAT (Batch
         *  128): dipanggil `AudioEnhancerService.onStartCommand` (service mulai), tick sehat
         *  `performWatchdogCheck`, dan caller saat recovery masih dibutuhkan. No-op total
         *  kalau `SCHEDULE_EXACT_ALARM` belum granted (API < 31 selalu diizinkan). */
        /** Batch 132 (instruksi eksplisit user, alasan baterai): heartbeat exact-alarm
         *  DIMATIKAN — fungsi ini sekarang no-op permanen. Beda dari watchdog 15 menit
         *  `WorkManager` di bawah (di-batch OS, murah baterai, TIDAK diubah): alarm ini
         *  `setExactAndAllowWhileIdle` bangunin CPU TERUS-MENERUS tiap ±1 menit selama
         *  service hidup (bisa berjam-jam) — itu sumber baterai NYATA yang diminta
         *  dihemat. Trade-off SADAR: pulih dari OS/OEM-kill sekarang HANYA lewat watchdog
         *  15 menit (`performWatchdogCheck` di bawah, TIDAK diubah) — bukan hilang, cuma
         *  lebih lambat (~15mnt vs ~1-9mnt sebelumnya). Semua caller (onStartCommand
         *  service, tick sehat watchdog, `WatchdogAlarmReceiver`) TETAP manggil fungsi
         *  ini aman (no-op) — 0 file lain perlu disentuh untuk logic ini. Alarm lama yang
         *  mungkin masih ter-pasang di device existing (sebelum update ini) akan fire
         *  SEKALI terakhir, `performWatchdogCheck` tetap jalan normal (resync + restart
         *  kalau perlu), lalu berhenti sendiri karena reschedule berikutnya no-op — 0
         *  alarm yatim permanen. */
        fun scheduleExactRecovery(context: Context) {
            // Sengaja kosong — lihat KDoc di atas (Batch 132).
        }

        /** Batch 128. Cabut heartbeat — dipanggil jalur ACTION_STOP (user/QS Tile/Widget/
         *  Sleep timer matikan) supaya tidak ada alarm yatim yang membangunkan app sia-sia.
         *  `FLAG_NO_CREATE`: kalau tidak ada alarm terpasang, tidak bikin PendingIntent baru. */
        fun cancelExactRecovery(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, WatchdogAlarmReceiver::class.java)
                val pendingIntent: PendingIntent? = PendingIntent.getBroadcast(
                    context,
                    FAST_RECOVERY_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            } catch (e: Exception) {
                android.util.Log.w("ServiceWatchdogWorker", "Gagal cabut heartbeat exact alarm", e)
            }
        }

        /** Batch 128: dibuat publik — dibaca UI Settings (kartu "Pemulihan Cepat") buat
         *  status izin "Alarms & reminders". API < 31 tidak ada izin khusus → true. */
        fun canUseExactAlarm(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
    }
}
