package com.audioenhancer.booster

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

/**
 * Automated audio-engine test — roadmap.md Fase 0 #8.
 *
 * Coverage utama:
 *
 *  1. [ENUM]  EffectState completeness — guard refactoring accidental agar
 *     tidak ada state yang terhapus/berganti nama tanpa disengaja.
 *
 *  2. [FAIL]  Effect creation failure → graceful degradation ke UNAVAILABLE.
 *     Di Robolectric, constructor AudioEffect (BassBoost / Virtualizer /
 *     Equalizer / LoudnessEnhancer / DynamicsProcessing) melempar
 *     RuntimeException ("AudioFlinger not running"). Setiap `attachXxx()`
 *     HARUS menangkap exception ini dan mendaratkan state di UNAVAILABLE —
 *     BUKAN FAILED, BUKAN state tak terdefinisi, dan service TIDAK boleh
 *     crash. Path ini menutup gap roadmap "effect creation failure".
 *
 *  3. [RECON] State reconciliation — `retryControlAcquisition()` hanya
 *     mentrigger retry untuk CONTROL_LOST / FAILED. Ketika semua state
 *     UNAVAILABLE (hardware memang tidak ada), method ini WAJIB return false
 *     dan idempotent. Menutup gap roadmap "state reconciliation".
 *
 *  4. [SMOKE] Binder contract + companion method smoke — onBind() non-null,
 *     requestStart() / requestStop() tidak crash dengan application context.
 *
 * CATATAN SANDBOX:
 *  Tidak ada kotlinc/Android SDK di sandbox Claude → test ini TIDAK bisa
 *  di-compile-check sebelum dikirim. Verifikasi alternatif: balance brace/
 *  paren/bracket dicek terprogram sebelum zip dikirim (0 selisih = clean).
 */
@RunWith(RobolectricTestRunner::class)
class AudioEnhancerServiceStateTest {

    private lateinit var controller: ServiceController<AudioEnhancerService>
    private lateinit var service: AudioEnhancerService

    @Before
    fun setUp() {
        controller = Robolectric.buildService(AudioEnhancerService::class.java)
        // create() → onCreate() → createNotificationChannel() + attachEffects()
        // + registerAudioDeviceCallback(). AudioEffect constructor gagal di
        // Robolectric (AudioFlinger tidak ada) → setiap catchXxx() mendaratkan
        // state ke UNAVAILABLE tanpa crash Service.
        service = controller.create().get()
    }

    @After
    fun tearDown() {
        // onDestroy() → unregisterAudioDeviceCallback() + releaseEffects(), keduanya
        // null-safe. Dibungkus try-catch agar test failure di setUp() tidak cascade.
        try { controller.destroy() } catch (_: Exception) { }
    }

    // ─── [ENUM] EffectState completeness ────────────────────────────────────────

    /**
     * Verifikasi SEMUA 5 nilai EffectState ada dan namanya tepat.
     * Kalau ada yang dihapus/rename di refactoring, test ini langsung merah.
     */
    @Test
    fun `EffectState enum has exactly the five expected named values`() {
        val actual = AudioEnhancerService.EffectState.values().map { it.name }.toSet()
        assertEquals(
            setOf("UNAVAILABLE", "AVAILABLE", "ENABLED", "FAILED", "CONTROL_LOST"),
            actual
        )
    }

    // ─── [FAIL] Effect creation failure → graceful UNAVAILABLE ──────────────────

    /** Smoke: onCreate() tidak lempar exception yang tidak tertangkap. */
    @Test
    fun `service onCreate does not crash when AudioFlinger is absent`() {
        assertNotNull(service)
    }

    @Test
    fun `bassState is UNAVAILABLE after failed BassBoost construction in Robolectric`() {
        assertEquals(AudioEnhancerService.EffectState.UNAVAILABLE, service.bassState)
    }

    @Test
    fun `virtualizerState is UNAVAILABLE after failed Virtualizer construction in Robolectric`() {
        assertEquals(AudioEnhancerService.EffectState.UNAVAILABLE, service.virtualizerState)
    }

    @Test
    fun `equalizerState is UNAVAILABLE after failed Equalizer construction in Robolectric`() {
        assertEquals(AudioEnhancerService.EffectState.UNAVAILABLE, service.equalizerState)
    }

    @Test
    fun `loudnessState is UNAVAILABLE after failed LoudnessEnhancer construction in Robolectric`() {
        assertEquals(AudioEnhancerService.EffectState.UNAVAILABLE, service.loudnessState)
    }

    @Test
    fun `dynamicsState is UNAVAILABLE after failed DynamicsProcessing construction in Robolectric`() {
        // Guard tambahan: DynamicsProcessing di-guard Build.VERSION.SDK_INT >= P di
        // attachDynamicsProcessing(). Apapun SDK Robolectric aktif, hasilnya UNAVAILABLE
        // (dari else-branch kalau API < 28, atau dari catch-block kalau API >= 28 tapi
        // AudioFlinger tidak ada). Tidak boleh ada UnsatisfiedLinkError/crash.
        assertEquals(AudioEnhancerService.EffectState.UNAVAILABLE, service.dynamicsState)
    }

    /**
     * FAILED = exception saat enable/attach setelah object ada.
     * RuntimeException saat KONSTRUKSI harus menghasilkan UNAVAILABLE, bukan FAILED.
     * Test ini memverifikasi bahwa catch-block di attachXxx() menulis state yang tepat.
     */
    @Test
    fun `no effect state is FAILED after constructor RuntimeException in Robolectric`() {
        val failedCount = listOf(
            service.bassState,
            service.virtualizerState,
            service.equalizerState,
            service.loudnessState,
            service.dynamicsState
        ).count { it == AudioEnhancerService.EffectState.FAILED }
        assertEquals(
            "Constructor RuntimeException harus menghasilkan UNAVAILABLE, bukan FAILED",
            0,
            failedCount
        )
    }

    // ─── [RECON] State reconciliation ───────────────────────────────────────────

    /**
     * UNAVAILABLE ≠ CONTROL_LOST/FAILED.
     * retryControlAcquisition() hanya bekerja untuk CONTROL_LOST/FAILED —
     * hardware yang memang tidak ada (UNAVAILABLE) tidak boleh di-retry.
     */
    @Test
    fun `retryControlAcquisition returns false when all effects are UNAVAILABLE`() {
        assertFalse(service.retryControlAcquisition())
    }

    /** Idempotent: pemanggil ganda tidak mengubah return value kalau state tidak berubah. */
    @Test
    fun `retryControlAcquisition is idempotent when called twice with unchanged state`() {
        service.retryControlAcquisition() // panggil pertama — tidak crash
        assertFalse(service.retryControlAcquisition()) // panggil kedua — tetap false
    }

    // ─── [SMOKE] Binder + companion methods ─────────────────────────────────────

    @Test
    fun `onBind returns a non-null IBinder`() {
        assertNotNull(service.onBind(Intent()))
    }

    @Test
    fun `requestStart with application context does not throw`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // startForegroundService() di-shadow Robolectric — tidak butuh AudioFlinger.
        AudioEnhancerService.requestStart(ctx)
    }

    @Test
    fun `requestStop with application context does not throw`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        AudioEnhancerService.requestStop(ctx)
    }
}
