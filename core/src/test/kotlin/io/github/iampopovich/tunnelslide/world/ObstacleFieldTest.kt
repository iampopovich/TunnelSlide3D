package io.github.iampopovich.tunnelslide.world

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObstacleFieldTest {
    @Test
    fun blocksAreSpawnedAheadAndDroppedBehind() {
        val field = ObstacleField(7L)
        field.update(0f, GameConfig.START_SPEED)
        assertTrue(field.obstacles.isNotEmpty())
        assertTrue(field.obstacles.all { it.z >= GameConfig.FIRST_ROW_DISTANCE })
        field.update(500f, GameConfig.START_SPEED)
        assertTrue(field.obstacles.all { it.z + it.length >= 498f })
    }

    @Test
    fun hitsOnlyWhenOverlappingInUAndZ() {
        val field = ObstacleField(3L)
        field.update(0f, GameConfig.START_SPEED)
        val o = field.obstacles.first()
        assertTrue(field.hits(o.u, o.z - 0.1f, o.z + 0.1f))
        assertFalse(field.hits(TunnelSurface.wrapAbs(o.u + 0.5f + 0.01f), o.z - 50f, o.z - 40f))
    }

    @Test
    fun sweepCatchesABlockSkippedInOneStep() {
        val field = ObstacleField(3L)
        field.update(0f, GameConfig.START_SPEED)
        val o = field.obstacles.first()
        // Jump over the whole block in one step: the swept interval must still hit it.
        assertTrue(field.hits(o.u, o.z - 1f, o.z + o.length + 1f))
    }

    @Test
    fun differentSeedsGiveDifferentLayouts() {
        val layouts = (1L..10L).map { seed ->
            ObstacleField(seed).apply { update(0f, GameConfig.START_SPEED) }
                .obstacles.take(6).map { (it.u * 1000).toInt() to (it.z * 10).toInt() }
        }
        assertTrue(layouts.toSet().size == layouts.size, "two seeds produced the same opening")
    }

    @Test
    fun obstaclesStaySortedByZ() {
        val field = ObstacleField(11L)
        field.update(0f, GameConfig.START_SPEED)
        val zs = field.obstacles.map { it.z }
        assertTrue(zs == zs.sorted())
    }
}
