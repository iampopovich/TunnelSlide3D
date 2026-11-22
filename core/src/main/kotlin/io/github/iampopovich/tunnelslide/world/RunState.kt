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
    /** Distance travelled along the tunnel (accumulated in double: long runs must not drift). */
    private var zAcc = 0.0
    val playerZ: Float get() = zAcc.toFloat()
    var speed = GameConfig.START_SPEED
        private set
    /** Current turn rate, revolutions per second (+ = right). */
    var turnRate = 0f
        private set
    /** Game time = fixed steps taken; exact, so moving obstacles stay in sync with predictions. */
    private var steps = 0L
    private var dtSum = 0.0
    val time: Float get() = dtSum.toFloat()
    var crashReason: CrashReason? = null
        private set
    val crashed: Boolean get() = crashReason != null

    val score: Int get() = playerZ.toInt()

    fun reset() {
        playerU = 0f; zAcc = 0.0; speed = GameConfig.START_SPEED; turnRate = 0f; steps = 0L; dtSum = 0.0
        crashReason = null
    }

    /** @param steer -1 (full left) .. 1 (full right) */
    fun step(dt: Float, steer: Float, field: ObstacleField? = null) {
        if (crashed) return
        steps++
        dtSum += dt
        val target = MathUtils.clamp(steer, -1f, 1f) * GameConfig.MAX_TURN_RATE
        val k = 1f - exp(-GameConfig.TURN_RESPONSE * dt)
        turnRate += (target - turnRate) * k
        val oldU = playerU
        playerU = TunnelSurface.wrapAbs(playerU + turnRate * dt)
        // Speed is a closed-form function of time (no float accumulation), see Kinematics.
        speed = minOf(GameConfig.MAX_SPEED, (GameConfig.START_SPEED + GameConfig.ACCELERATION * dtSum).toFloat())
        val fromZ = playerZ
        zAcc += speed.toDouble() * dt
        if (field == null) return
        field.update(playerZ, speed)
        if (field.zones.isOpen(playerZ)) {
            // Crossing the seam of an open surface = going over the edge.
            val seam = field.zones.seamNear(playerZ, 0f)
            val a0 = TunnelSurface.wrapAbs(oldU - seam)
            val a1 = TunnelSurface.wrapAbs(playerU - seam)
            if (abs(a1 - a0) > 0.5f) { crashReason = CrashReason.FELL; return }
        }
        if (field.hits(playerU, fromZ, playerZ, time, field.zones.shapeAt(playerZ))) crashReason = CrashReason.HIT
    }
}
