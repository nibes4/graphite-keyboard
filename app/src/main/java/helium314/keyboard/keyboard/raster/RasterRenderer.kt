// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.raster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PixelFormat
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import helium314.keyboard.keyboard.Key
import helium314.keyboard.latin.utils.needsDarkGlyphs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a keyboard with a [RasterTheme] the same way the theme scripts compose their previews:
 *  base   = plate + cast shadows + caps (Enter excluded), multiplied by the lighting map
 *  labels = drawn over, lit at label_lighting strength
 *  enter  = Enter cap + its glyph, lit at that key's lighting strength
 *  then plate grain everywhere and plastic grain on the caps.
 * Everything is kept in layers, so a pressed key only re-composes the rectangle around it.
 */
class RasterRenderer(private val theme: RasterTheme) {
    fun interface LabelPainter {
        /** Draw the key's label or icon on [canvas], already translated to the key; [capRect] is relative to the key. */
        fun draw(key: Key, canvas: Canvas, capRect: RectF, skin: RasterTheme.Skin, pressed: Boolean)
    }

    /** User setting: 0 = flat light, 1 = the theme's light, up to 1.5 = stronger. */
    var lightStrength = 1f
        set(value) { if (field != value) { field = value; lightDirty = true } }

    /** User setting: the Enter cap is repainted in this colour; the theme's own colour leaves the skin untouched. */
    var primaryColor = DEFAULT_PRIMARY
        set(value) {
            if (field == value) return
            field = value; tinted.clear(); nineCache.clear()
            enterSkin = theme.skins["enter"]?.let { s ->
                if (value == DEFAULT_PRIMARY || !needsDarkGlyphs(value)) s
                else RasterTheme.Skin(s.normal, s.pressed, s.shadow, s.pressedShadow, s.inset, s.shadowInset, DARK_GLYPH, s.lighting)
            }
        }
    private var enterSkin: RasterTheme.Skin? = theme.skins["enter"]
    private val tinted = HashMap<Bitmap, Bitmap>()
    private val enterLuma: Float by lazy { theme.skins["enter"]?.let { meanLuma(it.normal) } ?: 128f }

    private var w = 0
    private var h = 0
    private var f = 1f          // device px per theme image px
    private var ref = 1f        // device px per reference px (1080 wide board)
    private lateinit var base: Bitmap
    private lateinit var enter: Bitmap
    private lateinit var labels: Bitmap
    private lateinit var mask: Bitmap
    lateinit var out: Bitmap
        private set
    private val baseCanvas = Canvas()
    private val enterCanvas = Canvas()
    private val labelCanvas = Canvas()
    private val maskCanvas = Canvas()
    private lateinit var lightMap: FloatArray   // lighting map x gain, before the strength setting
    private lateinit var light: FloatArray
    private lateinit var noise: FloatArray
    private lateinit var capNoise: FloatArray
    private lateinit var plate: Bitmap
    private var lightDirty = true
    private var lightMean = 1f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint()
    private val scaledSkins = HashMap<Bitmap, Bitmap>()
    private val nineCache = HashMap<String, Bitmap>()
    private val shadowDrops = HashMap<String, Int>() // px, see shadowDrop
    private val iconCache = HashMap<String, Bitmap?>()
    private val r = Rect()
    private val capRect = RectF()

    // Tilt light: the beam follows the phone's tilt and the cast shadows move the other way. The composed board
    // stays as it is: the light shift is a low-res ratio map and the shadows are their own layer, both multiplied
    // over it on the GPU, so tilting never recomposes the board.
    /** Keep the cast shadows in their own layer ([drawShadows]) so the tilt can move them. Set before [setSize]. */
    var movableShadows = false
    private var tiltX = 0f              // light shift, device px
    private var tiltY = 0f
    private var tiltActive = false
    private var shadowDx = 0f            // shadow shift, device px
    private var shadowDy = 0f
    private var shade: Bitmap? = null   // white, darkened by the cast shadows (movable shadows only)
    private val shadeCanvas = Canvas()
    private val shadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
    private val capWhite = Paint().apply { color = Color.WHITE }
    private var grid = FloatArray(0)    // light over the panel at low res, after the strength setting
    private var gw = 0
    private var gh = 0
    private var gridStrength = -1f
    private var gridMean = -1f
    private var reach = FloatArray(REACH_DIRS) // how far the light may travel in each direction, see [measureReach]
    private var ratioBuf = IntArray(0)
    private var ratio: Bitmap? = null
    private val ratioPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
    private val layerPaint = Paint()
    private val ratioDst = RectF()
    private val ratioSrc = Rect()

