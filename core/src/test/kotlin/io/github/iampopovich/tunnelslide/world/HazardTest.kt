package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HazardTest {
    @Test
    fun hazardsStartLateAndEasyOnesFirst() {
        val track = HazardTrack(1L) { z -> (z / 6000f).coerceIn(0f, 1f) }
        assertNull(track.at(500f))
        track.ensure(30000f)
        val kinds = (0 until 30000 step 25).mapNotNull { track.at(it.toFloat())?.kind }.toSet()
        assertEquals(HazardKind.entries.toSet(), kinds)
        // Before difficulty unlocks them, only the gentle kinds appear.
        val early = (0 until 1300 step 10).mapNotNull { track.at(it.toFloat())?.kind }.toSet()
        assertTrue(early.all { it.minDifficulty <= 1300f / 6000f }, "$early")
    }

    @Test
    fun intensityFadesInAndOut() {
        val track = HazardTrack(2L) { 1f }
        track.ensure(5000f)
        val h = (0 until 5000).firstNotNullOf { track.at(it.toFloat()) }
        assertEquals(0f, track.intensity(h.start), 1e-4f)
        assertEquals(1f, track.intensity(h.start + GameConfig.HAZARD_RAMP + 1f), 1e-4f)
        assertEquals(0f, track.intensity(h.end - 0.01f), 1e-3f)
    }

    @Test
    fun foldEndsTheCurrentHazard() {
        val track = HazardTrack(3L) { 1f }
        track.ensure(5000f)
        val h = (0 until 5000).firstNotNullOf { track.at(it.toFloat()) }
        val z = h.start + 50f
        assertTrue(track.end(z))
        assertNull(track.at(z + GameConfig.HAZARD_RAMP + 1f))
    }

    @Test
    fun gravityWellPullsSideways() {
        val track = HazardTrack(4L) { 1f }
        track.forcedFirst = HazardKind.GRAVITY
        track.reset(4L)
        track.ensure(2000f)
        val h = track.at(200f)!!
        assertEquals(HazardKind.GRAVITY, h.kind)
        val pull = track.pullAt(h.start + GameConfig.HAZARD_RAMP + 5f)
        assertEquals(h.dir * GameConfig.GRAVITY_PULL * GameConfig.MAX_TURN_RATE, pull, 1e-5f)
    }

    @Test
    fun vortexWipesShieldsAndSlot() {
        val run = RunState(); val field = ObstacleField(3L)
        field.update(0f, run.speed)
        field.pickupsForTest().addFirst(Pickup(0f, 4f, BonusKind.VORTEX))
        field.pickupsForTest().addFirst(Pickup(0f, 0.5f, BonusKind.QUAKE))
        repeat(10) { run.step(GameConfig.FIXED_STEP, 0f, field) }
        assertEquals(BonusKind.QUAKE, run.slot)
        repeat(30) { run.step(GameConfig.FIXED_STEP, 0f, field) }
        assertNull(run.slot)
        assertEquals(RunEvent.VORTEX, run.event)
    }
}
