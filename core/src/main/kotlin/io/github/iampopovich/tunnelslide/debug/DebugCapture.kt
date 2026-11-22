package io.github.iampopovich.tunnelslide.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils

/**
 * Dev-only helper: when TS_SCREENSHOT=<path.png> is set, saves the frame
 * number TS_SCREENSHOT_FRAME (default 90) and quits. Lets builds be checked
 * without a human looking at the window.
 */
class DebugCapture private constructor(private val path: String, private val frame: Int) {
    private var frames = 0

    fun onFrameRendered() {
        frames++
        if (frames != frame) return
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true)
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        // Force opaque alpha, otherwise the PNG may come out transparent.
        for (i in 3 until pixels.size step 4) pixels[i] = 0xFF.toByte()
        pixmap.pixels.put(pixels).flip()
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap)
        pixmap.dispose()
        Gdx.app.log("DebugCapture", "saved $path at frame $frame")
        Gdx.app.exit()
    }

    companion object {
        fun fromEnv(): DebugCapture? {
            val path = System.getenv("TS_SCREENSHOT") ?: return null
            val frame = System.getenv("TS_SCREENSHOT_FRAME")?.toIntOrNull() ?: 90
            return DebugCapture(path, frame)
        }
    }
}
