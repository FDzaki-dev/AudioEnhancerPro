package com.audioenhancer.booster

import android.content.Context

/** Penyimpanan kecil untuk status onboarding & preferensi ringan lain. */
object PrefsHelper {
    private const val PREFS_NAME = "audio_enhancer_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_BASS = "bass_strength"
    private const val KEY_VIRTUALIZER = "virtualizer_strength"
    private const val KEY_LOUDNESS = "loudness_gain"
    private const val KEY_ACTIVE_PRESET = "active_preset"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "use_dynamic_color"
    private const val KEY_CUSTOM_PRESETS = "custom_presets_json"
    private const val KEY_LAST_SEEN_CRASH = "last_seen_crash_ts"
    private const val KEY_USER_WANTS_RUNNING = "user_wants_running"

    /** 0 = ikut sistem, 1 = terang, 2 = gelap. */
    const val THEME_MODE_SYSTEM = 0
    const val THEME_MODE_LIGHT = 1
    const val THEME_MODE_DARK = 2

    fun isOnboardingDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingDone(context: Context, done: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    // --- Pengaturan efek audio: disimpan supaya tidak reset saat app ditutup / HP reboot ---
    fun getBass(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_BASS, 500)

    fun setBass(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_BASS, value).apply()
    }

    fun getVirtualizer(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_VIRTUALIZER, 500)

    fun setVirtualizer(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_VIRTUALIZER, value).apply()
    }

    fun getLoudness(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_LOUDNESS, 0f)

    fun setLoudness(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY_LOUDNESS, value).apply()
    }

    // --- Preset aktif: supaya chip preset yang terpilih tidak hilang saat app dibuka ulang ---
    fun getActivePreset(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACTIVE_PRESET, null)

    fun setActivePreset(context: Context, label: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE_PRESET, label).apply()
    }

    // --- Mode tema manual: override system theme kalau user memilih terang/gelap secara eksplisit ---
    fun getThemeMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME_MODE, THEME_MODE_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    // --- Equalizer per-band: tiap pita frekuensi disimpan terpisah, dipulihkan tiap service dibuat ulang ---
    fun getEqualizerBandLevel(context: Context, band: Int, default: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("eq_band_$band", default)

    fun setEqualizerBandLevel(context: Context, band: Int, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt("eq_band_$band", value).apply()
    }

    // --- Dynamic color (Material You): opt-in, default false supaya identitas visual app terjaga ---
    fun getUseDynamicColor(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setUseDynamicColor(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    /** Preset yang disimpan user sendiri (di luar 4 preset bawaan) — nama harus unik,
     *  simpan ulang dengan nama sama akan menimpa yang lama. */
    data class CustomPreset(val name: String, val bass: Float, val virtualizer: Float, val loudness: Float)

    fun getCustomPresets(context: Context): List<CustomPreset> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CustomPreset(
                    obj.getString("name"),
                    obj.getDouble("bass").toFloat(),
                    obj.getDouble("virtualizer").toFloat(),
                    obj.getDouble("loudness").toFloat()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addCustomPreset(context: Context, preset: CustomPreset) {
        val current = getCustomPresets(context).toMutableList()
        current.removeAll { it.name == preset.name } // nama sama -> timpa, bukan duplikat
        current.add(preset)
        saveCustomPresets(context, current)
    }

    fun deleteCustomPreset(context: Context, name: String) {
        saveCustomPresets(context, getCustomPresets(context).filterNot { it.name == name })
    }

    private fun saveCustomPresets(context: Context, list: List<CustomPreset>) {
        val arr = org.json.JSONArray()
        list.forEach { p ->
            val obj = org.json.JSONObject()
            obj.put("name", p.name)
            obj.put("bass", p.bass.toDouble())
            obj.put("virtualizer", p.virtualizer.toDouble())
            obj.put("loudness", p.loudness.toDouble())
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CUSTOM_PRESETS, arr.toString()).apply()
    }

    // --- Crash log: timestamp file crash terakhir yang SUDAH dilihat user, biar banner
    // "sempat crash" cuma nongol sekali per insiden, bukan tiap kali app dibuka ---
    fun getLastSeenCrashTimestamp(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_SEEN_CRASH, 0L)

    fun setLastSeenCrashTimestamp(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SEEN_CRASH, timestamp).apply()
    }

    // --- Batch 9: "niat" user (mau service hidup atau tidak), TERPISAH dari
    // AudioEnhancerService.isRunning yang cuma state runtime in-memory (reset ke false
    // kalau process app mati). Dipakai ServiceWatchdogWorker buat bedakan 2 skenario beda:
    // (a) user SENGAJA tekan "Matikan" -> jangan dihidupkan paksa lagi oleh watchdog.
    // (b) OS/OEM battery-killer yang bunuh paksa TANPA sepengetahuan user -> watchdog
    // WAJIB hidupkan lagi, karena ini bukan pilihan user. Default true karena app ini
    // memang didesain "always on" (lihat README) — pertama kali dipasang dianggap user
    // mau langsung aktif, bukan mau mati duluan.
    fun getUserWantsRunning(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_USER_WANTS_RUNNING, true)

    fun setUserWantsRunning(context: Context, wantsRunning: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_USER_WANTS_RUNNING, wantsRunning).apply()
    }
}
