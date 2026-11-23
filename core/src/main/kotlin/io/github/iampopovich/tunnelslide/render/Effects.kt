package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.world.BonusKind
import io.github.iampopovich.tunnelslide.world.RunEvent

/** Short screen effects for bonus events: a fading coloured flash and camera shake. */
class Effects {
    /** Current flash colour; alpha fades from the peak to 0. */
    val flash = Color(0f, 0f, 0f, 0f)
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set
    private var peak = 0f
    private var flashLeft = 0f
    private var flashTime = 1f
    private var shakeLeft = 0f

    fun reset() {
        flashLeft = 0f; shakeLeft = 0f; shakeX = 0f; shakeY = 0f; flash.a = 0f
    }

    fun trigger(e: RunEvent) {
        when (e) {
            RunEvent.PICKUP -> startFlash(Color.WHITE, 0.15f, 0.25f)
            RunEvent.SHIELD_HIT -> { startFlash(BonusKind.SHIELD.color, 0.35f, 0.6f); shakeLeft = 0.25f; Gdx.input.vibrate(60) }
            RunEvent.QUAKE -> { startFlash(BonusKind.QUAKE.color, 0.6f, 0.7f); shakeLeft = 0.6f; Gdx.input.vibrate(200) }
            RunEvent.SLOWMO -> startFlash(BonusKind.SLOWMO.color, 0.5f, 0.5f)
            RunEvent.FOLD -> startFlash(BonusKind.FOLD.color, 0.5f, 0.6f)
            RunEvent.CLOSE_CALL -> { startFlash(Color.WHITE, 0.07f, 0.15f); Gdx.input.vibrate(15) }
            RunEvent.VORTEX -> { startFlash(BonusKind.VORTEX.color, 0.55f, 0.8f); shakeLeft = 0.4f; Gdx.input.vibrate(100) }
        }
    }

    private fun startFlash(c: Color, alpha: Float, time: Float) {
        flash.set(c.r, c.g, c.b, alpha); peak = alpha; flashLeft = time; flashTime = time
    }

    fun update(dt: Float) {
        flashLeft = maxOf(0f, flashLeft - dt)
        flash.a = peak * (flashLeft / flashTime)
        shakeLeft = maxOf(0f, shakeLeft - dt)
        val amp = shakeLeft * 0.25f
        shakeX = MathUtils.random(-amp, amp)
        shakeY = MathUtils.random(-amp, amp)
    }
}
