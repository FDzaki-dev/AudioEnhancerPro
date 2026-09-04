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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel(text = stringResource(R.string.settings_title))
        SkeuCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_app_version_label, appVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

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
    }
}
