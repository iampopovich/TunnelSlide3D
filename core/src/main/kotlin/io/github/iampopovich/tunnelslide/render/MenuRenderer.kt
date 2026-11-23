package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import io.github.iampopovich.tunnelslide.ui.MenuModel
import io.github.iampopovich.tunnelslide.ui.MenuScreen

/**
 * Menu overlay in the style of the top panel: chamfered translucent plates with neon edges,
 * drawn over the attract-mode tunnel. Row rectangles are kept for touch hit-testing.
 */
class MenuRenderer(private val hud: Hud) : Disposable {
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val viewport = ScreenViewport()
    private val layout = GlyphLayout()
    private val rows = ArrayList<Rectangle>()

    fun resize(width: Int, height: Int) = viewport.update(width, height, true)

    /** Row index under a touch at screen pixel (x, y from the top), or -1. */
    fun hit(x: Int, y: Int): Int {
        val wy = viewport.worldHeight - y
        return rows.indexOfFirst { it.contains(x.toFloat(), wy) }
    }

    /** -1 if (x) is in the left half of row [i], +1 otherwise. */
    fun side(i: Int, x: Int): Int {
        val r = rows.getOrNull(i) ?: return 0
        return if (x < r.x + r.width / 2f) -1 else 1
    }

    fun render(menu: MenuModel, best: Int, hint: String?, time: Float) {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        viewport.apply()
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = viewport.camera.combined
        batch.projectionMatrix = viewport.camera.combined

        val items = menu.items()
        // Rows fit between the title and the hint line, however many there are.
        val area = h * 0.62f
        val rowH = minOf(h * 0.105f, area / (items.size * 1.3f))
        val gap = rowH * 0.28f
        val rowW = minOf(w * 0.5f, h * 1.15f)
        val x0 = (w - rowW) / 2f
        val total = items.size * rowH + (items.size - 1) * gap
        val top = h * 0.44f + minOf(total, area) / 2f
        rows.clear()
        for (i in items.indices) rows.add(Rectangle(x0, top - i * (rowH + gap) - rowH, rowW, rowH))

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = VEIL
        shapes.rect(0f, 0f, w, h)
        val pulse = 0.75f + 0.25f * kotlin.math.sin(time * 4f)
        for ((i, r) in rows.withIndex()) {
            val sel = i == menu.selected
            plate(r, if (sel) PLATE_SEL else PLATE)
            val t = r.height * 0.035f
            val c = r.height * 0.35f
            for ((lw, col) in listOf(t * 4f to GLOW, t to if (sel) NEON.cpy().mul(1f, 1f, 1f, pulse) else NEON_DIM)) {
                shapes.color = col
                outline(r, c, lw)
            }
        }
        shapes.end()

        batch.begin()
        val title = hud.title
        val label = hud.label
        val value = hud.value
        if (title != null) {
            title.color = NEON
            val heading = when (menu.screen) {
                MenuScreen.MAIN -> "TUNNELSLIDE 3D"
                MenuScreen.SETTINGS -> "SETTINGS"
                MenuScreen.CALIBRATE -> "CALIBRATE"
            }
            centered(title, heading, w / 2f, h * 0.88f)
        }
        if (value != null) {
            for ((i, item) in items.withIndex()) {
                val r = rows[i]
                val y = r.y + r.height / 2f + value.capHeight / 2f
                value.color = if (i == menu.selected) TEXT_SEL else TEXT
                if (item.value == null) {
                    centered(value, item.text, r.x + r.width / 2f, y)
                } else {
                    layout.setText(value, item.text)
                    value.draw(batch, layout, r.x + r.height * 0.6f, y)
                    val v = if (item.adjustable) "<  ${item.value}  >" else item.value
                    value.color = VALUE_COL
                    layout.setText(value, v)
                    value.draw(batch, layout, r.x + r.width - r.height * 0.6f - layout.width, y)
                }
            }
        }
        if (label != null) {
            label.color = HINT
            val bottomRow = rows.lastOrNull()?.y ?: h * 0.2f
            hint?.let { centered(label, it, w / 2f, bottomRow - h * 0.06f) }
            if (menu.screen == MenuScreen.MAIN && best > 0) centered(label, "BEST  %06d".format(best), w / 2f, top + h * 0.05f)
        }
        batch.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /** Hexagonal plate: rectangle with chamfered left and right ends. */
    private fun plate(r: Rectangle, color: Color) {
        val c = r.height * 0.35f
        shapes.color = color
        shapes.rect(r.x + c, r.y, r.width - 2 * c, r.height)
        shapes.triangle(r.x, r.y + r.height / 2f, r.x + c, r.y + r.height, r.x + c, r.y)
        shapes.triangle(r.x + r.width, r.y + r.height / 2f, r.x + r.width - c, r.y + r.height, r.x + r.width - c, r.y)
    }

    private fun outline(r: Rectangle, c: Float, lw: Float) {
        val l = r.x; val rt = r.x + r.width; val b = r.y; val t = r.y + r.height; val m = r.y + r.height / 2f
        shapes.rectLine(l + c, t, rt - c, t, lw)
        shapes.rectLine(l + c, b, rt - c, b, lw)
        shapes.rectLine(l, m, l + c, t, lw)
        shapes.rectLine(l, m, l + c, b, lw)
        shapes.rectLine(rt, m, rt - c, t, lw)
        shapes.rectLine(rt, m, rt - c, b, lw)
    }

    private fun centered(font: BitmapFont, text: String, cx: Float, y: Float) {
        layout.setText(font, text)
        font.draw(batch, layout, cx - layout.width / 2f, y)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
    }

    companion object {
        private val VEIL = Color(0.01f, 0.02f, 0.06f, 0.55f)
        private val PLATE = Color(0.02f, 0.04f, 0.10f, 0.72f)
        private val PLATE_SEL = Color(0.05f, 0.14f, 0.24f, 0.85f)
        private val NEON = Color(0.35f, 0.90f, 1.00f, 1f)
        private val NEON_DIM = Color(0.35f, 0.90f, 1.00f, 0.45f)
        private val GLOW = Color(0.35f, 0.90f, 1.00f, 0.12f)
        private val TEXT = Color(0.75f, 0.90f, 1.00f, 0.85f)
        private val TEXT_SEL = Color(0.95f, 1.00f, 1.00f, 1f)
        private val VALUE_COL = Color(1.00f, 0.45f, 0.75f, 1f)
        private val HINT = Color(0.45f, 0.85f, 1.00f, 0.75f)
    }
}
