package com.audioenhancer.booster

// Batch 36: SkeuCard/SkeuTintedCard/SkeuPowerButton/SkeuSliderThumb/SkeuSwitch (helpText
// muted color, thumb OFF color) sekarang baca warna/brush/elevation lewat
// `LocalSkeuTokens.current` (Theme.kt), BUKAN lagi val top-level Glass*/TextMuted
// hardcoded — supaya 1 kode komponen jalan dinamis buat 2 sistem desain (AMOLED Glass
// existing + Radical Literal Skeuomorphism baru), dipilih via switch baru di Settings.
// Detail lengkap token per-tema: lihat blok "BATCH 36" di Theme.kt.
//
// Batch 35: TextMuted (Theme.kt, didefinisikan sejak Batch 34 tapi 0 pemanggil —
// technical debt) sekarang BENERAN dipakai — guide §16 hierarki tipografi (Display>
// Title>Section>Body>Secondary>Caption), helpText slider ini caption-tier (bukan
// Secondary/onSurfaceVariant lagi).
//
// Batch 34: KOREKSI dari Batch 33 (user salah upload acuan sebelumnya, guide yang
// benar: compose-amoled-hybrid-glass-final.md — "Premium AMOLED Hybrid Glassmorphism +
// Subtle Midnight Blue + Micro-Skeuomorphism"). Token warna diganti total ke nama
// persis guide baru (GlassBase/GlassElevated/GlassPressed, ganti GlassSurface* Batch
// 33) — lihat Theme.kt Batch 34 buat daftar lengkap. Perubahan filosofi kunci vs
// Batch 33:
// 1. Glass adalah MATERIAL UTAMA. Skeuomorphism turun jadi "micro" — HANYA buat
//    interaksi fisik (button/switch/slider/knob). Kartu struktural (SkeuCard/
//    SkeuTintedCard) "glass surfaces first, not physical objects" (guide §14) —
//    TIDAK BOLEH strong bevel/heavy shadow/thick border/bright glow.
// 2. Slider knob TIDAK BOLEH lagi "metallic realism" (radial gradient putih->accent
//    ala dial logam) — guide §13 eksplisit melarang, diganti radial gradient
//    accent-tinted glass (GlassHighlight/GlassElevated based, bukan Color.White sheen).
//
// Prinsip guide yang dipakai:
// 1. Tactile Depth via bevel gradient + border highlight/shadow (Modifier.background
//    Brush + border), BUKAN dual drawBehind shadow-layer manual.
// 2. Micro-interaction "klik fisik": Modifier.scale + Modifier.shadow(elevation)
//    animateDpAsState/animateFloatAsState — standar Compose, bukan Paint hack.
// 3. Realisme tactile HANYA di komponen fisik (power button, slider knob) — kartu
//    struktural (SkeuCard/SkeuTintedCard) glass murni, restrained (guide §14).
// 4. Glow (§18) HANYA buat state aktif/selected/focused, alpha direstrain — bukan
//    material, bukan Color.White.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** compose-bom 2024.06.00 -> `LocalIndication` non-null, jadi indication ripple
 *  dimatikan lewat no-op instance ini (bukan `provides null`). Dipertahankan dari
 *  struktur lama — bukan neumorphism-specific, murni utilitas UI generik. */
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

/** Batch 32: guide §9 "Glow Rules" — halo lembut TERBATAS buat state aktif/selected
 *  (power button ON, preset chip aktif, switch ON), native pakai `Brush.radialGradient`
 *  (BUKAN `BlurMaskFilter`/Paint hack — sudah kejadian di Batch 14, shadow layer custom
 *  gak reliable lintas API level tanpa compiler buat verifikasi ulang). Ditaruh SEBELUM
 *  `.shadow()`/`.clip()` di modifier chain manapun dipakai, supaya halo-nya boleh
 *  \"bleed\" keluar batas shape (gak ikut ke-clip) — itu kenapa harus tetap sengaja
 *  dipasang urutannya, JANGAN dipindah ke akhir chain kalau dipakai ulang di komponen
 *  lain. Dipakai SELEKTIF sesuai guide (\"Do not use glow for every card/border/text\"),
 *  bukan didekorasi ke semua komponen — token `SkeuPrimaryGlow` (Theme.kt) sekarang
 *  BENERAN dipakai (sebelumnya didefinisikan tapi 0 pemanggil). */
