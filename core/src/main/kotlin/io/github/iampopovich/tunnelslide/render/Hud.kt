package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import io.github.iampopovich.tunnelslide.GameConfig
import io.github.iampopovich.tunnelslide.world.BonusKind
import io.github.iampopovich.tunnelslide.world.CrashReason
import io.github.iampopovich.tunnelslide.world.HazardTrack
import io.github.iampopovich.tunnelslide.world.RunState
import kotlin.math.roundToInt

/**
 * Top "visor" stats panel: a chamfered translucent plate with neon edges, three readouts
 * (distance, speed with a bar, best) in Orbitron. Plus the crash overlay.
 * Everything is in screen pixels and rebuilt on resize, so it scales with any display.
 */
class Hud : Disposable {
    private val batch = SpriteBatch()

    /** Re-formats a HUD number only when it changes: String.format every frame feeds the GC. */
    private class CachedText {
        private var key = Int.MIN_VALUE
        private var text = ""
        fun of(value: Int, format: (Int) -> String): String {
            if (value != key) { key = value; text = format(value) }
            return text
        }
    }
    private val scoreText = CachedText()
    private val bestText = CachedText()
    private val speedText = CachedText()
    private val fpsText = CachedText()
    private val closeCallText = CachedText()
    private val shapes = ShapeRenderer()
    private val viewport = ScreenViewport()
    private val layout = GlyphLayout()
    private val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/Orbitron.ttf"))

    private var labelFont: BitmapFont? = null
    private var valueFont: BitmapFont? = null
    private var bigFont: BitmapFont? = null
    private var titleFont: BitmapFont? = null

