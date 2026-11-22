package io.github.iampopovich.tunnelslide

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import io.github.iampopovich.tunnelslide.debug.DebugCapture

/** Entry point shared by all platforms. */
class TunnelSlideGame : ApplicationAdapter() {
    private val capture = DebugCapture.fromEnv()

    override fun create() {
        Gdx.app.log(TAG, "TunnelSlide3D started on ${Gdx.app.type}")
    }

    override fun render() {
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        capture?.onFrameRendered()
    }

    companion object {
        const val TAG = "TunnelSlide3D"
    }
}
