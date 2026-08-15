package com.audioenhancer.booster

// Batch 37: rewrite total UI/UX -> iOS Glassmorphism dominan + Midnight-Blue jadi hint
// yang kentara (lihat blok komentar panjang di Theme.kt). Perubahan struktural (bukan
// cuma warna) di file ini: (1) SkeuCard/SkeuTintedCard/SkeuPowerButton sekarang punya
// layer `.background(tokens.specularBrush)` KEDUA di atas base glass -> sheen kaca ala
// iOS di pojok kiri-atas. (2) SkeuSwitch: blend ON 0.35->0.55 (midnight-blue lebih
// dominan saat aktif) + thumb OFF dinaikkan ke campuran putih 45% (bead kaca terang ala
// iOS, bukan abu gelap polos). Nama fungsi/komponen TIDAK diubah (dipanggil dari banyak
// tempat di BoosterScreen.kt) — cukup isi render-nya yang di-rewrite.
//
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
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

/** Batch 47: falloff di-ubah dari 2-stop hard cutoff (`[color, Transparent]`) ke
 *  4-stop halus — user lapor efeknya "kebaca bocor" bukan "menyala ambient". Hard
 *  cutoff bikin tepi glow terasa seperti warna solid yang terpotong tiba-tiba;
 *  4-stop mensimulasikan falloff cahaya beneran (cepat redup di 35%, landai
 *  sampai transparan di 100%) — masih pakai `Brush.radialGradient` native (BUKAN
 *  `BlurMaskFilter`, preseden Batch 14/32), cuma stop-nya lebih banyak. Berlaku
 *  GLOBAL ke semua pemakai (`SkeuPowerButton`, `SkeuSwitch`, preset chip
 *  `BoosterScreen.kt`), semua varian — bukan cuma Neumorphism. */
internal fun Modifier.skeuGlow(color: Color, spread: Dp = 12.dp): Modifier = this.drawBehind {
    val glowRadius = ((size.minDimension / 2f) + spread.toPx()).coerceAtLeast(1f)
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = color.alpha * 0.90f),
            0.35f to color.copy(alpha = color.alpha * 0.55f),
            0.70f to color.copy(alpha = color.alpha * 0.18f),
            1.00f to Color.Transparent,
            center = center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = center
    )
}

/** Batch 52: DIROMBAK dari native `Modifier.shadow(ambientColor=,spotColor=)`
 *  (Batch 47) ke shape-outline concentric-fade manual — `DrawScope.drawPath`
 *  + `translate` POLOS (operasi Canvas paling dasar, BUKAN `Paint.setShadowLayer`/
 *  `BlurMaskFilter`/`RenderEffect` — preseden Batch 14/32 soal custom
 *  Paint-shadow-hack TETAP dihormati, 0 native Canvas/Paint interop di sini).
 *  [Batch 54: draf awal Batch 52 pakai `drawOutline` — TERNYATA gak eksis di
 *  Compose UI graphics, CI gagal compile "Unresolved reference" (3 titik).
 *  Diganti `drawPath` (primitive DrawScope asli, `Outline` dikonversi ke
 *  `Path` manual via `Outline.Rectangle`/`Rounded`/`Generic` — sealed class,
 *  exhaustive `when` tanpa `else` sengaja dipertahankan biar compiler
 *  ngasih tau kalau ada varian baru nanti). Komentar di bawah TETAP akurat
 *  soal ALASAN/strategi (concentric-fade, falloff, invert) — cuma nama API
 *  primitive-nya yang beda.]
 *  Alasan ganti dari native shadow: user lapor 2x (Batch 47 DAN sekarang,
 *  screenshot device asli) kesan "extruded & pressed" masih kurang kerasa.
 *  Root cause didokumentasikan project ini sejak Batch 14: shadow native
 *  Android (`Modifier.shadow`, termasuk `ambientColor`/`spotColor`) DIBATASI
 *  alpha keras oleh sistem (tuned buat Material Design default, bukan
 *  neumorphism tebal) + warna custom cuma jalan di API 28+ (di bawah itu
 *  SENYAP diabaikan, balik ke shadow hitam default tipis tanpa warning). Fix:
 *  gambar ULANG siluet bentuk kartu (`shape.createOutline` -> `Path`)
 *  berkali-kali (`ShadowSteps`), makin jauh & makin transparan tiap step ke
 *  arah diagonal — mensimulasikan falloff blur TANPA `BlurMaskFilter`/
 *  `Modifier.blur()` (yang API-gated 31+) — hasil IDENTIK di semua API level
 *  dari `minSdk 24`, 0 fallback/gating. `invert=true` (dipakai
 *  `SkeuSliderTrack`/`SkeuSwitch`, elemen "tertekan") balik arah gelap/terang
 *  (gelap kiri-atas/terang kanan-bawah, kebalikan raised) — dikombinasi
 *  `.clip(shape)` yang SUDAH ada di caller, bleed otomatis terpotong ke
 *  DALAM bentuk = kebaca cekung, bukan bocor keluar kayak raised. `steps`
 *  dikecilkan (3) buat elemen kecil (track/switch) — hemat draw call, beda
 *  kebutuhan detail dari kartu besar (5). */
