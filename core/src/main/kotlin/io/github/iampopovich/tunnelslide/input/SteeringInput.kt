package io.github.iampopovich.tunnelslide.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.MathUtils
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Produces a steering value in [-1, 1] from tilt, keyboard or touch.
 *
 * Tilt: we project the accelerometer onto the screen's "right" axis, which depends on
 * the current screen rotation. Tilting the right edge down makes that reading negative.
 */
class SteeringInput {
    /** Accelerometer reading (along screen-right) that counts as "straight". Set by calibration. */
    var neutral = 0f
    var autopilot: ((Float) -> Float)? = null

    private var filteredTilt = 0f
    private val hasAccel = Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)

    fun read(dt: Float, time: Float): Float {
        autopilot?.let { return it(time) }

        var steer = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) steer -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) steer += 1f
        if (steer != 0f) return steer

        // Touch fallback: hold left/right half of the screen.
        for (p in 0 until 4) {
            if (!Gdx.input.isTouched(p)) continue
            steer += if (Gdx.input.getX(p) < Gdx.graphics.width / 2) -1f else 1f
        }
        if (steer != 0f) return MathUtils.clamp(steer, -1f, 1f)

        if (!hasAccel) return 0f
        val raw = -screenRightAccel() - neutral
        val k = 1f - exp(-FILTER_RATE * dt)
        filteredTilt += (raw - filteredTilt) * k
        return shape(filteredTilt / FULL_TILT)
    }

    /** Current accelerometer value along the screen-right axis, m/s^2. */
    fun screenRightAccel(): Float = when (Gdx.input.rotation) {
        90 -> -Gdx.input.accelerometerY
        180 -> -Gdx.input.accelerometerX
        270 -> Gdx.input.accelerometerY
        else -> Gdx.input.accelerometerX
    }

    private fun shape(x: Float): Float {
        val a = abs(x)
        if (a < DEAD_ZONE) return 0f
        val t = MathUtils.clamp((a - DEAD_ZONE) / (1f - DEAD_ZONE), 0f, 1f)
        return Math.copySign(t, x)
    }

    companion object {
        private const val G = 9.81f
        /** Tilt (as accelerometer projection) that gives full steering: ~25 degrees. */
        private val FULL_TILT = G * sin(25f * MathUtils.degreesToRadians)
        private const val DEAD_ZONE = 0.06f
        private const val FILTER_RATE = 18f
    }
}
