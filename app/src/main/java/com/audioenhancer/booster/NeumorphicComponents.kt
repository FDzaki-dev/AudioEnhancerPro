package com.audioenhancer.booster

// Batch 16: dipecah dari MainActivity.kt (God Activity split, audit High-priority item).
// Berisi semua "atom" UI Neumorphic Hybrid yang reusable lintas layar — TIDAK ada state
// Activity/lifecycle di sini, murni Composable + Modifier extension. NeumorphicCard,
// NeumorphicTintedCard, NeumorphicCircleButton, SectionLabel, FeatureControl sengaja
// jadi `internal` (bukan `private` lagi) karena sekarang dipakai dari BoosterScreen.kt
// (file terpisah, sama module/package). neumorphicDepth()/neumorphicInnerShadow() tetap
// `private` karena cuma dipakai di dalam file ini sendiri.

import android.graphics.Paint as AndroidPaint
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Batch 25 (hotfix Batch 24): `LocalIndication` di compose-foundation versi project ini
 *  (compose-bom 2024.06.00) bertipe `CompositionLocal<Indication>` NON-NULL — `provides
 *  null` gagal compile ("Null can not be a value of a non-null type Indication"), BEDA
 *  dari asumsi awal (banyak contoh online pakai versi lama yang nullable). Fix: no-op
 *  `Indication` instance (drawIndication cuma `drawContent()`, gak gambar apapun extra)
 *  dipakai lewat `CompositionLocalProvider(LocalIndication provides NoRippleIndication)`
 *  — efek visual PERSIS SAMA (ripple hilang), cuma cara Kotlin-nya beda dari `null`.
 *  `internal` biar dipakai dari `BoosterScreen.kt` juga (reusable, bukan cuma di sini). */
internal object NoRippleIndication : Indication {
    private object NoRippleIndicationInstance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() {
            drawContent()
        }
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance =
        NoRippleIndicationInstance
}



/** Varian bundar dari NeumorphicCard, khusus buat power button (64dp) — dual-shadow
 *  pakai teknik `neumorphicDepth`/`neumorphicInnerShadow` yang sama (lihat catatan Batch
 *  14/15 di NeumorphicCard), CircleShape bukan RoundedCornerShape, ring 2dp warna aksen
 *  saat `pressed` (persis `.power-btn.on` di HTML). Batch 15: tambah scale animasi
 *  0.97x saat DITEKAN JARI (gesture sesaat, beda dari `pressed` yang berarti "toggle
 *  ON") + ripple dimatikan (`indication = null`) — spec design system user minta feedback
 *  dari scale, bukan ripple Material. */
@Composable
internal fun NeumorphicCircleButton(
    pressed: Boolean,
    ringColor: Color?,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CircleShape
    val surface = MaterialTheme.colorScheme.surface
    val lightSide = if (LocalIsDarkTheme.current) NeuShadowLightSideDark else NeuShadowLightSideLight
    val darkSide = if (LocalIsDarkTheme.current) NeuShadowDarkSide else NeuShadowDarkSideLight
    val desc = contentDescription

    val interactionSource = remember { MutableInteractionSource() }
    val isPressedNow by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressedNow) 0.97f else 1f, label = "powerBtnScale")

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(scale)
            .then(
                if (!pressed) Modifier.neumorphicDepth(shape = shape, darkColor = darkSide, lightColor = lightSide)
                else Modifier.neumorphicInnerShadow(shape = shape, color = darkSide)
            )
            .clip(shape)
            .background(surface)
            .then(
                if (ringColor != null) Modifier.border(2.dp, ringColor, shape) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = desc,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = desc },
        contentAlignment = Alignment.Center,
        content = content
    )
}
/** Batch 14 (fix dilaporkan user: "efek kedalaman belum kelihatan" di device asli).
 *  ROOT CAUSE: `Modifier.shadow` (dipakai Batch 12) itu shadow ELEVATION bawaan Android —
 *  satu sumber cahaya virtual, alpha ambient/spot-nya DIBATASI KERAS oleh sistem
 *  (~3-15% max secara internal) TIDAK PEDULI warna/opacity yang kita kasih ke
 *  ambientColor/spotColor. Itu sebabnya di HTML (CSS `box-shadow`, opacity 100% kita
 *  kontrol manual) kelihatan tebal, tapi di APK asli nyaris invisible — bukan soal
 *  tuning angka seperti dugaan Batch 12, tapi API-nya sendiri gak bisa setebal itu.
 *  FIX: gambar shadow manual pakai `android.graphics.Paint.setShadowLayer` (blur+offset
 *  bebas kita atur, PERSIS cara CSS box-shadow kerja) — didukung penuh di canvas hardware-
 *  accelerated sejak Android 9/API 28. Di API <28 fallback diam-diam ke tanpa shadow
 *  (dual-shadow bawaan sudah tipis, tetap lebih rapi daripada dipaksakan crash/glitch).
 *  Batch 15: default offset/blur per-sisi TIDAK LAGI simetris — mengikuti spec design
 *  system user persis: sisi gelap (+8dp, 17dp blur) LEBIH BESAR dari sisi terang
 *  (-6dp, 15dp blur), karena cahaya "wajar" datang dari satu arah lebih dominan. */
