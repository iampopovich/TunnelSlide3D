package io.github.iampopovich.tunnelslide.ui

/**
 * Difficulty presets. Difficulty in the generator is 0..1; a level sets where it starts and
 * how fast it ramps up (ramp distance multiplier). Speed is the same for all levels: the
 * moving-obstacle prediction relies on one fixed speed curve.
 */
enum class Level(val label: String, val start: Float, val rampScale: Float) {
    EASY("EASY", 0f, 1.8f),
    NORMAL("NORMAL", 0f, 1f),
    HARD("HARD", 0.35f, 0.75f),
    EXTREME("EXTREME", 0.7f, 0.5f),
    /** Start difficulty chosen in the settings. */
    CUSTOM("CUSTOM", 0f, 1f),
}

/** Everything the player can change; persisted by the game through libGDX Preferences. */
class Settings {
    var level = Level.NORMAL
    /** CUSTOM level start difficulty, 0..1 in steps of 0.1. */
    var customStart = 0.5f
    var mono = false
    var sound = true
    /** Accelerometer reading (screen-right axis, sign as used by steering) meaning "straight". */
    var neutral = 0f

    fun startDifficulty(): Float = if (level == Level.CUSTOM) customStart else level.start

    /** Difficulty 0..1 at distance [z] for a base ramp distance [ramp]. */
    fun difficultyAt(z: Float, ramp: Float): Float {
        val s = startDifficulty()
        val k = (z / (ramp * level.rampScale)).coerceIn(0f, 1f)
        return s + (1f - s) * k
    }
}
