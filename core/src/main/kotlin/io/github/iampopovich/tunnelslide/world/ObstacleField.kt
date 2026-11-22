package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.abs
import kotlin.math.floor

/**
 * Keeps obstacles in front of the player and answers collision queries.
 *
 * Batch-3 generator: rows of lane-aligned blocks with one guaranteed gap. The gap centre
 * does a random walk limited by how far the player can turn before the next row, so every
 * row is reachable from the previous one. Proper patterns come in batch 4.
 */
class ObstacleField(seed: Long) {
    private val rng = RandomXS128(seed)
    private val items = ArrayDeque<Obstacle>()
    private val gaps = ArrayDeque<Pair<Float, Float>>()   // (row z, gap centre u)
    private var nextRowZ = GameConfig.FIRST_ROW_DISTANCE
    private var gapU = 0f

    val obstacles: Collection<Obstacle> get() = items

    fun reset(seed: Long) {
        rng.setSeed(seed)
        items.clear(); gaps.clear()
        nextRowZ = GameConfig.FIRST_ROW_DISTANCE
        gapU = 0f
    }

    /** Spawns rows up to the view distance and drops the ones left behind. */
    fun update(playerZ: Float, speed: Float) {
        while (nextRowZ < playerZ + GameConfig.VIEW_DISTANCE) {
            spawnRow(nextRowZ, speed)
            nextRowZ += GameConfig.ROW_SPACING
        }
        while (items.isNotEmpty() && items.first().z + items.first().length < playerZ - 2f) items.removeFirst()
        while (gaps.isNotEmpty() && gaps.first().first < playerZ - 2f) gaps.removeFirst()
    }

    /** Centre of the gap in the nearest row whose z is ahead of [z], or null. */
    fun gapAhead(z: Float): Float? = gaps.firstOrNull { it.first + GameConfig.BLOCK_LENGTH >= z }?.second

    /**
     * True if the player, moving from [fromZ] to [toZ] at across-position [u],
     * touches any obstacle. Sweeping the z interval avoids tunnelling at high speed.
     */
    fun hits(u: Float, fromZ: Float, toZ: Float): Boolean {
        for (o in items) {
            if (o.z > toZ + GameConfig.PLAYER_LENGTH) break   // sorted by z
            if (o.z + o.length < fromZ) continue
            if (abs(TunnelSurface.wrapRel(o.u - u)) < o.halfWidth + GameConfig.PLAYER_HALF_WIDTH) return true
        }
        return false
    }

    private fun spawnRow(z: Float, speed: Float) {
        val lanes = GameConfig.LANES.toInt()
        val laneW = 1f / lanes
        // How far (in u) the player can comfortably turn between two rows.
        val reach = GameConfig.MAX_TURN_RATE * (GameConfig.ROW_SPACING / speed) * GameConfig.REACH_SAFETY
        gapU = TunnelSurface.wrapAbs(gapU + (rng.nextFloat() * 2f - 1f) * reach)
        gaps.addLast(z to gapU)

        val density = MathUtils.clamp(
            GameConfig.BASE_DENSITY + (speed - GameConfig.START_SPEED) * GameConfig.DENSITY_PER_SPEED,
            0f, GameConfig.MAX_DENSITY,
        )
        val gapHalf = GameConfig.GAP_HALF_LANES * laneW
        for (lane in 0 until lanes) {
            val laneU = (lane + 0.5f) * laneW
            if (abs(TunnelSurface.wrapRel(laneU - gapU)) < gapHalf + laneW * 0.5f) continue
            if (rng.nextFloat() > density) continue
            items.addLast(
                Obstacle(
                    u = laneU,
                    halfWidth = laneW * 0.5f * GameConfig.BLOCK_WIDTH_FILL,
                    z = z,
                    length = GameConfig.BLOCK_LENGTH,
                    height = GameConfig.BLOCK_HEIGHT,
                    colorIndex = floor(rng.nextFloat() * BLOCK_COLORS).toInt(),
                ),
            )
        }
    }

    companion object {
        const val BLOCK_COLORS = 4
    }
}
