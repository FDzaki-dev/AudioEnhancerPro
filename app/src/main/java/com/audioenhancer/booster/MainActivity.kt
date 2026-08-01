package com.audioenhancer.booster

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        val intent = Intent(this, AudioEnhancerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        attemptBindService()

        requestIgnoreBatteryOptimizations()

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
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (darkTheme) Modifier.background(DarkBackgroundBrush)
                            else Modifier.background(MaterialTheme.colorScheme.background)
                        )
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
                            onRestartService = { startBoosterService() }
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

@Composable
private fun ServiceStatusBadge(onRestartService: () -> Unit = {}) {
    var isRunning by remember { mutableStateOf(AudioEnhancerService.isRunning) }

    // Cek status tiap 1 detik selagi layar ini terbuka, biar badge selalu akurat.
    LaunchedEffect(Unit) {
        while (true) {
            isRunning = AudioEnhancerService.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    val statusTint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    GlassTintedCard(tint = statusTint) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) Color(0xFF30D158) else Color(0xFFFF453A))
            )
            Text(
                if (isRunning) stringResource(R.string.status_running)
                else stringResource(R.string.status_not_running),
                style = MaterialTheme.typography.bodySmall,
                color = statusTint,
                modifier = Modifier.weight(1f)
            )
            if (!isRunning) {
                Button(onClick = onRestartService) {
                    Text(stringResource(R.string.restart_service))
                }
            }
        }
    }
}

private data class Preset(
    val label: String,
    val bass: Float,
    val virtualizer: Float,
    val loudness: Float
)

/** Heads-up kecil kalau app sempat crash sejak terakhir dibuka — sebelum ini,
 *  satu-satunya jejak crash adalah notifikasi "aktif" yang tiba-tiba hilang tanpa
 *  penjelasan. Cuma muncul sekali per insiden (ditandai "sudah dilihat" saat ditutup). */
@Composable
private fun CrashBanner() {
    val context = LocalContext.current
    var crashFile by remember {
        mutableStateOf(if (CrashLogger.hasUnseenCrash(context)) CrashLogger.latestCrashLog(context) else null)
    }
    var showDialog by remember { mutableStateOf(false) }
    val file = crashFile ?: return

    fun dismiss() {
        showDialog = false
        CrashLogger.markCrashSeen(context)
        crashFile = null
    }

    GlassTintedCard(tint = MaterialTheme.colorScheme.error) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.crash_banner_body),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.crash_view_button))
            }
        }
    }

    if (showDialog) {
        val crashText = remember(file) { runCatching { file.readText() }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text(stringResource(R.string.crash_dialog_title)) },
            text = {
                Text(
                    crashText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CrashLogger.deleteAllLogs(context)
                    dismiss()
                }) { Text(stringResource(R.string.crash_delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) {
                    Text(stringResource(R.string.crash_dismiss_button))
                }
            }
        )
    }
}

