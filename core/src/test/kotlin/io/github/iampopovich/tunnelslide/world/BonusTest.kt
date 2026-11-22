package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BonusTest {
    private fun driveUntil(
        run: RunState, field: ObstacleField, maxSeconds: Float,
        steer: () -> Float = { Autopilot.steer(run, field) }, stop: () -> Boolean,
    ) {
        repeat((maxSeconds / GameConfig.FIXED_STEP).toInt()) {
            if (run.crashed || stop()) return
            run.step(GameConfig.FIXED_STEP, steer(), field)
        }
    }

    @Test
    fun bonusFieldsLieOnTheRouteAndGetCollected() {
        val run = RunState(); val field = ObstacleField(5L)
        field.update(0f, run.speed)
        var collected = 0
        repeat((120f / GameConfig.FIXED_STEP).toInt()) {
            if (run.crashed) return@repeat
            run.step(GameConfig.FIXED_STEP, Autopilot.steer(run, field), field)
            if (run.event == RunEvent.PICKUP) { collected++; run.event = null }
        }
        assertTrue(collected >= 3, "bot following the route picked up only $collected bonuses")
    }

    @Test
    fun shieldAbsorbsOneCrash() {
        val run = RunState(); val field = ObstacleField(7L)
        field.update(0f, run.speed)
        // Give a shield by driving over a field placed right under the player.
        field.pickupsForTest().addFirst(Pickup(0f, 1f, BonusKind.SHIELD))
        run.step(GameConfig.FIXED_STEP, 0f, field)
        repeat(30) { run.step(GameConfig.FIXED_STEP, 0f, field) }
        assertEquals(1, run.shields)
        // Drive straight (no steering) into whatever comes: the first hit only costs the shield.
        driveUntil(run, field, 60f, steer = { 0f }) { run.shields == 0 }
        assertEquals(0, run.shields)
        assertFalse(run.crashed)
    }

    @Test
    fun quakeClearsEverythingInSight() {
        val run = RunState(); val field = ObstacleField(3L)
        field.update(0f, run.speed)
        field.pickupsForTest().addFirst(Pickup(0f, 1f, BonusKind.QUAKE))
        repeat(40) { run.step(GameConfig.FIXED_STEP, 0f, field) }
        assertEquals(BonusKind.QUAKE, run.slot)
        assertTrue(field.obstacles.any { it.z < run.playerZ + GameConfig.VIEW_DISTANCE })
        assertTrue(run.activate(field))
        assertTrue(field.obstacles.none { it.z + it.length >= run.playerZ && it.z <= run.playerZ + GameConfig.VIEW_DISTANCE })
        assertNull(run.slot)
    }

    @Test
    fun timeDilationSlowsTheClockForAWhile() {
        val run = RunState(); val field = ObstacleField(3L)
        field.update(0f, run.speed)
        field.pickupsForTest().addFirst(Pickup(0f, 1f, BonusKind.SLOWMO))
        repeat(40) { run.step(GameConfig.FIXED_STEP, 0f, field) }
        assertTrue(run.activate(field))
        assertEquals(GameConfig.SLOWMO_SCALE, run.timeScale)
        repeat((GameConfig.SLOWMO_DURATION / GameConfig.FIXED_STEP).toInt() + 2) { run.step(GameConfig.FIXED_STEP, 0f) }
        assertEquals(1f, run.timeScale)
    }

    @Test
    fun foldClosesAnOpenSurfaceAndIsKeptWhenThereIsNothingToFold() {
        val run = RunState(); val field = ObstacleField(2L)
        field.zones.forcedFirst = ZoneKind.FLAT
        field.reset(2L)
        field.update(0f, run.speed)
        field.pickupsForTest().addFirst(Pickup(0f, 1f, BonusKind.FOLD))
        repeat(40) { run.step(GameConfig.FIXED_STEP, Autopilot.steer(run, field), field) }
        assertEquals(BonusKind.FOLD, run.slot)
        assertFalse(run.activate(field))          // still in the tube: nothing to fold, bonus kept
        assertEquals(BonusKind.FOLD, run.slot)
        driveUntil(run, field, 30f) { field.zones.shapeAt(run.playerZ) > 0.9f }
        assertTrue(run.activate(field))
        val z = run.playerZ
        driveUntil(run, field, 10f) { run.playerZ > z + GameConfig.ZONE_TRANSITION + 1f }
        assertEquals(0f, field.zones.shapeAt(run.playerZ))
    }
}
