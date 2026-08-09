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

    @Test
    fun `dynamic color defaults to off`() {
        assertEquals(false, PrefsHelper.getUseDynamicColor(context))
    }

    @Test
    fun `dynamic color round-trips through prefs`() {
        PrefsHelper.setUseDynamicColor(context, true)
        assertEquals(true, PrefsHelper.getUseDynamicColor(context))

        PrefsHelper.setUseDynamicColor(context, false)
        assertEquals(false, PrefsHelper.getUseDynamicColor(context))
    }

    @Test
    fun `custom presets are empty when never saved`() {
        assertEquals(emptyList<PrefsHelper.CustomPreset>(), PrefsHelper.getCustomPresets(context))
    }

    @Test
    fun `custom preset round-trips through JSON serialization`() {
        val preset = PrefsHelper.CustomPreset(name = "Malam Hari", bass = 700f, virtualizer = 250f, loudness = 1500.5f)
        PrefsHelper.addCustomPreset(context, preset)

        val loaded = PrefsHelper.getCustomPresets(context)
        assertEquals(1, loaded.size)
        assertEquals(preset.name, loaded[0].name)
        assertEquals(preset.bass, loaded[0].bass, 0.001f)
        assertEquals(preset.virtualizer, loaded[0].virtualizer, 0.001f)
        assertEquals(preset.loudness, loaded[0].loudness, 0.001f)
    }

    @Test
    fun `multiple custom presets are stored independently in the JSON array`() {
        PrefsHelper.addCustomPreset(context, PrefsHelper.CustomPreset("Preset A", 100f, 100f, 100f))
        PrefsHelper.addCustomPreset(context, PrefsHelper.CustomPreset("Preset B", 200f, 200f, 200f))

        val loaded = PrefsHelper.getCustomPresets(context)
        assertEquals(2, loaded.size)
        assertEquals(setOf("Preset A", "Preset B"), loaded.map { it.name }.toSet())
    }

    @Test
    fun `saving a custom preset with an existing name overwrites it instead of duplicating`() {
        PrefsHelper.addCustomPreset(context, PrefsHelper.CustomPreset("Favorit", 100f, 100f, 100f))
        PrefsHelper.addCustomPreset(context, PrefsHelper.CustomPreset("Favorit", 999f, 999f, 999f))

        val loaded = PrefsHelper.getCustomPresets(context)
        assertEquals(1, loaded.size)
        assertEquals(999f, loaded[0].bass, 0.001f)
    }

    @Test
    fun `deleting a custom preset removes only that preset by name`() {
        PrefsHelper.addCustomPreset(context, PrefsHelper.CustomPreset("Simpan", 100f, 100f, 100f))
        PrefsHelper.addCustomPreset(context, PrefsHelper.CustomPreset("Hapus", 200f, 200f, 200f))

        PrefsHelper.deleteCustomPreset(context, "Hapus")

        val loaded = PrefsHelper.getCustomPresets(context)
        assertEquals(1, loaded.size)
        assertEquals("Simpan", loaded[0].name)
    }

    @Test
    fun `last seen crash timestamp defaults to zero`() {
        assertEquals(0L, PrefsHelper.getLastSeenCrashTimestamp(context))
    }

    @Test
    fun `last seen crash timestamp round-trips through prefs`() {
        PrefsHelper.setLastSeenCrashTimestamp(context, 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, PrefsHelper.getLastSeenCrashTimestamp(context))
    }

    @Test
    fun `app theme style defaults to amoled glass`() {
        assertEquals(PrefsHelper.APP_THEME_AMOLED_GLASS, PrefsHelper.getAppThemeStyle(context))
    }

    @Test
    fun `app theme style round-trips through prefs`() {
        PrefsHelper.setAppThemeStyle(context, PrefsHelper.APP_THEME_RADICAL_SKEUO)
        assertEquals(PrefsHelper.APP_THEME_RADICAL_SKEUO, PrefsHelper.getAppThemeStyle(context))

        PrefsHelper.setAppThemeStyle(context, PrefsHelper.APP_THEME_AMOLED_GLASS)
        assertEquals(PrefsHelper.APP_THEME_AMOLED_GLASS, PrefsHelper.getAppThemeStyle(context))
    }
}
