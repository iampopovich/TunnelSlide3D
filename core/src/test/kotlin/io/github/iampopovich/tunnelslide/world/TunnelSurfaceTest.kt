package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals

class TunnelSurfaceTest {
    private val r = 4f
    private val eps = 1e-4f

    @Test
    fun playerSitsOnTheBottomOfTheTube() {
        val p = TunnelSurface.point(0f, 10f, 0f, r, Vector3())
        assertEquals(0f, p.x, eps); assertEquals(-r, p.y, eps); assertEquals(-10f, p.z, eps)
    }

    @Test
    fun quarterTurnIsTheRightWall() {
        val p = TunnelSurface.point(0.25f, 0f, 0f, r, Vector3())
        assertEquals(r, p.x, eps); assertEquals(0f, p.y, eps)
    }

    @Test
    fun flatRibbonKeepsArcLength() {
        // Unrolled tube: a quarter revolution must be a quarter of the circumference away.
        val p = TunnelSurface.point(0.25f, 0f, 1f, r, Vector3())
        assertEquals(r * TunnelSurface.TAU / 4f, p.x, eps); assertEquals(-r, p.y, eps)
    }

    @Test
    fun normalPointsInwardsAtTheBottom() {
        val n = TunnelSurface.normal(0f, 0f, Vector2())
        assertEquals(0f, n.x, eps); assertEquals(1f, n.y, eps)
    }

    @Test
    fun wrapRelStaysInHalfOpenRange() {
        assertEquals(-0.5f, TunnelSurface.wrapRel(0.5f), eps)
        assertEquals(0.1f, TunnelSurface.wrapRel(1.1f), eps)
        assertEquals(-0.1f, TunnelSurface.wrapRel(-1.1f), eps)
        assertEquals(0.3f, TunnelSurface.wrapAbs(-0.7f), eps)
    }
}
