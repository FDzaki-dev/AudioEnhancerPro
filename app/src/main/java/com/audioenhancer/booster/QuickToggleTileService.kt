package com.audioenhancer.booster

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Tile Quick Settings buat nyalain/matiin AudioEnhancerService langsung dari
 * notification shade — mirip tile "1.1.1.1" milik Cloudflare, gak perlu buka
 * MainActivity/nongkrong di recent apps dulu buat toggle fitur.
 *
 * PENTING (biar ekspektasi jelas): tile ini MURNI kemudahan akses (UX shortcut).
 * Ini BUKAN solusi buat masalah OEM battery-killer Infinix yang sempat dibahas —
 * itu limitasi battery manager custom Transsion yang beroperasi di luar jangkauan
 * kode aplikasi manapun. Manfaat nyata tile ini: (1) toggle instan tanpa buka app,
 * (2) cara tercepat buat "membangunkan" ulang service kalau sempat dimatikan OEM,
 * tanpa perlu cari-cari app dulu.
 */
class QuickToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val wasRunning = AudioEnhancerService.isRunning
        if (wasRunning) {
            AudioEnhancerService.requestStop(this)
        } else {
            AudioEnhancerService.requestStart(this)
        }
        // Optimistic update: onStartCommand di service butuh sepersekian detik buat
        // benar-benar apply, jadi tile di-update duluan asumsi berhasil. Kalau
        // ternyata gagal (mis. attachEffects gagal), tile bakal balik sinkron begitu
        // onStartListening jalan lagi (tiap kali shade dibuka ulang).
        refreshTile(optimisticRunning = !wasRunning)
    }

    private fun refreshTile(optimisticRunning: Boolean? = null) {
        val tile = qsTile ?: return
        val running = optimisticRunning ?: AudioEnhancerService.isRunning
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.qs_tile_subtitle_on else R.string.qs_tile_subtitle_off)
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)
        tile.updateTile()
    }
}
