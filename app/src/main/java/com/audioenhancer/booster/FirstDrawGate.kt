package com.audioenhancer.booster

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

/**
 * Batch 312 (request user: "Tunda layanan sesudah frame pertama", dari log v310/v311): Service.onCreate + attachEffects (±80 ms) berjalan di THREAD
 * UTAMA dan urutannya terhadap frame pertama tidak tetap (v310: sesudah, di dalam jendela jank 46 frame; v311: sebelum komposisi, menunda frame
 * pertama ±80 ms). `MainActivity` memicu bind/start layanan lewat sini supaya SELALU sesudah traversal frame pertama selesai.
 *
 * Jaminan: [action] jalan paling banyak SEKALI, di thread utama, (a) segera sesudah traversal yang memuat `onDraw` pertama selesai, atau
 * (b) setelah [FALLBACK_MS] bila frame itu tak kunjung digambar (mis. jendela tak terlihat) supaya layanan TIDAK pernah tertinggal tak terikat.
 * [isAlive] false (Activity sudah hancur) = aksi dibatalkan (ViewModel yang sudah dibersihkan tidak boleh mengikat layanan lagi).
 */
internal object FirstDrawGate {
    private const val FALLBACK_MS = 1_500L
    const val REASON_AFTER_FIRST_DRAW = "sesudah-frame-pertama"
    const val REASON_FALLBACK = "cadangan"

    /** Menjamin [action] jalan SEKALI (bagian murni, dites di JVM). `fire` mengembalikan true hanya pada pemanggilan yang menjalankannya. */
    class Once(private val action: (String) -> Unit) {
        private var fired = false

        fun fire(reason: String): Boolean {
            if (fired) return false
            fired = true
            action(reason)
            return true
        }
    }

    fun runAfterFirstDraw(view: View, isAlive: () -> Boolean, action: (String) -> Unit) {
        val once = Once { reason -> if (isAlive()) action(reason) }
        view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var seen = false

            override fun onDraw() {
                if (seen) return
                seen = true
                // post: jalan SESUDAH traversal frame ini selesai; melepas listener di dalam onDraw sendiri dilarang sistem.
                view.post {
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this)
                    once.fire(REASON_AFTER_FIRST_DRAW)
                }
            }
        })
        Handler(Looper.getMainLooper()).postDelayed({ once.fire(REASON_FALLBACK) }, FALLBACK_MS)
    }
}
