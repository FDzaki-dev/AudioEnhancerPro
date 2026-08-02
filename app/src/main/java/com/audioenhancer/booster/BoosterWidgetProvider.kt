package com.audioenhancer.booster

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget home screen: status (Aktif/Nonaktif) + toggle sekali tap, TANPA buka app sama
 * sekali. Beda dari App Shortcuts (v1.42) yang cuma jalan pintas buka MainActivity —
 * widget ini beneran interaktif langsung dari home screen.
 *
 * Status di-refresh dari SATU titik: AudioEnhancerService manggil refreshAll() tiap
 * `isRunning` berubah (bukan lewat updatePeriodMillis yang minimal 30 menit, dan bukan
 * didup-duplikasi di tiap entry point start/stop — semuanya udah lewat
 * AudioEnhancerService.requestStart()/requestStop(), jadi satu hook di situ otomatis
 * nutup semua jalur: MainActivity, BootReceiver, QS Tile, App Shortcut, dan widget ini
 * sendiri).
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
                    R.id.widget_status_dot,
                    if (running) R.drawable.widget_status_dot_on else R.drawable.widget_status_dot_off
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
