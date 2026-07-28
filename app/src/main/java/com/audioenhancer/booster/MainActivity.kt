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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                    BoosterScreen(
                        onBass = { service?.setBassStrength(it) },
                        onVirtualizer = { service?.setVirtualizerStrength(it) },
                        onLoudness = { service?.setLoudnessGain(it) }
                    )
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
    onLoudness: (Float) -> Unit
) {
    var bass by remember { mutableStateOf(500f) }
    var virtualizer by remember { mutableStateOf(500f) }
    var loudness by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Audio Booster", style = MaterialTheme.typography.headlineMedium)
        Text("Efek berlaku ke seluruh audio sistem", style = MaterialTheme.typography.bodySmall)

        Text("Bass Boost: ${bass.toInt()}")
        Slider(value = bass, onValueChange = { bass = it; onBass(it.toInt().toShort()) }, valueRange = 0f..1000f)

        Text("Virtualizer / Kejernihan Stereo: ${virtualizer.toInt()}")
        Slider(value = virtualizer, onValueChange = { virtualizer = it; onVirtualizer(it.toInt().toShort()) }, valueRange = 0f..1000f)

        Text("Loudness Gain: ${loudness.toInt()} mB")
        Slider(value = loudness, onValueChange = { loudness = it; onLoudness(it) }, valueRange = 0f..3000f)

        Text(
            "Catatan: jika HP kamu pakai MIUI/ColorOS/EMUI, aktifkan juga 'Autostart' " +
            "dan matikan pembatasan baterai untuk app ini secara manual di pengaturan HP, " +
            "supaya service tidak dimatikan sistem.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
