package com.audioenhancer.booster

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Batch 290: nama sink output untuk banner "Output audio berubah" — fungsi murni, tanpa Robolectric. */
class OutputRouteLabelTest {

    @Test
    fun `tipe yang bermakna bagi pengguna punya nama`() {
        assertEquals("Speaker internal", OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals("Bluetooth A2DP", OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals("Headset kabel", OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals("USB DAC/device", OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_USB_DEVICE))
    }

    @Test
    fun `earpiece telephony dan FM bukan tujuan pengguna`() {
        assertNull(OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_TELEPHONY))
        assertNull(OutputRouteLabel.userFacingName(AudioDeviceInfo.TYPE_FM))
    }

    @Test
    fun `daftar sink dari HP user hanya menyisakan speaker internal`() {
        val types = listOf(
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_FM
        )
        assertEquals("Speaker internal", OutputRouteLabel.describe(types))
    }

    @Test
    fun `batch tanpa satu pun sink bermakna menghasilkan null`() {
        assertNull(OutputRouteLabel.describe(listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_FM)))
        assertNull(OutputRouteLabel.describe(emptyList()))
    }

    @Test
    fun `nama yang sama tidak diulang`() {
        val types = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )
        assertEquals("Bluetooth A2DP, Speaker internal", OutputRouteLabel.describe(types))
    }
}
