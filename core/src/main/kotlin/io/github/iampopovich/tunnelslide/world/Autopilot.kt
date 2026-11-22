package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils

/** Dev/test bot: steers towards the gap of the next row. Proves the generator is passable. */
object Autopilot {
    fun steer(run: RunState, field: ObstacleField): Float {
        val target = field.gapAhead(run.playerZ) ?: return 0f
        val err = TunnelSurface.wrapRel(target - run.playerU)
        return MathUtils.clamp(err * 40f, -1f, 1f)
    }
}
