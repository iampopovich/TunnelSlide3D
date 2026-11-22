package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.gen.Pattern
import io.github.iampopovich.tunnelslide.world.gen.PatternBuilder
import io.github.iampopovich.tunnelslide.world.gen.SafePath

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
    val zones = ZoneTrack(seed)
    /** Pattern names in generation order, for debugging and tests. */
    val history = ArrayList<Pattern>()

    val obstacles: Collection<Obstacle> get() = items
    /** Bumped whenever blocks are added, so renderers know when to rebuild their buffers. */
    var version = 0
        private set

    init { reset(seed) }

    fun reset(seed: Long) {
        rng.setSeed(seed)
        items.clear(); history.clear(); path.clear()
        version++
        zones.reset(seed xor 0x5EEDL)
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

    /** Dev override (TS_PATTERN): always generate this pattern. */
    var forcedPattern: Pattern? = null

    /** Dev override (TS_DIFFICULTY): fixed difficulty instead of the distance ramp. */
    var difficultyOverride: Float? = null

    /** Difficulty 0..1 as a function of distance travelled. */
    fun difficultyAt(z: Float): Float =
        difficultyOverride ?: MathUtils.clamp(z / GameConfig.DIFFICULTY_RAMP_DISTANCE, 0f, 1f)

    /**
     * True if the player, moving from [fromZ] to [toZ] at across-position [u],
     * touches any obstacle. Sweeping the z interval avoids tunnelling at high speed.
     */
    fun hits(u: Float, fromZ: Float, toZ: Float, time: Float = 0f, shape: Float = 0f): Boolean {
        for (o in items) {
            if (o.z > toZ + GameConfig.PLAYER_LENGTH) break   // sorted by z
            if (o.z + o.length < fromZ) continue
            if (o.overlapsU(u, GameConfig.PLAYER_HALF_WIDTH, time, shape)) return true
        }
        return false
    }

    /** One warning: the dangerous spot of an obstacle the player is heading into. */
    class Threat(var u: Float = 0f, var halfWidth: Float = 0f, var z: Float = 0f, var colorIndex: Int = 0)

    /**
     * Obstacles the player will hit if they keep [playerU], up to [ahead]. Moving obstacles
     * are checked at the moment the player gets to them (known in advance, see Kinematics).
     * Fills [out] (reusing its objects) and returns the count.
     */
    fun threats(playerU: Float, playerZ: Float, ahead: Float, shape: Float, out: ArrayList<Threat>): Int {
        var n = 0
        for (o in items) {
            if (o.z > playerZ + ahead) break
            if (o.z + o.length < playerZ) continue
            val at = o.threatAt(playerU, GameConfig.PLAYER_HALF_WIDTH, Kinematics.timeAt(o.z), shape) ?: continue
            if (n == out.size) out.add(Threat())
            out[n].apply { u = at; halfWidth = o.halfWidth; z = o.z; colorIndex = o.colorIndex }
            n++
        }
        return n
    }

    private fun spawnPattern(playerZ: Float, speed: Float) {
        val d = difficultyAt(path.lastZ)
        // Breathing space between patterns shrinks with difficulty.
        val pause = MathUtils.lerp(GameConfig.PATTERN_PAUSE_EASY, GameConfig.PATTERN_PAUSE_HARD, d)
        val pauseZ = path.lastZ + pause
        zones.ensure(pauseZ + PATTERN_MAX_LENGTH)
        PatternBuilder.claimSeams(zones, path.lastZ, pauseZ, path.lastU)
        path.add(pauseZ, path.lastU)

        // Reachability must hold at the speed the player will have at the END of the pattern
        // (speed only grows), so estimate it with some room for long patterns.
        val ahead = pauseZ - playerZ + PATTERN_MAX_LENGTH
        val arrivalSpeed = minOf(GameConfig.MAX_SPEED, speed + GameConfig.ACCELERATION * ahead / speed)
        val pattern = pick(d)
        val b = PatternBuilder(rng, path, d, arrivalSpeed, zones)
        pattern.build(b)
        history.add(pattern)

        val margin = GameConfig.PLAYER_HALF_WIDTH +
            b.laneW * MathUtils.lerp(GameConfig.ROUTE_MARGIN_EASY, GameConfig.ROUTE_MARGIN_HARD, d)
        b.blocks.filter { isClearOfRoute(it, margin) }
            .sortedBy { it.z }
            .forEach { insertSorted(it) }
        version++
    }

    private fun pick(d: Float): Pattern {
        forcedPattern?.let { return it }
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
            val pu = path.uAt(z)
            if (o.kind == ObstacleKind.ROLLER) {
                // Moving: check where it is when the player actually gets here, with a window.
                val t = Kinematics.timeAt(z)
                var dt = -GameConfig.ROUTE_TIME_WINDOW
                while (dt <= GameConfig.ROUTE_TIME_WINDOW) {
                    if (o.overlapsU(pu, margin, t + dt, 0f)) return false
                    dt += ROUTE_TIME_SAMPLE
                }
            } else if (o.mayCoverU(pu, margin)) {
                return false
            }
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
        private const val ROUTE_TIME_SAMPLE = 0.03f
        private const val PATTERN_MAX_LENGTH = 120f
        /** Extra z checked before/after a block: covers the turn lag of a real player. */
        private const val ROUTE_LOOK = 0.75f
    }
}
