package com.audioenhancer.booster

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
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
        // Batch 124 (hotfix URGENT, laporan user): app kehilangan kendali/pengaruh ke
        // output Audio saat di-kill, cuma bisa nyala lagi via widget/QS Tile/buka app
        // manual. Root cause TERVERIFIKASI ke dokumentasi resmi developer.android.com/
        // about/versions/12/foreground-services: sejak Android 12 (minSdk project ini
        // SELALU 31+), app DILARANG start foreground service dari context background
        // KECUALI masuk daftar exemption resmi. `ServiceWatchdogWorker` (CoroutineWorker,
        // jalan background) TIDAK masuk exemption itu (beda dari BOOT_COMPLETED/
        // MY_PACKAGE_REPLACED broadcast di BootReceiver yang MEMANG exempted, atau tap
        // widget/QS Tile yang MEMANG exempted kategori "user interacts with a UI element
        // related to your app" - INILAH SEBAB widget/QS Tile "selalu berhasil" sementara
        // watchdog diam-diam gagal kalau battery optimization belum di-exempt user).
        // requestStart() dari watchdog SEBELUMNYA 0 try-catch -> ForegroundServiceStart-
        // NotAllowedException lolos diam-diam, effect TIDAK PERNAH balik sampai user
        // ketemu widget/tile/app sendiri. Fix: notifikasi biasa (BUKAN foreground, jadi
        // BOLEH dipost dari background context apa pun - cuma butuh POST_NOTIFICATIONS
        // yang sudah ada) dengan `PendingIntent.getForegroundService()` - tap notifikasi
        // ini SENDIRI masuk exemption resmi ("the user performs an action on a UI element
        // ... notification"), jadi restart via tap notifikasi ini DIJAMIN tidak kena
        // exception yang sama. 0 permission baru, 0 perubahan arsitektur.
        private const val NOTIF_ID_RECOVERY = 1002
        // Channel TERPISAH dari CHANNEL_ID (IMPORTANCE_LOW, dipakai notifikasi ongoing
        // "Boomly aktif") - notifikasi recovery ini BUTUH tindakan user (bukan cuma info
        // status pasif), IMPORTANCE_HIGH (heads-up+bunyi) supaya kelihatan walau layar
        // mati/notif shade tertutup, sesuai tujuan hotfix ini (jangan sampai user gak
        // sadar butuh tap). User tetap bisa silent-kan channel ini terpisah dari channel
        // ongoing kalau mau, tanpa saling ganggu (best practice Android multi-channel).
        private const val CHANNEL_ID_RECOVERY = "audio_booster_recovery_channel"
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

        // Batch 121 (Fase 8 ROI #4 "Compressor", PROJECT_STATE.md): kompresor user-adjustable di
        // objek `DynamicsProcessing` yang sama dgn limiter; TIDAK di-expose ke UI granular —
        // SENGAJA 1 slider "Amount" 0..100 (lihat setCompressorAmount() di bawah) yang menyetir
        // ratio+threshold+postGain+attack+release sekaligus, konsisten filosofi "1 kontrol per
        // effect baru" seperti Loudness/Bass/Virtualizer. noiseGateThreshold sangat rendah +
        // expanderRatio 1:1 SENGAJA menonaktifkan noise-gate/expander bawaan MbcBand (fitur ini
        // murni kompresor, bukan gate) — constructor `DynamicsProcessing.MbcBand` TIDAK punya
        // varian tanpa parameter itu, jadi harus tetap diisi walau tidak dipakai fungsinya.
        // Batch 208 (request user: vokal kalah dari backsound noisy): kompresor jadi 3 band
        // (<=250 Hz / 250-5000 Hz = vokal / >5000 Hz). Angka per-band + kurva interpolasi 0..100
        // ada di `CompressorCurve` (bawah file, fungsi murni + tes). Knee tetap sama di semua band (konstanta
        // di bawah). Batch 210: noise-gate/expander band bass & treble + presence vokal (post-EQ) juga dari `CompressorCurve`.
        private const val COMPRESSOR_KNEE_DB = 6f

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

        /** Batch 154 (Fase 9 M5): patokan `SystemClock.elapsedRealtime()` awal sesi aktif sekarang
         *  (0 = tidak ada sesi). Tulis hanya di main thread (onStartCommand/onDestroy), dibaca UI
         *  lewat [currentSessionMs]. elapsedRealtime kebal ubah jam sistem. */
        @Volatile
        private var sessionStartElapsedMs = 0L

        /** Lama sesi aktif berjalan (ms); 0 kalau Service tidak sedang aktif. */
        fun currentSessionMs(): Long {
            val start = sessionStartElapsedMs
            return if (isRunning && start > 0L) SystemClock.elapsedRealtime() - start else 0L
        }

        /** Dipanggil UI "Reset statistik": mulai ulang jam sesi berjalan supaya waktu sebelum reset
         *  tidak ikut tertambah saat sesi berakhir. No-op kalau Service tidak aktif. */
        fun restartSessionClock() {
            if (isRunning && sessionStartElapsedMs > 0L) sessionStartElapsedMs = SystemClock.elapsedRealtime()
        }

        /** Batch 171 (port LagFix v133, perintah user): menggeser app dari Recents = `remove-task`, lalu modul
         *  OEM (Transsion `TranManualCleanMgr`, terbukti di log LagFix) mengirim SIGKILL ke proses. Selama
         *  [isRunning] true, task app disembunyikan dari Recents lewat `AppTask.setExcludeFromRecents` (tak
         *  ada kartu yang bisa digeser); mati -> dikembalikan. Dipanggil dari transisi start/stop Service
         *  (semua jalur: app, tile, widget, shortcut, notifikasi, boot, sleep timer) dan dari
         *  `MainActivity.onResume` (task baru tidak mewarisi flag). 1 panggilan binder, tanpa timer/loop.
         *  Efektivitas di device BELUM terbukti; hasil tiap penerapan dicatat apa adanya di logcat. */
        fun syncExcludeFromRecents(context: android.content.Context) {
            try {
                val exclude = isRunning
                val tasks = context.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty()
                tasks.forEach { it.setExcludeFromRecents(exclude) }
                android.util.Log.i(TAG, "LIFECYCLE recentsExclusion exclude=$exclude appTasks=${tasks.size}")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Sinkron excludeFromRecents gagal", e)
            }
        }

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

        /** Batch 124 hotfix: dipanggil `ServiceWatchdogWorker` HANYA kalau `requestStart()`
         *  di atas gagal dilempar sistem (background start restriction, lihat komentar
         *  `NOTIF_ID_RECOVERY`). Notifikasi BIASA (bukan foreground) - aman dipost dari
         *  context background mana pun. `PendingIntent.getForegroundService()` bikin tap
         *  notifikasi ini restart Service via jalur yang OFFICIALLY exempted (tap user di
         *  notifikasi), effect otomatis lengkap balik lewat attachEffects()->
         *  restoreSavedSettings() yang sudah ada, 0 logic baru di jalur itu. Pakai
         *  `CHANNEL_ID_RECOVERY` (dibuat bareng `CHANNEL_ID` di `createNotificationChannel()`
         *  - channel sudah pasti ada di sistem sejak service pernah jalan sekali,
         *  notification channel persist lintas proses/restart). Dibungkus
         *  try-catch total: kalau POST_NOTIFICATIONS belum granted atau gagal apa pun,
         *  gagal diam-diam ke Log.e (BUKAN crash Worker), konsisten pola file ini. */
        // Batch 143 (lint MissingPermission, false positive): notify() compat tanpa POST_NOTIFICATIONS
        // di-drop diam-diam, + sudah dibungkus try-catch total di bawah.
        @android.annotation.SuppressLint("MissingPermission")
        fun postRecoveryNotification(context: android.content.Context) {
            try {
                val startIntent = Intent(context, AudioEnhancerService::class.java)
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                // Batch 143 (lint NewApi): getForegroundService baru ada di API 26, minSdk 24 —
                // di API 24-25 NoSuchMethodError (Error, BUKAN Exception) lolos dari catch di bawah.
                // Pola SAMA dgn ScheduleWorker.postStartNotification().
                val restartPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(context, 1, startIntent, pendingFlags)
                } else {
                    PendingIntent.getService(context, 1, startIntent, pendingFlags)
                }
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_RECOVERY)
                    .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                    .setContentTitle(context.getString(R.string.notif_recovery_title))
                    .setContentText(context.getString(R.string.notif_recovery_body))
                    .setContentIntent(restartPending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIF_ID_RECOVERY, notification)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal kirim notifikasi recovery watchdog", e)
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

    // Batch 229 (request user: "enhancer kehilangan pengaruh saat beralih ke aplikasi sosmed macam YouTube"):
    // Effect Guard. Efek session-0 BISA lepas diam-diam tepat saat aplikasi lain mulai memutar audio: sistem
    // memindahkan chain efek ke output thread lain, vendor HAL mereset parameter, listener melaporkan efek
    // `enabled=false`, atau kontrol diambil (CONTROL_LOST). SEBELUM B229 semua itu hanya dicatat ke state; tidak
    // ada yang memulihkan sampai user buka app (banner) atau route audio berpindah. Sekarang tiga pemicu
    // (AudioPlaybackCallback sistem, listener enable/control tiap efek, perubahan route) mengantre SATU
    // pemeriksaan ber-debounce (`runEffectGuard`) yang memulihkan hanya yang rusak. Angka & aturan murni ada di
    // `EffectGuardPolicy` (bawah file, ada tesnya). NOT VERIFIED di device.
    private val guardHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @android.annotation.SuppressLint("NewApi")
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var lastPlaybackSignature: String = ""
    private var lastReacquireAtMs: Long = EffectGuardPolicy.NEVER
    private var reacquireFailures: Int = 0
    private var reassertWindowStartMs: Long = EffectGuardPolicy.NEVER
    private var reassertCount: Int = 0
    @Volatile private var pendingGuardReason: String = ""
    // Stempel setter publik terakhir (UI/preset/auto-profil/restore). Dipakai guard supaya TIDAK "memulihkan" nilai
    // lama di tengah user menggeser slider (efek sudah baru, pref belum tertulis) — lihat `runEffectGuard`.
    @Volatile private var lastAdjustAtMs: Long = EffectGuardPolicy.NEVER
    private val guardRunnable = Runnable { runEffectGuard(pendingGuardReason) }
    private val guardSettleRunnable = Runnable { runEffectGuard("playback-settle") }

    // Batch 230 (request user: "Perbaikan terakhir kurang berasa, teruskan!!"): B229 hanya memulihkan efek yang
    // TERBACA rusak (lost/disabled/drift). Efek session-0 yang terbaca SEHAT tapi tertinggal di output thread lama
    // (sistem memilih output untuk efek global saat efek DIBUAT) tidak terdeteksi API publik manapun -> tetap tak
    // terdengar di YouTube/sosmed. Perbaikan: saat pemutar media BARU mulai, efek dipasang ulang (release + attach)
    // sehingga sistem memilih ulang output yang sedang aktif. Aturan murni ada di `EffectGuardPolicy`. NOT VERIFIED.
    private var lastBindAtMs: Long = EffectGuardPolicy.NEVER
    private val rebindRunnable = Runnable { runEffectRebind("playback-start") }

    // Batch 237 (request user: "Real time 'gapless' belum kesampaian"): serah-terima DUA TAHAP. Tahap CEPAT
    // (`rebindRunnable`, REBIND_FAST_DELAY_MS) memasang efek ke output baru secepat mungkin sesudah pemutar berganti;
    // tahap PENEGASAN (`rebindSettleRunnable`, REBIND_DELAY_MS = jalur 700 ms yang terbukti mengatasi "mendem") tetap
    // jalan sebagai jaring pengaman bila tahap cepat mendarat di output yang belum final. Keduanya make-before-break.
    // `lastPlayerKey` = identitas pemutar media aktif (pergantian pemutar bertanda tangan atribut SAMA tak terlihat
    // oleh tanda tangan usage/contentType); `lastPlaybackChangeAtMs` hanya untuk log latensi (diagnostik).
    private val rebindSettleRunnable = Runnable { runEffectRebind("playback-settle", settle = true) }
    private var lastPlayerKey: String = ""
    private var lastPlaybackChangeAtMs: Long = EffectGuardPolicy.NEVER

    // Batch 231: pemeriksaan berkala SELAMA ada pemutar media aktif. Efek bisa dimatikan/di-reset vendor TANPA event
    // apa pun (tak ada callback enable/control/playback), jadi guard berbasis event B229 tak pernah terpicu. Tiap
    // GUARD_TICK_MS `runEffectGuard("tick")` membaca status nyata efek dan HANYA memulihkan yang rusak (efek sehat = diam).
    private val guardTickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || !EffectGuardPolicy.hasMediaPlayer(lastPlaybackSignature)) return
            runEffectGuard(EffectGuardPolicy.TICK_REASON)
            guardHandler.postDelayed(this, EffectGuardPolicy.GUARD_TICK_MS)
        }
    }

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
        } catch (e: Exception) {
            // Batch 206: dicatat (SpotBugs DE_MIGHT_IGNORE); tetap lanjut di prioritas default.
            android.util.Log.w(TAG, "Gagal menaikkan prioritas thread audio, lanjut prioritas default", e)
        }
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
        registerPlaybackGuard() // Batch 229
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
            endUsageSession()
            // Batch 9: catat ini SEBAGAI PILIHAN USER (bukan OS yang bunuh), supaya
            // ServiceWatchdogWorker gak menghidupkan paksa lagi tiap 15 menit.
            PrefsHelper.setUserWantsRunning(this, false)
            // Batch 128: cabut heartbeat exact alarm (Fast Recovery) — user sengaja mati,
            // alarm yatim cuma bangunin app sia-sia (performWatchdogCheck juga diam, ini
            // sekadar hemat wakeup). Jalur yang SAMA dipakai tombol Matikan, QS Tile,
            // Widget, DAN habisnya Sleep timer.
            ServiceWatchdogWorker.cancelExactRecovery(this)
            // Batch 119: Sleep timer ikut dibersihkan di jalur berhenti ini (dipakai tombol
            // Matikan, QS Tile, DAN habisnya timer itu sendiri).
            sleepHandler.removeCallbacks(sleepTick)
            guardHandler.removeCallbacksAndMessages(null) // Batch 229: user sengaja mati -> batalkan pemeriksaan Effect Guard yang antre
            PrefsHelper.setSleepTimerEndAt(this, 0L)
            BoosterWidgetProvider.refreshAll(this)
            // Batch 44 (bugfix): QS Tile SEBELUMNYA gak ikut diberi tahu di sini —
            // lihat catatan lengkap di `QuickToggleTileService.requestTileUpdate()`.
            QuickToggleTileService.requestTileUpdate(this)
            syncExcludeFromRecents(this) // Batch 171: Boomly mati -> task kembali tampil di Recents
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val wasRunning = isRunning // Batch 154: onStartCommand dipanggil ulang tiap klien start — sesi baru HANYA dari mati→hidup
        // Batch 221 (adaptasi AUDIO_BOOSTER_ARCHITECTURE_GUIDE §1, Android 14+): tipe foreground service dideklarasikan
        // EKSPLISIT saat runtime (API 29+), SAMA dgn `android:foregroundServiceType="mediaPlayback"` di manifest +
        // izin `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (sudah ada sebelumnya). Pasangan manifest<->runtime dijaga
        // `ForegroundServiceManifestTest`. <29 jatuh ke bentuk lama (tanpa tipe) — di luar prioritas API 31+.
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        // Re-enable jaga-jaga kalau sebelumnya sempat di-"Matikan" lewat notifikasi sementara
        // Service-nya sendiri tetap hidup karena masih bound — tanpa ini, buka app lagi setelah
        // tap "Matikan" tidak akan menyalakan ulang efeknya.
        enableEffects()
        isRunning = true
        if (!wasRunning) {
            beginUsageSession()
            resetEffectGuardState() // Batch 229: sesi baru = hitung ulang backoff/jendela Effect Guard
            updateGuardTick() // Batch 231: nyala lagi saat media sudah diputar -> pemeriksaan berkala ikut hidup
        }
        // Batch 9: tandai "user mau service ini hidup" tiap kali start beneran terjadi
        // (dari MainActivity, BootReceiver, QS Tile, Widget, atau Shortcut — semuanya
        // lewat requestStart() -> sini). ServiceWatchdogWorker baca flag ini buat
        // mutusin boleh/gaknya restart otomatis kalau nemu service mati.
        PrefsHelper.setUserWantsRunning(this, true)
        // Batch 128 (Fast Recovery, heartbeat PROAKTIF): pasang exact alarm SEKARANG selagi
        // service hidup, bukan nunggu tick watchdog 15 menit mendeteksi service mati
        // (desain reaktif Batch 127 = waktu pulih tak pernah lebih cepat dari watchdog).
        // Kalau service dibunuh OS, alarm ini tetap hidup di luar proses app dan fire
        // ≤~5 menit lagi → restart via jalur exempted. No-op diam kalau izin "Alarms &
        // reminders" belum granted (lihat kartu Pemulihan Cepat di SettingsScreen.kt).
        // Dipanggil juga di restart START_STICKY (intent null) & BootReceiver (lewat sini).
        ServiceWatchdogWorker.scheduleExactRecovery(this)
        BoosterWidgetProvider.refreshAll(this)
        // Batch 44 (bugfix): sama seperti cabang ACTION_STOP di atas — QS Tile ikut
        // disinkronkan di sini juga (jalur "start").
        QuickToggleTileService.requestTileUpdate(this)
        syncExcludeFromRecents(this) // Batch 171: Boomly menyala -> task disembunyikan dari Recents
        // Batch 119: lanjutkan Sleep timer kalau masih ada (restart OS / start ulang manual).
        scheduleSleepTick()
        // START_STICKY: minta sistem restart service ini jika dibunuh karena low memory
        return START_STICKY
    }

    /** Batch 154: catat awal sesi aktif + hitung 1 "dinyalakan" (statistik lokal, `PrefsHelper`). */
    private fun beginUsageSession() {
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        PrefsHelper.addUsageStart(this)
    }

    /** Batch 154: tutup sesi aktif — tambahkan durasinya ke total lalu kosongkan patokan (idempoten;
     *  sesi yang mati paksa tanpa onDestroy tidak terhitung, dicatat jujur di UI). */
    private fun endUsageSession() {
        val start = sessionStartElapsedMs
        if (start <= 0L) return
        sessionStartElapsedMs = 0L
        PrefsHelper.addUsageDuration(this, SystemClock.elapsedRealtime() - start)
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
        unregisterPlaybackGuard() // Batch 229
        guardHandler.removeCallbacksAndMessages(null)
        releaseEffects()
        isRunning = false
        endUsageSession() // no-op kalau sesi sudah ditutup di jalur ACTION_STOP
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
        lastBindAtMs = SystemClock.elapsedRealtime() // Batch 230: efek baru dipasang -> pasang-ulang otomatis ditahan sementara
    }

    private fun attachBass(enableNow: Boolean = true) {
        try {
            bassBoost = BassBoost(EffectGuardPolicy.EFFECT_PRIORITY, 0).apply { // Batch 231: priority dinaikkan (lihat EffectGuardPolicy.EFFECT_PRIORITY)
                if (enableNow) enabled = true // Batch 233: serah-terima efek (pasang-ulang) membuat efek baru TANPA menyala dulu
                setControlStatusListener { fx, granted ->
                    if (fx !== bassBoost) return@setControlStatusListener // Batch 233: callback instance lama/usang diabaikan
                    bassState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                    requestEffectGuard(if (granted) "control-regained:bass" else "control-lost:bass") // Batch 229
                }
                setEnableStatusListener { fx, isEnabled ->
                    if (fx !== bassBoost) return@setEnableStatusListener // Batch 233
                    if (bassState != EffectState.CONTROL_LOST) {
                        bassState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                    if (!isEnabled) requestEffectGuard("disabled:bass") // Batch 229: dimatikan sistem/HAL -> pulihkan (diam kalau user yang mematikan)
                }
            }
            bassState = EffectState.ENABLED
        } catch (e: Exception) {
            bassBoost = null; bassState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "BassBoost tidak tersedia di device ini", e)
        }
    }

    private fun attachVirtualizer(enableNow: Boolean = true) {
        try {
            virtualizer = Virtualizer(EffectGuardPolicy.EFFECT_PRIORITY, 0).apply { // Batch 231
                if (enableNow) enabled = true // Batch 233: serah-terima efek (pasang-ulang) membuat efek baru TANPA menyala dulu
                setControlStatusListener { fx, granted ->
                    if (fx !== virtualizer) return@setControlStatusListener // Batch 233: callback instance lama/usang diabaikan
                    virtualizerState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                    requestEffectGuard(if (granted) "control-regained:virtualizer" else "control-lost:virtualizer") // Batch 229
                }
                setEnableStatusListener { fx, isEnabled ->
                    if (fx !== virtualizer) return@setEnableStatusListener // Batch 233
                    if (virtualizerState != EffectState.CONTROL_LOST) {
                        virtualizerState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                    if (!isEnabled) requestEffectGuard("disabled:virtualizer") // Batch 229: dimatikan sistem/HAL -> pulihkan (diam kalau user yang mematikan)
                }
            }
            virtualizerState = EffectState.ENABLED
        } catch (e: Exception) {
            virtualizer = null; virtualizerState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "Virtualizer tidak tersedia di device ini", e)
        }
    }

    private fun attachEqualizer(enableNow: Boolean = true) {
        try {
            equalizer = Equalizer(EffectGuardPolicy.EFFECT_PRIORITY, 0).apply { // Batch 231
                if (enableNow) enabled = true // Batch 233: serah-terima efek (pasang-ulang) membuat efek baru TANPA menyala dulu
                setControlStatusListener { fx, granted ->
                    if (fx !== equalizer) return@setControlStatusListener // Batch 233: callback instance lama/usang diabaikan
                    equalizerState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                    requestEffectGuard(if (granted) "control-regained:equalizer" else "control-lost:equalizer") // Batch 229
                }
                setEnableStatusListener { fx, isEnabled ->
                    if (fx !== equalizer) return@setEnableStatusListener // Batch 233
                    if (equalizerState != EffectState.CONTROL_LOST) {
                        equalizerState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                    if (!isEnabled) requestEffectGuard("disabled:equalizer") // Batch 229: dimatikan sistem/HAL -> pulihkan (diam kalau user yang mematikan)
                }
            }
            equalizerState = EffectState.ENABLED
        } catch (e: Exception) {
            equalizer = null; equalizerState = EffectState.UNAVAILABLE
            android.util.Log.e(TAG, "Equalizer tidak tersedia di device ini", e)
        }
    }

    private fun attachLoudness(enableNow: Boolean = true) {
        try {
            loudnessEnhancer = LoudnessEnhancer(0).apply {
                if (enableNow) enabled = true // Batch 233: serah-terima efek (pasang-ulang) membuat efek baru TANPA menyala dulu
                setControlStatusListener { fx, granted ->
                    if (fx !== loudnessEnhancer) return@setControlStatusListener // Batch 233: callback instance lama/usang diabaikan
                    loudnessState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                    requestEffectGuard(if (granted) "control-regained:loudness" else "control-lost:loudness") // Batch 229
                }
                setEnableStatusListener { fx, isEnabled ->
                    if (fx !== loudnessEnhancer) return@setEnableStatusListener // Batch 233
                    if (loudnessState != EffectState.CONTROL_LOST) {
                        loudnessState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                    }
                    if (!isEnabled) requestEffectGuard("disabled:loudness") // Batch 229: dimatikan sistem/HAL -> pulihkan (diam kalau user yang mematikan)
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
     *  atas SEKARANG JUGA membawa MBC (`mbcInUse=true`; Batch 121 = 1 band, Batch 208 = 3 band `CompressorCurve.BAND_COUNT`,
     *  full-range, `cutoffFrequency` 20000 Hz) — kompresor yang BENERAN bisa diatur
     *  user (beda dari limiter di atas yang hardcoded/tidak ada slider), pelengkap
     *  `LoudnessEnhancer`. Batch 234: band ini TIDAK lagi dipasang netral (`enabled=false`) lalu dipulihkan
     *  belakangan — nilai tersimpan ditulis LANGSUNG begitu objek dibuat (`applyCompressorBands(..., this)`),
     *  karena pada serah-terima efek objek baru bisa memakai modul yang sama dgn yang lama sehingga netral
     *  sesaat = kompresor mati sesaat. `restoreSavedSettings()`/`applySavedToEffects()` sesudahnya hanya
     *  menegaskan nilai yang SAMA. PreEq fallback di atas ikut memakai gain tersimpan.
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
    private fun attachDynamicsProcessing(enableNow: Boolean = true) {
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
        // Batch 234: nilai tersimpan dibaca SEBELUM objek dibuat supaya bisa ditulis LANGSUNG begitu objek ada (lihat blok `apply`).
        val savedCompressor = PrefsHelper.getCompressorAmount(this)
        val savedVocalFocus = PrefsHelper.getVocalFocus(this)
        try {
            // Batch 235: `Config` SUDAH berisi nilai tersimpan (MBC, presence, EQ cadangan, limiter) saat konstruktor menulisnya ke
            // mesin efek; sebelumnya (B234 ke bawah) konstruktor menulis isi bawaan `Config.Builder` dulu, nilai tersimpan baru
            // menyusul lewat blok `apply` di bawah. Isi `Config` & jalur cadangannya: lihat `buildDynamicsConfig`.
            val config = buildDynamicsConfig(needsEqFallback, savedCompressor, savedVocalFocus)
            dynamicsProcessing = DynamicsProcessing(EffectGuardPolicy.EFFECT_PRIORITY, 0, config).apply { // Batch 231
                // Batch 235: penulisan di blok ini KINI penegasan idempoten (nilai SAMA dgn `config`) — dipertahankan karena
                // (1) menjaga `dynamicsState = FAILED` tetap terlapor, (2) menjadi jaring bila `buildDynamicsConfig` jatuh ke
                // `Config` bawaan (pembangunan Config terisi gagal).
                // Batch 234: HANYA nilai tersimpan yang ditulis, TIDAK PERNAH nilai netral. Pada serah-terima efek (pasang-ulang)
                // objek baru sering memakai modul efek yang SAMA dgn objek lama (output sama), jadi menulis band kompresor
                // `enabled=false` dulu lalu memulihkannya belakangan (B84-B233) = kompresor +24 dB mati sesaat = "mendem" +
                // lonjakan di tiap perpindahan pemutar. Urutan: kompresor + presence dulu (paling terdengar), baru EQ cadangan
                // dan limiter. `restoreSavedSettings()`/`applySavedToEffects()` sesudahnya hanya menegaskan nilai yang SAMA.
                applyCompressorBands(savedCompressor, savedVocalFocus, this)
                // Batch 87: band count HARUS sama dengan preEqBandCount di Config di atas
                // (kontrak `Eq`/constructor Config — dicek dokumentasi resmi sebelum ditulis).
                // Batch 234: gain awal = nilai EQ tersimpan (sebelumnya 0 dB netral); `fallbackEqGainsMb` tetap diisi lewat
                // `restoreSavedSettings()`/`applySavedToEffects()`, jangan duplikat logic itu di sini.
                if (needsEqFallback) {
                    FALLBACK_EQ_BANDS_HZ.forEachIndexed { index, freqHz ->
                        val savedGainDb = PrefsHelper.getEqualizerBandLevel(this@AudioEnhancerService, index, 0) / 100f
                        setPreEqBandAllChannelsTo(index, DynamicsProcessing.EqBand(true, freqHz, savedGainDb))
                    }
                }
                setLimiterAllChannelsTo(masterLimiter()) // Batch 235: angka limiter dipindah ke `masterLimiter()` (dipakai juga `buildDynamicsConfig`)
                if (enableNow) enabled = true // Batch 233: serah-terima efek (pasang-ulang) membuat efek baru TANPA menyala dulu
                // Batch 87: kalau fallback aktif, listener yang SAMA (satu-satunya objek
                // effect ini) SEKARANG juga menentukan `equalizerState` — objek limiter &
                // objek "EQ" adalah literal 1 instance yang sama di jalur fallback, jadi
                // status kontrol/enable-nya memang SATU. Kalau fallback TIDAK aktif
                // (mayoritas device), baris `if (needsEqFallback)` di bawah tidak pernah
                // jalan — `equalizerState` 100% tidak disentuh dari sini, persis kode lama.
                setControlStatusListener { fx, granted ->
                    if (fx !== dynamicsProcessing) return@setControlStatusListener // Batch 233
                    dynamicsState = if (granted) EffectState.ENABLED else EffectState.CONTROL_LOST
                    if (needsEqFallback) equalizerState = dynamicsState
                    requestEffectGuard(if (granted) "control-regained:dynamics" else "control-lost:dynamics") // Batch 229
                }
                setEnableStatusListener { fx, isEnabled ->
                    if (fx !== dynamicsProcessing) return@setEnableStatusListener // Batch 233
                    if (dynamicsState != EffectState.CONTROL_LOST) {
                        dynamicsState = if (isEnabled) EffectState.ENABLED else EffectState.AVAILABLE
                        if (needsEqFallback && equalizerState != EffectState.CONTROL_LOST) equalizerState = dynamicsState
                    }
                    if (!isEnabled) requestEffectGuard("disabled:dynamics") // Batch 229
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
     *  recreate object di sini tidak menjamin menang. Batch 231 (atas perintah user
     *  "teruskan kandidat"): priority efek DINAIKKAN lewat `EffectGuardPolicy.EFFECT_PRIORITY`
     *  (Bass, Virtualizer, Equalizer, DynamicsProcessing; LoudnessEnhancer tetap normal karena
     *  API publiknya tanpa parameter priority), jadi recreate kini bisa merebut kontrol dari
     *  app/vendor berprioritas normal. Kalau app lain masih pegang kontrol, effect ini kemungkinan
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
            attachBass(enableNow = false) // Batch 233: dinyalakan SETELAH nilai tersimpan diterapkan
            retried = true
        }
        if (virtualizerState == EffectState.CONTROL_LOST || virtualizerState == EffectState.FAILED) {
            try { virtualizer?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release Virtualizer lama sebelum retry", e)
            }
            attachVirtualizer(enableNow = false) // Batch 233: dinyalakan SETELAH nilai tersimpan diterapkan
            retried = true
        }
        if (equalizerState == EffectState.CONTROL_LOST || equalizerState == EffectState.FAILED) {
            try { equalizer?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release Equalizer lama sebelum retry", e)
            }
            attachEqualizer(enableNow = false) // Batch 233: dinyalakan SETELAH nilai tersimpan diterapkan
            retried = true
        }
        if (loudnessState == EffectState.CONTROL_LOST || loudnessState == EffectState.FAILED) {
            try { loudnessEnhancer?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release LoudnessEnhancer lama sebelum retry", e)
            }
            attachLoudness(enableNow = false) // Batch 233: dinyalakan SETELAH nilai tersimpan diterapkan
            retried = true
        }
        if (dynamicsState == EffectState.CONTROL_LOST || dynamicsState == EffectState.FAILED) {
            try { dynamicsProcessing?.release() } catch (e: Exception) {
                android.util.Log.e(TAG, "Gagal release DynamicsProcessing lama sebelum retry", e)
            }
            attachDynamicsProcessing(enableNow = false) // Batch 233: dinyalakan SETELAH nilai tersimpan diterapkan
            retried = true
        }
        if (retried) {
            android.util.Log.w(TAG, "retryControlAcquisition(): recreate effect yang CONTROL_LOST/FAILED")
            applySavedToEffects() // Batch 234: hanya menulis ke efek (sebelumnya `restoreSavedSettings()` = + tulis prefs & stempel "user menggeser")
            enableMixEffects() // Batch 233: nyalakan efek yang baru dibuat (tanpa jendela nilai bawaan)
        }
        return retried
    }

    // ---- Batch 229: Effect Guard (pemulihan efek saat pindah ke aplikasi lain, mis. YouTube) ----

    /** Antre SATU pemeriksaan [runEffectGuard] (debounce [EffectGuardPolicy.DEBOUNCE_MS]: banyak event beruntun
     *  = 1 pemeriksaan). Diam kalau Service tidak sedang aktif, jadi "Matikan" dari user tidak pernah dilawan. */
    private fun requestEffectGuard(reason: String, delayMs: Long = EffectGuardPolicy.DEBOUNCE_MS) {
        if (!isRunning) return
        pendingGuardReason = reason
        guardHandler.removeCallbacks(guardRunnable)
        guardHandler.postDelayed(guardRunnable, delayMs)
    }

    private fun markSettingAdjusted() {
        lastAdjustAtMs = SystemClock.elapsedRealtime()
    }

    private fun resetEffectGuardState() {
        lastReacquireAtMs = EffectGuardPolicy.NEVER
        reacquireFailures = 0
        reassertWindowStartMs = EffectGuardPolicy.NEVER
        reassertCount = 0
    }

    /** `AudioPlaybackCallback` (API 26+, publik, tanpa izin) = tahu kapan aplikasi LAIN mulai/berhenti memutar audio
     *  (YouTube, TikTok, dst). Dipasang di `guardHandler` (main thread). Gagal-aman: kalau sistem menolak, hanya Log. */
    @android.annotation.SuppressLint("NewApi")
    private fun registerPlaybackGuard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = audioManager ?: return
        try {
            val callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    onPlaybackConfigsChanged(configs)
                }
            }
            manager.registerAudioPlaybackCallback(callback, guardHandler)
            playbackCallback = callback
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal register AudioPlaybackCallback (pemulihan efek saat pindah aplikasi nonaktif)", e)
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private fun unregisterPlaybackGuard() {
        val callback = playbackCallback ?: return
        playbackCallback = null
        try {
            audioManager?.unregisterAudioPlaybackCallback(callback)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal unregister AudioPlaybackCallback", e)
        }
    }

    /** Hanya bereaksi bila "tanda tangan" pemutaran (usage/contentType tiap pemutar aktif) BERUBAH — mis. musik
     *  berhenti lalu video YouTube mulai. Dua pemeriksaan: cepat ([EffectGuardPolicy.DEBOUNCE_MS]) dan satu lagi
     *  setelah sistem selesai memindahkan output ([EffectGuardPolicy.SETTLE_RECHECK_MS]). Tanda tangan juga di-Log
     *  (diagnostik: ketahuan jenis pemutaran apa yang memicu efek lepas). */
    @android.annotation.SuppressLint("NewApi")
    private fun onPlaybackConfigsChanged(configs: List<AudioPlaybackConfiguration>?) {
        val signature = try {
            EffectGuardPolicy.playbackSignature(
                configs.orEmpty().map { it.audioAttributes.usage to it.audioAttributes.contentType }
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Gagal membaca AudioAttributes pemutar aktif", e)
            "n=${configs?.size ?: 0}"
        }
        // Batch 237: identitas pemutar media (hashCode `AudioPlaybackConfiguration`: dua objek yang `equals` pasti sama hash-nya).
        // Gagal membaca = anggap tak berubah (tak memicu pasang-ulang palsu).
        val playerKey = try {
            EffectGuardPolicy.playerKey(
                configs.orEmpty()
                    .filter { EffectGuardPolicy.isRebindUsage(it.audioAttributes.usage) }
                    .map { it.hashCode() }
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Gagal membaca identitas pemutar media aktif", e)
            lastPlayerKey
        }
        if (signature == lastPlaybackSignature && playerKey == lastPlayerKey) return
        val previousSignature = lastPlaybackSignature
        val previousPlayerKey = lastPlayerKey
        lastPlaybackSignature = signature
        lastPlayerKey = playerKey
        val rebind = isRunning && (
            EffectGuardPolicy.shouldRebindOnPlayback(previousSignature, signature) ||
                EffectGuardPolicy.shouldRebindOnPlayers(previousPlayerKey, playerKey)
            )
        android.util.Log.i(TAG, "Pemutaran sistem berubah: [$signature] pemutar=[$playerKey] running=$isRunning pasangUlang=$rebind")
        if (!isRunning) return
        requestEffectGuard("playback")
        if (rebind) {
            // Batch 230: pemutar media BARU mulai -> pasang ulang efek (sistem sudah memilih output saat pemutar mulai).
            // Batch 232: juga saat himpunan pemutar media BERUBAH (mis. video lama berhenti sesudah yang baru mulai); tiap
            // perubahan baru mengundurkan jadwal ini (debounce), jadi pasang-ulang jatuh SETELAH perpindahan output reda.
            // Batch 237: DUA TAHAP — cepat (efek ikut output baru secepat mungkin) + penegasan (jalur 700 ms B230-B236,
            // jaring pengaman bila tahap cepat mendarat di output yang belum final; efek tak bisa mendeteksinya sendiri).
            // Pemicunya juga pergantian IDENTITAS pemutar (atribut sama), bukan hanya perubahan tanda tangan atribut.
            lastPlaybackChangeAtMs = SystemClock.elapsedRealtime()
            guardHandler.removeCallbacks(rebindRunnable)
            guardHandler.postDelayed(rebindRunnable, EffectGuardPolicy.REBIND_FAST_DELAY_MS)
            guardHandler.removeCallbacks(rebindSettleRunnable)
            guardHandler.postDelayed(rebindSettleRunnable, EffectGuardPolicy.REBIND_DELAY_MS)
        }
        updateGuardTick() // Batch 231
        guardHandler.removeCallbacks(guardSettleRunnable)
        guardHandler.postDelayed(guardSettleRunnable, EffectGuardPolicy.SETTLE_RECHECK_MS)
    }

    /** Batch 231: (re)jadwalkan pemeriksaan berkala. Hidup hanya bila Service aktif DAN ada pemutar media; mati otomatis
     *  begitu pemutaran berhenti / "Matikan" (`removeCallbacksAndMessages(null)`). Dipanggil juga saat Service dinyalakan
     *  lagi, karena callback pemutaran tidak berbunyi ulang bila tanda tangan pemutaran tidak berubah. */
    private fun updateGuardTick() {
        guardHandler.removeCallbacks(guardTickRunnable)
        if (isRunning && EffectGuardPolicy.hasMediaPlayer(lastPlaybackSignature)) {
            guardHandler.postDelayed(guardTickRunnable, EffectGuardPolicy.GUARD_TICK_MS)
        }
    }

    /** Batch 233: SERAH-TERIMA TANPA CELAH (make-before-break). B230-B232 melepas kelima efek LAMA lebih dulu, lalu membuat
     *  yang baru dengan `enabled = true` dan nilai bawaan; nilai tersimpan baru diterapkan setelah kelimanya terpasang.
     *  Selama jendela itu keluaran tanpa efek / bernilai bawaan (kompresor +24 dB hilang) = suara "mendem" dan lonjakan
     *  sepersekian detik, berulang tiap perpindahan pemutar (sosmed) — gejala yang dilaporkan user. Kini: efek lama TETAP
     *  hidup dan menyala; efek baru dibuat TANPA menyala, diberi nilai tersimpan, baru dinyalakan; efek lama dilepas
     *  PALING AKHIR. Efek baru yang gagal dibuat tidak menggantikan efek lama yang masih sehat. Callback instance lama
     *  diabaikan lewat pemeriksaan identitas di listener (tanpa ini kontrol yang berpindah memicu pemulihan berantai).
     *  Penjelasan perilaku sistem (instance baru berprioritas sama mengambil kontrol, modul efek yang sama terus berjalan)
     *  berasal dari pembacaan kode AudioFlinger, BELUM diuji di device.
     *  Batch 234 (menutup dua celah yang B233 sisakan): (1) objek DynamicsProcessing baru KINI langsung ditulis dgn nilai
     *  tersimpan saat dibuat (bukan netral dulu) — pada output yang sama modulnya dipakai bersama, jadi netral sesaat =
     *  kompresor mati sesaat; (2) nilai tersimpan diterapkan lewat [applySavedToEffects] (tanpa tulis prefs & tanpa stempel
     *  "user menggeser" yang menunda pasang-ulang berikutnya); (3) jeda minimum antar pasang-ulang 5 -> 2 dtk.
     *
     *  Batch 230: lepas lalu pasang ulang 5 efek session-0 (Bass, Virtualizer, Equalizer, Loudness, Dynamics) +
     *  terapkan ulang nilai tersimpan. Alasan: sistem memilih output thread untuk efek global saat efek DIBUAT;
     *  efek yang dibuat sebelum pemutar baru (mis. YouTube) mulai bisa tertinggal di output lama sambil tetap
     *  terbaca `enabled` dan "sehat" — tak ada API publik untuk mendeteksinya, jadi satu-satunya jalan non-root
     *  adalah membuatnya ulang saat output baru sudah aktif. Visualizer TIDAK disentuh (observer pasif).
     *  Pengaman: diam bila Service mati; ditunda selama user menggeser slider; dibatasi
     *  [EffectGuardPolicy.REBIND_FAST_MIN_GAP_MS] (tahap cepat; B238, sebelumnya REBIND_MIN_INTERVAL_MS) sejak pemasangan terakhir (anti-churn) — Batch 232:
     *  yang tertahan jeda itu DITUNDA sampai jeda habis (bukan dibuang), dan dibatalkan bila pemutaran sudah berhenti. */
    private fun runEffectRebind(reason: String, settle: Boolean = false) {
        if (!isRunning) return
        // Batch 237: tahap cepat & penegasan masing-masing menunda DIRINYA sendiri (runnable berbeda), bukan saling menimpa.
        val runnable = if (settle) rebindSettleRunnable else rebindRunnable
        val now = SystemClock.elapsedRealtime()
        if (EffectGuardPolicy.isQuietPeriod(now, lastAdjustAtMs)) {
            guardHandler.removeCallbacks(runnable)
            guardHandler.postDelayed(runnable, EffectGuardPolicy.USER_ADJUST_QUIET_MS)
            return
        }
        if (!EffectGuardPolicy.hasMediaPlayer(lastPlaybackSignature)) {
            // Batch 232: pemutaran sudah berhenti selagi pasang-ulang menunggu -> tak ada output aktif yang perlu diikuti.
            android.util.Log.i(TAG, "EffectRebind[$reason]: dibatalkan (tak ada pemutar media aktif)")
            return
        }
        // Batch 237: tahap penegasan hanya menunggu jeda pendek sesudah tahap cepat (REBIND_SETTLE_MIN_GAP_MS).
        // Batch 238: tahap cepat juga memakai jeda pendek (REBIND_FAST_MIN_GAP_MS). Sebelumnya 2 dtk (REBIND_MIN_INTERVAL_MS)
        // diukur dari pemasangan terakhir = tahap PENEGASAN perpindahan sebelumnya (+700 ms); pada geser video tiap 1-2 dtk tahap
        // cepat selalu tertahan sampai melewati tahap penegasan miliknya sendiri, jadi efek di video baru tak pernah datang di +200 ms.
        val minGapMs = if (settle) EffectGuardPolicy.REBIND_SETTLE_MIN_GAP_MS else EffectGuardPolicy.REBIND_FAST_MIN_GAP_MS
        val waitMs = EffectGuardPolicy.rebindWaitMs(now, lastBindAtMs, minGapMs)
        if (waitMs > 0L) {
            // Batch 232: DITUNDA, bukan dibuang. Sebelumnya (B230) pasang-ulang yang tiba < 30 dtk sejak pemasangan
            // terakhir langsung dibuang, sehingga perpindahan output berikutnya (pemutar datang-pergi di sosmed) tak
            // pernah diikuti dan efek tertinggal di output lama ("mendem").
            android.util.Log.i(TAG, "EffectRebind[$reason]: ditunda $waitMs ms (jeda minimum $minGapMs ms sejak serah-terima terakhir)")
            guardHandler.removeCallbacks(runnable)
            guardHandler.postDelayed(runnable, waitMs)
            return
        }
        val before = describeEffectStates()
        val oldBass = bassBoost; val oldBassState = bassState
        val oldVirtualizer = virtualizer; val oldVirtualizerState = virtualizerState
        val oldEqualizer = equalizer; val oldEqualizerState = equalizerState
        val oldLoudness = loudnessEnhancer; val oldLoudnessState = loudnessState
        val oldDynamics = dynamicsProcessing; val oldDynamicsState = dynamicsState
        val oldFallbackActive = equalizerFallbackActive
        // Efek lama TETAP hidup dan menyala selama efek baru dibuat; efek baru dibuat tanpa menyala.
        attachBass(enableNow = false)
        if (shouldKeepOld("BassBoost", oldBass != null, bassBoost != null)) { bassBoost = oldBass; bassState = oldBassState }
        attachVirtualizer(enableNow = false)
        if (shouldKeepOld("Virtualizer", oldVirtualizer != null, virtualizer != null)) { virtualizer = oldVirtualizer; virtualizerState = oldVirtualizerState }
        attachEqualizer(enableNow = false) // urutan SAMA dgn attachEffects(): `attachDynamicsProcessing()` membaca `equalizerState` (fallback EQ)
        if (shouldKeepOld("Equalizer", oldEqualizer != null, equalizer != null)) { equalizer = oldEqualizer; equalizerState = oldEqualizerState }
        attachLoudness(enableNow = false)
        if (shouldKeepOld("LoudnessEnhancer", oldLoudness != null, loudnessEnhancer != null)) { loudnessEnhancer = oldLoudness; loudnessState = oldLoudnessState }
        attachDynamicsProcessing(enableNow = false)
        if (shouldKeepOld("DynamicsProcessing", oldDynamics != null, dynamicsProcessing != null)) {
            dynamicsProcessing = oldDynamics; dynamicsState = oldDynamicsState
            equalizerFallbackActive = oldFallbackActive
            if (oldFallbackActive) equalizerState = oldEqualizerState
        }
        // Nilai tersimpan diterapkan ke efek baru SEBELUM dinyalakan, supaya tak ada jendela nilai bawaan.
        // Batch 234: lewat `applySavedToEffects()` (hanya menulis ke efek; bukan `restoreSavedSettings()` yang juga menulis
        // prefs + menandai "user menggeser" sehingga menunda pasang-ulang/guard berikutnya).
        applySavedToEffects()
        enableMixEffects()
        // Efek lama dilepas PALING AKHIR (hanya yang benar-benar digantikan).
        releaseReplaced("BassBoost", oldBass, bassBoost)
        releaseReplaced("Virtualizer", oldVirtualizer, virtualizer)
        releaseReplaced("Equalizer", oldEqualizer, equalizer)
        releaseReplaced("LoudnessEnhancer", oldLoudness, loudnessEnhancer)
        // Batch 237 (lint run36: NewApi "Implicit cast from DynamicsProcessing to AudioEffect requires API level 28"):
        // DynamicsProcessing baru ada sejak API 28 dan attach-nya sudah dijaga SDK_INT >= P; pelepasan lamanya kini dijaga sama.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) releaseReplacedDynamics(oldDynamics, dynamicsProcessing)
        val finishedAtMs = SystemClock.elapsedRealtime()
        lastBindAtMs = finishedAtMs
        // Batch 237: latensi NYATA di device (diagnostik): durasi serah-terima + selang sejak perubahan pemutar terakhir.
        val sincePlaybackChange = if (lastPlaybackChangeAtMs == EffectGuardPolicy.NEVER) "n/a" else "${finishedAtMs - lastPlaybackChangeAtMs} ms"
        android.util.Log.w(
            TAG,
            "EffectRebind[$reason]: efek diserahterimakan tanpa celah; sebelum ($before) sesudah (${describeEffectStates()}); " +
                "serah-terima ${finishedAtMs - now} ms, sejak perubahan pemutar $sincePlaybackChange"
        )
    }

    /** Batch 233: lepas efek LAMA hanya bila benar-benar sudah digantikan instance baru (`old !== current`). Efek lama
     *  yang dipertahankan karena efek baru gagal dibuat ([shouldKeepOld]) TIDAK dilepas. */
    private fun releaseReplaced(name: String, old: android.media.audiofx.AudioEffect?, current: android.media.audiofx.AudioEffect?) {
        if (old == null || old === current) return
        try { old.release() } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal release $name lama setelah serah-terima efek", e)
        }
    }

    /** Batch 237: pembungkus sempit untuk efek DynamicsProcessing (API 28+). Cast implisit ke `AudioEffect` hanya terjadi di
     *  sini dan HANYA dipanggil di balik `SDK_INT >= P`; `NewApi` ditekan di fungsi ini saja, bukan di `runEffectRebind`. */
    @android.annotation.SuppressLint("NewApi")
    private fun releaseReplacedDynamics(old: DynamicsProcessing?, current: DynamicsProcessing?) {
        releaseReplaced("DynamicsProcessing", old, current)
    }

    /** Batch 233: true bila efek baru GAGAL dibuat padahal efek lama masih ada -> pertahankan yang lama (jangan kehilangan
     *  efek hanya karena satu pembuatan ulang gagal). Keputusan ada di [EffectGuardPolicy.handoffOutcome] (ada tesnya). */
    private fun shouldKeepOld(name: String, hadOld: Boolean, hasNew: Boolean): Boolean {
        val keep = EffectGuardPolicy.handoffOutcome(hadOld, hasNew) == EffectGuardPolicy.HandoffOutcome.KEEP_OLD
        if (keep) android.util.Log.w(TAG, "EffectRebind: $name baru gagal dibuat, efek lama dipertahankan")
        return keep
    }

    /** Pemeriksaan + pemulihan. Hanya menyentuh yang RUSAK: (1) kontrol hilang -> rebut ulang lewat
     *  [retryControlAcquisition] dengan backoff (bukan loop rapat); (2) efek terbaca `enabled=false` -> nyalakan
     *  lagi; (3) parameter terbaca menyimpang dari yang tersimpan (HAL reset) -> [restoreSavedSettings]. Efek sehat
     *  TIDAK disentuh sama sekali (perilaku & suara sama persis dgn sebelum B229). `FAILED` SENGAJA tidak memicu
     *  rebut-ulang otomatis (biasanya kegagalan permanen lokal mis. strength tak didukung; recreate sia-sia).
     *  Dibatasi [EffectGuardPolicy.REASSERT_MAX_PER_WINDOW] intervensi per jendela supaya tidak adu tarik dgn
     *  sistem/aplikasi lain. */
    private fun runEffectGuard(reason: String) {
        if (!isRunning) return
        val now = SystemClock.elapsedRealtime()
        val lost = hasControlLostEffect()
        val disabled = findDisabledEffects()
        val adjusting = EffectGuardPolicy.isQuietPeriod(now, lastAdjustAtMs)
        val drifted = if (lost || adjusting) emptyList() else findDriftedSettings()
        if (adjusting && !lost && disabled.isEmpty()) {
            // User sedang menggeser slider: nilai tersimpan bisa tertinggal sesaat dari efek. Periksa drift SETELAH tenang.
            requestEffectGuard(reason, EffectGuardPolicy.USER_ADJUST_QUIET_MS)
            return
        }
        if (!lost && disabled.isEmpty() && drifted.isEmpty()) {
            if (reason != EffectGuardPolicy.TICK_REASON) { // Batch 231: tick berkala sehat = diam (hindari banjir log)
                android.util.Log.i(TAG, "EffectGuard[$reason]: efek sehat (${describeEffectStates()})")
            }
            return
        }
        val decision = EffectGuardPolicy.reassertDecision(now, reassertWindowStartMs, reassertCount)
        reassertWindowStartMs = decision.windowStartMs
        reassertCount = decision.count
        if (!decision.allowed) {
            android.util.Log.w(
                TAG,
                "EffectGuard[$reason]: DITAHAN (batas intervensi tercapai) lost=$lost disabled=$disabled drifted=$drifted (${describeEffectStates()})"
            )
            return
        }
        var reacquired = false
        if (lost && EffectGuardPolicy.mayReacquire(now, lastReacquireAtMs, reacquireFailures)) {
            lastReacquireAtMs = now
            reacquired = retryControlAcquisition() // sudah menerapkan nilai tersimpan ke efek baru (`applySavedToEffects()`, B234)
            reacquireFailures = if (hasControlLostEffect()) reacquireFailures + 1 else 0
        }
        if (disabled.isNotEmpty()) enableEffects()
        // Batch 238: nilai tersimpan ditulis lewat `applySavedToEffects()` (hanya ke efek), BUKAN `restoreSavedSettings()`.
        // Setter publik di `restoreSavedSettings()` memanggil `markSettingAdjusted()` -> stempel "user menggeser" palsu ->
        // `runEffectRebind` (tahap penegasan 700 ms) ditunda USER_ADJUST_QUIET_MS (1,5 dtk) tepat saat guard (400 ms) menemukan
        // efek rusak = saat perpindahan output paling butuh serah-terima. Jalur serah-terima sudah bersih sejak B234; ini sisanya.
        if (!reacquired && (disabled.isNotEmpty() || drifted.isNotEmpty())) applySavedToEffects()
        android.util.Log.w(
            TAG,
            "EffectGuard[$reason]: lost=$lost reacquired=$reacquired disabled=$disabled drifted=$drifted " +
                "gagalBeruntun=$reacquireFailures (${describeEffectStates()})"
        )
    }

    private fun hasControlLostEffect(): Boolean =
        listOf(bassState, virtualizerState, equalizerState, loudnessState, dynamicsState)
            .any { it == EffectState.CONTROL_LOST }

    private fun describeEffectStates(): String =
        "bass=$bassState virt=$virtualizerState eq=$equalizerState loud=$loudnessState dyn=$dynamicsState"

    /** Baca `enabled` langsung dari objek efek. `null` = tak terbaca/efek tak ada (BUKAN dianggap mati). */
    private fun readEnabled(name: String, read: () -> Boolean?): Boolean? = try {
        read()
    } catch (e: Exception) {
        android.util.Log.w(TAG, "Gagal membaca status enabled $name", e)
        null
    }

    private fun findDisabledEffects(): List<String> {
        val disabled = mutableListOf<String>()
        if (readEnabled("bass") { bassBoost?.enabled } == false) disabled += "bass"
        if (readEnabled("virtualizer") { virtualizer?.enabled } == false) disabled += "virtualizer"
        if (readEnabled("equalizer") { equalizer?.enabled } == false) disabled += "equalizer"
        if (readEnabled("loudness") { loudnessEnhancer?.enabled } == false) disabled += "loudness"
        if (readEnabled("dynamics") { dynamicsProcessing?.enabled } == false) disabled += "dynamics"
        return disabled
    }

    /** Bandingkan parameter yang SEDANG aktif di efek dengan nilai tersimpan ([PrefsHelper]). Toleransi pembulatan
     *  device ada di [EffectGuardPolicy]; DynamicsProcessing tidak dibandingkan (tak ada getter praktis) — ia
     *  ikut dipulihkan lewat [restoreSavedSettings] bila efek lain terdeteksi menyimpang/mati. */
    private fun findDriftedSettings(): List<String> {
        val drifted = mutableListOf<String>()
        try {
            bassBoost?.takeIf { it.strengthSupported }?.let {
                if (EffectGuardPolicy.strengthDiffers(PrefsHelper.getBass(this), it.roundedStrength.toInt())) drifted += "bass"
            }
            virtualizer?.takeIf { it.strengthSupported }?.let {
                if (EffectGuardPolicy.strengthDiffers(PrefsHelper.getVirtualizer(this), it.roundedStrength.toInt())) drifted += "virtualizer"
            }
            loudnessEnhancer?.let {
                if (EffectGuardPolicy.loudnessDiffers(PrefsHelper.getLoudness(this), it.targetGain)) drifted += "loudness"
            }
            if (!equalizerFallbackActive) {
                equalizer?.let { eq ->
                    for (band in 0 until eq.numberOfBands.toInt()) {
                        val actual = eq.getBandLevel(band.toShort()).toInt()
                        if (EffectGuardPolicy.eqLevelDiffers(PrefsHelper.getEqualizerBandLevel(this, band, 0), actual)) {
                            drifted += "equalizer"
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Gagal membandingkan parameter efek dengan nilai tersimpan", e)
        }
        return drifted
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
            requestEffectGuard("route") // Batch 229: parameter bisa ter-reset HAL saat sink berpindah -> verifikasi + terapkan ulang bila perlu
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

    /** Batch 234: padanan [restoreSavedSettings] KHUSUS serah-terima efek ([runEffectRebind], [retryControlAcquisition]),
     *  tempat efek BARU dibuat tanpa menyala dan harus sudah bernilai benar sebelum [enableMixEffects]. Beda dari
     *  [restoreSavedSettings] yang memanggil setter publik: (1) HANYA menulis ke efek — tanpa menulis ulang SharedPreferences
     *  dan tanpa [markSettingAdjusted]; nilai yang sama ditulis balik ke disk tanpa guna, dan stempel "user sedang menggeser"
     *  palsu akan menunda pasang-ulang berikutnya + pemeriksaan drift Effect Guard selama [EffectGuardPolicy.USER_ADJUST_QUIET_MS];
     *  (2) urutan: nilai ringan dulu, kompresor DynamicsProcessing terakhir (nilainya SUDAH ditulis saat objek dibuat —
     *  lihat [attachDynamicsProcessing]; penulisan ulang di sini idempoten dan menjaga status FAILED tetap terlapor).
     *  Penanganan gagal per efek SAMA dgn setter publik (state FAILED + Log.e). */
    @android.annotation.SuppressLint("NewApi")
    private fun applySavedToEffects() {
        try { bassBoost?.setStrength(PrefsHelper.getBass(this).toShort()) } catch (e: Exception) {
            bassState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal menerapkan BassBoost tersimpan (serah-terima efek)", e)
        }
        try { virtualizer?.setStrength(PrefsHelper.getVirtualizer(this).toShort()) } catch (e: Exception) {
            virtualizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal menerapkan Virtualizer tersimpan (serah-terima efek)", e)
        }
        try { loudnessEnhancer?.setTargetGain(PrefsHelper.getLoudness(this).toInt()) } catch (e: Exception) {
            loudnessState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal menerapkan LoudnessEnhancer tersimpan (serah-terima efek)", e)
        }
        if (isEqualizerSupported()) {
            for (band in 0 until getEqualizerBandCount()) {
                val savedMb = PrefsHelper.getEqualizerBandLevel(this, band, 0)
                try {
                    if (equalizerFallbackActive) {
                        // Jalur cadangan: PreEq DynamicsProcessing, konversi mB -> dB sama dgn `setEqualizerBand()`.
                        dynamicsProcessing?.setPreEqBandAllChannelsTo(
                            band, DynamicsProcessing.EqBand(true, FALLBACK_EQ_BANDS_HZ[band], savedMb / 100f)
                        )
                        fallbackEqGainsMb[band] = savedMb.toShort()
                    } else {
                        equalizer?.setBandLevel(band.toShort(), savedMb.toShort())
                    }
                } catch (e: Exception) {
                    equalizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal menerapkan EQ band $band tersimpan (serah-terima efek)", e)
                }
            }
        }
        applyCompressorBands(PrefsHelper.getCompressorAmount(this), PrefsHelper.getVocalFocus(this))
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
        enableMixEffects()
        try { visualizer?.enabled = true } catch (e: Exception) {
            visualizerState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal enable Visualizer", e)
        }
    }

    /** Batch 233: bagian [enableEffects] untuk 5 efek mixer session-0 SAJA (tanpa Visualizer). Dipakai serah-terima
     *  efek ([runEffectRebind]) dan [retryControlAcquisition]; perilaku per efek SAMA dengan sebelumnya. */
    private fun enableMixEffects() {
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
        markSettingAdjusted()
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
        markSettingAdjusted()
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
        markSettingAdjusted()
        try { loudnessEnhancer?.setTargetGain(gainMb.toInt()) } catch (e: Exception) {
            loudnessState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set LoudnessEnhancer gain", e)
        }
        PrefsHelper.setLoudness(this, gainMb)
    }

    /** Batch 121 (Fase 8 ROI #4 "Compressor"): 1 slider "Amount" 0..100 menyetir band
     *  MBC 3 band (Batch 208) di `dynamicsProcessing` (lihat `attachDynamicsProcessing()`,
     *  `CompressorCurve`, konstanta `COMPRESSOR_*` di companion). [amount] 0 = band di-nonaktifkan (bypass
     *  total, 0 beda audible dari sebelum fitur ini ada); >0 menaikkan ratio + menurunkan
     *  threshold BERSAMAAN, `postGain` makeup ikut naik supaya loudness tidak terasa
     *  drop drastis saat kompresi aktif (Batch 198: attack/release ikut mengencang ke arah
     *  100%; ujung kurva band vokal = 20:1 / -36 dB / +24 dB, band bass/treble makeup lebih kecil,
     *  lihat `CompressorCurve`). Kurva 0..100 di bawah PERKIRAAN (tidak ada API
     *  resmi query "kurva ideal" per device) — NOT VERIFIED di device fisik, kandidat
     *  pertama kalau user lapor kompresi terlalu halus/agresif. `dynamicsProcessing`
     *  null (API<28 atau gagal construct) = no-op aman, sama pola seperti `setLoudnessGain()`
     *  di atas — setting TETAP disimpan ke `PrefsHelper` tanpa syarat (Batch 57, audit Gap
     *  #14: user tidak boleh kehilangan preferensi slider walau apply gagal). */
    fun setCompressorAmount(amount: Int) {
        val clamped = amount.coerceIn(0, 100)
        applyCompressorBands(clamped, PrefsHelper.getVocalFocus(this))
        PrefsHelper.setCompressorAmount(this, clamped)
    }

    /** Batch 209 (request user: slider "Vocal Focus" 0..100): seberapa jauh makeup band bass & treble
     *  ditarik di bawah band vokal saat Compressor menyala (0 = makeup sama di semua band, 50 = Batch 208,
     *  100 = bass/treble paling ditekan; lihat `CompressorCurve`). Tidak berefek bila Compressor 0%
     *  (bypass), tapi nilainya TETAP disimpan ke `PrefsHelper` tanpa syarat (pola Batch 57) dan
     *  langsung dipakai begitu Compressor dinyalakan. `dynamicsProcessing` null = no-op aman. */
    fun setVocalFocus(amount: Int) {
        val clamped = amount.coerceIn(0, 100)
        PrefsHelper.setVocalFocus(this, clamped)
        applyCompressorBands(PrefsHelper.getCompressorAmount(this), clamped)
    }

    // Batch 235: konversi kurva -> objek band, SATU sumber untuk dua jalur: `applyCompressorBands` (menulis ke objek yang
    // sudah ada) dan `buildDynamicsConfig` (mengisi `Config` sebelum objek dibuat). Dua salinan angka bisa berselisih diam-diam.
    // `amount` DIPAKAI MENTAH (bukan yang sudah di-clamp) untuk `enabled`, sama dgn perilaku B234; `fraction`/`focus` 0..1.
    @android.annotation.SuppressLint("NewApi")
    private fun compressorMbcBand(bandIndex: Int, amount: Int, fraction: Float, focus: Float): DynamicsProcessing.MbcBand {
        val spec = CompressorCurve.band(bandIndex, fraction, focus)
        return DynamicsProcessing.MbcBand(
            /* enabled            = */ amount > 0,
            /* cutoffFrequency    = */ spec.cutoffHz,
            /* attackTime         = */ spec.attackMs,
            /* releaseTime        = */ spec.releaseMs,
            /* ratio              = */ spec.ratio,
            /* threshold          = */ spec.thresholdDb,
            /* kneeWidth          = */ COMPRESSOR_KNEE_DB,
            /* noiseGateThreshold = */ spec.noiseGateDb,
            /* expanderRatio      = */ spec.expanderRatio,
            /* preGain            = */ 0f,
            /* postGain           = */ spec.postGainDb
        )
    }

    // Batch 210: presence vokal (post-EQ 3 band: netral / +gain 2-4,5 kHz / netral), ikut Compressor & Vocal Focus.
    @android.annotation.SuppressLint("NewApi")
    private fun presenceEqBand(presenceIndex: Int, amount: Int, fraction: Float, focus: Float): DynamicsProcessing.EqBand =
        DynamicsProcessing.EqBand(
            amount > 0,
            CompressorCurve.presenceCutoffHz(presenceIndex),
            CompressorCurve.presenceGainDb(presenceIndex, fraction, focus)
        )

    // Batch 84 limiter utama (angka dipindah dari `attachDynamicsProcessing()` di Batch 235, nilai TIDAK berubah).
    @android.annotation.SuppressLint("NewApi")
    private fun masterLimiter(): DynamicsProcessing.Limiter = DynamicsProcessing.Limiter(
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

    @android.annotation.SuppressLint("NewApi")
    private fun newDynamicsConfigBuilder(needsEqFallback: Boolean): DynamicsProcessing.Config.Builder =
        DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, // respons transient limiter lebih relevan dari resolusi frekuensi di sini
            2,                                                // channelCount (stereo, lihat catatan panjang `attachDynamicsProcessing`)
            needsEqFallback, if (needsEqFallback) FALLBACK_EQ_BANDS_HZ.size else 0, // pre-EQ: Batch 87, HANYA aktif kalau Equalizer legacy UNAVAILABLE
            true, CompressorCurve.BAND_COUNT, // Batch 121: MBC = kompresor user-adjustable (lihat setCompressorAmount()); Batch 208: 1 band full-range -> 3 band (`CompressorCurve`)
            true, CompressorCurve.PRESENCE_BAND_COUNT, // post-EQ: Batch 210, presence vokal 2-4,5 kHz (lihat `CompressorCurve`); SEBELUMNYA "false, 0" (tidak dipakai)
            true      // limiter: tetap selalu dipakai (Batch 84), lepas dari status fallback EQ
        )

    /** Batch 235 (lanjutan B234, "Temuan terbuka" jendela konstruktor): `DynamicsProcessing(priority, 0, config)` menulis isi
     *  `config` ke mesin efek SAAT DIBUAT. Dengan `Config` bawaan (netral) lalu nilai tersimpan ditulis sesudahnya (B234), masih
     *  ada jendela milidetik: pada serah-terima efek objek baru memakai modul efek yang SAMA dgn objek lama (output sama), jadi
     *  tulisan netral konstruktor bisa mereset kompresor yang sedang dipakai. Kini `Config` dibangun SUDAH terisi nilai tersimpan
     *  (MBC 3 band + presence post-EQ + EQ cadangan + limiter), jadi tak ada tulisan netral sama sekali.
     *  Pembangunan `Config` terisi dibungkus try-catch: bila gagal (mis. tanda tangan API berbeda di sebuah device), jatuh ke
     *  `Config` bawaan — suara tetap benar lewat penulisan sesudah konstruktor di `attachDynamicsProcessing` (perilaku B234),
     *  bukan efek hilang. NOT VERIFIED di device (sandbox tanpa kompiler Kotlin/Android). */
    @android.annotation.SuppressLint("NewApi")
    private fun buildDynamicsConfig(
        needsEqFallback: Boolean,
        savedCompressor: Int,
        savedVocalFocus: Int
    ): DynamicsProcessing.Config {
        return try {
            val builder = newDynamicsConfigBuilder(needsEqFallback)
            val fraction = savedCompressor.coerceIn(0, 100) / 100f
            val focus = savedVocalFocus.coerceIn(0, 100) / 100f
            val mbc = DynamicsProcessing.Mbc(true, true, CompressorCurve.BAND_COUNT)
            for (bandIndex in 0 until CompressorCurve.BAND_COUNT) {
                mbc.setBand(bandIndex, compressorMbcBand(bandIndex, savedCompressor, fraction, focus))
            }
            builder.setMbcAllChannelsTo(mbc)
            val presence = DynamicsProcessing.Eq(true, true, CompressorCurve.PRESENCE_BAND_COUNT)
            for (presenceIndex in 0 until CompressorCurve.PRESENCE_BAND_COUNT) {
                presence.setBand(presenceIndex, presenceEqBand(presenceIndex, savedCompressor, fraction, focus))
            }
            builder.setPostEqAllChannelsTo(presence)
            if (needsEqFallback) {
                val preEq = DynamicsProcessing.Eq(true, true, FALLBACK_EQ_BANDS_HZ.size)
                FALLBACK_EQ_BANDS_HZ.forEachIndexed { index, freqHz ->
                    val savedGainDb = PrefsHelper.getEqualizerBandLevel(this, index, 0) / 100f
                    preEq.setBand(index, DynamicsProcessing.EqBand(true, freqHz, savedGainDb))
                }
                builder.setPreEqAllChannelsTo(preEq)
            }
            builder.setLimiterAllChannelsTo(masterLimiter())
            builder.build()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Config DynamicsProcessing terisi nilai tersimpan gagal dibangun; memakai Config bawaan (nilai ditulis sesudah objek dibuat)", e)
            newDynamicsConfigBuilder(needsEqFallback).build()
        }
    }

    // Batch 143 (lint NewApi, false positive): `dynamicsProcessing` dijamin null di API<28 (guard
    // SDK_INT<P di attachDynamicsProcessing) & akses via `?.` — argumen MbcBand() tidak dievaluasi.
    // Batch 209: isi 3 band dipisah dari setter publik supaya Compressor & Vocal Focus memakai 1 jalur.
    @android.annotation.SuppressLint("NewApi")
    // Batch 234: [target] eksplisit (bawaan = objek aktif) — dipakai `attachDynamicsProcessing()` untuk menulis nilai
    // tersimpan ke objek yang BARU dibuat selagi field `dynamicsProcessing` masih menunjuk objek lama (serah-terima efek).
    // Batch 235: konversi band lewat `compressorMbcBand`/`presenceEqBand` (juga dipakai `buildDynamicsConfig`).
    private fun applyCompressorBands(amount: Int, focusPercent: Int, target: DynamicsProcessing? = dynamicsProcessing) {
        val fraction = amount.coerceIn(0, 100) / 100f
        val focus = focusPercent.coerceIn(0, 100) / 100f
        try {
            // Batch 208: 3 band (bass / VOKAL / treble). Band tengah = kurva B198 persis; band luar
            // dapat makeup lebih kecil agar vokal menonjol dari backsound (lihat `CompressorCurve`).
            for (bandIndex in 0 until CompressorCurve.BAND_COUNT) {
                target?.setMbcBandAllChannelsTo(bandIndex, compressorMbcBand(bandIndex, amount, fraction, focus))
            }
            for (presenceIndex in 0 until CompressorCurve.PRESENCE_BAND_COUNT) {
                target?.setPostEqBandAllChannelsTo(presenceIndex, presenceEqBand(presenceIndex, amount, fraction, focus))
            }
        } catch (e: Exception) {
            dynamicsState = EffectState.FAILED; android.util.Log.e(TAG, "Gagal set Compressor MBC band", e)
        }
    }

    // Batch 143 (lint NewApi, false positive): cabang fallback cuma aktif kalau `equalizerFallbackActive`
    // (hanya true saat dynamicsProcessing berhasil dibuat = API>=28).
    @android.annotation.SuppressLint("NewApi")
    fun setEqualizerBand(band: Short, levelMb: Short) {
        markSettingAdjusted()
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
            // Batch 124: channel kedua khusus notifikasi recovery watchdog (lihat
            // komentar CHANNEL_ID_RECOVERY) - dibuat di sini juga (bukan cuma lazy-create
            // di postRecoveryNotification()) supaya konsisten 1 tempat pembuatan channel,
            // sama pola seperti channel utama di atas.
            val recoveryChannel = NotificationChannel(
                CHANNEL_ID_RECOVERY, getString(R.string.notif_recovery_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.notif_recovery_channel_desc) }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(recoveryChannel)
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

/** Parameter 1 band MBC kompresor (hasil [CompressorCurve.band]). Murni data, tanpa dependency Android. */
internal data class CompressorBandSpec(
    val cutoffHz: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val ratio: Float,
    val thresholdDb: Float,
    val postGainDb: Float,
    val noiseGateDb: Float,
    val expanderRatio: Float
)

/**
 * Batch 208 (request user: "kompresor sudah bertenaga, tapi vokal manusia kurang kedengeran dibanding
 * backsound yang noisy banget"). Penyebab: kompresor B198 = 1 band full-range 20:1 / -36 dB / +24 dB. Satu
 * detektor untuk seluruh spektrum -> bass/noise backsound ikut menurunkan gain vokal, dan makeup +24 dB
 * mengangkat lantai noise sama besar dgn vokal (selisih vokal-vs-backsound habis).
 *
 * Perbaikan: 3 band. Band TENGAH (250-5000 Hz = fundamental + formant + presence vokal) memakai kurva B198
 * PERSIS (ratio/threshold/makeup/attack/release identik di semua posisi slider = kekuatan tidak berkurang),
 * tetapi kini detektornya tidak lagi terpicu bass. Band BAWAH (<=250 Hz, gemuruh/bass/thump) dan ATAS
 * (>5000 Hz, desis/cymbal) tetap dikompresi 20:1 namun makeup-nya lebih kecil (diatur Vocal Focus) -> vokal naik
 * relatif terhadap keduanya. Slider 0 = bypass (semua band nonaktif, nilai netral); interpolasi linear netral -> ujung,
 * sama bentuknya dgn B198.
 *
 * Batch 209: seberapa jauh makeup bass/treble ditarik di bawah band vokal kini diatur slider "Vocal Focus"
 * (0..100, default 50 = angka Batch 208: bass +16 / treble +18 / vokal +24 dB di Compressor 100%).
 *
 * Batch 210: + presence vokal (post-EQ 2-4,5 kHz, hingga +4 dB) dan expander/noise-gate band bass/treble
 * (ambang hingga -60 dB, rasio hingga 3:1), keduanya ikut slider Compressor & Vocal Focus.
 *
 * Tuning (ubah HANYA angka ini, bukan logika): jangkauan slider Vocal Focus = `FOCUS_DEPTH_DB` (bass 16,
 * treble 12; naikkan = efek slider lebih kuat); batas vokal/noise = `CUTOFFS_HZ` (250 / 5000).
 * NOT VERIFIED di device.
 */
internal object CompressorCurve {
    const val BAND_COUNT = 3

    // Nilai netral (slider 0%) = sama dgn B121/B198: attack 10 ms, release 150 ms, ratio 1:1, threshold -1 dB, makeup 0 dB.
    const val NEUTRAL_ATTACK_MS = 10f
    const val NEUTRAL_RELEASE_MS = 150f
    private const val NEUTRAL_RATIO = 1f
    private const val NEUTRAL_THRESHOLD_DB = -1f

    // Indeks band: 0 = bass/gemuruh, 1 = VOKAL, 2 = treble/desis. `cutoffFrequency` MbcBand = batas ATAS band
    // (harus naik); band terakhir 20000 Hz = nilai yang sudah terbukti diterima (B121, kompresor 1 band).
    private val CUTOFFS_HZ = floatArrayOf(250f, 5000f, 20000f)
    private val ATTACK_MAX_MS = floatArrayOf(10f, 3f, 2f)
    private val RELEASE_MAX_MS = floatArrayOf(250f, 100f, 80f)
    private val RATIO_MAX = floatArrayOf(20f, 20f, 20f)
    private val THRESHOLD_MAX_DB = floatArrayOf(-36f, -36f, -36f)
    private val POST_GAIN_MAX_DB = floatArrayOf(24f, 24f, 24f)

    // Batch 209 (slider "Vocal Focus"): makeup band bass/treble di 100% = POST_GAIN_MAX_DB - focus * FOCUS_DEPTH_DB.
    // Band vokal kedalaman 0 (TIDAK pernah dipengaruhi focus). focus 0.5 (default) = 24-8 = 16 dB bass,
    // 24-6 = 18 dB treble = angka Batch 208 persis; focus 0 = semua band +24; focus 1 = bass +8 / treble +12.
    private val FOCUS_DEPTH_DB = floatArrayOf(16f, 0f, 12f)
    const val DEFAULT_FOCUS = 0.5f

    // Batch 210 (polish, request user "1+2"): (2) noise-gate/expander ringan HANYA band bass & treble: di bawah
    // ambang (-60 dB di Compressor 100%, level INPUT band, sebelum makeup) sinyal ditarik turun lebih jauh
    // dgn rasio 1 + compressor * focus * EXPANDER_EXTRA (default: 2:1; focus 0 = mati) -> lantai noise yang
    // terangkat makeup ikut diredam. Band VOKAL TIDAK PERNAH di-gate (ambang -90 dB, rasio 1:1 = kondisi B198)
    // supaya vokal pelan tidak terpotong.
    private const val NEUTRAL_GATE_DB = -90f
    private val GATE_THRESHOLD_MAX_DB = floatArrayOf(-60f, -90f, -60f)
    private val EXPANDER_EXTRA = floatArrayOf(2f, 0f, 2f)

    // Batch 210: (1) presence vokal = post-EQ 3 band (<2000 Hz netral / 2000-4500 Hz naik / >4500 Hz netral);
    // gain = compressor * focus * PRESENCE_MAX_DB (default 100% & focus 50 = +2 dB, focus 100 = +4 dB, focus 0 = 0).
    const val PRESENCE_BAND_COUNT = 3
    private val PRESENCE_CUTOFFS_HZ = floatArrayOf(2000f, 4500f, 20000f)
    private const val PRESENCE_MAX_DB = 4f

    /** Batas ATAS band post-EQ presence [index] (0 until [PRESENCE_BAND_COUNT]) dalam Hz; menaik. */
    fun presenceCutoffHz(index: Int): Float {
        require(index in 0 until PRESENCE_BAND_COUNT) { "band presence $index di luar 0..${PRESENCE_BAND_COUNT - 1}" }
        return PRESENCE_CUTOFFS_HZ[index]
    }

    /** Gain dB band post-EQ presence [index]: hanya band tengah (indeks 1) naik, sisanya 0 dB. */
    fun presenceGainDb(index: Int, fraction: Float, focus: Float = DEFAULT_FOCUS): Float {
        require(index in 0 until PRESENCE_BAND_COUNT) { "band presence $index di luar 0..${PRESENCE_BAND_COUNT - 1}" }
        if (index != 1) return 0f
        return fraction.coerceIn(0f, 1f) * focus.coerceIn(0f, 1f) * PRESENCE_MAX_DB
    }

    /** Parameter band [index] (0 until [BAND_COUNT]) pada posisi slider Compressor [fraction] dan Vocal Focus
     *  [focus] (keduanya 0..1, di luar rentang dipotong). */
    fun band(index: Int, fraction: Float, focus: Float = DEFAULT_FOCUS): CompressorBandSpec {
        require(index in 0 until BAND_COUNT) { "band $index di luar 0..${BAND_COUNT - 1}" }
        val f = fraction.coerceIn(0f, 1f)
        val g = focus.coerceIn(0f, 1f)
        return CompressorBandSpec(
            cutoffHz = CUTOFFS_HZ[index],
            attackMs = NEUTRAL_ATTACK_MS + f * (ATTACK_MAX_MS[index] - NEUTRAL_ATTACK_MS),
            releaseMs = NEUTRAL_RELEASE_MS + f * (RELEASE_MAX_MS[index] - NEUTRAL_RELEASE_MS),
            ratio = NEUTRAL_RATIO + f * (RATIO_MAX[index] - NEUTRAL_RATIO),
            thresholdDb = NEUTRAL_THRESHOLD_DB + f * (THRESHOLD_MAX_DB[index] - NEUTRAL_THRESHOLD_DB),
            postGainDb = f * (POST_GAIN_MAX_DB[index] - g * FOCUS_DEPTH_DB[index]),
            noiseGateDb = NEUTRAL_GATE_DB + f * (GATE_THRESHOLD_MAX_DB[index] - NEUTRAL_GATE_DB),
            expanderRatio = 1f + f * g * EXPANDER_EXTRA[index]
        )
    }
}

/**
 * Batch 229 — aturan murni (tanpa dependency Android, ada tesnya) untuk Effect Guard di [AudioEnhancerService]:
 * debounce/jadwal pemeriksaan, backoff rebut-ulang kontrol efek, pembatas intervensi per jendela waktu, toleransi
 * pembulatan device saat membandingkan parameter aktif vs tersimpan, dan "tanda tangan" pemutaran sistem.
 * Tuning (ubah HANYA angka di sini): `REASSERT_MAX_PER_WINDOW`/`REASSERT_WINDOW_MS` = seberapa sering guard boleh
 * memulihkan (turunkan bila terdengar adu tarik dgn aplikasi lain); `STRENGTH_TOLERANCE`/`EQ_TOLERANCE_MB` = seberapa
 * besar selisih dianggap "menyimpang" (naikkan bila guard memulihkan terus-menerus karena pembulatan device).
 * NOT VERIFIED di device.
 */
internal object EffectGuardPolicy {
    /** Penanda "belum pernah" untuk stempel waktu `elapsedRealtime` (nilai sah selalu >= 0). */
    const val NEVER = -1L
    const val DEBOUNCE_MS = 400L
    const val SETTLE_RECHECK_MS = 2_500L
    const val MIN_REACQUIRE_INTERVAL_MS = 5_000L
    const val MAX_REACQUIRE_INTERVAL_MS = 120_000L
    const val REASSERT_WINDOW_MS = 30_000L
    const val REASSERT_MAX_PER_WINDOW = 6
    const val STRENGTH_TOLERANCE = 60
    const val EQ_TOLERANCE_MB = 100
    const val LOUDNESS_TOLERANCE_MB = 1f
    const val USER_ADJUST_QUIET_MS = 1_500L
    /** Batch 231: priority kontrol efek session-0 (Bass, Virtualizer, Equalizer, DynamicsProcessing). Normal = 0 dan
     *  umumnya dipakai sistem/app lain, jadi nilai positif besar memenangkan arbitrase saat efek dibuat atau direbut ulang.
     *  Naikkan lagi bila log masih `control-lost`. Tidak berlaku untuk LoudnessEnhancer (API publik tanpa parameter priority). */
    const val EFFECT_PRIORITY = 10_000
    /** Batch 231: jarak pemeriksaan berkala selama ada pemutar media aktif. */
    const val GUARD_TICK_MS = 15_000L
    /** Batch 231: alasan pemeriksaan berkala; hasil "sehat" tidak di-log (hindari banjir log). */
    const val TICK_REASON = "tick"
    /** Batch 230: jeda setelah pemutar media baru terdeteksi sebelum efek dipasang ulang (sistem selesai memilih output).
     *  Batch 237: kini jeda tahap PENEGASAN (serah-terima kedua, jaring pengaman); tahap pertama = [REBIND_FAST_DELAY_MS].
     *  JANGAN dihapus/dipendekkan tanpa data device: inilah jalur yang terbukti mengatasi "mendem" (B233). */
    const val REBIND_DELAY_MS = 700L
    /** Batch 237: jeda tahap CEPAT serah-terima setelah pemutar media berubah. Callback pemutaran datang SETELAH pemutar
     *  mulai (output sudah dipilih sistem), jadi efek bisa langsung diikutkan; tahap penegasan ([REBIND_DELAY_MS]) menutup
     *  kemungkinan output belum final. Serah-terima make-before-break, jadi dua kali tak menimbulkan celah. Turunkan HANYA
     *  bila log `serah-terima N ms, sejak perubahan pemutar M ms` menunjukkan M masih terlalu besar; naikkan bila efek
     *  di video baru sering tak terdengar sesudah tahap cepat. */
    const val REBIND_FAST_DELAY_MS = 200L
    /** Batch 237: jeda minimum antara pemasangan terakhir dan serah-terima tahap PENEGASAN (hanya mencegah dua serah-terima
     *  saling menumpuk di main thread; jauh lebih pendek dari [REBIND_MIN_INTERVAL_MS] agar jalur 700 ms tetap tepat waktu). */
    const val REBIND_SETTLE_MIN_GAP_MS = 150L
    /** Batch 238: jeda minimum antara pemasangan terakhir (tahap APA PUN) dan serah-terima tahap CEPAT. Syarat (dijaga tes):
     *  [REBIND_FAST_DELAY_MS] + nilai ini <= [REBIND_DELAY_MS], sehingga tahap cepat TIDAK pernah tertahan melewati tahap
     *  penegasan perpindahan yang sama (dengan 2 dtk, [REBIND_MIN_INTERVAL_MS], geser video tiap 1-2 dtk membuat tahap cepat
     *  selalu telat dan efek baru baru terdengar di +700 ms). Make-before-break = tak ada kedip, jadi jeda ini hanya
     *  pembatas beban binder; naikkan HANYA bila log `ditunda` + `serah-terima N ms` menunjukkan main thread kewalahan. */
    const val REBIND_FAST_MIN_GAP_MS = 500L
    /** Batch 238: TIDAK lagi dipakai jalur serah-terima ([REBIND_FAST_MIN_GAP_MS] untuk tahap cepat, [REBIND_SETTLE_MIN_GAP_MS] untuk
     *  penegasan); dipertahankan sebagai batas atas historis + nilai bawaan [rebindWaitMs]/[mayRebind].
     *  Batch 230: jarak minimum antar pemasangan efek (awal/ulang). Batch 232: 30 dtk -> 5 dtk DAN pasang-ulang yang
     *  tertahan jeda ini sekarang DITUNDA sampai jeda habis (bukan dibuang). Alasan: di aplikasi sosmed pemutar
     *  datang-pergi dalam hitungan detik; dengan jeda 30 dtk yang membuang, pasang-ulang hanya terjadi untuk pemutar
     *  PERTAMA lalu efek berpindah output lagi dan tak pernah dipasang ulang (gejala: efek terdengar sepersekian
     *  detik lalu "mendem"). Naikkan bila terdengar kedip tiap geser video.
     *  Batch 234: 5 dtk -> 2 dtk. Angka 5 dtk dipilih saat pasang-ulang masih punya celah (B230-B232: lepas dulu, baru
     *  pasang) sehingga sering-sering = kedip; sejak serah-terima tanpa celah (B233) tahanan ini hanya soal biaya CPU/binder,
     *  sementara 5 dtk membiarkan output baru tanpa efek sampai 5 dtk penuh di perpindahan beruntun. */
    const val REBIND_MIN_INTERVAL_MS = 2_000L

    private const val MAX_BACKOFF_SHIFT = 6

    data class ReassertDecision(val allowed: Boolean, val windowStartMs: Long, val count: Int)

    /** Jeda minimum sebelum rebut-ulang kontrol boleh diulang: 5 dtk, lalu berlipat tiap kegagalan beruntun, maks 120 dtk. */
    fun reacquireIntervalMs(consecutiveFailures: Int): Long =
        (MIN_REACQUIRE_INTERVAL_MS shl consecutiveFailures.coerceIn(0, MAX_BACKOFF_SHIFT))
            .coerceAtMost(MAX_REACQUIRE_INTERVAL_MS)

    fun mayReacquire(nowMs: Long, lastAttemptMs: Long, consecutiveFailures: Int): Boolean =
        lastAttemptMs == NEVER || nowMs - lastAttemptMs >= reacquireIntervalMs(consecutiveFailures)

    /** Jendela geser sederhana: maks [REASSERT_MAX_PER_WINDOW] intervensi per [REASSERT_WINDOW_MS]. */
    fun reassertDecision(nowMs: Long, windowStartMs: Long, count: Int): ReassertDecision {
        val expired = windowStartMs == NEVER || nowMs - windowStartMs >= REASSERT_WINDOW_MS
        val start = if (expired) nowMs else windowStartMs
        val used = if (expired) 0 else count
        return if (used >= REASSERT_MAX_PER_WINDOW) {
            ReassertDecision(allowed = false, windowStartMs = start, count = used)
        } else {
            ReassertDecision(allowed = true, windowStartMs = start, count = used + 1)
        }
    }

    /** true selama [USER_ADJUST_QUIET_MS] sesudah setter publik terakhir (user/preset/restore sedang mengubah nilai). */
    fun isQuietPeriod(nowMs: Long, lastAdjustMs: Long): Boolean =
        lastAdjustMs != NEVER && nowMs - lastAdjustMs < USER_ADJUST_QUIET_MS

    fun strengthDiffers(savedPerMille: Int, actualPerMille: Int): Boolean =
        kotlin.math.abs(savedPerMille - actualPerMille) > STRENGTH_TOLERANCE

    fun eqLevelDiffers(savedMb: Int, actualMb: Int): Boolean =
        kotlin.math.abs(savedMb - actualMb) > EQ_TOLERANCE_MB

    fun loudnessDiffers(savedMb: Float, actualMb: Float): Boolean =
        kotlin.math.abs(savedMb - actualMb) > LOUDNESS_TOLERANCE_MB

    /** Batch 230: usage yang memicu pasang-ulang = UNKNOWN (0) dan MEDIA (1). GAME/notifikasi/dering/alarm TIDAK
     *  (suara pendek sering datang-pergi; memasang ulang tiap kali = kedip tanpa guna). */
    private val REBIND_USAGES = setOf(0, 1)

    /** Batch 237: true bila [usage] termasuk pemutar media yang memicu pasang-ulang (UNKNOWN/MEDIA). */
    fun isRebindUsage(usage: Int): Boolean = usage in REBIND_USAGES

    /** Batch 237: kunci identitas himpunan pemutar media aktif (urut supaya stabil; kosong = tak ada pemutar media).
     *  Melengkapi tanda tangan usage/contentType: dua pemutar BERBEDA dengan atribut sama (video lama diganti video baru
     *  dalam satu callback) bertanda tangan identik sehingga tak terlihat oleh [shouldRebindOnPlayback]. */
    fun playerKey(mediaPlayerHashes: List<Int>): String = mediaPlayerHashes.sorted().joinToString(",")

    /** Batch 237: true bila himpunan pemutar media berganti identitas selagi masih ada pemutar media (berhenti total =
     *  false, sama seperti [shouldRebindOnPlayback]). */
    fun shouldRebindOnPlayers(previousKey: String, newKey: String): Boolean =
        newKey.isNotEmpty() && newKey != previousKey

    private fun mediaEntryCounts(signature: String): Map<String, Int> {
        if (signature.isEmpty()) return emptyMap()
        return signature.split(',')
            .filter { entry ->
                val usage = entry.substringBefore('/').toIntOrNull()
                usage != null && usage in REBIND_USAGES
            }
            .groupingBy { it }
            .eachCount()
    }

    /** Batch 230: true bila ADA pemutar media baru — mis. YouTube mulai, baik saat musik sudah berhenti maupun masih
     *  tercatat. Batch 232: diperluas ke SETIAP perubahan himpunan pemutar media (bertambah ATAU berkurang) selama
     *  masih ada pemutar media sesudahnya. Alasan: sistem memilih ulang output efek global juga saat pemutar LAMA
     *  berhenti (mis. video berikutnya sudah mulai, video sebelumnya baru berhenti sedetik kemudian), dan versi
     *  "hanya bertambah" tak pernah memasang ulang pada langkah itu. Berhenti total (tak ada media tersisa) dan
     *  perubahan non-media (notifikasi/dering/alarm/game) tetap false. */
    fun shouldRebindOnPlayback(previousSignature: String, newSignature: String): Boolean {
        val after = mediaEntryCounts(newSignature)
        if (after.isEmpty()) return false
        return after != mediaEntryCounts(previousSignature)
    }

    /** Batch 232: sisa jeda (ms) sebelum pasang-ulang boleh jalan lagi; 0 = boleh sekarang. Dipakai untuk MENUNDA
     *  (bukan membuang) pasang-ulang yang tiba di dalam [REBIND_MIN_INTERVAL_MS] sejak pemasangan terakhir. */
    fun rebindWaitMs(nowMs: Long, lastBindMs: Long, minIntervalMs: Long = REBIND_MIN_INTERVAL_MS): Long =
        if (lastBindMs == NEVER) 0L else (minIntervalMs - (nowMs - lastBindMs)).coerceAtLeast(0L)

    /** Batch 231: true bila tanda tangan pemutaran memuat minimal satu pemutar media (usage UNKNOWN/MEDIA). */
    fun hasMediaPlayer(signature: String): Boolean = mediaEntryCounts(signature).isNotEmpty()

    /** Batch 230: boleh pasang ulang bila belum pernah memasang, atau sudah >= [REBIND_MIN_INTERVAL_MS] sejak terakhir. */
    fun mayRebind(nowMs: Long, lastBindMs: Long): Boolean =
        lastBindMs == NEVER || nowMs - lastBindMs >= REBIND_MIN_INTERVAL_MS

    /** Batch 233: hasil serah-terima satu efek. ADOPT_NEW = pakai instance baru; KEEP_OLD = pembuatan baru gagal tetapi
     *  efek lama masih ada, jadi lama dipertahankan; NONE = tak ada efek sama sekali (tak didukung device). */
    enum class HandoffOutcome { ADOPT_NEW, KEEP_OLD, NONE }

    fun handoffOutcome(hadOld: Boolean, createdNew: Boolean): HandoffOutcome = when {
        createdNew -> HandoffOutcome.ADOPT_NEW
        hadOld -> HandoffOutcome.KEEP_OLD
        else -> HandoffOutcome.NONE
    }

    /** Urutan pemutar tidak berarti: diurutkan supaya tanda tangan stabil. Kosong = tidak ada pemutaran aktif. */
    fun playbackSignature(usageAndContentType: List<Pair<Int, Int>>): String =
        usageAndContentType.map { "${it.first}/${it.second}" }.sorted().joinToString(",")
}
