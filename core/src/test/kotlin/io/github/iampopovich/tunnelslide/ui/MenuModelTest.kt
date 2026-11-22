package io.github.iampopovich.tunnelslide.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuModelTest {
    private val settings = Settings()
    private val menu = MenuModel(settings)

    private fun indexOf(id: ItemId) = menu.items().indexOfFirst { it.id == id }

    @Test
    fun playStartsTheGame() {
        assertEquals(MenuAction.PLAY, menu.press(indexOf(ItemId.PLAY)))
    }

    @Test
    fun difficultyCyclesBothWays() {
        val i = indexOf(ItemId.LEVEL)
        menu.press(i, +1); assertEquals(Level.HARD, settings.level)
        menu.press(i, -1); menu.press(i, -1); assertEquals(Level.EASY, settings.level)
        menu.press(i, -1); assertEquals(Level.CUSTOM, settings.level)
    }

    @Test
    fun customStartOnlyForCustomLevelAndClamped() {
        menu.press(indexOf(ItemId.SETTINGS))
        assertEquals(MenuScreen.SETTINGS, menu.screen)
        assertEquals(-1, indexOf(ItemId.CUSTOM_START))
        settings.level = Level.CUSTOM
        val i = indexOf(ItemId.CUSTOM_START)
        repeat(20) { menu.press(i, +1) }
        assertEquals(0.9f, settings.customStart, 1e-6f)
        repeat(20) { menu.press(i, -1) }
        assertEquals(0f, settings.customStart, 1e-6f)
    }

    @Test
    fun togglesAndCalibrationFlow() {
        menu.press(indexOf(ItemId.SETTINGS))
        menu.press(indexOf(ItemId.MONO)); assertTrue(settings.mono)
        menu.press(indexOf(ItemId.SOUND)); assertEquals(false, settings.sound)
        menu.press(indexOf(ItemId.CALIBRATE))
        assertEquals(MenuScreen.CALIBRATE, menu.screen)
        assertEquals(MenuAction.CAPTURE_NEUTRAL, menu.press(indexOf(ItemId.CAPTURE)))
        assertEquals(MenuScreen.SETTINGS, menu.screen)
    }

    @Test
    fun backGoesUpThenExits() {
        menu.press(indexOf(ItemId.SETTINGS))
        assertEquals(MenuAction.NONE, menu.back())
        assertEquals(MenuScreen.MAIN, menu.screen)
        assertEquals(ItemId.SETTINGS, menu.items()[menu.selected].id)
        assertEquals(MenuAction.EXIT, menu.back())
    }

    @Test
    fun levelsShapeTheDifficultyCurve() {
        val ramp = 6000f
        settings.level = Level.EASY
        val easy = settings.difficultyAt(3000f, ramp)
        settings.level = Level.NORMAL
        val normal = settings.difficultyAt(3000f, ramp)
        settings.level = Level.EXTREME
        assertEquals(0.7f, settings.difficultyAt(0f, ramp), 1e-6f)
        assertTrue(easy < normal && normal < settings.difficultyAt(3000f, ramp))
        settings.level = Level.CUSTOM; settings.customStart = 0.4f
        assertEquals(0.4f, settings.difficultyAt(0f, ramp), 1e-6f)
        assertEquals(1f, settings.difficultyAt(1e6f, ramp), 1e-6f)
    }
}
