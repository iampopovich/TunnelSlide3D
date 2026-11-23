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
import io.github.iampopovich.tunnelslide.world.BonusKind
import io.github.iampopovich.tunnelslide.world.Obstacle
import io.github.iampopovich.tunnelslide.world.ObstacleField
import io.github.iampopovich.tunnelslide.world.Pickup
import io.github.iampopovich.tunnelslide.world.ObstacleKind
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
    private var blockCount = 0
    private var trailCount = 0
    private var pads: Mesh? = null
    private var padCount = 0
    private var scratch = FloatArray(0)
    private var builtVersion = -1
    private var builtSeam = Float.NaN
    private var zBase = 0f

    private val threats = ArrayList<ObstacleField.Threat>()
    private val visible = ArrayList<Pickup>()
    private val visibleObstacles = ArrayList<Obstacle>()

    fun render(camera: Camera, run: RunState, field: ObstacleField, surface: SurfaceUniforms) {
        if (field.version != builtVersion || surface.seam != builtSeam) rebuild(field, surface.seam, run.playerZ)
        // Warnings only for what the player is actually heading into, refreshed every frame.
        val n = if (run.crashed) 0 else
            field.threats(run.playerU, run.playerZ, GameConfig.VIEW_DISTANCE, surface.shape, threats)
        // Bonus fields: a handful at most, rebuilt every frame (taken ones disappear).
        visible.clear()
        for (p in field.pickups) {
            if (!p.taken && p.z + GameConfig.PICKUP_LENGTH > run.playerZ && p.z < run.playerZ + GameConfig.VIEW_DISTANCE) visible.add(p)
        }
        padCount = visible.size
        pads = upload(pads, visible.size) { out ->
            var i = 0
            for (p in visible) {
                if (p.kind == BonusKind.VORTEX) {
                    // Plasma vortex: a floating cube, easy to spot (a spinning look comes with batch 10).
                    i = writeCube(out, i, p.u, p.z, 0.9f, GameConfig.PICKUP_HALF_WIDTH * 0.8f,
                        0.9f, 0.35f, surface.seam, 0, 0f, 0f, 0f, 0f, p.kind.color)
                } else {
                    // Inset a little so the tile's grid lines stay visible around the field.
                    i = writeCube(out, i, p.u, p.z + PAD_INSET, GameConfig.PICKUP_LENGTH - 2f * PAD_INSET, GameConfig.PICKUP_HALF_WIDTH * 0.94f,
                        0.06f, 0.03f, surface.seam, 0, 0f, 0f, 0f, 0f, p.kind.color)
                }
            }
        }
        trailCount = n
        trails = upload(trails, n) { out ->
            var i = 0
            for (k in 0 until n) {
                val t = threats[k]
                i = writeCube(out, i, t.u, t.z - GameConfig.TRAIL_LENGTH, GameConfig.TRAIL_LENGTH, t.halfWidth,
                    0.001f, TRAIL_LIFT, surface.seam, t.colorIndex, 0f, 0f, 0f, 0f)
            }
        }

        shader.bind()
        shader.setUniformMatrix("u_projView", camera.combined)
        surface.apply(shader)
        // Seam-relative position of the player, as the cube positions are stored.
        shader.setUniformf("u_zShift", zBase - run.playerZ)
        shader.setUniformf("u_clock", run.time / GameConfig.ROLLER_STEP)
        shader.setUniformf("u_rest", GameConfig.ROLLER_REST)
        shader.setUniformf("u_lanes", GameConfig.LANES)
        shader.setUniformf("u_fogColor", Palette.FOG.r, Palette.FOG.g, Palette.FOG.b)

        trails?.takeIf { trailCount > 0 }?.let {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            Gdx.gl.glDepthMask(false)
            shader.setUniformf("u_trail", 1f)
            it.render(shader, GL20.GL_TRIANGLES, 0, trailCount * 36)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
        shader.setUniformf("u_trail", 0f)
        blocks?.takeIf { blockCount > 0 }?.render(shader, GL20.GL_TRIANGLES, 0, blockCount * 36)
        pads?.takeIf { padCount > 0 }?.render(shader, GL20.GL_TRIANGLES, 0, padCount * 36)
    }

    private fun rebuild(field: ObstacleField, seam: Float, playerZ: Float) {
        builtVersion = field.version
        builtSeam = seam
        zBase = playerZ
        val list = visibleObstacles.apply { clear(); addAll(field.obstacles) }
        blockCount = list.size
        blocks = upload(blocks, list.size) { out ->
            var i = 0
            for (o in list) {
                val mode = when (o.kind) {
                    ObstacleKind.BLOCK -> 0f
                    ObstacleKind.ROLLER -> 1f
                    ObstacleKind.PILLAR -> 2f
                }
                i = writeCube(out, i, o.u, o.z, o.length, o.halfWidth, o.height, 0f, seam, o.colorIndex,
                    mode, o.phase, o.sweepLanes.toFloat(), o.dir.toFloat())
            }
        }
    }

    /** Fills a mesh with [count] cubes, growing it when needed; an empty set keeps the old buffer. */
    private fun upload(old: Mesh?, count: Int, fill: (FloatArray) -> Unit): Mesh? {
        if (count == 0) return old
        val mesh = if (old != null && old.maxVertices >= count * 24) old else {
            old?.dispose()
            // 16-bit indices: a mesh holds at most MAX_CUBES; growth is capped there.
            val capacity = maxOf(count * 2, MIN_CAPACITY).coerceAtMost(MAX_CUBES).coerceAtLeast(count)
            Mesh(
                false, capacity * 24, capacity * 36,
                VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_block"),
                VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_extra"),
                VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_roll"),
                VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 3, "a_color"),
            ).apply { setIndices(indicesFor(capacity)) }   // static pattern, set once per mesh
        }
        val size = count * 24 * FLOATS
        if (scratch.size < size) scratch = FloatArray(size)
        fill(scratch)
        mesh.setVertices(scratch, 0, size)
        return mesh
    }

    private fun writeCube(
        out: FloatArray, start: Int, u: Float, z: Float, length: Float, halfWidth: Float,
        height: Float, lift: Float, seam: Float, colorIndex: Int,
        mode: Float, phase: Float, sweep: Float, dir: Float, color: Color? = null,
    ): Int {
        var i = start
        val a = TunnelSurface.wrapAbs(u - seam)
        val width = halfWidth * 2f * TunnelSurface.TAU * GameConfig.TUNNEL_RADIUS
        val c = color ?: BLOCK_COLORS[colorIndex % BLOCK_COLORS.size]
        val zRel = z - zBase
        for (v in 0 until 24) {
            for (k in 0..5) out[i++] = CUBE[v * 6 + k]
            out[i++] = a; out[i++] = zRel; out[i++] = length; out[i++] = width
            out[i++] = height; out[i++] = lift; out[i++] = mode; out[i++] = phase
            out[i++] = sweep; out[i++] = dir
            out[i++] = c.r; out[i++] = c.g; out[i++] = c.b
        }
        return i
    }

    override fun dispose() {
        blocks?.dispose()
        trails?.dispose()
        pads?.dispose()
        shader.dispose()
    }

    companion object {
        private const val PAD_INSET = 0.15f
        private const val FLOATS = 3 + 3 + 4 + 4 + 2 + 3
        private const val MIN_CAPACITY = 32
        private const val MAX_CUBES = 65535 / 24
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
            return if (cachedIndices.size == need) cachedIndices else cachedIndices.copyOf(need)   // only on mesh growth
        }
    }
}