private const val ShadowSteps = 5

@Composable
private fun BoxScope.SkeuDualDirectionalShadow(
    tokens: SkeuTokens,
    shape: Shape,
    depth: Dp,
    invert: Boolean = false,
    steps: Int = ShadowSteps
) {
    if (tokens.shadowLightTint == Color.Transparent) return
    val topLeftTint = if (invert) tokens.shadowDarkTint else tokens.shadowLightTint
    val bottomRightTint = if (invert) tokens.shadowLightTint else tokens.shadowDarkTint
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                // Batch 54 (fix urgent): `drawOutline` TERNYATA gak eksis di
                // Compose UI graphics (Unresolved reference, CI build gagal —
                // log_fail run105 debug+release, 3 titik). Perbaikan: `Outline`
                // dikonversi ke `Path` (`addRect`/`addRoundRect`/langsung pakai
                // `outline.path` buat `Outline.Generic` — CircleShape.
                // createOutline() balikin `Outline.Generic`, RoundedCornerShape
                // balikin `Outline.Rounded`) SEKALI di luar loop (bukan per-step,
                // hemat alokasi), lalu gambar pakai `drawPath` — primitive
                // DrawScope asli yang beneran ada (dipastikan sebelum dikirim,
                // lihat PROJECT_STATE.md Batch 54 utk cara verifikasi).
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = when (outline) {
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Generic -> outline.path
                }
                val maxSpread = depth.toPx() * 1.15f
                for (step in steps downTo 1) {
                    val t = step / steps.toFloat()
                    val spread = maxSpread * t
                    // Alpha makin KECIL makin jauh dari bentuk asli (t besar =
                    // spread besar = paling jauh = paling transparan; t kecil =
                    // dekat bentuk = paling pekat) — falloff landai simulasi
                    // blur, BUKAN hard-edge cutoff (pola sama `skeuGlow`).
                    val alphaMultiplier = 1f - t
                    translate(left = spread, top = spread) {
                        drawPath(
                            path = path,
                            color = bottomRightTint.copy(alpha = bottomRightTint.alpha * alphaMultiplier)
                        )
                    }
                    translate(left = -spread, top = -spread) {
                        drawPath(
                            path = path,
                            color = topLeftTint.copy(alpha = topLeftTint.alpha * alphaMultiplier)
                        )
                    }
                }
            }
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
    // Batch 39: default radius sekarang baca `LocalSkeuTokens.current.cardRadius`
    // (per-varian, lihat Theme.kt) — bukan const global `SkeuCardRadius` lagi, biar
    // varian Skeuomorphism (radius lebih tegas/kecil) beneran otonom, gak numpang
    // radius iOS-glass 2 varian lain.
    radius: Dp = LocalSkeuTokens.current.cardRadius,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    // Batch 36: fill/border/elevation sekarang datang dari `LocalSkeuTokens.current`
    // (Theme.kt) — AMOLED Glass tetap frosted-glass tint (persis sebelumnya), Radical
    // Literal Skeuomorphism jadi raised-bevel surface (guide §5 "Raised object").
    // 1 kode komponen, 3 tema, TANPA duplikasi/percabangan when() di sini.
    val tokens = LocalSkeuTokens.current
    // Batch 47: outer Box TANPA `modifier` (`modifier` caller tetap di Column persis
    // posisi lama — supaya sizing/layout existing callers TIDAK berubah sama sekali),
    // cuma wadah buat 2 layer dual-shadow opsional di belakang konten.
    Box {
        SkeuDualDirectionalShadow(tokens, shape, tokens.cardElevation)
        Column(
            modifier = modifier
                .shadow(elevation = tokens.cardElevation, shape = shape, clip = false)
                .clip(shape)
                .background(tokens.cardBrush)
                // Batch 37: layer sheen kaca KEDUA di atas base glass — pojok kiri-atas
                // konsentrasi terang lalu transparan penuh (readability aman, gak nutup
                // teks). Ini yang bikin kartu kebaca sebagai KACA, bukan cuma kartu
                // gelap solid berwarna biru.
                .background(tokens.specularBrush)
                .border(1.dp, tokens.cardBorderBrush, shape),
            content = content
        )
    }
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
    val tokens = LocalSkeuTokens.current
    // Batch 39: radius dari token per-varian (`tokens.cardRadius`), bukan const
    // global `SkeuCardRadius` lagi (sama alasannya dengan SkeuCard di atas).
    val shape = RoundedCornerShape(tokens.cardRadius)
    val blended = lerp(tokens.baseSurface, tint, 0.22f)
    Box {
        SkeuDualDirectionalShadow(tokens, shape, tokens.cardElevation + 1.dp)
        Column(
            modifier = modifier
                .shadow(elevation = tokens.cardElevation + 1.dp, shape = shape, clip = false)
                .clip(shape)
                .background(Brush.linearGradient(listOf(blended, tokens.elevatedSurface)))
                .background(tokens.specularBrush)
                .border(1.dp, tint.copy(alpha = 0.4f), shape),
            content = content
        )
    }
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
            // Batch 52: clip HANYA saat invert/pressed — shadow raised (default)
            // butuh bleed KELUAR lingkaran (kesan extruded, sama seperti
            // SkeuCard), shadow invert/pressed justru harus KEPOTONG di dalam
            // lingkaran biar kebaca cekung (bukan cuma halo warna kebalik).
            .then(if (pressed || isPressedNow) Modifier.clip(shape) else Modifier)
    ) {
        // Batch 52: dual-shadow KHUSUS Neumorphism (0 efek 3 varian lain — tokens
        // Transparent, `SkeuDualDirectionalShadow` no-op). Raised default, INVERT
        // (cekung) saat `pressed`/ditekan — cue "ditekan masuk" sekarang beneran
        // dari shadow terbalik, bukan cuma elevation->0dp+ring seperti sebelumnya.
        SkeuDualDirectionalShadow(tokens, shape, depth = 7.dp, invert = pressed || isPressedNow, steps = 4)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (pressed) Modifier.skeuGlow(tokens.primaryGlow, spread = 14.dp) else Modifier)
                .shadow(elevation = elevation, shape = shape, clip = false)
                .clip(shape)
                .background(tokens.bevelBrush)
                .background(tokens.specularBrush)
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
}

