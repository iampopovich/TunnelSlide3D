package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.exp

/** State of one run. Pure logic, no rendering, so it can be unit tested. */
class RunState {
    /** Absolute position across the tunnel, [0, 1). */
    var playerU = 0f
        private set
    /** Distance travelled along the tunnel. */
    var playerZ = 0f
        private set
    var speed = GameConfig.START_SPEED
        private set
    /** Current turn rate, revolutions per second (+ = right). */
    var turnRate = 0f
        private set
    var time = 0f
        private set
    var crashed = false
        private set

    val score: Int get() = playerZ.toInt()

    fun reset() {
        playerU = 0f; playerZ = 0f; speed = GameConfig.START_SPEED; turnRate = 0f; time = 0f
        crashed = false
    }

    /** @param steer -1 (full left) .. 1 (full right) */
    fun step(dt: Float, steer: Float, field: ObstacleField? = null) {
        if (crashed) return
        time += dt
        val target = MathUtils.clamp(steer, -1f, 1f) * GameConfig.MAX_TURN_RATE
        val k = 1f - exp(-GameConfig.TURN_RESPONSE * dt)
        turnRate += (target - turnRate) * k
        playerU = TunnelSurface.wrapAbs(playerU + turnRate * dt)
        speed = minOf(GameConfig.MAX_SPEED, speed + GameConfig.ACCELERATION * dt)
        val fromZ = playerZ
        playerZ += speed * dt
        if (field != null) {
            field.update(playerZ, speed)
            if (field.hits(playerU, fromZ, playerZ)) crashed = true
        }
    }
}
