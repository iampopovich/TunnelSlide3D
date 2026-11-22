package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObstacleTest {
    private val lane = 1f / 24f
    private val hw = GameConfig.PLAYER_HALF_WIDTH

    @Test
    fun pillarBlocksBothEndsOnlyWhileTheTubeIsClosed() {
        val p = Obstacle(0.1f, lane * 0.35f, 0f, 1f, 8f, 0, ObstacleKind.PILLAR)
        assertTrue(p.overlapsU(0.1f, hw, 0f, shape = 0f))
        assertTrue(p.overlapsU(0.6f, hw, 0f, shape = 0f))     // opposite wall
        assertFalse(p.overlapsU(0.6f, hw, 0f, shape = 1f))    // flat: just a tall column
        assertFalse(p.overlapsU(0.35f, hw, 0f, shape = 0f))   // passes over the axis, far above
    }

    private fun roller(phase: Float = 0f, dir: Int = 1, sweep: Int = 0) =
        Obstacle(0.5f * lane, lane * 0.5f, 0f, 1f, 1f, 0, ObstacleKind.ROLLER, phase, dir, sweep)

    private val tick = GameConfig.ROLLER_STEP
    private val resting = 0.01f
    private val rolling = GameConfig.ROLLER_REST * GameConfig.ROLLER_STEP + 0.05f

    @Test
    fun twoLaneSweepIsTheClassicTickTock() {
        val r = roller(sweep = 2)
        assertTrue(r.overlapsU(0.5f * lane, hw, resting, 0f))
        assertFalse(r.overlapsU(1.5f * lane, hw, resting, 0f))
        assertTrue(r.overlapsU(1.5f * lane, hw, rolling, 0f))          // mid-roll: both lanes
        assertTrue(r.overlapsU(1.5f * lane, hw, tick + resting, 0f))   // tock: on the next lane
        assertFalse(r.overlapsU(0.5f * lane, hw, tick + resting, 0f))
        assertTrue(r.overlapsU(0.5f * lane, hw, 2 * tick + resting, 0f))  // and back home
    }

    @Test
    fun circlingRollerGoesAllTheWayRoundAndWraps() {
        val r = roller(dir = -1)
        val out = FloatArray(3)
        for (k in 0 until 30) {
            r.rollerLanes(k * tick + resting, out)
            assertEquals(Obstacle.mod(-k.toFloat(), 24f), out[0], 1e-3f)
            assertEquals(0f, out[2])
        }
        // After 24 ticks it is home again.
        assertTrue(r.overlapsU(0.5f * lane, hw, 24 * tick + resting, 0f))
    }

    @Test
    fun sweepGoesEdgeToEdgeAndBack() {
        val out = FloatArray(3)
        val positions = (0 until 12).map { roller(sweep = 4).rollerLanes(it * tick + resting, out)[0].toInt() }
        assertEquals(listOf(0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 2, 1), positions)
    }

    @Test
    fun phaseShiftsTheClockByTicks() {
        val out = FloatArray(3)
        assertEquals(3f, roller(phase = 3f).rollerLanes(resting, out)[0])
    }

    @Test
    fun warningsOnlyForWhatThePlayerIsHeadingInto() {
        val block = Obstacle(0.5f * lane, lane * 0.45f, 0f, 1f, 1f, 0)
        assertEquals(0.5f * lane, block.threatAt(0.5f * lane, hw, 0f, 0f))
        assertEquals(null, block.threatAt(3.5f * lane, hw, 0f, 0f))

        val pillar = Obstacle(0.5f * lane, lane * 0.35f, 0f, 1f, 8f, 0, ObstacleKind.PILLAR)
        val far = TunnelSurface.wrapAbs(0.5f * lane + 0.5f)
        assertEquals(far, pillar.threatAt(far, hw, 0f, shape = 0f)!!, 1e-5f)   // opposite end
        assertEquals(null, pillar.threatAt(far, hw, 0f, shape = 1f))           // flat: no far end

        // A roller is judged where it will be when the player arrives.
        val r = roller(sweep = 2)
        assertEquals(null, r.threatAt(1.5f * lane, hw, resting, 0f))
        assertEquals(1.5f * lane, r.threatAt(1.5f * lane, hw, tick + resting, 0f)!!, 1e-5f)
    }
}
