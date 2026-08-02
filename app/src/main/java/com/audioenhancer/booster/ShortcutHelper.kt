package com.audioenhancer.booster

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * App Shortcuts (long-press ikon launcher). Ada 2 jenis di sini:
 * - 1 shortcut STATIS ("Nyalakan/Matikan") — dideklarasikan lewat res/xml/shortcuts.xml,
 *   gak perlu di-publish manual dari kode, otomatis kebaca sistem dari manifest.
 * - Shortcut DINAMIS, satu per preset custom user — WAJIB di-refresh manual tiap kali
 *   preset custom ditambah/diedit/dihapus, kalau tidak shortcut bisa nunjuk ke preset
 *   yang udah gak ada lagi (tap -> gak ada efek karena nama preset gak ketemu).
 *
 * Kenapa cuma preset CUSTOM (bukan 4 preset bawaan) yang jadi dynamic shortcut: preset
 * bawaan nilainya tetap & gampang diakses dari dalam app, sedangkan preset custom itu
 * yang paling sering "kelupaan ada" oleh user — jadi paling worth buat akses instan.
 */
object ShortcutHelper {
    const val EXTRA_ACTION = "com.audioenhancer.booster.SHORTCUT_ACTION"
    const val EXTRA_CUSTOM_PRESET_NAME = "com.audioenhancer.booster.SHORTCUT_CUSTOM_PRESET"
    const val ACTION_TOGGLE = "toggle"

    // Sisa slot dynamic yang aman: kebanyakan launcher/OEM menjamin minimal 4 total
    // shortcut (statis + dinamis) per app. 1 slot statis (toggle) sudah dipakai,
    // jadi sisain 3 buat preset custom supaya gak ada yang di-drop diam-diam.
    private const val MAX_DYNAMIC = 3

    /** Preset custom terbaru yang ditampilkan duluan (paling gampang dijangkau setelah baru disimpan). */
    fun refreshCustomPresetShortcuts(context: Context) {
        val presetsToShow = PrefsHelper.getCustomPresets(context).takeLast(MAX_DYNAMIC).reversed()

        if (presetsToShow.isEmpty()) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            return
        }

        val icon = IconCompat.createWithResource(context, R.drawable.ic_shortcut_preset)
        val shortcuts = presetsToShow.map { preset ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW // sekadar beda dari ACTION_MAIN default, intent-nya eksplisit jadi tetap kekirim ke MainActivity
                putExtra(EXTRA_CUSTOM_PRESET_NAME, preset.name)
            }
            ShortcutInfoCompat.Builder(context, "custom_preset_${preset.name}")
                .setShortLabel(preset.name)
                .setLongLabel(preset.name)
                .setIcon(icon)
                .setIntent(intent)
                .build()
        }
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