internal fun Modifier.skeuGlow(color: Color, spread: Dp = 12.dp): Modifier = this.drawBehind {
    val glowRadius = ((size.minDimension / 2f) + spread.toPx()).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(color, Color.Transparent), center = center, radius = glowRadius),
        radius = glowRadius,
        center = center
    )
}

/** Kartu struktural — guide §2.5 mewajibkan material frosted-glass + midnight blue
 *  tint (bukan solid flat lagi), TAPI tetap "visually quiet" dibanding tactile control
 *  fisik (guide §8): tint subtle (`MidnightBlueGlassBrush`, alpha rendah), border tipis
 *  low-alpha, elevation kecil standar Compose. Tidak ada glow di sini (glow guide §9
 *  cuma buat state aktif/selected, kartu struktural bukan itu). */
@Composable
internal fun SkeuCard(
    modifier: Modifier = Modifier,
    radius: Dp = SkeuCardRadius,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    // Batch 36: fill/border/elevation sekarang datang dari `LocalSkeuTokens.current`
    // (Theme.kt) — AMOLED Glass tetap frosted-glass tint (persis sebelumnya), Radical
    // Literal Skeuomorphism jadi raised-bevel surface (guide §5 "Raised object").
    // 1 kode komponen, 2 tema, TANPA duplikasi/percabangan when() di sini.
    val tokens = LocalSkeuTokens.current
    Column(
        modifier = modifier
            .shadow(elevation = tokens.cardElevation, shape = shape, clip = false)
            .clip(shape)
            .background(tokens.cardBrush)
            .border(1.dp, tokens.cardBorderBrush, shape),
        content = content
    )
}

/** Varian tinted buat banner info/warning — glass base yang sama dengan SkeuCard,
 *  di-blend tambahan ke warna semantik (error/primary) biar tetap dibaca sebagai
 *  status, bukan solid flat mentah. */
@Composable
internal fun SkeuTintedCard(
    modifier: Modifier = Modifier,
    tint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(SkeuCardRadius)
    val tokens = LocalSkeuTokens.current
    val blended = lerp(tokens.baseSurface, tint, 0.22f)
    Column(
        modifier = modifier
            .shadow(elevation = tokens.cardElevation + 1.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(blended, tokens.elevatedSurface)))
            .border(1.dp, tint.copy(alpha = 0.4f), shape),
        content = content
    )
}

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

/** Power button — satu-satunya elemen "physical utility" bundar di app (guide poin
 *  3), jadi satu-satunya yang dapat bevel gradient penuh + micro-interaction klik
 *  fisik (guide poin 2: scale + shadow elevation animateDpAsState, PERSIS snippet
 *  guide, bukan lagi custom Paint shadow-layer/inner-shadow-well neumorphic). Saat
 *  `pressed` (state ON/aktif), elevation dijatuhkan ke 0 dan ring accent primary
 *  menyala — kesan "ditekan masuk", tanpa reimplementasi inner-shadow terpisah. */
@Composable
internal fun SkeuPowerButton(
    pressed: Boolean,
    ringColor: Color?,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CircleShape
    val desc = contentDescription
    val tokens = LocalSkeuTokens.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressedNow by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressedNow) 0.97f else 1f, label = "powerBtnScale")
    val elevation by animateDpAsState(if (pressed || isPressedNow) 0.dp else 6.dp, label = "powerBtnElevation")

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(scale)
            .then(if (pressed) Modifier.skeuGlow(tokens.primaryGlow, spread = 14.dp) else Modifier)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(tokens.bevelBrush)
            .border(1.5.dp, tokens.bevelBorderBrush, shape)
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

