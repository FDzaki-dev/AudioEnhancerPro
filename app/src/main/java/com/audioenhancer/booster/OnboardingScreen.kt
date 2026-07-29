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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Satu halaman penjelasan fitur di onboarding. */
data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val detail: String? = null
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val onboardingPages = listOf(
        OnboardingPage(
            emoji = "🎧",
            title = stringResource(R.string.ob1_title),
            description = stringResource(R.string.ob1_desc)
        ),
        OnboardingPage(
            emoji = "🔊",
            title = stringResource(R.string.ob2_title),
            description = stringResource(R.string.ob2_desc),
            detail = stringResource(R.string.ob2_detail)
        ),
        OnboardingPage(
            emoji = "🌐",
            title = stringResource(R.string.ob3_title),
            description = stringResource(R.string.ob3_desc),
            detail = stringResource(R.string.ob3_detail)
        ),
        OnboardingPage(
            emoji = "📢",
            title = stringResource(R.string.ob4_title),
            description = stringResource(R.string.ob4_desc),
            detail = stringResource(R.string.ob4_detail)
        ),
        OnboardingPage(
            emoji = "🛡️",
            title = stringResource(R.string.ob5_title),
            description = stringResource(R.string.ob5_desc),
            detail = stringResource(R.string.ob5_detail)
        ),
        OnboardingPage(
            emoji = "🔔",
            title = stringResource(R.string.ob6_title),
            description = stringResource(R.string.ob6_desc),
            detail = stringResource(R.string.ob6_detail)
        )
    )
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(24.dp)
    ) {

        TextButton(
            onClick = onFinish,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.onboarding_skip))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val page = onboardingPages[pageIndex]

            // Jarak halaman ini dari halaman yang sedang aktif di layar (0 = penuh di tengah,
            // 1 = sepenuhnya di luar layar) — dipakai untuk crossfade + scale halus saat swipe,
            // menggantikan perpindahan instan/patah sebelumnya.
            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            val transitionFraction = 1f - abs(pageOffset).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.4f + (0.6f * transitionFraction)
                        val scale = 0.88f + (0.12f * transitionFraction)
                        scaleX = scale
                        scaleY = scale
                    },
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
            Text(if (isLastPage) stringResource(R.string.onboarding_start) else stringResource(R.string.onboarding_next))
        }
    }
    }
}