@Composable
fun BoosterScreen(
    onBass: (Short) -> Unit,
    onVirtualizer: (Short) -> Unit,
    onLoudness: (Float) -> Unit,
    onEqualizerBand: (Int, Short) -> Unit = { _, _ -> },
    onOpenHelp: () -> Unit = {},
    bassSupported: Boolean = true,
    virtualizerSupported: Boolean = true,
    loudnessSupported: Boolean = true,
    bassStrengthSupported: Boolean = true,
    virtualizerStrengthSupported: Boolean = true,
    equalizerSupported: Boolean = false,
    equalizerBandCount: Int = 0,
    equalizerLevelMin: Short = -1500,
    equalizerLevelMax: Short = 1500,
    equalizerCenterFreqsHz: List<Int> = emptyList(),
    equalizerInitialLevels: List<Short> = emptyList(),
    initialBass: Float = 500f,
    initialVirtualizer: Float = 500f,
    initialLoudness: Float = 0f,
    initialActivePreset: String? = null,
    onActivePresetChange: (String?) -> Unit = {},
    notificationPermissionGranted: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
    themeMode: Int = PrefsHelper.THEME_MODE_SYSTEM,
    onThemeModeChange: (Int) -> Unit = {},
    useDynamicColor: Boolean = false,
    onUseDynamicColorChange: (Boolean) -> Unit = {},
    connectionState: MainActivity.ConnectionState = MainActivity.ConnectionState.CONNECTED,
    onRetryConnection: () -> Unit = {},
    onRestartService: () -> Unit = {}
) {
    val presets = listOf(
        Preset(stringResource(R.string.preset_flat), bass = 0f, virtualizer = 0f, loudness = 0f),
        Preset(stringResource(R.string.preset_bass_heavy), bass = 900f, virtualizer = 300f, loudness = 500f),
        Preset(stringResource(R.string.preset_vocal_boost), bass = 200f, virtualizer = 600f, loudness = 800f),
        Preset(stringResource(R.string.preset_treble_boost), bass = 100f, virtualizer = 800f, loudness = 600f)
    )
    var bass by remember { mutableStateOf(initialBass) }
    var virtualizer by remember { mutableStateOf(initialVirtualizer) }
    var loudness by remember { mutableStateOf(initialLoudness) }
    // Preset yang tersimpan direstore di sini — nilai slider di atas sudah otomatis benar
    // karena tiap terapkan preset juga menulis nilai numeriknya ke PrefsHelper (lihat applyPreset).
    var activePreset by remember { mutableStateOf(initialActivePreset) }
    val haptics = LocalHapticFeedback.current
    // Counter yang di-increment tiap preset diterapkan, dipakai buat maksa EqualizerSection
    // reset tampilannya ke flat (0) juga — supaya konsisten sama nama presetnya. Sebelumnya
    // preset cuma reset bass/virtualizer/loudness, equalizer manual dibiarkan di posisi lama.
    var eqResetCounter by remember { mutableStateOf(0) }

    val context = LocalContext.current
    var customPresets by remember { mutableStateOf(PrefsHelper.getCustomPresets(context)) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var presetPendingDelete by remember { mutableStateOf<String?>(null) }

    fun applyCustomPreset(preset: PrefsHelper.CustomPreset) {
        // Preset custom sengaja TIDAK ikut me-reset equalizer manual — beda dari preset
        // bawaan, preset custom cuma menyimpan bass/virtualizer/loudness, bukan EQ.
        bass = preset.bass; onBass(preset.bass.toInt().toShort())
        virtualizer = preset.virtualizer; onVirtualizer(preset.virtualizer.toInt().toShort())
        loudness = preset.loudness; onLoudness(preset.loudness)
        activePreset = preset.name
        onActivePresetChange(preset.name)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun applyPreset(preset: Preset) {
        bass = preset.bass; onBass(preset.bass.toInt().toShort())
        virtualizer = preset.virtualizer; onVirtualizer(preset.virtualizer.toInt().toShort())
        loudness = preset.loudness; onLoudness(preset.loudness)
        activePreset = preset.label
        onActivePresetChange(preset.label)
        if (equalizerBandCount > 0) {
            for (band in 0 until equalizerBandCount) onEqualizerBand(band, 0)
            eqResetCounter++
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Di layar lebar (tablet/foldable), konten dibatasi max 600dp dan ditengahkan supaya
    // slider/kartu tidak melebar aneh sampai ke tepi — di HP biasa (layar < 600dp) perilakunya
    // tetap sama seperti sebelumnya (full width).
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.app_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush = Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.primary)
                        )
                    )
                )
                Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeModeToggle(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                IconButton(onClick = onOpenHelp) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.cd_help))
                }
            }
        }

        // Motif waveform kecil — signature visual "audio" yang hidup, bukan sekadar dekorasi acak.
        Row(
            modifier = Modifier.height(24.dp).padding(start = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val waveHeights = listOf(0.4f, 0.7f, 1f, 0.55f, 0.85f, 0.35f, 0.65f, 0.45f)
            waveHeights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(DynamicColorAccent2, DynamicColorAccent)))
                )
            }
        }

        ServiceStatusBadge(onRestartService = onRestartService)
        CrashBanner()

        when (connectionState) {
            MainActivity.ConnectionState.CONNECTING -> {
                GlassCard {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.connection_loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            MainActivity.ConnectionState.ERROR -> {
                GlassTintedCard(tint = MaterialTheme.colorScheme.error) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.connection_error_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            stringResource(R.string.connection_error_body),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        Button(onClick = onRetryConnection) {
                            Text(stringResource(R.string.connection_retry))
                        }
                    }
                }
            }
            MainActivity.ConnectionState.CONNECTED -> { /* tidak perlu tampilkan apa-apa */ }
        }

        if (!notificationPermissionGranted) {
            GlassTintedCard(tint = MaterialTheme.colorScheme.error) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.notif_perm_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.notif_perm_body),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Button(onClick = onOpenNotificationSettings) {
                        Text(stringResource(R.string.notif_perm_button))
                    }
                }
            }
        }

        if (!bassSupported || !virtualizerSupported || !loudnessSupported) {
            GlassTintedCard(tint = MaterialTheme.colorScheme.error) {
                Text(
                    stringResource(R.string.unsupported_banner),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if ((bassSupported && !bassStrengthSupported) || (virtualizerSupported && !virtualizerStrengthSupported)) {
            GlassTintedCard(tint = MaterialTheme.colorScheme.primary) {
                Text(
                    stringResource(R.string.strength_unsupported_banner),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column {
            SectionLabel(stringResource(R.string.presets_title))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = activePreset == preset.label,
                        onClick = { applyPreset(preset) },
                        label = { Text(preset.label) },
                        shape = RoundedCornerShape(50),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                customPresets.forEach { custom ->
                    FilterChip(
                        selected = activePreset == custom.name,
                        onClick = { applyCustomPreset(custom) },
                        label = { Text(custom.name) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_delete_preset, custom.name),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { presetPendingDelete = custom.name }
                            )
                        },
                        shape = RoundedCornerShape(50),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                AssistChip(
                    onClick = { presetNameInput = ""; showSavePresetDialog = true },
                    label = { Text(stringResource(R.string.preset_save_chip)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50)
                )
            }
        }

        if (showSavePresetDialog) {
            AlertDialog(
                onDismissRequest = { showSavePresetDialog = false },
                title = { Text(stringResource(R.string.preset_save_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.preset_save_dialog_hint)) }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = presetNameInput.isNotBlank(),
                        onClick = {
                            val newPreset = PrefsHelper.CustomPreset(presetNameInput.trim(), bass, virtualizer, loudness)
                            PrefsHelper.addCustomPreset(context, newPreset)
                            customPresets = PrefsHelper.getCustomPresets(context)
                            activePreset = newPreset.name
                            onActivePresetChange(newPreset.name)
                            showSavePresetDialog = false
                        }
                    ) { Text(stringResource(R.string.preset_save_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSavePresetDialog = false }) {
                        Text(stringResource(R.string.preset_save_cancel))
                    }
                }
            )
        }

        presetPendingDelete?.let { nameToDelete ->
            AlertDialog(
                onDismissRequest = { presetPendingDelete = null },
                title = { Text(stringResource(R.string.preset_delete_dialog_title)) },
                text = { Text(stringResource(R.string.preset_delete_dialog_body, nameToDelete)) },
                confirmButton = {
                    TextButton(onClick = {
                        PrefsHelper.deleteCustomPreset(context, nameToDelete)
                        customPresets = PrefsHelper.getCustomPresets(context)
                        if (activePreset == nameToDelete) { activePreset = null; onActivePresetChange(null) }
                        presetPendingDelete = null
                    }) { Text(stringResource(R.string.preset_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { presetPendingDelete = null }) {
                        Text(stringResource(R.string.preset_delete_cancel))
                    }
                }
            )
        }

        FeatureControl(
            title = stringResource(R.string.feature_bass_title),
            icon = Icons.Filled.VolumeUp,
            accentColor = BassAccent,
            accentColor2 = BassAccent2,
            helpText = when {
                !bassSupported -> stringResource(R.string.feature_help_unsupported)
                !bassStrengthSupported -> stringResource(R.string.feature_help_strength_unsupported)
                else -> stringResource(R.string.feature_bass_help_normal)
            },
            value = bass,
            valueLabel = bass.toInt().toString(),
            onValueChange = { bass = it; onBass(it.toInt().toShort()); activePreset = null; onActivePresetChange(null) },
            valueRange = 0f..1000f,
            enabled = bassSupported && bassStrengthSupported
        )

        FeatureControl(
            title = stringResource(R.string.feature_virtualizer_title),
            icon = Icons.Filled.SurroundSound,
            accentColor = VirtualizerAccent,
            accentColor2 = VirtualizerAccent2,
            helpText = when {
                !virtualizerSupported -> stringResource(R.string.feature_help_unsupported)
                !virtualizerStrengthSupported -> stringResource(R.string.feature_help_strength_unsupported)
                else -> stringResource(R.string.feature_virtualizer_help_normal)
            },
            value = virtualizer,
            valueLabel = virtualizer.toInt().toString(),
            onValueChange = { virtualizer = it; onVirtualizer(it.toInt().toShort()); activePreset = null; onActivePresetChange(null) },
            valueRange = 0f..1000f,
            enabled = virtualizerSupported && virtualizerStrengthSupported
        )

        FeatureControl(
            title = stringResource(R.string.feature_loudness_title),
            icon = Icons.Filled.Campaign,
            accentColor = LoudnessAccent,
            accentColor2 = LoudnessAccent2,
            helpText = if (loudnessSupported) stringResource(R.string.feature_loudness_help_normal)
                       else stringResource(R.string.feature_help_unsupported),
            value = loudness,
            valueLabel = "${loudness.toInt()} mB",
            onValueChange = { loudness = it; onLoudness(it); activePreset = null; onActivePresetChange(null) },
            valueRange = 0f..3000f,
            enabled = loudnessSupported
        )

        if (equalizerSupported && equalizerBandCount > 0) {
            EqualizerSection(
                bandCount = equalizerBandCount,
                levelMin = equalizerLevelMin,
                levelMax = equalizerLevelMax,
                centerFreqsHz = equalizerCenterFreqsHz,
                initialLevels = if (eqResetCounter == 0) equalizerInitialLevels else List(equalizerBandCount) { 0 },
                resetKey = eqResetCounter,
                onBandChange = { band, level ->
                    onEqualizerBand(band, level)
                    activePreset = null; onActivePresetChange(null)
                }
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = useDynamicColor,
                            onValueChange = {
                                onUseDynamicColorChange(it)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            role = Role.Switch
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(stringResource(R.string.dynamic_color_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.dynamic_color_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = useDynamicColor, onCheckedChange = null)
                }
            }
        }

        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.battery_card_title), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.battery_card_body),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        TextButton(onClick = onOpenHelp) {
            Text(stringResource(R.string.see_full_explanation))
        }
        }
    }
}

/** Satu tombol ikon yang berputar antar 3 mode: ikut sistem → terang → gelap → (ulang). */
@Composable
private fun ThemeModeToggle(themeMode: Int, onThemeModeChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val (icon, description) = when (themeMode) {
        PrefsHelper.THEME_MODE_LIGHT -> Icons.Filled.LightMode to stringResource(R.string.theme_desc_light)
        PrefsHelper.THEME_MODE_DARK -> Icons.Filled.DarkMode to stringResource(R.string.theme_desc_dark)
        else -> Icons.Filled.Brightness4 to stringResource(R.string.theme_desc_system)
    }
    IconButton(onClick = {
        val next = when (themeMode) {
            PrefsHelper.THEME_MODE_SYSTEM -> PrefsHelper.THEME_MODE_LIGHT
            PrefsHelper.THEME_MODE_LIGHT -> PrefsHelper.THEME_MODE_DARK
            else -> PrefsHelper.THEME_MODE_SYSTEM
        }
        onThemeModeChange(next)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }) {
        Icon(icon, contentDescription = description)
    }
}

/** Bagian equalizer manual per-pita-frekuensi — collapsible, disembunyikan by default supaya
 *  tidak membanjiri layar utama (fitur lanjutan, kebanyakan user cukup pakai preset/slider utama). */
@Composable
private fun EqualizerSection(
    bandCount: Int,
    levelMin: Short,
    levelMax: Short,
    centerFreqsHz: List<Int>,
    initialLevels: List<Short>,
    resetKey: Int = 0,
    onBandChange: (Int, Short) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val levels = remember(bandCount, resetKey) {
        mutableStateListOf(*Array(bandCount) { i -> initialLevels.getOrElse(i) { 0 } })
    }
    val haptics = LocalHapticFeedback.current

    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text(stringResource(R.string.eq_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.eq_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.cd_eq_collapse) else stringResource(R.string.cd_eq_expand)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                for (band in 0 until bandCount) {
                    FeatureControl(
                        title = formatFreqLabel(centerFreqsHz.getOrElse(band) { 0 }),
                        helpText = "",
                        value = levels[band].toFloat(),
                        valueLabel = "${levels[band]} mB",
                        onValueChange = {
                            val level = it.toInt().toShort()
                            levels[band] = level
                            onBandChange(band, level)
                        },
                        valueRange = levelMin.toFloat()..levelMax.toFloat(),
                        accentColor = EqualizerAccent,
                        accentColor2 = EqualizerAccent2,
                        wrapInCard = false
                    )
                }
            }
        }
    }
}

internal fun formatFreqLabel(hz: Int): String =
    if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

/** Kartu ala kaca premium: fill translucent (nembus ke gradient background di
 *  belakangnya), border TIPIS bergradasi (bukan tebal solid ala Batch 1, bukan
 *  juga hairline datar ala Apple), shadow lembut buat kesan melayang halus. */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColor2: Color = accentColor,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(accentColor, accentColor2.copy(alpha = 0.35f)))),
        content = content
    )
}

