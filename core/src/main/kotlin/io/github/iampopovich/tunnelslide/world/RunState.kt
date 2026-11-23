package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.abs
import kotlin.math.exp

enum class CrashReason { HIT, FELL }

enum class RunEvent { PICKUP, SHIELD_HIT, QUAKE, SLOWMO, FOLD, VORTEX }

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

    // --- Bonuses ---
    /** Shields left; each one absorbs one crash into an obstacle (not a fall). */
    var shields = 0
        private set
    /** The tap-activated bonus waiting in the single slot. */
    var slot: BonusKind? = null
        private set
    /** Game seconds of time dilation left. */
    var slowLeft = 0f
        private set
    /** Game clock rate: the loop feeds the simulation `frame * timeScale` seconds. */
    val timeScale: Float get() = if (slowLeft > 0f) GameConfig.SLOWMO_SCALE else 1f
    /** Last thing that happened, for effects and sounds; cleared by the reader. */
    var event: RunEvent? = null

    fun reset() {
        playerU = 0f; zAcc = 0.0; speed = GameConfig.START_SPEED; turnRate = 0f; steps = 0L; dtSum = 0.0
        crashReason = null
        shields = 0; slot = null; slowLeft = 0f; event = null
    }

    /**
     * Uses the bonus in the slot. Returns true if something happened (a fold with nothing to
     * fold keeps the bonus).
     */
    fun activate(field: ObstacleField): Boolean {
        if (crashed) return false
        when (slot ?: return false) {
            BonusKind.QUAKE -> {
                field.quake(playerZ, playerZ + GameConfig.VIEW_DISTANCE)
                event = RunEvent.QUAKE
            }
            BonusKind.SLOWMO -> {
                slowLeft = GameConfig.SLOWMO_DURATION
                event = RunEvent.SLOWMO
            }
            BonusKind.FOLD -> {
                // Ends whatever is in the way: an open surface and/or a hazard zone.
                val folded = field.zones.fold(playerZ)
                val ended = field.hazards.end(playerZ)
                if (!folded && !ended) return false
                event = RunEvent.FOLD
            }
            BonusKind.SHIELD, BonusKind.VORTEX -> return false   // never stored in the slot
        }
        slot = null
        return true
    }

    /** @param steer -1 (full left) .. 1 (full right) */
    fun step(dt: Float, steer: Float, field: ObstacleField? = null) {
        if (crashed) return
        steps++
        dtSum += dt
        if (slowLeft > 0f) slowLeft = maxOf(0f, slowLeft - dt)
        // A gravity well adds a constant sideways pull on top of the player's steering.
        val pull = field?.hazards?.pullAt(playerZ) ?: 0f
        val target = MathUtils.clamp(steer, -1f, 1f) * GameConfig.MAX_TURN_RATE + pull
        turnRate = if (GameConfig.TURN_RESPONSE <= 0f) target
                   else turnRate + (target - turnRate) * (1f - exp(-GameConfig.TURN_RESPONSE * dt))
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
        field.pickupAt(playerU, fromZ, playerZ)?.let { collect(it) }
        val hit = field.firstHit(playerU, fromZ, playerZ, time, field.zones.shapeAt(playerZ)) ?: return
        if (shields > 0) {
            shields--
            field.destroy(hit)
            event = RunEvent.SHIELD_HIT
        } else {
            crashReason = CrashReason.HIT
        }
    }

    private fun collect(p: Pickup) {
        p.taken = true
        when (p.kind) {
            BonusKind.SHIELD -> shields = minOf(GameConfig.MAX_SHIELDS, shields + 1)
            BonusKind.VORTEX -> { shields = 0; slot = null; event = RunEvent.VORTEX; return }
            else -> slot = p.kind   // one slot: a new bonus replaces the old one
        }
        event = RunEvent.PICKUP
    }
}
