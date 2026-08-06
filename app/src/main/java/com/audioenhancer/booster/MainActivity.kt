package com.audioenhancer.booster

// Batch 16: God Activity split (audit High-priority item #1) — MainActivity.kt dipecah
// jadi 3 file (lihat BoosterScreen.kt, NeumorphicComponents.kt).
// Batch 17 (audit High #2): state+business logic seputar koneksi AudioEnhancerService
// (dulu ada di class ini) DIPINDAH ke BoosterViewModel.kt (plain AndroidViewModel, TANPA
// DI framework — Hilt/Koin PENDING, Atomic Change terpisah, lihat PROJECT_STATE.md
// Batch 17). MainActivity.kt sekarang CUMA: lifecycle Activity, permission launcher,
// handling shortcut Intent, dan glue ke ViewModel + BoosterScreen(). `ConnectionState`
// enum juga PINDAH ke `BoosterViewModel.ConnectionState` (dulu nested di sini).
// State yang TETAP di sini (SENGAJA tidak dipindah ke ViewModel): notification
// permission (butuh ActivityResultLauncher, API Activity-only) & shortcut preset name
// (butuh Intent dari Activity) — dua-duanya inheren terikat ke lifecycle/API Activity,
// bukan business logic audio yang reusable.

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import dagger.hilt.android.AndroidEntryPoint

// Batch 18: @AndroidEntryPoint WAJIB ada di sini supaya `by viewModels()` di bawah bisa
// resolve BoosterViewModel lewat Hilt (HiltViewModelFactory) — tanpa ini, `by viewModels()`
// balik ke default factory biasa yang GAK TAHU cara construct @HiltViewModel (crash
// runtime "Cannot create an instance of...").
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: BoosterViewModel by viewModels()

    private var notificationPermissionGranted by mutableStateOf(true)

    // Diisi kalau app dibuka lewat App Shortcut (long-press ikon launcher) yang nunjuk
    // ke preset custom tertentu. BoosterScreen yang nge-apply beneran (butuh akses ke
    // service/pending-buffer di dalam Compose), di sini cuma nampung nama presetnya.
    private var shortcutCustomPresetName by mutableStateOf<String?>(null)

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

        viewModel.attemptBindService()

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
                            onBass = { viewModel.setBass(it) },
                            onVirtualizer = { viewModel.setVirtualizer(it) },
                            onLoudness = { viewModel.setLoudness(it) },
                            onEqualizerBand = { band, level -> viewModel.setEqualizerBand(band, level) },
                            onOpenHelp = { showOnboarding = true },
                            bassSupported = viewModel.bassSupported,
                            virtualizerSupported = viewModel.virtualizerSupported,
                            loudnessSupported = viewModel.loudnessSupported,
                            bassStrengthSupported = viewModel.bassStrengthSupported,
                            virtualizerStrengthSupported = viewModel.virtualizerStrengthSupported,
                            equalizerSupported = viewModel.equalizerSupported,
                            equalizerBandCount = viewModel.equalizerBandCount,
                            equalizerLevelMin = viewModel.equalizerLevelMin,
                            equalizerLevelMax = viewModel.equalizerLevelMax,
                            equalizerCenterFreqsHz = viewModel.equalizerCenterFreqsHz,
                            equalizerInitialLevels = viewModel.equalizerInitialLevels,
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
                            connectionState = viewModel.connectionState,
                            onRetryConnection = { viewModel.attemptBindService() },
                            onRestartService = { viewModel.startBoosterService() },
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
}