private fun Modifier.neumorphicDepth(
    shape: Shape,
    darkColor: Color,
    lightColor: Color,
    darkBlur: androidx.compose.ui.unit.Dp = NeuShadowDarkBlur,
    darkOffset: androidx.compose.ui.unit.Dp = NeuShadowDarkOffset,
    lightBlur: androidx.compose.ui.unit.Dp = NeuShadowLightBlur,
    lightOffset: androidx.compose.ui.unit.Dp = NeuShadowLightOffset
): Modifier = this.drawBehind {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val androidPath = Path().apply { addOutline(outline) }.asAndroidPath()
    drawIntoCanvas { canvas ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
        }
        // Sisi gelap (kanan-bawah) — meniru bayangan jatuh dari permukaan timbul.
        val darkOffsetPx = darkOffset.toPx()
        paint.setShadowLayer(darkBlur.toPx(), darkOffsetPx, darkOffsetPx, darkColor.toArgb())
        canvas.nativeCanvas.drawPath(androidPath, paint)
        // Sisi terang (kiri-atas) — highlight tipis, arah berlawanan dari sisi gelap.
        val lightOffsetPx = lightOffset.toPx()
        paint.setShadowLayer(lightBlur.toPx(), -lightOffsetPx, -lightOffsetPx, lightColor.toArgb())
        canvas.nativeCanvas.drawPath(androidPath, paint)
    }
}

/** Batch 15: shadow "ke dalam" (inset) buat pressed/carved state — spec design system
 *  user minta "Inset Shadow + Accent Outline" (bukan cuma border rata seperti Batch 12-14).
 *  Trik: clip canvas ke bentuk kartu, lalu gambar STROKE tebal di sepanjang outline dengan
 *  shadow layer — karena di-clip, shadow-nya cuma kelihatan di bagian yang "masuk" ke
 *  dalam kartu (bukan keluar), persis kesan carved/tertekan. */
private fun Modifier.neumorphicInnerShadow(
    shape: Shape,
    color: Color,
    blur: androidx.compose.ui.unit.Dp = 14.dp,
    offset: androidx.compose.ui.unit.Dp = 6.dp
): Modifier = this.drawWithContent {
    drawContent()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@drawWithContent
    val outline = shape.createOutline(size, layoutDirection, this)
    val androidPath = Path().apply { addOutline(outline) }.asAndroidPath()
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val checkpoint = nativeCanvas.save()
        nativeCanvas.clipPath(androidPath)
        val strokeWidthPx = blur.toPx() * 2.2f
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.TRANSPARENT
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
        paint.setShadowLayer(blur.toPx(), offset.toPx(), offset.toPx(), color.toArgb())
        nativeCanvas.drawPath(androidPath, paint)
        nativeCanvas.restoreToCount(checkpoint)
    }
}

/** Kartu inti Batch 12 "Neumorphic Hybrid" (dual-shadow-nya diperbaiki Batch 14, radius+
 *  pressed-state diselaraskan ke spec design system user di Batch 15 — lihat
 *  `neumorphicDepth`/`neumorphicInnerShadow`): permukaan SOLID (bukan translucent),
 *  kedalaman dari dua shadow offset berlawanan arah — gelap di kanan-bawah, terang tipis
 *  di kiri-atas — meniru cahaya jatuh dari kiri-atas ke permukaan yang "timbul" dari
 *  background datar. `accentColor`/`accentColor2` masih diterima buat kompatibilitas
 *  call-site lama tapi TIDAK dipakai di sini (dulu buat border gradient); pewarnaan aksen
 *  sekarang tanggung jawab konten di dalamnya (ikon/value di FeatureControl), bukan
 *  bingkai kartu. `radius` bisa dioverride (mis. icon box pakai `NeuIconBoxRadius`). */
@Composable
internal fun NeumorphicCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accentColor: Color = MaterialTheme.colorScheme.primary,
    @Suppress("UNUSED_PARAMETER") accentColor2: Color = accentColor,
    pressed: Boolean = false,
    radius: androidx.compose.ui.unit.Dp = NeuCardRadius,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val surface = MaterialTheme.colorScheme.surface
    val lightSide = if (LocalIsDarkTheme.current) NeuShadowLightSideDark else NeuShadowLightSideLight
    val darkSide = if (LocalIsDarkTheme.current) NeuShadowDarkSide else NeuShadowDarkSideLight

    Column(
        modifier = modifier
            .then(
                if (!pressed) Modifier.neumorphicDepth(shape = shape, darkColor = darkSide, lightColor = lightSide)
                else Modifier.neumorphicInnerShadow(shape = shape, color = darkSide)
            )
            .clip(shape)
            .background(surface)
            .then(
                // State "ditekan": inset shadow di atas + accent outline tipis, persis
                // spec "Inset Shadow + Accent Outline" — bukan border polos lagi.
                if (pressed) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape) else Modifier
            ),
        content = content
    )
}

