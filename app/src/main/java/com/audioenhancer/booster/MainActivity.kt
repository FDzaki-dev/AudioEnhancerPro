package com.audioenhancer.booster

// Batch 16: God Activity split (audit High-priority item #1) — MainActivity.kt dipecah
// jadi 3 file (lihat BoosterScreen.kt, SkeuomorphicComponents.kt).
// Batch 17 (audit High #2): state+business logic seputar koneksi AudioEnhancerService
// (dulu ada di class ini) DIPINDAH ke BoosterViewModel.kt (plain AndroidViewModel).
// MainActivity.kt sekarang CUMA: lifecycle Activity, permission launcher, handling
// shortcut Intent, dan glue ke ViewModel + BoosterScreen(). `ConnectionState` enum juga
// PINDAH ke `BoosterViewModel.ConnectionState` (dulu nested di sini).
// Batch 18 sempat pasang Hilt DI di sini (@AndroidEntryPoint) — DICABUT lagi di Batch 49
// (lihat CHANGELOG.md v1.86.0, kapt/Hilt ternyata dead weight buat compile time, cuma
// buat 1 titik inject sepele yang AndroidViewModel sudah handle sendiri tanpa DI
// framework apapun). `by viewModels()` di bawah sekarang pakai default factory AndroidX
// biasa (SavedStateViewModelFactory), TIDAK butuh anotasi Activity apapun lagi.
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
            var useDynamicColor by remember { mutableStateOf(PrefsHelper.getUseDynamicColor(this@MainActivity)) }
            // Batch 36: switch sistem desain baru (AMOLED Glass <-> Radical Literal
            // Skeuomorphism), TERPISAH dari Material You (useDynamicColor di atas) &
            // dari themeMode lama (dead code sejak Batch 31, tetap tidak dipakai).
            var appThemeStyleKey by remember { mutableStateOf(PrefsHelper.getAppThemeStyle(this@MainActivity)) }
            // Batch 38: tambah varian ke-3 SKEUOMORPHISM (`when`, bukan lagi if/else
            // 2-cabang) — String persistence sudah didesain sejak Batch 36 buat
            // nampung >2 varian, jadi cukup nambah 1 cabang di sini.
            // Batch 43: +varian ke-4 STUDIO_EQ, cabang ke-3.
            val appThemeStyle = when (appThemeStyleKey) {
                PrefsHelper.APP_THEME_RADICAL_SKEUO -> AppThemeStyle.RADICAL_SKEUO
                PrefsHelper.APP_THEME_SKEUOMORPHISM -> AppThemeStyle.SKEUOMORPHISM
                PrefsHelper.APP_THEME_STUDIO_EQ -> AppThemeStyle.STUDIO_EQ
                else -> AppThemeStyle.AMOLED_GLASS
            }
            // Batch 31: WAJIB dark-mode — tidak ada lagi themeMode/isSystemInDarkTheme
            // branching (neumorphism + light theme dicabut total). Status bar/nav bar
            // ikon di-set gelap (kontras di atas base gelap) sekali saja, tidak perlu
            // SideEffect resync karena darkTheme tidak lagi bisa berubah runtime.
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }

            AudioEnhancerTheme(useDynamicColor = useDynamicColor, themeStyle = appThemeStyle) {
                // Batch 37: root background sekarang gradient (bukan flat solid) —
                // glassmorphism butuh backdrop bervariasi supaya kartu kaca di atasnya
                // kebaca sebagai kaca. Batch 38: varian ke-3 BUKAN glass (gak butuh
                // backdrop vivid buat efek translucency), pakai gradient netral sendiri
                // (`NeumoScreenBackgroundBrush`, Batch 46: rename dari
                // `SkeuoScreenBackgroundBrush` — varian ke-3 sekarang Neumorphism, bukan
                // Skeuomorphism lagi) — konsisten sama bahasa desain soft-UI-nya, bukan
                // biru. Batch 43: +cabang STUDIO_EQ -> StudioEqScreenBackgroundBrush
                // (gradient netral gelap studio, bukan biru midnight/gunmetal varian lain).
                val screenBrush = when (appThemeStyle) {
                    AppThemeStyle.RADICAL_SKEUO -> AuroraScreenBackgroundBrush
                    AppThemeStyle.SKEUOMORPHISM -> NeumoScreenBackgroundBrush
                    AppThemeStyle.STUDIO_EQ -> StudioEqScreenBackgroundBrush
                    else -> ScreenBackgroundBrush
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(screenBrush)
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
                            // Batch 58: surfacing AudioEnhancerService.EffectState (Batch 57)
                            // ke BoosterScreen — di-poll berkala oleh viewModel sendiri
                            // (viewModelScope), bukan sekali baca kayak *Supported di atas.
                            bassEffectState = viewModel.bassEffectState,
                            virtualizerEffectState = viewModel.virtualizerEffectState,
                            loudnessEffectState = viewModel.loudnessEffectState,
                            equalizerEffectState = viewModel.equalizerEffectState,
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
                            useDynamicColor = useDynamicColor,
                            onUseDynamicColorChange = {
                                useDynamicColor = it
                                PrefsHelper.setUseDynamicColor(this@MainActivity, it)
                            },
                            // Batch 38: BoosterScreen sekarang terima String key langsung
                            // (bukan Boolean themeStyleIsRadical, Batch 36) — mewadahi 3
                            // varian tema (Midnight Glass/Aurora Glass/Skeuomorphism)
                            // tanpa Boolean kedua yang gampang rancu.
                            appThemeStyleKey = appThemeStyleKey,
                            onThemeStyleChange = { newKey ->
                                appThemeStyleKey = newKey
                                PrefsHelper.setAppThemeStyle(this@MainActivity, newKey)
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