    private var keysTop = 0f
    private var keysBottom = 0f
    private var panelTop = 0     // view y of the panel's top edge (negative: the panel starts above the view)
    private var panelH = 0       // panel height in view px

    /**
     * Size the layers; returns true if they were (re)allocated and everything must be drawn.
     * [rowsTop]/[rowsBottom] are the view y where the key rows start and end: the plate and the light are
     * placed so the theme's own key rows land exactly there, the way they were painted.
     */
    fun setSize(width: Int, height: Int, rowsTop: Float, rowsBottom: Float): Boolean {
        if (width == w && height == h && rowsTop == keysTop && rowsBottom == keysBottom) return false
        w = width; h = height; keysTop = rowsTop; keysBottom = rowsBottom
        ref = w / 1080f
        f = ref / theme.scale
        if (theme.panelHeight > 0) {
            val sy = (rowsBottom - rowsTop) / (theme.panelKeysBottom - theme.panelKeysTop)
            panelTop = (rowsTop - theme.panelKeysTop * sy).roundToInt()
            panelH = max(1, (theme.panelHeight * sy).roundToInt())
        } else {
            panelTop = 0; panelH = h
        }
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        enter = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        labels = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        baseCanvas.setBitmap(base); enterCanvas.setBitmap(enter); labelCanvas.setBitmap(labels); maskCanvas.setBitmap(mask)
        shade = if (movableShadows) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { shadeCanvas.setBitmap(it) } else null
        plate = panelSlice(theme.background, 0, h)
        scaledSkins.clear(); nineCache.clear(); shadowDrops.clear(); iconCache.clear(); tinted.clear()

        val lm = IntArray(w * h)
        panelSlice(theme.lighting, 0, h).getPixels(lm, 0, w, 0, 0, w, h)
        lightMap = FloatArray(w * h) { Color.red(lm[it]) / 255f * theme.lightingGain }
        light = FloatArray(w * h)
        lightDirty = true
        noise = tiled(theme.grain, theme.grainGain, theme.grainSize, h)
        capNoise = theme.capGrain?.let { tiled(it, theme.capGrainStrength, theme.capGrainSize, h) } ?: FloatArray(w * h)
        // the compose mask keeps plate grain off the caps, so these stay on the base between and around the keys
        addScratches(noise, h, { lightMap[it] }, 0x6A2F1L)
        shadowPaint.alpha = (theme.shadowOpacity.coerceIn(0f, 1f) * 255).roundToInt()
        tiltActive = false; tiltX = 0f; tiltY = 0f; shadowDx = 0f; shadowDy = 0f
        return true
    }

