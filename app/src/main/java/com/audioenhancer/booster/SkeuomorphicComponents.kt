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

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GfxCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
private const val ShadowSteps = 6

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
                // Batch 56 (diminta user, "push lebih dalam lagi"): multiplier
                // 1.15f -> 1.6f — bleed shadow lebih jauh dari tepi bentuk,
                // depth/kontras lebih kerasa tanpa ubah struktur teknik.
                val maxSpread = depth.toPx() * 1.6f
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

// =====================================================================================
// Batch 180-184 — MESIN KEDALAMAN FISIK (timbul + cekung), HANYA aktif saat
// `LocalSkeuTokens.current.depth != null` (Old Money). 5 varian lain 0 perubahan: cabang
// `depth == null` memanggil kode lama persis.
//
// Kenapa B179 "nyaru": sorot gading alpha 2% + bayangan alpha 32% SEHUE dgn latar, permukaan
// kartu cuma +-3 level dari latar → kedalaman nyaris tak terbaca. Mesin ini memakai 3 sumber
// kedalaman yang terbaca: (1) LUMINANSI — permukaan pelat lebih terang dari latar, lantai sumur
// jauh lebih gelap; (2) bevel FACET per-sisi menurut arah cahaya (sisi menghadap cahaya = sorot
// gading, sisi membelakangi = hitam hangat, bukan gradien miring palsu); (3) bayangan jatuh
// Gaussian beneran 3 lapis (kontak / tengah / ambient) + grain halus agar permukaan bukan datar.
//
// OPTIMASI BEBAN PROSESOR (Batch 184, request user "efek jangan membebani prosesor device"):
//  1. Blur Gaussian di-render di bitmap PERANGKAT LUNAK (`Canvas(ImageBitmap)` + `BlurMaskFilter`;
//     BUKAN canvas hardware = alasan larangan B14/B32) — dan untuk kartu besar HANYA SEKALI:
//     satu template 9-slice kecil dipakai bersama SEMUA kartu (8 `drawImage` irisan, bagian tengah
//     meregang), jadi 0 render blur per kartu & 0 render ulang saat ukuran kartu berubah. Hanya
//     elemen kecil/pendek (< 2x zona sudut template) memakai bitmap per-ukuran.
//  2. Semua bitmap lewat cache GLOBAL ber-batas (`DepthBitmapStore`, LRU 48 entri): elemen berukuran
//     sama (kotak ikon, knob, thumb) berbagi 1 bitmap; memori terbatas.
//  3. Lambda gambar memakai `depthDraw` (kunci `remember`) → identitas lambda STABIL antar
//     rekomposisi, sehingga `drawWithCache` TIDAK dibangun ulang & tidak di-invalidate tiap
//     rekomposisi (mis. saat drag slider yang merekomposisi seluruh layar).
//  4. Permukaan pelat (gradien + kilau + peredupan) di-bake ke 1 tekstur 48x48 bersama →
//     1 `drawImage` menggantikan 4 pengisian gradien layar penuh per kartu.
//  5. Tak ada alokasi di lambda gambar (Path/Brush dibangun di blok cache, bukan per frame).
// =====================================================================================

private const val DepthWellBitmapScale = 0.75f
private const val DepthFallbackScale = 0.3333f
private const val DepthTemplateScale = 0.5f
private const val DepthSqrt2 = 1.4142135f

// Template 9-slice bayangan pelat (dp): zona sudut tak-meregang Kx/Ky (>= chamfer 8 + offset
// maks + 2.5 sigma terlebar), margin kiri/atas/kanan/bawah, lebar pita tengah Z. Divalidasi simulasi:
// selisih alpha maks 3%, rata-rata 0.2% vs render langsung. Elemen < 2*Kx x 2*Ky → bitmap sendiri.
private const val SliceKx = 44f
private const val SliceKy = 52f
private const val SliceML = 16f
private const val SliceMT = 10f
private const val SliceMR = 34f
private const val SliceMB = 40f
private const val SliceZ = 8f

/** Satu lapis bayangan jatuh. [dx]/[dy] = offset (dp, + = kanan/bawah, menjauhi cahaya kiri-atas),
 *  [blur] = lebar blur (~2 sigma, dp), [alpha] = kepekatan lapis. */
private class DepthShadowLayer(val dx: Float, val dy: Float, val blur: Float, val alpha: Float)

/** Kumpulan lapis + margin bleed seragam (dp) + skala bitmap untuk jalur bitmap-per-ukuran. */
private class DepthShadowSpec(val layers: List<DepthShadowLayer>, val bleedDp: Float, val scale: Float)

// Pelat/kartu: kontak tajam + tengah + ambient lebar.
private val DepthPlateShadow = DepthShadowSpec(
    listOf(
        DepthShadowLayer(1.0f, 1.5f, 2.0f, 0.85f),
        DepthShadowLayer(4.0f, 6.0f, 8.0f, 0.62f),
        DepthShadowLayer(10.0f, 15.0f, 18.0f, 0.50f)
    ),
    34f,
    DepthFallbackScale
)

// Knob kecil (thumb slider/switch).
private val DepthKnobShadow = DepthShadowSpec(
    listOf(
        DepthShadowLayer(0.6f, 1.0f, 1.4f, 0.85f),
        DepthShadowLayer(1.8f, 3.0f, 4.0f, 0.55f)
    ),
    10f,
    DepthWellBitmapScale
)

// Power button.
private val DepthButtonShadow = DepthShadowSpec(
    listOf(
        DepthShadowLayer(0.8f, 1.2f, 2.0f, 0.85f),
        DepthShadowLayer(2.5f, 4.0f, 6.0f, 0.60f),
        DepthShadowLayer(6.0f, 9.0f, 12.0f, 0.45f)
    ),
    24f,
    DepthWellBitmapScale
)

// Tombol/pil/tab (kunci).
private val DepthKeyShadow = DepthShadowSpec(
    listOf(
        DepthShadowLayer(0.5f, 0.9f, 1.2f, 0.85f),
        DepthShadowLayer(1.8f, 3.0f, 4.5f, 0.55f)
    ),
    10f,
    DepthWellBitmapScale
)

/** Bagian dekorasi pelat yang digambar: [face] permukaan ter-bake, [grain] butiran, [frame] alur
 *  ukir; [front] = digambar DI ATAS konten (overlay dialog), bukan di belakang. */
private class DepthParts(val face: Boolean, val grain: Boolean, val frame: Boolean, val front: Boolean)

private val DepthPartsPlate = DepthParts(face = true, grain = true, frame = true, front = false)
private val DepthPartsKey = DepthParts(face = false, grain = false, frame = false, front = false)
private val DepthPartsDialog = DepthParts(face = false, grain = true, frame = true, front = true)

/** `BlurMaskFilter.radius` → sigma = 0.57735 * radius + 0.5 (konversi Skia). Dibalik di sini
 *  supaya parameter layer = sigma yang diinginkan (px bitmap), minimal radius 0.5 (radius <= 0
 *  melempar IllegalArgumentException). */
