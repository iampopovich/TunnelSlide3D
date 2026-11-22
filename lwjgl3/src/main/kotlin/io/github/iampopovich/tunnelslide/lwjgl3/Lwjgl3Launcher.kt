package io.github.iampopovich.tunnelslide.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.iampopovich.tunnelslide.TunnelSlideGame
import io.github.iampopovich.tunnelslide.debug.DebugOptions

fun main() {
    val debug = DebugOptions.fromEnv()
    // Automated screenshots: on Wayland a window that is not on screen blocks forever in a
    // vsync'ed buffer swap, so pace frames with a timer instead.
    val headlessCapture = debug.has("TS_SCREENSHOT")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("TunnelSlide3D")
        setWindowedMode(1280, 720)
        useVsync(!headlessCapture)
        setForegroundFPS(if (headlessCapture) 60 else Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)
        setIdleFPS(60)
        // r, g, b, a, depth, stencil, MSAA samples
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    Lwjgl3Application(TunnelSlideGame(debug), config)
}