    /**
     * View rows [top]..[bottom] of [img] stretched over the panel. Rows outside the panel mirror the rows inside it:
     * repeating the edge row turned every speck of the plate into a vertical streak under the bottom row.
     */
    private fun panelSlice(img: Bitmap, top: Int, bottom: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(img, w, panelH, true)
        val slice = Bitmap.createBitmap(w, max(1, bottom - top), Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        for (y in top until bottom) {
            val py = y - panelTop
            val src = when {
                py < 0 -> -py - 1
                py >= panelH -> 2 * panelH - 1 - py
                else -> py
            }.coerceIn(0, panelH - 1)
            scaled.getPixels(row, 0, w, 0, src, w, 1)
            slice.setPixels(row, 0, w, 0, y - top, w, 1)
        }
        return slice
    }

    /**
     * Grain tile at device scale, repeated. [size] is one tile pixel in reference px; 0 keeps the theme's scale,
     * where the downscale averages the grain to about one grey level and the plastic reads as smooth.
     */
    private fun tiled(tile: Bitmap, gain: Float, size: Float, rows: Int): FloatArray {
        val t = if (size > 0f) RasterTheme.scaled(tile, size * ref) else RasterTheme.scaled(tile, f)
        val tw = t.width; val th = t.height
        val px = IntArray(tw * th); t.getPixels(px, 0, tw, 0, 0, tw, th)
        val out = FloatArray(w * rows)
        for (y in 0 until rows) {
            val row = (y % th) * tw
            for (x in 0 until w) out[y * w + x] = (Color.red(px[row + x % tw]) - 128) * gain
        }
        return out
    }

    /**
     * Hairline scratches in the base plastic, added to a [rows] tall grain array so the compose loop stays as it is.
     * Short straight strokes at random angles, a few longer ones, tapered at both ends; most catch the light
     * (brighter where [lightAt] is high), a few read as dark scuffs. Seeded, so every resize draws the same wear.
     */
    private fun addScratches(into: FloatArray, rows: Int, lightAt: (Int) -> Float, seed: Long) {
        val strength = theme.scratchStrength
        if (strength <= 0f) return
        val h = rows
        var mean = 0f
        for (i in 0 until w * h) mean += lightAt(i)
        mean = max(1e-3f, mean / (w * h))
        val rnd = java.util.Random(seed)
        val count = (w / (100f * ref) * (h / (100f * ref)) * theme.scratchDensity).roundToInt()
        repeat(count) {
            val len = ref * if (rnd.nextFloat() < 0.15f) 28f + 46f * rnd.nextFloat() else 5f + 17f * rnd.nextFloat()
            val angle = rnd.nextFloat() * Math.PI.toFloat()
            val dx = kotlin.math.cos(angle); val dy = kotlin.math.sin(angle)
            val x0 = rnd.nextFloat() * w; val y0 = rnd.nextFloat() * h
            val amp = strength * (0.35f + 0.65f * rnd.nextFloat()) * if (rnd.nextFloat() < 0.2f) -0.6f else 1f
            val steps = max(2, (len * 2).roundToInt())
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                val x = x0 + dx * len * t; val y = y0 + dy * len * t
                val ix = x.toInt(); val iy = y.toInt()
                if (ix < 0 || iy < 0 || ix >= w - 1 || iy >= h - 1) continue
                // half-pixel steps, split over the two pixels across the stroke so it stays a soft hairline
                val v = amp * kotlin.math.sin(Math.PI.toFloat() * t) * 0.5f
                val i = iy * w + ix
                val lit = v * lightAt(i) / mean
                if (abs(dx) > abs(dy)) {
                    val fy = y - iy
                    into[i] += lit * (1 - fy); into[i + w] += lit * fy
                } else {
                    val fx = x - ix
                    into[i] += lit * (1 - fx); into[i + 1] += lit * fx
                }
            }
        }
    }

    private fun updateLight() {
        // strength scales the contrast around the mean brightness, so 0 keeps the board as bright on average
        var mean = 0f
        for (v in lightMap) mean += v
        mean /= lightMap.size
        val s = lightStrength
        for (i in lightMap.indices) light[i] = max(0f, mean + s * (lightMap[i] - mean))
        lightMean = mean
        lightDirty = false
    }

    /**
     * The part of the panel above this view, [height] px tall: the suggestion strip sits there, so it gets
     * the same plate, light and grain, and the board reads as one piece the way the preview shows it.
     */
    fun stripImage(height: Int): Bitmap {
        if (lightDirty) updateLight()
        val hs = max(1, height)
        val p = IntArray(w * hs); val l = IntArray(w * hs)
        panelSlice(theme.background, -hs, 0).getPixels(p, 0, w, 0, 0, w, hs)
        panelSlice(theme.lighting, -hs, 0).getPixels(l, 0, w, 0, 0, w, hs)
        val s = lightStrength
        // own grain and scratches: reading the board's rows again would repeat its top scratches a strip higher
        val stripNoise = tiled(theme.grain, theme.grainGain, theme.grainSize, hs)
        addScratches(stripNoise, hs, { Color.red(l[it]) / 255f * theme.lightingGain }, 0x51A7EL)
        for (y in 0 until hs) for (x in 0 until w) {
            val i = y * w + x
            val lt = max(0f, lightMean + s * (Color.red(l[i]) / 255f * theme.lightingGain - lightMean))
            val nz = stripNoise[i] + 0.5f
            val b = p[i]
            p[i] = (0xff shl 24) or (clamp(((b shr 16) and 0xff) * lt + nz) shl 16) or
                    (clamp(((b shr 8) and 0xff) * lt + nz) shl 8) or clamp((b and 0xff) * lt + nz)
        }
        return Bitmap.createBitmap(p, w, hs, Bitmap.Config.ARGB_8888)
    }

