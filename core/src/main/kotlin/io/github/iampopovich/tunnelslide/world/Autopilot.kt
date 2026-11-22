package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils

/**
 * Dev/test bot: pure pursuit of the generator's safe route. Proves every run is passable.
 *
 * It aims at the route point [LEAD_S] seconds ahead with a proportional gain. On a straight
 * slope this leaves no steady offset when LEAD_S = 1 / (GAIN * MAX_TURN_RATE); a separate
 * slope feed-forward was tried and failed on zigzags shorter than its look-ahead.
 */
object Autopilot {
    fun steer(run: RunState, field: ObstacleField): Float {
        val target = field.path.uAt(run.playerZ + run.speed * LEAD_S)
        return MathUtils.clamp(TunnelSurface.wrapRel(target - run.playerU) * GAIN, -1f, 1f)
    }

    private const val GAIN = 45f
    private const val LEAD_S = 0.04f
}
