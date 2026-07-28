package com.audioenhancer.booster

import android.content.Context

/** Penyimpanan kecil untuk status onboarding & preferensi ringan lain. */
object PrefsHelper {
    private const val PREFS_NAME = "audio_enhancer_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_BASS = "bass_strength"
    private const val KEY_VIRTUALIZER = "virtualizer_strength"
    private const val KEY_LOUDNESS = "loudness_gain"

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
}