/** Banner info/warning, tetap gaya kaca — fill di-blend solid dulu (bukan alpha
 *  mentah) supaya tetap kelihatan jelas di atas gradient background gelap. */
@Composable
private fun GlassTintedCard(
    modifier: Modifier = Modifier,
    tint: Color,
    tint2: Color = tint,
    content: @Composable ColumnScope.() -> Unit
) {
    val blendedContainer = lerp(MaterialTheme.colorScheme.surface, tint, 0.24f).copy(alpha = 0.75f)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = blendedContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(tint, tint2.copy(alpha = 0.35f)))),
        content = content
    )
}

/** Label section: besar & berwarna gradient, bukan kecil-pasif abu-abu. */
@Composable
private fun SectionLabel(text: String, accentColor: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = accentColor,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun FeatureControl(
    title: String,
    helpText: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColor2: Color = accentColor,
    wrapInCard: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val innerContent: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(accentColor, accentColor2))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    brush = Brush.linearGradient(listOf(accentColor2, accentColor))
                ),
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (helpText.isNotBlank()) {
            Text(
                helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accentColor2,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.18f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = "$title, $valueLabel" }
        )
    }

    if (wrapInCard) {
        GlassCard(accentColor = accentColor, accentColor2 = accentColor2) {
            Column(modifier = Modifier.padding(16.dp), content = innerContent)
        }
    } else {
        Column(content = innerContent)
    }
}