/** Varian tinted buat banner info/warning — tetap SOLID (bukan alpha mentah di atas
 *  background), aksen di-blend penuh ke warna dasar supaya tone banner beda dari kartu
 *  netral tapi kontras teks tetap terjamin karena base-nya opaque 100%. */
@Composable
internal fun NeumorphicTintedCard(
    modifier: Modifier = Modifier,
    tint: Color,
    @Suppress("UNUSED_PARAMETER") tint2: Color = tint,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(NeuCardRadius)
    val blended = lerp(MaterialTheme.colorScheme.surface, tint, 0.22f)
    val lightSide = if (LocalIsDarkTheme.current) NeuShadowLightSideDark else NeuShadowLightSideLight
    val darkSide = if (LocalIsDarkTheme.current) NeuShadowDarkSide else NeuShadowDarkSideLight

    Column(
        modifier = modifier
            .neumorphicDepth(shape = shape, darkColor = darkSide, lightColor = lightSide)
            .clip(shape)
            .background(blended)
            .border(1.dp, tint.copy(alpha = 0.4f), shape),
        content = content
    )
}


/** Label section: warna aksen SOLID (bukan gradient). Batch 15: font size & letter-spacing
 *  di-hardcode eksplisit sesuai spec design system user (12sp, letterSpacing 1.4) —
 *  TIDAK ikut token typography global `bodyMedium` lagi (itu dipakai elemen lain juga,
 *  butuh ukuran beda). */
@Composable
internal fun SectionLabel(text: String, accentColor: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        color = accentColor,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/** Batch 22: Slider custom sesuai spec design system user ("Track 10dp, Thumb 22dp +
 *  shadow + ring") — Compose Slider bawaan Material3 (dipakai apa adanya sejak Batch 12)
 *  diganti pakai overload `thumb=`/`track=` (tersedia sejak material3 1.2.0, project ini
 *  di compose-bom 2024.06.00 → material3 1.2.1, aman). Track custom digambar manual
 *  (Box dual-layer, BUKAN Canvas) biar konsisten sama pola Modifier-chain di file ini.
 *  Thumb custom pakai `neumorphicDepth()` yang SAMA PERSIS dipakai NeumorphicCard/
 *  NeumorphicCircleButton — biar dual-shadow-nya konsisten satu bahasa desain di semua
 *  komponen, bukan reimplementasi shadow terpisah. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeumorphicSliderTrack(
    sliderState: SliderState,
    activeColor: Color,
    inactiveColor: Color,
    enabled: Boolean
) {
    val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
    val fraction = if (range != 0f) {
        ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
    } else 0f
    val trackColor = if (enabled) activeColor else activeColor.copy(alpha = 0.35f)
    val bgColor = if (enabled) inactiveColor else inactiveColor.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(trackColor)
        )
    }
}

@Composable
private fun NeumorphicSliderThumb(accentColor: Color, enabled: Boolean) {
    val shape = CircleShape
    val surface = MaterialTheme.colorScheme.surface
    val lightSide = if (LocalIsDarkTheme.current) NeuShadowLightSideDark else NeuShadowLightSideLight
    val darkSide = if (LocalIsDarkTheme.current) NeuShadowDarkSide else NeuShadowDarkSideLight
    val ringAlpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(22.dp)
            .neumorphicDepth(shape = shape, darkColor = darkSide, lightColor = lightSide)
            .clip(shape)
            .background(surface)
            .border(2.dp, accentColor.copy(alpha = ringAlpha), shape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeatureControl(
    title: String,
    helpText: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColor2: Color = accentColor,
    wrapInCard: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val innerContent: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) {
                    // Batch 12: fill ikon SOLID (bukan gradient) — bagian dari "gradient
                    // cuma buat elemen non-teks", dan solid juga lebih konsisten dgn
                    // permukaan neumorphic di sekelilingnya (icon-orb sendiri jadi kartu
                    // extruded kecil, bukan blob warna).
                    NeumorphicCard(pressed = false, radius = NeuIconBoxRadius) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Text(title, fontWeight = FontWeight.Bold)
            }
            // Batch 12: teks value warna SOLID (bukan gradient-clip lagi) — kontras
            // konsisten di semua state, gak tergantung posisi teks di dalam gradient.
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (helpText.isNotBlank()) {
            Text(
                helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            valueRange = valueRange,
            enabled = enabled,
            thumb = { NeumorphicSliderThumb(accentColor = accentColor2, enabled = enabled) },
            track = { sliderState ->
                NeumorphicSliderTrack(
                    sliderState = sliderState,
                    activeColor = accentColor,
                    inactiveColor = accentColor.copy(alpha = 0.18f),
                    enabled = enabled
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = "$title, $valueLabel" }
        )
    }

    if (wrapInCard) {
        NeumorphicCard(accentColor = accentColor, accentColor2 = accentColor2) {
            Column(modifier = Modifier.padding(16.dp), content = innerContent)
        }
    } else {
        Column(content = innerContent)
    }
}
