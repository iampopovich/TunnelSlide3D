package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneTrackTest {
    /** The shader formula, written out on the CPU. */
    private fun shaderShape(base: Float, ramps: FloatArray, dist: Float): Float {
        var s = base
        for (k in 0..1) {
            val len = ramps[k * 3 + 1]
            if (len > 0f) s += ramps[k * 3 + 2] * ((dist - ramps[k * 3]) / len).coerceIn(0f, 1f)
        }
        return s
    }

    @Test
    fun shaderRampsReproduceShapeAtEverywhereInView() {
        val zones = ZoneTrack(3L)
        val ramps = FloatArray(6)
        var z = 0f
        while (z < 8000f) {
            val base = zones.viewRamps(z, GameConfig.VIEW_DISTANCE, ramps)
            var d = 0f
            while (d <= GameConfig.VIEW_DISTANCE) {
                assertEquals(zones.shapeAt(z + d), shaderShape(base, ramps, d), 1e-3f, "z=$z d=$d")
                d += 5f
            }
            z += 7f
        }
    }

    @Test
    fun startsAsATubeAndLaterHasEpisodes() {
        val zones = ZoneTrack(5L)
        assertEquals(0f, zones.shapeAt(0f))
        assertFalse(zones.isOpen(100f))
        zones.ensure(10000f)
        val kinds = zones.all.map { it.kind }.toSet()
        assertTrue(ZoneKind.FLAT in kinds && ZoneKind.OUTSIDE in kinds, "$kinds")
    }

    @Test
    fun shapeIsContinuous() {
        val zones = ZoneTrack(8L)
        var prev = zones.shapeAt(0f)
        var z = 0.5f
        while (z < 6000f) {
            val s = zones.shapeAt(z)
            assertTrue(kotlin.math.abs(s - prev) < 0.05f, "jump at z=$z: $prev -> $s")
            prev = s; z += 0.5f
        }
    }
}
