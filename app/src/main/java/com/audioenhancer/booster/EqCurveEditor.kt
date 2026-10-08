package com.audioenhancer.booster

// Batch 133 (Fase 8 A, ROI #6 — "Custom EQ curve editor drag-point", instruksi
// eksplisit user "next" setelah backlog ROI PROJECT_STATE.md ditawarkan). Diferensiator
// vs app EQ generic (kebanyakan cuma slider vertikal polos). PELENGKAP `EqualizerSection`
// (BoosterScreen.kt) yang sudah ada, BUKAN pengganti — slider tetap dipertahankan di
// bawah kurva ini buat adjustment presisi (drag di kurva kurang presisi buat nilai
// eksak, wajar untuk UI drag-point manapun). State TIDAK didup: composable ini murni
// "controlled" — baca `levels` (List<Short> yang sama persis dipakai EqualizerSection,
// termasuk instance SnapshotStateList-nya), tulis lewat `onBandChange` yang dipanggil
// pemanggil PERSIS pola `onValueChange` tiap slider (`levels[band] = level` lalu
// teruskan ke callback asli) — 0 duplikasi source-of-truth, 0 risiko desync
// kurva-vs-slider. 0 perubahan ke `AudioEnhancerService.kt`/`BoosterViewModel.kt`:
// backend get/set-band-level + range + center freq SUDAH lengkap sejak lama (Batch 87),
// fitur ini murni tambahan visual di layer UI.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Batch 188: kelipatan snap kurva (mB) = `step` slider band di `EqualizerSection` (Batch 169),
 *  supaya drag kurva tak memicu apply-efek (binder) + tulis prefs per 1 mB — cukup per notch 50 mB. */
private const val EQ_CURVE_STEP_MB = 50

private fun eqLevelToY(level: Short, lvMin: Short, lvMax: Short, topPad: Float, bottomPad: Float, h: Float): Float {
    val range = (lvMax - lvMin).toFloat().coerceAtLeast(1f)
    val fraction = (level - lvMin).toFloat() / range // 0 = min, 1 = max
    val usable = h - topPad - bottomPad
    return (h - bottomPad) - fraction * usable
}

private fun eqYToLevel(y: Float, lvMin: Short, lvMax: Short, topPad: Float, bottomPad: Float, h: Float): Short {
    val usable = (h - topPad - bottomPad).coerceAtLeast(1f)
    val clampedY = y.coerceIn(topPad, h - bottomPad)
    val fraction = 1f - (clampedY - topPad) / usable
    val raw = lvMin + fraction * (lvMax - lvMin)
    val snapped = (raw / EQ_CURVE_STEP_MB).roundToInt() * EQ_CURVE_STEP_MB
    return snapped.coerceIn(lvMin.toInt(), lvMax.toInt()).toShort()
}

/**
 * Kurva EQ drag-point. Titik per band cuma bisa digeser VERTIKAL (gain) — posisi
 * horizontal tetap sesuai urutan band (frekuensi TIDAK bisa diubah user, sama seperti
 * slider di bawahnya; band ini bukan parametric EQ). Interpolasi antar titik pakai
 * cubic Bezier sederhana (titik tengah per segmen) — cukup buat 5 titik, tanpa
 * dependency spline eksternal.
 */
