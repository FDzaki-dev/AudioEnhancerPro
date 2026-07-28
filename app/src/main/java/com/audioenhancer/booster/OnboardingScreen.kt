package com.audioenhancer.booster

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Satu halaman penjelasan fitur di onboarding. */
data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val detail: String? = null
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "🎧",
        title = "Selamat datang di AudioEnhancerPro",
        description = "Aplikasi ini meningkatkan kualitas & volume audio di seluruh sistem HP kamu — " +
            "bukan cuma di dalam aplikasi ini saja, tapi juga saat kamu dengar musik, nonton video, " +
            "atau main game di aplikasi lain."
    ),
    OnboardingPage(
        emoji = "🔊",
        title = "Bass Boost",
        description = "Menambah kekuatan suara nada rendah (bass) supaya musik terasa lebih 'nendang'.",
        detail = "Geser slider ke kanan untuk bass lebih kuat. Nilai 0 = mati, 1000 = maksimal. " +
            "Cocok dipakai saat dengar musik EDM, hip-hop, atau lewat speaker kecil yang biasanya lemah di bass."
    ),
    OnboardingPage(
        emoji = "🌐",
        title = "Virtualizer (Kejernihan Stereo)",
        description = "Membuat suara terasa lebih lebar dan 'mengelilingi' kamu, seperti efek surround.",
        detail = "Efek ini paling terasa kalau kamu pakai earphone/headset. Kalau dengar lewat speaker HP, " +
            "efeknya lebih halus. Naikkan pelan-pelan — nilai terlalu tinggi bisa bikin suara terdengar aneh di beberapa lagu."
    ),
    OnboardingPage(
        emoji = "📢",
        title = "Loudness Gain",
        description = "Menambah volume audio melebihi batas normal sistem (boost tambahan di atas volume HP).",
        detail = "Berguna kalau volume HP kamu sudah maksimal tapi masih kurang keras. " +
            "Catatan: makin tinggi gain, makin besar juga risiko suara pecah (distorsi) — kalau terdengar pecah, turunkan lagi."
    ),
    OnboardingPage(
        emoji = "🛡️",
        title = "Kenapa app minta izin baterai?",
        description = "Supaya booster tetap aktif walau HP di-lock atau app di-scroll dari recent apps.",
        detail = "Android secara default mematikan aplikasi background untuk hemat baterai. " +
            "Kalau app ini dimatikan sistem, efek boost ikut hilang. Izin 'abaikan optimasi baterai' mencegah itu. " +
            "Di HP Xiaomi/Oppo/Vivo/Huawei, kamu mungkin juga perlu aktifkan 'Autostart' secara manual di pengaturan HP — " +
            "app tidak bisa melakukan ini secara otomatis, itu kebijakan keamanan Android."
    ),
    OnboardingPage(
        emoji = "🔔",
        title = "Notifikasi yang selalu muncul",
        description = "Selama booster aktif, kamu akan lihat notifikasi kecil yang tidak bisa di-swipe hilang.",
        detail = "Ini bukan bug — notifikasi ini justru yang membuat Android tahu aplikasi sedang " +
            "'bekerja penting' sehingga tidak gampang dimatikan. Tekan tombol 'Matikan' di notifikasi " +
            "itu sendiri kalau kamu mau menghentikan booster sepenuhnya."
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        TextButton(
            onClick = onFinish,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Lewati")
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val page = onboardingPages[pageIndex]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = page.emoji, fontSize = 64.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                if (page.detail != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card {
                        Text(
                            text = page.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // Indikator titik halaman
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(onboardingPages.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .then(
                            Modifier.background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                )
            }
        }

        val isLastPage = pagerState.currentPage == onboardingPages.size - 1
        Button(
            onClick = {
                if (isLastPage) {
                    onFinish()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1,
                            animationSpec = tween(300)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLastPage) "Mulai Pakai Aplikasi" else "Lanjut")
        }
    }
}
