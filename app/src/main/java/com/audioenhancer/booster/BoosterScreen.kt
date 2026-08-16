package com.audioenhancer.booster

// Batch 16: dipecah dari MainActivity.kt (God Activity split, audit High-priority item).
// Berisi layar utama (BoosterScreen) + composable pendukungnya yang SPESIFIK ke layar ini
// (ServiceStatusBadge, PowerToggleRow, CrashBanner, EqualizerSection, Preset). Semua
// tetap `private` (dipakai cuma di dalam file ini) KECUALI `BoosterScreen` sendiri
// (dipanggil dari MainActivity.kt) dan `formatFreqLabel` (sudah `internal` sebelumnya,
// ada test unit-nya di FormatFreqLabelTest.kt). Komponen visual generik (SkeuCard dkk)
// ada di SkeuomorphicComponents.kt.
// Batch 31: ThemeModeToggle DIHAPUS — app WAJIB dark-mode, tidak ada lagi pilihan
// terang/ikuti sistem (lihat PROJECT_STATE.md).

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
private fun ServiceStatusBadge(onRestartService: () -> Unit = {}) {
    var isRunning by remember { mutableStateOf(AudioEnhancerService.isRunning) }
    val haptics = LocalHapticFeedback.current

    // Cek status tiap 1 detik selagi layar ini terbuka, biar badge selalu akurat.
    LaunchedEffect(Unit) {
        while (true) {
            isRunning = AudioEnhancerService.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    val statusTint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    SkeuTintedCard(tint = statusTint) {
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
                // Batch 24: ripple Material3 default dimatikan, konsisten sama
                // SkeuPowerButton (Batch 15) — feedback tekan sekarang murni dari
                // scale (SkeuPowerButton), bukan ripple, di SELURUH komponen interaktif.
                CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
                    Button(onClick = {
                        onRestartService()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) {
                        Text(stringResource(R.string.restart_service))
                    }
                }
            }
        }
    }
}

/** Batch 13: tombol toggle utama "Aktif/Nonaktif" — porting 1:1 dari `.power-row`/`.power-btn`
 *  di docs/preview/current.html. State polling isRunning SENGAJA dibuat independen dari
 *  ServiceStatusBadge (pola yang sama, duplikasi kecil disengaja) supaya composable ini
 *  tetap berdiri sendiri dan tidak mengubah perilaku badge yang sudah ada.
 *  Aksi start/stop pakai AudioEnhancerService.requestStart/requestStop — fungsi yang sama
 *  dipakai ShortcutHelper (long-press ikon launcher) & QuickToggleTileService, jadi semua
 *  entry point toggle konsisten satu sumber kebenaran. */
