package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.TunnelSurface
import io.github.iampopovich.tunnelslide.world.ZoneTrack

/** Per-frame values of surface.glsl, computed once and applied to every shader. */
class SurfaceUniforms {
    private val ramps = FloatArray(6)
    var seam = 0f
        private set
    var playerA = 0f
        private set
    private var base = 0f

    fun update(run: RunState, zones: ZoneTrack) {
        seam = zones.seamNear(run.playerZ, GameConfig.VIEW_DISTANCE)
        playerA = TunnelSurface.wrapAbs(run.playerU - seam)
        base = zones.viewRamps(run.playerZ, GameConfig.VIEW_DISTANCE, ramps)
    }

    fun apply(shader: ShaderProgram) {
        shader.setUniformf("u_radius", GameConfig.TUNNEL_RADIUS)
        shader.setUniformf("u_playerA", playerA)
        shader.setUniformf("u_shapeBase", base)
        shader.setUniformf("u_ramp0", ramps[0], ramps[1], ramps[2])
        shader.setUniformf("u_ramp1", ramps[3], ramps[4], ramps[5])
    }
}
