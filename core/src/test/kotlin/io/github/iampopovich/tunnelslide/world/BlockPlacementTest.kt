package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockPlacementTest {
    private val r = 4f
    private val eps = 1e-4f
    /** MathUtils.sin/cos use a lookup table, ~1e-3 error at radius 4. */
    private val trigEps = 3e-3f

    private fun place(uRel: Float, morph: Float = 0f) =
        BlockPlacement.compose(uRel, near = 10f, length = 2f, width = 1f, height = 1f, lift = 0f,
            morph = morph, radius = r, out = Matrix4())

    @Test
    fun blockOnTheRightWallStandsOnItAndPointsToTheAxis() {
        val m = place(0.25f)
        // Bottom face centre touches the wall at x = R ...
        val bottom = Vector3(0f, -0.5f, 0f).mul(m)
        assertEquals(r, bottom.x, eps); assertEquals(0f, bottom.y, eps)
        // ... and the top face centre is one unit closer to the tunnel axis.
        val top = Vector3(0f, 0.5f, 0f).mul(m)
        assertEquals(r - 1f, top.x, eps); assertEquals(0f, top.y, eps)
        // Along-tunnel extent: near face at dist 10, far face at dist 12.
        assertEquals(-10f, Vector3(0f, 0f, 0.5f).mul(m).z, eps)
        assertEquals(-12f, Vector3(0f, 0f, -0.5f).mul(m).z, eps)
    }

    @Test
    fun blockAtAnyAngleHasItsBottomOnTheSurface() {
        for (k in 0 until 24) {
            val u = k / 24f - 0.5f
            val bottom = Vector3(0f, -0.5f, 0f).mul(place(u))
            assertEquals(r, Vector3(bottom.x, bottom.y, 0f).len(), trigEps, "u=$u")
            // Bottom edge corners lie on the same tangent line, i.e. the face is not tilted.
            val left = Vector3(-0.5f, -0.5f, 0f).mul(place(u))
            val right = Vector3(0.5f, -0.5f, 0f).mul(place(u))
            assertEquals(left.dst(bottom), right.dst(bottom), trigEps)
            assertEquals(r * r + 0.25f, left.len2() - left.z * left.z, 0.03f)
        }
    }

    @Test
    fun turningTheTunnelOnlyMovesTheBlockAlongTheSurface() {
        // Same block seen from two player positions: shape is identical, just rotated about the axis.
        val a = place(0.10f); val b = place(0.15f)
        val sizeA = Vector3(0.5f, 0.5f, 0f).mul(a).dst(Vector3(-0.5f, -0.5f, 0f).mul(a))
        val sizeB = Vector3(0.5f, 0.5f, 0f).mul(b).dst(Vector3(-0.5f, -0.5f, 0f).mul(b))
        assertEquals(sizeA, sizeB, eps)
    }
}
