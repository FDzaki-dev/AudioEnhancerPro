package com.audioenhancer.booster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * **STATUS (Batch 132): jalur ini DORMAN.** `ServiceWatchdogWorker.scheduleExactRecovery()`
 * sekarang no-op permanen (instruksi user demi baterai), jadi tidak ada exact alarm baru
 * yang menuju receiver ini. Pemulihan dari OS/OEM-kill HANYA lewat watchdog 15 menit
 * `ServiceWatchdogWorker` (WorkManager). Class + entri manifest sengaja DIPERTAHANKAN:
 * alarm lama yang mungkin masih terpasang di device existing fire SEKALI terakhir ke
 * sini, `performWatchdogCheck` jalan normal, lalu berhenti sendiri karena reschedule
 * berikutnya no-op; `cancelExactRecovery()` juga mengacu ke class ini. Jangan hapus
 * tanpa instruksi eksplisit user.
 *
 * Histori desain (Batch 127-130, BUKAN behavior aktif): lapisan "fast recovery" exact
 * alarm (`setExactAndAllowWhileIdle`) pelengkap watchdog 15 menit. Saat fire, OS beri
 * temporary background-start exemption sehingga `AudioEnhancerService.requestStart()`
 * tidak kena blokir background-start Android 12+. `goAsync()` dipakai karena
 * `performWatchdogCheck` suspend & butuh window eksekusi di luar `onReceive()`.
 * Receiver TIDAK exported, TIDAK menerima broadcast implicit/publik.
 *
 * Alur `onReceive` (tetap berlaku untuk alarm sisa): cek ulang lewat
 * `performWatchdogCheck` (logic yang sama dgn worker 15-menit); kalau masih butuh
 * recovery panggil `scheduleExactRecovery` (sekarang no-op, jadi chain berhenti).
 * **NOT VERIFIED** device fisik.
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