/** Track slider flat/minimal buat 3 varian (guide poin 3), TAPI Batch 52: track
 *  yang belum terisi (`bgColor`) sekarang dapat inset shadow cekung KHUSUS
 *  Neumorphism (`SkeuDualDirectionalShadow(invert=true)`, 0 efek 3 varian lain)
 *  — cue "tertekan" (guide neumorphism "well/groove" tempat thumb bergerak),
 *  bagian terisi (`trackColor`) TETAP flat solid di atasnya (area itu kebaca
 *  "terisi", bukan cekung). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkeuSliderTrack(
    sliderState: SliderState,
    activeColor: Color,
    inactiveColor: Color,
    enabled: Boolean
) {
    val tokens = LocalSkeuTokens.current
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
        SkeuDualDirectionalShadow(tokens, shape, depth = 3.dp, invert = true, steps = 3)
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
                    // Batch 39: radius icon-box dari token per-varian (otonom), bukan
                    // const global `SkeuIconBoxRadius` lagi.
                    SkeuCard(radius = LocalSkeuTokens.current.iconBoxRadius) {
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
            checked -> lerp(MaterialTheme.colorScheme.surfaceVariant, accentColor, 0.55f)
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
        // Batch 52: inset shadow cekung KHUSUS Neumorphism (0 efek 3 varian
        // lain) — groove tempat thumb "duduk", cue "tertekan" (pelengkap raised
        // thumb di bawah).
        SkeuDualDirectionalShadow(tokens, trackShape, depth = 2.5.dp, invert = true, steps = 3)
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .scale(thumbScale)
                .shadow(elevation = thumbElevation, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (checked) accentColor else lerp(tokens.elevatedSurface, Color.White, 0.45f))
                .alpha(if (enabled) 1f else 0.5f)
        )
    }
}
