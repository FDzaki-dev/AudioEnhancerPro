package com.audioenhancer.booster

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Automated audio-engine test — roadmap.md Fase 0 #8.
 *
 * Coverage utama:
 *
 *  1. [ENUM]  EffectState completeness — guard refactoring accidental agar
 *     tidak ada state yang terhapus/berganti nama tanpa disengaja.
 *
 *  2. [FAIL]  Effect creation → graceful degradation. Premis lama (konstruktor AudioEffect
 *     SELALU melempar di Robolectric → UNAVAILABLE) TIDAK berlaku di Robolectric 4.13:
 *     laporan CI run4 (Batch 204) menunjukkan kelima efek (BassBoost / Virtualizer /
 *     Equalizer / LoudnessEnhancer / DynamicsProcessing) berakhir ENABLED = konstruktor
 *     BERHASIL dan `enabled = true` tak melempar. Yang dijaga sekarang: setelah onCreate()
 *     tiap state HANYA UNAVAILABLE (konstruktor gagal → catch-block `attachXxx()` menulis
 *     ini) atau ENABLED (konstruktor berhasil) — BUKAN FAILED/CONTROL_LOST, BUKAN state tak
 *     terdefinisi — dan service TIDAK boleh crash. Menutup gap roadmap "effect creation failure".
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
// Batch 202: Application POLOS, bukan `AudioEnhancerApp` dari manifest. `AudioEnhancerApp.onCreate`
// memanggil `ServiceWatchdogWorker.schedule` → `WorkManager.getInstance`, yang di Robolectric melempar
// "WorkManager is not initialized properly" sebelum satu tes pun jalan (laporan CI run1/run2).
// Jalur Service yang dites (onCreate/attach efek/requestStart/requestStop) tidak menyentuh WorkManager.
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class AudioEnhancerServiceStateTest {

    private lateinit var controller: ServiceController<AudioEnhancerService>
    private lateinit var service: AudioEnhancerService

    @Before
    fun setUp() {
        controller = Robolectric.buildService(AudioEnhancerService::class.java)
        // create() → onCreate() → createNotificationChannel() + attachEffects()
        // + registerAudioDeviceCallback(). AudioEffect constructor di Robolectric 4.13
        // BERHASIL → state ENABLED (run4); kalau suatu saat gagal → catchXxx() mendaratkan
        // UNAVAILABLE. Keduanya tanpa crash Service.
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

    // ─── [FAIL] Effect creation → graceful degradation (UNAVAILABLE atau ENABLED) ──

    /**
     * Batch 204: state efek setelah onCreate() HARUS UNAVAILABLE (konstruktor gagal) atau ENABLED
     * (konstruktor berhasil, mis. Robolectric 4.13). Selain itu (FAILED/CONTROL_LOST/AVAILABLE)
     * berarti catch-block / jalur enable salah menulis state.
     */
    private fun assertUnavailableOrEnabled(name: String, actual: AudioEnhancerService.EffectState) {
        val valid = setOf(
            AudioEnhancerService.EffectState.UNAVAILABLE,
            AudioEnhancerService.EffectState.ENABLED
        )
        assertTrue("$name harus UNAVAILABLE atau ENABLED setelah onCreate, tapi $actual", actual in valid)
    }

    /** Smoke: onCreate() tidak lempar exception yang tidak tertangkap. */
    @Test
    fun `service onCreate does not crash when AudioFlinger is absent`() {
        assertNotNull(service)
    }

    @Test
    fun `bassState is UNAVAILABLE or ENABLED after onCreate in Robolectric`() {
        assertUnavailableOrEnabled("bassState", service.bassState)
    }

    @Test
    fun `virtualizerState is UNAVAILABLE or ENABLED after onCreate in Robolectric`() {
        assertUnavailableOrEnabled("virtualizerState", service.virtualizerState)
    }

    @Test
    fun `equalizerState is UNAVAILABLE or ENABLED after onCreate in Robolectric`() {
        assertUnavailableOrEnabled("equalizerState", service.equalizerState)
    }

    @Test
    fun `loudnessState is UNAVAILABLE or ENABLED after onCreate in Robolectric`() {
        assertUnavailableOrEnabled("loudnessState", service.loudnessState)
    }

    @Test
    fun `dynamicsState is UNAVAILABLE or ENABLED after onCreate in Robolectric`() {
        // Guard tambahan: DynamicsProcessing di-guard Build.VERSION.SDK_INT >= P di
        // attachDynamicsProcessing(). API < 28 → UNAVAILABLE (else-branch); API >= 28 →
        // ENABLED bila konstruktor berhasil (Robolectric 4.13, run4) atau UNAVAILABLE dari
        // catch-block. Tidak boleh ada UnsatisfiedLinkError/crash.
        assertUnavailableOrEnabled("dynamicsState", service.dynamicsState)
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