    /** Suggestion strip background: [stripImage] with the tilt light on top, stretched to the strip. */
    fun stripDrawable(height: Int): Drawable = object : Drawable() {
        private val bmp = stripImage(height)
        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.scale(bounds.width().toFloat() / bmp.width, bounds.height().toFloat() / bmp.height)
            drawLit(canvas, bmp, -bmp.height)
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }

    /**
     * Tilt of the phone as fractions of the board width (light moves towards +x / +y), within ±[MAX_TILT].
     * Returns true if the light or the shadows moved and the view needs a redraw.
     */
    fun setTilt(tiltFx: Float, tiltFy: Float): Boolean {
        if (w == 0) return false
        ensureGrid()
        // Nick 2026-11-03: at the extremes most of the board went dark, so the light travels only as far as
        // the board stays mostly lit in that direction
        var fx = tiltFx; var fy = tiltFy
        val mag = hypot(fx, fy)
        if (mag > 0f) {
            val a = (atan2(fy, fx) / (2 * PI.toFloat()) * REACH_DIRS + REACH_DIRS) % REACH_DIRS
            val i = a.toInt() % REACH_DIRS
            val limit = reach[i] + (reach[(i + 1) % REACH_DIRS] - reach[i]) * (a - a.toInt())
            if (mag > limit) { fx *= limit / mag; fy *= limit / mag }
        }
        var result = false
        val tx = fx * w; val ty = fy * w
        val neutral = abs(tx) < 0.5f && abs(ty) < 0.5f
        if (neutral && tiltActive) {
            tiltActive = false; tiltX = 0f; tiltY = 0f; result = true
        } else if (!neutral && (!tiltActive || abs(tx - tiltX) >= 1.5f || abs(ty - tiltY) >= 1.5f)) {
            tiltX = tx; tiltY = ty
            updateRatio()
            tiltActive = true; result = true
        }
        val sdx = if (neutral) 0f else -fx * SHADOW_SHIFT * ref
        // Nick 2026-11-01: never a shadow above the keys, so the tilt moves them sideways and down only
        val sdy = if (neutral) 0f else max(0f, -fy * SHADOW_SHIFT * ref)
        if (shade != null && (abs(sdx - shadowDx) >= 0.5f || abs(sdy - shadowDy) >= 0.5f || (neutral && (shadowDx != 0f || shadowDy != 0f)))) {
            shadowDx = sdx; shadowDy = sdy
            result = true
        }
        return result
    }

    private fun ensureGrid() {
        if (lightDirty) updateLight()
        val s = lightStrength
        if (grid.isEmpty() || gridStrength != s || gridMean != lightMean) {
            gw = max(2, theme.lighting.width / 2); gh = max(2, theme.lighting.height / 2)
            val px = IntArray(gw * gh)
            Bitmap.createScaledBitmap(theme.lighting, gw, gh, true).getPixels(px, 0, gw, 0, 0, gw, gh)
            val mean = lightMean
            grid = FloatArray(gw * gh) { max(0f, mean + s * (Color.red(px[it]) / 255f * theme.lightingGain - mean)) }
            gridStrength = s; gridMean = mean
            ratioBuf = IntArray(gw * gh)
            ratio = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
            measureReach()
        }
    }

    /** Share of the panel in shade (below [SHADE_LEVEL] of the mean light) with the light moved by fx, fy. */
    private fun shadeShare(fx: Float, fy: Float): Float {
        val sx = fx * gw; val sy = fy * w * gh / panelH   // same mapping as updateRatio
        val dark = SHADE_LEVEL * lightMean
        var n = 0; var all = 0
        for (y in 0 until gh step 3) for (x in 0 until gw step 3) {
            all++
            if (sample(x - sx, y - sy) < dark) n++
        }
        return n.toFloat() / all
    }

