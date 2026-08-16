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
 * Berjalan sebagai foreground service (mediaPlayback) + START_STICKY supaya
 * tidak mudah dibunuh oleh Android task manager.
 *
 * CATATAN: service ini TIDAK memegang PowerManager.WakeLock apa pun — jadi tidak
 * ada beban baterai dari wakelock yang lupa dilepas. "Tidak mudah dibunuh" di sini
 * murni dari kombinasi foreground service + START_STICKY, bukan dari wakelock.
 */
class AudioEnhancerService : Service() {

    /**
     * Batch 57: state nyata tiap AudioEffect — sebelumnya UI cuma tahu "object effect
     * berhasil dibuat" (`bassBoost != null`) via `isBassSupported()`, TIDAK ada bukti
     * effect itu ACTUALLY aktif/didengar di output, dan kalau OS mencabut kontrol effect
     * ini (mis. aplikasi lain minta priority lebih tinggi ke session yang sama) UI tidak
     * pernah tahu — badge tetap nampilin "Aktif" padahal engine diam. Lihat audit
     * eksternal "Gap #3: Tidak Ada Verifikasi Bahwa Effect Benar-Benar Aktif di Output"
     * & "Gap #4: Tidak Ada Handling AudioEffect Control Ownership".
     * - UNAVAILABLE: effect gagal dibuat sama sekali (device/chipset tidak support).
     * - AVAILABLE: effect ada & attached, TAPI sedang enabled=false (mis. abis "Matikan").
     * - ENABLED: effect ada, enabled=true, DAN kontrol dipegang penuh — kondisi sehat.
     * - FAILED: pemanggilan enable/attach melempar exception (bukan sekadar unsupported).
     * - CONTROL_LOST: OS mencabut kontrol effect ini dari app (`OnControlStatusChangeListener`
     *   melapor `controlGranted=false`) — effect object masih ada tapi TIDAK lagi
     *   memproses audio kita, walau `enabled` masih kebaca `true` di sisi app.
     */
    enum class EffectState { UNAVAILABLE, AVAILABLE, ENABLED, FAILED, CONTROL_LOST }

