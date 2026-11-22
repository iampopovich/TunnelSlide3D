package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class KinematicsTest {
    @Test
    fun predictedArrivalMatchesTheSimulation() {
        val run = RunState()
        repeat((600f / GameConfig.FIXED_STEP).toInt()) {   // 10 minutes, well past MAX_SPEED
            run.step(GameConfig.FIXED_STEP, 0f)
            if (it % 600 == 0) {
                // The generator checks moving obstacles within ROUTE_TIME_WINDOW of this prediction.
                assertEquals(run.time, Kinematics.timeAt(run.playerZ), 0.01f, "at z=${run.playerZ}")
            }
        }
    }
}
