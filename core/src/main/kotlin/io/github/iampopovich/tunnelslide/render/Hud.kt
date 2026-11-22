package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import io.github.iampopovich.tunnelslide.world.RunState

/** Minimal text HUD. Will be replaced by a proper UI in the menu batch. */
class Hud : Disposable {
    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val viewport = ScreenViewport()

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        font.data.setScale(maxOf(1f, height / 360f))
    }

    fun render(run: RunState, extra: String = "") {
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        val h = viewport.worldHeight
        val line = font.lineHeight
        font.draw(batch, "DIST ${run.playerZ.toInt()}", 16f, h - 16f)
        font.draw(batch, "SPEED ${run.speed.toInt()}", 16f, h - 16f - line)
        font.draw(batch, "FPS ${Gdx.graphics.framesPerSecond} $extra", 16f, h - 16f - 2 * line)
        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
    }
}
