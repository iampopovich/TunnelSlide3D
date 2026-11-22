package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.GameConfig

/** Dev/test bot: follows the generator's safe route. Proves every run is passable. */
object Autopilot {
    fun steer(run: RunState, field: ObstacleField): Float {
        // Position error is measured where the player is now; the route slope is taken a bit
        // ahead to compensate the turn-rate lag (1 / TURN_RESPONSE). Looking ahead for the
        // position as well would double-count and leave the bot permanently off the route.
        val err = TunnelSurface.wrapRel(field.path.uAt(run.playerZ) - run.playerU)
        val lookZ = run.playerZ + run.speed / GameConfig.TURN_RESPONSE
        val ff = field.path.slopeAt(lookZ) * run.speed / GameConfig.MAX_TURN_RATE
        return MathUtils.clamp(err * GAIN + ff, -1f, 1f)
    }

    private const val GAIN = 45f
}
