package io.github.iampopovich.tunnelslide.input

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertTrue

class TiltFilterTest {
    private val dt = 1f / 60f

    @Test
    fun followsARealTiltQuickly() {
        val f = TiltFilter()
        var frames = 1
        while (f.update(0.8f, dt) < 0.8f * 0.9f) frames++
        assertTrue(frames * dt <= 0.06f, "90% of a tilt after ${frames * dt}s")
    }

    @Test
    fun smoothsJitterAtRest() {
        val f = TiltFilter()
        var peak = 0f
        for (i in 0 until 600) peak = maxOf(peak, abs(f.update(if (i % 2 == 0) 0.03f else -0.03f, dt)))
        assertTrue(peak < 0.02f, "jitter leaks through: $peak")
    }

    @Test
    fun tiltToTurnRateIsQuick() {
        // Whole chain on a step tilt: filter + turn-rate response reach 90% within 0.15 s
        // (was ~0.35 s with the fixed filter and TURN_RESPONSE 12).
        val f = TiltFilter()
        var turn = 0f
        var t = 0f
        while (turn < 0.9f * GameConfig.MAX_TURN_RATE) {
            val target = f.update(1f, dt) * GameConfig.MAX_TURN_RATE
            turn += (target - turn) * (1f - exp(-GameConfig.TURN_RESPONSE * dt))
            t += dt
        }
        assertTrue(t <= 0.15f, "90% turn rate after ${t}s")
    }
}