    /** Fonts shared with the menu (rebuilt on resize). */
    val label: BitmapFont? get() = labelFont
    val value: BitmapFont? get() = valueFont
    val title: BitmapFont? get() = titleFont

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        labelFont?.dispose(); valueFont?.dispose(); bigFont?.dispose(); titleFont?.dispose()
        titleFont = font((height * 0.11f).roundToInt())
        labelFont = font((height * 0.026f).roundToInt())
        valueFont = font((height * 0.050f).roundToInt())
        bigFont = font((height * 0.070f).roundToInt())
    }

    private fun font(px: Int): BitmapFont {
        val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = maxOf(px, 8)
            color = Color.WHITE
            characters = FreeTypeFontGenerator.DEFAULT_CHARS
            minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
        }
        return generator.generateFont(p).apply { setUseIntegerPositions(true) }
    }

    /** @param crashAge seconds since the crash, or negative while running */
    fun render(run: RunState, best: Int, crashAge: Float, fx: Effects? = null, hazards: HazardTrack? = null) {
        viewport.apply()
        // The 3D pass leaves depth testing on; 2D overlays at equal depth would hide the text.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = viewport.camera.combined
        batch.projectionMatrix = viewport.camera.combined

        if (crashAge >= 0f) drawCrashVeil(crashAge)
        drawScreenTints(run, fx)
        val panel = panelGeometry()
        drawPanelShapes(panel, run)
        drawBonusShapes(panel, run)
        drawPanelText(panel, run, best)
        drawBonusText(panel, run)
        if (hazards != null && crashAge < 0f) drawHazardLabel(panel, run, hazards)
        if (crashAge >= 0f) drawCrashText(run, crashAge)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /** Panel corners: top edge spans [left, right] at the screen top, bottom edge is inset by [chamfer]. */
    private class Panel(val left: Float, val right: Float, val top: Float, val bottom: Float, val chamfer: Float) {
        val width get() = right - left
        val height get() = top - bottom
    }

    private fun panelGeometry(): Panel {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        val height = h * 0.13f
        val width = minOf(w * 0.66f, h * 1.6f)
        val left = (w - width) / 2f
        return Panel(left, left + width, h, h - height, height * 0.7f)
    }

    private fun drawPanelShapes(p: Panel, run: RunState) {
        val x0 = p.left; val x1 = p.right; val c = p.chamfer
        val top = p.top; val bot = p.bottom

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Plate: trapezoid made of two triangles.
        shapes.color = PLATE
        shapes.triangle(x0, top, x1, top, x1 - c, bot)
        shapes.triangle(x0, top, x1 - c, bot, x0 + c, bot)

        // Neon edge: wide faint glow, then thin bright core.
        val t = p.height * 0.035f
        for ((width, color) in listOf(t * 4f to GLOW, t to NEON)) {
            shapes.color = color
            shapes.rectLine(x0, top, x0 + c, bot, width)
            shapes.rectLine(x0 + c, bot, x1 - c, bot, width)
            shapes.rectLine(x1 - c, bot, x1, top, width)
        }
        // Accent ticks hanging from the bottom edge.
        shapes.color = NEON
        val tick = p.height * 0.18f
        shapes.rectLine(x0 + c + p.width * 0.04f, bot, x0 + c + p.width * 0.04f + tick, bot - tick * 0.5f, t)
        shapes.rectLine(x1 - c - p.width * 0.04f, bot, x1 - c - p.width * 0.04f - tick, bot - tick * 0.5f, t)

        // Section dividers, slanted like the panel sides.
        val inner = p.height * 0.18f
        for (k in 1..2) {
            val xTop = x0 + p.width * (k / 3f) + if (k == 1) -p.width * 0.03f else p.width * 0.03f
            val slant = if (k == 1) c * 0.35f else -c * 0.35f
            shapes.color = DIVIDER
            shapes.rectLine(xTop, top - inner, xTop + slant, bot + inner, t * 0.8f)
        }

        // Speed bar under the centre readout.
        val barW = p.width * 0.22f
        val barH = p.height * 0.07f
        val bx = (x0 + x1) / 2f - barW / 2f
        val by = bot + p.height * 0.14f
        val k = ((run.speed - GameConfig.START_SPEED) / (GameConfig.MAX_SPEED - GameConfig.START_SPEED)).coerceIn(0f, 1f)
        shapes.color = BAR_BACK
        shapes.rect(bx, by, barW, barH)
        shapes.rect(bx, by, barW * maxOf(k, 0.02f), barH, BAR_LOW, BAR_HIGH.cpy().lerp(BAR_LOW, 1f - k), BAR_HIGH.cpy().lerp(BAR_LOW, 1f - k), BAR_LOW)
        shapes.end()
    }

    private fun drawPanelText(p: Panel, run: RunState, best: Int) {
        val label = labelFont ?: return
        val value = valueFont ?: return
        val big = bigFont ?: return
        val cx = (p.left + p.right) / 2f
        val sideL = p.left + p.width * 0.22f
        val sideR = p.right - p.width * 0.22f
        val labelY = p.top - p.height * 0.12f
        val valueY = p.top - p.height * 0.40f

        batch.begin()
        label.color = LABEL
        centered(label, "SCORE", sideL, labelY)
        centered(label, "SPEED", cx, labelY)
        centered(label, "BEST", sideR, labelY)
        value.color = VALUE
        centered(value, scoreText.of(run.score) { "%06d".format(it) }, sideL, valueY)
        centered(value, bestText.of(best) { "%06d".format(it) }, sideR, valueY)
        big.color = VALUE
        centered(big, speedText.of((run.speed * SPEED_DISPLAY_SCALE).roundToInt()) { it.toString() }, cx, valueY + p.height * 0.06f)

        label.color = FPS
        label.draw(batch, fpsText.of(Gdx.graphics.framesPerSecond) { "FPS $it" }, 12f, 12f + label.capHeight)

        // Close call: "+N" with the speed multiplier, rising and fading for a second.
        val age = run.time - run.lastCloseCallAt
        if (age in 0f..CLOSE_CALL_SHOW) {
            val k = age / CLOSE_CALL_SHOW
            val y = viewport.worldHeight * (0.66f + 0.05f * k)
            label.setColor(CLOSE_CALL.r, CLOSE_CALL.g, CLOSE_CALL.b, 1f - k * k)
            centered(label, "CLOSE CALL", cx, y + label.lineHeight * 1.1f)
            value.setColor(CLOSE_CALL.r, CLOSE_CALL.g, CLOSE_CALL.b, 1f - k * k)
            centered(value, closeCallText.of(run.closeCalls) { "+${run.lastCloseCall}  x${"%.1f".format(run.speed / GameConfig.START_SPEED)}" }, cx, y)
            value.color = VALUE
        }
        batch.end()
    }

    /** Fading flash from bonus events and a blue frame while time dilation is on. */
    private fun drawScreenTints(run: RunState, fx: Effects?) {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (fx != null && fx.flash.a > 0.001f) {
            shapes.color = fx.flash
            shapes.rect(0f, 0f, w, h)
        }
        if (run.slowLeft > 0f) {
            val c = BonusKind.SLOWMO.color
            val edge = h * 0.06f
            val a = 0.35f * minOf(1f, run.slowLeft * 2f)
            val solid = Color(c.r, c.g, c.b, a)
            val clear = Color(c.r, c.g, c.b, 0f)
            shapes.rect(0f, 0f, w, edge, solid, solid, clear, clear)
            shapes.rect(0f, h - edge, w, edge, clear, clear, solid, solid)
            shapes.rect(0f, 0f, edge, h, solid, clear, clear, solid)
            shapes.rect(w - edge, 0f, edge, h, clear, solid, solid, clear)
        }
        shapes.end()
    }

    /** Shield pips (left wing) and the bonus slot (right wing), hanging under the panel. */
    private fun drawBonusShapes(p: Panel, run: RunState) {
        val r = p.height * 0.11f
        val y = p.bottom - r * 1.9f
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        val shield = BonusKind.SHIELD.color
        for (k in 0 until GameConfig.MAX_SHIELDS) {
            val cx = p.left + p.chamfer + p.width * 0.06f + k * r * 2.6f
            shapes.color = if (k < run.shields) shield else BAR_BACK
            diamond(cx, y, r)
        }
        run.slot?.let { kind ->
            shapes.color = kind.color
            diamond(p.right - p.chamfer - p.width * 0.06f, y, r * 1.2f)
        }
        shapes.end()
    }

    private fun diamond(cx: Float, cy: Float, r: Float) {
        shapes.triangle(cx - r, cy, cx, cy + r, cx + r, cy)
        shapes.triangle(cx - r, cy, cx, cy - r, cx + r, cy)
    }

    private fun drawBonusText(p: Panel, run: RunState) {
        val label = labelFont ?: return
        val kind = run.slot ?: return
        val r = p.height * 0.11f
        val y = p.bottom - r * 1.9f + label.capHeight / 2f
        batch.begin()
        label.color = kind.color
        layout.setText(label, kind.label)
        label.draw(batch, layout, p.right - p.chamfer - p.width * 0.06f - r * 2f - layout.width, y)
        batch.end()
    }

    /** Name of the active hazard, centred under the panel, fading with the hazard. */
    private fun drawHazardLabel(p: Panel, run: RunState, hazards: HazardTrack) {
        val h = hazards.at(run.playerZ) ?: return
        val k = hazards.intensity(run.playerZ)
        val label = labelFont ?: return
        batch.begin()
        label.color = Color(h.kind.color.r, h.kind.color.g, h.kind.color.b, k)
        centered(label, h.kind.label, (p.left + p.right) / 2f, p.bottom - p.height * 0.12f)
        batch.end()
    }

    private fun drawCrashVeil(age: Float) {
        val flash = maxOf(0f, 1f - age * 4f)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0.25f + flash, flash, flash, 0.55f + 0.45f * flash)
        shapes.rect(0f, 0f, viewport.worldWidth, viewport.worldHeight)
        shapes.end()
    }

    private fun drawCrashText(run: RunState, age: Float) {
        val big = bigFont ?: return
        val label = labelFont ?: return
        val cx = viewport.worldWidth / 2f
        val h = viewport.worldHeight
        batch.begin()
        big.color = VALUE
        centered(big, if (run.crashReason == CrashReason.FELL) "FELL OFF" else "CRASH", cx, h * 0.58f)
        if (age > RESTART_DELAY) {
            label.color = LABEL
            centered(label, "TAP / SPACE: RESTART      BACK / ESC: MENU", cx, h * 0.44f)
        }
        batch.end()
    }

    private fun centered(font: BitmapFont, text: String, cx: Float, y: Float) {
        layout.setText(font, text)
        font.draw(batch, layout, cx - layout.width / 2f, y)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        labelFont?.dispose(); valueFont?.dispose(); bigFont?.dispose(); titleFont?.dispose()
        generator.dispose()
    }

    companion object {
        const val RESTART_DELAY = 0.6f
        private const val CLOSE_CALL_SHOW = 1f
        private val CLOSE_CALL = Color(1f, 0.85f, 0.3f, 1f)
        /** Speed readout is shown x10 so it reads like km/h. Display only. */
        private const val SPEED_DISPLAY_SCALE = 10f

        private val PLATE = Color(0.02f, 0.04f, 0.10f, 0.72f)
        private val NEON = Color(0.35f, 0.90f, 1.00f, 1f)
        private val GLOW = Color(0.35f, 0.90f, 1.00f, 0.18f)
        private val DIVIDER = Color(0.35f, 0.90f, 1.00f, 0.45f)
        private val LABEL = Color(0.45f, 0.85f, 1.00f, 0.85f)
        private val VALUE = Color(0.92f, 0.98f, 1.00f, 1f)
        private val FPS = Color(0.45f, 0.85f, 1.00f, 0.45f)
        private val BAR_BACK = Color(0.35f, 0.90f, 1.00f, 0.15f)
        private val BAR_LOW = Color(0.35f, 0.90f, 1.00f, 0.95f)
        private val BAR_HIGH = Color(1.00f, 0.20f, 0.60f, 0.95f)
    }
}
