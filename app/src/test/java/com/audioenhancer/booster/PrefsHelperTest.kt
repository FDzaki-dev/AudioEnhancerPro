package com.audioenhancer.booster

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit test pertama untuk PrefsHelper. Sebelumnya semua verifikasi persistensi
 * (bass/virtualizer/loudness/preset/tema/equalizer) hanya dicek manual lewat
 * install-and-poke, tidak ada automated test sama sekali.
 *
 * Pakai Robolectric karena PrefsHelper bergantung ke android.content.Context
 * (SharedPreferences) — bukan pure-Kotlin, jadi tidak bisa dites dengan JUnit polos.
 */
@RunWith(RobolectricTestRunner::class)
class PrefsHelperTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("audio_enhancer_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `bass strength round-trips through prefs`() {
        PrefsHelper.setBass(context, 750)
        assertEquals(750, PrefsHelper.getBass(context))
    }

    @Test
    fun `virtualizer strength round-trips through prefs`() {
        PrefsHelper.setVirtualizer(context, 300)
        assertEquals(300, PrefsHelper.getVirtualizer(context))
    }

    @Test
    fun `loudness gain round-trips through prefs`() {
        PrefsHelper.setLoudness(context, 1234.5f)
        assertEquals(1234.5f, PrefsHelper.getLoudness(context), 0.001f)
    }

    @Test
    fun `active preset defaults to null when never set`() {
        assertNull(PrefsHelper.getActivePreset(context))
    }

    @Test
    fun `active preset round-trips and can be cleared`() {
        PrefsHelper.setActivePreset(context, "Bass Heavy")
        assertEquals("Bass Heavy", PrefsHelper.getActivePreset(context))

        PrefsHelper.setActivePreset(context, null)
        assertNull(PrefsHelper.getActivePreset(context))
    }

    @Test
    fun `theme mode defaults to system`() {
        assertEquals(PrefsHelper.THEME_MODE_SYSTEM, PrefsHelper.getThemeMode(context))
    }

    @Test
    fun `theme mode round-trips through prefs`() {
        PrefsHelper.setThemeMode(context, PrefsHelper.THEME_MODE_DARK)
        assertEquals(PrefsHelper.THEME_MODE_DARK, PrefsHelper.getThemeMode(context))
    }

    @Test
    fun `equalizer band levels are stored independently per band`() {
        PrefsHelper.setEqualizerBandLevel(context, 0, -300)
        PrefsHelper.setEqualizerBandLevel(context, 1, 450)

        assertEquals(-300, PrefsHelper.getEqualizerBandLevel(context, 0, 0))
        assertEquals(450, PrefsHelper.getEqualizerBandLevel(context, 1, 0))
        // Band yang belum pernah di-set harus tetap balik ke default, bukan ikut band lain.
        assertEquals(0, PrefsHelper.getEqualizerBandLevel(context, 2, 0))
    }
}
