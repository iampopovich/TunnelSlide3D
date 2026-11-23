package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Maps tunnel coordinates to 3D. MUST stay in sync with assets/shaders/tunnel.vert.
 *
 * theta - angle across the surface relative to the player (0 = right under the camera)
 * dist  - distance ahead of the player along the tunnel (camera looks down -Z)
 * shape - 0 = inside a tube (ride the inner bottom),
 *         1 = flat ribbon cut open at the seam (you can fall off its edges),
 *         2 = outside of a tube (ride on top, the rest curves away below).
 *         In between, the surface is a curve of constant curvature (1 - shape) / R through
 *         the player's point, so the tube unrolls like a rolled sheet: the seam edges part
 *         and meet again instead of sweeping across. Arc length is preserved for every shape.
 */
object TunnelSurface {
    const val TAU = MathUtils.PI2

    /** Angle of absolute position [u] seen from [playerU], measured so the ribbon is cut at [seam]. */
    fun theta(u: Float, playerU: Float, seam: Float): Float =
        (wrapAbs(u - seam) - wrapAbs(playerU - seam)) * TAU

    fun point(theta: Float, dist: Float, shape: Float, radius: Float, out: Vector3): Vector3 {
        val a = (1f - shape) * theta
        val arc = radius * theta
        val sinc: Float
        val cosc: Float
        if (abs(a) < 1e-3f) {
            // Series near the flat ribbon, where sin(a)/a and (1-cos a)/a are 0/0.
            sinc = 1f - a * a / 6f
            cosc = a * 0.5f
        } else {
            sinc = sin(a) / a
            cosc = (1f - cos(a)) / a
        }
        return out.set(arc * sinc, -radius + arc * cosc, -dist)
    }

    /** Unit vector along the surface in the +theta direction (in the XY plane). */
    fun tangent(theta: Float, shape: Float, out: Vector2): Vector2 {
        val a = (1f - shape) * theta
        return out.set(cos(a), sin(a))
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