private fun depthBlurRadius(sigmaPx: Float): Float = ((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.5f)

private fun Outline.toDepthPath(): Path {
    val o = this
    return when (o) {
        is Outline.Rectangle -> Path().apply { addRect(o.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
        is Outline.Generic -> o.path
    }
}

/** Kunci cache global. [kind]: 1 template bayangan pelat, 2 bayangan per-ukuran, 3 bayangan dalam
 *  sumur, 4 permukaan pelat ter-bake. */
private data class DepthStoreKey(
    val kind: Int,
    val w: Int,
    val h: Int,
    val density: Float,
    val shape: Shape?,
    val style: DepthStyle?,
    val extra: Any?
)

/** Cache GLOBAL bitmap efek (LRU, maks 48 entri): dibagi lintas elemen berukuran sama & lintas
 *  rekomposisi; memori terbatas. Hanya dipakai dari thread UI (blok cache gambar), tetap disinkronkan. */
private object DepthBitmapStore {
    private const val MAX_ENTRIES = 48
    private val map = object : LinkedHashMap<Any, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(key: Any, build: () -> ImageBitmap): ImageBitmap = synchronized(map) {
        val hit = map[key]
        if (hit != null) {
            hit
        } else {
            val fresh = build()
            map[key] = fresh
            fresh
        }
    }
}

/** `drawWithCache` dgn lambda STABIL: [block] dari pemanggil disimpan lewat `remember(keys)`, jadi
 *  rekomposisi dgn kunci sama TIDAK mengganti lambda → cache gambar tak dibangun ulang & node tak
 *  di-invalidate (lihat poin 3 di komentar atas). Kunci = SEMUA parameter yang dipakai blok.
 *
 *  WAJIB (Batch 185, akar crash `ClassCastException` v224): SEMUA modifier `depth*` lewat SATU lambda
 *  `composed` ini → grup/slot compose-nya DIBAGI. `remember(*keys)` (vararg) memakai 1 slot PER kunci,
 *  jadi saat rantai modifier berganti varian di posisi yg sama (mis. `depthCastShadow` ↔
 *  `depthWellLip` ketika kunci ditekan) jumlah slot beda → slot lambda terbaca sbg kunci lama →
 *  cast gagal. Solusi: kunci digabung jadi SATU `List` (slot tetap = 1 kunci + 1 nilai, apa pun
 *  variannya) dan kunci PERTAMA selalu [tag] unik per jenis modifier supaya dua varian berkunci
 *  mirip tak pernah saling memakai lambda. Jangan ganti ke `remember(*keys)`. */
private fun Modifier.depthDraw(tag: String, vararg keys: Any?, block: CacheDrawScope.() -> DrawResult): Modifier =
    composed {
        val keyList = listOf(tag, *keys)
        val stable = remember(keyList) { block }
        this.drawWithCache(stable)
    }

/** Tekstur butiran (kulit/kertas): tile 128px, derau halus + derau lembut (32px di-upsample
 *  bilinear, wrap → mulus saat di-tile). Deterministik (seed tetap). Putih gading di sisi +,
 *  hitam di sisi - → menggelapkan/menerangi permukaan tipis tanpa menggeser hue. */
private val DepthGrainTile: ImageBitmap by lazy {
    val n = 128
    val m = 32
    val step = n / m
    val rnd = java.util.Random(180L)
    val fine = FloatArray(n * n) { rnd.nextGaussian().toFloat() }
    val low = FloatArray(m * m) { rnd.nextGaussian().toFloat() }
    val px = IntArray(n * n)
    for (y in 0 until n) {
        val fy = y / step.toFloat()
        val y0 = fy.toInt()
        val ty = fy - y0
        for (x in 0 until n) {
            val fx = x / step.toFloat()
            val x0 = fx.toInt()
            val tx = fx - x0
            val a = low[(y0 % m) * m + (x0 % m)]
            val b = low[(y0 % m) * m + ((x0 + 1) % m)]
            val c = low[((y0 + 1) % m) * m + (x0 % m)]
            val d = low[((y0 + 1) % m) * m + ((x0 + 1) % m)]
            val soft = (a * (1f - tx) + b * tx) * (1f - ty) + (c * (1f - tx) + d * tx) * ty
            val v = ((0.65f * fine[y * n + x] + 0.55f * soft) / 2.5f).coerceIn(-1f, 1f)
            val alpha = (abs(v) * 255f).toInt()
            px[y * n + x] = if (v >= 0f) (alpha shl 24) or 0x00F2EADB else (alpha shl 24)
        }
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val DepthGrainBrush: Brush by lazy {
    ShaderBrush(ImageShader(DepthGrainTile, TileMode.Repeated, TileMode.Repeated))
}

/** Permukaan pelat ter-bake 48x48 (tak bergantung ukuran kartu, di-skala bilinear): gradien
 *  kiri-atas→kanan-bawah + peredupan lembut ke kanan-bawah + kilau lembut dari sisi cahaya. Menggantikan
 *  4 pengisian gradien layar penuh per kartu dengan 1 `drawImage` (opak). [tint] = geser ke warna status. */
private fun renderDepthFace(style: DepthStyle, tint: Color?): ImageBitmap {
    val n = 48
    val top = (if (tint != null) lerp(style.faceTop, tint, 0.16f) else style.faceTop).toArgb()
    val bottom = (if (tint != null) lerp(style.faceBottom, tint, 0.12f) else style.faceBottom).toArgb()
    val lit = (if (tint != null) lerp(style.rimLight, tint, 0.28f) else style.rimLight).toArgb()
    val shade = style.rimShade.toArgb()
    val px = IntArray(n * n)
    for (y in 0 until n) {
        val v = (y + 0.5f) / n
        for (x in 0 until n) {
            val u = (x + 0.5f) / n
            val t = (u + v) * 0.5f
            val dv = kotlin.math.hypot(u - 0.40f, v - 0.35f).coerceIn(0f, 1f)
            val va = dv * 0.14f
            val ds = (kotlin.math.hypot(u - 0.20f, v - 0.05f) / 0.85f).coerceIn(0f, 1f)
            val sa = (1f - ds) * 0.07f
            var rgb = 0
            for (shift in intArrayOf(16, 8, 0)) {
                val c0 = ((top shr shift) and 255).toFloat()
                val c1 = ((bottom shr shift) and 255).toFloat()
                var c = c0 + (c1 - c0) * t
                c = c * (1f - va) + ((shade shr shift) and 255).toFloat() * va
                c = c * (1f - sa) + ((lit shr shift) and 255).toFloat() * sa
                rgb = rgb or (c.roundToInt().coerceIn(0, 255) shl shift)
            }
            px[y * n + x] = (0xFF shl 24) or rgb
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Render bayangan jatuh (timbul) ke bitmap perangkat lunak. Bitmap = bentuk + margin
 *  ([mL],[mT],[mR],[mB] dp); bentuk digambar di (mL, mT). Semua panjang dihitung dalam px-bitmap
 *  (`dp * density * scale`), `Density(k)` membuat sudut chamfer/rounded (dalam dp) ikut skala. */
private fun renderDepthCastShadow(
    shape: Shape,
    wPx: Float,
    hPx: Float,
    density: Float,
    style: DepthStyle,
    layers: List<DepthShadowLayer>,
    mL: Float,
    mT: Float,
    mR: Float,
    mB: Float,
    scale: Float
): ImageBitmap {
    val k = density * scale
    val bw = ceil((wPx + (mL + mR) * density) * scale).toInt().coerceAtLeast(2)
    val bh = ceil((hPx + (mT + mB) * density) * scale).toInt().coerceAtLeast(2)
    val bmp = ImageBitmap(bw, bh, ImageBitmapConfig.Argb8888)
    val canvas = GfxCanvas(bmp)
    val base = shape.createOutline(Size(wPx * scale, hPx * scale), LayoutDirection.Ltr, Density(k)).toDepthPath()
    for (layer in layers) {
        val path = Path().apply { addPath(base, Offset(mL * k + layer.dx * k, mT * k + layer.dy * k)) }
        val paint = Paint().apply {
            isAntiAlias = true
            color = style.castShadow.copy(alpha = layer.alpha)
        }
        paint.asFrameworkPaint().maskFilter =
            BlurMaskFilter(depthBlurRadius(layer.blur * 0.5f * k), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
    }
    return bmp
}

/** Render bayangan DALAM (cekung): oklusi ambien merata + bayangan gelap dari dinding sisi kiri-atas
 *  + bibir terang tipis di dinding sisi kanan-bawah, dipotong ke bentuk (`DstOut` pada area luar =
 *  tepi tetap mulus). */
private fun renderDepthWellInner(shape: Shape, wPx: Float, hPx: Float, density: Float, style: DepthStyle): ImageBitmap {
    val bw = (wPx * DepthWellBitmapScale).roundToInt().coerceAtLeast(1)
    val bh = (hPx * DepthWellBitmapScale).roundToInt().coerceAtLeast(1)
    val k = density * DepthWellBitmapScale
    val bmp = ImageBitmap(bw, bh, ImageBitmapConfig.Argb8888)
    val canvas = GfxCanvas(bmp)
    val shapePath = shape.createOutline(Size(bw.toFloat(), bh.toFloat()), LayoutDirection.Ltr, Density(k)).toDepthPath()
    val pad = 48f * k
    fun outside(dx: Float, dy: Float): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-pad + dx, -pad + dy, bw + pad + dx, bh + pad + dy))
        addPath(shapePath, Offset(dx, dy))
    }
    val ao = Paint().apply {
        isAntiAlias = true
        color = style.castShadow.copy(alpha = 0.40f)
    }
    ao.asFrameworkPaint().maskFilter = BlurMaskFilter(depthBlurRadius(2.6f * k), BlurMaskFilter.Blur.NORMAL)
    canvas.drawPath(outside(0.4f * k, 0.6f * k), ao)
    val shadow = Paint().apply {
        isAntiAlias = true
        color = style.castShadow.copy(alpha = 0.85f)
    }
    shadow.asFrameworkPaint().maskFilter = BlurMaskFilter(depthBlurRadius(1.6f * k), BlurMaskFilter.Blur.NORMAL)
    canvas.drawPath(outside(2.0f * k, 2.6f * k), shadow)
    val lip = Paint().apply {
        isAntiAlias = true
        color = style.rimLight.copy(alpha = 0.20f)
    }
    lip.asFrameworkPaint().maskFilter = BlurMaskFilter(depthBlurRadius(0.5f * k), BlurMaskFilter.Blur.NORMAL)
    canvas.drawPath(outside(-1.0f * k, -1.2f * k), lip)
    val cut = Paint().apply {
        isAntiAlias = true
        color = Color.Black
        blendMode = BlendMode.DstOut
    }
    canvas.drawPath(outside(0f, 0f), cut)
    return bmp
}

/** Gambar bitmap bayangan per-ukuran (margin seragam [bleedDp] di semua sisi). */
private fun DrawScope.drawShadowBitmap(bmp: ImageBitmap, density: Float, scale: Float, bleedDp: Float) {
    val m = (bleedDp * density).roundToInt()
    drawImage(
        image = bmp,
        dstOffset = IntOffset(-m, -m),
        dstSize = IntSize((bmp.width / scale).roundToInt(), (bmp.height / scale).roundToInt())
    )
}

/** Gambar template bayangan pelat sebagai 8 irisan (9-slice; irisan tengah tertutup permukaan
 *  pelat opak jadi dilewati). Sudut tak meregang, pita tepi meregang sepanjang sumbunya. */
private fun DrawScope.drawShadowSlices(bmp: ImageBitmap, w: Float, h: Float, density: Float, scale: Float) {
    val q = density * scale
    val f = 1f / scale
    val lw = ((SliceML + SliceKx) * q).roundToInt()
    val cw = (SliceZ * q).roundToInt()
    val rw = bmp.width - lw - cw
    val th = ((SliceMT + SliceKy) * q).roundToInt()
    val ch = (SliceZ * q).roundToInt()
    val bth = bmp.height - th - ch
    val xL0 = -SliceML * density
    val xL1 = xL0 + lw * f
    val xR1 = w + SliceMR * density
    val xR0 = xR1 - rw * f
    val yT0 = -SliceMT * density
    val yT1 = yT0 + th * f
    val yB1 = h + SliceMB * density
    val yB0 = yB1 - bth * f
    fun patch(sx: Int, sy: Int, sw: Int, sh: Int, x0: Float, y0: Float, x1: Float, y1: Float) {
        val ix0 = x0.roundToInt()
        val iy0 = y0.roundToInt()
        val ix1 = x1.roundToInt()
        val iy1 = y1.roundToInt()
        if (sw > 0 && sh > 0 && ix1 > ix0 && iy1 > iy0) {
            drawImage(bmp, IntOffset(sx, sy), IntSize(sw, sh), IntOffset(ix0, iy0), IntSize(ix1 - ix0, iy1 - iy0))
        }
    }
    patch(0, 0, lw, th, xL0, yT0, xL1, yT1)
    patch(lw, 0, cw, th, xL1, yT0, xR0, yT1)
    patch(lw + cw, 0, rw, th, xR0, yT0, xR1, yT1)
    patch(0, th, lw, ch, xL0, yT1, xL1, yB0)
    patch(lw + cw, th, rw, ch, xR0, yT1, xR1, yB0)
    patch(0, th + ch, lw, bth, xL0, yB0, xL1, yB1)
    patch(lw, th + ch, cw, bth, xL1, yB0, xR0, yB1)
    patch(lw + cw, th + ch, rw, bth, xR0, yB0, xR1, yB1)
}

/** Bayangan jatuh (digambar DI BELAKANG, boleh keluar batas — jangan di-clip di atasnya).
 *  [slice] = pakai template 9-slice bersama bila ukuran cukup besar (kartu); selain itu bitmap per-ukuran. */
private fun Modifier.depthCastShadow(
    shape: Shape,
    style: DepthStyle,
    spec: DepthShadowSpec,
    slice: Boolean = false
): Modifier = depthDraw("castShadow", shape, style, spec, slice) {
    val w = size.width
    val h = size.height
    val d = density
    if (w < 1f || h < 1f) {
        onDrawBehind { }
    } else if (slice && w >= 2f * SliceKx * d && h >= 2f * SliceKy * d) {
        val tpl = DepthBitmapStore.get(DepthStoreKey(1, 0, 0, d, shape, style, spec)) {
            renderDepthCastShadow(
                shape, (2f * SliceKx + SliceZ) * d, (2f * SliceKy + SliceZ) * d, d, style, spec.layers,
                SliceML, SliceMT, SliceMR, SliceMB, DepthTemplateScale
            )
        }
        onDrawBehind { drawShadowSlices(tpl, w, h, d, DepthTemplateScale) }
    } else {
        val bmp = DepthBitmapStore.get(DepthStoreKey(2, w.roundToInt(), h.roundToInt(), d, shape, style, spec)) {
            renderDepthCastShadow(
                shape, w, h, d, style, spec.layers, spec.bleedDp, spec.bleedDp, spec.bleedDp, spec.bleedDp, spec.scale
            )
        }
        onDrawBehind { drawShadowBitmap(bmp, d, spec.scale, spec.bleedDp) }
    }
}

/** Bayangan dalam sumur cekung — digambar di ATAS lantai sumur, di dalam bentuk (bitmap sudah
 *  dipotong ke bentuk). Taruh SETELAH `.background(lantai)`, SEBELUM konten/thumb. */
private fun Modifier.depthWellInner(shape: Shape, style: DepthStyle): Modifier = depthDraw("wellInner", shape, style) {
    val w = size.width
    val h = size.height
    val d = density
    if (w < 2f || h < 2f) {
        onDrawBehind { }
    } else {
        val wi = w.roundToInt()
        val hi = h.roundToInt()
        val bmp = DepthBitmapStore.get(DepthStoreKey(3, wi, hi, d, shape, style, null)) {
            renderDepthWellInner(shape, wi.toFloat(), hi.toFloat(), d, style)
        }
        onDrawBehind { drawImage(bmp, dstSize = IntSize(wi, hi)) }
    }
}

/** Bibir luar sumur: garis terang tipis di tepi kanan-bawah LUAR sumur (tempat cahaya menangkap
 *  bibir lubang). Dipasang SEBELUM `.clip(shape)` supaya tidak terpotong; lantai sumur menutupi
 *  sisanya sehingga hanya sabit tipis di kanan-bawah yang tampak. */
private fun Modifier.depthWellLip(shape: Shape, style: DepthStyle): Modifier = depthDraw("wellLip", shape, style) {
    val w = size.width
    val h = size.height
    val shift = Offset(0.7.dp.toPx(), 1.0.dp.toPx())
    val base = if (w >= 1f && h >= 1f) shape.createOutline(size, layoutDirection, this).toDepthPath() else null
    val lip = if (base != null) Path().apply { addPath(base, shift) } else null
    onDrawBehind {
        if (lip != null) drawPath(lip, style.rimLight.copy(alpha = 0.22f))
    }
}

/** Butiran halus di latar layar (kulit gelap) — hanya Old Money (dipanggil dari MainActivity).
 *  Menggambar tekstur yang sama dgn pelat dengan alpha rendah di BELAKANG semua konten. */
internal fun Modifier.skeuBackdropGrain(): Modifier = this.drawBehind {
    drawRect(brush = DepthGrainBrush, alpha = 0.05f)
}

/** Segi-8 chamfer pada jarak [inset] dari tepi (titik sudut = rumus segi-8 dalam bevel; [k] = c + inset*(√2-1)). */
private fun depthOctagon(w: Float, h: Float, inset: Float, k: Float): Path = Path().apply {
    moveTo(k, inset)
    lineTo(w - k, inset)
    lineTo(w - inset, k)
    lineTo(w - inset, h - k)
    lineTo(w - k, h - inset)
    lineTo(k, h - inset)
    lineTo(inset, h - k)
    lineTo(inset, k)
    close()
}

/** Dekorasi pelat timbul: permukaan ter-bake + grain + alur ukir + bevel FACET 8 sisi (bentuk
 *  chamfer [chamfer] seragam), bagian yang aktif menurut [parts]. Tiap facet (atas, kanan, bawah,
 *  kiri, 4 diagonal) diwarnai menurut `dot(normal, arahCahaya)`: > 0 = sorot gading (alpha skala
 *  cos), < 0 = hitam hangat — seperti pelat logam/kulit di-emboss dgn sumber cahaya tunggal.
 *  [tint] (kartu bertint) menggeser permukaan & sorot ke warna status. [bevel] = lebar facet. */
private fun Modifier.depthPlateSurface(
    style: DepthStyle,
    chamfer: Dp,
    parts: DepthParts = DepthPartsPlate,
    tint: Color? = null,
    bevel: Dp = style.bevelWidth
): Modifier = depthDraw("plateSurface", style, chamfer, parts, tint, bevel) {
    val w = size.width
    val h = size.height
    val lit = if (tint != null) lerp(style.rimLight, tint, 0.28f) else style.rimLight
    val b = bevel.toPx()
    val c = min(chamfer.toPx(), min(w, h) / 2f)
    val k = c + b * (DepthSqrt2 - 1f)
    val frameInset = style.frameInset.toPx()
    val frameShiftX = 0.8.dp.toPx()
    val frameShiftY = 0.9.dp.toPx()
    val frameStroke = 1.dp.toPx()
    val framePath: Path? =
        if (parts.frame && frameInset > 0f && w > frameInset * 4f && h > frameInset * 4f) {
            depthOctagon(w, h, frameInset, c + frameInset * (DepthSqrt2 - 1f))
        } else {
            null
        }
    val face: ImageBitmap? =
        if (parts.face && w >= 4f && h >= 4f) {
            DepthBitmapStore.get(DepthStoreKey(4, 0, 0, 1f, null, style, tint)) { renderDepthFace(style, tint) }
        } else {
            null
        }
    val facets = ArrayList<Pair<Path, Color>>(8)
    if (w >= 4f && h >= 4f) {
        // Segi-8 luar (O) & dalam (I, inset b). Facet i = O[i] -> O[i+1] -> I[i+1] -> I[i].
        val ox = floatArrayOf(c, w - c, w, w, w - c, c, 0f, 0f)
        val oy = floatArrayOf(0f, 0f, c, h - c, h, h, h - c, c)
        val ix = floatArrayOf(k, w - k, w - b, w - b, w - k, k, b, b)
        val iy = floatArrayOf(b, b, k, h - k, h - b, h - b, h - k, k)
        // Normal keluar tiap facet, urut: atas, kanan-atas, kanan, kanan-bawah, bawah, kiri-bawah, kiri, kiri-atas.
        val nx = floatArrayOf(0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f)
        val ny = floatArrayOf(-1f, -0.7071f, 0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f)
        for (i in 0 until 8) {
            val j = (i + 1) % 8
            val l = nx[i] * style.lightX + ny[i] * style.lightY
            val scaled = min(1f, abs(l) / 0.83f)
            val color = if (l > 0f) {
                lit.copy(alpha = style.rimLightAlpha * scaled)
            } else {
                style.rimShade.copy(alpha = style.rimShadeAlpha * scaled)
            }
            val quad = Path().apply {
                moveTo(ox[i], oy[i])
                lineTo(ox[j], oy[j])
                lineTo(ix[j], iy[j])
                lineTo(ix[i], iy[i])
                close()
            }
            facets.add(Pair(quad, color))
        }
    }
    val wi = w.roundToInt()
    val hi = h.roundToInt()
    val ops: DrawScope.() -> Unit = {
        if (face != null) drawImage(face, dstSize = IntSize(wi, hi))
        if (parts.grain) drawRect(brush = DepthGrainBrush, alpha = style.grainAlpha)
        if (framePath != null) {
            drawPath(framePath, style.rimShade.copy(alpha = 0.40f), style = Stroke(width = frameStroke))
            translate(left = frameShiftX, top = frameShiftY) {
                drawPath(framePath, lit.copy(alpha = 0.09f), style = Stroke(width = frameStroke))
            }
        }
        for (f in facets) drawPath(f.first, f.second)
    }
    if (parts.front) {
        onDrawWithContent {
            drawContent()
            ops()
        }
    } else {
        onDrawBehind(ops)
    }
}

/** Kubah knob: sorot radial miring ke arah cahaya (kiri-atas), makin gelap ke kanan-bawah, plus
 *  titik kilau (specular) kecil di sisi cahaya — terbaca sebagai mutiara/kubah licin. */
private fun Modifier.depthDome(hi: Color, lo: Color, style: DepthStyle): Modifier =
    depthDraw("dome", hi, lo, style) {
        val w = size.width
        val h = size.height
        val dome = Brush.radialGradient(
            colors = listOf(hi, lo),
            center = Offset(w / 2f + style.lightX * w * 0.22f, h / 2f + style.lightY * h * 0.22f),
            radius = (max(w, h) * 0.95f).coerceAtLeast(1f)
        )
        val spec = Brush.radialGradient(
            colors = listOf(style.rimLight.copy(alpha = 0.55f), style.rimLight.copy(alpha = 0f)),
            center = Offset(w / 2f + style.lightX * w * 0.24f, h / 2f + style.lightY * h * 0.24f),
            radius = (max(w, h) * 0.26f).coerceAtLeast(1f)
        )
        onDrawBehind {
            drawCircle(brush = dome)
            drawCircle(brush = spec)
        }
    }

/** Cincin tepi bundar: terang di sisi cahaya, gelap di sisi sebaliknya (gradien linear searah
 *  cahaya = kecerahan ∝ cos sudut, persis fisika tepi silinder). */
private fun Modifier.depthRingRim(style: DepthStyle, width: Dp): Modifier = depthDraw("ringRim", style, width) {
    val w = size.width
    val h = size.height
    val rimW = width.toPx()
    val r = min(w, h) / 2f
    val cx = w / 2f
    val cy = h / 2f
    val rim = Brush.linearGradient(
        0.00f to style.rimLight.copy(alpha = style.rimLightAlpha),
        0.50f to style.rimLight.copy(alpha = 0f),
        0.51f to style.rimShade.copy(alpha = 0f),
        1.00f to style.rimShade.copy(alpha = style.rimShadeAlpha),
        start = Offset(cx + style.lightX * r, cy + style.lightY * r),
        end = Offset(cx - style.lightX * r, cy - style.lightY * r)
    )
    onDrawBehind {
        drawCircle(brush = rim, radius = (r - rimW / 2f).coerceAtLeast(0.5f), style = Stroke(width = rimW))
    }
}

private fun Modifier.depthKnobFace(style: DepthStyle, hi: Color, lo: Color): Modifier =
    this.depthDome(hi, lo, style).depthRingRim(style, 1.4.dp)

/** Rim pil stadium: sisi atas/bawah = garis lurus (facet datar → warna seragam: terang di atas,
 *  gelap di bawah), ujung kiri/kanan = busur dgn gradien searah cahaya (kecerahan ∝ cos, sama
 *  seperti `depthRingRim` untuk lingkaran). */
private fun Modifier.depthPillRim(style: DepthStyle, width: Dp): Modifier = depthDraw("pillRim", style, width) {
    val w = size.width
    val h = size.height
    val rimW = width.toPx()
    val half = rimW / 2f
    val r = h / 2f
    val litColor = style.rimLight.copy(alpha = style.rimLightAlpha)
    val shadeColor = style.rimShade.copy(alpha = style.rimShadeAlpha)
    fun capBrush(cx: Float, cy: Float): Brush = Brush.linearGradient(
        0.00f to style.rimLight.copy(alpha = style.rimLightAlpha),
        0.50f to style.rimLight.copy(alpha = 0f),
        0.51f to style.rimShade.copy(alpha = 0f),
        1.00f to style.rimShade.copy(alpha = style.rimShadeAlpha),
        start = Offset(cx + style.lightX * r, cy + style.lightY * r),
        end = Offset(cx - style.lightX * r, cy - style.lightY * r)
    )
    val leftBrush = capBrush(r, r)
    val rightBrush = capBrush(w - r, r)
    val arcSize = Size((h - rimW).coerceAtLeast(1f), (h - rimW).coerceAtLeast(1f))
    onDrawBehind {
        if (w > h) {
            drawLine(litColor, Offset(r, half), Offset(w - r, half), strokeWidth = rimW)
            drawLine(shadeColor, Offset(r, h - half), Offset(w - r, h - half), strokeWidth = rimW)
        }
        drawArc(
            brush = leftBrush,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(half, half),
            size = arcSize,
            style = Stroke(width = rimW)
        )
        drawArc(
            brush = rightBrush,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w - h + half, half),
            size = arcSize,
            style = Stroke(width = rimW)
        )
    }
}

/** Bentuk kunci: chamfer (tombol/tab, identitas Old Money), pil (preset), bundar (tombol ikon). */
private enum class DepthKeyShape { Chamfer, Pill, Circle }

/** Keadaan diam kunci: [Raised] = timbul; [Flat] = tak terlihat sampai aktif/ditekan (tab tak terpilih). */
private enum class DepthRest { Raised, Flat }

/** Kunci fisik serbaguna (tombol, pil, tab, tombol ikon). Diam = timbul (bayangan jatuh + permukaan
 *  + rim menurut bentuk); ditekan = cekung sesaat (lantai gelap + bayangan dalam + bibir terang).
 *  [selected] != null = kunci terpilih-tunggal (semantik selectable); [selectedRecessed] true =
 *  terpilih tampak cekung (pil preset: "tertekan"), false = terpilih tampak timbul (tab: "terangkat").
 *  [enamel] != null = isian berwarna bergradasi (tombol utama, pil terpilih). Area sentuh diperluas
 *  oleh [touchPad] DI LUAR visual. Dipakai HANYA saat `depth != null` (pemanggil menjaga cabang lama). */
@Composable
private fun DepthKeyBox(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    selected: Boolean?,
    role: Role,
    kind: DepthKeyShape,
    enamel: Color?,
    selectedRecessed: Boolean,
    rest: DepthRest,
    contentColor: Color,
    minWidth: Dp,
    minHeight: Dp,
    contentPadding: PaddingValues,
    touchPad: PaddingValues,
    spacing: Dp,
    content: @Composable RowScope.() -> Unit
) {
    val depth = LocalSkeuTokens.current.depth ?: return
    val shape: Shape = when (kind) {
        DepthKeyShape.Chamfer -> CutCornerShape(6.dp)
        DepthKeyShape.Pill -> RoundedCornerShape(50)
        DepthKeyShape.Circle -> CircleShape
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = selected == true
    val recessed = pressed || (active && selectedRecessed)
    val flat = rest == DepthRest.Flat && !active && !pressed
    val faceBrush: Brush? = when {
        flat -> null
        recessed && enamel != null -> {
            val e = lerp(depth.wellFloor, enamel, 0.72f)
            Brush.verticalGradient(listOf(lerp(e, depth.rimLight, 0.14f), e, lerp(e, Color.Black, 0.22f)))
        }
        recessed -> SolidColor(depth.wellFloor)
        enamel != null ->
            Brush.verticalGradient(
                listOf(lerp(enamel, depth.rimLight, 0.18f), enamel, lerp(enamel, Color.Black, 0.25f))
            )
        else -> Brush.verticalGradient(listOf(depth.faceTop, depth.faceBottom))
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .then(
                if (selected != null) {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = role,
                        onClick = onClick
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = role,
                        onClick = onClick
                    )
                }
            )
            .padding(touchPad),
        propagateMinConstraints = true
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
                .then(
                    when {
                        flat -> Modifier
                        recessed -> Modifier.depthWellLip(shape, depth)
                        else -> Modifier.depthCastShadow(shape, depth, DepthKeyShadow)
                    }
                )
                .clip(shape)
                .then(if (faceBrush != null) Modifier.background(faceBrush) else Modifier)
                .then(
                    when {
                        flat -> Modifier
                        recessed -> Modifier.depthWellInner(shape, depth)
                        kind == DepthKeyShape.Chamfer -> Modifier.depthPlateSurface(depth, 6.dp, DepthPartsKey, null, 1.2.dp)
                        kind == DepthKeyShape.Pill -> Modifier.depthPillRim(depth, 1.2.dp)
                        else -> Modifier.depthRingRim(depth, 1.4.dp)
                    }
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
            }
        }
    }
}

/** Tombol utama (isian enamel burgundy). `depth == null` → `Button` Material lama persis. */
@Composable
internal fun SkeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalSkeuTokens.current.depth == null) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    DepthKeyBox(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = null,
        role = Role.Button,
        kind = DepthKeyShape.Chamfer,
        enamel = MaterialTheme.colorScheme.primary,
        selectedRecessed = false,
        rest = DepthRest.Raised,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        minWidth = 58.dp,
        minHeight = 40.dp,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        touchPad = PaddingValues(vertical = 4.dp),
        spacing = 0.dp,
        content = content
    )
}

/** Tombol sekunder (kunci timbul, label burgundy diterangkan agar terbaca di permukaan pelat). */
@Composable
internal fun SkeuOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val depth = LocalSkeuTokens.current.depth
    if (depth == null) {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    DepthKeyBox(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = null,
        role = Role.Button,
        kind = DepthKeyShape.Chamfer,
        enamel = null,
        selectedRecessed = false,
        rest = DepthRest.Raised,
        contentColor = lerp(MaterialTheme.colorScheme.primary, depth.rimLight, 0.30f),
        minWidth = 58.dp,
        minHeight = 40.dp,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        touchPad = PaddingValues(vertical = 4.dp),
        spacing = 0.dp,
        content = content
    )
}

/** Tombol teks (kunci timbul kecil). */
@Composable
internal fun SkeuTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val depth = LocalSkeuTokens.current.depth
    if (depth == null) {
        TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    DepthKeyBox(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = null,
        role = Role.Button,
        kind = DepthKeyShape.Chamfer,
        enamel = null,
        selectedRecessed = false,
        rest = DepthRest.Raised,
        contentColor = lerp(MaterialTheme.colorScheme.primary, depth.rimLight, 0.30f),
        minWidth = 48.dp,
        minHeight = 40.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        touchPad = PaddingValues(vertical = 4.dp),
        spacing = 0.dp,
        content = content
    )
}

/** Tombol ikon (kunci bundar timbul 40dp, area sentuh 48dp). */
@Composable
internal fun SkeuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalSkeuTokens.current.depth == null) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    DepthKeyBox(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = null,
        role = Role.Button,
        kind = DepthKeyShape.Circle,
        enamel = null,
        selectedRecessed = false,
        rest = DepthRest.Raised,
        contentColor = LocalContentColor.current,
        minWidth = 40.dp,
        minHeight = 40.dp,
        contentPadding = PaddingValues(8.dp),
        touchPad = PaddingValues(4.dp),
        spacing = 0.dp,
        content = { content() }
    )
}

