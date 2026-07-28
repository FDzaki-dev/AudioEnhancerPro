package com.audioenhancer.booster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var service: AudioEnhancerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as AudioEnhancerService.LocalBinder).getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, AudioEnhancerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        requestIgnoreBatteryOptimizations()

        setContent {
            MaterialTheme {
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
                            onOpenHelp = { showOnboarding = true }
                        )
                    }
                }
            }
        }
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

    override fun onDestroy() {
        if (bound) { unbindService(connection); bound = false }
        super.onDestroy()
    }
}

@Composable
fun BoosterScreen(
    onBass: (Short) -> Unit,
    onVirtualizer: (Short) -> Unit,
    onLoudness: (Float) -> Unit,
    onOpenHelp: () -> Unit = {}
) {
    var bass by remember { mutableStateOf(500f) }
    var virtualizer by remember { mutableStateOf(500f) }
    var loudness by remember { mutableStateOf(0f) }

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
            IconButton(onClick = onOpenHelp) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Bantuan / penjelasan fitur")
            }
        }

        FeatureControl(
            title = "🔊 Bass Boost",
            helpText = "Menguatkan nada rendah supaya musik terasa lebih 'nendang'. 0 = mati.",
            value = bass,
            valueLabel = bass.toInt().toString(),
            onValueChange = { bass = it; onBass(it.toInt().toShort()) },
            valueRange = 0f..1000f
        )

        FeatureControl(
            title = "🌐 Virtualizer",
            helpText = "Membuat suara terasa lebih lebar, paling terasa saat pakai earphone/headset.",
            value = virtualizer,
            valueLabel = virtualizer.toInt().toString(),
            onValueChange = { virtualizer = it; onVirtualizer(it.toInt().toShort()) },
            valueRange = 0f..1000f
        )

        FeatureControl(
            title = "📢 Loudness Gain",
            helpText = "Boost volume tambahan di atas batas normal HP. Turunkan kalau suara mulai pecah.",
            value = loudness,
            valueLabel = "${loudness.toInt()} mB",
            onValueChange = { loudness = it; onLoudness(it) },
            valueRange = 0f..3000f
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

@Composable
private fun FeatureControl(
    title: String,
    helpText: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
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
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}
