package com.audioenhancer.booster

// Batch 73: layar baru, entry point cek-update MANUAL — user tegur eksplisit gak ada
// cara mantau/trigger update selain nunggu banner otomatis (UpdateBanner,
// BoosterScreen.kt) yang cuma nongol KALAU ada rilis baru & disembunyikan total kalau
// enggak (checkForUpdate() jalan diam-diam, gagalnya pun ditelan — lihat UpdateManager.kt).
// Batch 81 (REVISI Batch 73 — user keluhkan hasil "ketemu update" gak informatif +
// maksa bolak-balik tab ke layar utama cuma buat unduh): section ini SEKARANG juga
// nampilkan ringkasan 1-baris rilis + tombol unduh LANGSUNG di sini begitu ketemu
// update — BUKAN lagi cuma "lihat banner di layar utama". Tetap 0 logic unduh
// baru/duplikat: reuse penuh state (`updateDownloadProgress` dkk) & fungsi
// (`downloadAndInstallUpdate()`) yang sudah ada di ViewModel, yang SAMA dipakai
// UpdateBanner — UpdateBanner tetap muncul juga kalau user balik ke layar utama
// (state `updateInfo` dibagi bareng), cuma sekarang bukan satu-satunya jalan lagi.

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    appVersionName: String,
    manualUpdateCheckState: BoosterViewModel.ManualUpdateCheckState,
    foundUpdateInfo: UpdateManager.UpdateInfo?,
    updateDownloadProgress: Float?,
    updateDownloadFailed: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // Batch 97: dibaca+ditulis LANGSUNG di sini (pola sama seperti `customPresets` di
    // BoosterScreen.kt) — SENGAJA TIDAK di-hoist ke MainActivity.kt (0 param/callback baru
    // di SettingsScreen/BoosterScreen buat ini). Aman karena MainActivity.kt me-render
    // BoosterScreen/SettingsScreen lewat percabangan if/else-if/else yang SALING EKSKLUSIF
    // (lihat showSettings) — pindah balik ke BoosterScreen selalu berarti composable itu
    // masuk ulang dari awal (state lama dibuang), jadi `remember` di BoosterScreen otomatis
    // baca nilai TERBARU dari PrefsHelper begitu user tekan tombol kembali di sini, tanpa
    // butuh state di-hoist sama sekali.
    var useHorizontalLayout by remember { mutableStateOf(PrefsHelper.getUseHorizontalTabLayout(context)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(22.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Batch 90: SEBELUMNYA pakai headlineMedium (sama token dengan judul
            // "Boomly" di BoosterScreen.kt) — sejak token itu dinaikkan ke skala
            // iOS Large Title asli (34sp, roadmap.md Fase 7 Fase 2 opsi B), title
            // di sini jadi kegedean buat muat 1 baris di sebelah tombol back.
            // Pola iOS asli: layar yang di-push (bukan root) pakai title INLINE
            // kecil di navigation bar (17pt Semibold), Large Title cuma dipakai
            // root screen — persis kasus di sini (SettingsScreen dibuka dari
            // ikon ⚙️ BoosterScreen, bukan root). `titleMedium` (17sp SemiBold,
            // SUDAH ADA, tidak berubah) pas persis buat pola ini, 0 token baru.
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel(text = stringResource(R.string.settings_title))
        // Batch 91 (roadmap.md Fase 7 Fase 2 opsi A, "Grouped-list SettingsScreen.kt"):
        // diaudit dulu sebelum ubah apa pun (bukan langsung refactor) — screen ini
        // SEBELUMNYA render versi-app sebagai 1 baris teks gabung ("Versi aplikasi: X")
        // lalu tombol full-width nempel persis di bawahnya TANPA pemisah visual antara
        // "info" dan "aksi" — beda dari pola iOS Settings asli (Settings > General >
        // About: baris "Version" [label kiri, value kanan] TERPISAH dari baris aksi di
        // bawahnya lewat garis tipis grouped-list). Konten LAIN di card ini (tombol cek
        // update + status/notes/download conditional Batch 73/81) TIDAK direstruktur —
        // itu 1 alur aksi tunggal, bukan beberapa baris sejajar kayak Bass/Virtualizer/
        // Loudness, jadi TIDAK butuh dipecah lagi jadi row-row terpisah (ZERO-REFACTOR
        // bagian yang gak relevan ke task).
        SkeuCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                // Baris versi: label kiri + value kanan, pola row iOS asli ("Version  17.2"),
                // BUKAN 1 baris gabung "Versi aplikasi: 17.2" lagi. String lama
                // `settings_app_version_label` (format gabungan) sudah tidak dipakai di sini,
                // ganti `settings_app_version_row_label` (label polos, 0 placeholder).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.settings_app_version_row_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        appVersionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalSkeuTokens.current.mutedText
                    )
                }

                // Batch 92 (fix validasi screenshot 349772.jpg): `SkeuGroupDivider()`
                // default `startIndent=50.dp` DIRANCANG buat skip lebar icon-box 40dp +
                // spacing Row 10dp di `FeatureControl` (biar divider align ke bawah TEKS
                // judul, bukan ke bawah icon — lihat komentar `SkeuGroupDivider` di
                // `SkeuomorphicComponents.kt`). Baris "Versi Aplikasi" di sini TIDAK
                // punya icon sama sekali, jadi 50dp default itu jadi indent NYASAR yang
                // gak align ke elemen apa pun di atasnya (screenshot user nunjukin garis
                // mulai jauh di kanan dari teks "Versi Aplikasi", padahal harusnya flush).
                // `startIndent=0.dp` biar align rata sama teks di atasnya, bukan ngikut
                // asumsi icon-box yang gak ada di baris ini.
                SkeuGroupDivider(startIndent = 0.dp)

                val isChecking = manualUpdateCheckState == BoosterViewModel.ManualUpdateCheckState.CHECKING
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !isChecking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_check_update_button))
                }

                val statusText = when (manualUpdateCheckState) {
                    BoosterViewModel.ManualUpdateCheckState.CHECKING ->
                        stringResource(R.string.settings_checking_update)
                    BoosterViewModel.ManualUpdateCheckState.UP_TO_DATE ->
                        stringResource(R.string.settings_up_to_date)
                    BoosterViewModel.ManualUpdateCheckState.FOUND ->
                        stringResource(R.string.settings_update_found, appVersionName, foundUpdateInfo?.versionName ?: "")
                    BoosterViewModel.ManualUpdateCheckState.ERROR ->
                        stringResource(R.string.settings_check_update_failed)
                    BoosterViewModel.ManualUpdateCheckState.IDLE -> null
                }
                if (statusText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val statusColor = when (manualUpdateCheckState) {
                        BoosterViewModel.ManualUpdateCheckState.ERROR -> MaterialTheme.colorScheme.error
                        BoosterViewModel.ManualUpdateCheckState.FOUND,
                        BoosterViewModel.ManualUpdateCheckState.UP_TO_DATE -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                }

                // Batch 81 (diminta user — feedback "ketemu update" sebelumnya cuma
                // nyuruh pindah ke layar utama buat lihat detail/unduh, dikeluhkan
                // bolak-balik tab & gak informatif): tampilkan ringkasan 1-baris
                // (UpdateManager.extractReleaseSummary, BUKAN link changelog
                // selengkapnya) + tombol unduh LANGSUNG di sini. Reuse state/fungsi
                // unduh yang SUDAH ADA di ViewModel (dipakai bareng UpdateBanner di
                // BoosterScreen) — 0 logic unduh baru/duplikat, cuma wiring.
                if (manualUpdateCheckState == BoosterViewModel.ManualUpdateCheckState.FOUND &&
                    foundUpdateInfo != null
                ) {
                    if (foundUpdateInfo.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_whats_new_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            foundUpdateInfo.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val isDownloading = updateDownloadProgress != null
                    OutlinedButton(
                        onClick = onDownloadAndInstall,
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    R.string.update_downloading_label,
                                    ((updateDownloadProgress ?: 0f) * 100).toInt()
                                )
                            )
                        } else {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.update_download_button))
                        }
                    }
                    if (updateDownloadFailed) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.update_download_failed_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Batch 97: request eksplisit user — revert layar utama dari tab horizontal
        // (Batch 94-96) balik ke 1 scroll vertikal SEBAGAI DEFAULT, mode tab horizontal
        // TETAP ADA tapi dipindah jadi opsi custom opt-in di sini (BUKAN dihapus — 0
        // logic BoosterScreen.kt dibuang, cuma di-gate di balik toggle ini).
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel(text = stringResource(R.string.settings_layout_section_title))
        SkeuCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = useHorizontalLayout,
                        onValueChange = {
                            useHorizontalLayout = it
                            PrefsHelper.setUseHorizontalTabLayout(context, it)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        role = Role.Switch
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_horizontal_layout_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.settings_horizontal_layout_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSkeuTokens.current.mutedText
                    )
                }
                SkeuSwitch(checked = useHorizontalLayout, onCheckedChange = null)
            }
        }

        // Batch 132 (instruksi eksplisit user, alasan baterai): kartu "Pemulihan Cepat"
        // (izin Alarm & pengingat, Batch 128) DIHAPUS — heartbeat exact-alarm di
        // baliknya sudah dimatikan permanen (`ServiceWatchdogWorker.scheduleExactRecovery()`
        // sekarang no-op). Mempertahankan kartu ini akan menampilkan status/tombol izin
        // yang TIDAK LAGI berpengaruh ke behavior apa pun — UI bohong, lebih baik dihapus
        // daripada dibiarkan. String `settings_fast_recovery_*` di strings.xml sengaja
        // TIDAK dihapus batch ini (di luar scope Tunnel Vision, 0 dampak fungsional
        // dibiarkan — lihat "Temuan terbuka"). Pemulihan otomatis sekarang: watchdog 15
        // menit saja (`ServiceWatchdogWorker`, tidak berubah).
        // Batch 119 (Fase 8 roadmap item B, "Sleep timer" bagian 1 — instruksi user "next"):
        // auto-stop Boomly setelah N menit. Sumber kebenaran = `PrefsHelper` (waktu berakhir
        // absolut) yang dibaca Service; UI cuma menulis lewat `AudioEnhancerService.
        // requestSleepTimer()/cancelSleepTimer()` & polling 1 dtk buat tampilan sisa waktu
        // (state LOKAL, 0 hoist ke ViewModel — pola sama seperti `backupStatus` di bawah).
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel(text = stringResource(R.string.settings_sleep_timer_section_title))
        var sleepEndAt by remember { mutableStateOf(PrefsHelper.getSleepTimerEndAt(context)) }
        var sleepNow by remember { mutableStateOf(System.currentTimeMillis()) }
        var sleepServiceRunning by remember { mutableStateOf(AudioEnhancerService.isRunning) }
        LaunchedEffect(Unit) {
            while (true) {
                sleepNow = System.currentTimeMillis()
                sleepEndAt = PrefsHelper.getSleepTimerEndAt(context)
                sleepServiceRunning = AudioEnhancerService.isRunning
                delay(1000L)
            }
        }
        val sleepRemainingMs = sleepEndAt - sleepNow
        val sleepTimerActive = sleepServiceRunning && sleepRemainingMs > 0L
        SkeuCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_sleep_timer_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSkeuTokens.current.mutedText
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (sleepTimerActive) {
                    Text(
                        stringResource(R.string.settings_sleep_timer_active, formatSleepRemaining(sleepRemainingMs)),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            AudioEnhancerService.cancelSleepTimer(context)
                            sleepEndAt = 0L
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_sleep_timer_cancel))
                    }
                } else {
                    val onPickSleep: (Int) -> Unit = { minutes ->
                        AudioEnhancerService.requestSleepTimer(context, minutes)
                        sleepNow = System.currentTimeMillis()
                        sleepEndAt = PrefsHelper.getSleepTimerEndAt(context)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    SleepTimerDurationRow(listOf(15, 30, 45), sleepServiceRunning, onPickSleep)
                    Spacer(modifier = Modifier.height(8.dp))
                    SleepTimerDurationRow(listOf(60, 90, 120), sleepServiceRunning, onPickSleep)
                    if (!sleepServiceRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_sleep_timer_need_running),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSkeuTokens.current.mutedText
                        )
                    }
                }
            }
        }

        // Batch 122 (Fase 8 roadmap item B, ROI #5 "Auto-profile per output device" —
        // instruksi user "next"): extend `OutputRouteBanner`/`onOutputRouteChanged()`
        // (Service, Batch 82/83, SUDAH ADA) dari sekadar info banner jadi BENERAN
        // auto-apply preset custom. Opt-in (default MATI, lihat PrefsHelper.
        // getAutoProfileEnabled) — mengubah efek TANPA sentuhan user butuh persetujuan
        // eksplisit. Sumber kebenaran = `PrefsHelper` per kategori (`ROUTE_CATEGORY_*`
        // di AudioEnhancerService), dibaca Service saat route berubah; UI di sini CUMA
        // baca/tulis prefs langsung (pola SAMA `useHorizontalLayout` di atas, 0 hoist ke
        // ViewModel/MainActivity). Preset BUILT-IN (4 preset bawaan) TIDAK didukung —
        // definisinya cuma ada di `BoosterScreen.kt` (private, UI-only), SENGAJA di luar
        // scope biar tidak perlu refactor pindahin preset table ke layer Service
        // (Tunnel Vision).
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel(text = stringResource(R.string.settings_auto_profile_section_title))
        var autoProfileEnabled by remember { mutableStateOf(PrefsHelper.getAutoProfileEnabled(context)) }
        // Snapshot sekali (pola sama `customPresets` di BoosterScreen.kt tapi TANPA
        // remember-state reaktif — user yang mau assign preset yang BARU dibuat cukup
        // balik ke layar utama simpan preset, lalu ke sini lagi, re-entry re-read prefs
        // otomatis, SAMA seperti `useHorizontalLayout`).
        val autoProfilePresetNames = remember { PrefsHelper.getCustomPresets(context).map { it.name } }
        var autoProfileSpeaker by remember { mutableStateOf(PrefsHelper.getAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_SPEAKER)) }
        var autoProfileWired by remember { mutableStateOf(PrefsHelper.getAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_WIRED)) }
        var autoProfileBluetooth by remember { mutableStateOf(PrefsHelper.getAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_BLUETOOTH)) }
        var autoProfileUsb by remember { mutableStateOf(PrefsHelper.getAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_USB)) }
        SkeuCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = autoProfileEnabled,
                            onValueChange = {
                                autoProfileEnabled = it
                                PrefsHelper.setAutoProfileEnabled(context, it)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            role = Role.Switch
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_auto_profile_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            stringResource(R.string.settings_auto_profile_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSkeuTokens.current.mutedText
                        )
                    }
                    SkeuSwitch(checked = autoProfileEnabled, onCheckedChange = null)
                }
                if (autoProfileEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (autoProfilePresetNames.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_auto_profile_need_preset),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSkeuTokens.current.mutedText
                        )
                    } else {
                        AutoProfileRouteRow(
                            stringResource(R.string.settings_auto_profile_route_speaker),
                            autoProfilePresetNames, autoProfileSpeaker
                        ) { picked ->
                            autoProfileSpeaker = picked
                            PrefsHelper.setAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_SPEAKER, picked)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AutoProfileRouteRow(
                            stringResource(R.string.settings_auto_profile_route_wired),
                            autoProfilePresetNames, autoProfileWired
                        ) { picked ->
                            autoProfileWired = picked
                            PrefsHelper.setAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_WIRED, picked)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AutoProfileRouteRow(
                            stringResource(R.string.settings_auto_profile_route_bluetooth),
                            autoProfilePresetNames, autoProfileBluetooth
                        ) { picked ->
                            autoProfileBluetooth = picked
                            PrefsHelper.setAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_BLUETOOTH, picked)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AutoProfileRouteRow(
                            stringResource(R.string.settings_auto_profile_route_usb),
                            autoProfilePresetNames, autoProfileUsb
                        ) { picked ->
                            autoProfileUsb = picked
                            PrefsHelper.setAutoProfileForRoute(context, AudioEnhancerService.ROUTE_CATEGORY_USB, picked)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            }
        }

        // Batch 114 (Fase 8 roadmap item D — Export/Import preset, request eksplisit
        // user "planning biar lebih powerful"): backup/restore preset custom ke file
        // .json lewat Storage Access Framework. State status LOKAL di composable ini
        // (pola sama seperti `useHorizontalLayout` di atas — 0 hoist ke MainActivity/
        // BoosterViewModel; screen ini re-entry dari awal tiap dibuka lewat percabangan
        // if/else-if eksklusif MainActivity.kt, lihat komentar Batch 97 di atas). String
        // template dibaca via stringResource() DI SINI (composable scope) lalu diformat
        // manual (`String.format`) di dalam callback launcher — stringResource(id, args)
        // TIDAK BISA dipanggil di luar composition (di dalam launcher/coroutine callback).
        // Guard Thread Safety: baca/tulis FILE (ContentResolver stream) WAJIB
        // Dispatchers.IO, BUKAN blocking Main thread — beda dari baca SharedPreferences
        // polos (`getCustomPresets`, dipakai sinkron di banyak tempat lain di codebase
        // ini termasuk cek isEmpty() di bawah, data kecil, bukan "I/O berat").
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel(text = stringResource(R.string.settings_backup_section_title))
        val exportSuccessTemplate = stringResource(R.string.settings_export_preset_success)
        val exportEmptyMsg = stringResource(R.string.settings_export_preset_empty)
        val exportFailedMsg = stringResource(R.string.settings_export_preset_failed)
        val importSuccessTemplate = stringResource(R.string.settings_import_preset_success)
        val importFailedMsg = stringResource(R.string.settings_import_preset_failed)
        val scope = rememberCoroutineScope()
        var backupStatus by remember { mutableStateOf<String?>(null) }
        var backupStatusIsError by remember { mutableStateOf(false) }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val json = PrefsHelper.exportCustomPresetsToJson(context)
                val count = PrefsHelper.getCustomPresets(context).size
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                backupStatusIsError = !ok
                backupStatus = if (ok) String.format(exportSuccessTemplate, count) else exportFailedMsg
            }
        }
        // Batch 114: filter 2 mime type (bukan cuma "application/json") — sebagian
        // file manager/OEM SAF provider salah tag file .json sebagai "text/plain",
        // kalau filter cuma 1 type file hasil ekspor sendiri bisa "hilang" dari daftar
        // picker di device tertentu (bukan hipotetis, bug SAF yang cukup umum).
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        if (text != null) PrefsHelper.importCustomPresetsFromJson(context, text)
                        else PrefsHelper.ImportResult(false, 0)
                    } catch (_: Exception) {
                        PrefsHelper.ImportResult(false, 0)
                    }
                }
                backupStatusIsError = !result.success
                backupStatus = if (result.success) {
                    String.format(importSuccessTemplate, result.importedCount)
                } else {
                    importFailedMsg
                }
            }
        }

        SkeuCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_backup_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSkeuTokens.current.mutedText
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (PrefsHelper.getCustomPresets(context).isEmpty()) {
                            backupStatusIsError = true
                            backupStatus = exportEmptyMsg
                        } else {
                            exportLauncher.launch("boomly_presets_backup.json")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_export_preset_button))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_import_preset_button))
                }
                if (backupStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        backupStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Batch 122: 1 baris kategori route — chip "Tidak ada" + 1 chip per preset custom,
 *  scroll horizontal (bisa banyak preset). Styling FilterChip disamakan persis dengan
 *  chip preset di `BoosterScreen.kt` (selected = filled primary, unselected = outline
 *  transparan) — 0 token warna baru. */
@Composable
private fun AutoProfileRouteRow(label: String, presetNames: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.settings_auto_profile_none)) },
                shape = RoundedCornerShape(50),
                border = if (selected == null) null else BorderStroke(1.dp, LocalSkeuTokens.current.mutedText.copy(alpha = 0.35f)),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = LocalSkeuTokens.current.mutedText,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
            presetNames.forEach { name ->
                val isSelected = selected == name
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(name) },
                    label = { Text(name) },
                    shape = RoundedCornerShape(50),
                    border = if (isSelected) null else BorderStroke(1.dp, LocalSkeuTokens.current.mutedText.copy(alpha = 0.35f)),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = LocalSkeuTokens.current.mutedText,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

/** Batch 119: 1 baris tombol durasi Sleep timer (lebar sama rata). */
@Composable
private fun SleepTimerDurationRow(minutes: List<Int>, enabled: Boolean, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        minutes.forEach { m ->
            OutlinedButton(
                onClick = { onPick(m) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_sleep_timer_minutes_short, m))
            }
        }
    }
}

/** Batch 119: sisa waktu -> "mm:ss" (< 1 jam) atau "h:mm:ss". */
private fun formatSleepRemaining(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (h > 0L) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(java.util.Locale.ROOT, "%02d:%02d", m, sec)
}
