package com.audioenhancer.booster

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Batch 134 (Fase 8 B, ROI #7 "Scheduler"): jadwal nyala/mati Boomly (harian; sejak Batch 207 hari bisa dipilih).
 *
 * Desain: rantai `OneTimeWork` tunggal (unique name [UNIQUE_WORK_NAME]) — selalu ada
 * PALING BANYAK 1 work pending untuk event terdekat (nyala atau mati). Tiap work selesai
 * mengeksekusi event-nya lalu memanggil [reschedule] untuk event berikutnya. WorkManager
 * menyimpan work lintas reboot/update app → 0 perubahan BootReceiver/Application.
 *
 * Aksi: nyala = `AudioEnhancerService.requestStart()` (sama seperti watchdog/BootReceiver),
 * mati = `requestStop()` (jalur ACTION_STOP yang SAMA dengan tombol "Matikan" → watchdog
 * TIDAK menghidupkan lagi). Sumber kebenaran jadwal = `PrefsHelper` (baca ulang tiap
 * reschedule; UI cuma menulis prefs lalu memanggil [reschedule]).
 *
 * Batasan jujur (NOT VERIFIED device):
 * (1) WorkManager tidak exact — di Doze/hemat daya jam bisa tertunda; event yang telat
 *     lebih dari [STALE_TOLERANCE_MS] DILEWATI (mis. HP mati semalaman → "mati jam 22:00"
 *     tidak boleh mematikan service yang baru dinyalakan pagi harinya).
 * (2) Android 12+: `requestStart()` dari background bisa diblokir sistem (pola yang SAMA
 *     dgn watchdog, Batch 124) — fallback: notifikasi "ketuk untuk menyalakan"
 *     (`PendingIntent.getForegroundService`, ketukan user = jalur resmi exempted).
 *     Exemption battery optimization membuat start otomatis tanpa sentuhan.
 * (3) Batch 207: hari tertentu SUDAH ada ([PrefsHelper.getScheduleDays], default semua hari = perilaku lama).
 *     Event NYALA terjadi hanya di hari terpilih; event MATI mengikuti hari event NYALA-nya (jendela
 *     lewat tengah malam, mis. 22:00-06:00: mati jam 06:00 hari berikutnya tetap milik jendela hari
 *     kemarin). Belum: event non-jam (butuh sentuh Service).
 * (4) Batch 151: preset per jadwal — saat event NYALA benar-benar menyalakan Service,
 *     preset pilihan user ([PrefsHelper.getSchedulePreset]) ditulis ke prefs dulu, lalu
 *     Service membacanya lewat jalur start normal. Kalau Service SUDAH hidup pada jam
 *     nyala, event dilewati seperti sebelumnya (preset TIDAK ditimpa ke efek yang jalan).
 */
class ScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            val event = inputData.getString(KEY_EVENT)
            val scheduledAt = inputData.getLong(KEY_SCHEDULED_AT, 0L)
            val fresh = scheduledAt > 0L && System.currentTimeMillis() - scheduledAt <= STALE_TOLERANCE_MS
            if (PrefsHelper.getScheduleEnabled(ctx) && fresh) {
                when (event) {
                    EVENT_START -> performStart(ctx)
                    EVENT_STOP -> performStop(ctx)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Eksekusi event jadwal gagal", e)
        }
        // Selalu lanjutkan rantai (juga saat event dilewati/gagal) supaya jadwal tidak putus.
        reschedule(ctx)
        return Result.success()
    }

    private fun performStart(ctx: Context) {
        if (AudioEnhancerService.isRunning) return
        applySchedulePresetIfAny(ctx)
        try {
            AudioEnhancerService.requestStart(ctx)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "requestStart() diblokir sistem - fallback notifikasi ketuk-untuk-nyalakan", e)
            postStartNotification(ctx)
        }
    }

    /** Batch 151: tulis preset jadwal ke prefs SEBELUM start (juga sebelum fallback notifikasi —
     *  ketukan user nanti menyalakan Service yang membaca prefs yang sama). Gagal/hilang =
     *  Log saja, start tetap jalan dengan setelan terakhir. */
    private fun applySchedulePresetIfAny(ctx: Context) {
        val presetName = PrefsHelper.getSchedulePreset(ctx) ?: return
        try {
            if (PrefsHelper.applyCustomPresetToPrefs(ctx, presetName)) {
                android.util.Log.i(TAG, "Jadwal nyala: preset '$presetName' diterapkan ke prefs")
            } else {
                android.util.Log.w(TAG, "Preset jadwal '$presetName' tidak ditemukan - pakai setelan terakhir")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal menerapkan preset jadwal", e)
        }
    }

    private fun performStop(ctx: Context) {
        if (!AudioEnhancerService.isRunning) return
        try {
            AudioEnhancerService.requestStop(ctx)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "requestStop() gagal", e)
        }
    }

    /** Notifikasi BIASA (bukan foreground) di channel sendiri — teks berbeda dari
     *  notifikasi recovery watchdog (itu bilang "sistem menghentikan", tidak akurat di sini). */
    // Batch 143 (lint MissingPermission, false positive): notify() compat tanpa POST_NOTIFICATIONS
    // di-drop diam-diam, + sudah dibungkus try-catch total.
    @android.annotation.SuppressLint("MissingPermission")
    private fun postStartNotification(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, ctx.getString(R.string.notif_schedule_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }
            val startIntent = Intent(ctx, AudioEnhancerService::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val tapPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(ctx, NOTIF_REQUEST_CODE, startIntent, flags)
            } else {
                PendingIntent.getService(ctx, NOTIF_REQUEST_CODE, startIntent, flags)
            }
            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(ctx.getString(R.string.notif_schedule_title))
                .setContentText(ctx.getString(R.string.notif_schedule_body))
                .setContentIntent(tapPending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID_SCHEDULE, notification)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Gagal kirim notifikasi jadwal", e)
        }
    }

    companion object {
        private const val TAG = "ScheduleWorker"
        private const val UNIQUE_WORK_NAME = "boomly_daily_schedule"
        private const val KEY_EVENT = "schedule_event"
        private const val KEY_SCHEDULED_AT = "schedule_at"
        private const val EVENT_START = "start"
        private const val EVENT_STOP = "stop"
        private const val STALE_TOLERANCE_MS = 60L * 60L * 1000L
        private const val CHANNEL_ID = "boomly_schedule_channel"
        private const val NOTIF_ID_SCHEDULE = 1003
        private const val NOTIF_REQUEST_CODE = 2
        // Batch 211: dipindah ke atas (sebelumnya di bawah `nextOccurrence`) -> detekt typed PropertyUsedBeforeDeclaration.
        private const val CANDIDATE_DAYS = 8
        /** Pasang/ganti work event terdekat sesuai prefs, atau batalkan kalau jadwal mati.
         *  Aman dipanggil berulang (REPLACE) — dipakai UI tiap toggle/jam berubah DAN
         *  `doWork()` untuk melanjutkan rantai. */
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!PrefsHelper.getScheduleEnabled(context)) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val now = System.currentTimeMillis()
            val days = PrefsHelper.getScheduleDays(context)
            val startMin = PrefsHelper.getScheduleStartMinutes(context)
            val stopMin = PrefsHelper.getScheduleStopMinutes(context)
            // Jendela lewat tengah malam (nyala > mati): event MATI jatuh di hari kalender berikutnya,
            // tapi hari aktifnya = hari event NYALA (kemarin) -> geser -1 hari saat cek hari.
            val stopDayShift = if (startMin > stopMin) -1 else 0
            val startAt = nextOccurrence(now, startMin, days)
            val stopAt = nextOccurrence(now, stopMin, days, stopDayShift)
            val event = if (startAt <= stopAt) EVENT_START else EVENT_STOP
            val at = if (startAt <= stopAt) startAt else stopAt
            val request = OneTimeWorkRequestBuilder<ScheduleWorker>()
                .setInitialDelay((at - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_EVENT, event)
                        .putLong(KEY_SCHEDULED_AT, at)
                        .build()
                )
                .build()
            wm.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Epoch-ms kemunculan berikutnya (STRIKT setelah [nowMs]) jam [minutesOfDay]
         *  (0..1439) di zona waktu lokal — `Calendar` menangani DST. Batch 207: hanya pada hari
         *  yang ada di [daysMask] (bit 0 = Minggu .. bit 6 = Sabtu; default semua hari = perilaku lama);
         *  hari dicek pada tanggal event digeser [dayShift] hari (-1 = event MATI jendela lewat tengah malam).
         *  Mask 0 diperlakukan semua hari. Maks 8 kandidat tanggal (hari ini + 7) cukup untuk 1 hari terpilih. */
        internal fun nextOccurrence(
            nowMs: Long,
            minutesOfDay: Int,
            daysMask: Int = PrefsHelper.SCHEDULE_ALL_DAYS,
            dayShift: Int = 0
        ): Long {
            val mask = (daysMask and PrefsHelper.SCHEDULE_ALL_DAYS).let { if (it == 0) PrefsHelper.SCHEDULE_ALL_DAYS else it }
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
                set(Calendar.MINUTE, minutesOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            repeat(CANDIDATE_DAYS) {
                if (cal.timeInMillis > nowMs && isDayEnabled(cal, mask, dayShift)) return cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis // tak tercapai: mask != 0 menjamin ada hari cocok dalam 8 kandidat
        }

        private fun isDayEnabled(eventTime: Calendar, mask: Int, dayShift: Int): Boolean {
            val shifted = (eventTime.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayShift) }
            val bit = shifted.get(Calendar.DAY_OF_WEEK) - 1 // Minggu = 0 .. Sabtu = 6
            return ((mask shr bit) and 1) == 1
        }
    }
}
