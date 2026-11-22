package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.sqrt

/**
 * Forward motion does not depend on steering: speed grows from START_SPEED with constant
 * acceleration up to MAX_SPEED. So the moment the player reaches any z is known in advance,
 * which lets the generator check moving obstacles against the route at the right time.
 * Game time (RunState.time) is the clock; slow-motion effects must scale it, not the speed.
 */
object Kinematics {
    private val v0 = GameConfig.START_SPEED
    private val a = GameConfig.ACCELERATION
    private val vMax = GameConfig.MAX_SPEED
    private val tCap = (vMax - v0) / a
    private val zCap = v0 * tCap + a * tCap * tCap / 2f

    fun timeAt(z: Float): Float =
        if (z <= zCap) (-v0 + sqrt(v0 * v0 + 2f * a * maxOf(z, 0f))) / a
        else tCap + (z - zCap) / vMax

    fun zAt(t: Float): Float =
        if (t <= tCap) v0 * t + a * t * t / 2f
        else zCap + (t - tCap) * vMax
}
