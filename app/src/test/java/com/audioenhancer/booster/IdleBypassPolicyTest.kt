package com.audioenhancer.booster

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 336: aturan murni bypass idle + penjaga sumber untuk titik penyalaan efek di service. */
class IdleBypassPolicyTest {

    private fun neutral(
        bass: Int = 0, virtualizer: Int = 0, loudnessMb: Float = 0f, compressor: Int = 0, clarity: Int = 0,
        eq: List<Int> = List(IdleBypassPolicy.MAX_EQ_BANDS) { 0 }
    ) = IdleBypassPolicy.isAllNeutral(bass, virtualizer, loudnessMb, compressor, clarity, eq)

    @Test
    fun `all zero settings are neutral`() = assertTrue(neutral())

    @Test
    fun `empty eq list is neutral`() = assertTrue(neutral(eq = emptyList()))

    @Test
    fun `any single non neutral setting disables the bypass`() {
        assertFalse(neutral(bass = 1))
        assertFalse(neutral(virtualizer = 1))
        assertFalse(neutral(loudnessMb = 100f))
        assertFalse(neutral(compressor = 1))
        assertFalse(neutral(clarity = 1))
        assertFalse(neutral(eq = List(IdleBypassPolicy.MAX_EQ_BANDS) { if (it == 3) -200 else 0 }))
    }

    @Test
    fun `loudness rounding noise below half a millibel stays neutral`() {
        assertTrue(neutral(loudnessMb = 0.4f))
        assertFalse(neutral(loudnessMb = 1f))
    }

    @Test
    fun `eq band limit covers every equalizer size and fallback`() {
        assertTrue(IdleBypassPolicy.MAX_EQ_BANDS >= 10)
        assertEquals(16, IdleBypassPolicy.MAX_EQ_BANDS)
    }

    @Test
    fun `bypass is opt in and off by default`() {
        val prefs = source("PrefsHelper.kt")
        assertTrue(prefs.contains("getBoolean(IDLE_BYPASS_KEY, false)"))
    }

    @Test
    fun `every enable path honours the bypass and the guard ignores bypassed effects`() {
        val svc = source("AudioEnhancerService.kt")
        val mix = svc.substring(svc.indexOf("private fun enableMixEffects()"), svc.indexOf("fun isBassSupported()"))
        assertTrue("enableMixEffects", mix.indexOf("if (mixBypassNow()) return") in 0 until mix.indexOf("bassBoost?.enabled = true"))
        val one = svc.substring(svc.indexOf("private fun enableMixEffect(kind"), svc.indexOf("private suspend fun runOrphanSweep("))
        assertTrue("enableMixEffect(kind)", one.indexOf("if (mixBypassNow()) return") in 0 until one.indexOf("when (kind)"))
        val find = svc.substring(svc.indexOf("private fun findDisabledEffects()"), svc.indexOf("private fun findDriftedSettings()"))
        assertTrue(find.indexOf("if (mixBypassed) return emptyList()") in 0 until find.indexOf("val disabled"))
        val attach = svc.substring(svc.indexOf("private fun attachEffects()"), svc.indexOf("private fun attachBass("))
        assertTrue(attach.contains("val enableNow = deferredEnableKinds.isEmpty() && !bypass"))
        assertTrue(attach.indexOf("mixBypassNow()") in 0 until attach.indexOf("attachBass(enableNow)"))
    }

