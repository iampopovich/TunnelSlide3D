package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

/**
 * Maps tunnel coordinates to 3D. MUST stay in sync with assets/shaders/tunnel.vert.
 *
 * uRel  - position across the surface relative to the player, one revolution = 1, in [-0.5, 0.5)
 * dist  - distance ahead of the player along the tunnel
 * morph - 0 = tube (player rides the inner bottom), 1 = flat ribbon of the same width
 *
 * Camera looks down -Z, so a point `dist` ahead has z = -dist.
 */
object TunnelSurface {
    const val TAU = MathUtils.PI2

    fun point(uRel: Float, dist: Float, morph: Float, radius: Float, out: Vector3): Vector3 {
        val th = uRel * TAU
        val tubeX = radius * MathUtils.sin(th)
        val tubeY = -radius * MathUtils.cos(th)
        val flatX = radius * th
        val flatY = -radius
        return out.set(
            MathUtils.lerp(tubeX, flatX, morph),
            MathUtils.lerp(tubeY, flatY, morph),
            -dist,
        )
    }

    /** Unit vector along the surface in the +u direction (in the XY plane). */
    fun tangent(uRel: Float, morph: Float, out: Vector2): Vector2 {
        val th = uRel * TAU
        val tx = MathUtils.lerp(MathUtils.cos(th), 1f, morph)
        val ty = MathUtils.lerp(MathUtils.sin(th), 0f, morph)
        return out.set(tx, ty).nor()
    }

    /** Unit normal pointing from the surface towards the rider (inwards for the tube). */
    fun normal(uRel: Float, morph: Float, out: Vector2): Vector2 {
        tangent(uRel, morph, out)
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