@Composable
internal fun EqCurveEditor(
    bandCount: Int,
    levelMin: Short,
    levelMax: Short,
    centerFreqsHz: List<Int>,
    levels: List<Short>,
    onBandChange: (Int, Short) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bandCount < 2) return // Kurva butuh minimal 2 titik — guard, bukan skenario nyata (fallback selalu 5 band)

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var draggedBand by remember { mutableIntStateOf(-1) }
    // Batch 188: `pointerInput(bandCount)` TIDAK restart saat `levels` berganti instance (reset /
    // preset di EqualizerSection = `remember(bandCount, resetKey)` → list BARU) — lambda gesture
    // lama membaca list basi → hit-test titik meleset. Baca lewat State terbaru.
    val latestLevels by rememberUpdatedState(levels)
    val latestOnBandChange by rememberUpdatedState(onBandChange)
    val latestMin by rememberUpdatedState(levelMin)
    val latestMax by rememberUpdatedState(levelMax)

    // topPad: clearance titik paling atas (gain max) + label nilai saat drag.
    // bottomPad: ruang label frekuensi di bawah kurva.
    val topPadDp = 26.dp
    val bottomPadDp = 22.dp
    val pointRadiusDp = 7.dp
    val pointRadiusActiveDp = 10.dp

    val mutedColor = LocalSkeuTokens.current.mutedText
    val accent = EqualizerAccent
    val accent2 = EqualizerAccent2

    // Batch 188: objek gambar di-cache lintas frame (sebelumnya Path/Paint/Brush/List/String
    // dialokasi ulang di SETIAP frame draw selama drag).
    val labelPaint = remember(mutedColor, density) {
        android.graphics.Paint().apply {
            color = mutedColor.copy(alpha = 0.85f).toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }
    val valuePaint = remember(accent, density) {
        android.graphics.Paint().apply {
            color = accent.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val freqLabels = remember(centerFreqsHz, bandCount) {
        List(bandCount) { i -> formatFreqLabel(centerFreqsHz.getOrElse(i) { 0 }) }
    }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
    val fillBrush = remember(accent, accent2) {
        Brush.verticalGradient(colors = listOf(accent.copy(alpha = 0.28f), accent2.copy(alpha = 0.04f)))
    }
    val strokeBrush = remember(accent, accent2) { Brush.horizontalGradient(colors = listOf(accent, accent2)) }
    val curveStroke = remember(density) { Stroke(width = with(density) { 2.5.dp.toPx() }) }
    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val ys = remember(bandCount) { FloatArray(bandCount) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(bandCount) {
                val topPad = topPadDp.toPx()
                val bottomPad = bottomPadDp.toPx()
                // Batch 137 (instruksi eksplisit user, "preventing touch" — cegah band
                // ke-ubah tanpa sengaja cuma karena jari LEWAT/bergerak di atas kurva).
                // SEBELUM ini: onDragStart pilih band ke-TERDEKAT semata dari posisi-X
                // sentuhan-turun, ABAIKAN seberapa jauh Y-nya dari titik itu — artinya
                // sentuhan DI MANA PUN di sepanjang kanvas 150dp ini (termasuk swipe yang
                // cuma numpang lewat, bukan diniatkan pegang titik) langsung mengubah nilai
                // band ke posisi-Y sentuhan itu. Fix: hit-test radius 2D (x DAN y) di
                // sekitar posisi RENDER titik terdekat — drag CUMA mulai kalau sentuhan-
                // turun ada dalam `hitRadiusPx` (>radius visual titik, tetap nyaman
                // disentuh) dari titik itu. Di luar radius: `draggedBand` TETAP -1
                // (default `remember`, tidak diubah), 0 band berubah, 0 haptic — dan tetap
                // -1 SEPANJANG sisa gesture (guard `if (draggedBand < 0) return@` di
                // lambda `onDrag` di bawah, sudah ada sejak awal) walau jari terus
                // bergerak kemana pun setelahnya. Jarak dihitung kuadrat (hindari sqrt)
                // biar 0 dependency ke extension `Offset.getDistance()`.
                val hitRadiusPx = 28.dp.toPx()
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val lv = latestLevels
                        val lMin = latestMin
                        val lMax = latestMax
                        val stepX = w / (bandCount - 1).coerceAtLeast(1)
                        val nearest = (offset.x / stepX).roundToInt().coerceIn(0, bandCount - 1)
                        val nearestX = nearest * stepX
                        val current = lv.getOrElse(nearest) { 0 }
                        val nearestY = eqLevelToY(current, lMin, lMax, topPad, bottomPad, h)
                        val dx = offset.x - nearestX
                        val dy = offset.y - nearestY
                        if (dx * dx + dy * dy <= hitRadiusPx * hitRadiusPx) {
                            draggedBand = nearest
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newLevel = eqYToLevel(offset.y, lMin, lMax, topPad, bottomPad, h)
                            if (newLevel != current) latestOnBandChange(nearest, newLevel)
                        }
                        // else: sentuhan di luar radius titik manapun -> dibiarkan, 0 efek.
                    },
                    onDragEnd = { draggedBand = -1 },
                    onDragCancel = { draggedBand = -1 }
                ) { change, _ ->
                    val band = draggedBand
                    if (band < 0) return@detectDragGestures
                    val newLevel = eqYToLevel(change.position.y, latestMin, latestMax, topPad, bottomPad, size.height.toFloat())
                    // Batch 188: lewati kalau tetap di notch yang sama → 0 apply efek/tulis prefs/recompose berulang.
                    if (newLevel != latestLevels.getOrElse(band) { 0 }) latestOnBandChange(band, newLevel)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val topPad = topPadDp.toPx()
        val bottomPad = bottomPadDp.toPx()
        val stepX = w / (bandCount - 1).coerceAtLeast(1)

        val lv = latestLevels
        for (i in 0 until bandCount) {
            ys[i] = eqLevelToY(lv.getOrElse(i) { 0 }, levelMin, levelMax, topPad, bottomPad, h)
        }
        val zeroY = eqLevelToY(0, levelMin, levelMax, topPad, bottomPad, h)

        // Garis baseline 0 gain, putus-putus
        drawLine(
            color = mutedColor.copy(alpha = 0.35f),
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )

        // Kurva smooth lewat titik-titik (cubic bezier titik-tengah per segmen)
        curvePath.reset()
        curvePath.moveTo(0f, ys[0])
        for (i in 1 until bandCount) {
            val midX = ((i - 1) * stepX + i * stepX) / 2f
            curvePath.cubicTo(midX, ys[i - 1], midX, ys[i], i * stepX, ys[i])
        }

        // Area fill tipis dari kurva ke garis 0 gain (nuansa "spectrum EQ" asli)
        fillPath.reset()
        fillPath.addPath(curvePath)
        fillPath.lineTo((bandCount - 1) * stepX, zeroY)
        fillPath.lineTo(0f, zeroY)
        fillPath.close()
        drawPath(path = fillPath, brush = fillBrush, style = Fill)
        drawPath(path = curvePath, brush = strokeBrush, style = curveStroke)

        for (i in 0 until bandCount) {
            val p = Offset(i * stepX, ys[i])
            val isActive = i == draggedBand
            val radius = if (isActive) pointRadiusActiveDp.toPx() else pointRadiusDp.toPx()
            drawCircle(color = Color.White, radius = radius + 1.dp.toPx(), center = p)
            drawCircle(color = if (isActive) accent else accent2, radius = radius, center = p)

            drawContext.canvas.nativeCanvas.drawText(freqLabels[i], p.x, h - 4.dp.toPx(), labelPaint)
            if (isActive) {
                val mb = lv.getOrElse(i) { 0 }
                val sign = if (mb > 0) "+" else ""
                drawContext.canvas.nativeCanvas.drawText(
                    "$sign$mb mB",
                    p.x,
                    (p.y - radius - 10.dp.toPx()).coerceAtLeast(12.dp.toPx()),
                    valuePaint
                )
            }
        }
    }
}
