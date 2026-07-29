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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var service: AudioEnhancerService? = null
    private var bound = false

    private var bassSupported by mutableStateOf(true)
    private var virtualizerSupported by mutableStateOf(true)
    private var loudnessSupported by mutableStateOf(true)
    private var notificationPermissionGranted by mutableStateOf(true)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as AudioEnhancerService.LocalBinder).getService()
            bound = true
            bassSupported = service?.isBassSupported() ?: true
            virtualizerSupported = service?.isVirtualizerSupported() ?: true
            loudnessSupported = service?.isLoudnessSupported() ?: true
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
            val darkTheme = when (themeMode) {
                PrefsHelper.THEME_MODE_LIGHT -> false
                PrefsHelper.THEME_MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }

            AudioEnhancerTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
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
                            onBass = { service?.setBassStrength(it) },
                            onVirtualizer = { service?.setVirtualizerStrength(it) },
                            onLoudness = { service?.setLoudnessGain(it) },
                            onOpenHelp = { showOnboarding = true },
                            bassSupported = bassSupported,
                            virtualizerSupported = virtualizerSupported,
                            loudnessSupported = loudnessSupported,
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
                if (isRunning) "Service berjalan di background — cek juga notifikasi 'Audio Booster aktif'"
                else "Service TIDAK berjalan — coba tutup & buka ulang app",
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

private val presets = listOf(
    Preset("Flat", bass = 0f, virtualizer = 0f, loudness = 0f),
    Preset("Bass Heavy", bass = 900f, virtualizer = 300f, loudness = 500f),
    Preset("Vocal Boost", bass = 200f, virtualizer = 600f, loudness = 800f),
    Preset("Treble Boost", bass = 100f, virtualizer = 800f, loudness = 600f)
)

@Composable
fun BoosterScreen(
    onBass: (Short) -> Unit,
    onVirtualizer: (Short) -> Unit,
    onLoudness: (Float) -> Unit,
    onOpenHelp: () -> Unit = {},
    bassSupported: Boolean = true,
    virtualizerSupported: Boolean = true,
    loudnessSupported: Boolean = true,
    initialBass: Float = 500f,
    initialVirtualizer: Float = 500f,
    initialLoudness: Float = 0f,
    initialActivePreset: String? = null,
    onActivePresetChange: (String?) -> Unit = {},
    notificationPermissionGranted: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
    themeMode: Int = PrefsHelper.THEME_MODE_SYSTEM,
    onThemeModeChange: (Int) -> Unit = {}
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                Text("Audio Booster", style = MaterialTheme.typography.headlineMedium)
                Text("Efek berlaku ke seluruh audio sistem", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeModeToggle(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
                IconButton(onClick = onOpenHelp) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Bantuan / penjelasan fitur")
                }
            }
        }

        ServiceStatusBadge()

        if (!notificationPermissionGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "🔕 Notifikasi belum diizinkan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Tanpa izin ini, notifikasi 'Audio Booster aktif' tidak akan muncul (service tetap jalan, tapi kamu tidak lihat indikatornya di status bar).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Button(onClick = onOpenNotificationSettings) {
                        Text("Buka Pengaturan Notifikasi")
                    }
                }
            }
        }

        if (!bassSupported || !virtualizerSupported || !loudnessSupported) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Sebagian efek tidak didukung chipset/HP ini dan otomatis dinonaktifkan di bawah. " +
                    "Efek lain tetap berfungsi normal.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column {
            Text("Preset Cepat", fontWeight = FontWeight.Bold)
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
            title = "🔊 Bass Boost",
            helpText = if (bassSupported) "Menguatkan nada rendah supaya musik terasa lebih 'nendang'. 0 = mati."
                       else "Tidak didukung di HP ini.",
            value = bass,
            valueLabel = bass.toInt().toString(),
            onValueChange = { bass = it; onBass(it.toInt().toShort()); activePreset = null; onActivePresetChange(null) },
            valueRange = 0f..1000f,
            enabled = bassSupported
        )

        FeatureControl(
            title = "🌐 Virtualizer",
            helpText = if (virtualizerSupported) "Membuat suara terasa lebih lebar, paling terasa saat pakai earphone/headset."
                       else "Tidak didukung di HP ini.",
            value = virtualizer,
            valueLabel = virtualizer.toInt().toString(),
            onValueChange = { virtualizer = it; onVirtualizer(it.toInt().toShort()); activePreset = null; onActivePresetChange(null) },
            valueRange = 0f..1000f,
            enabled = virtualizerSupported
        )

        FeatureControl(
            title = "📢 Loudness Gain",
            helpText = if (loudnessSupported) "Boost volume tambahan di atas batas normal HP. Turunkan kalau suara mulai pecah."
                       else "Tidak didukung di HP ini.",
            value = loudness,
            valueLabel = "${loudness.toInt()} mB",
            onValueChange = { loudness = it; onLoudness(it); activePreset = null; onActivePresetChange(null) },
            valueRange = 0f..3000f,
            enabled = loudnessSupported
        )

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🛡️ Kenapa perlu izin baterai & autostart?", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Supaya booster tetap jalan walau HP di-lock atau app di-scroll dari recent apps. " +
                    "Di HP MIUI/ColorOS/EMUI, aktifkan juga 'Autostart' secara manual di pengaturan HP.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        TextButton(onClick = onOpenHelp) {
            Text("Lihat penjelasan lengkap tiap fitur →")
        }
    }
}

/** Satu tombol ikon yang berputar antar 3 mode: ikut sistem → terang → gelap → (ulang). */
@Composable
private fun ThemeModeToggle(themeMode: Int, onThemeModeChange: (Int) -> Unit) {
    val (icon, description) = when (themeMode) {
        PrefsHelper.THEME_MODE_LIGHT -> Icons.Filled.LightMode to "Tema: Terang (tap untuk ganti ke Gelap)"
        PrefsHelper.THEME_MODE_DARK -> Icons.Filled.DarkMode to "Tema: Gelap (tap untuk ganti ke Ikuti Sistem)"
        else -> Icons.Filled.Brightness4 to "Tema: Ikuti Sistem (tap untuk ganti ke Terang)"
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
        Text(
            helpText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, enabled = enabled)
    }
}
