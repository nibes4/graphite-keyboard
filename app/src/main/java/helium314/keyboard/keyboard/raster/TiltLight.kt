// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.raster

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.sqrt

/**
 * Reads the phone's tilt for the tilt light. Listens only between [start] and [stop] (the keyboard view starts it
 * a moment after it is shown and stops it when hidden), 30 times a second, on the gravity sensor if the phone has
 * one, else on the accelerometer. The pose the keyboard opened in is neutral, which is the theme's own light,
 * and the neutral pose slowly follows a new way of holding the phone, so the board settles back to its look.
 * [onTilt] gets the light shift as fractions of the board width, within ±[RasterRenderer.MAX_TILT].
 */
class TiltLight(context: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val smoothing = if (sensor?.type == Sensor.TYPE_GRAVITY) 0.08f else 0.25f   // seconds
    @Suppress("DEPRECATION")
    private val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?)?.defaultDisplay
    private var running = false
    private var last = 0L
    private val g = FloatArray(2)      // smoothed down direction on the screen
    private val base = FloatArray(2)   // neutral pose

    val isRunning get() = running

    fun start() {
        if (running || sensor == null) return
        running = true
        last = 0L
        sensors!!.registerListener(this, sensor, 33_000)
    }

    fun stop() {
        if (!running) return
        running = false
        sensors!!.unregisterListener(this)
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (n < 1f) return
        // device axes to screen axes (x right, y up)
        val dx = v[0] / n; val dy = v[1] / n
        val sx: Float; val sy: Float
        when (display?.rotation) {
            Surface.ROTATION_90 -> { sx = -dy; sy = dx }
            Surface.ROTATION_180 -> { sx = -dx; sy = -dy }
            Surface.ROTATION_270 -> { sx = dy; sy = -dx }
            else -> { sx = dx; sy = dy }
        }
        if (last == 0L) {
            g[0] = sx; g[1] = sy; base[0] = sx; base[1] = sy
            last = event.timestamp
            return
        }
        val dt = ((event.timestamp - last) / 1e9f).coerceIn(0f, 0.5f)
        last = event.timestamp
        val a = dt / (smoothing + dt)
        g[0] += (sx - g[0]) * a; g[1] += (sy - g[1]) * a
        val b = dt / (RECENTER_SECONDS + dt)
        base[0] += (g[0] - base[0]) * b; base[1] += (g[1] - base[1]) * b
        // the light slides towards the edge that rises, as if it hung above the phone
        val m = RasterRenderer.MAX_TILT
        onTilt(((g[0] - base[0]) * GAIN).coerceIn(-m, m), (-(g[1] - base[1]) * GAIN).coerceIn(-m, m))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** Light shift per unit change of the down direction: about 20° of tilt moves it the full MAX_TILT. */
        private const val GAIN = 0.75f
        private const val RECENTER_SECONDS = 8f
    }
}