/** Pil "Preset Cepat". `depth == null` (5 tema lain) → memanggil [legacy] (FilterChip/AssistChip
 *  apa adanya dari pemanggil, 0 perubahan). `depth != null` → pil fisik: [selected] = cekung enamel
 *  burgundy; ditekan = cekung sesaat; diam = timbul. */
@Composable
internal fun SkeuPresetPill(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    labelColor: Color? = null,
    legacy: @Composable () -> Unit
) {
    val tokens = LocalSkeuTokens.current
    if (tokens.depth == null) {
        legacy()
        return
    }
    val textColor = if (selected) Color.White else (labelColor ?: tokens.mutedText)
    DepthKeyBox(
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        selected = selected,
        role = Role.RadioButton,
        kind = DepthKeyShape.Pill,
        enamel = if (selected) MaterialTheme.colorScheme.primary else null,
        selectedRecessed = true,
        rest = DepthRest.Raised,
        contentColor = textColor,
        minWidth = 0.dp,
        minHeight = 32.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        touchPad = PaddingValues(0.dp),
        spacing = 6.dp
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
        if (trailingIcon != null) trailingIcon()
    }
}

/** Bilah tab fisik: palung CEKUNG berisi kunci-kunci; tab terpilih = kunci TIMBUL (terangkat) dgn
 *  label burgundy tebal, tab lain rata (tak terlihat) dgn label redup. `depth == null` → [legacy]
 *  (ScrollableTabRow lama apa adanya). */
