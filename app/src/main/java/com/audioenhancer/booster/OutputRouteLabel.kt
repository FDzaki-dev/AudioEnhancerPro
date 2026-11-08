package com.audioenhancer.booster

import android.media.AudioDeviceInfo

/**
 * Batch 290: nama sink output yang MUNCUL ke pengguna (banner "Output audio berubah").
 *
 * Sebelumnya `AudioEnhancerService` menulis SEMUA sink yang dilaporkan callback ke banner, dengan fallback
 * "device tipe N" untuk tipe di luar daftar — di HP user jadi "device tipe 1, Speaker internal, device tipe 18,
 * device tipe 14", padahal earpiece (1), telephony (18) dan FM (14) bukan tujuan audio yang bisa dipilih pengguna.
 * Kini: tipe yang bermakna bagi pengguna punya nama (satu sumber kebenaran, dipakai juga Log lewat
 * `AudioEnhancerService.describeOutputDeviceType`), tipe lain = `null` (tidak ditampilkan di banner; tetap dicatat
 * mentah di Log). Murni (tanpa Context) supaya bisa dites tanpa Robolectric; konstanta `AudioDeviceInfo.TYPE_*`
 * adalah `static final int` Java sehingga di-inline kompilator.
 */
internal object OutputRouteLabel {

    /** Nama ramah untuk [type], atau `null` kalau bukan sink yang relevan bagi pengguna. */
    fun userFacingName(type: Int): String? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Speaker internal"
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
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line out digital"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Aux"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Alat bantu dengar"
        else -> null // earpiece (1), telephony (18), FM (14), dll: bukan tujuan yang dipilih pengguna
    }

    /** Deskripsi untuk banner: nama-nama ramah dari [types], tanpa duplikat, dipisah koma; `null` kalau tak ada satu pun. */
    fun describe(types: List<Int>): String? =
        types.mapNotNull { userFacingName(it) }.distinct().takeIf { it.isNotEmpty() }?.joinToString()
}
