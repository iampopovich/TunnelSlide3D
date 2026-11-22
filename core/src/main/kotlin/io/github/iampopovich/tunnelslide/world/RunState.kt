package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.abs
import kotlin.math.exp

enum class CrashReason { HIT, FELL }

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
    var crashReason: CrashReason? = null
        private set
    val crashed: Boolean get() = crashReason != null

    val score: Int get() = playerZ.toInt()

    fun reset() {
        playerU = 0f; playerZ = 0f; speed = GameConfig.START_SPEED; turnRate = 0f; time = 0f
        crashReason = null
    }

    /** @param steer -1 (full left) .. 1 (full right) */
    fun step(dt: Float, steer: Float, field: ObstacleField? = null) {
        if (crashed) return
        time += dt
        val target = MathUtils.clamp(steer, -1f, 1f) * GameConfig.MAX_TURN_RATE
        val k = 1f - exp(-GameConfig.TURN_RESPONSE * dt)
        turnRate += (target - turnRate) * k
        val oldU = playerU
        playerU = TunnelSurface.wrapAbs(playerU + turnRate * dt)
        speed = minOf(GameConfig.MAX_SPEED, speed + GameConfig.ACCELERATION * dt)
        val fromZ = playerZ
        playerZ += speed * dt
        if (field == null) return
        field.update(playerZ, speed)
        if (field.zones.isOpen(playerZ)) {
            // Crossing the seam of an open surface = going over the edge.
            val seam = field.zones.seamNear(playerZ, 0f)
            val a0 = TunnelSurface.wrapAbs(oldU - seam)
            val a1 = TunnelSurface.wrapAbs(playerU - seam)
            if (abs(a1 - a0) > 0.5f) { crashReason = CrashReason.FELL; return }
        }
        if (field.hits(playerU, fromZ, playerZ)) crashReason = CrashReason.HIT
    }
}