@Composable
internal fun SkeuTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    legacy: @Composable () -> Unit
) {
    val tokens = LocalSkeuTokens.current
    val depth = tokens.depth
    if (depth == null) {
        legacy()
        return
    }
    val trough = CutCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .depthWellLip(trough, depth)
            .clip(trough)
            .background(depth.wellFloor)
    ) {
        Box(Modifier.matchParentSize().depthWellInner(trough, depth))
        Row(modifier = Modifier.fillMaxWidth().padding(3.dp)) {
            labels.forEachIndexed { index, label ->
                val sel = index == selectedIndex
                DepthKeyBox(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    selected = sel,
                    role = Role.Tab,
                    kind = DepthKeyShape.Chamfer,
                    enamel = null,
                    selectedRecessed = false,
                    rest = DepthRest.Flat,
                    contentColor = if (sel) MaterialTheme.colorScheme.primary else tokens.mutedText,
                    minWidth = 0.dp,
                    minHeight = 42.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    touchPad = PaddingValues(0.dp),
                    spacing = 0.dp
                ) {
                    Text(
                        text = label,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Dialog fisik: `depth == null` → `AlertDialog` Material lama persis; `depth != null` → pelat
 *  chamfer dgn bayangan jatuh di belakang + bevel/grain/alur ukir di atas permukaan. */
@Composable
internal fun SkeuAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    val depth = LocalSkeuTokens.current.depth
    if (depth == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            title = title,
            text = text
        )
        return
    }
    val dialogShape = CutCornerShape(8.dp)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = Modifier
            .depthCastShadow(dialogShape, depth, DepthPlateShadow, slice = true)
            .depthPlateSurface(depth, 8.dp, DepthPartsDialog),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = dialogShape,
        containerColor = lerp(depth.faceTop, depth.faceBottom, 0.5f)
    )
}

/** Warna kolom isian: `depth != null` → wadah = lantai sumur gelap (cekung secara tonal);
 *  selain itu = default Material persis. */
@Composable
internal fun skeuFieldColors(): TextFieldColors {
    val depth = LocalSkeuTokens.current.depth
    return if (depth == null) {
        OutlinedTextFieldDefaults.colors()
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = depth.wellFloor,
            unfocusedContainerColor = depth.wellFloor
        )
    }
}

/** Soket cekung (kotak ikon Old Money): lantai gelap + bayangan dalam + bibir luar; [content]
 *  duduk DI ATAS lantai & bayangan. */
@Composable
private fun DepthSocket(
    modifier: Modifier,
    shape: Shape,
    style: DepthStyle,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.depthWellLip(shape, style)) {
        Box(Modifier.matchParentSize().clip(shape).background(style.wellFloor))
        Box(Modifier.matchParentSize().depthWellInner(shape, style))
        Column(modifier = modifier, content = content)
    }
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
    val tokens = LocalSkeuTokens.current
    // Batch 112: shape SEKARANG `tokens.cardShape` (bukan `RoundedCornerShape(radius)`
    // bikin sendiri) — root cause komplain user "kartu Serene M3 masih rounded biasa"
    // adalah baris ini SEBELUMNYA 0 pernah baca shape ASLI varian (cuma radius Dp),
    // jadi cut-corner Serene M3 gak pernah ke-render di kartu manapun. 4 varian lama
    // (`cardShape` = `RoundedCornerShape(cardRadius-nya)`) 0 perubahan visual — CUMA
    // kalau caller override `radius` custom (bukan default), shape tetap fallback ke
    // `RoundedCornerShape(radius)` biar override itu tidak diam-diam diabaikan.
    val shape = if (radius == tokens.cardRadius) tokens.cardShape else RoundedCornerShape(radius)
    // Batch 180: mesin kedalaman fisik (Old Money). Kartu normal = pelat TIMBUL (bayangan jatuh
    // Gaussian + permukaan lebih terang dari latar + bevel facet + grain); kotak ikon (radius !=
    // cardRadius) = soket CEKUNG. 5 varian lain `depth == null` → lanjut ke kode lama di bawah.
    val depth = tokens.depth
    if (depth != null) {
        if (radius != tokens.cardRadius) {
            DepthSocket(modifier, shape, depth, content)
        } else {
            Box(modifier = Modifier.depthCastShadow(shape, depth, DepthPlateShadow, slice = true)) {
                Column(
                    modifier = modifier
                        .clip(shape)
                        .depthPlateSurface(depth, tokens.cardRadius),
                    content = content
                )
            }
        }
        return
    }
    // Batch 36: fill/border/elevation sekarang datang dari `LocalSkeuTokens.current`
    // (Theme.kt) — AMOLED Glass tetap frosted-glass tint (persis sebelumnya), Radical
    // Literal Skeuomorphism jadi raised-bevel surface (guide §5 "Raised object").
    // 1 kode komponen, 5 tema, TANPA duplikasi/percabangan when() di sini.
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
    // Batch 112: shape sekarang `tokens.cardShape` (bukan `RoundedCornerShape`
    // bikin sendiri) — sama fix root-cause seperti `SkeuCard` di atas, 0 param
    // override radius di fungsi ini jadi langsung pakai token, tanpa fallback.
    val shape = tokens.cardShape
    val blended = lerp(tokens.baseSurface, tint, 0.22f)
    // Batch 180: pelat timbul bertint (banner status) — permukaan = warna pelat Old Money yang
    // digeser ke tint, sorot facet digeser ke warna status; bukan lagi gradien lebih gelap dari
    // kartu biasa.
    val depth = tokens.depth
    if (depth != null) {
        Box(modifier = Modifier.depthCastShadow(shape, depth, DepthPlateShadow, slice = true)) {
            Column(
                modifier = modifier
                    .clip(shape)
                    .depthPlateSurface(depth, tokens.cardRadius, DepthPartsPlate, tint),
                content = content
            )
        }
        return
    }
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
    // Batch 180: depth != null (Old Money) = timbul saat diam, CEKUNG saat ON/ditekan (sumur).
    val depth = tokens.depth
    val isDown = pressed || isPressedNow

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
        if (depth == null) {
            SkeuDualDirectionalShadow(tokens, shape, depth = 10.dp, invert = pressed || isPressedNow, steps = 5)
        } else if (!isDown) {
            Box(
                Modifier
                    .matchParentSize()
                    .depthCastShadow(shape, depth, DepthButtonShadow)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (pressed) Modifier.skeuGlow(tokens.primaryGlow, spread = 14.dp) else Modifier)
                .shadow(elevation = if (depth == null) elevation else 0.dp, shape = shape, clip = false)
                .clip(shape)
                .background(if (depth != null && isDown) SolidColor(depth.wellFloor) else tokens.bevelBrush)
                .background(tokens.specularBrush)
                .then(
                    if (depth == null) {
                        Modifier.border(1.5.dp, tokens.bevelBorderBrush, shape)
                    } else if (!isDown) {
                        Modifier.depthRingRim(depth, 1.6.dp)
                    } else {
                        Modifier
                    }
                )
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
        if (depth != null && isDown) {
            Box(Modifier.matchParentSize().depthWellInner(shape, depth))
        }
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
    // Batch 180: depth != null = alur CEKUNG fisik (lantai gelap + bibir luar + bayangan dalam di
    // ATAS bagian terisi, jadi isian pun terlihat tenggelam di alur).
    val depth = tokens.depth
    if (depth != null) {
        val floor = lerp(depth.wellFloor, inactiveColor.copy(alpha = 1f), if (enabled) 0.14f else 0.05f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .depthWellLip(shape, depth)
                .clip(shape)
                .background(floor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(shape)
                    // B182: isian = enamel bulat (atas lebih terang, bawah lebih gelap), bukan warna datar.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                lerp(trackColor, depth.rimLight, 0.18f),
                                trackColor,
                                lerp(trackColor, Color.Black, 0.25f)
                            )
                        )
                    )
            )
            Box(Modifier.matchParentSize().depthWellInner(shape, depth))
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(bgColor)
    ) {
        SkeuDualDirectionalShadow(tokens, shape, depth = 4.5.dp, invert = true, steps = 4)
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
    // Batch 180: knob fisik Old Money — bayangan jatuh + kubah sorot champagne (tanpa emas) +
    // cincin tepi terang/gelap menurut arah cahaya; cincin aksen tipis tetap (a11y aktif/nonaktif).
    val depth = tokens.depth
    if (depth != null) {
        val lo = lerp(depth.knobShade, accentColor, if (enabled) 0.25f else 0.06f)
        Box(
            modifier = Modifier
                .size(SkeuSliderThumbSize)
                .depthCastShadow(shape, depth, DepthKnobShadow)
                .clip(shape)
                .depthKnobFace(depth, tokens.sliderKnobHighlight, lo)
                // Cincin aksen DI DALAM cincin tepi bevel (padding = lebar rim) supaya sorot/bayangan
                // tepi tidak tertutup cincin aksen.
                .padding(1.4.dp)
                .border(1.2.dp, accentColor.copy(alpha = ringAlpha), shape)
        )
        return
    }
    val dialBrush = Brush.radialGradient(
        colors = listOf(
            tokens.sliderKnobHighlight,
            lerp(tokens.elevatedSurface, accentColor, if (enabled) 0.30f else 0.08f)
        )
    )
    Box(
        modifier = Modifier
            .size(SkeuSliderThumbSize)
            .shadow(elevation = if (enabled) 4.dp else 0.dp, shape = shape, clip = false)
            .clip(shape)
            .background(dialBrush)
            .border(2.dp, accentColor.copy(alpha = ringAlpha), shape)
    )
}

