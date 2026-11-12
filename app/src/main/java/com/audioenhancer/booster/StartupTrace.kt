package com.audioenhancer.booster

import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import java.util.concurrent.ConcurrentHashMap

/**
 * Batch 310 (request user: pilih "Ukur sisa jank 36 frame" setelah Baseline Profile terbukti bekerja di Android 16, ProfileVerifier kode 1):
 * penanda waktu MENTAH di log aplikasi (tag `StartupTrace`, ikut Ekspor Log Debug) untuk membuktikan di mana sisa ±0,4 dtk buka dingin
 * dihabiskan SEBELUM ada yang diubah. Tiap baris memuat `t=+N ms` (sejak proses mulai, saat baris dicetak, jadi untuk rentang = AKHIR rentang)
 * dan nama thread; rentang memuat `durasi`. TIDAK mengubah perilaku: hanya `Log.i`. Dicabut setelah penyebabnya terbukti.
 */
internal object StartupTrace {
    private const val TAG = "StartupTrace"
    private const val NANOS_PER_MILLI = 1_000_000L
    private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    /** Teks satu baris (murni, dites di JVM). [durationMs] `null` = penanda titik waktu, bukan rentang. */
    fun formatLine(name: String, sinceProcessStartMs: Long, durationMs: Long?, thread: String): String = buildString {
        append(name).append(" t=+").append(sinceProcessStartMs).append("ms")
        if (durationMs != null) append(" durasi=").append(durationMs).append("ms")
        append(" thread=").append(thread)
    }

    fun mark(name: String) {
        Log.i(TAG, formatLine(name, sinceProcessStartMs(), null, Thread.currentThread().name))
    }

    /** Seperti [mark] tetapi hanya SEKALI per proses (aman dipanggil dari badan composable yang bisa recompose). */
    fun markOnce(name: String) {
        if (seen.add(name)) mark(name)
    }

    /** Jalankan [block] lalu catat durasinya (juga bila melempar). */
    fun <T> span(name: String, block: () -> T): T {
        val startNs = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            val durationMs = (SystemClock.elapsedRealtimeNanos() - startNs) / NANOS_PER_MILLI
            Log.i(TAG, formatLine(name, sinceProcessStartMs(), durationMs, Thread.currentThread().name))
        }
    }

    /** Catat awal `onDraw` PERTAMA pada [view] (sekali), lalu lepas listenernya. */
    fun markFirstDraw(view: View) {
        view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var done = false

            override fun onDraw() {
                if (done) return
                done = true
                mark("frame-pertama-onDraw")
                view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
            }
        })
    }

    private fun sinceProcessStartMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
}
