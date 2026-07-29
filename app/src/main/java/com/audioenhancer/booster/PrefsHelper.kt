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
}
