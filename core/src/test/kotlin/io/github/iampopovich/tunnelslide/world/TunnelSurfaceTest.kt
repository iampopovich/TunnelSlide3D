package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals

class TunnelSurfaceTest {
    private val r = 4f
    private val eps = 1e-4f
    private val trig = 3e-3f
    private val quarter = TunnelSurface.TAU / 4f

    @Test
    fun playerSitsUnderTheCameraInEveryShape() {
        for (shape in listOf(0f, 0.5f, 1f, 1.5f, 2f)) {
            val p = TunnelSurface.point(0f, 10f, shape, r, Vector3())
            assertEquals(0f, p.x, trig); assertEquals(-r, p.y, trig); assertEquals(-10f, p.z, eps)
            val n = TunnelSurface.normal(0f, shape, Vector2())
            assertEquals(0f, n.x, trig); assertEquals(1f, n.y, trig)
        }
    }

    @Test
    fun quarterTurnInsideIsTheRightWall() {
        val p = TunnelSurface.point(quarter, 0f, 0f, r, Vector3())
        assertEquals(r, p.x, trig); assertEquals(0f, p.y, trig)
    }

    @Test
    fun flatRibbonKeepsArcLength() {
        val p = TunnelSurface.point(quarter, 0f, 1f, r, Vector3())
        assertEquals(r * quarter, p.x, eps); assertEquals(-r, p.y, eps)
    }

    @Test
    fun quarterTurnOutsideCurvesAwayDownwards() {
        // Outside of a tube whose centre is below the player: a quarter turn is level with the centre.
        val p = TunnelSurface.point(quarter, 0f, 2f, r, Vector3())
        assertEquals(r, p.x, trig); assertEquals(-2f * r, p.y, trig)
        val n = TunnelSurface.normal(quarter, 2f, Vector2())
        assertEquals(1f, n.x, trig); assertEquals(0f, n.y, trig)   // points outwards
    }

    @Test
    fun thetaIsMeasuredFromThePlayerAndCutAtTheSeam() {
        assertEquals(0f, TunnelSurface.theta(0.3f, 0.3f, 0.9f), eps)
        // Seam at 0: u = 0.95 is on the far left of a player at 0.05, not just next to them.
        assertEquals(-0.1f * TunnelSurface.TAU, TunnelSurface.theta(0.95f, 0.05f, 0.5f), 1e-3f)
        assertEquals(0.9f * TunnelSurface.TAU, TunnelSurface.theta(0.95f, 0.05f, 0f), 1e-3f)
    }

    @Test
    fun wrapRelStaysInHalfOpenRange() {
        assertEquals(-0.5f, TunnelSurface.wrapRel(0.5f), eps)
        assertEquals(0.1f, TunnelSurface.wrapRel(1.1f), eps)
        assertEquals(-0.1f, TunnelSurface.wrapRel(-1.1f), eps)
        assertEquals(0.3f, TunnelSurface.wrapAbs(-0.7f), eps)
    }
}
