package com.audioenhancer.booster

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Batch 17 (audit High #2, lanjutan Batch 16): ekstraksi state + business logic seputar
// koneksi ke AudioEnhancerService dari MainActivity.kt ke sini. Plain AndroidViewModel
// dulu — SENGAJA TANPA DI framework (Hilt/Koin), itu Atomic Change terpisah (butuh ubah
// build.gradle.kts+settings.gradle.kts, PENDING, lihat PROJECT_STATE.md Batch 17).
//
// Kenapa AndroidViewModel (bukan ViewModel polos): bindService/unbindService di sini
// SENGAJA pakai `getApplication()` (application Context), bukan Activity Context — ini
// PERUBAHAN PERILAKU kecil dari sebelumnya (dulu bind pakai Activity Context di
// MainActivity langsung). Alasannya: ViewModel outlive Activity Context per-definisi
// (VM bisa hidup lebih lama dari 1 instance Activity kalau ada config change), jadi
// nyimpen Activity Context di dalam ViewModel adalah context-leak risk. Application
// Context aman dipakai buat bindService karena app ini TIDAK butuh Activity Context
// spesifik untuk itu (cuma butuh Context apa pun yang valid). Siklus bind/unbind
// (attemptBindService di init lifecycle activity, unbind di onCleared) secara PRAKTIS
// nyaris sama seperti sebelumnya karena rotasi/config change sudah dideprioritaskan
// user (lihat PROJECT_STATE.md), jadi ViewModel ini efektifnya tetap 1:1 umur dengan
// MainActivity di app ini. BELUM divalidasi runtime — kalau ada gejala aneh soal
// binding/unbinding setelah update, laporkan, ini kandidat pertama yang dicurigai.
// Batch 18: @HiltViewModel + @Inject constructor — Application di-provide OTOMATIS oleh
// Hilt (binding bawaan ApplicationComponent, TANPA perlu bikin Module/Provides manual).
// Kelas ini masih AndroidViewModel (bukan ViewModel polos) — @HiltViewModel mendukung
// dua-duanya, gak perlu diganti.
@HiltViewModel
class BoosterViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    /** Status koneksi ke AudioEnhancerService — dipakai UI untuk loading/error state eksplisit. */
    enum class ConnectionState { CONNECTING, CONNECTED, ERROR }

    private var service: AudioEnhancerService? = null
    private var bound = false

    // Status koneksi ke service, dipakai untuk tampilkan loading/error state eksplisit di UI —
    // sebelumnya kalau bindService() gagal total, app cuma diam tanpa penjelasan sama sekali.
    var connectionState by mutableStateOf(ConnectionState.CONNECTING)
        private set

    var bassSupported by mutableStateOf(true); private set
    var virtualizerSupported by mutableStateOf(true); private set
    var loudnessSupported by mutableStateOf(true); private set
    var bassStrengthSupported by mutableStateOf(true); private set
    var virtualizerStrengthSupported by mutableStateOf(true); private set

    // Info equalizer per-band, diisi begitu service konek (band count 0 = belum siap/tidak didukung).
    var equalizerSupported by mutableStateOf(false); private set
    var equalizerBandCount by mutableStateOf(0); private set
    var equalizerLevelMin by mutableStateOf<Short>(-1500); private set
    var equalizerLevelMax by mutableStateOf<Short>(1500); private set
    var equalizerCenterFreqsHz by mutableStateOf<List<Int>>(emptyList()); private set
    var equalizerInitialLevels by mutableStateOf<List<Short>>(emptyList()); private set

    // Menyimpan perubahan slider yang terjadi SEBELUM bindService() selesai konek (race condition:
    // user geser slider dalam <100ms setelah app dibuka). Diterapkan begitu service tersedia,
    // supaya perubahan itu tidak diam-diam hilang.
    private var pendingBass: Short? = null
    private var pendingVirtualizer: Short? = null
    private var pendingLoudness: Float? = null
    private val pendingEqualizerBands = mutableMapOf<Int, Short>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as AudioEnhancerService.LocalBinder).getService()
            bound = true
            connectionState = ConnectionState.CONNECTED
            bassSupported = service?.isBassSupported() ?: true
            virtualizerSupported = service?.isVirtualizerSupported() ?: true
            loudnessSupported = service?.isLoudnessSupported() ?: true
            bassStrengthSupported = service?.isBassStrengthSupported() ?: true
            virtualizerStrengthSupported = service?.isVirtualizerStrengthSupported() ?: true

            equalizerSupported = service?.isEqualizerSupported() ?: false
            val bandCount = service?.getEqualizerBandCount() ?: 0
            equalizerBandCount = bandCount
            service?.getEqualizerLevelRange()?.let { range ->
                equalizerLevelMin = range.getOrElse(0) { -1500 }
                equalizerLevelMax = range.getOrElse(1) { 1500 }
            }
            equalizerCenterFreqsHz = (0 until bandCount).map { service?.getEqualizerBandCenterFreqHz(it) ?: 0 }
            equalizerInitialLevels = (0 until bandCount).map { service?.getEqualizerBandLevel(it) ?: 0 }

            // Terapkan perubahan yang sempat tertunda selagi belum konek.
            pendingBass?.let { service?.setBassStrength(it) }; pendingBass = null
            pendingVirtualizer?.let { service?.setVirtualizerStrength(it) }; pendingVirtualizer = null
            pendingLoudness?.let { service?.setLoudnessGain(it) }; pendingLoudness = null
            pendingEqualizerBands.forEach { (band, level) -> service?.setEqualizerBand(band.toShort(), level) }
            pendingEqualizerBands.clear()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            connectionState = ConnectionState.CONNECTING
        }
    }

    /** Bisa dipanggil ulang kapan saja (bukan cuma sekali) — misal dari tombol
     *  "Nyalakan Lagi" kalau service sempat di-stop lewat notifikasi sementara app masih kebuka. */
    fun startBoosterService() {
        AudioEnhancerService.requestStart(getApplication())
    }

    /** Coba bind ke service lagi. Dipanggil dari MainActivity.onCreate DAN dari tombol
     *  "Coba Lagi" — sengaja terpisah dari init{} supaya retry tidak ikut memicu ulang
     *  dialog izin notifikasi/baterai yang seharusnya cuma relevan di startup pertama
     *  (dialog itu tetap tanggung jawab MainActivity, bukan ViewModel). */
    fun attemptBindService() {
        connectionState = ConnectionState.CONNECTING
        startBoosterService()
        val ctx: Context = getApplication()
        val intent = Intent(ctx, AudioEnhancerService::class.java)
        try {
            val boundOk = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!boundOk) connectionState = ConnectionState.ERROR
        } catch (_: Exception) {
            connectionState = ConnectionState.ERROR
        }
    }

    fun setBass(value: Short) {
        if (bound) service?.setBassStrength(value) else pendingBass = value
    }

    fun setVirtualizer(value: Short) {
        if (bound) service?.setVirtualizerStrength(value) else pendingVirtualizer = value
    }

    fun setLoudness(value: Float) {
        if (bound) service?.setLoudnessGain(value) else pendingLoudness = value
    }

    fun setEqualizerBand(band: Int, level: Short) {
        if (bound) service?.setEqualizerBand(band.toShort(), level) else pendingEqualizerBands[band] = level
    }

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