    @Test
    fun `public setters re evaluate the bypass after persisting and the listener is released`() {
        val svc = source("AudioEnhancerService.kt")
        val pairs = listOf(
            "PrefsHelper.setBass(this, strength.toInt())" to "refreshIdleBypass(\"bass\")",
            "PrefsHelper.setVirtualizer(this, strength.toInt())" to "refreshIdleBypass(\"virtualizer\")",
            "PrefsHelper.setLoudness(this, gainMb)" to "refreshIdleBypass(\"loudness\")",
            "PrefsHelper.setCompressorAmount(this, clamped)" to "refreshIdleBypass(\"compressor\")",
            "PrefsHelper.setClarity(this, clamped)" to "refreshIdleBypass(\"clarity\")",
            "PrefsHelper.setEqualizerBandLevel(this, band.toInt(), levelMb.toInt())" to "refreshIdleBypass(\"eq\")"
        )
        for ((persist, refresh) in pairs) {
            val a = svc.indexOf(persist)
            assertTrue("$persist ada", a >= 0)
            assertTrue("$refresh sesudah $persist", svc.indexOf(refresh, a) in a + 1 until a + persist.length + 120)
        }
        assertTrue(svc.contains("PrefsHelper.registerChangeListener(this, prefChangeListener)"))
        assertTrue(svc.contains("PrefsHelper.unregisterChangeListener(this, prefChangeListener)"))
        val refresh = svc.substring(svc.indexOf("private fun refreshIdleBypass("), svc.indexOf("private fun disableMixEffects()"))
        assertTrue("digerbang isRunning", refresh.indexOf("if (!isRunning) return") in 0 until refresh.indexOf("idleBypassWanted()"))
    }

    @Test
    fun `idle bypass section tells the four cases apart`() {
        fun section(opt: Boolean, neutral: Boolean, running: Boolean, bypassed: Boolean) =
            DebugLogFormat.buildIdleBypassSection(opt, neutral, running, bypassed, "bass=0")
        assertTrue(section(false, true, true, false).contains("kesimpulan: saklar OFF"))
        assertTrue(section(true, false, true, false).contains("ada pengaturan tak netral"))
        assertTrue(section(true, true, true, true).contains("sesuai harapan"))
        assertTrue(section(true, true, true, false).contains("TIDAK SESUAI"))
        assertTrue(section(true, true, false, false).contains("layanan mati"))
    }

    @Test
    fun `idle bypass section shows switch settings and neutrality`() {
        val text = DebugLogFormat.buildIdleBypassSection(true, false, true, false, "bass=300 pita EQ tak nol=0")
        assertTrue(text.startsWith("---- bypass idle (Hemat baterai saat netral) ----"))
        assertTrue(text.contains("saklar Pengaturan: ON"))
        assertTrue(text.contains("bass=300 pita EQ tak nol=0"))
        assertTrue(text.contains("semua pengaturan netral: tidak"))
        assertTrue(text.contains("status di layanan: efek mixer menyala normal"))
    }

    @Test
    fun `export includes the idle bypass section and the service exposes the real state`() {
        val exporter = source("DebugLogExporter.kt")
        assertTrue(exporter.contains("readAvailableEffects() + readIdleBypassSection(context) + readProfileStatus(context)"))
        assertTrue(exporter.contains("serviceBypassed = AudioEnhancerService.mixBypassed"))
        val svc = source("AudioEnhancerService.kt")
        assertTrue(svc.contains("var mixBypassed = false\n            private set"))
        assertFalse("tak boleh ada salinan instance yang menyimpang dari status companion", svc.contains("private var mixBypassed"))
    }

    @Test
    fun `leaving the bypass enables the limiter before any gain effect and entering disables it last`() {
        val svc = source("AudioEnhancerService.kt")
        val refresh = svc.substring(svc.indexOf("private fun refreshIdleBypass("), svc.indexOf("private fun disableMixEffects()"))
        val limiterFirst = refresh.indexOf("enableMixEffect(OrphanEffects.Kind.DYNAMICS)")
        assertTrue("limiter dinyalakan dulu", limiterFirst >= 0 && limiterFirst < refresh.indexOf("enableMixEffects()"))
        val disable = svc.substring(svc.indexOf("private fun disableMixEffects()"), svc.indexOf("private fun releaseEffects()"))
        assertTrue("limiter dimatikan terakhir", disable.indexOf("loudnessEnhancer?.enabled = false") < disable.indexOf("dynamicsProcessing?.enabled = false"))
        assertTrue(disable.indexOf("bassBoost?.enabled = false") < disable.indexOf("loudnessEnhancer?.enabled = false"))
    }

    private fun source(name: String): String {
        val rel = "src/main/java/com/audioenhancer/booster/$name"
        val text = checkNotNull(listOf(rel, "app/$rel").map { File(it) }.firstOrNull { it.isFile }?.readText()) { "$name tidak ditemukan" }
        return text.replace(Regex("//[^\n]*"), "")
    }
}
