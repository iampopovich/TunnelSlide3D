package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.utils.Disposable
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.RunState

/**
 * Draws the tunnel surface. The mesh is static: columns are positions measured from the
 * seam (where the ribbon is cut when the surface opens), rows are distances ahead.
 * The vertex shader turns them into tube / flat / outside geometry relative to the player;
 * forward motion is a pattern offset in the fragment shader.
 */
class TunnelRenderer : Disposable {
    private val shader = Shaders.withSurface("tunnel")
    private val mesh: Mesh = buildMesh()

    fun render(camera: Camera, run: RunState, surface: SurfaceUniforms) {
        val lanes = GameConfig.LANES
        val ringLen = GameConfig.RING_LENGTH
        // Offsets are reduced modulo the pattern period on the CPU, so mediump floats
        // on phones never see the huge absolute distance.
        val uOffset = ((surface.seam * lanes) % PATTERN_LANES) / lanes
        val zOffset = run.playerZ % (ringLen * PATTERN_RINGS)

        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        shader.bind()
        shader.setUniformMatrix("u_projView", camera.combined)
        surface.apply(shader)
        shader.setUniformf("u_uOffset", uOffset)
        shader.setUniformf("u_zOffset", zOffset)
        shader.setUniformf("u_lanes", lanes)
        shader.setUniformf("u_ringLen", ringLen)
        shader.setUniformf("u_fogDist", GameConfig.VIEW_DISTANCE)
        shader.setUniformf("u_fogColor", Palette.FOG.r, Palette.FOG.g, Palette.FOG.b)
        mesh.render(shader, GL20.GL_TRIANGLES)
    }

    private fun buildMesh(): Mesh {
        val cols = GameConfig.TUNNEL_SIDES
        val rows = GameConfig.TUNNEL_RINGS
        val verts = FloatArray((cols + 1) * (rows + 1) * 2)
        var i = 0
        for (r in 0..rows) {
            // Rows get denser near the camera where detail matters most.
            val t = r.toFloat() / rows
            val dist = GameConfig.VIEW_DISTANCE * t * t * 0.6f + GameConfig.VIEW_DISTANCE * t * 0.4f
            for (c in 0..cols) {
                verts[i++] = c.toFloat() / cols
                verts[i++] = dist
            }
        }
        val idx = ShortArray(cols * rows * 6)
        var j = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = (r * (cols + 1) + c).toShort()
            val b = (a + 1).toShort()
            val d = (a + cols + 1).toShort()
            val e = (d + 1).toShort()
            idx[j++] = a; idx[j++] = d; idx[j++] = b
            idx[j++] = b; idx[j++] = d; idx[j++] = e
        }
        return Mesh(true, verts.size / 2, idx.size, VertexAttribute(0x100, 2, "a_coord")).apply {
            setVertices(verts)
            setIndices(idx)
        }
    }

    override fun dispose() {
        mesh.dispose()
        shader.dispose()
    }

    companion object {
        /** Pattern repeats every PATTERN_LANES lanes and PATTERN_RINGS rings. */
        const val PATTERN_LANES = 2f
        const val PATTERN_RINGS = 16f
    }
}
