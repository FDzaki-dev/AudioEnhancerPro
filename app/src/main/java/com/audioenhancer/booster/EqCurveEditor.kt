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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    var draggedBand by remember { mutableIntStateOf(-1) }

    // topPad: clearance titik paling atas (gain max) + label nilai saat drag.
    // bottomPad: ruang label frekuensi di bawah kurva.
    val topPadDp = 26.dp
    val bottomPadDp = 22.dp
    val pointRadiusDp = 7.dp
    val pointRadiusActiveDp = 10.dp

    val mutedColor = LocalSkeuTokens.current.mutedText
    val accent = EqualizerAccent
    val accent2 = EqualizerAccent2

    fun levelToY(level: Short, topPad: Float, bottomPad: Float, h: Float): Float {
        val range = (levelMax - levelMin).toFloat().coerceAtLeast(1f)
        val fraction = (level - levelMin).toFloat() / range // 0 = min, 1 = max
        val usable = h - topPad - bottomPad
        return (h - bottomPad) - fraction * usable
    }

    fun yToLevel(y: Float, topPad: Float, bottomPad: Float, h: Float): Short {
        val usable = (h - topPad - bottomPad).coerceAtLeast(1f)
        val clampedY = y.coerceIn(topPad, h - bottomPad)
        val fraction = 1f - (clampedY - topPad) / usable
        val level = levelMin + fraction * (levelMax - levelMin)
        return level.roundToInt().coerceIn(levelMin.toInt(), levelMax.toInt()).toShort()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(bandCount) {
                val topPad = topPadDp.toPx()
                val bottomPad = bottomPadDp.toPx()
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val stepX = w / (bandCount - 1).coerceAtLeast(1)
                        val nearest = (offset.x / stepX).roundToInt().coerceIn(0, bandCount - 1)
                        draggedBand = nearest
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val newLevel = yToLevel(offset.y, topPad, bottomPad, size.height.toFloat())
                        onBandChange(nearest, newLevel)
                    },
                    onDragEnd = { draggedBand = -1 },
                    onDragCancel = { draggedBand = -1 }
                ) { change, _ ->
                    if (draggedBand < 0) return@detectDragGestures
                    val newLevel = yToLevel(change.position.y, topPad, bottomPad, size.height.toFloat())
                    onBandChange(draggedBand, newLevel)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val topPad = topPadDp.toPx()
        val bottomPad = bottomPadDp.toPx()
        val stepX = w / (bandCount - 1).coerceAtLeast(1)

        val points = (0 until bandCount).map { i ->
            val level = levels.getOrElse(i) { 0 }
            Offset(i * stepX, levelToY(level, topPad, bottomPad, h))
        }
        val zeroY = levelToY(0, topPad, bottomPad, h)

        // Garis baseline 0 gain, putus-putus
        drawLine(
            color = mutedColor.copy(alpha = 0.35f),
            start = Offset(0f, zeroY),
            end = Offset(w, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        // Kurva smooth lewat titik-titik (cubic bezier titik-tengah per segmen)
        val curvePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }

        // Area fill tipis dari kurva ke garis 0 gain (nuansa "spectrum EQ" asli)
        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, zeroY)
            lineTo(points.first().x, zeroY)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.28f), accent2.copy(alpha = 0.04f))
            ),
            style = Fill
        )
        drawPath(
            path = curvePath,
            brush = Brush.horizontalGradient(colors = listOf(accent, accent2)),
            style = Stroke(width = 2.5.dp.toPx())
        )

        val labelPaint = android.graphics.Paint().apply {
            color = mutedColor.copy(alpha = 0.85f).toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        val valuePaint = android.graphics.Paint().apply {
            color = accent.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 11.sp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }

        points.forEachIndexed { i, p ->
            val isActive = i == draggedBand
            val radius = if (isActive) pointRadiusActiveDp.toPx() else pointRadiusDp.toPx()
            drawCircle(color = Color.White, radius = radius + 1.dp.toPx(), center = p)
            drawCircle(color = if (isActive) accent else accent2, radius = radius, center = p)

            drawContext.canvas.nativeCanvas.drawText(
                formatFreqLabel(centerFreqsHz.getOrElse(i) { 0 }),
                p.x,
                h - 4.dp.toPx(),
                labelPaint
            )
            if (isActive) {
                val mb = levels.getOrElse(i) { 0 }
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
