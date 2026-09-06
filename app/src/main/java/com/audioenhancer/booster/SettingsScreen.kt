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
    }
}
