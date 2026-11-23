package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import io.github.iampopovich.tunnelslide.GameConfig

/** Danger zones of the original. They overlay the surface zones (tube/flat/outside). */
enum class HazardKind(val label: String, val color: Color, val minDifficulty: Float) {
    /** The tunnel is drawn in negative. */
    BLACKOUT("BLACKOUT", Color(0.12f, 0.12f, 0.16f, 1f), 0f),
    /** The obstacles are drawn in negative. */
    NEBULA("DARK NEBULA", Color(0.6f, 0.4f, 1f, 1f), 0.1f),
    /** Colour drains away: the whole scene turns black-and-white. */
    MONOCHROME("MONOCHROME", Color(0.85f, 0.85f, 0.85f, 1f), 0.05f),
    /** The whole scene flips between negative and positive on a beat. */
    NEGATIVE("NEGATIVE", Color(0.95f, 0.35f, 0.1f, 1f), 0.2f),
    /** Disco: everything cycles through colours, warning trails lose their meaning. */
    HUESHIFT("HUE SHIFT", Color(1f, 0.3f, 0.9f, 1f), 0.15f),
    /** Pulls the player sideways and cuts the view distance. */
    GRAVITY("GRAVITY WELL", Color(0.4f, 0.7f, 1f, 1f), 0.25f),
    /** Vortices on the track: touching one wipes your shields and bonus. */
    PLASMA("PLASMA CLOUD", Color(0.85f, 0.25f, 1f, 1f), 0.3f),
}

class Hazard(val kind: HazardKind, val start: Float, var end: Float, val dir: Int)

/** Schedule of hazards along the run; difficulty decides which kinds may appear. */
class HazardTrack(seed: Long, private val difficultyAt: (Float) -> Float) {
    private val rng = RandomXS128(seed)
    private val list = ArrayList<Hazard>()
    private var nextStart = 0f
    /** Dev override (TS_HAZARD): first hazard of this kind, soon after the start. */
    var forcedFirst: HazardKind? = null

    init { reset(seed) }

    fun reset(seed: Long) {
        rng.setSeed(seed)
        list.clear()
        nextStart = if (forcedFirst != null) 120f else GameConfig.HAZARD_FIRST + rng.nextFloat() * 400f
    }

    fun ensure(z: Float) {
        while (nextStart < z) {
            val d = difficultyAt(nextStart)
            val kinds = HazardKind.entries.filter { d >= it.minDifficulty }
            val kind = if (list.isEmpty() && forcedFirst != null) forcedFirst!! else kinds[rng.nextInt(kinds.size)]
            val len = MathUtils.lerp(GameConfig.HAZARD_MIN, GameConfig.HAZARD_MAX, rng.nextFloat())
            list.add(Hazard(kind, nextStart, nextStart + len, if (rng.nextBoolean()) 1 else -1))
            nextStart += len + MathUtils.lerp(GameConfig.HAZARD_GAP_MAX, GameConfig.HAZARD_GAP_MIN, d) * (0.7f + 0.6f * rng.nextFloat())
        }
    }

    fun at(z: Float): Hazard? {
        ensure(z)
        for (h in list) {
            if (h.start > z) return null
            if (z < h.end) return h
        }
        return null
    }

    /** 0..1: fades in over the first and out over the last HAZARD_RAMP units. */
    fun intensity(z: Float): Float {
        val h = at(z) ?: return 0f
        val r = GameConfig.HAZARD_RAMP
        return MathUtils.clamp(minOf((z - h.start) / r, (h.end - z) / r), 0f, 1f)
    }

    /** Hazard (if any) whose stretch overlaps [from, to]. */
    fun overlapping(from: Float, to: Float): Hazard? {
        ensure(to)
        return list.firstOrNull { it.start < to && it.end > from }
    }

    /** Space fold: the current hazard fades out starting at [z]. */
    fun end(z: Float): Boolean {
        val h = at(z) ?: return false
        h.end = minOf(h.end, z + GameConfig.HAZARD_RAMP)
        return true
    }

    /** Sideways pull of a gravity well at [z], in revolutions per second. */
    fun pullAt(z: Float): Float {
        val h = at(z) ?: return 0f
        if (h.kind != HazardKind.GRAVITY) return 0f
        return h.dir * GameConfig.GRAVITY_PULL * GameConfig.MAX_TURN_RATE * intensity(z)
    }
}
