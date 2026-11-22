package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.BlockPlacement
import io.github.iampopovich.tunnelslide.world.Obstacle
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.TunnelSurface
import io.github.iampopovich.tunnelslide.world.ZoneTrack

/** Draws obstacles as neon-edged boxes and their warning trails. */
class BlockRenderer : Disposable {
    private val shader = ShaderProgram(
        Gdx.files.internal("shaders/block.vert"),
        Gdx.files.internal("shaders/block.frag"),
    ).also { require(it.isCompiled) { "block shader: ${it.log}" } }

    private val cube = buildCube()
    private val model = Matrix4()

    private var seam = 0f
    private lateinit var zones: ZoneTrack

    fun render(camera: Camera, run: RunState, obstacles: Collection<Obstacle>, zones: ZoneTrack) {
        this.zones = zones
        seam = zones.seamNear(run.playerZ, GameConfig.VIEW_DISTANCE)
        shader.bind()
        shader.setUniformMatrix("u_projView", camera.combined)
        shader.setUniformf("u_fogDist", GameConfig.VIEW_DISTANCE)
        shader.setUniformf("u_fogColor", Palette.FOG.r, Palette.FOG.g, Palette.FOG.b)

        // Pass 1: translucent trails lying on the surface, no depth writes.
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        Gdx.gl.glDepthMask(false)
        shader.setUniformf("u_trail", 1f)
        for (o in obstacles) {
            val dist = o.z - run.playerZ
            if (dist + o.length < 0f || dist > GameConfig.VIEW_DISTANCE) continue
            val trailStart = maxOf(dist - GameConfig.TRAIL_LENGTH, 0f)
            draw(o, run, trailStart, dist - trailStart, TRAIL_LIFT, 0.001f)
        }
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // Pass 2: solid blocks.
        shader.setUniformf("u_trail", 0f)
        for (o in obstacles) {
            val dist = o.z - run.playerZ
            if (dist + o.length < 0f || dist > GameConfig.VIEW_DISTANCE) continue
            draw(o, run, dist, o.length, 0f, o.height)
        }
    }

    /** Places the unit cube on the surface: [near] is the distance of its near face. */
    private fun draw(o: Obstacle, run: RunState, near: Float, length: Float, lift: Float, height: Float) {
        if (length <= 0f) return
        val r = GameConfig.TUNNEL_RADIUS
        val theta = TunnelSurface.theta(o.u, run.playerU, seam)
        val width = o.halfWidth * 2f * TunnelSurface.TAU * r
        val shape = zones.shapeAt(run.playerZ + near + length * 0.5f)
        BlockPlacement.compose(theta, near, length, width, height, lift, shape, r, model)

        val c = BLOCK_COLORS[o.colorIndex % BLOCK_COLORS.size]
        shader.setUniformMatrix("u_model", model)
        shader.setUniformf("u_color", c.r, c.g, c.b)
        cube.render(shader, GL20.GL_TRIANGLES)
    }

    private fun buildCube(): Mesh {
        // 6 faces x 4 vertices: position (unit cube centred at origin) + normal.
        val faces = arrayOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
        )
        val verts = FloatArray(24 * 6)
        val idx = ShortArray(36)
        var v = 0; var i = 0
        for ((f, n) in faces.withIndex()) {
            // Two axes spanning the face.
            val a = if (n[0] != 0f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
            val b = floatArrayOf(n[1] * a[2] - n[2] * a[1], n[2] * a[0] - n[0] * a[2], n[0] * a[1] - n[1] * a[0])
            for ((sa, sb) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                for (k in 0..2) verts[v++] = 0.5f * (n[k] + sa * a[k] + sb * b[k])
                for (k in 0..2) verts[v++] = n[k]
            }
            val base = (f * 4).toShort()
            for (o in intArrayOf(0, 1, 2, 2, 3, 0)) idx[i++] = (base + o).toShort()
        }
        return Mesh(
            true, 24, 36,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
        ).apply { setVertices(verts); setIndices(idx) }
    }

    override fun dispose() {
        cube.dispose()
        shader.dispose()
    }

    companion object {
        private const val TRAIL_LIFT = 0.045f   // above the faceted tube surface
        val BLOCK_COLORS = arrayOf(
            Color(1.00f, 0.35f, 0.10f, 1f),   // orange
            Color(1.00f, 0.15f, 0.55f, 1f),   // magenta
            Color(1.00f, 0.85f, 0.15f, 1f),   // yellow
            Color(0.30f, 1.00f, 0.35f, 1f),   // green
        )
    }
}
