package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.gen.Pattern
import io.github.iampopovich.tunnelslide.world.gen.PatternBuilder
import io.github.iampopovich.tunnelslide.world.gen.SafePath
import kotlin.math.abs

/**
 * Keeps obstacles in front of the player and answers collision queries.
 *
 * Generation: patterns are chained one after another along a [SafePath]. After a pattern
 * is built, every block that touches the route (plus a difficulty-dependent margin) is
 * removed, so the run is always passable no matter how dense the pattern is.
 */
class ObstacleField(seed: Long) {
    private val rng = RandomXS128(seed)
    private val items = ArrayDeque<Obstacle>()
    val path = SafePath()
    /** Pattern names in generation order, for debugging and tests. */
    val history = ArrayList<Pattern>()

    val obstacles: Collection<Obstacle> get() = items

    init { reset(seed) }

    fun reset(seed: Long) {
        rng.setSeed(seed)
        items.clear(); history.clear(); path.clear()
        // Straight run-up from the start; the first route point can be anywhere,
        // there is enough distance to reach it.
        path.add(0f, 0f)
        path.add(GameConfig.FIRST_ROW_DISTANCE * 0.5f, 0f)
        path.add(GameConfig.FIRST_ROW_DISTANCE, rng.nextFloat() * 0.3f - 0.15f)
    }

    /** Spawns patterns up to the view distance and drops what is left behind. */
    fun update(playerZ: Float, speed: Float) {
        while (path.lastZ < playerZ + GameConfig.VIEW_DISTANCE + 20f) spawnPattern(playerZ, speed)
        while (items.isNotEmpty() && items.first().z + items.first().length < playerZ - 2f) items.removeFirst()
        path.trimBefore(playerZ - 5f)
    }

    /** Dev override (TS_DIFFICULTY): fixed difficulty instead of the distance ramp. */
    var difficultyOverride: Float? = null

    /** Difficulty 0..1 as a function of distance travelled. */
    fun difficultyAt(z: Float): Float =
        difficultyOverride ?: MathUtils.clamp(z / GameConfig.DIFFICULTY_RAMP_DISTANCE, 0f, 1f)

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

    private fun spawnPattern(playerZ: Float, speed: Float) {
        val d = difficultyAt(path.lastZ)
        // Breathing space between patterns shrinks with difficulty.
        val pause = MathUtils.lerp(GameConfig.PATTERN_PAUSE_EASY, GameConfig.PATTERN_PAUSE_HARD, d)
        val pauseZ = path.lastZ + pause
        path.add(pauseZ, path.lastU)

        // Reachability must hold at the speed the player will have at the END of the pattern
        // (speed only grows), so estimate it with some room for long patterns.
        val ahead = pauseZ - playerZ + PATTERN_MAX_LENGTH
        val arrivalSpeed = minOf(GameConfig.MAX_SPEED, speed + GameConfig.ACCELERATION * ahead / speed)
        val pattern = pick(d)
        val b = PatternBuilder(rng, path, d, arrivalSpeed)
        pattern.build(b)
        history.add(pattern)

        val margin = GameConfig.PLAYER_HALF_WIDTH +
            b.laneW * MathUtils.lerp(GameConfig.ROUTE_MARGIN_EASY, GameConfig.ROUTE_MARGIN_HARD, d)
        b.blocks.filter { isClearOfRoute(it, margin) }
            .sortedBy { it.z }
            .forEach { insertSorted(it) }
    }

    private fun pick(d: Float): Pattern {
        val options = Pattern.entries
        val total = options.sumOf { it.weight(d).toDouble() }.toFloat()
        var r = rng.nextFloat() * total
        for (p in options) {
            r -= p.weight(d)
            if (r <= 0f) return p
        }
        return Pattern.SCATTER
    }

    /** Samples the route along the block's length (plus the player's own length). */
    private fun isClearOfRoute(o: Obstacle, margin: Float): Boolean {
        var z = o.z - GameConfig.PLAYER_LENGTH - ROUTE_LOOK
        val end = o.z + o.length + ROUTE_LOOK
        while (z <= end) {
            if (abs(TunnelSurface.wrapRel(o.u - path.uAt(z))) < o.halfWidth + margin) return false
            z += ROUTE_SAMPLE
        }
        return true
    }

    /** Patterns may overlap in z only at their joints; keep the deque sorted for [hits]. */
    private fun insertSorted(o: Obstacle) {
        var i = items.size
        while (i > 0 && items[i - 1].z > o.z) i--
        items.add(i, o)
    }

    companion object {
        const val BLOCK_COLORS = PatternBuilder.BLOCK_COLORS
        private const val ROUTE_SAMPLE = 0.25f
        private const val PATTERN_MAX_LENGTH = 120f
        /** Extra z checked before/after a block: covers the turn lag of a real player. */
        private const val ROUTE_LOOK = 0.75f
    }
}
