package io.github.iampopovich.tunnelslide.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneTrackTest {
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
    fun seamsAreSnappedToEvenLaneBoundaries() {
        val field = ObstacleField(4L)
        var z = 0f
        while (z < 9000f) { field.update(z, 30f); z += 50f }
        val seams = field.zones.all.mapNotNull { it.seam }
        assertTrue(seams.isNotEmpty())
        for (s in seams) {
            val k = s * 12f
            assertEquals(kotlin.math.round(k), k, 1e-3f, "seam $s")
        }
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