/** Track slider tetap flat/minimal (guide poin 3 — bukan target realisme tactile,
 *  cuma knob-nya). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkeuSliderTrack(
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

/** Knob slider — guide §13 "Tactile Slider": radial gradient RESTRAINED
 *  (`GlassHighlight` -> `GlassElevated`, contoh persis guide), accent HANYA sebagai
 *  tint tipis + border ring buat "clear active/inactive distinction" — BUKAN lagi
 *  radial gradient putih->accent ala dial logam (guide §13 eksplisit: "Avoid metallic
 *  realism that conflicts with the glass aesthetic"). */
@Composable
private fun SkeuSliderThumb(accentColor: Color, enabled: Boolean) {
    val tokens = LocalSkeuTokens.current
    val shape = CircleShape
    val ringAlpha = if (enabled) 1f else 0.4f
    val dialBrush = Brush.radialGradient(
        colors = listOf(
            tokens.sliderKnobHighlight,
            lerp(tokens.elevatedSurface, accentColor, if (enabled) 0.30f else 0.08f)
        )
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .shadow(elevation = if (enabled) 4.dp else 0.dp, shape = shape, clip = false)
            .clip(shape)
            .background(dialBrush)
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
                    SkeuCard(radius = SkeuIconBoxRadius) {
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
                color = LocalSkeuTokens.current.mutedText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            valueRange = valueRange,
            enabled = enabled,
            thumb = { SkeuSliderThumb(accentColor = accentColor2, enabled = enabled) },
            track = { sliderState ->
                SkeuSliderTrack(
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
        SkeuCard {
            Column(modifier = Modifier.padding(16.dp), content = innerContent)
        }
    } else {
        Column(content = innerContent)
    }
}

/** Batch 32: toggle/switch tactile — guide §7 "Toggles / Switches" eksplisit minta
 *  physical indentation (bukan pill Material3 default polos yang dipakai sebelumnya,
 *  0 treatment tactile sama sekali). 3 state wajib guide, semua diimplementasi:
 *  OFF = recessed/muted (track abu netral, thumb GlassElevated datar tanpa glow),
 *  ON = active/illuminated (track blend ke accentColor 35%, thumb solid accentColor
 *  + glow tipis via `skeuGlow` — DUA cue sekaligus, structural [posisi+ukuran thumb]
 *  DAN color, sesuai syarat a11y guide §7 "must not depend solely on structural
 *  changes"), PRESSED = thumb mengecil sesaat (scale 0.88, micro-interaction guide §6,
 *  BUKAN exclusively-scale karena posisi+warna tetap jadi cue utama). `onCheckedChange
 *  = null` -> switch murni dekoratif/non-interaktif (dipakai kalau parent Row lain yang
 *  sudah pegang `toggleable` sendiri, pola yang sama dipakai `Switch` Material3). */
@Composable
internal fun SkeuSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    val tokens = LocalSkeuTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedNow by interactionSource.collectIsPressedAsState()
    val trackShape = RoundedCornerShape(50)

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            checked -> lerp(MaterialTheme.colorScheme.surfaceVariant, accentColor, 0.35f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "skeuSwitchTrack"
    )
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "skeuSwitchThumbOffset")
    val thumbScale by animateFloatAsState(if (isPressedNow) 0.88f else 1f, label = "skeuSwitchThumbScale")
    val thumbElevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            isPressedNow -> 0.5.dp
            checked -> 3.dp
            else -> 1.dp
        },
        label = "skeuSwitchThumbElevation"
    )

    Box(
        modifier = modifier
            .width(46.dp)
            .height(26.dp)
            .clip(trackShape)
            .background(trackColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (checked) 0.6f else 0.35f), trackShape)
            .then(if (checked && enabled) Modifier.skeuGlow(accentColor.copy(alpha = 0.3f), spread = 6.dp) else Modifier)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                } else Modifier
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .scale(thumbScale)
                .shadow(elevation = thumbElevation, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (checked) accentColor else tokens.elevatedSurface)
                .alpha(if (enabled) 1f else 0.5f)
        )
    }
}
