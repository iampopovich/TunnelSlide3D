package io.github.iampopovich.tunnelslide.world.gen

import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.Autopilot
import io.github.iampopovich.tunnelslide.world.ObstacleField
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.TunnelSurface
import io.github.iampopovich.tunnelslide.world.ZoneKind
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
        for (seed in listOf(5L, 13L, 17L, 29L)) {
            drive(seed, 200f) { run, field ->
                val need = abs(field.path.slopeAt(run.playerZ)) * run.speed
                assertTrue(need <= GameConfig.MAX_TURN_RATE * GameConfig.REACH_SAFETY * 1.01f,
                    "seed $seed: route needs $need rev/s at z=${run.playerZ}")
            }
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
    fun routeKeepsAwayFromOpenEdges() {
        var checked = 0
        drive(17L, 200f) { run, field ->
            if (field.zones.isOpen(run.playerZ)) {
                val seam = field.zones.seamNear(run.playerZ, 0f)
                val rel = abs(TunnelSurface.wrapRel(field.path.uAt(run.playerZ) - seam))
                assertTrue(rel >= GameConfig.EDGE_MARGIN_LANES / GameConfig.LANES - 1e-4f, "route at the edge, z=${run.playerZ}")
                checked++
            }
        }
        assertTrue(checked > 0, "no open zone reached")
    }

    @Test
    fun steeringOverTheEdgeOfAFlatZoneIsAFall() {
        val run = RunState(); val field = ObstacleField(2L)
        field.zones.forcedFirst = ZoneKind.FLAT
        field.reset(2L)
        field.zones.forcedFirst = ZoneKind.FLAT
        field.update(0f, run.speed)
        repeat((60f / GameConfig.FIXED_STEP).toInt()) {
            if (run.crashed) return@repeat
            // Follow the route until the surface is open, then hold full right forever.
            val steer = if (field.zones.isOpen(run.playerZ)) 1f else Autopilot.steer(run, field)
            run.step(GameConfig.FIXED_STEP, steer, field)
        }
        assertTrue(run.crashed)
    }

    @Test
    fun botSurvivesTheWholeDifficultyRampOnManySeeds() {
        for (seed in 1L..20L) {
            val run = drive(seed, 200f)
            assertTrue(!run.crashed, "seed $seed crashed at z=${run.playerZ}")
        }
    }
}