/** Batch 172: lebar thumb (dipakai `SkeuSliderThumb` DAN hitung posisi thumb di gate sentuh) dan
 *  radius horizontal "pegang thumb" dari titik tengah thumb. */
private val SkeuSliderThumbSize = 22.dp
private val SliderGrabRadius = 32.dp

/** Batch 172-174 (request user: slider sentuh-jauh-dari-thumb; B174: "di-tap bukan di-drag malah
 *  ngikut = regresi"). Slider HANYA berubah lewat DRAG yang dimulai dekat thumb
 *  ([SliderGrabRadius], horizontal); TAP (di mana pun) TIDAK pernah mengubah nilai. Semua sentuhan
 *  diproses di sini: `pointerInput` pass `Initial` (jalan SEBELUM handler bawaan `Slider`)
 *  mengonsumsi event down → onPress/onTap Slider M3 (sumber lompatan/`pressOffset` yang membuat
 *  thumb "ngikut" titik tap atau meloncat di awal drag) tak pernah jalan. Setelah gerak melewati
 *  `touchSlop`: dominan horizontal + mulai dekat thumb = drag RELATIF (nilai = nilai saat down +
 *  geseran jari, lewat `snapToStep` B169, tanpa lompatan); dominan horizontal tapi mulai jauh =
 *  diblok (event dikonsumsi); dominan vertikal = scroll → TIDAK dikonsumsi, halaman tetap scroll.
 *  Akhir drag memanggil [onDragEnd] (haptic). Beda dari Batch 138 (di-revert B140): key `Unit`
 *  TANPA `value` (tak ada restart paksa), nilai terkini lewat [State] (`rememberUpdatedState`),
 *  dedupe emisi pakai variabel lokal gesture (bukan `value` yang bisa basi antar-rekomposisi). */
