package com.audioenhancer.booster

// Batch 16: God Activity split (audit High-priority item #1). MainActivity.kt sekarang
// CUMA berisi Activity class — lifecycle, service binding, permission launcher, dan
// pemanggilan BoosterScreen(). Semua Composable UI (BoosterScreen + pendukungnya) pindah
// ke BoosterScreen.kt, komponen visual generik (NeumorphicCard dkk) pindah ke
// NeumorphicComponents.kt. TIDAK ADA perubahan logic/behavior — murni pemindahan lokasi
// kode + penyesuaian visibility (`private`→`internal`) yang diperlukan supaya composable
// lintas-file tetap bisa saling panggil. Lanjutan yang BELUM dikerjakan (lihat
// PROJECT_STATE.md Batch 16): ekstraksi state+business logic ke ViewModel (MVVM), lalu DI
// (Hilt/Koin) — sengaja dipisah batch demi batch (Batch Lock), bukan sekaligus, karena
// sandbox Claude tidak punya compiler untuk verifikasi runtime.

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    /** Status koneksi ke AudioEnhancerService — dipakai UI untuk loading/error state eksplisit. */
    enum class ConnectionState { CONNECTING, CONNECTED, ERROR }

    private var service: AudioEnhancerService? = null
    private var bound = false

    // Status koneksi ke service, dipakai untuk tampilkan loading/error state eksplisit di UI —
    // sebelumnya kalau bindService() gagal total, app cuma diam tanpa penjelasan sama sekali.
    private var connectionState by mutableStateOf(ConnectionState.CONNECTING)

    private var bassSupported by mutableStateOf(true)
    private var virtualizerSupported by mutableStateOf(true)
    private var loudnessSupported by mutableStateOf(true)
    private var bassStrengthSupported by mutableStateOf(true)
    private var virtualizerStrengthSupported by mutableStateOf(true)
    private var notificationPermissionGranted by mutableStateOf(true)

    // Diisi kalau app dibuka lewat App Shortcut (long-press ikon launcher) yang nunjuk
    // ke preset custom tertentu. BoosterScreen yang nge-apply beneran (butuh akses ke
    // service/pending-buffer di dalam Compose), di sini cuma nampung nama presetnya.
    private var shortcutCustomPresetName by mutableStateOf<String?>(null)

    // Info equalizer per-band, diisi begitu service konek (band count 0 = belum siap/tidak didukung).
    private var equalizerSupported by mutableStateOf(false)
    private var equalizerBandCount by mutableStateOf(0)
    private var equalizerLevelMin by mutableStateOf<Short>(-1500)
    private var equalizerLevelMax by mutableStateOf<Short>(1500)
    private var equalizerCenterFreqsHz by mutableStateOf<List<Int>>(emptyList())
    private var equalizerInitialLevels by mutableStateOf<List<Short>>(emptyList())

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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationPermissionGranted = granted }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            notificationPermissionGranted = granted
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Bisa dipanggil ulang kapan saja (bukan cuma di onCreate) — misal dari tombol
     *  "Nyalakan Lagi" kalau service sempat di-stop lewat notifikasi sementara app masih kebuka. */
    private fun startBoosterService() {
        AudioEnhancerService.requestStart(this)
    }

    /** Coba bind ke service lagi. Dipakai di onCreate DAN dari tombol "Coba Lagi" —
     *  sengaja dipisah dari recreate() supaya retry tidak ikut memicu ulang dialog
     *  izin notifikasi/baterai yang seharusnya cuma relevan di startup pertama. */
    private fun attemptBindService() {
        connectionState = ConnectionState.CONNECTING
        startBoosterService()
        val intent = Intent(this, AudioEnhancerService::class.java)
        try {
            val boundOk = bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!boundOk) connectionState = ConnectionState.ERROR
        } catch (_: Exception) {
            connectionState = ConnectionState.ERROR
        }
    }

    /** Dipanggil dari onCreate (cold start) & onNewIntent (app udah kebuka, launchMode
     *  singleTop) — dua-duanya bisa terjadi tergantung app lagi jalan atau enggak
     *  pas shortcut di-tap. Toggle langsung dieksekusi di sini (gak butuh Compose),
     *  preset custom cuma "dititip" ke state, BoosterScreen yang eksekusi beneran. */
    private fun handleShortcutIntent(intent: Intent?) {
        intent ?: return
        when (intent.getStringExtra(ShortcutHelper.EXTRA_ACTION)) {
            ShortcutHelper.ACTION_TOGGLE -> {
                if (AudioEnhancerService.isRunning) AudioEnhancerService.requestStop(this)
                else AudioEnhancerService.requestStart(this)
            }
        }
        intent.getStringExtra(ShortcutHelper.EXTRA_CUSTOM_PRESET_NAME)?.let { name ->
            shortcutCustomPresetName = name
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        attemptBindService()

        requestIgnoreBatteryOptimizations()
        handleShortcutIntent(intent)

        setContent {
            var themeMode by remember { mutableStateOf(PrefsHelper.getThemeMode(this@MainActivity)) }
            var useDynamicColor by remember { mutableStateOf(PrefsHelper.getUseDynamicColor(this@MainActivity)) }
            val darkTheme = when (themeMode) {
                PrefsHelper.THEME_MODE_LIGHT -> false
                PrefsHelper.THEME_MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }

            // Ikon status bar/nav bar (terang/gelap) di-sync ulang tiap kali darkTheme berubah —
            // bukan cuma sekali dibaca dari system di awal. Tanpa ini, kalau user override tema
            // manual berlawanan dari sistem (misal sistem terang, dipaksa Dark), ikon status bar
            // bisa nyaris tidak kelihatan karena warnanya tetap mengikuti sistem, bukan tema aktif.
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            AudioEnhancerTheme(darkTheme = darkTheme, useDynamicColor = useDynamicColor) {
                Surface(
                    // Batch 12: background SELALU flat solid (colorScheme.background), tidak
                    // ada lagi cabang gradient khusus dark theme. Neumorphism butuh base color
                    // rata satu warna supaya perhitungan dual-shadow (terang/gelap) konsisten
                    // di seluruh permukaan — gradient bikin sisi shadow salah kontras di
                    // sebagian area layar.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    var showOnboarding by remember {
                        mutableStateOf(!PrefsHelper.isOnboardingDone(this@MainActivity))
                    }

                    if (showOnboarding) {
                        OnboardingScreen(onFinish = {
                            PrefsHelper.setOnboardingDone(this@MainActivity)
                            showOnboarding = false
                        })
                    } else {
                        BoosterScreen(
                            onBass = { if (bound) service?.setBassStrength(it) else pendingBass = it },
                            onVirtualizer = { if (bound) service?.setVirtualizerStrength(it) else pendingVirtualizer = it },
                            onLoudness = { if (bound) service?.setLoudnessGain(it) else pendingLoudness = it },
                            onEqualizerBand = { band, level ->
                                if (bound) service?.setEqualizerBand(band.toShort(), level)
                                else pendingEqualizerBands[band] = level
                            },
                            onOpenHelp = { showOnboarding = true },
                            bassSupported = bassSupported,
                            virtualizerSupported = virtualizerSupported,
                            loudnessSupported = loudnessSupported,
                            bassStrengthSupported = bassStrengthSupported,
                            virtualizerStrengthSupported = virtualizerStrengthSupported,
                            equalizerSupported = equalizerSupported,
                            equalizerBandCount = equalizerBandCount,
                            equalizerLevelMin = equalizerLevelMin,
                            equalizerLevelMax = equalizerLevelMax,
                            equalizerCenterFreqsHz = equalizerCenterFreqsHz,
                            equalizerInitialLevels = equalizerInitialLevels,
                            initialBass = PrefsHelper.getBass(this@MainActivity).toFloat(),
                            initialVirtualizer = PrefsHelper.getVirtualizer(this@MainActivity).toFloat(),
                            initialLoudness = PrefsHelper.getLoudness(this@MainActivity),
                            initialActivePreset = PrefsHelper.getActivePreset(this@MainActivity),
                            onActivePresetChange = { PrefsHelper.setActivePreset(this@MainActivity, it) },
                            notificationPermissionGranted = notificationPermissionGranted,
                            onOpenNotificationSettings = { openNotificationSettings() },
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                PrefsHelper.setThemeMode(this@MainActivity, it)
                            },
                            useDynamicColor = useDynamicColor,
                            onUseDynamicColorChange = {
                                useDynamicColor = it
                                PrefsHelper.setUseDynamicColor(this@MainActivity, it)
                            },
                            connectionState = connectionState,
                            onRetryConnection = { attemptBindService() },
                            onRestartService = { startBoosterService() },
                            requestedCustomPresetName = shortcutCustomPresetName,
                            onRequestedPresetConsumed = { shortcutCustomPresetName = null }
                        )
                    }
                }
            }
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        if (bound) { unbindService(connection); bound = false }
        super.onDestroy()
    }
}
