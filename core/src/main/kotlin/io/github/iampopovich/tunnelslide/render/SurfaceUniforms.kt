package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.HazardKind
import io.github.iampopovich.tunnelslide.world.HazardTrack
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.TunnelSurface
import io.github.iampopovich.tunnelslide.world.ZoneTrack

/** Per-frame values shared by all surface shaders, computed once per frame. */
class SurfaceUniforms {
    var seam = 0f
        private set
    var playerA = 0f
        private set
    var shape = 0f
        private set
    var mono = false
    /** Hazard visuals, each 0..1 (faded in/out with the hazard). */
    var invertTunnel = 0f
        private set
    var invertBlocks = 0f
        private set
    var hue = 0f
        private set
    var fogDist = GameConfig.VIEW_DISTANCE
        private set
    var hazardRoll = 0f
        private set
    /** Gravity-well image twist for the post pass, radians at the centre (signed). */
    var swirl = 0f
        private set

    fun updateHazards(run: RunState, hazards: HazardTrack) {
        val h = hazards.at(run.playerZ)
        val k = hazards.intensity(run.playerZ)
        invertTunnel = if (h?.kind == HazardKind.BLACKOUT) k else 0f
        invertBlocks = if (h?.kind == HazardKind.NEBULA) k else 0f
        // Hue keeps turning while the disco is on; amount fades with intensity.
        hue = if (h?.kind == HazardKind.HUESHIFT) k else 0f
        val gravity = if (h?.kind == HazardKind.GRAVITY) k else 0f
        fogDist = GameConfig.VIEW_DISTANCE * (1f - (1f - GameConfig.GRAVITY_FOG) * gravity)
        hazardRoll = gravity * (h?.dir ?: 0) * GRAVITY_ROLL
        swirl = gravity * (h?.dir ?: 0) * GRAVITY_SWIRL
        time = run.time
    }

    private var time = 0f

    fun update(run: RunState, zones: ZoneTrack) {
        // The seam only matters once the surface starts to open, i.e. right where we are.
        seam = zones.seamNear(run.playerZ, GameConfig.ZONE_TRANSITION)
        playerA = TunnelSurface.wrapAbs(run.playerU - seam)
        shape = zones.shapeAt(run.playerZ)
    }

    fun apply(shader: ShaderProgram) {
        shader.setUniformf("u_radius", GameConfig.TUNNEL_RADIUS)
        shader.setUniformf("u_playerA", playerA)
        shader.setUniformf("u_shape", shape)
        shader.setUniformf("u_mono", if (mono) 1f else 0f)
        shader.setUniformf("u_invertTunnel", invertTunnel)
        shader.setUniformf("u_invertBlocks", invertBlocks)
        shader.setUniformf("u_hueAmount", hue)
        shader.setUniformf("u_hueAngle", (time * HUE_SPEED) % TWO_PI)
        shader.setUniformf("u_fogDist", fogDist)
    }

    companion object {
        private const val HUE_SPEED = 2.5f
        private const val TWO_PI = 6.2831855f
        private const val GRAVITY_ROLL = 9f   // degrees of camera tilt towards the pull
        private const val GRAVITY_SWIRL = 0.7f
    }
}
