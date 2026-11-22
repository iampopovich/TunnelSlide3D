package io.github.iampopovich.tunnelslide.world.gen

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.Obstacle
import io.github.iampopovich.tunnelslide.world.TunnelSurface

/**
 * Small DSL used by patterns. A pattern first lays out the route with [go], then drops
 * blocks anywhere in its z range. [ObstacleField] later removes every block that touches
 * the route, so a pattern can be as dense as it likes and still always be passable.
 */
class PatternBuilder(
    val rng: RandomXS128,
    val path: SafePath,
    /** 0 = easiest, 1 = hardest. */
    val difficulty: Float,
    speed: Float,
) {
    val blocks = ArrayList<Obstacle>()
    val startZ = path.lastZ
    var z = path.lastZ
        private set
    var u = path.lastU
        private set

    /** Fastest route drift (u per unit of z) the player can follow at this speed. */
    val maxSlope = GameConfig.MAX_TURN_RATE / speed * GameConfig.REACH_SAFETY
    val laneW = 1f / LANES
    var color = rng.nextInt(BLOCK_COLORS)

    /** Advances the route by [dz] while drifting by [du] (clamped to what is reachable). */
    fun go(dz: Float, du: Float = 0f) {
        val limit = maxSlope * dz
        z += dz
        u = TunnelSurface.wrapAbs(u + MathUtils.clamp(du, -limit, limit))
        path.add(z, u)
    }

    /** Max drift over [dz]. */
    fun reach(dz: Float) = maxSlope * dz

    fun randomDrift(dz: Float, share: Float = 1f) = (rng.nextFloat() * 2f - 1f) * reach(dz) * share

    fun lane(index: Int): Float = TunnelSurface.wrapAbs((index + 0.5f) * laneW)

    /** Lane index closest to an absolute u. */
    fun laneOf(u: Float): Int = MathUtils.floor(TunnelSurface.wrapAbs(u) * LANES) % LANES

    fun block(laneIndex: Int, z: Float, length: Float = GameConfig.BLOCK_LENGTH, height: Float = GameConfig.BLOCK_HEIGHT) {
        val idx = ((laneIndex % LANES) + LANES) % LANES
        blocks.add(Obstacle(lane(idx), laneW * 0.5f * GameConfig.BLOCK_WIDTH_FILL, z, length, height, color))
    }

    /** A full ring of blocks; the route will carve its gap. */
    fun ring(z: Float, length: Float = GameConfig.WALL_LENGTH) {
        for (l in 0 until LANES) block(l, z, length)
    }

    fun chance(p: Float) = rng.nextFloat() < p

    companion object {
        val LANES = GameConfig.LANES.toInt()
        const val BLOCK_COLORS = 4

    }
}
