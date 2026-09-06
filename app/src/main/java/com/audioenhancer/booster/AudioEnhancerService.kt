package com.audioenhancer.booster

import android.app.*
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
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

        // Batch 87 (roadmap.md Fase 0 #6 "Rebuild arsitektur session-0", FASE 1 dari
        // rebuild bertahap — bukan seluruh item #6 sekaligus, lihat PENDING_Fase0_
        // Item6_RebuildSessionZero.md buat sisa fase). Bagian PALING konkret & PALING
        // rendah-risiko dari "strategi modern (DynamicsProcessing/post-processing)
        // sebagai fallback" yang diminta roadmap: kalau `Equalizer` legacy device ini
        // UNAVAILABLE total (chipset/HAL tidak expose sama sekali — kasus jarang tapi
        // NYATA, lihat roadmap.md Fase 0 #2 "belum ada fallback engine kalau effect
        // null"), `DynamicsProcessing` yang SUDAH dipasang buat limiter (Batch 84)
        // SEKARANG JUGA dipasangi PreEq stage 5-band sebagai pengganti. 5 titik
        // frekuensi ini TIDAK di-query dari device — TIDAK ADA API resmi query "band
        // layout ideal" dari `DynamicsProcessing` (beda dari `Equalizer.numberOfBands`/
        // `getCenterFreq()` yang device-specific) — jadi ini pilihan TETAP/arbitrary,
        // representatif rentang audible umum (bass dalam -> treble tinggi), dicek dulu
        // ke dokumentasi resmi `DynamicsProcessing.EqBand`: parameter constructor
        // `cutoffFrequency` = frekuensi TERATAS yang diproses band itu (bukan frekuensi
        // tengah kayak `Equalizer.getCenterFreq()`), band HARUS naik urutannya — 5 angka
        // di bawah sudah menaik, aman. `getEqualizerBandCenterFreqHz()` di bawah tetap
        // mengembalikan angka ini apa adanya buat label UI (pendekatan, bukan center Hz
        // sesungguhnya — beda semantik dicatat, dampak ke user cuma label, bukan fungsi).
        private val FALLBACK_EQ_BANDS_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        // +-12 dB (1200 mB): TIDAK ADA API resmi query gain range EqBand per-device
        // (beda dari `Equalizer.bandLevelRange` yang device-aware) — angka konservatif,
        // filosofi sama seperti limiter Batch 84 (ceiling -1 dBFS SUDAH terpasang di
        // effect yang SAMA, jadi walau user set semua band fallback ke +12 dB sekaligus,
        // limiter di bawahnya tetap jadi pengaman terakhir).
        private const val FALLBACK_EQ_RANGE_MB: Short = 1200
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

    // Batch 84 (roadmap.md Fase 0 #5, "Gain staging + dynamics pipeline"): effect
    // TAMBAHAN, bukan pengganti 4 effect di atas — lihat komentar panjang di
    // `attachDynamicsProcessing()` soal apa yang dipasang & kenapa.
    private var dynamicsProcessing: DynamicsProcessing? = null

    // Batch 87: true kalau `dynamicsProcessing` di atas SEDANG berfungsi ganda sebagai
    // pengganti `Equalizer` (fallback, lihat `FALLBACK_EQ_BANDS_HZ`/`attachDynamicsProcessing()`)
    // KARENA `equalizer` (field di atas) UNAVAILABLE di device ini — false di mayoritas
    // device (Equalizer legacy tetap dipakai apa adanya, 0 perubahan perilaku). Dibaca
    // `isEqualizerSupported()`/`setEqualizerBand()`/`getEqualizerBand*()` di bawah buat
    // menentukan rute mana yang dipakai — TIDAK disurface ke ViewModel/UI batch ini
    // (pola sama seperti Batch 60/83: Service-layer dulu; kandidat kuat roadmap.md Fase
    // 0 #9 kalau nanti user mau UI beda tampilan "EQ asli" vs "EQ fallback").
    private var equalizerFallbackActive: Boolean = false

    // Batch 87: cache lokal nilai gain per-band (mB) yang SEDANG diterapkan ke fallback —
    // dibutuhkan karena beda dari `Equalizer.getBandLevel()` (baca balik dari effect asli),
    // `DynamicsProcessing.EqBand` TIDAK expose getter baca-balik gain per-band yang praktis
    // dipanggil dari instance effect langsung (cuma ada lewat objek `Config`, jalur baca
    // terpisah dari objek live `dynamicsProcessing` di atas) — cache ini SUMBER KEBENARAN
    // buat `getEqualizerBandLevel()` versi fallback, PrefsHelper tetap sumber kebenaran
    // lintas restart (sama seperti effect lain di file ini).
    private val fallbackEqGainsMb = ShortArray(FALLBACK_EQ_BANDS_HZ.size)

    // @Volatile: listener control/enable-status Android TIDAK dijamin dipanggil di main
    // thread (beda dari lifecycle callback Service/BroadcastReceiver yang selalu main
    // thread) — sama alasan seperti `isRunning` di atas (Batch 45), field ini dibaca dari
    // thread lain (mis. ViewModel/UI poll ke depan) jadi WAJIB visible langsung ke main
    // memory, bukan cache lokal per-thread.
    @Volatile var bassState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var virtualizerState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var loudnessState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var equalizerState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var dynamicsState: EffectState = EffectState.UNAVAILABLE; private set

    // Batch 82 (roadmap.md Fase 0 #3, "Output routing awareness"): deskripsi ringkas
    // sink output TERAKHIR yang terdeteksi (mis. "Bluetooth A2DP (terhubung)") — diisi
    // `onOutputRouteChanged()` di bawah. @Volatile: sama alasan seperti state effect di
    // atas, ditulis dari callback sistem (thread TIDAK dijamin sama dengan pembaca ke
    // depan kalau ViewModel/UI mulai poll field ini). SENGAJA belum dikonsumsi
    // ViewModel/UI batch ini (pola sama seperti Batch 60: Service-layer dulu, Log
    // diagnostik cukup untuk batch ini, surface ke UI kalau ada kebutuhan/laporan nyata
    // dari device — kandidat kuat buat roadmap.md Fase 0 #9 "UI/error-state lanjutan").
    @Volatile var lastOutputRouteDescription: String? = null; private set

    private var audioManager: AudioManager? = null

    // Batch 82: listener perubahan device audio SISTEM (bukan cuma sesi app ini) — cara
    // resmi Android modern (API 23+, project ini minSdk 31 jadi selalu tersedia) untuk tahu
    // kapan sink output BERPINDAH (speaker->Bluetooth, headset dicabut, USB DAC nyambung,
    // dst) TANPA perlu polling. Kelas anonim (bukan fungsi top-level) supaya bisa
    // unregister persis instance yang sama di `onDestroy()` (API `unregisterAudioDeviceCallback`
    // butuh reference objek yang SAMA persis dengan yang di-register, bukan instance baru).
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            onOutputRouteChanged(addedDevices, added = true)
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            onOutputRouteChanged(removedDevices, added = false)
        }
    }

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
        // Batch 82: register SETELAH attachEffects() — urutan tidak kritis (callback baru
        // aktif async lewat sistem), tapi biar konsisten "state effect dulu baru listener
        // tambahan" sama seperti pola attachXxx() di atas. Dibungkus try-catch: teoretis
        // OEM tertentu bisa restrict (belum ada laporan nyata), gagal diam-diam ke Log.e
        // daripada crash Service ini seluruhnya cuma gara-gara 1 listener opsional.
        try {
            audioManager = getSystemService(AudioManager::class.java)
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal register AudioDeviceCallback (output routing awareness nonaktif)", e)
        }
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
        // Batch 82: lepas listener SEBELUM releaseEffects() — urutan ini yang penting
        // (beda dari onCreate di atas): Service mau mati, hentikan dulu sumber callback
        // baru masuk supaya tidak ada race kecil `onOutputRouteChanged()` terpanggil
        // (mis. panggil `enableEffects()`) di tengah/sesudah effect object dilepas.
        try { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal unregister AudioDeviceCallback", e)
        }
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
        // Batch 84: dipanggil PALING TERAKHIR secara kode — TAPI ini urutan penulisan
        // kode saja, BUKAN jaminan urutan proses sinyal DSP aktual. Effect session-0
        // legacy (API `AudioEffect` publik ini) TIDAK punya API resmi buat app menentukan
        // urutan insert di chain HAL — itu justru salah satu alasan utama roadmap.md Fase
        // 0 #6 ("Rebuild session-0 architecture") ada sebagai item terpisah yang jauh
        // lebih besar. Lihat komentar panjang di `attachDynamicsProcessing()` untuk detail.
        attachDynamicsProcessing()

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

    /** Batch 84 (roadmap.md Fase 0 #5, "Gain staging + dynamics pipeline"): effect
     *  TAMBAHAN (bukan pengganti 4 effect di atas) — dipasang sebagai `DynamicsProcessing`
     *  BERISI HANYA stage limiter (0 pre-EQ band, 0 MBC band, 0 post-EQ band,
     *  `limiterInUse=true` saja), fungsi SATU-SATUNYA: jadi "ceiling" pengaman terakhir
     *  supaya kombinasi Bass+Virtualizer+EQ+Loudness yang di-set user TINGGI BERBARENGAN
     *  tidak numpuk sampai lewat 0 dBFS (clipping/distorsi) — SEBELUMNYA nol proteksi
     *  apa pun terhadap skenario ini (audit: "belum ada master limiter/compressor
     *  terkontrol").
     *
     *  KENAPA INI BUKAN "#6 Rebuild session-0" (item terpisah, jauh lebih besar): effect
     *  ini MENAMBAH satu stage limiter, TIDAK mengganti/menata-ulang 4 effect legacy di
     *  atas. Audit asli minta pipeline eksplisit "Input → Pre-Gain → EQ → Dynamics →
     *  Loudness → Output" — API `AudioEffect` publik session-0 TIDAK punya cara resmi
     *  buat app memaksa urutan insert semacam itu di HAL (semua effect session-0 nyambung
     *  independen, urutan proses akhir ditentukan sistem/HAL, di luar kendali app). Jadi
     *  limiter ini BERFUNGSI sebagai ceiling tambahan yang mestinya tetap efektif terlepas
     *  dari urutan proses effect lain (limiter menangkap level SETELAH semua effect ikut
     *  campur ke sinyal, bukan sebelum) — TAPI urutan pasti "Dynamics SEBELUM Loudness"
     *  seperti diminta audit TIDAK bisa dijamin tanpa rebuild ke API modern (#6).
     *
     *  Parameter (HARDCODED, belum ada slider UI — murni safety net, bukan fitur
     *  loudness-maximizer baru):
     *  - threshold -1 dBFS, ratio 20:1 (nyaris brickwall), attack 3ms (cepat, tangkap
     *    transient) — target: baru aktif kalau sinyal beneran mepet clipping.
     *  - releaseTime 60ms (moderat) — cukup cepat buat audio umum, TIDAK terlalu agresif
     *    sampai "pumping" (volume naik-turun kedengaran, distorsi persepsi) yang biasa
     *    muncul kalau release limiter kelewat cepat.
     *  - postGain 0 dB — SENGAJA tidak menambah volume; ini ceiling pasif, bukan
     *    pengganti/duplikat `LoudnessEnhancer` yang MEMANG untuk menaikkan loudness.
     *
     *  channelCount di-hardcode 2 (stereo): `DynamicsProcessing.Config.Builder` (beda
     *  dari BassBoost/Virtualizer/Equalizer/LoudnessEnhancer di atas) BUTUH channelCount
     *  eksplisit di construction time, dan TIDAK ada API resmi buat query channel count
     *  OUTPUT sistem yang sedang aktif dari sisi effect sebelum construct. Stereo adalah
     *  default hampir universal consumer Android (speaker device modern, Bluetooth,
     *  wired umumnya stereo) — device mono-only (kalau ada) BELUM divalidasi, kandidat
     *  gap pertama kalau ada laporan `IllegalArgumentException`/crash di device semacam
     *  itu (dicatat juga di `roadmap.md`).
     *
     *  Pola attach/state SAMA PERSIS 4 fungsi di atas (`dynamicsState` ikut
     *  `retryControlAcquisition()`, `releaseEffects()`, `disableEffects()`,
     *  `enableEffects()` — lihat masing-masing) supaya konsisten, TERMASUK ikut nudge
     *  `enableEffects()` di `onOutputRouteChanged()` (Batch 82/83) tanpa perubahan apa pun
     *  di fungsi itu.
     *
     *  **KOREKSI PENTING (masih Batch 84, ditemukan & diperbaiki SEBELUM zip dikirim)**:
     *  `DynamicsProcessing` baru ada sejak API 28 (Android 9/Pie) — SEMPAT salah asumsi
     *  minSdk project ini 31 (ikut deskripsi generik role, BUKAN fakta project ini),
     *  padahal `app/build.gradle.kts` project ini `minSdk = 24`. Referensi LANGSUNG ke
     *  class ini (construct/import) di device API 24-27 melempar `NoClassDefFoundError`
     *  — itu subclass `Error`, BUKAN `Exception`, jadi `catch (e: Exception)` di bawah
     *  TIDAK AKAN menangkapnya — app bisa crash total di device lama kalau tidak
     *  di-guard. Makanya SELURUH isi fungsi ini sekarang dibungkus
     *  `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` — pola standar Android untuk
     *  API level gating (aman untuk minSdk 24 project ini, yang sudah ART-only sejak
     *  Android 5.0, bukan era Dalvik lama yang kadang verify eager). Di bawah API 28,
     *  `dynamicsState` langsung `UNAVAILABLE` (diperlakukan SAMA seperti "chipset tidak
     *  dukung" — dari sudut pandang user/UI, hasilnya sama: limiter tidak ada). */
    private fun attachDynamicsProcessing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            dynamicsProcessing = null; dynamicsState = EffectState.UNAVAILABLE
            equalizerFallbackActive = false // Batch 87: API<28 = tidak ada limiter MAUPUN fallback EQ, sama-sama UNAVAILABLE
            return
        }
        // Batch 87 (roadmap.md Fase 0 #6, FASE 1): dievaluasi SEBELUM try-block karena
        // `attachEqualizer()` SELALU dipanggil duluan (urutan tetap di `attachEffects()`/
        // `retryControlAcquisition()`, TIDAK diubah) — `equalizerState` di titik ini SUDAH
        // final (ENABLED = Equalizer legacy device ini sehat, biarkan apa adanya; UNAVAILABLE
        // = device ini TIDAK expose Equalizer legacy sama sekali, baru di sini fallback coba
        // diaktifkan). needsEqFallback FALSE di mayoritas device (Equalizer legacy normal) —
        // jalur situ 100% identik kode lama, 0 perubahan perilaku.
        val needsEqFallback = equalizerState == EffectState.UNAVAILABLE
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, // respons transient limiter lebih relevan dari resolusi frekuensi di sini
                2,                                                // channelCount (stereo, lihat catatan panjang di atas)
                needsEqFallback, if (needsEqFallback) FALLBACK_EQ_BANDS_HZ.size else 0, // pre-EQ: Batch 87, HANYA aktif kalau Equalizer legacy UNAVAILABLE
                false, 0, // multi-band compressor: di luar scope "master limiter"/"EQ fallback" batch ini
                false, 0, // post-EQ: tidak dipakai
                true      // limiter: tetap selalu dipakai (Batch 84), lepas dari status fallback EQ
            ).build()
            dynamicsProcessing = DynamicsProcessing(0, 0, config).apply {
                // Batch 87: band count HARUS sama dengan preEqBandCount di Config di atas
                // (kontrak `Eq`/constructor Config — dicek dokumentasi resmi sebelum ditulis).
                // Gain awal 0 dB (netral) — nilai tersimpan (kalau ada) diterapkan belakangan
                // lewat `restoreSavedSettings()`, SAMA seperti pola Equalizer asli, jangan
                // duplikat logic restore di sini.
                if (needsEqFallback) {
                    FALLBACK_EQ_BANDS_HZ.forEachIndexed { index, freqHz ->
                        setPreEqBandAllChannelsTo(index, DynamicsProcessing.EqBand(true, freqHz, 0f))
                    }
                }
                setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(
                        /* inUse        = */ true, // FIX (v133): constructor Limiter TIDAK punya param channelIndex —
                                                    // param pertama sebenarnya `inUse: Boolean` (lihat android.media.audiofx.DynamicsProcessing.Limiter).
                                                    // Literal `0` (Int) di posisi ini yang bikin compileDebugKotlin gagal (run 133).
                        /* enabled      = */ true,
                        /* linkGroup    = */ 0,
                        /* attackTime   = */ 3f,
                        /* releaseTime  = */ 60f,
                        /* ratio        = */ 20f,
                        /* threshold    = */ -1f,
                        /* postGain     = */ 0f
                    )
                )
                enabled = true
                // Batch 87: kalau fallback aktif, listener yang SAMA (satu-satunya objek
                // effect ini) SEKARANG juga menentukan `equalizerState` — objek limiter &
                // objek "EQ" adalah literal 1 instance yang sama di jalur fallback, jadi
                // status kontrol/enable-nya memang SATU. Kalau fallback TIDAK aktif
                // (mayoritas device), baris `if (needsEqFallback)` di bawah tidak pernah
                // jalan — `equalizerState` 100% tidak disentuh dari sini, persis kode lama.
                setControlStatusListener { _, granted ->
                    dynamicsState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                    if (needsEqFallback) equalizerState = dynamicsState
                }
                setEnableStatusListener { _, isEnabled ->
                    if (dynamicsState != EffectState.CONTROL_LOST) {
                        dynamicsState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                        if (needsEqFallback && equalizerState != EffectState.CONTROL_LOST) equalizerState = dynamicsState
                    }
                }
            }
            dynamicsState = EffectState.ENABLED
            equalizerFallbackActive = needsEqFallback
            // Batch 87: "upgrade" equalizerState dari UNAVAILABLE -> ENABLED HANYA di jalur
            // fallback (needsEqFallback true berarti equalizerState memang UNAVAILABLE tepat
            // sebelum baris ini — lihat definisi needsEqFallback di atas) — TIDAK PERNAH
            // menimpa status Equalizer legacy yang sudah sehat (jalur itu tidak lewat sini).
            if (needsEqFallback) equalizerState = EffectState.ENABLED
        } catch (e: Exception) {
            dynamicsProcessing = null; dynamicsState = EffectState.UNAVAILABLE
            equalizerFallbackActive = false
            // Batch 87: equalizerState SENGAJA TIDAK disentuh di sini kalau needsEqFallback
            // true — sudah UNAVAILABLE dari attachEqualizer() sebelumnya (Equalizer legacy
            // gagal), sekarang fallback-nya JUGA gagal (device ini API<28 pun sudah return
            // duluan di atas, jadi exception di sini artinya construct DynamicsProcessing
            // sendiri yang gagal) — hasil akhirnya tetap UNAVAILABLE, konsisten, bukan silent
            // fallback ke state lain yang menyesatkan UI.
            android.util.Log.e(
                TAG,
                "DynamicsProcessing (master limiter${if (needsEqFallback) " + EQ fallback" else ""}) tidak tersedia di device ini",
                e
            )
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
        if (dynamicsState == EffectState.CONTROL_LOST || dynamicsState == EffectState.FAILED) {
            try { dynamicsProcessing?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release DynamicsProcessing lama sebelum retry", e)
            }
            attachDynamicsProcessing()
            retried = true
        }
        if (retried) {
            android.util.Log.w(TAG, "retryControlAcquisition(): recreate effect yang CONTROL_LOST/FAILED")
            restoreSavedSettings()
        }
        return retried
    }

    /** Batch 82 (roadmap.md Fase 0 #3, audit Gap "Output routing awareness" — audio
     *  session 0 tidak dijamin "menempel" seragam di semua HAL vendor saat sink output
     *  berpindah, lihat komentar panjang `EffectState` di atas soal kenapa gap ini beda
     *  dari `CONTROL_LOST`). Dipanggil `AudioDeviceCallback` tiap ada device audio
     *  SISTEM nyambung/lepas — filter `isSink` dulu (buang device INPUT seperti mic
     *  eksternal, tidak relevan buat effect output session-0 di sini).
     *
     *  Aksi yang diambil SENGAJA ringan (bukan `retryControlAcquisition()`): cuma
     *  re-assert `enabled = true` (lewat `enableEffects()` yang SUDAH ADA, idempotent +
     *  null-safe + menandai `FAILED` kalau exception) sebagai "nudge" jaga-jaga effect
     *  yang diam-diam ke-disable HAL saat route pindah. TIDAK recreate object AudioEffect
     *  di sini — alasan SAMA PERSIS dengan kenapa `retryControlAcquisition()` juga belum
     *  ada pemanggil otomatis (lihat komentar fungsi itu): route audio bisa berpindah
     *  CUKUP SERING dalam pemakaian normal (mis. earbuds TWS reconnect berkali-kali),
     *  recreate object tiap kali berisiko churn CPU/baterai sia-sia tanpa bukti itu
     *  benar-benar perlu. Kalau nudge ringan ini TIDAK cukup dan effect beneran
     *  `CONTROL_LOST`, jalur yang SUDAH ADA (listener di `attachXxx()` →
     *  `ControlRecoveryBanner` UI, Batch 61/62) tetap akan menangkapnya lewat mekanisme
     *  normal — fungsi ini TIDAK menggantikan jalur itu, cuma lapisan tambahan di depan.
     *
     *  Digerbang `isRunning` SENGAJA: kalau user baru saja tekan "Matikan" (effect
     *  sengaja `disabled`, `isRunning=false`), route change TIDAK BOLEH diam-diam
     *  menyalakan ulang effect — itu akan melanggar pilihan eksplisit user (persis
     *  alasan `enableEffects()` juga tidak dipanggil sembarangan tempat lain).
     *
     *  BELUM divalidasi runtime — kandidat pertama dicurigai kalau nanti ada laporan
     *  "kok Logcat gak pernah kecatat pas ganti Bluetooth/headset": kemungkinan device
     *  tertentu tidak fire `AudioDeviceCallback` untuk tipe device tertentu (variasi HAL
     *  vendor, sama kelas masalah dengan capability lain di file ini). */
    private fun onOutputRouteChanged(devices: Array<AudioDeviceInfo>, added: Boolean) {
        val outputDevices = devices.filter { it.isSink }
        if (outputDevices.isEmpty()) return // semua device di batch callback ini INPUT, bukan urusan fungsi ini
        val label = outputDevices.joinToString { describeOutputDeviceType(it.type) }
        val suffix = if (added) "terhubung" else "terputus"
        lastOutputRouteDescription = "$label ($suffix)"
        android.util.Log.i(TAG, "Output route berubah: $label $suffix")
        if (isRunning) {
            enableEffects()
        }
    }

    /** Nama ringkas tipe sink output buat Log/`lastOutputRouteDescription` — HANYA cover
     *  tipe yang relevan skenario audit (speaker, Bluetooth klasik+BLE, wired, USB DAC),
     *  bukan daftar lengkap seluruh `AudioDeviceInfo.TYPE_*` (banyak yang tipe INPUT atau
     *  tidak relevan konteks booster audio ini). Tipe di luar daftar tetap tercatat
     *  (fallback `"device tipe $type"`), bukan hilang diam-diam. */
    private fun describeOutputDeviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker internal"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset kabel"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headphone kabel"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC/device"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        else -> "device tipe $type"
    }

    private fun restoreSavedSettings() {
        setBassStrength(PrefsHelper.getBass(this).toShort())
        setVirtualizerStrength(PrefsHelper.getVirtualizer(this).toShort())
        setLoudnessGain(PrefsHelper.getLoudness(this))

        // Batch 87: SEBELUMNYA baca `equalizer.numberOfBands` + tulis `eq.setBandLevel()`
        // LANGSUNG di sini (duplikat logic dari `setEqualizerBand()` di bawah). Sekarang
        // lewat `getEqualizerBandCount()`/`setEqualizerBand()` publik SUPAYA 1 sumber logic
        // dipakai baik jalur Equalizer asli MAUPUN jalur fallback `DynamicsProcessing` PreEq
        // (Batch 87, lihat `attachDynamicsProcessing()`) — device dengan Equalizer legacy
        // normal 0 perubahan perilaku (persis kode lama, cuma dipindah lewat fungsi publik).
        if (isEqualizerSupported()) {
            try {
                for (band in 0 until getEqualizerBandCount()) {
                    val saved = PrefsHelper.getEqualizerBandLevel(this, band, 0)
                    setEqualizerBand(band.toShort(), saved.toShort())
                }
            } catch (_: Exception) { }
        }
    }

    private fun releaseEffects() {
        bassBoost?.release(); virtualizer?.release()
        equalizer?.release(); loudnessEnhancer?.release()
        dynamicsProcessing?.release()
        // Batch 57: object sudah dilepas total, state HARUS balik UNAVAILABLE — kalau
        // dibiarkan ENABLED/CONTROL_LOST, pembaca state (ke depan: ViewModel/UI) bisa
        // salah kira effect masih hidup padahal Service ini sendiri sudah di-destroy.
        bassState = EffectState.UNAVAILABLE
        virtualizerState = EffectState.UNAVAILABLE
        loudnessState = EffectState.UNAVAILABLE
        equalizerState = EffectState.UNAVAILABLE
        dynamicsState = EffectState.UNAVAILABLE // Batch 84
        equalizerFallbackActive = false // Batch 87
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
        // Batch 84: limiter ikut mati bareng — kalau booster "Matikan", tidak ada lagi
        // sinyal yang di-boost, jadi tidak ada lagi yang perlu di-limit.
        try { dynamicsProcessing?.enabled = false } catch (e: Exception) { android.util.Log.e(TAG, "Gagal disable DynamicsProcessing", e) }
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
        // Batch 84: ikut pola 4 effect di atas — termasuk otomatis kena nudge
        // `onOutputRouteChanged()` (Batch 82/83) karena fungsi itu manggil enableEffects()
        // ini apa adanya, tanpa perubahan apa pun di fungsi itu.
        try { dynamicsProcessing?.enabled = true } catch (e: Exception) {
            dynamicsState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable DynamicsProcessing", e)
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
        // Batch 87: dua rute — Equalizer legacy asli (mayoritas device, kode TIDAK berubah)
        // ATAU fallback PreEq `DynamicsProcessing` (lihat `equalizerFallbackActive`,
        // `attachDynamicsProcessing()`). `levelMb` (satuan lama, milliBel, konsisten
        // `Equalizer.setBandLevel()`) dikonversi -> dB (`EqBand.gain`, satuan resmi API ini,
        // dicek dokumentasi sebelum ditulis) dengan bagi 100 — 1 dB = 100 mB, konversi
        // standar, BUKAN asumsi baru.
        if (equalizerFallbackActive) {
            try {
                val bandIndex = band.toInt()
                val gainDb = levelMb / 100f
                dynamicsProcessing?.setPreEqBandAllChannelsTo(
                    bandIndex, DynamicsProcessing.EqBand(true, FALLBACK_EQ_BANDS_HZ[bandIndex], gainDb)
                )
                fallbackEqGainsMb[bandIndex] = levelMb
            } catch (e: Exception) {
                equalizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set EQ fallback band $band", e)
            }
        } else {
            try { equalizer?.setBandLevel(band, levelMb) } catch (e: Exception) {
                equalizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set Equalizer band $band", e)
            }
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
    // Batch 87: tiap fungsi di bawah sekarang cek `equalizerFallbackActive` dulu — device
    // dengan Equalizer legacy normal (mayoritas, `equalizerFallbackActive == false`) lewat
    // cabang `else`/fallback-default yang PERSIS logic lama, 0 perubahan. Cabang fallback
    // BARU cuma kepakai di device yang SEBELUM batch ini `isEqualizerSupported()`-nya
    // permanen false (roadmap.md Fase 0 #2/#6).
    fun isEqualizerSupported(): Boolean = equalizer != null || equalizerFallbackActive

    fun getEqualizerBandCount(): Int =
        if (equalizerFallbackActive) FALLBACK_EQ_BANDS_HZ.size
        else try { equalizer?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }

    /** [min, max] dalam milliBel. */
    fun getEqualizerLevelRange(): ShortArray =
        if (equalizerFallbackActive) shortArrayOf((-FALLBACK_EQ_RANGE_MB).toShort(), FALLBACK_EQ_RANGE_MB)
        else try { equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500) } catch (_: Exception) { shortArrayOf(-1500, 1500) }

    /** Frekuensi band dalam Hz. Equalizer asli: frekuensi TENGAH (`getCenterFreq()`, API
     *  mengembalikan milliHertz). Fallback (Batch 87): `cutoffFrequency` (frekuensi TERATAS
     *  band itu, lihat komentar `FALLBACK_EQ_BANDS_HZ`) dipakai APA ADANYA sebagai label —
     *  beda semantik dari center-freq asli, TAPI dampaknya cuma ke angka label slider UI,
     *  bukan ke fungsi EQ itu sendiri. */
    fun getEqualizerBandCenterFreqHz(band: Int): Int =
        if (equalizerFallbackActive) FALLBACK_EQ_BANDS_HZ.getOrElse(band) { 0f }.toInt()
        else try { (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000 } catch (_: Exception) { 0 }

    fun getEqualizerBandLevel(band: Int): Short =
        if (equalizerFallbackActive) fallbackEqGainsMb.getOrElse(band) { 0 }
        else try { equalizer?.getBandLevel(band.toShort()) ?: 0 } catch (_: Exception) { 0 }

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
