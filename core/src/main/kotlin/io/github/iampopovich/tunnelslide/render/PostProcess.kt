package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/**
 * Off-screen scene rendering for the effects that need a full-screen pass:
 * Classic motion blur (feedback with the previous frame), anaglyph stereo (two eyes) and the
 * gravity-well swirl. When none is active the game renders straight to the screen and this
 * class costs nothing.
 *
 * Buffers are rendered at [SCALE] of the screen: the blur and the grey anaglyph hide the
 * lower resolution, and the phone GPU saves a lot of fill rate.
 */
class PostProcess : Disposable {
    private val shader = ShaderProgram(
        Gdx.files.internal("shaders/post.vert").readString(),
        Gdx.files.internal("shaders/post.frag").readString(),
    ).also { require(it.isCompiled) { "post shader: ${it.log}" } }
    private val quad = Mesh(true, 4, 0, VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position")).apply {
        setVertices(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    }
    private var left: FrameBuffer? = null
    private var right: FrameBuffer? = null
    private val history = arrayOfNulls<FrameBuffer>(2)
    private var cur = 0
    private var width = 0
    private var height = 0
    private var fresh = true

    fun resize(w: Int, h: Int) {
        disposeBuffers()
        width = maxOf(1, (w * SCALE).toInt()); height = maxOf(1, (h * SCALE).toInt())
        fresh = true
    }

    private fun scene(stereoEye: Int): FrameBuffer {
        val existing = if (stereoEye == 1) right else left
        if (existing != null) return existing
        val fb = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)
        if (stereoEye == 1) right = fb else left = fb
        return fb
    }

    private fun historyBuffer(i: Int): FrameBuffer =
        history[i] ?: FrameBuffer(Pixmap.Format.RGBA8888, width, height, false).also { history[i] = it }

    /** Renders one eye's scene into its buffer. [eye] 0 = left/mono, 1 = right. */
    fun captureEye(eye: Int, draw: () -> Unit) {
        val fb = scene(eye)
        fb.begin()
        draw()
        fb.end()
    }

    /** Composites into the history buffer and shows it on screen. */
    fun present(stereo: Int, blur: Float, swirl: Float) {
        val l = scene(0)
        val r = if (stereo > 0) scene(1) else l
        val prev = historyBuffer(1 - cur)
        val out = historyBuffer(cur)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        out.begin()
        l.colorBufferTexture.bind(0)
        r.colorBufferTexture.bind(1)
        prev.colorBufferTexture.bind(2)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        shader.bind()
        shader.setUniformi("u_left", 0)
        shader.setUniformi("u_right", 1)
        shader.setUniformi("u_history", 2)
        shader.setUniformf("u_stereo", stereo.toFloat())
        shader.setUniformf("u_blur", if (fresh) 0f else blur)
        shader.setUniformf("u_swirl", swirl)
        shader.setUniformf("u_aspect", width.toFloat() / height)
        quad.render(shader, GL20.GL_TRIANGLE_STRIP)
        out.end()
        fresh = false

        // Blit to the screen: same shader, no stereo/blur/swirl, from the composited buffer.
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        out.colorBufferTexture.bind(0)
        out.colorBufferTexture.bind(1)
        out.colorBufferTexture.bind(2)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        shader.setUniformf("u_stereo", 0f)
        shader.setUniformf("u_blur", 0f)
        shader.setUniformf("u_swirl", 0f)
        quad.render(shader, GL20.GL_TRIANGLE_STRIP)
        cur = 1 - cur
    }

    /** Next frame starts without history (no smear from an old frame). */
    fun restart() { fresh = true }

    private fun disposeBuffers() {
        left?.dispose(); right?.dispose(); history.forEach { it?.dispose() }
        left = null; right = null; history.fill(null)
    }

    override fun dispose() {
        disposeBuffers()
        shader.dispose()
        quad.dispose()
    }

    companion object {
        const val SCALE = 0.75f
    }
}
