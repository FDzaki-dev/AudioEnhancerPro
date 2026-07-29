package com.audioenhancer.booster

import android.app.*
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Service utama: menempelkan efek audio ke sesi output global (session 0)
 * supaya boosting berlaku ke seluruh audio sistem, bukan hanya 1 aplikasi.
 * Berjalan sebagai foreground service (mediaPlayback) + START_STICKY + wakelock
 * supaya tidak mudah dibunuh oleh Android task manager.
 */
class AudioEnhancerService : Service() {

    companion object {
        const val CHANNEL_ID = "audio_booster_channel"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.audioenhancer.booster.STOP"
        var isRunning = false
            private set
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): AudioEnhancerService = this@AudioEnhancerService
    }
    override fun onBind(intent: Intent?): IBinder = binder

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        attachEffects()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        isRunning = true
        // START_STICKY: minta sistem restart service ini jika dibunuh karena low memory
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // SEBELUMNYA di sini ada kode yang aktif restart foreground service via broadcast
        // setiap kali app di-swipe dari recent apps. Itu dihapus karena JUSTRU jadi sumber
        // ketidakpastian: mulai Android 12, start foreground service dari background context
        // (seperti dari BroadcastReceiver setelah app tidak lagi foreground) dibatasi sistem —
        // kadang diizinkan kadang ditolak diam-diam tergantung timing, sehingga notifikasi
        // kadang muncul kadang hilang secara acak.
        //
        // Tidak perlu restart manual di sini sama sekali: service ini sudah punya
        // android:stopWithTask="false" di Manifest + return START_STICKY, yang berarti
        // Android SECARA DEFAULT tetap menjaga service ini hidup walau task di-swipe,
        // tanpa perlu trik tambahan yang justru rawan gagal.
    }

    override fun onDestroy() {
        releaseEffects()
        isRunning = false
        super.onDestroy()
    }

    /** Menempel ke audio session 0 = mixer output global perangkat. */
    private fun attachEffects() {
        try {
            bassBoost = BassBoost(0, 0).apply { enabled = true }
        } catch (e: Exception) { bassBoost = null }

        try {
            virtualizer = Virtualizer(0, 0).apply { enabled = true }
        } catch (e: Exception) { virtualizer = null }

        try {
            equalizer = Equalizer(0, 0).apply { enabled = true }
        } catch (e: Exception) { equalizer = null }

        try {
            loudnessEnhancer = LoudnessEnhancer(0).apply { enabled = true }
        } catch (e: Exception) { loudnessEnhancer = null }

        // Terapkan ulang setting terakhir yang tersimpan, supaya tidak balik ke default
        // setiap kali service ini dibuat ulang (app ditutup, task dikill, atau HP reboot).
        restoreSavedSettings()
    }

    private fun restoreSavedSettings() {
        setBassStrength(PrefsHelper.getBass(this).toShort())
        setVirtualizerStrength(PrefsHelper.getVirtualizer(this).toShort())
        setLoudnessGain(PrefsHelper.getLoudness(this))
    }

    private fun releaseEffects() {
        bassBoost?.release(); virtualizer?.release()
        equalizer?.release(); loudnessEnhancer?.release()
    }

    // ---- Kontrol dari UI ----
    fun isBassSupported(): Boolean = bassBoost != null
    fun isVirtualizerSupported(): Boolean = virtualizer != null
    fun isLoudnessSupported(): Boolean = loudnessEnhancer != null

    fun setBassStrength(strength: Short) { // 0..1000
        try { bassBoost?.setStrength(strength) } catch (_: Exception) {}
        PrefsHelper.setBass(this, strength.toInt())
    }

    fun setVirtualizerStrength(strength: Short) { // 0..1000
        try { virtualizer?.setStrength(strength) } catch (_: Exception) {}
        PrefsHelper.setVirtualizer(this, strength.toInt())
    }

    fun setLoudnessGain(gainMb: Float) { // dalam milliBel, misal 0..3000
        try { loudnessEnhancer?.setTargetGain(gainMb.toInt()) } catch (_: Exception) {}
        PrefsHelper.setLoudness(this, gainMb)
    }

    fun setEqualizerBand(band: Short, levelMb: Short) {
        try { equalizer?.setBandLevel(band, levelMb) } catch (_: Exception) {}
    }

    fun getEqualizer(): Equalizer? = equalizer

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Audio Booster Aktif",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Menampilkan status booster audio yang sedang berjalan" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AudioEnhancerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Booster aktif")
            .setContentText("Meningkatkan kualitas & volume audio sistem")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "Matikan", stopPending)
            .build()
    }
}