    companion object {
        private const val TAG = "AudioEnhancerService"
        const val CHANNEL_ID = "audio_booster_channel"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.audioenhancer.booster.STOP"
        // Batch 45: RACE CONDITION nyata ketemu. Field ini ditulis di main thread
        // (onStartCommand/onDestroy Service, dijamin main thread oleh framework),
        // TAPI dibaca dari THREAD LAIN juga: ServiceWatchdogWorker.doWork() jalan
        // sebagai CoroutineWorker WorkManager (background dispatcher, BUKAN main
        // thread) tiap 15 menit. Tanpa @Volatile, JMM TIDAK menjamin thread watchdog
        // lihat nilai TERBARU field ini (bisa baca versi stale dari cache CPU/register
        // core lain) — potensi 2 kegagalan diam-diam: watchdog nganggep service masih
        // hidup padahal udah mati (gagal restart, tujuan utama watchdog gagal total)
        // ATAU nganggep mati padahal hidup (restart double sia-sia). Widget/QS Tile
        // TIDAK kena isu sama karena BroadcastReceiver.onReceive/TileService callback
        // dijamin selalu main thread oleh framework Android — cuma watchdog yang beda
        // thread. @Volatile bikin baca-tulis field ini selalu langsung ke main memory
        // (happens-before), BUKAN cache lokal per-thread.
        @Volatile
        var isRunning = false
            private set

        /** Nyalakan service (atau re-enable efek kalau service masih hidup tapi lagi
         *  "dimatikan" lewat notifikasi). Dipakai bareng oleh MainActivity, BootReceiver,
         *  dan QuickToggleTileService — sebelumnya logika start ini terduplikasi 2x
         *  (MainActivity + BootReceiver) dengan copy-paste persis sama. */
        fun requestStart(context: android.content.Context) {
            val intent = Intent(context, AudioEnhancerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Matikan efek + lepas foreground. Pakai `startService` biasa (BUKAN
         *  `startForegroundService`) karena action ini cuma masuk akal dipanggil saat
         *  service SUDAH hidup & sudah dalam state foreground (lagi nampilin
         *  notifikasi) — sama seperti tombol "Matikan" di notifikasi yang sudah lebih
         *  dulu ada, yang juga pakai `PendingIntent.getService` biasa. */
        fun requestStop(context: android.content.Context) {
            val intent = Intent(context, AudioEnhancerService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
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

    // @Volatile: listener control/enable-status Android TIDAK dijamin dipanggil di main
    // thread (beda dari lifecycle callback Service/BroadcastReceiver yang selalu main
    // thread) — sama alasan seperti `isRunning` di atas (Batch 45), field ini dibaca dari
    // thread lain (mis. ViewModel/UI poll ke depan) jadi WAJIB visible langsung ke main
    // memory, bukan cache lokal per-thread.
    @Volatile var bassState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var virtualizerState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var loudnessState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var equalizerState: EffectState = EffectState.UNAVAILABLE; private set

    override fun onCreate() {
        super.onCreate()
        // Batch 45: "kunci" service ini di prioritas penjadwalan CPU tertinggi yang
        // disediakan Android buat kerja audio (sama seperti yang dipakai native audio
        // thread sistem), BUKAN cuma andalkan status "foreground service" (itu cuma
        // menaikkan importance/oom_adj buat gak gampang dibunuh, TIDAK otomatis
        // menaikkan nice-value penjadwalan CPU thread). Tanpa ini, panggilan
        // attachEffects()/enableEffects()/set*Strength() (IPC ke audio HAL) tetap
        // bisa antre di belakang proses lain kalau CPU lagi sibuk — dikunci di sini
        // supaya konsisten dapat slot CPU prioritas puncak, bukan naik-turun ikut
        // beban sistem. Dibungkus try-catch: SecurityException teoretis mungkin di
        // sebagian OEM yang restrict RT-priority, gagal diam-diam ke prioritas default
        // (bukan crash) kalau device tidak mengizinkan.
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) { }
        createNotificationChannel()
        attachEffects()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // PENTING: tidak cukup cuma stopSelf() di sini. Kalau MainActivity masih bound
            // (app masih kebuka), Service TIDAK akan benar-benar di-destroy oleh stopSelf() —
            // Android cuma men-destroy Service kalau ref-count "started" DAN "bound" sama-sama
            // nol. Makanya efek di-nonaktifkan & foreground dilepas SECARA EKSPLISIT di sini,
            // supaya "Matikan" selalu benar-benar mematikan efek walau app masih kebuka.
            disableEffects()
            isRunning = false
            // Batch 9: catat ini SEBAGAI PILIHAN USER (bukan OS yang bunuh), supaya
            // ServiceWatchdogWorker gak menghidupkan paksa lagi tiap 15 menit.
            PrefsHelper.setUserWantsRunning(this, false)
            BoosterWidgetProvider.refreshAll(this)
            // Batch 44 (bugfix): QS Tile SEBELUMNYA gak ikut diberi tahu di sini —
            // lihat catatan lengkap di `QuickToggleTileService.requestTileUpdate()`.
            QuickToggleTileService.requestTileUpdate(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        // Re-enable jaga-jaga kalau sebelumnya sempat di-"Matikan" lewat notifikasi sementara
        // Service-nya sendiri tetap hidup karena masih bound — tanpa ini, buka app lagi setelah
        // tap "Matikan" tidak akan menyalakan ulang efeknya.
        enableEffects()
        isRunning = true
        // Batch 9: tandai "user mau service ini hidup" tiap kali start beneran terjadi
        // (dari MainActivity, BootReceiver, QS Tile, Widget, atau Shortcut — semuanya
        // lewat requestStart() -> sini). ServiceWatchdogWorker baca flag ini buat
        // mutusin boleh/gaknya restart otomatis kalau nemu service mati.
        PrefsHelper.setUserWantsRunning(this, true)
        BoosterWidgetProvider.refreshAll(this)
        // Batch 44 (bugfix): sama seperti cabang ACTION_STOP di atas — QS Tile ikut
        // disinkronkan di sini juga (jalur "start").
        QuickToggleTileService.requestTileUpdate(this)
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
        BoosterWidgetProvider.refreshAll(this)
        // Batch 44 (bugfix): jalur "service di-destroy" (mis. dibunuh OS) juga ikut
        // sinkronkan QS Tile — cabang ke-3 & terakhir yang sebelumnya kelewat.
        QuickToggleTileService.requestTileUpdate(this)
        super.onDestroy()
    }

    /** Menempel ke audio session 0 = mixer output global perangkat.
     *  Batch 57: tiap effect sekarang dipasangi `OnControlStatusChangeListener` +
     *  `OnEnableStatusChangeListener` (API bawaan `android.media.audiofx.AudioEffect`,
     *  diwarisi semua 4 subclass di sini) — SEBELUMNYA object berhasil dibuat langsung
     *  dianggap "aktif" selamanya tanpa bukti lanjutan. Constructor gagal (chipset tidak
     *  support) TETAP `UNAVAILABLE` seperti sebelumnya (null check `isXxxSupported()` di
     *  bawah TIDAK berubah — kompatibel mundur). PERTAMA KALI dipakai di project ini —
     *  belum divalidasi runtime, kandidat pertama dicurigai kalau ada laporan badge/state
     *  baru ini tidak pernah berubah dari ENABLED atau crash saat callback terpanggil.
     *
     *  Batch 61 (audit Gap #4, lanjutan Batch 57): DIPECAH jadi 4 fungsi
     *  `attachBass()`/`attachVirtualizer()`/`attachEqualizer()`/`attachLoudness()` di
     *  bawah — SEBELUMNYA 4 blok try-catch ini nempel jadi 1 fungsi besar, gak bisa
     *  dipanggil ulang PER-EFFECT. Perilaku tiap blok saat dipanggil dari sini (startup
     *  normal) 100% SAMA seperti sebelum refactor — 0 logic berubah, cuma dipindah jadi
     *  fungsi terpisah supaya `retryControlAcquisition()` (baru, di bawah) bisa panggil
     *  ulang 1 effect spesifik tanpa reset effect lain yang sehat. */
    private fun attachEffects() {
        attachBass()
        attachVirtualizer()
        attachEqualizer()
        attachLoudness()

        // Terapkan ulang setting terakhir yang tersimpan, supaya tidak balik ke default
        // setiap kali service ini dibuat ulang (app ditutup, task dikill, atau HP reboot).
        restoreSavedSettings()
    }

    private fun attachBass() {
        try {
            bassBoost = BassBoost(0, 0).apply {
                enabled = true
                setControlStatusListener { _, granted ->
                    bassState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                }
                setEnableStatusListener { _, isEnabled ->
                    if (bassState != EffectState.CONTROL_LOST) {
                        bassState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                }
            }
            bassState = EffectState.ENABLED
        } catch (e: Exception) {
            bassBoost = null; bassState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "BassBoost tidak tersedia di device ini", e)
        }
    }

    private fun attachVirtualizer() {
        try {
            virtualizer = Virtualizer(0, 0).apply {
                enabled = true
                setControlStatusListener { _, granted ->
                    virtualizerState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                }
                setEnableStatusListener { _, isEnabled ->
                    if (virtualizerState != EffectState.CONTROL_LOST) {
                        virtualizerState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                }
            }
            virtualizerState = EffectState.ENABLED
        } catch (e: Exception) {
            virtualizer = null; virtualizerState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "Virtualizer tidak tersedia di device ini", e)
        }
    }

    private fun attachEqualizer() {
        try {
            equalizer = Equalizer(0, 0).apply {
                enabled = true
                setControlStatusListener { _, granted ->
                    equalizerState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                }
                setEnableStatusListener { _, isEnabled ->
                    if (equalizerState != EffectState.CONTROL_LOST) {
                        equalizerState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                }
            }
            equalizerState = EffectState.ENABLED
        } catch (e: Exception) {
            equalizer = null; equalizerState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "Equalizer tidak tersedia di device ini", e)
        }
    }

    private fun attachLoudness() {
        try {
            loudnessEnhancer = LoudnessEnhancer(0).apply {
                enabled = true
                setControlStatusListener { _, granted ->
                    loudnessState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                }
                setEnableStatusListener { _, isEnabled ->
                    if (loudnessState != EffectState.CONTROL_LOST) {
                        loudnessState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                }
            }
            loudnessState = EffectState.ENABLED
        } catch (e: Exception) {
            loudnessEnhancer = null; loudnessState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "LoudnessEnhancer tidak tersedia di device ini", e)
        }
    }

    /** Batch 61 (audit Gap #4 "Tidak Ada Handling AudioEffect Control Ownership" —
     *  lanjutan Batch 57 yang baru sebatas DETEKSI `CONTROL_LOST` via listener, belum
     *  ada strategi re-acquire/recovery apa pun): coba rebut kembali kontrol effect
     *  yang `CONTROL_LOST` ATAU `FAILED`, PER-EFFECT (bukan restart Service penuh) —
     *  release object lama (kalau masih ada) lalu recreate persis proses yang sama
     *  seperti startup pertama (`attachBass()` dkk di atas), lalu terapkan ulang
     *  setting slider terakhir (`restoreSavedSettings()`) SUPAYA user tidak kehilangan
     *  nilai yang mereka set. Effect yang sudah sehat (`ENABLED`/`AVAILABLE`) TIDAK
     *  disentuh sama sekali.
     *
     *  PENTING — TIDAK DIJAMIN BERHASIL: `CONTROL_LOST` artinya sistem Android sudah
     *  memutuskan app/effect LAIN menang priority-arbitration di session yang sama;
     *  recreate object di sini TIDAK mengubah priority (`BassBoost(0, 0)` dkk masih
     *  priority normal, sama seperti sebelumnya, SENGAJA tidak dinaikkan — menaikkan
     *  priority effect global session-0 punya efek samping ke app lain yang di luar
     *  scope batch ini). Kalau app lain masih pegang kontrol, effect ini kemungkinan
     *  besar akan langsung balik `CONTROL_LOST` lagi begitu listener baru terpasang —
     *  itu BUKAN bug fungsi ini, itu cara kerja arbitration Android yang memang di
     *  luar kendali 1 aplikasi manapun. Fungsi ini PALING BERGUNA buat kasus effect
     *  lain (mis. app lain) SUDAH release effect-nya duluan (skenario paling umum:
     *  user tutup app lain yang tadi rebut kontrol) tapi listener kita belum
     *  ke-trigger ulang otomatis oleh sistem.
     *
     *  SENGAJA belum ada pemanggil otomatis batch ini (bukan dari
     *  `ServiceWatchdogWorker`, bukan dari listener manapun) — cuma fungsi publik yang
     *  bisa dipanggil, BELUM disurface ke ViewModel/UI (pola sama seperti Batch 57:
     *  Service-layer dulu). Kalau nanti dipanggil otomatis dari watchdog (poll 15
     *  menit), PERLU hati-hati: jangan retry-loop rapat kalau kondisi persisten
     *  (device lain terus-terusan pegang kontrol) — bisa bikin churn object AudioEffect
     *  tanpa guna, potensi baterai/CPU sia-sia. Keputusan itu SENGAJA ditunda ke batch
     *  terpisah setelah ada cara uji/observasi perilakunya di device nyata.
     *
     *  @return true kalau ADA MINIMAL 1 effect yang di-retry, false kalau semua effect
     *  sudah sehat (tidak ada yang perlu di-retry) — pemanggil (ke depan: UI/watchdog)
     *  bisa pakai ini buat tahu apakah aksi retry ini benar-benar melakukan sesuatu. */
    fun retryControlAcquisition(): Boolean {
        var retried = false
        if (bassState == EffectState.CONTROL_LOST || bassState == EffectState.FAILED) {
            try { bassBoost?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release BassBoost lama sebelum retry", e)
            }
            attachBass()
            retried = true
        }
        if (virtualizerState == EffectState.CONTROL_LOST || virtualizerState == EffectState.FAILED) {
            try { virtualizer?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release Virtualizer lama sebelum retry", e)
            }
            attachVirtualizer()
            retried = true
        }
        if (equalizerState == EffectState.CONTROL_LOST || equalizerState == EffectState.FAILED) {
            try { equalizer?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release Equalizer lama sebelum retry", e)
            }
            attachEqualizer()
            retried = true
        }
        if (loudnessState == EffectState.CONTROL_LOST || loudnessState == EffectState.FAILED) {
            try { loudnessEnhancer?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release LoudnessEnhancer lama sebelum retry", e)
            }
            attachLoudness()
            retried = true
        }
        if (retried) {
            android.util.Log.w(TAG, "retryControlAcquisition(): recreate effect yang CONTROL_LOST/FAILED")
            restoreSavedSettings()
        }
        return retried
    }

    private fun restoreSavedSettings() {
        setBassStrength(PrefsHelper.getBass(this).toShort())
        setVirtualizerStrength(PrefsHelper.getVirtualizer(this).toShort())
        setLoudnessGain(PrefsHelper.getLoudness(this))

        equalizer?.let { eq ->
            try {
                for (band in 0 until eq.numberOfBands) {
                    val saved = PrefsHelper.getEqualizerBandLevel(this, band, 0)
                    eq.setBandLevel(band.toShort(), saved.toShort())
                }
            } catch (_: Exception) { }
        }
    }

    private fun releaseEffects() {
        bassBoost?.release(); virtualizer?.release()
        equalizer?.release(); loudnessEnhancer?.release()
        // Batch 57: object sudah dilepas total, state HARUS balik UNAVAILABLE — kalau
        // dibiarkan ENABLED/CONTROL_LOST, pembaca state (ke depan: ViewModel/UI) bisa
        // salah kira effect masih hidup padahal Service ini sendiri sudah di-destroy.
        bassState = EffectState.UNAVAILABLE
        virtualizerState = EffectState.UNAVAILABLE
        loudnessState = EffectState.UNAVAILABLE
        equalizerState = EffectState.UNAVAILABLE
    }

    /** Dipanggil dari notifikasi "Matikan" — reversible (beda dari releaseEffects yang
     *  benar-benar melepas objek AudioEffect saat Service betulan di-destroy).
     *  Batch 57: exception di sini SENGAJA tetap dicatat cuma via Logcat (bukan diubah
     *  jadi FAILED) — kegagalan disable saat user MEMANG minta "Matikan" bukan kegagalan
     *  engine yang perlu ditandai merah ke UI, `OnEnableStatusChangeListener` di atas juga
     *  akan reflect state sebenarnya kalau enabled beneran berhasil diubah sistem. */
    private fun disableEffects() {
        try { bassBoost?.enabled = false } catch (e: Exception) { android.util.Log.e(TAG, "Gagal disable BassBoost", e) }
        try { virtualizer?.enabled = false } catch (e: Exception) { android.util.Log.e(TAG, "Gagal disable Virtualizer", e) }
        try { equalizer?.enabled = false } catch (e: Exception) { android.util.Log.e(TAG, "Gagal disable Equalizer", e) }
        try { loudnessEnhancer?.enabled = false } catch (e: Exception) { android.util.Log.e(TAG, "Gagal disable LoudnessEnhancer", e) }
    }

    /** Nyalakan ulang efek yang sempat di-nonaktifkan lewat notifikasi "Matikan".
     *  Batch 57 (audit Gap #3/#13 "enableEffects() terlalu silent"): exception di sini
     *  SEKARANG diekspos — Log.e (diagnostik) + state per-effect ditandai `FAILED` (beda
     *  dari `CONTROL_LOST`, yang datang dari listener sistem, bukan dari exception lokal
     *  saat pemanggilan `.enabled = true`). */
    private fun enableEffects() {
        try { bassBoost?.enabled = true } catch (e: Exception) {
            bassState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable BassBoost", e)
        }
        try { virtualizer?.enabled = true } catch (e: Exception) {
            virtualizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable Virtualizer", e)
        }
        try { equalizer?.enabled = true } catch (e: Exception) {
            equalizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable Equalizer", e)
        }
        try { loudnessEnhancer?.enabled = true } catch (e: Exception) {
            loudnessState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable LoudnessEnhancer", e)
        }
    }

    // ---- Kontrol dari UI ----
    fun isBassSupported(): Boolean = bassBoost != null
    fun isVirtualizerSupported(): Boolean = virtualizer != null
    fun isLoudnessSupported(): Boolean = loudnessEnhancer != null

    // Batch 57 (audit Gap #14 "setting tetap disimpan walau engine gagal"): PrefsHelper
    // TETAP disimpan tanpa syarat di 4 fungsi ini SENGAJA — kalau save digagalkan pas
    // apply gagal, restart berikutnya user malah kehilangan preferensi slider yang mereka
    // set (worse UX). Yang berubah cuma: kegagalan `set*` ke effect sekarang di-Log.e
    // (sebelumnya silent total), dan (bass/virtualizer/equalizer) menandai state FAILED
    // biar gap #12 "isRunning != actual processing" makin sempit — persistence vs
    // reconciliation state penuh (4 sisi: UI/persisted/actual effect/output route) masih
    // gap terbuka, di luar scope batch ini (lihat audit Gap #15).
    //
    // Batch 60 (audit Gap #7 "range kontrol hard-coded, belum dinormalisasi dari
    // capability aktual device"): DICEK ULANG via dokumentasi resmi Android SDK
    // (BassBoost/Virtualizer.setStrength) SEBELUM nulis kode apa pun — [0, 1000]
    // (per mille) BUKAN asumsi hard-coded yang salah, itu KONTRAK API PLATFORM tetap
    // yang sama di semua device (bukan device-specific range yang perlu di-query kayak
    // Equalizer.bandLevelRange). Normalisasi capability YANG BENERAN ada & relevan buat
    // 2 effect ini cuma 2: (1) `strengthSupported` — SUDAH di-cek sejak sebelum Batch 57
    // (`isBassStrengthSupported()`/`isVirtualizerStrengthSupported()` di bawah, dipakai
    // `BoosterScreen` buat disable slider), (2) *rounding* — device BOLEH membulatkan
    // strength yang diminta ke nilai terdekat yang didukung tanpa lapor balik via
    // exception (dokumentasi resmi: "it is allowed to round the given strength to the
    // nearest supported value"), jadi nilai yang BENERAN aktif di effect bisa beda dari
    // yang di-set — ini yang SEBELUMNYA gak pernah dibaca balik sama sekali. Fungsi
    // `getBassRoundedStrength()`/`getVirtualizerRoundedStrength()` (grup fungsi di bawah)
    // + Log.w diagnostik di `setBassStrength()`/`setVirtualizerStrength()` menutup gap
    // ini. SENGAJA belum disurface ke ViewModel/UI batch ini (pola sama seperti Batch 57:
    // Service-layer dulu, UI kalau perlu batch berikutnya) — beda dari EffectState
    // (Batch 57→58) karena dampak rounding biasanya cuma beda 1-2 unit per mille (nyaris
    // tak terlihat di slider 0..1000), jadi Log.w diagnostik dulu cukup buat batch ini;
    // baru disurface ke UI kalau ada laporan device nyata yang roundingnya signifikan.
    //
    // LoudnessEnhancer target gain SENGAJA TIDAK disentuh batch ini: dicek juga di
    // dokumentasi resmi, effect ini TIDAK punya API query range sama sekali (beda dari
    // BassBoost/Virtualizer/Equalizer yang punya `strengthSupported`/`bandLevelRange`) —
    // gak ada cara "capability detection" yang bisa diimplementasikan dari sisi app.
    // Device yang menolak suatu gainmB akan lempar `IllegalArgumentException`, yang
    // SUDAH tertangkap generic `catch (e: Exception)` di `setLoudnessGain()` (state
    // FAILED + Log.e, sejak Batch 57) — jalur ini SUDAH gap-closed, tidak butuh
    // perubahan baru. Master limiter/gain-staging yang lebih menyeluruh tetap item
    // terpisah (roadmap.md Fase 0 #5), bukan scope "capability detection" ini.
    fun setBassStrength(strength: Short) { // 0..1000, per mille — lihat komentar di atas
        try {
            bassBoost?.setStrength(strength)
            if (bassBoost?.strengthSupported == true) {
                val rounded = bassBoost?.roundedStrength ?: strength
                if (rounded != strength) {
                    android.util.Log.w(TAG, "BassBoost strength diminta=$strength dibulatkan device ke=$rounded")
                }
            }
        } catch (e: Exception) {
            bassState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set BassBoost strength", e)
        }
        PrefsHelper.setBass(this, strength.toInt())
    }

    fun setVirtualizerStrength(strength: Short) { // 0..1000, per mille — lihat komentar di atas
        try {
            virtualizer?.setStrength(strength)
            if (virtualizer?.strengthSupported == true) {
                val rounded = virtualizer?.roundedStrength ?: strength
                if (rounded != strength) {
                    android.util.Log.w(TAG, "Virtualizer strength diminta=$strength dibulatkan device ke=$rounded")
                }
            }
        } catch (e: Exception) {
            virtualizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set Virtualizer strength", e)
        }
        PrefsHelper.setVirtualizer(this, strength.toInt())
    }

    fun setLoudnessGain(gainMb: Float) { // dalam milliBel, misal 0..3000 — tidak ada API range query (lihat komentar di atas)
        try { loudnessEnhancer?.setTargetGain(gainMb.toInt()) } catch (e: Exception) {
            loudnessState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set LoudnessEnhancer gain", e)
        }
        PrefsHelper.setLoudness(this, gainMb)
    }

    fun setEqualizerBand(band: Short, levelMb: Short) {
        try { equalizer?.setBandLevel(band, levelMb) } catch (e: Exception) {
            equalizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set Equalizer band $band", e)
        }
        PrefsHelper.setEqualizerBandLevel(this, band.toInt(), levelMb.toInt())
    }

    // ---- Info tambahan untuk UI: bedakan "efek tidak ada sama sekali" vs "ada tapi
    // kontrol kekuatan/strength granular tidak didukung chipset ini" ----
    fun isBassStrengthSupported(): Boolean =
        try { bassBoost?.strengthSupported ?: false } catch (_: Exception) { false }

    fun isVirtualizerStrengthSupported(): Boolean =
        try { virtualizer?.strengthSupported ?: false } catch (_: Exception) { false }

    // Batch 60: nilai strength AKTUAL yang device pakai setelah pembulatan (lihat
    // komentar panjang di atas `setBassStrength()`) — beda dari nilai yang di-set kalau
    // device tidak mendukung akurasi per mille penuh. Belum dikonsumsi ViewModel/UI
    // (diagnostik/Log.w dulu cukup untuk batch ini).
    fun getBassRoundedStrength(): Short =
        try { bassBoost?.roundedStrength ?: 0 } catch (_: Exception) { 0 }

    fun getVirtualizerRoundedStrength(): Short =
        try { virtualizer?.roundedStrength ?: 0 } catch (_: Exception) { 0 }

    // ---- Equalizer per-band: dipakai UI untuk membangun slider per pita frekuensi ----
    fun isEqualizerSupported(): Boolean = equalizer != null

    fun getEqualizerBandCount(): Int =
        try { equalizer?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }

    /** [min, max] dalam milliBel. */
    fun getEqualizerLevelRange(): ShortArray =
        try { equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500) } catch (_: Exception) { shortArrayOf(-1500, 1500) }

    /** Frekuensi tengah band dalam Hz (Android API mengembalikan milliHertz). */
    fun getEqualizerBandCenterFreqHz(band: Int): Int =
        try { (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000 } catch (_: Exception) { 0 }

    fun getEqualizerBandLevel(band: Int): Short =
        try { equalizer?.getBandLevel(band.toShort()) ?: 0 } catch (_: Exception) { 0 }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_desc) }
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
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.notif_action_stop), stopPending)
            .build()
    }
}
