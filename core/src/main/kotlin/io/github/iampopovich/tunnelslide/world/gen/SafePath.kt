package io.github.iampopovich.tunnelslide.world.gen

import io.github.iampopovich.tunnelslide.world.TunnelSurface

/**
 * The guaranteed route through the obstacles: a polyline of waypoints (z, u), sorted by z.
 * Between waypoints u changes linearly (the short way round). Patterns only ever add
 * waypoints whose slope the player can follow, and no block may overlap the route.
 */
class SafePath {
    private val zs = ArrayList<Float>()
    private val us = ArrayList<Float>()

    val lastZ: Float get() = zs.last()
    val lastU: Float get() = us.last()
    val isEmpty: Boolean get() = zs.isEmpty()

    fun clear() { zs.clear(); us.clear() }

    fun add(z: Float, u: Float) {
        require(zs.isEmpty() || z > zs.last()) { "waypoints must go forward: $z after ${zs.last()}" }
        zs.add(z); us.add(TunnelSurface.wrapAbs(u))
    }

    /** Route position at [z]; clamps to the ends. */
    fun uAt(z: Float): Float {
        if (z <= zs.first()) return us.first()
        if (z >= zs.last()) return us.last()
        val i = segment(z)
        val t = (z - zs[i]) / (zs[i + 1] - zs[i])
        return TunnelSurface.wrapAbs(us[i] + TunnelSurface.wrapRel(us[i + 1] - us[i]) * t)
    }

    /** du/dz of the route at [z]; 0 outside. */
    fun slopeAt(z: Float): Float {
        if (z <= zs.first() || z >= zs.last()) return 0f
        val i = segment(z)
        return TunnelSurface.wrapRel(us[i + 1] - us[i]) / (zs[i + 1] - zs[i])
    }

    /** Max |du/dz| over all segments (used by tests). */
    fun maxSlope(): Float = (0 until zs.size - 1).maxOfOrNull {
        kotlin.math.abs(TunnelSurface.wrapRel(us[it + 1] - us[it]) / (zs[it + 1] - zs[it]))
    } ?: 0f

    /** Drops waypoints far behind [z], always keeping the segment that contains it. */
    fun trimBefore(z: Float) {
        while (zs.size > 2 && zs[1] < z) { zs.removeAt(0); us.removeAt(0) }
    }

    private fun segment(z: Float): Int {
        var lo = 0; var hi = zs.size - 1
        while (hi - lo > 1) { val m = (lo + hi) ushr 1; if (zs[m] <= z) lo = m else hi = m }
        return lo
    }
}
