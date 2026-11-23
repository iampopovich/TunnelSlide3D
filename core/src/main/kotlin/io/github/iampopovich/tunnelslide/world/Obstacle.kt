package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.GameConfig

enum class ObstacleKind {
    /** Static block standing on the surface. */
    BLOCK,
    /** Full-span pillar: in the tube it crosses the axis and also blocks the opposite side. */
    PILLAR,
    /** "Tick-tock" cube: every tick it rests, then rolls one lane over its bottom edge. */
    ROLLER,
}

/**
 * An obstacle lying on the tunnel surface, in tunnel coordinates.
 * [u] is the centre across the tunnel (absolute, [0, 1)); for a roller it is the centre of
 * its home lane. [z] is the near edge.
 *
 * Rollers move in one of two ways:
 * - [sweepLanes] == 0: around the whole circumference in direction [dir], one lane per tick;
 * - [sweepLanes] >= 2: back and forth over that many lanes starting at home (on the flat:
 *   from edge to edge).
 * [phase] shifts the roller's clock by a number of ticks.
 */
class Obstacle(
    val u: Float,
    val halfWidth: Float,
    val z: Float,
    val length: Float,
    val height: Float,
    val colorIndex: Int,
    val kind: ObstacleKind = ObstacleKind.BLOCK,
    val phase: Float = 0f,
    val dir: Int = 1,
    val sweepLanes: Int = 0,
) {
    /** Lane width in u for a roller (it rolls exactly one of its own widths per tick). */
    val step: Float get() = halfWidth * 2f

    /** Lane offsets of a roller at [time]: before the current roll, after it, and whether it is rolling. */
    fun rollerLanes(time: Float, out: FloatArray): FloatArray {
        val s = time / GameConfig.ROLLER_STEP + phase
        val k = MathUtils.floor(s).toFloat()
        val q = s - k
        if (sweepLanes < 2) {
            val l0 = mod(dir * k, GameConfig.LANES)
            out[0] = l0; out[1] = l0 + dir
        } else {
            out[0] = tri(k, sweepLanes.toFloat()); out[1] = tri(k + 1f, sweepLanes.toFloat())
        }
        out[2] = if (q >= GameConfig.ROLLER_REST) 1f else 0f
        return out
    }

    /** True if a body of half-width [hw] at [pu] overlaps this obstacle (u only). */
    fun overlapsU(pu: Float, hw: Float, time: Float, shape: Float): Boolean {
        val reach = halfWidth + hw
        fun near(c: Float) = kotlin.math.abs(TunnelSurface.wrapRel(c - pu)) < reach
        return when (kind) {
            ObstacleKind.BLOCK -> near(u)
            // The far end touches the opposite wall only while the tube is closed.
            ObstacleKind.PILLAR -> near(u) || (shape < 0.5f && near(u + 0.5f))
            ObstacleKind.ROLLER -> {
                val l = rollerLanes(time, tmp)
                near(u + l[0] * step) || (l[2] > 0f && (near(u + l[1] * step) || near(u + (l[0] + l[1]) * 0.5f * step)))
            }
        }
    }

    /**
     * Side clearance between a body of half-width [hw] at [pu] and this obstacle at [time]:
     * negative when they overlap (same footprints as [overlapsU]).
     */
    fun clearanceU(pu: Float, hw: Float, time: Float, shape: Float): Float {
        fun gap(c: Float) = kotlin.math.abs(TunnelSurface.wrapRel(c - pu)) - halfWidth - hw
        return when (kind) {
            ObstacleKind.BLOCK -> gap(u)
            ObstacleKind.PILLAR -> if (shape < 0.5f) minOf(gap(u), gap(u + 0.5f)) else gap(u)
            ObstacleKind.ROLLER -> {
                val l = rollerLanes(time, tmp)
                var g = gap(u + l[0] * step)
                if (l[2] > 0f) g = minOf(g, gap(u + l[1] * step), gap(u + (l[0] + l[1]) * 0.5f * step))
                g
            }
        }
    }

    /**
     * Where this obstacle will hit a player who keeps position [pu] until reaching it at
     * [time]: the centre of the dangerous spot, or null if it will miss.
     */
    fun threatAt(pu: Float, hw: Float, time: Float, shape: Float): Float? {
        val reach = halfWidth + hw
        fun near(c: Float) = kotlin.math.abs(TunnelSurface.wrapRel(c - pu)) < reach
        return when (kind) {
            ObstacleKind.BLOCK -> if (near(u)) u else null
            ObstacleKind.PILLAR -> when {
                near(u) -> u
                shape < 0.5f && near(u + 0.5f) -> TunnelSurface.wrapAbs(u + 0.5f)
                else -> null
            }
            ObstacleKind.ROLLER -> {
                val l = rollerLanes(time, tmp)
                val c0 = TunnelSurface.wrapAbs(u + l[0] * step)
                val c1 = TunnelSurface.wrapAbs(u + l[1] * step)
                when {
                    near(c0) -> c0
                    l[2] > 0f && near(c1) -> c1
                    else -> null
                }
            }
        }
    }

    /** Route planning for static obstacles: everything they may ever cover. */
    fun mayCoverU(pu: Float, margin: Float): Boolean {
        val reach = halfWidth + margin
        fun near(c: Float) = kotlin.math.abs(TunnelSurface.wrapRel(c - pu)) < reach
        return when (kind) {
            ObstacleKind.BLOCK -> near(u)
            ObstacleKind.PILLAR -> near(u) || near(u + 0.5f)
            ObstacleKind.ROLLER -> true   // moving: must be checked in time, see ObstacleField
        }
    }

    private val tmp = FloatArray(3)

    companion object {
        fun mod(x: Float, m: Float) = x - m * MathUtils.floor(x / m)

        /** Ping-pong over n positions: 0, 1, .., n-1, n-2, .., 1, 0, 1, ... */
        fun tri(k: Float, n: Float): Float {
            val p = 2f * (n - 1f)
            val r = mod(k, p)
            return if (r <= n - 1f) r else p - r
        }
    }
}
