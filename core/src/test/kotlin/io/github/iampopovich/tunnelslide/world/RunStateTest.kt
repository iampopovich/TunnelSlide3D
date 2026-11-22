package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertTrue

class RunStateTest {
    @Test
    fun movesForwardAndAccelerates() {
        val run = RunState()
        repeat(120) { run.step(GameConfig.FIXED_STEP, 0f) }
        assertTrue(run.playerZ > GameConfig.START_SPEED * 0.99f)
        assertTrue(run.speed > GameConfig.START_SPEED)
    }

    @Test
    fun fullRightTurnApproachesMaxRateAndWrapsU() {
        val run = RunState()
        repeat(1200) { run.step(GameConfig.FIXED_STEP, 1f) }
        assertTrue(run.turnRate > GameConfig.MAX_TURN_RATE * 0.99f)
        assertTrue(run.playerU in 0f..1f)
    }
}