private fun Modifier.thumbOnlyDrag(
    enabled: State<Boolean>,
    value: State<Float>,
    range: State<ClosedFloatingPointRange<Float>>,
    step: State<Float>,
    isRtl: State<Boolean>,
    onDrag: State<(Float) -> Unit>,
    onDragEnd: State<() -> Unit>
): Modifier = pointerInput(Unit) {
    val thumbWidthPx = SkeuSliderThumbSize.toPx()
    val grabRadiusPx = SliderGrabRadius.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val r = range.value
        val span = r.endInclusive - r.start
        val trackWidthPx = size.width - thumbWidthPx
        if (!enabled.value || span <= 0f || trackWidthPx <= 0f) return@awaitEachGesture
        val sign = if (isRtl.value) -1f else 1f
        val startValue = value.value
        val startFraction = ((startValue - r.start) / span).coerceIn(0f, 1f)
        val thumbX = thumbWidthPx / 2f + startFraction * trackWidthPx
        val thumbCenterX = if (isRtl.value) size.width - thumbX else thumbX
        val nearThumb = abs(down.position.x - thumbCenterX) <= grabRadiusPx
        down.consume()
        var lastEmitted = startValue
        var decided = false
        var horizontal = false
        var dragging = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial)
                .changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                if (dragging) onDragEnd.value.invoke()
                return@awaitEachGesture
            }
            val delta = change.position - down.position
            if (!decided && delta.getDistance() >= viewConfiguration.touchSlop) {
                decided = true
                horizontal = abs(delta.x) > abs(delta.y)
                dragging = horizontal && nearThumb
            }
            if (horizontal) change.consume()
            if (dragging) {
                val fraction = (startFraction + sign * delta.x / trackWidthPx).coerceIn(0f, 1f)
                val snapped = snapToStep(r.start + fraction * span, step.value, r)
                if (snapped != lastEmitted) {
                    lastEmitted = snapped
                    onDrag.value.invoke(snapped)
                }
            }
        }
    }
}

