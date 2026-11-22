package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

/**
 * Maps tunnel coordinates to 3D. MUST stay in sync with assets/shaders/tunnel.vert.
 *
 * theta - angle across the surface relative to the player (0 = right under the camera)
 * dist  - distance ahead of the player along the tunnel (camera looks down -Z)
 * shape - 0 = inside a tube (ride the inner bottom),
 *         1 = flat ribbon cut open at the seam (you can fall off its edges),
 *         2 = outside of a tube (ride on top, the rest curves away below).
 *         Values in between blend linearly. Arc length is preserved in all three.
 */
object TunnelSurface {
    const val TAU = MathUtils.PI2

    /** Angle of absolute position [u] seen from [playerU], measured so the ribbon is cut at [seam]. */
    fun theta(u: Float, playerU: Float, seam: Float): Float =
        (wrapAbs(u - seam) - wrapAbs(playerU - seam)) * TAU

    fun point(theta: Float, dist: Float, shape: Float, radius: Float, out: Vector3): Vector3 {
        val s = MathUtils.sin(theta)
        val c = MathUtils.cos(theta)
        val flatX = radius * theta
        val x: Float
        val y: Float
        if (shape <= 1f) {
            x = MathUtils.lerp(radius * s, flatX, shape)
            y = MathUtils.lerp(-radius * c, -radius, shape)
        } else {
            val k = shape - 1f
            x = MathUtils.lerp(flatX, radius * s, k)
            y = MathUtils.lerp(-radius, -2f * radius + radius * c, k)
        }
        return out.set(x, y, -dist)
    }

    /** Unit vector along the surface in the +theta direction (in the XY plane). */
    fun tangent(theta: Float, shape: Float, out: Vector2): Vector2 {
        val s = MathUtils.sin(theta)
        val c = MathUtils.cos(theta)
        return if (shape <= 1f) {
            out.set(MathUtils.lerp(c, 1f, shape), MathUtils.lerp(s, 0f, shape)).nor()
        } else {
            val k = shape - 1f
            out.set(MathUtils.lerp(1f, c, k), MathUtils.lerp(0f, -s, k)).nor()
        }
    }

    /** Unit normal pointing from the surface towards the rider's side. */
    fun normal(theta: Float, shape: Float, out: Vector2): Vector2 {
        tangent(theta, shape, out)
        return out.set(-out.y, out.x)
    }

    /** Wraps any u difference into [-0.5, 0.5). */
    fun wrapRel(u: Float): Float {
        var r = u - MathUtils.floor(u)   // [0, 1)
        if (r >= 0.5f) r -= 1f
        return r
    }

    /** Wraps absolute u into [0, 1). */
    fun wrapAbs(u: Float): Float = u - MathUtils.floor(u)
}
