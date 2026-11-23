package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseCallTest {
    private val lane = 1f / GameConfig.LANES
    private val blockHalf = lane * 0.5f * GameConfig.BLOCK_WIDTH_FILL

    /** Player rides straight at u = 0 past one block whose side is [clearance] away. */
    private fun passBlock(clearance: Float): RunState {
        val run = RunState(); val field = ObstacleField(1L)
        field.update(0f, run.speed)
        val items = field.obstaclesForTest()
        items.clear()
        items.addLast(Obstacle(blockHalf + GameConfig.PLAYER_HALF_WIDTH + clearance, blockHalf, 30f, 2f, 1f, 0))
        repeat((3f / GameConfig.FIXED_STEP).toInt()) { if (!run.crashed) run.step(GameConfig.FIXED_STEP, 0f, field) }
        return run
    }

    @Test
    fun grazingABlockScoresACloseCall() {
        val run = passBlock(GameConfig.CLOSE_CALL_MARGIN * 0.5f)
        assertFalse(run.crashed)
        assertEquals(1, run.closeCalls)
        assertEquals(run.playerZ.toInt() + run.bonusPoints, run.score)
        assertTrue(run.bonusPoints > 0)
    }

    @Test
    fun aNormalPassIsNotACloseCall() {
        val run = passBlock(GameConfig.CLOSE_CALL_MARGIN * 1.5f)
        assertFalse(run.crashed)
        assertEquals(0, run.closeCalls)
    }

    @Test
    fun touchingIsACrashNotACloseCall() {
        val run = passBlock(-0.001f)
        assertTrue(run.crashed)
        assertEquals(0, run.closeCalls)
    }

    @Test
    fun rewardGrowsWithSpeed() {
        val run = RunState()
        assertEquals(GameConfig.CLOSE_CALL_POINTS.toInt(), run.closeCallPoints(GameConfig.START_SPEED))
        assertTrue(run.closeCallPoints(GameConfig.MAX_SPEED) > 4 * run.closeCallPoints(GameConfig.START_SPEED) - 2)
    }

    @Test
    fun theNeighbourLaneCentreIsNotACloseCall() {
        // Riding the middle of the lane next to a block is ordinary driving.
        // Block centre one lane away from the player: clearance = lane - block half - player half.
        val run = passBlock(lane - blockHalf - GameConfig.PLAYER_HALF_WIDTH)
        assertEquals(0, run.closeCalls)
    }
}