/** Batch 169 (request user: slider terlalu licin/susah presisi): bulatkan [raw] ke kelipatan
 *  [step] terdekat (dihitung dari 0, bukan dari awal range → 0 mB EQ tetap kena), dibatasi ke
 *  [range]. `step <= 0` = tanpa snap (perilaku lama). SENGAJA dilakukan di callback, BUKAN
 *  `Slider(steps=)`: nilai preset/custom yang tak pas kelipatan tetap tampil apa adanya (label
 *  = posisi thumb) dan tak ada `pointerInput` baru (lihat gatekeeper Batch 138→140). */
private fun snapToStep(raw: Float, step: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (step <= 0f) return raw
    return ((raw / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
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
    step: Float = 0f,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accentColor2: Color = accentColor,
    wrapInCard: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    // Batch 172: nilai terkini untuk gate sentuh (dibaca di dalam pointerInput(Unit), tanpa restart).
    val latestEnabled = rememberUpdatedState(enabled)
    val latestValue = rememberUpdatedState(value)
    val latestRange = rememberUpdatedState(valueRange)
    val isRtl = rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    // Batch 174: drag dari thumb (relatif, tanpa lompatan) diproses `thumbOnlyDrag`; snap B169 di
    // dalamnya, jadi handler cukup meneruskan nilai & memberi haptic di akhir drag.
    val latestStep = rememberUpdatedState(step)
    val latestOnDrag = rememberUpdatedState(onValueChange)
    val dragEndHandler: () -> Unit = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val latestOnDragEnd = rememberUpdatedState(dragEndHandler)
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
            onValueChange = { raw ->
                val snapped = snapToStep(raw, step, valueRange)
                // Dengan snap, lewati callback kalau tetap di kelipatan yang sama (jari bergerak
                // di dalam 1 notch) → tak ada tulis/apply efek berulang untuk nilai identik.
                if (step <= 0f || snapped != value) onValueChange(snapped)
            },
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
                .thumbOnlyDrag(latestEnabled, latestValue, latestRange, latestStep, isRtl, latestOnDrag, latestOnDragEnd)
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

/** Batch 88 (user eksplisit minta "rombak total iOS look" — dikerjakan bertahap per
 *  fase, metode HYBRID: pola struktur iOS ditambahkan TANPA ubah warna/bevel/shadow
 *  tiap tema, "ciri khas utama" masing-masing varian TETAP dari `LocalSkeuTokens`
 *  seperti sebelumnya, 0 token baru ditambah ke `SkeuTokens` — kalau nambah field baru
 *  di sana WAJIB diisi ulang ke SEMUA 4 varian, Theme.kt, risiko lupa 1 varian).
 *  FASE 1: garis pemisah tipis ala grouped-list iOS (Settings.app) — dipakai BoosterScreen
 *  buat gabung beberapa `FeatureControl(wrapInCard = false)` ke DALAM 1 `SkeuCard`
 *  (pola `wrapInCard=false` ITU SENDIRI bukan baru — sudah dipakai `EqualizerSection`
 *  sejak lama buat multi-band, di sini dipakai pertama kali buat baris Bass/Virtualizer/
 *  Loudness). Warna pakai `tokens.mutedText` yang SUDAH ada di ke-4 varian (bukan warna
 *  baru) alpha rendah — sengaja TETAP "quiet" (prinsip restraint Batch 34), supaya
 *  garisnya cuma penanda struktur, BUKAN elemen dekoratif baru yang bisa geser
 *  identitas visual tiap tema. `startIndent` default nge-align ke bawah teks judul row
 *  (lewati lebar icon-box 40dp + spacing Row 10dp = 50dp) — inset divider ala iOS asli,
 *  BUKAN garis full-width gaya list Android Material biasa. */
@Composable
internal fun SkeuGroupDivider(startIndent: Dp = 50.dp) {
    // Batch 180: Old Money = alur UKIR (garis gelap + garis sorot gading 1px tepat di bawahnya),
    // bukan garis alpha tipis datar.
    val depth = LocalSkeuTokens.current.depth
    if (depth != null) {
        Column(modifier = Modifier.padding(start = startIndent, top = 14.dp, bottom = 14.dp)) {
            HorizontalDivider(thickness = 1.dp, color = depth.rimShade.copy(alpha = 0.55f))
            HorizontalDivider(thickness = 1.dp, color = depth.rimLight.copy(alpha = 0.07f))
        }
        return
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, top = 14.dp, bottom = 14.dp),
        thickness = 0.6.dp,
        color = LocalSkeuTokens.current.mutedText.copy(alpha = 0.16f)
    )
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
    // Batch 180: depth != null = groove CEKUNG (lantai gelap, bukan surfaceVariant yang lebih
    // terang dari pelat); depth == null = nilai lama (`surfaceVariant`) persis.
    val depth = tokens.depth
    val offTrack = depth?.wellFloor ?: MaterialTheme.colorScheme.surfaceVariant

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> offTrack.copy(alpha = 0.4f)
            checked -> lerp(offTrack, accentColor, 0.55f)
            else -> offTrack
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
            .then(if (depth != null) Modifier.depthWellLip(trackShape, depth) else Modifier)
            .clip(trackShape)
            .background(trackColor)
            .then(
                if (depth == null) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (checked) 0.6f else 0.35f), trackShape)
                } else {
                    Modifier
                }
            )
            .then(if (checked && enabled) Modifier.skeuGlow(accentColor.copy(alpha = 0.3f), spread = 6.dp) else Modifier)
            .then(if (depth != null) Modifier.depthWellInner(trackShape, depth) else Modifier)
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
        if (depth == null) {
            SkeuDualDirectionalShadow(tokens, trackShape, depth = 3.5.dp, invert = true, steps = 4)
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .scale(thumbScale)
                .then(
                    if (depth != null) {
                        Modifier.depthCastShadow(CircleShape, depth, DepthKnobShadow)
                    } else {
                        Modifier.shadow(elevation = thumbElevation, shape = CircleShape, clip = false)
                    }
                )
                .clip(CircleShape)
                .then(
                    if (depth != null) {
                        val hi = if (checked) lerp(accentColor, depth.rimLight, 0.45f) else depth.rimLight
                        val lo = if (checked) lerp(accentColor, Color.Black, 0.30f) else depth.knobShade
                        Modifier.depthKnobFace(depth, hi, lo)
                    } else {
                        Modifier.background(if (checked) accentColor else lerp(tokens.elevatedSurface, Color.White, 0.45f))
                    }
                )
                .alpha(if (enabled) 1f else 0.5f)
        )
    }
}
