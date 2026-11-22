package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.utils.Disposable
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.ObstacleField
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.TunnelSurface

/**
 * Draws all obstacles in two draw calls (trails, then blocks).
 *
 * Every vertex stores its block's tunnel placement; block.vert puts the cube on the surface
 * with the same formula as the tunnel. The buffers depend only on the obstacle set and the
 * seam, so they are rebuilt when a pattern spawns, not every frame. Positions along z are
 * stored relative to [zBase] to keep them small for the GPU.
 */
class BlockRenderer : Disposable {
    private val shader = Shaders.withSurface("block")
    private var blocks: Mesh? = null
    private var trails: Mesh? = null
    private var builtVersion = -1
    private var builtSeam = Float.NaN
    private var zBase = 0f

    fun render(camera: Camera, run: RunState, field: ObstacleField, surface: SurfaceUniforms) {
        if (field.version != builtVersion || surface.seam != builtSeam) rebuild(field, surface.seam, run.playerZ)

        shader.bind()
        shader.setUniformMatrix("u_projView", camera.combined)
        surface.apply(shader)
        // Seam-relative position of the player, as the cube positions are stored.
        shader.setUniformf("u_zShift", zBase - run.playerZ)
        shader.setUniformf("u_fogDist", GameConfig.VIEW_DISTANCE)
        shader.setUniformf("u_fogColor", Palette.FOG.r, Palette.FOG.g, Palette.FOG.b)

        trails?.let {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            Gdx.gl.glDepthMask(false)
            shader.setUniformf("u_trail", 1f)
            it.render(shader, GL20.GL_TRIANGLES)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
        blocks?.let {
            shader.setUniformf("u_trail", 0f)
            it.render(shader, GL20.GL_TRIANGLES)
        }
    }

    private fun rebuild(field: ObstacleField, seam: Float, playerZ: Float) {
        builtVersion = field.version
        builtSeam = seam
        zBase = playerZ
        val list = field.obstacles.toList()
        blocks = upload(blocks, list.size) { out ->
            var i = 0
            for (o in list) i = writeCube(out, i, o.u, o.z, o.length, o.halfWidth, o.height, 0f, seam, o.colorIndex)
        }
        trails = upload(trails, list.size) { out ->
            var i = 0
            for (o in list) {
                val start = o.z - GameConfig.TRAIL_LENGTH
                i = writeCube(out, i, o.u, start, GameConfig.TRAIL_LENGTH, o.halfWidth, 0.001f, TRAIL_LIFT, seam, o.colorIndex)
            }
        }
    }

    private fun upload(old: Mesh?, count: Int, fill: (FloatArray) -> Unit): Mesh? {
        if (count == 0) { old?.dispose(); return null }
        val capacity = maxOf(count, MIN_CAPACITY)
        val mesh = if (old != null && old.maxVertices >= capacity * 24) old else {
            old?.dispose()
            Mesh(
                false, capacity * 2 * 24, capacity * 2 * 36,
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_block"),
                VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_extra"),
                VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 3, "a_color"),
            )
        }
        val verts = FloatArray(count * 24 * FLOATS)
        fill(verts)
        mesh.setVertices(verts)
        mesh.setIndices(indicesFor(count))
        return mesh
    }

    private fun writeCube(
        out: FloatArray, start: Int, u: Float, z: Float, length: Float, halfWidth: Float,
        height: Float, lift: Float, seam: Float, colorIndex: Int,
    ): Int {
        var i = start
        val a = TunnelSurface.wrapAbs(u - seam)
        val width = halfWidth * 2f * TunnelSurface.TAU * GameConfig.TUNNEL_RADIUS
        val c = BLOCK_COLORS[colorIndex % BLOCK_COLORS.size]
        val zRel = z - zBase
        for (v in 0 until 24) {
            for (k in 0..5) out[i++] = CUBE[v * 6 + k]
            out[i++] = a; out[i++] = zRel; out[i++] = length; out[i++] = width
            out[i++] = height; out[i++] = lift
            out[i++] = c.r; out[i++] = c.g; out[i++] = c.b
        }
        return i
    }

    override fun dispose() {
        blocks?.dispose()
        trails?.dispose()
        shader.dispose()
    }

    companion object {
        private const val FLOATS = 3 + 3 + 4 + 2 + 3
        private const val MIN_CAPACITY = 256
        private const val TRAIL_LIFT = 0.045f   // above the faceted tube surface

        val BLOCK_COLORS = arrayOf(
            Color(1.00f, 0.35f, 0.10f, 1f),   // orange
            Color(1.00f, 0.15f, 0.55f, 1f),   // magenta
            Color(1.00f, 0.85f, 0.15f, 1f),   // yellow
            Color(0.30f, 1.00f, 0.35f, 1f),   // green
        )

        /** Unit cube: 6 faces x 4 vertices, position + normal. */
        private val CUBE: FloatArray = run {
            val faces = arrayOf(
                floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
                floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
                floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
            )
            val v = FloatArray(24 * 6)
            var i = 0
            for (n in faces) {
                val a = if (n[0] != 0f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
                val b = floatArrayOf(n[1] * a[2] - n[2] * a[1], n[2] * a[0] - n[0] * a[2], n[0] * a[1] - n[1] * a[0])
                for ((sa, sb) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                    for (k in 0..2) v[i++] = 0.5f * (n[k] + sa * a[k] + sb * b[k])
                    for (k in 0..2) v[i++] = n[k]
                }
            }
            v
        }

        private var cachedIndices = ShortArray(0)

        private fun indicesFor(count: Int): ShortArray {
            val need = count * 36
            if (cachedIndices.size < need) {
                // Indices are 16-bit: at most 65535 / 24 = 2730 cubes per mesh.
                require(count * 24 <= 65535) { "too many blocks for one mesh: $count" }
                cachedIndices = ShortArray(need)
                for (cube in 0 until count) for (f in 0 until 6) {
                    val base = cube * 24 + f * 4
                    val o = cube * 36 + f * 6
                    cachedIndices[o] = base.toShort(); cachedIndices[o + 1] = (base + 1).toShort()
                    cachedIndices[o + 2] = (base + 2).toShort(); cachedIndices[o + 3] = (base + 2).toShort()
                    cachedIndices[o + 4] = (base + 3).toShort(); cachedIndices[o + 5] = base.toShort()
                }
            }
            return if (cachedIndices.size == need) cachedIndices else cachedIndices.copyOf(need)
        }
    }
}
