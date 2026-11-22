package io.github.iampopovich.tunnelslide.world.gen

import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.Autopilot
import io.github.iampopovich.tunnelslide.world.ObstacleField
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.TunnelSurface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {
    /** Drives a bot for [seconds] and calls [check] every step. */
    private fun drive(seed: Long, seconds: Float, check: (RunState, ObstacleField) -> Unit = { _, _ -> }): RunState {
        val run = RunState(); val field = ObstacleField(seed)
        field.update(0f, run.speed)
        repeat((seconds / GameConfig.FIXED_STEP).toInt()) {
            if (!run.crashed) {
                run.step(GameConfig.FIXED_STEP, Autopilot.steer(run, field), field)
                check(run, field)
            }
        }
        return run
    }

    @Test
    fun routeIsNeverBlocked() {
        drive(9L, 60f) { run, field ->
            for (o in field.obstacles) {
                if (o.z > run.playerZ + 1f) break
                if (o.z + o.length < run.playerZ || o.z > run.playerZ + 0.5f) continue
                val gap = abs(TunnelSurface.wrapRel(o.u - field.path.uAt(run.playerZ)))
                assertTrue(gap >= o.halfWidth + GameConfig.PLAYER_HALF_WIDTH, "route blocked at z=${run.playerZ}")
            }
        }
    }

    @Test
    fun routeNeverTurnsFasterThanThePlayerCan() {
        drive(13L, 120f) { run, field ->
            val need = abs(field.path.slopeAt(run.playerZ)) * run.speed
            assertTrue(need <= GameConfig.MAX_TURN_RATE * GameConfig.REACH_SAFETY * 1.001f,
                "route needs $need rev/s at z=${run.playerZ}")
        }
    }

    @Test
    fun difficultyUnlocksAllPatterns() {
        val field = ObstacleField(21L)
        var z = 0f
        while (z < 20000f) { field.update(z, GameConfig.MAX_SPEED); z += 100f }
        assertEquals(Pattern.entries.toSet(), field.history.toSet())
        // Hard patterns do not appear at the very start.
        val early = ObstacleField(21L).apply { update(0f, GameConfig.START_SPEED) }.history
        assertTrue(early.none { it == Pattern.CHANNEL || it == Pattern.SPIRAL || it == Pattern.GATES }, "$early")
    }

    @Test
    fun botSurvivesTheWholeDifficultyRampOnManySeeds() {
        for (seed in 1L..20L) {
            val run = drive(seed, 200f)
            assertTrue(!run.crashed, "seed $seed crashed at z=${run.playerZ}")
        }
    }
}
