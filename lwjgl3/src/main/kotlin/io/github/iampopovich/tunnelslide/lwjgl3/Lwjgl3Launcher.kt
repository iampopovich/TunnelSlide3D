package io.github.iampopovich.tunnelslide.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.iampopovich.tunnelslide.TunnelSlideGame
import io.github.iampopovich.tunnelslide.debug.DebugOptions

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("TunnelSlide3D")
        setWindowedMode(1280, 720)
        useVsync(true)
        setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)
        // r, g, b, a, depth, stencil, MSAA samples
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    }
    Lwjgl3Application(TunnelSlideGame(DebugOptions.fromEnv()), config)
}
