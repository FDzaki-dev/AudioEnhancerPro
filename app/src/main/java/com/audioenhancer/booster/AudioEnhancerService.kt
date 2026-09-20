package com.audioenhancer.booster

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

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
        // Batch 119 (Sleep timer): minta Service (re)jadwalkan tick timer dari nilai yang
        // SUDAH ditulis ke `PrefsHelper.getSleepTimerEndAt()` (Service = pembaca tunggal).
        const val ACTION_SLEEP_TIMER_SYNC = "com.audioenhancer.booster.SLEEP_TIMER_SYNC"
        // Interval tick maksimum timer tidur; tick terakhir memakai sisa waktu persis (<30 dtk)
        // jadi berhenti tepat waktu, bukan mengikuti kelipatan interval.
        private const val SLEEP_TICK_MAX_MS = 30_000L

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

        // Batch 121 (Fase 8 ROI #4 "Compressor", PROJECT_STATE.md): parameter tetap
        // kompresor MBC 1-band full-range, TIDAK di-expose ke UI granular — SENGAJA 1
        // slider "Amount" 0..100 (lihat setCompressorAmount() di bawah) yang menyetir
        // ratio+threshold+postGain sekaligus, bukan attack/release/knee terpisah,
        // konsisten filosofi "1 kontrol per effect baru" seperti Loudness/Bass/
        // Virtualizer. cutoffFrequency 20000 Hz = band TUNGGAL cover seluruh rentang
        // audible (mbcBandCount=1, tidak ada "band kedua" yang butuh batas). Angka
        // attack/release/knee dipilih dari rentang umum kompresor "program"/vocal-safe
        // — TIDAK ADA API resmi query kurva ideal per device (sama filosofi seperti
        // FALLBACK_EQ_RANGE_MB di atas) — NOT VERIFIED di device fisik, kandidat
        // pertama kalau user lapor kompresi kurang/lewat agresif. noiseGateThreshold
        // sangat rendah + expanderRatio 1:1 SENGAJA menonaktifkan noise-gate/expander
        // bawaan MbcBand (fitur ini murni kompresor, bukan gate) — constructor
        // `DynamicsProcessing.MbcBand` TIDAK punya varian tanpa parameter itu, jadi
        // harus tetap diisi walau tidak dipakai fungsinya.
        private const val COMPRESSOR_BAND_CUTOFF_HZ = 20000f
        private const val COMPRESSOR_ATTACK_MS = 10f
        private const val COMPRESSOR_RELEASE_MS = 150f
        private const val COMPRESSOR_KNEE_DB = 6f
        private const val COMPRESSOR_NOISE_GATE_DB = -90f

        // Batch 122 (Fase 8 ROI #5 "Auto-profile per output device"): 4 bucket kategori
        // output yang DIEKSPOS ke UI (SettingsScreen.kt) buat auto-profile, dipakai SEBAGAI
        // KEY PrefsHelper — JANGAN diubah string-nya tanpa migrasi (akan memutus mapping
        // yang sudah disimpan user). Lihat `routeCategoryOf()` buat mapping dari
        // `AudioDeviceInfo.type` mentah. `TYPE_HDMI`/`TYPE_DOCK`/dll masuk kategori "other"
        // via `routeCategoryOf()` TAPI SENGAJA TIDAK diekspos sebagai pilihan di Settings
        // (jarang relevan buat app audio booster) — `onOutputRouteChanged()` otomatis skip
        // kategori ini (tidak ada `ROUTE_CATEGORY_OTHER` yang bisa di-lookup user).
        const val ROUTE_CATEGORY_SPEAKER = "speaker"
        const val ROUTE_CATEGORY_WIRED = "wired"
        const val ROUTE_CATEGORY_BLUETOOTH = "bluetooth"
        const val ROUTE_CATEGORY_USB = "usb"

        // Batch 120 (Fase 8E, spectrum visualizer, part 1/2 - lihat RESUME POINT):
        // jumlah bar spectrum yang di-ekspos ke UI. Ditaruh di companion (bukan cuma
        // konstanta lokal fungsi capture) supaya Part 2 (UI, belum dikerjakan) bisa baca
        // ukuran array `spectrumLevels` tanpa hard-code angka kedua kalinya di file lain.
        const val SPECTRUM_BAND_COUNT = 24
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

        /** Batch 119 (Sleep timer): Boomly berhenti otomatis setelah [minutes] menit — lewat
         *  jalur `ACTION_STOP` yang SAMA dengan tombol "Matikan" (efek off, watchdog TIDAK
         *  menghidupkan lagi). Hanya bermakna kalau service lagi jalan; UI menonaktifkan
         *  pilihan durasi kalau `isRunning == false`. */
        fun requestSleepTimer(context: android.content.Context, minutes: Int) {
            if (minutes <= 0) return
            PrefsHelper.setSleepTimerEndAt(context, System.currentTimeMillis() + minutes * 60_000L)
            syncSleepTimer(context)
        }

        /** Batch 119: batalkan timer. Cukup nol-kan prefs — tick berikutnya di Service
         *  membaca 0 lalu berhenti sendiri; sinkron langsung kalau service hidup. */
        fun cancelSleepTimer(context: android.content.Context) {
            PrefsHelper.setSleepTimerEndAt(context, 0L)
            if (isRunning) syncSleepTimer(context)
        }

        private fun syncSleepTimer(context: android.content.Context) {
            val intent = Intent(context, AudioEnhancerService::class.java).apply { action = ACTION_SLEEP_TIMER_SYNC }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal sinkron Sleep Timer ke service", e)
            }
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
    // `attachDynamicsProcessing()` soal apa yang dipasang & kenapa. Batch 121: objek
    // YANG SAMA ini sekarang JUGA membawa 1 band MBC (kompresor user-adjustable, lihat
    // `setCompressorAmount()`) — bukan effect/instance terpisah.
    private var dynamicsProcessing: DynamicsProcessing? = null

    // Batch 120 (Fase 8E, spectrum visualizer, part 1/2 - lihat RESUME POINT): BEDA dari
    // 4 effect di atas — `Visualizer` cuma OBSERVER pasif (baca sinyal, tidak memproses/
    // mengubah audio), jadi TIDAK ikut chain DSP session-0. Butuh `RECORD_AUDIO` runtime
    // permission (terverifikasi dokumentasi resmi developer.android.com Batch 119) karena
    // ini menempel session 0 (mixer global), BUKAN sesi App sendiri — beda dari kasus
    // Visualizer di app pemutar musik biasa yang boleh tempel ke sesi sendiri tanpa izin
    // itu. Callback capture jalan di thread yang MEMBUAT object ini (dokumentasi resmi:
    // Looper thread pembuat, atau thread baru kalau tidak ada Looper) — di sini itu main
    // thread Service (Service dijamin Android selalu punya Looper). SENGAJA tidak dibuat
    // di HandlerThread terpisah: API `setDataCaptureListener()` TIDAK punya parameter
    // pilih-thread (SEMPAT salah asumsi ada overload +`Handler`, dicek ulang ke
    // dokumentasi resmi — tidak ada), dan komputasi per-frame (`computeSpectrumBands()`,
    // ~500 sample, capture rate rendah ~10 Hz karena `/2` di bawah) jauh di bawah
    // ambang "komputasi berat" — non-blocking, aman di main thread.
    private var visualizer: Visualizer? = null

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
    // Batch 120: UNAVAILABLE di sini overload 2 arti (beda dari 4 EffectState di atas
    // yang UNAVAILABLE-nya murni "chipset tidak support") — bisa berarti "izin
    // RECORD_AUDIO belum diberikan" ATAU "Visualizer gagal/tidak didukung device".
    // Part 2 (UI) HARUS cek `hasRecordAudioPermission()` terpisah kalau mau bedakan
    // 2 kasus itu buat teks yang tepat ke user (minta izin vs "tidak didukung device").
    @Volatile var visualizerState: EffectState = EffectState.UNAVAILABLE; private set
    @Volatile var spectrumLevels: FloatArray = FloatArray(SPECTRUM_BAND_COUNT); private set

    // Batch 82 (roadmap.md Fase 0 #3, "Output routing awareness"): deskripsi ringkas
    // sink output TERAKHIR yang terdeteksi (mis. "Bluetooth A2DP (terhubung)") — diisi
    // `onOutputRouteChanged()` di bawah. @Volatile: sama alasan seperti state effect di
    // atas, ditulis dari callback sistem (thread TIDAK dijamin sama dengan pembaca ke
    // depan kalau ViewModel/UI mulai poll field ini). SENGAJA belum dikonsumsi
    // ViewModel/UI batch ini (pola sama seperti Batch 60: Service-layer dulu, Log
    // diagnostik cukup untuk batch ini, surface ke UI kalau ada kebutuhan/laporan nyata
    // dari device — kandidat kuat buat roadmap.md Fase 0 #9 "UI/error-state lanjutan").
    @Volatile var lastOutputRouteDescription: String? = null; private set

    /** Batch 123 (hotfix regresi speaker internal, lapor user setelah Batch 122): snapshot
     *  Bass/Virtualizer/Loudness/EQ tepat SEBELUM preset auto-profile PERTAMA kali
     *  diterapkan dalam 1 "sesi keterhubungan" (di-set null lagi setelah restore). Root
     *  cause bug: `onOutputRouteChanged()` versi lama HANYA menangani `added=true` — saat
     *  device eksternal (wired/Bluetooth/USB) LEPAS, nilai Bass/Virtualizer/Loudness/EQ
     *  yang sudah ditimpa custom preset kategori device itu TIDAK PERNAH direvert, jadi
     *  nempel ke speaker internal walau speaker sama sekali TIDAK diatur ke preset
     *  tersebut. Dipulihkan via `restoreAutoProfileBaseline()` saat route balik ke
     *  kategori TANPA preset ter-assign (termasuk speaker internal). `null` = belum ada
     *  snapshot aktif (setting SUDAH sinkron dengan manual user, tidak ada yang perlu
     *  direstore). @Volatile: ditulis dari callback sistem, sama alasan seperti state
     *  effect lain di file ini. */
    @Volatile private var autoProfileBaseline: AutoProfileBaseline? = null

    private data class AutoProfileBaseline(
        val bass: Short,
        val virtualizer: Short,
        val loudness: Float,
        val eqBands: List<Short>
    )

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

    // Batch 119 (Fase 8 item B, Sleep timer, bagian 1 = auto-stop; fade-out & Scheduler
    // BELUM). Timer = Handler main-thread yang membaca waktu berakhir ABSOLUT dari prefs
    // tiap tick (maks 30 dtk), bukan menghitung mundur di memori — jadi sisa waktu selalu
    // benar walau tick tertunda, dan Service yang di-restart OS bisa melanjutkan. Habis
    // waktu -> `requestStop()` (jalur ACTION_STOP normal, tidak ada logika stop kedua).
    // KETERBATASAN yang disengaja/dicatat: Handler pakai uptimeMillis (tidak maju saat CPU
    // deep-sleep) dan bukan alarm exact (butuh izin exact-alarm) — kalau CPU tidur, tick
    // tertunda sampai CPU bangun lagi; timer hilang kalau Service dibunuh OS lalu waktunya
    // sudah lewat saat restart (dibersihkan diam-diam, TIDAK memaksa stop).
    private val sleepHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepTick = object : Runnable {
        override fun run() {
            val endAt = PrefsHelper.getSleepTimerEndAt(this@AudioEnhancerService)
            if (endAt <= 0L) return // dibatalkan / sudah dibersihkan
            val remaining = endAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                AudioEnhancerService.requestStop(this@AudioEnhancerService)
                return
            }
            sleepHandler.postDelayed(this, remaining.coerceAtMost(SLEEP_TICK_MAX_MS))
        }
    }

    /** Idempotent: batalkan tick lama, jadwalkan ulang kalau ada timer yang masih di masa depan. */
    private fun scheduleSleepTick() {
        sleepHandler.removeCallbacks(sleepTick)
        val endAt = PrefsHelper.getSleepTimerEndAt(this)
        if (endAt > System.currentTimeMillis()) {
            sleepHandler.post(sleepTick)
        } else if (endAt > 0L) {
            // Kedaluwarsa saat service mati — bersihkan saja, JANGAN stop (hindari service
            // yang baru dinyalakan user langsung mati gara-gara sisa timer lama).
            PrefsHelper.setSleepTimerEndAt(this, 0L)
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
        // Batch 119: sinkron Sleep timer — TIDAK menyentuh foreground/efek. Kalau service
        // tidak aktif, timer tak bermakna: bersihkan & jangan biarkan instance idle.
        if (intent?.action == ACTION_SLEEP_TIMER_SYNC) {
            if (isRunning) {
                scheduleSleepTick()
                return START_STICKY
            }
            PrefsHelper.setSleepTimerEndAt(this, 0L)
            stopSelf()
            return START_NOT_STICKY
        }
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
            // Batch 119: Sleep timer ikut dibersihkan di jalur berhenti ini (dipakai tombol
            // Matikan, QS Tile, DAN habisnya timer itu sendiri).
            sleepHandler.removeCallbacks(sleepTick)
            PrefsHelper.setSleepTimerEndAt(this, 0L)
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
        // Batch 119: lanjutkan Sleep timer kalau masih ada (restart OS / start ulang manual).
        scheduleSleepTick()
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
        sleepHandler.removeCallbacks(sleepTick) // Batch 119 (prefs sengaja TIDAK dibersihkan)
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
        // Batch 120: dipanggil PALING TERAKHIR — Visualizer cuma observer pasif, urutan
        // relatif ke 5 effect di atas tidak relevan (tidak ikut chain DSP apa pun).
        attachVisualizer()

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
     *  BERISI stage limiter (0 pre-EQ band kecuali fallback EQ Batch 87, 0 post-EQ band,
     *  `limiterInUse=true`), fungsi UTAMA: jadi "ceiling" pengaman terakhir
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
     *  Batch 121 (Fase 8 ROI #4 "Compressor"): objek `DynamicsProcessing` YANG SAMA di
     *  atas SEKARANG JUGA membawa 1 band MBC (`mbcInUse=true, mbcBandCount=1`,
     *  full-range, `cutoffFrequency` 20000 Hz) — kompresor yang BENERAN bisa diatur
     *  user (beda dari limiter di atas yang hardcoded/tidak ada slider), pelengkap
     *  `LoudnessEnhancer`. Band ini dipasang `enabled=false` di sini (netral) — nilai
     *  asli diterapkan belakangan lewat `restoreSavedSettings()` -> `setCompressorAmount()`
     *  (fungsi publik di bawah, dipanggil UI), pola SAMA seperti PreEq fallback di atas.
     *  Konstanta tuning ada di companion (`COMPRESSOR_*`).
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
                true, 1, // Batch 121: 1 band MBC full-range = kompresor user-adjustable baru (lihat setCompressorAmount()); SEBELUMNYA "false, 0" (di luar scope Batch 84)
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
                // Batch 121: band kompresor mulai NONAKTIF (enabled=false, netral) — nilai
                // asli (kalau ada) diterapkan belakangan lewat restoreSavedSettings() ->
                // setCompressorAmount(), SAMA pola seperti PreEq fallback di atas (jangan
                // duplikat logic restore di sini).
                setMbcBandAllChannelsTo(
                    0,
                    DynamicsProcessing.MbcBand(
                        /* enabled            = */ false,
                        /* cutoffFrequency    = */ COMPRESSOR_BAND_CUTOFF_HZ,
                        /* attackTime         = */ COMPRESSOR_ATTACK_MS,
                        /* releaseTime        = */ COMPRESSOR_RELEASE_MS,
                        /* ratio              = */ 1f,
                        /* threshold          = */ 0f,
                        /* kneeWidth          = */ COMPRESSOR_KNEE_DB,
                        /* noiseGateThreshold = */ COMPRESSOR_NOISE_GATE_DB,
                        /* expanderRatio      = */ 1f,
                        /* preGain            = */ 0f,
                        /* postGain           = */ 0f
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
     *  vendor, sama kelas masalah dengan capability lain di file ini).
     *
     *  Batch 123 (hotfix regresi speaker internal): `added=false` (device lepas) SEKARANG
     *  ikut ditangani untuk auto-profile — TAPI HANYA kalau setelah lepas TIDAK ADA lagi
     *  device output eksternal lain yang masih nyambung (dicek via `getDevices()`), jadi
     *  route baru bisa dipastikan balik ke speaker internal. Kalau MASIH ada device
     *  eksternal lain nyambung (mis. wired+Bluetooth nyambung bareng, lalu salah satu
     *  lepas), fungsi ini SENGAJA skip (sama seperti perilaku lama) — tidak ada cara ANDAL
     *  tahu device MANA yang jadi aktif berikutnya, menebak salah lebih berisiko daripada
     *  diam. Kategori (baik dari device yang baru nyambung MAUPUN speaker yang dipastikan
     *  aktif) tanpa preset ter-assign SEKARANG memicu `restoreAutoProfileBaseline()` kalau
     *  ada snapshot aktif — mencegah setting preset kategori SEBELUMNYA "nempel" ke
     *  kategori yang tidak diatur (root cause laporan user: speaker internal ikut pakai
     *  preset Kustom padahal tidak pernah diatur ke situ). */
    private fun onOutputRouteChanged(devices: Array<AudioDeviceInfo>, added: Boolean) {
        val outputDevices = devices.filter { it.isSink }
        if (outputDevices.isEmpty()) return // semua device di batch callback ini INPUT, bukan urusan fungsi ini
        val label = outputDevices.joinToString { describeOutputDeviceType(it.type) }
        val suffix = if (added) "terhubung" else "terputus"
        lastOutputRouteDescription = "$label ($suffix)"
        android.util.Log.i(TAG, "Output route berubah: $label $suffix")
        if (isRunning) {
            enableEffects()
            // Batch 122/123 (Fase 8 ROI #5 "Auto-profile per output device"): hanya kalau
            // toggle "Auto-Profil per Output" aktif (opt-in, default MATI — lihat
            // PrefsHelper.getAutoProfileEnabled()). Digerbang `isRunning` SAMA PERSIS
            // alasan `enableEffects()` di atas: user matiin Boomly = jangan diam-diam ubah
            // apa pun.
            if (PrefsHelper.getAutoProfileEnabled(this)) {
                val category: String? = if (added) {
                    routeCategoryOf(outputDevices.first().type)
                } else if (hasNoExternalOutputDeviceLeft()) {
                    ROUTE_CATEGORY_SPEAKER // dipastikan balik ke speaker, lihat doc di atas
                } else {
                    null // ambigu (device eksternal lain masih nyambung) -> skip, jangan nebak
                }
                if (category != null) {
                    val presetName = PrefsHelper.getAutoProfileForRoute(this, category)
                    if (presetName != null) {
                        if (applyCustomPresetByName(presetName)) {
                            android.util.Log.i(TAG, "Auto-profile: route=$category -> preset '$presetName'")
                        }
                    } else if (autoProfileBaseline != null) {
                        // Kategori aktif TIDAK diatur ("Tidak ada") TAPI ada snapshot manual
                        // aktif dari route sebelumnya -> pulihkan, jangan biarkan nempel.
                        restoreAutoProfileBaseline()
                        android.util.Log.i(TAG, "Auto-profile: route=$category tidak diatur -> restore manual")
                    }
                }
            }
        }
    }

    /** Batch 123: true kalau, setelah 1 device output LEPAS, TIDAK ADA lagi device output
     *  eksternal lain (Bluetooth/wired/USB/HDMI/dock/dll) yang masih terdaftar tersambung
     *  — artinya route audio bisa dipastikan balik ke speaker (atau earpiece) internal.
     *  Query `getDevices()` LANGSUNG ke `AudioManager` (bukan cuma dari payload callback
     *  `removedDevices`) supaya dapat state TERKINI, bukan snapshot device yang baru lepas
     *  saja. Aman no-op (return false = jangan asumsikan speaker) kalau `audioManager`
     *  null (belum sempat di-init). */
    private fun hasNoExternalOutputDeviceLeft(): Boolean {
        val manager = audioManager ?: return false
        val remaining = try {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (e: Exception) {
            return false
        }
        return remaining.none {
            it.isSink && it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
                it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
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

    /** Batch 122 (Fase 8 ROI #5 "Auto-profile per output device"): reduksi tipe device
     *  MENTAH (banyak varian, lihat `describeOutputDeviceType()` di atas) jadi 4 bucket
     *  stabil buat KEY mapping preset (`ROUTE_CATEGORY_*` companion) — user atur "kalau
     *  Bluetooth nyambung, pakai preset X" di Settings, BUKAN per-tipe-device individual
     *  (terlalu granular buat berguna, mis. Bluetooth A2DP vs SCO vs BLE headset SEMUA
     *  tetap "earbuds/speaker Bluetooth" dari sudut pandang user awam). */
    private fun routeCategoryOf(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> ROUTE_CATEGORY_SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> ROUTE_CATEGORY_WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> ROUTE_CATEGORY_BLUETOOTH
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> ROUTE_CATEGORY_USB
        else -> "other" // HDMI/Dock/dll — SENGAJA tidak ada konstanta publik, lihat komentar companion object
    }

    /** Batch 120 (Fase 8E, part 1/2 - lihat RESUME POINT): pasang `Visualizer` ke session
     *  0 buat capture data spectrum. Gagal-aman kalau izin `RECORD_AUDIO` belum ada —
     *  TIDAK melempar exception ke pemanggil, cuma set `visualizerState = UNAVAILABLE`
     *  (Service tetap hidup normal tanpa spectrum, 4 effect audio lain tidak terpengaruh
     *  sama sekali). Aman dipanggil ulang (dari `retryVisualizerPermission()`) — no-op
     *  kalau sudah ENABLED, coba pasang ulang dari nol kalau belum. */
    private fun attachVisualizer() {
        if (visualizerState == EffectState.ENABLED) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            visualizerState = EffectState.UNAVAILABLE
            return
        }
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange().getOrElse(1) { 1024 }
                // Batch 120: setengah max capture rate SENGAJA (bukan max) — konsisten
                // dengan filosofi hemat baterai project ini (lihat komentar `onCreate()`
                // soal wakelock), animasi bar tetap halus tanpa perlu rate tertinggi.
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        // Tidak dipakai — spectrum bar butuh domain frekuensi (FFT), bukan waveform mentah.
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft ?: return
                        spectrumLevels = computeSpectrumBands(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            visualizerState = EffectState.ENABLED
        } catch (e: Exception) {
            try { visualizer?.release() } catch (_: Exception) { }
            visualizer = null
            visualizerState = EffectState.FAILED
            android.util.Log.e(TAG, "Gagal attach Visualizer (spectrum)", e)
        }
    }

    /** Batch 120: reduksi array FFT mentah (format packed resmi Android — lihat dokumentasi
     *  `Visualizer.getFft()`: fft[0]=Re(0), fft[1]=Re(N/2), lalu fft[2k]/fft[2k+1]=Re(k)/Im(k)
     *  buat k=1..N/2-1) jadi [SPECTRUM_BAND_COUNT] magnitude 0f..1f. Grouping bin PAKAI skala
     *  kuadratik (bukan linear) supaya band rendah/bass — yang jumlah bin FFT-nya jauh lebih
     *  sedikit dari treble — tidak keteken visual jadi cuma 1-2 bar pertama. Normalisasi
     *  `/90f` ANGKA PERKIRAAN (byte magnitude teoretis maks sekitar 180 kalau Re=Im=127) —
     *  BELUM divalidasi audio nyata di device fisik, kandidat pertama kalau user lapor bar
     *  "terlalu pendek"/"selalu mentok atas" begitu Part 2 (UI) selesai. */
    private fun computeSpectrumBands(fft: ByteArray): FloatArray {
        val n = fft.size
        val bands = FloatArray(SPECTRUM_BAND_COUNT)
        if (n < 4) return bands
        val magCount = n / 2 + 1
        val mags = FloatArray(magCount)
        mags[0] = kotlin.math.abs(fft[0].toInt()).toFloat()
        mags[magCount - 1] = kotlin.math.abs(fft[1].toInt()).toFloat()
        var k = 1
        var idx = 2
        while (idx + 1 < n && k < magCount - 1) {
            val re = fft[idx].toInt()
            val im = fft[idx + 1].toInt()
            mags[k] = kotlin.math.sqrt((re * re + im * im).toFloat())
            idx += 2
            k += 1
        }
        for (b in 0 until SPECTRUM_BAND_COUNT) {
            val startFrac = b.toFloat() / SPECTRUM_BAND_COUNT
            val endFrac = (b + 1).toFloat() / SPECTRUM_BAND_COUNT
            val startBin = (startFrac * startFrac * (magCount - 1)).toInt().coerceIn(0, magCount - 2)
            val endBin = (endFrac * endFrac * (magCount - 1)).toInt().coerceIn(startBin + 1, magCount - 1)
            var sum = 0f
            for (i in startBin..endBin) sum += mags[i]
            val avg = sum / (endBin - startBin + 1)
            bands[b] = (avg / 90f).coerceIn(0f, 1f)
        }
        return bands
    }

    private fun restoreSavedSettings() {
        setBassStrength(PrefsHelper.getBass(this).toShort())
        setVirtualizerStrength(PrefsHelper.getVirtualizer(this).toShort())
        setLoudnessGain(PrefsHelper.getLoudness(this))
        // Batch 121: no-op aman kalau dynamicsProcessing null/API<28 (setCompressorAmount()
        // sudah dibungkus try-catch generic, sama pola seperti 3 baris di atas).
        setCompressorAmount(PrefsHelper.getCompressorAmount(this))

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
        try { visualizer?.release() } catch (e: Exception) { android.util.Log.e(TAG, "Gagal release Visualizer", e) }
        visualizer = null
        // Batch 57: object sudah dilepas total, state HARUS balik UNAVAILABLE — kalau
        // dibiarkan ENABLED/CONTROL_LOST, pembaca state (ke depan: ViewModel/UI) bisa
        // salah kira effect masih hidup padahal Service ini sendiri sudah di-destroy.
        bassState = EffectState.UNAVAILABLE
        virtualizerState = EffectState.UNAVAILABLE
        loudnessState = EffectState.UNAVAILABLE
        equalizerState = EffectState.UNAVAILABLE
        dynamicsState = EffectState.UNAVAILABLE // Batch 84
        visualizerState = EffectState.UNAVAILABLE // Batch 120
        spectrumLevels = FloatArray(SPECTRUM_BAND_COUNT) // Batch 120
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
        // Batch 120: spectrum ikut berhenti saat "Matikan" — tidak ada gunanya capture
        // audio kalau booster sendiri lagi off, murni hemat CPU/baterai.
        try { visualizer?.enabled = false } catch (e: Exception) { android.util.Log.e(TAG, "Gagal disable Visualizer", e) }
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
        try { visualizer?.enabled = true } catch (e: Exception) {
            visualizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable Visualizer", e)
        }
    }

    // ---- Kontrol dari UI ----
    fun isBassSupported(): Boolean = bassBoost != null
    fun isVirtualizerSupported(): Boolean = virtualizer != null
    fun isLoudnessSupported(): Boolean = loudnessEnhancer != null

    /** Batch 121 (Fase 8 ROI #4 "Compressor"): band MBC kompresor hidup di objek
     *  `dynamicsProcessing` YANG SAMA dengan master limiter — availability-nya makanya
     *  ikut objek itu (device API<28/gagal construct = sama-sama tidak ada), BUKAN
     *  effect terpisah seperti 3 fungsi `isXxxSupported()` di atas. */
    fun isCompressorSupported(): Boolean = dynamicsProcessing != null

    /** State kontrol kompresor ikut 1:1 `dynamicsState` (lihat `isCompressorSupported()`
     *  di atas soal kenapa) — TIDAK ada `EffectState` terpisah untuk band ini, pola sama
     *  seperti `equalizerFallbackActive` berbagi `dynamicsState` di jalur fallback EQ. */
    val compressorState: EffectState get() = dynamicsState

    /** Batch 120 (Fase 8E, part 1/2): dipakai UI (Part 2, belum dikerjakan) buat tahu
     *  apakah perlu munculkan tombol/dialog minta izin RECORD_AUDIO, TERPISAH dari
     *  `visualizerState` (yang UNAVAILABLE-nya overload 2 arti, lihat komentar field). */
    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Dipanggil UI (Part 2) SETELAH dialog izin RECORD_AUDIO dijawab user (granted
     *  maupun ditolak) — no-op aman kalau dipanggil saat sudah ENABLED atau saat izin
     *  ternyata masih belum granted (balik ke UNAVAILABLE apa adanya, tidak crash). */
    fun retryVisualizerPermission() {
        attachVisualizer()
    }

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

    /** Batch 121 (Fase 8 ROI #4 "Compressor"): 1 slider "Amount" 0..100 menyetir band
     *  MBC full-range di `dynamicsProcessing` (lihat `attachDynamicsProcessing()` &
     *  konstanta `COMPRESSOR_*` di companion). [amount] 0 = band di-nonaktifkan (bypass
     *  total, 0 beda audible dari sebelum fitur ini ada); >0 menaikkan ratio + menurunkan
     *  threshold BERSAMAAN, `postGain` makeup ikut naik supaya loudness tidak terasa
     *  drop drastis saat kompresi aktif. Kurva 0..100 di bawah PERKIRAAN (tidak ada API
     *  resmi query "kurva ideal" per device) — NOT VERIFIED di device fisik, kandidat
     *  pertama kalau user lapor kompresi terlalu halus/agresif. `dynamicsProcessing`
     *  null (API<28 atau gagal construct) = no-op aman, sama pola seperti `setLoudnessGain()`
     *  di atas — setting TETAP disimpan ke `PrefsHelper` tanpa syarat (Batch 57, audit Gap
     *  #14: user tidak boleh kehilangan preferensi slider walau apply gagal). */
    fun setCompressorAmount(amount: Int) {
        val clamped = amount.coerceIn(0, 100)
        val fraction = clamped / 100f
        try {
            dynamicsProcessing?.setMbcBandAllChannelsTo(
                0,
                DynamicsProcessing.MbcBand(
                    /* enabled            = */ clamped > 0,
                    /* cutoffFrequency    = */ COMPRESSOR_BAND_CUTOFF_HZ,
                    /* attackTime         = */ COMPRESSOR_ATTACK_MS,
                    /* releaseTime        = */ COMPRESSOR_RELEASE_MS,
                    /* ratio              = */ 1f + fraction * 5f,   // 1:1 (off) .. 6:1 (max)
                    /* threshold          = */ -1f - fraction * 23f, // -1 dB (off) .. -24 dB (max)
                    /* kneeWidth          = */ COMPRESSOR_KNEE_DB,
                    /* noiseGateThreshold = */ COMPRESSOR_NOISE_GATE_DB,
                    /* expanderRatio      = */ 1f,
                    /* preGain            = */ 0f,
                    /* postGain           = */ fraction * 6f         // 0 dB (off) .. +6 dB makeup (max)
                )
            )
        } catch (e: Exception) {
            dynamicsState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set Compressor MBC band", e)
        }
        PrefsHelper.setCompressorAmount(this, clamped)
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

    /** Batch 122 (Fase 8 ROI #5 "Auto-profile per output device"): terapkan 1
     *  `PrefsHelper.CustomPreset` LANGSUNG dari Service (BUKAN UI) — dipanggil
     *  `onOutputRouteChanged()` di atas, tapi ditulis reusable buat calon caller
     *  otomatis lain nanti (mis. Scheduler jam/event, roadmap Fase 8 B berikutnya).
     *  Reuse SEMUA setter publik yang SUDAH ADA (`setBassStrength`/
     *  `setVirtualizerStrength`/`setLoudnessGain`/`setEqualizerBand`) — 0 logic baru
     *  buat "cara apply", cuma orkestrasi lookup by name. `eqBands.isEmpty()` = preset
     *  lama TIDAK sentuh EQ SAMA SEKALI (pola HARUS SAMA seperti `applyCustomPreset()`
     *  lokal di `BoosterScreen.kt` — kalau itu berubah, cek fungsi ini juga).
     *  `PrefsHelper.setActivePreset()` ikut dipanggil (BUKAN preset built-in) — SAMA
     *  seperti UI, biar chip preset yang benar ke-highlight kalau user buka app.
     *
     *  KETERBATASAN JUJUR (Tunnel Vision — di luar scope batch ini): kalau app UI
     *  SEDANG TERBUKA pas ini terpanggil, slider Bass/Virtualizer/Loudness/EQ di layar
     *  TIDAK live-refresh ke nilai baru (arsitektur slider `BoosterScreen.kt` baca
     *  `initial*` SEKALI saat composition, bukan polling nilai efek real-time) — user
     *  harus tutup-buka app buat lihat slider ke-update. Suara/efek ITU SENDIRI tetap
     *  benar berubah real-time; ini MURNI keterbatasan tampilan. Extend polling
     *  `BoosterViewModel` buat live-refresh slider = kandidat batch berikutnya kalau
     *  user komplain soal ini.
     *
     *  Return `false` kalau preset [name] tidak ditemukan (no-op aman — mis. preset
     *  sudah dihapus user tapi masih ter-assign di `PrefsHelper.getAutoProfileForRoute`,
     *  lihat komentar di sana). */
    fun applyCustomPresetByName(name: String): Boolean {
        val preset = PrefsHelper.getCustomPresets(this).firstOrNull { it.name == name } ?: return false
        captureAutoProfileBaselineIfNeeded()
        setBassStrength(preset.bass.toInt().toShort())
        setVirtualizerStrength(preset.virtualizer.toInt().toShort())
        setLoudnessGain(preset.loudness)
        if (preset.eqBands.isNotEmpty()) {
            preset.eqBands.forEachIndexed { index, mb -> setEqualizerBand(index.toShort(), mb.toShort()) }
        }
        PrefsHelper.setActivePreset(this, preset.name)
        return true
    }

    /** Batch 123 (hotfix regresi speaker internal — lihat doc `autoProfileBaseline` di
     *  atas): snapshot nilai Bass/Virtualizer/Loudness/EQ SAAT INI (dari `PrefsHelper`,
     *  sumber kebenaran yang sama dipakai slider manual) HANYA kalau belum ada snapshot
     *  aktif. No-op kalau `autoProfileBaseline` sudah terisi — mencegah snapshot kedua
     *  menimpa nilai manual asli dengan nilai preset kategori SEBELUMNYA (mis. Bluetooth
     *  nyambung dulu baru USB, baseline HARUS tetap nilai manual dari sebelum Bluetooth,
     *  bukan nilai preset Bluetooth). */
    private fun captureAutoProfileBaselineIfNeeded() {
        if (autoProfileBaseline != null) return
        val bandCount = getEqualizerBandCount()
        autoProfileBaseline = AutoProfileBaseline(
            bass = PrefsHelper.getBass(this).toShort(),
            virtualizer = PrefsHelper.getVirtualizer(this).toShort(),
            loudness = PrefsHelper.getLoudness(this),
            eqBands = if (bandCount > 0) (0 until bandCount).map { getEqualizerBandLevel(it) } else emptyList()
        )
    }

    /** Batch 123: kebalikan `captureAutoProfileBaselineIfNeeded()` — pulihkan Bass/
     *  Virtualizer/Loudness/EQ ke nilai manual user SEBELUM preset auto-profile pertama
     *  kali diterapkan, lalu kosongkan snapshot (`autoProfileBaseline = null`) supaya
     *  siklus connect/disconnect berikutnya capture snapshot BARU yang benar. Dipanggil
     *  dari `onOutputRouteChanged()` saat route balik ke kategori tanpa preset ter-assign.
     *  `PrefsHelper.setActivePreset(null)` ikut dipanggil — balik ke manual = tidak ada
     *  chip preset custom yang seharusnya ter-highlight (preset built-in di BoosterScreen.kt
     *  tidak dipengaruhi field ini). No-op aman kalau tidak ada snapshot. */
    private fun restoreAutoProfileBaseline() {
        val baseline = autoProfileBaseline ?: return
        setBassStrength(baseline.bass)
        setVirtualizerStrength(baseline.virtualizer)
        setLoudnessGain(baseline.loudness)
        baseline.eqBands.forEachIndexed { index, mb -> setEqualizerBand(index.toShort(), mb) }
        PrefsHelper.setActivePreset(this, null)
        autoProfileBaseline = null
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
