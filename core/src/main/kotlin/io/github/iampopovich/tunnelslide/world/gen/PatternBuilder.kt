package io.github.iampopovich.tunnelslide.world.gen

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.Obstacle
import io.github.iampopovich.tunnelslide.world.ObstacleKind
import io.github.iampopovich.tunnelslide.world.TunnelSurface
import io.github.iampopovich.tunnelslide.world.ZoneTrack

/**
 * Small DSL used by patterns. A pattern first lays out the route with [go], then drops
 * blocks anywhere in its z range. [ObstacleField] later removes every block that touches
 * the route, so a pattern can be as dense as it likes and still always be passable.
 */
class PatternBuilder(
    val rng: RandomXS128,
    val path: SafePath,
    /** 0 = easiest, 1 = hardest. */
    val difficulty: Float,
    val speed: Float,
    private val zones: ZoneTrack,
) {
    val blocks = ArrayList<Obstacle>()
    val startZ = path.lastZ
    var z = path.lastZ
        private set
    var u = path.lastU
        private set

    /** Fastest route drift (u per unit of z) the player can follow at this speed. */
    val maxSlope = GameConfig.MAX_TURN_RATE / speed * GameConfig.REACH_SAFETY
    val laneW = 1f / LANES
    var color = rng.nextInt(BLOCK_COLORS)

    /**
     * Advances the route by [dz] while drifting by [du] (clamped to what is reachable).
     * Before and on open surfaces the route also moves away from the edges - early enough
     * and never faster than the player can turn.
     */
    fun go(dz: Float, du: Float = 0f) {
        val limit = maxSlope * dz
        val fromZ = z
        z += dz
        val margin = GameConfig.EDGE_MARGIN_LANES * laneW
        // Distance needed to cross the whole edge band, with room to spare.
        val warn = margin / maxSlope * 2f
        claimSeams(zones, fromZ, z + warn, u)
        var next = TunnelSurface.wrapAbs(u + MathUtils.clamp(du, -limit, limit))
        if (isOpenWithin(fromZ, z + warn)) {
            val safe = awayFromEdge(next, u, zones.seamNear(fromZ, z + warn - fromZ), margin)
            if (safe != next) {
                next = TunnelSurface.wrapAbs(u + MathUtils.clamp(TunnelSurface.wrapRel(safe - u), -limit, limit))
            }
        }
        u = next
        path.add(z, u)
    }

    private fun isOpenWithin(from: Float, to: Float): Boolean {
        var z = from
        while (z < to) { if (zones.isOpen(z)) return true; z += 2f }
        return zones.isOpen(to)
    }

    /**
     * Nearest safe position for [next]. The side of the seam is taken from the CURRENT
     * route point [current]: deciding by [next] would let a drift that overshoots the seam
     * pick the far side, and the route would then cross the edge on its way there.
     */
    private fun awayFromEdge(next: Float, current: Float, seam: Float, margin: Float): Float {
        val side = if (TunnelSurface.wrapRel(current - seam) >= 0f) 1f else -1f
        val rel = TunnelSurface.wrapRel(next - seam)
        val onSide = rel * side >= 0f
        if (onSide && kotlin.math.abs(rel) >= margin) return next
        return TunnelSurface.wrapAbs(seam + side * margin)
    }

    /**
     * Short straight stretch so the player can line up before a wall: steering reacts with
     * a lag of 1 / TURN_RESPONSE, so a wall right at a bend of the route is unfair.
     */
    fun settle() = go(speed * GameConfig.SETTLE_TIME)

    /** Max drift over [dz]. */
    fun reach(dz: Float) = maxSlope * dz

    fun randomDrift(dz: Float, share: Float = 1f) = (rng.nextFloat() * 2f - 1f) * reach(dz) * share

    fun lane(index: Int): Float = TunnelSurface.wrapAbs((index + 0.5f) * laneW)

    /** Lane index closest to an absolute u. */
    fun laneOf(u: Float): Int = MathUtils.floor(TunnelSurface.wrapAbs(u) * LANES) % LANES

    fun block(laneIndex: Int, z: Float, length: Float = GameConfig.BLOCK_LENGTH, height: Float = GameConfig.BLOCK_HEIGHT) {
        val idx = ((laneIndex % LANES) + LANES) % LANES
        blocks.add(Obstacle(lane(idx), laneW * 0.5f * GameConfig.BLOCK_WIDTH_FILL, z, length, height, color))
    }

    /** Full-span pillar standing on [laneIndex] (in the tube it reaches the opposite wall). */
    fun pillar(laneIndex: Int, z: Float, length: Float = GameConfig.PILLAR_LENGTH) {
        val idx = ((laneIndex % LANES) + LANES) % LANES
        blocks.add(Obstacle(lane(idx), laneW * 0.5f * GameConfig.PILLAR_WIDTH_FILL, z, length,
            2f * GameConfig.TUNNEL_RADIUS, color, ObstacleKind.PILLAR))
    }

    /**
     * Tick-tock roller with its home on [laneIndex]: circles the whole tunnel in [dir]
     * when [sweepLanes] is 0, otherwise sweeps back and forth over that many lanes.
     */
    fun roller(laneIndex: Int, z: Float, phase: Float, dir: Int = 1, sweepLanes: Int = 0) {
        val idx = ((laneIndex % LANES) + LANES) % LANES
        val side = laneW * TunnelSurface.TAU * GameConfig.TUNNEL_RADIUS   // cube side in world units
        blocks.add(Obstacle(lane(idx), laneW * 0.5f, z, side, side, color, ObstacleKind.ROLLER, phase, dir, sweepLanes))
    }

    /** Kind of surface the pattern is built on (where the route currently is). */
    fun zoneKindHere() = zones.zoneAt(z).kind

    /** Seam lane index of the open zone around the route, or null in a closed tube. */
    fun seamLaneHere(): Int? {
        if (zoneKindHere() != io.github.iampopovich.tunnelslide.world.ZoneKind.FLAT) return null
        return MathUtils.round(zones.seamNear(z, 0f) * LANES) % LANES
    }

    /** A full ring of blocks; the route will carve its gap. */
    fun ring(z: Float, length: Float = GameConfig.WALL_LENGTH) {
        for (l in 0 until LANES) block(l, z, length)
    }

    fun chance(p: Float) = rng.nextFloat() < p

    companion object {
        val LANES = GameConfig.LANES.toInt()

        /** Zones the route enters in [fromZ, toZ] get their seam opposite the route. */
        fun claimSeams(zones: ZoneTrack, fromZ: Float, toZ: Float, routeU: Float) {
            zones.ensure(toZ)
            for (zone in zones.all) {
                if (zone.start > toZ) break
                if (zone.seam == null && zone.start <= toZ && zone.end > fromZ) {
                    // Snap to an even lane boundary: the panel pattern is drawn relative to
                    // the seam and repeats every 2 lanes, so the switch stays invisible.
                    val lanes2 = LANES / 2f
                    zone.seam = TunnelSurface.wrapAbs(MathUtils.round(TunnelSurface.wrapAbs(routeU + 0.5f) * lanes2) / lanes2)
                }
            }
        }
        const val BLOCK_COLORS = 4

    }
}
