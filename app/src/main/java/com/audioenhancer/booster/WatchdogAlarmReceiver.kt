package com.audioenhancer.booster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Batch 127 — lapisan "fast recovery" OPPORTUNISTIC, pelengkap `ServiceWatchdogWorker`
 * (WorkManager, lantai 15 menit, TIDAK BISA lebih cepat, batasan OS/dijelaskan lengkap
 * di sana). Receiver ini HANYA dipicu exact alarm internal dari
 * `ServiceWatchdogWorker.scheduleExactRecovery()` — TIDAK exported, TIDAK menerima
 * broadcast implicit/publik apa pun.
 *
 * Kenapa berguna (bukan cuma "interval lebih pendek"): saat exact alarm
 * (`setExactAndAllowWhileIdle`) fire, OS taruh app di temporary power/background-start
 * exemption — jadi `AudioEnhancerService.requestStart()` di titik ini TIDAK kena blokir
 * background-start restriction Android 12+ yang jadi alasan try-catch di
 * `ServiceWatchdogWorker` Batch 124. `goAsync()` dipakai karena `performWatchdogCheck`
 * suspend & butuh window eksekusi resmi di luar `onReceive()` yang sudah return.
 *
 * Self-chaining, BUKAN loop permanen: tiap fire cek ulang lewat
 * `performWatchdogCheck` (shared logic, sama persis dgn worker 15-menit) — kalau HASIL
 * masih butuh recovery, jadwalkan 1x lagi (`scheduleExactRecovery`); kalau sudah pulih
 * atau user matiin app dari surface manapun, TIDAK reschedule, chain berhenti sendiri.
 * **NOT VERIFIED** device fisik — lihat catatan RESUME POINT PROJECT_STATE.md.
 */
class WatchdogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val stillNeedsRecovery = ServiceWatchdogWorker.performWatchdogCheck(context)
                if (stillNeedsRecovery) {
                    ServiceWatchdogWorker.scheduleExactRecovery(context)
                }
            } catch (e: Exception) {
                // Robust guard (SOP): receiver TIDAK BOLEH crash proses app kalau ada
                // kegagalan tak terduga di chain fast-recovery — watchdog 15 menit
                // WorkManager tetap jalan independen sebagai jaring pengaman utama.
                android.util.Log.e("WatchdogAlarmReceiver", "fast-recovery check gagal, lanjut andalkan watchdog 15 menit", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