@Composable
private fun PowerToggleRow() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var isRunning by remember { mutableStateOf(AudioEnhancerService.isRunning) }

    LaunchedEffect(Unit) {
        while (true) {
            isRunning = AudioEnhancerService.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeuPowerButton(
            pressed = isRunning,
            ringColor = if (isRunning) MaterialTheme.colorScheme.primary else null,
            onClick = {
                if (isRunning) AudioEnhancerService.requestStop(context)
                else AudioEnhancerService.requestStart(context)
                isRunning = !isRunning
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            contentDescription = stringResource(R.string.cd_power_toggle)
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
        Column {
            Text(
                if (isRunning) stringResource(R.string.power_toggle_on_label) else stringResource(R.string.power_toggle_off_label),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (isRunning) stringResource(R.string.power_toggle_on_desc) else stringResource(R.string.power_toggle_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSkeuTokens.current.mutedText
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

/** Batch 26: batas panjang nama custom preset — lihat komentar di pemakaiannya
 *  (dialog simpan preset) buat alasan lengkap. */
private const val PRESET_NAME_MAX_LENGTH = 24
/** Heads-up kecil kalau app sempat crash sejak terakhir dibuka — sebelum ini,
 *  satu-satunya jejak crash adalah notifikasi "aktif" yang tiba-tiba hilang tanpa
 *  penjelasan. Cuma muncul sekali per insiden (ditandai "sudah dilihat" saat ditutup). */
@Composable
private fun CrashBanner(onCrashLogsDeleted: () -> Unit = {}) {
    val context = LocalContext.current
    var crashEntry by remember {
        mutableStateOf(if (CrashLogger.hasUnseenCrash(context)) CrashLogger.latestCrashLog(context) else null)
    }
    var showDialog by remember { mutableStateOf(false) }
    val entry = crashEntry ?: return

    fun dismiss() {
        showDialog = false
        CrashLogger.markCrashSeen(context)
        crashEntry = null
    }

    SkeuTintedCard(tint = MaterialTheme.colorScheme.error) {
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
        val crashText = remember(entry) { entry.readText(context) }
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
                    onCrashLogsDeleted()
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
    // Batch 58: EffectState (Batch 57) di-poll ViewModel, dipass ke sini buat helpText
    // yang lebih spesifik (FAILED/CONTROL_LOST) — default ENABLED biar preview/pemanggil
    // lama yang belum kasih parameter ini tidak berubah perilaku (backward-compatible).
    bassEffectState: AudioEnhancerService.EffectState = AudioEnhancerService.EffectState.ENABLED,
    virtualizerEffectState: AudioEnhancerService.EffectState = AudioEnhancerService.EffectState.ENABLED,
    loudnessEffectState: AudioEnhancerService.EffectState = AudioEnhancerService.EffectState.ENABLED,
    equalizerEffectState: AudioEnhancerService.EffectState = AudioEnhancerService.EffectState.ENABLED,
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
    useDynamicColor: Boolean = false,
    onUseDynamicColorChange: (Boolean) -> Unit = {},
    // Batch 38: appThemeStyleKey ganti dari Boolean (themeStyleIsRadical, Batch 36)
    // jadi String langsung — mewadahi >2 varian tema (sekarang 3: Midnight Glass /
    // Aurora Glass / Skeuomorphism) tanpa perlu Boolean kedua yang gampang rancu.
    // Default `PrefsHelper.APP_THEME_AMOLED_GLASS` konsisten dengan `MainActivity.kt`.
    appThemeStyleKey: String = PrefsHelper.APP_THEME_AMOLED_GLASS,
    onThemeStyleChange: (String) -> Unit = {},
    connectionState: BoosterViewModel.ConnectionState = BoosterViewModel.ConnectionState.CONNECTED,
    onRetryConnection: () -> Unit = {},
    onRestartService: () -> Unit = {},
    requestedCustomPresetName: String? = null,
    onRequestedPresetConsumed: () -> Unit = {}
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
    // Feedback sukses eksplisit (simpan/hapus preset, hapus log crash) — sebelumnya cuma
    // haptic + dialog tertutup diam-diam, tidak ada konfirmasi visual sama sekali kalau
    // aksi itu benar-benar berhasil (Fase 3 roadmap: "loading/success/error state feedback").
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun showSnackbar(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

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

    // Preset custom yang diminta lewat App Shortcut (long-press ikon launcher). LaunchedEffect
    // dipakai (bukan langsung di body) supaya cuma jalan sekali per request, dan supaya
    // customPresets sempat ke-load duluan sebelum dicari — kalau presetnya udah kehapus di
    // antara shortcut dibuat & di-tap, ya didiamkan saja (gak ada preset buat diterapkan).
    LaunchedEffect(requestedCustomPresetName, customPresets) {
        if (requestedCustomPresetName != null) {
            customPresets.firstOrNull { it.name == requestedCustomPresetName }?.let { applyCustomPreset(it) }
            onRequestedPresetConsumed()
        }
    }

    // Sinkronkan dynamic shortcut sekali tiap layar ini kebuka — jaring pengaman kalau ada
    // preset yang sempat berubah di luar sesi Compose ini (jarang terjadi, tapi murah kok).
    LaunchedEffect(Unit) {
        ShortcutHelper.refreshCustomPresetShortcuts(context)
    }

    // Di layar lebar (tablet/foldable), konten dibatasi max 600dp dan ditengahkan supaya
    // slider/kartu tidak melebar aneh sampai ke tepi — di HP biasa (layar < 600dp) perilakunya
    // tetap sama seperti sebelumnya (full width).
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                // Batch 12: judul warna SOLID onBackground (bukan gradient-clip lagi) —
                // kontras maksimum, konsisten di dark & light theme.
                Text(
                    stringResource(R.string.app_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenHelp) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.cd_help))
                }
            }
        }

        // Batch 13: power toggle "Aktif/Nonaktif". Ditaruh persis di posisi yang sama
        // seperti mockup: tepat di bawah header, sebelum status card service.
        PowerToggleRow()

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
        CrashBanner(onCrashLogsDeleted = { showSnackbar(context.getString(R.string.crash_logs_deleted_message)) })

        when (connectionState) {
            BoosterViewModel.ConnectionState.CONNECTING -> {
                SkeuCard {
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
            BoosterViewModel.ConnectionState.ERROR -> {
                SkeuTintedCard(tint = MaterialTheme.colorScheme.error) {
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
                        CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
                            Button(onClick = {
                                onRetryConnection()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) {
                                Text(stringResource(R.string.connection_retry))
                            }
                        }
                    }
                }
            }
            BoosterViewModel.ConnectionState.CONNECTED -> { /* tidak perlu tampilkan apa-apa */ }
        }

        if (!notificationPermissionGranted) {
            SkeuTintedCard(tint = MaterialTheme.colorScheme.error) {
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
                    CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
                        Button(onClick = {
                            onOpenNotificationSettings()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }) {
                            Text(stringResource(R.string.notif_perm_button))
                        }
                    }
                }
            }
        }

        if (!bassSupported || !virtualizerSupported || !loudnessSupported) {
            SkeuTintedCard(tint = MaterialTheme.colorScheme.error) {
                Text(
                    stringResource(R.string.unsupported_banner),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if ((bassSupported && !bassStrengthSupported) || (virtualizerSupported && !virtualizerStrengthSupported)) {
            SkeuTintedCard(tint = MaterialTheme.colorScheme.primary) {
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
                CompositionLocalProvider(LocalIndication provides NoRippleIndication) {
                presets.forEach { preset ->
                    val selected = activePreset == preset.label
                    Box(modifier = Modifier.then(if (selected) Modifier.skeuGlow(LocalSkeuTokens.current.primaryGlow, spread = 8.dp) else Modifier)) {
                        FilterChip(
                            selected = selected,
                            onClick = { applyPreset(preset) },
                            label = { Text(preset.label) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = LocalSkeuTokens.current.mutedText,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                customPresets.forEach { custom ->
                    val selected = activePreset == custom.name
                    Box(modifier = Modifier.then(if (selected) Modifier.skeuGlow(LocalSkeuTokens.current.primaryGlow, spread = 8.dp) else Modifier)) {
                        FilterChip(
                            selected = selected,
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
                                labelColor = LocalSkeuTokens.current.mutedText,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                AssistChip(
                    onClick = { presetNameInput = ""; showSavePresetDialog = true },
                    label = { Text(stringResource(R.string.preset_save_chip)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50)
                )
                }
            }
            // Batch 30: empty state — sebelumnya user baru yang belum pernah simpan preset
            // custom cuma lihat 4 chip bawaan + 1 chip "Simpan" tanpa konteks apapun (chip
            // "Simpan" doang gak menjelaskan APA yang disimpan/KAPAN berguna). Hint 1 baris
            // ini CUMA muncul kalau belum ada preset custom sama sekali — begitu user
            // nyimpen 1 preset pertama, hint otomatis hilang (gak numpuk jadi noise permanen).
            if (customPresets.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.presets_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSkeuTokens.current.mutedText
                )
            }
        }

        if (showSavePresetDialog) {
            // Cegah nama custom preset sama persis (case-insensitive) dengan salah satu
            // dari 4 preset bawaan — tanpa ini, chip built-in & chip custom bisa
            // sama-sama ke-highlight "selected" bareng saat activePreset match nama itu,
            // state visual jadi ambigu meski fungsinya sendiri tetap benar.
            //
            // Batch 8: cek yang sama juga WAJIB diterapkan ke SESAMA preset custom lain
            // (bukan cuma built-in) — sebelumnya cuma dicek ke `presets` (built-in), jadi
            // "Rock" dan "rock" bisa lolos jadi 2 custom preset terpisah yang isinya beda
            // tapi labelnya nyaris identik (dynamic shortcut & chip jadi membingungkan).
            // `it.name != trimmedPresetName` sengaja dikecualikan supaya nyimpen ulang
            // preset custom dengan nama PERSIS SAMA (exact match) tetap diizinkan —
            // itu perilaku "timpa yang lama" yang memang disengaja di
            // PrefsHelper.addCustomPreset, bukan bug.
            val trimmedPresetName = presetNameInput.trim()
            val nameCollidesWithBuiltIn = presets.any { it.label.equals(trimmedPresetName, ignoreCase = true) } ||
                customPresets.any { it.name != trimmedPresetName && it.name.equals(trimmedPresetName, ignoreCase = true) }
            AlertDialog(
                onDismissRequest = { showSavePresetDialog = false },
                title = { Text(stringResource(R.string.preset_save_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = presetNameInput,
                        // Batch 26: batas 24 karakter — tanpa ini nama panjang bikin chip
                        // preset (FilterChip lebar scroll horizontal) jadi meluber/kepotong
                        // aneh & dynamic shortcut (ShortcutHelper, label ikon launcher) juga
                        // ke-truncate paksa oleh sistem tanpa peringatan ke user.
                        onValueChange = { if (it.length <= PRESET_NAME_MAX_LENGTH) presetNameInput = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.preset_save_dialog_hint)) },
                        isError = nameCollidesWithBuiltIn,
                        supportingText = {
                            if (nameCollidesWithBuiltIn) {
                                Text(
                                    stringResource(R.string.preset_save_name_collision_error),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    stringResource(
                                        R.string.preset_save_char_count,
                                        presetNameInput.length,
                                        PRESET_NAME_MAX_LENGTH
                                    ),
                                    color = LocalSkeuTokens.current.mutedText
                                )
                            }
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = trimmedPresetName.isNotBlank() && !nameCollidesWithBuiltIn,
                        onClick = {
                            val newPreset = PrefsHelper.CustomPreset(trimmedPresetName, bass, virtualizer, loudness)
                            PrefsHelper.addCustomPreset(context, newPreset)
                            customPresets = PrefsHelper.getCustomPresets(context)
                            ShortcutHelper.refreshCustomPresetShortcuts(context)
                            activePreset = newPreset.name
                            onActivePresetChange(newPreset.name)
                            showSavePresetDialog = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSnackbar(context.getString(R.string.preset_saved_message, newPreset.name))
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
                        ShortcutHelper.refreshCustomPresetShortcuts(context)
                        if (activePreset == nameToDelete) { activePreset = null; onActivePresetChange(null) }
                        presetPendingDelete = null
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSnackbar(context.getString(R.string.preset_deleted_message, nameToDelete))
                    }) { Text(stringResource(R.string.preset_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { presetPendingDelete = null }) {
                        Text(stringResource(R.string.preset_delete_cancel))
                    }
                }
            )
        }

        // Batch 13: label section "Kontrol" — porting dari docs/preview/current.html,
        // sebelumnya kartu Bass/Virtualizer/Loudness langsung tampil tanpa header section.
        SectionLabel(stringResource(R.string.controls_title))

        FeatureControl(
            title = stringResource(R.string.feature_bass_title),
            icon = Icons.Filled.VolumeUp,
            accentColor = BassAccent,
            accentColor2 = BassAccent2,
            helpText = when {
                !bassSupported -> stringResource(R.string.feature_help_unsupported)
                // Batch 58: CONTROL_LOST/FAILED (Batch 57) diprioritaskan di atas cek
                // strength_unsupported — dua-duanya soal "effect ada tapi lagi
                // bermasalah", bukan soal chipset gak punya fitur kontrol granular.
                bassEffectState == AudioEnhancerService.EffectState.CONTROL_LOST ->
                    stringResource(R.string.feature_help_control_lost)
                bassEffectState == AudioEnhancerService.EffectState.FAILED ->
                    stringResource(R.string.feature_help_failed)
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
                virtualizerEffectState == AudioEnhancerService.EffectState.CONTROL_LOST ->
                    stringResource(R.string.feature_help_control_lost)
                virtualizerEffectState == AudioEnhancerService.EffectState.FAILED ->
                    stringResource(R.string.feature_help_failed)
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
            helpText = when {
                !loudnessSupported -> stringResource(R.string.feature_help_unsupported)
                loudnessEffectState == AudioEnhancerService.EffectState.CONTROL_LOST ->
                    stringResource(R.string.feature_help_control_lost)
                loudnessEffectState == AudioEnhancerService.EffectState.FAILED ->
                    stringResource(R.string.feature_help_failed)
                else -> stringResource(R.string.feature_loudness_help_normal)
            },
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
            SkeuCard {
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
                            color = LocalSkeuTokens.current.mutedText
                        )
                    }
                    SkeuSwitch(checked = useDynamicColor, onCheckedChange = null)
                }
            }
        }

        // Batch 36: switch "Gaya Tampilan Radikal" — pilih antar sistem desain
        // (default Midnight Glass vs Aurora Glass vs Skeuomorphism, Batch 38).
        // SkeuCard/SkeuSwitch di sini otomatis ikut re-render pakai token tema yang
        // BARU dipilih (LocalSkeuTokens di-provide ulang dari MainActivity begitu
        // `onThemeStyleChange` mengubah state di atas AudioEnhancerTheme).
        // Batch 38: appThemeStyleKey adalah 1 nilai String tunggal (3 pilihan saling
        // eksklusif) — nyalain toggle ini otomatis matiin toggle Skeuomorphism di
        // bawahnya (dan sebaliknya); matiin salah satu toggle balik ke default
        // Midnight Glass (APP_THEME_AMOLED_GLASS).
        SkeuCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = appThemeStyleKey == PrefsHelper.APP_THEME_RADICAL_SKEUO,
                        onValueChange = { isOn ->
                            onThemeStyleChange(
                                if (isOn) PrefsHelper.APP_THEME_RADICAL_SKEUO else PrefsHelper.APP_THEME_AMOLED_GLASS
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        role = Role.Switch
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.theme_style_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.theme_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSkeuTokens.current.mutedText
                    )
                }
                SkeuSwitch(checked = appThemeStyleKey == PrefsHelper.APP_THEME_RADICAL_SKEUO, onCheckedChange = null)
            }
        }

        // Batch 38: switch "Skeuomorphism" — varian ke-3, SATU tingkat sejajar dengan
        // Aurora Glass di atas (bukan sub-opsi-nya), bahasa desain BEDA total dari 2
        // varian glass (bevel/shadow/tekstur fisik realistis, aksen metalik hangat,
        // BUKAN kaca/blue-tint). Diminta user eksplisit "gak kurang gak lebih" — jadi
        // scope-nya cuma nambah toggle ini + token visual-nya (Theme.kt/
        // SkeuomorphicComponents.kt), TIDAK menyentuh fitur lain.
        SkeuCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = appThemeStyleKey == PrefsHelper.APP_THEME_SKEUOMORPHISM,
                        onValueChange = { isOn ->
                            onThemeStyleChange(
                                if (isOn) PrefsHelper.APP_THEME_SKEUOMORPHISM else PrefsHelper.APP_THEME_AMOLED_GLASS
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        role = Role.Switch
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.theme_style_skeuo_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.theme_style_skeuo_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSkeuTokens.current.mutedText
                    )
                }
                SkeuSwitch(checked = appThemeStyleKey == PrefsHelper.APP_THEME_SKEUOMORPHISM, onCheckedChange = null)
            }
        }

        // Batch 43: switch "Studio Equalizer" — varian ke-4, sejajar 2 di atas
        // (bukan sub-opsi). Neumorphism soft-UI, palet abu-abu studio gelap + aksen
        // neon-lime khusus state aktif, diminta user dengan 4 warna HEX eksak
        // ("Background/Base #1E222A, Dark Shadow #14171D, Light Shadow #282D37,
        // Aksen Glow (Aktif) #39FF14").
        SkeuCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = appThemeStyleKey == PrefsHelper.APP_THEME_STUDIO_EQ,
                        onValueChange = { isOn ->
                            onThemeStyleChange(
                                if (isOn) PrefsHelper.APP_THEME_STUDIO_EQ else PrefsHelper.APP_THEME_AMOLED_GLASS
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        role = Role.Switch
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.theme_style_studioeq_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.theme_style_studioeq_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSkeuTokens.current.mutedText
                    )
                }
                SkeuSwitch(checked = appThemeStyleKey == PrefsHelper.APP_THEME_STUDIO_EQ, onCheckedChange = null)
            }
        }

        SkeuCard {
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
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { OemAutostartHelper.openAutostartSettings(context) }) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (OemAutostartHelper.deviceLikelyNeedsAutostart())
                            stringResource(R.string.battery_autostart_button)
                        else
                            stringResource(R.string.battery_autostart_button_generic)
                    )
                }
            }
        }

        TextButton(onClick = onOpenHelp) {
            Text(stringResource(R.string.see_full_explanation))
        }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
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

    SkeuCard {
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
                            color = LocalSkeuTokens.current.mutedText
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
