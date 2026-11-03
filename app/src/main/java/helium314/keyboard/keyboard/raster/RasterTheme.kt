// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.raster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A raster key theme: per key type 9-slice cap and shadow images, a lighting map, plate grain and
 * plastic grain, as produced by the theme scripts (theme.json "format": 1).
 * Sizes in theme.json are in reference px (1080 wide board) times "scale".
 */
class RasterTheme private constructor(context: Context, private val dir: String) {
    private val assets = context.assets
    val spec = JSONObject(assets.open("$dir/theme.json").bufferedReader().readText())
    val scale = spec.getInt("scale")
    val capInsetX: Float
    val capInsetY: Float
    val labelSize = spec.getDouble("label_size").toFloat()
    val labelLighting = spec.getDouble("label_lighting").toFloat()
    val lightingGain = spec.getJSONObject("lighting").getDouble("gain").toFloat()
    val capGrainStrength = spec.optJSONObject("cap_grain")?.getDouble("strength")?.toFloat() ?: 0f
    /** Plate grain contrast: grey levels per tile level away from 128. */
    val grainGain = spec.getJSONObject("grain").optDouble("gain", 0.5).toFloat()
    /** Grain tile pixel size in reference px; 0 = the theme's own scale (the tile averages away on the phone). */
    val grainSize = spec.getJSONObject("grain").optDouble("size", 0.0).toFloat()
    val capGrainSize = spec.optJSONObject("cap_grain")?.optDouble("size", 0.0)?.toFloat() ?: 0f
    /** Hairline scratches in the base plastic (keys stay clean): grey levels where the light is average (0 = none), and how many per 100x100 reference px. */
    val scratchStrength = spec.optJSONObject("scratches")?.optDouble("strength", 22.0)?.toFloat() ?: 22f
    val scratchDensity = spec.optJSONObject("scratches")?.optDouble("density", 3.5)?.toFloat() ?: 3.5f
    /** Darkness of the cast shadows, 1 = as painted. */
    val shadowOpacity = spec.optDouble("shadow_opacity", 1.0).toFloat()
    /** Reference px of the board the plate and light were painted for, and where its key rows start and end. */
    val panelHeight = spec.optJSONObject("panel")?.getDouble("height")?.toFloat() ?: 0f
    val panelKeysTop = spec.optJSONObject("panel")?.getJSONArray("keys")?.getDouble(0)?.toFloat() ?: 0f
    val panelKeysBottom = spec.optJSONObject("panel")?.getJSONArray("keys")?.getDouble(1)?.toFloat() ?: panelHeight
    val skins = HashMap<String, Skin>()
    val background: Bitmap = decode(spec.getString("background"))
    val lighting: Bitmap = decode(spec.getJSONObject("lighting").getString("image"))
    val grain: Bitmap = decode(spec.getJSONObject("grain").getString("image"))
    val capGrain: Bitmap? = spec.optJSONObject("cap_grain")?.let { decode(it.getString("image")) }
    private val icons = HashMap<String, Bitmap?>()

    class Skin(
        val normal: Bitmap, val pressed: Bitmap, val shadow: Bitmap, val pressedShadow: Bitmap,
        val inset: Int, val shadowInset: Int, val label: Int, val lighting: Float
    )

    init {
        val ci = spec.getJSONArray("cap_inset")
        capInsetX = ci.getDouble(0).toFloat()
        capInsetY = ci.getDouble(1).toFloat()
        val keys = spec.getJSONObject("keys")
        for (kind in keys.keys()) {
            val k = keys.getJSONObject(kind)
            skins[kind] = Skin(
                decode(k.getString("normal")), decode(k.getString("pressed")),
                decode(k.getString("shadow")), decode(k.getString("pressed_shadow")),
                k.getInt("inset"), k.getInt("shadow_inset"), Color.parseColor(k.getString("label")),
                k.getDouble("lighting").toFloat()
            )
        }
    }

    private fun decode(name: String): Bitmap {
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return assets.open("$dir/$name").use { BitmapFactory.decodeStream(it, null, opts)!! }
    }

    /** Alpha mask of a key icon, square, side = cap height; null if the theme has no such icon. */
    fun icon(name: String): Bitmap? = icons.getOrPut(name) {
        try {
            val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ALPHA_8 }
            assets.open("$dir/icon_$name.png").use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) { null }
    }

    companion object {
        const val THEME_NAME = "raycast"
        private var instance: RasterTheme? = null

        @JvmStatic
        fun get(context: Context): RasterTheme =
            instance ?: RasterTheme(context.applicationContext, "raster_themes/$THEME_NAME").also { instance = it }

        /** Stretch img to tw x th the way the theme scripts do: only the centre row and column stretch. */
        fun nine(img: Bitmap, tw: Int, th: Int, paint: Paint): Bitmap {
            var src = img
            if (tw < src.width || th < src.height)
                src = Bitmap.createScaledBitmap(src, min(src.width, tw), min(src.height, th), true)
            val w = src.width; val h = src.height
            val cx = w / 2; val cy = h / 2
            val out = Bitmap.createBitmap(max(tw, 1), max(th, 1), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val xs = arrayOf(intArrayOf(0, cx, 0, cx), intArrayOf(cx, cx + 1, cx, tw - (w - cx - 1)), intArrayOf(cx + 1, w, tw - (w - cx - 1), tw))
            val ys = arrayOf(intArrayOf(0, cy, 0, cy), intArrayOf(cy, cy + 1, cy, th - (h - cy - 1)), intArrayOf(cy + 1, h, th - (h - cy - 1), th))
            val s = Rect(); val d = Rect()
            for (x in xs) for (y in ys) {
                if (x[3] <= x[2] || y[3] <= y[2]) continue
                s.set(x[0], y[0], x[1], y[1]); d.set(x[2], y[2], x[3], y[3])
                c.drawBitmap(src, s, d, paint)
            }
            return out
        }

        /** Downscale by an arbitrary factor with averaging (halving steps, then one bilinear step). */
        fun scaled(img: Bitmap, f: Float): Bitmap {
            var b = img
            var left = f
            while (left < 0.5f && b.width >= 4 && b.height >= 4) {
                b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
                left *= 2f
            }
            return Bitmap.createScaledBitmap(b, max(1, (b.width * left).roundToInt()), max(1, (b.height * left).roundToInt()), true)
        }
    }
}
