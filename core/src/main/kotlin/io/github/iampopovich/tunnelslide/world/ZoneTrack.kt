package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig

enum class ZoneKind(val shape: Float) { TUBE(0f), FLAT(1f), OUTSIDE(2f) }

/**
 * A stretch of the run with one surface shape. While the player covers the first
 * [GameConfig.ZONE_TRANSITION] units of a zone, the WHOLE visible surface morphs from the
 * previous shape in real time (the shape is a function of the player's position only). [seam] is where the ribbon is cut when
 * the surface is open; it is chosen when the route first reaches the zone.
 */
class Zone(val kind: ZoneKind, val start: Float, val end: Float, val fromShape: Float) {
    var seam: Float? = null
}

/** Schedule of surface shapes along the run: long tubes with flat / outside episodes. */
class ZoneTrack(seed: Long) {
    private val rng = RandomXS128(seed)
    private val zones = ArrayList<Zone>()
    /** Dev override (TS_ZONE): force the first special zone kind and bring it close. */
    var forcedFirst: ZoneKind? = null

    init { reset(seed) }

    fun reset(seed: Long) {
        rng.setSeed(seed)
        zones.clear()
    }

    val all: List<Zone> get() = zones

    /** Makes sure zones cover everything up to [z]. */
    fun ensure(z: Float) {
        if (zones.isEmpty()) {
            val firstEnd = if (forcedFirst != null) 110f
            else GameConfig.ZONE_FIRST_SPECIAL + rng.nextFloat() * 300f
            zones.add(Zone(ZoneKind.TUBE, Float.NEGATIVE_INFINITY, firstEnd, 0f))
        }
        while (zones.last().end < z) {
            val prev = zones.last()
            val kind = nextKind(prev.kind)
            val len = if (kind == ZoneKind.TUBE) {
                MathUtils.lerp(GameConfig.ZONE_TUBE_MIN, GameConfig.ZONE_TUBE_MAX, rng.nextFloat())
            } else {
                MathUtils.lerp(GameConfig.ZONE_SPECIAL_MIN, GameConfig.ZONE_SPECIAL_MAX, rng.nextFloat())
            }
            val zone = Zone(kind, prev.end, prev.end + len, prev.kind.shape)
            // Reuse the seam while the surface stays open-ish; a tube hides any seam change.
            if (prev.kind != ZoneKind.TUBE) zone.seam = prev.seam
            zones.add(zone)
        }
    }

    private fun nextKind(prev: ZoneKind): ZoneKind {
        if (prev == ZoneKind.TUBE) {
            forcedFirst?.let { forced -> if (zones.size == 1) return forced }
            return if (rng.nextBoolean()) ZoneKind.FLAT else ZoneKind.OUTSIDE
        }
        // After an episode: usually back to the tube, sometimes straight into the other one.
        if (rng.nextFloat() < GameConfig.ZONE_CHAIN_CHANCE) {
            return if (prev == ZoneKind.FLAT) ZoneKind.OUTSIDE else ZoneKind.FLAT
        }
        return ZoneKind.TUBE
    }

    fun zoneAt(z: Float): Zone {
        ensure(z)
        var lo = 0; var hi = zones.size - 1
        while (lo < hi) {
            val m = (lo + hi + 1) ushr 1
            if (zones[m].start <= z) lo = m else hi = m - 1
        }
        return zones[lo]
    }

    /** Surface shape when the player is at [z] (0 tube .. 1 flat .. 2 outside). */
    fun shapeAt(z: Float): Float {
        val zone = zoneAt(z)
        val t = (z - zone.start) / GameConfig.ZONE_TRANSITION
        return if (t >= 1f) zone.kind.shape else MathUtils.lerp(zone.fromShape, zone.kind.shape, maxOf(t, 0f))
    }

    /** True where the surface has edges you can fall off. */
    fun isOpen(z: Float): Boolean {
        val s = shapeAt(z)
        return s > OPEN_FROM && s < OPEN_TO
    }

    /**
     * Seam that matters around [z]: an open (flat/outside) zone overlapping [z, z + ahead]
     * wins, otherwise the zone at [z]. Tube zones after an episode inherit its seam, so the
     * closing transition keeps the right cut.
     */
    fun seamNear(z: Float, ahead: Float): Float {
        ensure(z + ahead)
        for (zone in zones) {
            if (zone.start > z + ahead) break
            if (zone.end <= z) continue
            if (zone.kind != ZoneKind.TUBE && zone.seam != null) return zone.seam!!
        }
        return zoneAt(z).seam ?: 0f
    }

    companion object {
        const val OPEN_FROM = 0.35f
        const val OPEN_TO = 1.65f
    }
}
