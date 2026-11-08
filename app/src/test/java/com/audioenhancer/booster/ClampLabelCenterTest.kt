package com.audioenhancer.booster

import org.junit.Assert.assertEquals
import org.junit.Test

/** Batch 289: jepit pusat label sumbu kurva EQ — fungsi murni, tanpa Robolectric. */
class ClampLabelCenterTest {

    @Test
    fun `label di tepi kiri digeser masuk setengah lebar teks`() {
        assertEquals(35f, clampLabelCenter(center = 0f, textWidth = 70f, canvasWidth = 700f), 0.001f)
    }

    @Test
    fun `label di tepi kanan digeser masuk setengah lebar teks`() {
        assertEquals(665f, clampLabelCenter(center = 700f, textWidth = 70f, canvasWidth = 700f), 0.001f)
    }

    @Test
    fun `label di tengah tidak berubah`() {
        assertEquals(350f, clampLabelCenter(center = 350f, textWidth = 70f, canvasWidth = 700f), 0.001f)
    }

    @Test
    fun `teks lebih lebar dari kanvas dibiarkan di pusat asal`() {
        assertEquals(10f, clampLabelCenter(center = 10f, textWidth = 80f, canvasWidth = 60f), 0.001f)
    }
}