    /**
     * For each of [REACH_DIRS] directions, the longest light shift (up to [MAX_TILT]) that keeps the shaded share
     * of the panel at most [MAX_SHADE], or a little over the level pose if the theme's own light is darker than that.
     * Walks out in eighths and stops at the first step over the limit, so a direction is never trusted past a dark one.
     */
    private fun measureReach() {
        val cap = max(MAX_SHADE, shadeShare(0f, 0f) + 0.05f)
        for (d in 0 until REACH_DIRS) {
            val ang = 2 * PI.toFloat() * d / REACH_DIRS
            var ok = 0f
            for (k in 1..8) {
                val f = MAX_TILT * k / 8
                if (shadeShare(f * cos(ang), f * sin(ang)) > cap) break
                ok = f
            }
            reach[d] = ok
        }
    }

    private fun updateRatio() {
        ensureGrid()
        // shifted light / light, stored divided by the largest ratio and multiplied back when the layer is drawn
        val sx = tiltX * gw / w; val sy = tiltY * gh / panelH
        var top = 1f
        for (y in 0 until gh) for (x in 0 until gw)
            top = max(top, sample(x - sx, y - sy) / max(grid[y * gw + x], MIN_LIGHT))
        val k = min(top, 4f)
        for (y in 0 until gh) for (x in 0 until gw) {
            val i = y * gw + x
            val g = clamp(sample(x - sx, y - sy) / max(grid[i], MIN_LIGHT) / k * 255f + 0.5f)
            ratioBuf[i] = (0xff shl 24) or (g shl 16) or (g shl 8) or g
        }
        ratio!!.setPixels(ratioBuf, 0, gw, 0, 0, gw, gh)
        layerPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(k, k, k, 1f) })
    }

    /** Bilinear sample of [grid], edges repeated. */
    private fun sample(x: Float, y: Float): Float {
        val cx = x.coerceIn(0f, gw - 1f); val cy = y.coerceIn(0f, gh - 1f)
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val x1 = min(x0 + 1, gw - 1); val y1 = min(y0 + 1, gh - 1)
        val ax = cx - x0; val ay = cy - y0
        val a = grid[y0 * gw + x0] + (grid[y0 * gw + x1] - grid[y0 * gw + x0]) * ax
        val b = grid[y1 * gw + x0] + (grid[y1 * gw + x1] - grid[y1 * gw + x0]) * ax
        return a + (b - a) * ay
    }

    /** Draw [bmp], holding view rows from [top] on, with the tilt light applied. Neutral tilt draws it untouched. */
    fun drawLit(canvas: Canvas, bmp: Bitmap, top: Int) {
        val rb = ratio
        if (!tiltActive || rb == null) { canvas.drawBitmap(bmp, 0f, 0f, null); return }
        canvas.saveLayer(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), layerPaint)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        val pTop = (panelTop - top).toFloat(); val pBottom = (panelTop + panelH - top).toFloat()
        ratioDst.set(0f, pTop, w.toFloat(), pBottom)
        canvas.drawBitmap(rb, null, ratioDst, ratioPaint)
        // rows outside the panel repeat its edge rows, like panelSlice does for the plate and the light
        if (pTop > 0) {
            ratioSrc.set(0, 0, gw, 1); ratioDst.set(0f, 0f, w.toFloat(), pTop)
            canvas.drawBitmap(rb, ratioSrc, ratioDst, ratioPaint)
        }
        if (pBottom < bmp.height) {
            ratioSrc.set(0, gh - 1, gw, gh); ratioDst.set(0f, pBottom, w.toFloat(), bmp.height.toFloat())
            canvas.drawBitmap(rb, ratioSrc, ratioDst, ratioPaint)
        }
        canvas.restore()
    }

    /** Movable shadows: the shadow layer, shifted by the tilt, multiplied over the board everywhere but on the caps. */
    fun drawShadows(canvas: Canvas) {
        val sh = shade ?: return
        canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), shadePaint)
        canvas.drawColor(Color.WHITE) // the layer multiplies everywhere, so where the shifted shadows leave a gap it must be white
        canvas.drawBitmap(sh, shadowDx, shadowDy, paint)
        canvas.drawBitmap(mask, 0f, 0f, capWhite)
        canvas.restore()
    }

    fun kindOf(key: Key): String? = when {
        key.isSpacer || key.backgroundType == Key.BACKGROUND_TYPE_EMPTY -> null
        key.hasActionKeyBackground() -> "enter"
        key.hasFunctionalBackground() -> "modifier"
        else -> "letter"
    }

    /** Cap rectangle in view coordinates. Gaps between caps follow the reference: 20 / 108 across, 37 / 156 down. */
    fun capRect(key: Key, padLeft: Int, padTop: Int, pitchW: Int, pitchH: Int, out: RectF) {
        val ix = pitchW * 10f / 108f
        val iy = pitchH * 18.5f / 156f
        val x0 = key.x - key.horizontalGap / 2f + padLeft
        val y0 = key.y - key.verticalGap / 2f + padTop
        out.set(x0 + ix, y0 + iy, x0 + key.width + key.horizontalGap - ix, y0 + key.height + key.verticalGap - iy)
    }

    /** Area a key paints, shadow included. */
    fun extent(cap: RectF, out: Rect) {
        val p = (62 * f).roundToInt() + 2
        out.set(cap.left.toInt() - p, cap.top.toInt() - p, cap.right.toInt() + p + 1, cap.bottom.toInt() + p + 1)
    }

    /** Device px per reference px (1080 wide board). */
    val refScale get() = ref

    /** Theme icon mask, downscaled with averaging to [side] px; null if the theme has none. */
    fun icon(name: String, side: Int): Bitmap? = iconCache.getOrPut("$name/$side") {
        theme.icon(name)?.let { RasterTheme.scaled(it, side.toFloat() / it.width) }
    }

    private fun skinImage(img: Bitmap) = scaledSkins.getOrPut(img) { RasterTheme.scaled(img, f) }

    private fun nine(kind: String, pressed: Boolean, shadow: Boolean, cw: Int, ch: Int): Pair<Bitmap, Int> {
        val skin = theme.skins[kind]!!
        val inset = ((if (shadow) skin.shadowInset else skin.inset) * f).roundToInt()
        val key = "$kind/$pressed/$shadow/$cw/$ch"
        val bmp = nineCache.getOrPut(key) {
            val src = when {
                shadow && pressed -> skin.pressedShadow
                shadow -> skin.shadow
                pressed -> skin.pressed
                else -> skin.normal
            }
            val img = if (kind == "enter" && !shadow && primaryColor != DEFAULT_PRIMARY) tint(skinImage(src)) else skinImage(src)
            RasterTheme.nine(img, cw + 2 * inset, ch + 2 * inset, paint)
        }
        return bmp to inset
    }

    private fun skinFor(kind: String) = if (kind == "enter") enterSkin!! else theme.skins[kind]!!

    /**
     * Px the painted shadow rises above the cap's top edge. Nick 2026-11-01: no shadow above the keys,
     * so the shadow is drawn this much lower; moving it keeps its soft corners, a clip would leave a step.
     * Measured on the sliced bitmaps: [RasterTheme.nine] squashes the skin for short keys such as the arrow row,
     * so the theme's own rows do not tell where the edges land.
     */
    private fun shadowDrop(kind: String, pressed: Boolean, cw: Int, ch: Int) = shadowDrops.getOrPut("$kind/$pressed/$cw/$ch") {
        val (s, ps) = nine(kind, pressed, true, cw, ch)
        val (c, pc) = nine(kind, pressed, false, cw, ch)
        max(0, firstOpaqueRow(c) - pc - (firstOpaqueRow(s) - ps))
    }

    private fun firstOpaqueRow(img: Bitmap): Int {
        val x = img.width / 2
        for (y in 0 until img.height) if (Color.alpha(img.getPixel(x, y)) > 8) return y
        return 0
    }

    /**
     * Repaint a cap in [primaryColor] keeping its shading: each pixel's brightness relative to the cap's mean
     * brightness scales the new colour (darker edges stay darker, the highlight rises towards white).
     */
    private fun tint(img: Bitmap): Bitmap = tinted.getOrPut(img) {
        val px = IntArray(img.width * img.height)
        img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
        val tr = Color.red(primaryColor); val tg = Color.green(primaryColor); val tb = Color.blue(primaryColor)
        val headroom = max(1e-3f, 255f / enterLuma - 1f)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            val t = luma(c) / enterLuma
            px[i] = if (t <= 1f) {
                (a shl 24) or (clamp(tr * t) shl 16) or (clamp(tg * t) shl 8) or clamp(tb * t)
            } else {
                val k = min(1f, (t - 1f) / headroom)
                (a shl 24) or (clamp(tr + (255 - tr) * k) shl 16) or (clamp(tg + (255 - tg) * k) shl 8) or clamp(tb + (255 - tb) * k)
            }
        }
        Bitmap.createBitmap(px, img.width, img.height, Bitmap.Config.ARGB_8888)
    }

    private fun luma(c: Int) = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    /** Mean brightness of the opaque pixels: the reference the shading is measured against. */
    private fun meanLuma(img: Bitmap): Float {
        val px = IntArray(img.width * img.height)
        img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
        var sum = 0.0; var n = 0
        for (c in px) if ((c ushr 24) > 200) { sum += luma(c); n++ }
        return if (n == 0) 128f else max(1f, (sum / n).toFloat())
    }

    /**
     * Redraw [dirty] (view coordinates) from [keys] and compose it into [out].
     * [padLeft]/[padTop] are the view paddings, [pitchW]/[pitchH] the most common key pitch.
     */
    fun render(keys: Collection<Key>, dirty: Rect, padLeft: Int, padTop: Int, pitchW: Int, pitchH: Int, painter: LabelPainter) {
        if (!dirty.intersect(0, 0, w, h)) return
        if (lightDirty) { updateLight(); dirty.set(0, 0, w, h) }
        val canvases = if (shade != null) arrayOf(baseCanvas, enterCanvas, labelCanvas, maskCanvas, shadeCanvas)
            else arrayOf(baseCanvas, enterCanvas, labelCanvas, maskCanvas)
        for (c in canvases) { c.save(); c.clipRect(dirty) }
        if (shade != null) shadeCanvas.drawColor(Color.WHITE)
        baseCanvas.drawBitmap(plate, 0f, 0f, null)
        enterCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        labelCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (pass in 0..1) for (key in keys) {
            val kind = kindOf(key) ?: continue
            capRect(key, padLeft, padTop, pitchW, pitchH, capRect)
            extent(capRect, r)
            if (!Rect.intersects(r, dirty)) continue
            val cw = capRect.width().roundToInt(); val ch = capRect.height().roundToInt()
            val cx = capRect.left.roundToInt(); val cy = capRect.top.roundToInt()
            val pressed = key.isPressed
            if (pass == 0) {
                val (s, p) = nine(kind, pressed, true, cw, ch)
                val drop = shadowDrop(kind, pressed, cw, ch).toFloat()
                (if (shade != null) shadeCanvas else baseCanvas).drawBitmap(s, (cx - p).toFloat(), cy - p + drop, shadowPaint)
                continue
            }
            val (c, p) = nine(kind, pressed, false, cw, ch)
            (if (kind == "enter") enterCanvas else baseCanvas).drawBitmap(c, (cx - p).toFloat(), (cy - p).toFloat(), null)
            maskCanvas.drawBitmap(c, (cx - p).toFloat(), (cy - p).toFloat(), null)
            val target = if (kind == "enter") enterCanvas else labelCanvas
            val kx = (key.x + padLeft).toFloat(); val ky = (key.y + padTop).toFloat()
            target.save()
            target.translate(kx, ky)
            capRect.offset(-kx, -ky)
            if (pressed) target.translate(0f, 2 * ref)
            painter.draw(key, target, capRect, skinFor(kind), pressed)
            target.restore()
        }
        for (c in canvases) c.restore()
        compose(dirty)
    }

    private var bufB = IntArray(0); private var bufL = IntArray(0); private var bufE = IntArray(0); private var bufM = IntArray(0)

    private fun compose(d: Rect) {
        val rw = d.width(); val rh = d.height(); val n = rw * rh
        if (bufB.size < n) { bufB = IntArray(n); bufL = IntArray(n); bufE = IntArray(n); bufM = IntArray(n) }
        base.getPixels(bufB, 0, rw, d.left, d.top, rw, rh)
        labels.getPixels(bufL, 0, rw, d.left, d.top, rw, rh)
        enter.getPixels(bufE, 0, rw, d.left, d.top, rw, rh)
        mask.getPixels(bufM, 0, rw, d.left, d.top, rw, rh)
        // a whole board is ~1M pixels: on one core that is the visible pause after a language switch,
        // so big areas are split into row bands composed side by side; a pressed key stays on this thread
        val bands = if (n < PARALLEL_MIN_PIXELS) 1 else POOL_THREADS + 1
        if (bands == 1) composeRows(d, 0, rh)
        else {
            val per = (rh + bands - 1) / bands
            val jobs = (1 until bands).map { b ->
                POOL.submit { composeRows(d, min(rh, b * per), min(rh, (b + 1) * per)) }
            }
            composeRows(d, 0, min(rh, per))
            for (job in jobs) job.get()
        }
        out.setPixels(bufB, 0, rw, d.left, d.top, rw, rh)
    }

    private fun composeRows(d: Rect, fromRow: Int, toRow: Int) {
        val rw = d.width()
        val labLit = theme.labelLighting
        val entLit = theme.skins["enter"]?.lighting ?: 1f
        for (y in fromRow until toRow) {
            var i = (d.top + y) * w + d.left
            var j = y * rw
            for (x in 0 until rw) {
                val b = bufB[j]
                val lt = light[i]
                var rr = ((b shr 16) and 0xff) * lt
                var gg = ((b shr 8) and 0xff) * lt
                var bb = (b and 0xff) * lt
                val l1 = min(lt, 1f)
                val la = bufL[j] ushr 24
                if (la != 0) {
                    val a = la / 255f; val k = (1 - labLit + labLit * l1) * a; val l = bufL[j]
                    rr = rr * (1 - a) + ((l shr 16) and 0xff) * k
                    gg = gg * (1 - a) + ((l shr 8) and 0xff) * k
                    bb = bb * (1 - a) + (l and 0xff) * k
                }
                val ea = bufE[j] ushr 24
                if (ea != 0) {
                    val a = ea / 255f; val k = (1 - entLit + entLit * l1) * a; val e = bufE[j]
                    rr = rr * (1 - a) + ((e shr 16) and 0xff) * k
                    gg = gg * (1 - a) + ((e shr 8) and 0xff) * k
                    bb = bb * (1 - a) + (e and 0xff) * k
                }
                // plate grain only between the keys, the caps keep their own finer plastic grain
                val m = (bufM[j] ushr 24) / 255f
                val nz = noise[i] * (1 - m) + capNoise[i] * m + 0.5f
                bufB[j] = (0xff shl 24) or (clamp(rr + nz) shl 16) or (clamp(gg + nz) shl 8) or clamp(bb + nz)
                i++; j++
            }
        }
    }

    private fun clamp(v: Float) = max(0, min(255, v.toInt()))

    companion object {
        /** Told when the board got a new size or light, so the suggestion strip can repaint its piece of the panel. */
        @JvmStatic
        var onPanelChanged: ((RasterRenderer) -> Unit)? = null
        /** Told when the tilt light moved, so the suggestion strip redraws with it. */
        @JvmStatic
        var onTiltChanged: (() -> Unit)? = null

        /** Largest light shift, as a fraction of the board width. */
        const val MAX_TILT = 0.25f
        /** Shadow shift in reference px per unit of tilt: at MAX_TILT the shadows move only 4 px, a background hint
         *  of depth. 18 px (the first value) made the keys look a few millimetres tall. */
        private const val SHADOW_SHIFT = 16f
        private const val MIN_LIGHT = 0.04f
        /** Light below this share of the mean reads as shade: the labels there go dim (measured on tilt captures). */
        private const val SHADE_LEVEL = 0.7f
        /** At most a third of the board in shade at any tilt, well under the half Nick saw. */
        private const val MAX_SHADE = 1 / 3f
        private const val REACH_DIRS = 16
        /** The colour the Enter skin is painted in (same value as Defaults.PREF_PRIMARY_COLOR). */
        const val DEFAULT_PRIMARY = -0xC64325
        private const val DARK_GLYPH = -0xe3e3e2 // #1C1C1E

        /** Below this many pixels (a few keys) splitting costs more than it saves. */
        private const val PARALLEL_MIN_PIXELS = 200_000
        /** Helpers next to the UI thread; the UI thread composes one band itself. */
        private val POOL_THREADS = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 3)
        private val POOL: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newFixedThreadPool(POOL_THREADS) { r ->
                Thread(r, "graphite-compose").apply { isDaemon = true; priority = Thread.MAX_PRIORITY }
            }
    }
}
