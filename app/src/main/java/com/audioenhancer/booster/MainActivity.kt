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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var service: AudioEnhancerService? = null
    private var bound = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val intent = Intent(this, AudioEnhancerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        requestIgnoreBatteryOptimizations()

        setContent {
            var themeMode by remember { mutableStateOf(PrefsHelper.getThemeMode(this@MainActivity)) }
            var useDynamicColor by remember { mutableStateOf(PrefsHelper.getUseDynamicColor(this@MainActivity)) }
            val darkTheme = when (themeMode) {
                PrefsHelper.THEME_MODE_LIGHT -> false
                PrefsHelper.THEME_MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }

            AudioEnhancerTheme(darkTheme = darkTheme, useDynamicColor = useDynamicColor) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
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
                            }
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
private fun ServiceStatusBadge() {
    var isRunning by remember { mutableStateOf(AudioEnhancerService.isRunning) }

    // Cek status tiap 1 detik selagi layar ini terbuka, biar badge selalu akurat.
    LaunchedEffect(Unit) {
        while (true) {
            isRunning = AudioEnhancerService.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isRunning) androidx.compose.ui.graphics.Color(0xFF30D158) else androidx.compose.ui.graphics.Color(0xFFFF453A))
            )
            Text(
                if (isRunning) stringResource(R.string.status_running)
                else stringResource(R.string.status_not_running),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class Preset(
    val label: String,
    val bass: Float,
    val virtualizer: Float,
    val loudness: Float
)

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
    onUseDynamicColorChange: (Boolean) -> Unit = {}
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

    fun applyPreset(preset: Preset) {
        bass = preset.bass; onBass(preset.bass.toInt().toShort())
        virtualizer = preset.virtualizer; onVirtualizer(preset.virtualizer.toInt().toShort())
        loudness = preset.loudness; onLoudness(preset.loudness)
        activePreset = preset.label
        onActivePresetChange(preset.label)
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
                Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeModeToggle(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                IconButton(onClick = onOpenHelp) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.cd_help))
                }
            }
        }

        ServiceStatusBadge()

        if (!notificationPermissionGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.notif_perm_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    stringResource(R.string.unsupported_banner),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if ((bassSupported && !bassStrengthSupported) || (virtualizerSupported && !virtualizerStrengthSupported)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    stringResource(R.string.strength_unsupported_banner),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column {
            Text(stringResource(R.string.presets_title), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = activePreset == preset.label,
                        onClick = { applyPreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
            }
        }

        FeatureControl(
            title = stringResource(R.string.feature_bass_title),
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
                initialLevels = equalizerInitialLevels,
                onBandChange = { band, level ->
                    onEqualizerBand(band, level)
                    activePreset = null; onActivePresetChange(null)
                }
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = useDynamicColor,
                            onValueChange = onUseDynamicColorChange,
                            role = Role.Switch
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.battery_card_title), fontWeight = FontWeight.Bold)
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
    onBandChange: (Int, Short) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val levels = remember(bandCount) {
        mutableStateListOf(*Array(bandCount) { i -> initialLevels.getOrElse(i) { 0 } })
    }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.eq_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.eq_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        valueRange = levelMin.toFloat()..levelMax.toFloat()
                    )
                }
            }
        }
    }
}

internal fun formatFreqLabel(hz: Int): String =
    if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

@Composable
private fun FeatureControl(
    title: String,
    helpText: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (helpText.isNotBlank()) {
            Text(
                helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$title, $valueLabel" }
        )
    }
}
