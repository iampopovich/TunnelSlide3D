package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import io.github.iampopovich.tunnelslide.GameConfig
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
    }
}
