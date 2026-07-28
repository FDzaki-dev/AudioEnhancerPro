package com.audioenhancer.booster

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
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
        // Saat user swipe app dari recent apps, langsung jadwalkan restart cepat
        val restartIntent = Intent(applicationContext, RestartReceiver::class.java)
        sendBroadcast(restartIntent)
    }

    override fun onDestroy() {
        releaseEffects()
        wakeLock?.let { if (it.isHeld) it.release() }
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
    }

    fun setVirtualizerStrength(strength: Short) { // 0..1000
        try { virtualizer?.setStrength(strength) } catch (_: Exception) {}
    }

    fun setLoudnessGain(gainMb: Float) { // dalam milliBel, misal 0..3000
        try { loudnessEnhancer?.setTargetGain(gainMb.toInt()) } catch (_: Exception) {}
    }

    fun setEqualizerBand(band: Short, levelMb: Short) {
        try { equalizer?.setBandLevel(band, levelMb) } catch (_: Exception) {}
    }

    fun getEqualizer(): Equalizer? = equalizer

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AudioBooster::ServiceWakeLock"
        ).apply { setReferenceCounted(false); acquire(10 * 60 * 60 * 1000L) }
    }

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
