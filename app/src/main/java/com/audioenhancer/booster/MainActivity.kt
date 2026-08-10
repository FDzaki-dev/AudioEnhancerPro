package com.audioenhancer.booster

// Batch 16: God Activity split (audit High-priority item #1) — MainActivity.kt dipecah
// jadi 3 file (lihat BoosterScreen.kt, SkeuomorphicComponents.kt).
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

// Batch 18: @AndroidEntryPoint WAJIB ada di sini supaya `by viewModels()` di bawah bisa
// resolve BoosterViewModel lewat Hilt (HiltViewModelFactory) — tanpa ini, `by viewModels()`
// balik ke default factory biasa yang GAK TAHU cara construct @HiltViewModel (crash
// runtime "Cannot create an instance of...").
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: BoosterViewModel by viewModels()

    private var notificationPermissionGranted by mutableStateOf(true)

    // Batch 41: exemption battery optimization resmi Android (beda dari Autostart OEM di
    // OemAutostartHelper.kt — itu deep-link proprietary per-merk, ini API standar AOSP
    // API 23+). `batteryOptimizationIgnored` dibaca UI buat status ✓, `...ResultTick`
    // di-increment tiap user BALIK dari halaman sistem (commit ATAU cancel, gak bisa
    // dibedakan dari hasil intent-nya — makanya kita re-cek langsung ke PowerManager,
    // bukan asumsi dari result code) — dipakai sebagai key LaunchedEffect di Compose
    // buat trigger Snackbar SEKALI per kembalian (bukan tiap recomposition biasa).
    private var batteryOptimizationIgnored by mutableStateOf(true)
    private var batteryOptimizationResultTick by mutableStateOf(0)

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshBatteryOptimizationState()
        batteryOptimizationResultTick++
    }

    private fun refreshBatteryOptimizationState() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(packageName)
    }

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
            val appThemeStyle = when (appThemeStyleKey) {
                PrefsHelper.APP_THEME_RADICAL_SKEUO -> AppThemeStyle.RADICAL_SKEUO
                PrefsHelper.APP_THEME_SKEUOMORPHISM -> AppThemeStyle.SKEUOMORPHISM
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
                // kebaca sebagai kaca. Batch 38: varian Skeuomorphism BUKAN glass (gak
                // butuh backdrop vivid buat efek translucency), pakai gradient netral
                // gunmetal sendiri (`SkeuoScreenBackgroundBrush`) — konsisten sama
                // bahasa desain bevel/material fisiknya, bukan biru.
                val screenBrush = when (appThemeStyle) {
                    AppThemeStyle.RADICAL_SKEUO -> AuroraScreenBackgroundBrush
                    AppThemeStyle.SKEUOMORPHISM -> SkeuoScreenBackgroundBrush
                    else -> ScreenBackgroundBrush
                }
                // Batch 41: Box pembungkus baru — SATU-SATUNYA alasan ditambah adalah supaya
                // SnackbarHost bisa "melayang" di atas Surface (konfirmasi battery-optimization),
                // TIDAK mengubah layout/perilaku konten Surface di dalamnya sama sekali.
                val snackbarHostState = remember { SnackbarHostState() }

                // Snackbar muncul SEKALI tiap user balik dari halaman battery-optimization
                // sistem (key = tick, guard tick>0 biar gak nembak pas komposisi pertama
                // sebelum user pernah diarahkan kemanapun — lihat komentar
                // batteryOptimizationResultTick di atas).
                LaunchedEffect(batteryOptimizationResultTick) {
                    if (batteryOptimizationResultTick > 0) {
                        val message = if (batteryOptimizationIgnored) {
                            getString(R.string.battery_opt_granted_snackbar)
                        } else {
                            getString(R.string.battery_opt_denied_snackbar)
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
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
                            batteryOptimizationIgnored = batteryOptimizationIgnored,
                            onRequestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
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
                // Batch 41: SnackbarHost "melayang" di atas Surface (sibling terakhir di
                // Box, jadi digambar paling atas) — konfirmasi hasil battery-optimization.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(bottom = 8.dp)
                )
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

    // Batch 41: SEBELUMNYA `startActivity(intent)` fire-and-forget — gak pernah tahu hasilnya
    // (user commit atau cancel), gak ada feedback ke UI. Sekarang lewat
    // `batteryOptimizationLauncher` (ActivityResultContracts.StartActivityForResult) supaya
    // begitu user balik ke app, kita re-cek status asli via PowerManager & kasih feedback
    // Snackbar (lihat BoosterScreen.kt) — pola SAMA PERSIS seperti
    // `notificationPermissionLauncher` di atas. Dipanggil otomatis dari onCreate (perilaku
    // existing TIDAK diubah — SENGAJA tetap auto-prompt tiap app dibuka selama belum granted,
    // lihat PROJECT_STATE.md Batch 41) DAN bisa dipanggil manual dari tombol baru di
    // BoosterScreen (`onRequestIgnoreBatteryOptimizations`).
    private fun requestIgnoreBatteryOptimizations() {
        refreshBatteryOptimizationState()
        if (!batteryOptimizationIgnored) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryOptimizationLauncher.launch(intent)
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
        // Batch 41: user bisa juga ubah battery optimization dari Settings sistem di luar
        // flow launcher kita (mis. lewat App Info langsung) — re-cek tiap resume, konsisten
        // dengan pola notification permission di atas. TIDAK increment
        // batteryOptimizationResultTick di sini (itu KHUSUS buat hasil dari launcher kita,
        // biar Snackbar cuma muncul setelah user beneran diarahkan lewat tombol/app ini,
        // bukan tiap kali app di-resume).
        refreshBatteryOptimizationState()
    }
}
