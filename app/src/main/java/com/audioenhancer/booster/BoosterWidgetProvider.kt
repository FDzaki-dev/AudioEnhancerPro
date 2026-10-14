package com.audioenhancer.booster

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * Widget home screen: status (Aktif/Nonaktif) + toggle sekali tap, TANPA buka app sama
 * sekali. Beda dari App Shortcuts (v1.42) yang cuma jalan pintas buka MainActivity —
 * widget ini beneran interaktif langsung dari home screen.
 *
 * Status di-refresh UTAMA dari SATU titik push: AudioEnhancerService manggil refreshAll()
 * tiap `isRunning` berubah (bukan didup-duplikasi di tiap entry point start/stop —
 * semuanya udah lewat AudioEnhancerService.requestStart()/requestStop(), jadi satu hook
 * di situ otomatis nutup semua jalur: MainActivity, BootReceiver, QS Tile, App Shortcut,
 * dan widget ini sendiri). Batch 131: `updatePeriodMillis` di `widget_booster_info.xml`
 * SEKARANG diaktifkan (30 menit, lantai native OS) sebagai jalur KEDUA — murni jaring
 * pengaman TERAKHIR untuk tampilan pasif kalau widget tak pernah disentuh & shade/app tak
 * pernah dibuka; `onUpdate()` di bawah cuma re-render dari `isRunning` TERKINI (fungsi
 * sama dengan refreshAll()), jadi aman idempoten ditumpuk dengan jalur push, 0 duplikasi
 * state. Tap toggle (`onReceive` di bawah) TIDAK PERNAH baca teks widget ini untuk
 * memutuskan start/stop — selalu baca `AudioEnhancerService.isRunning` langsung, jadi
 * 0 salah aksi walau tampilan sempat basi di antara dua refresh.
 *
 * Batch 214: panel diisi bar equalizer DEKORATIF (`widget_eq`, vektor ditint `setColorFilter`: redup saat Nonaktif,
 * menyala saat Aktif) + halo cincin di knob Aktif; bukan level audio nyata (tak ada data yang dibaca).
 *
 * Batch 213 (revisi B212, Neumorphism burgundy): KNOB (`widget_knob`, ImageView) DAN PANEL teks (`widget_panel`,
 * background) sama-sama ganti drawable tiap state — Nonaktif = timbul, Aktif = cekung + ikon menyala + warna teks
 * status ikut. B212 salah: panel ikut background pelat sehingga cekung terus. Knob = ImageView ber-drawable
 * intrinsic 56dp (`adjustViewBounds`) supaya diskalakan seragam di widget pendek. INSET: padding root 8dp + ruang
 * bayangan di drawable, radius pelat = radius sistem peluncur di Android 12+ (`values-v31`). Logika toggle/refresh
 * TIDAK berubah. Content description knob statis lama dihapus (dulu selalu "Nonaktif"); TalkBack membaca teks.
 */
class BoosterWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.audioenhancer.booster.WIDGET_TOGGLE"

        /** Dipanggil dari AudioEnhancerService tiap isRunning berubah. Aman dipanggil
         *  walau belum ada widget yang dipasang user (getAppWidgetIds balikin array kosong). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BoosterWidgetProvider::class.java))
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val running = AudioEnhancerService.isRunning
            val toggleIntent = Intent(context, BoosterWidgetProvider::class.java).apply { action = ACTION_TOGGLE }
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_booster)
                views.setTextViewText(
                    R.id.widget_status_text,
                    context.getString(if (running) R.string.qs_tile_subtitle_on else R.string.qs_tile_subtitle_off)
                )
                views.setImageViewResource(
                    R.id.widget_knob,
                    if (running) R.drawable.widget_status_dot_on else R.drawable.widget_status_dot_off
                )
                views.setInt(
                    R.id.widget_panel,
                    "setBackgroundResource",
                    if (running) R.drawable.widget_panel_on else R.drawable.widget_panel_off
                )
                views.setInt(
                    R.id.widget_eq,
                    "setColorFilter",
                    ContextCompat.getColor(context, if (running) R.color.widget_eq_on else R.color.widget_eq_off)
                )
                views.setTextColor(
                    R.id.widget_status_text,
                    ContextCompat.getColor(
                        context,
                        if (running) R.color.widget_text_active else R.color.widget_text_secondary
                    )
                )
                val togglePending = PendingIntent.getBroadcast(
                    context, id, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, togglePending)
                manager.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (AudioEnhancerService.isRunning) AudioEnhancerService.requestStop(context)
            else AudioEnhancerService.requestStart(context)
            // Sengaja TIDAK refreshAll() di sini — isRunning baru beneran kepakai begitu
            // onStartCommand() service kelar jalan (async), dan itu udah manggil
            // refreshAll() sendiri. Manggil di sini juga cuma nampilin state LAMA
            // (belum keupdate) buat sepersekian detik doang, gak ada gunanya.
        }
    }
}
