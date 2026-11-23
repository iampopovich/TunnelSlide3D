package io.github.iampopovich.tunnelslide.ui

enum class MenuScreen { MAIN, SETTINGS, CALIBRATE }

enum class ItemId { PLAY, LEVEL, SETTINGS, STYLE, STEREO, MONO, SOUND, CALIBRATE, CUSTOM_START, CAPTURE, BACK }

/** One row; [value] is shown on the right with < > arrows when [adjustable]. */
data class MenuItem(val id: ItemId, val text: String, val value: String? = null, val adjustable: Boolean = false)

enum class MenuAction { NONE, PLAY, CHANGED, CAPTURE_NEUTRAL, EXIT }

/**
 * Menu logic without any rendering: what rows exist, which is selected, what a press does.
 * Keyboard: up/down select, enter press, left/right adjust, escape back.
 * Touch: tap a row; on adjustable rows the left/right half decreases/increases.
 */
class MenuModel(private val settings: Settings) {
    var screen = MenuScreen.MAIN
        private set
    var selected = 0
        private set

    fun items(): List<MenuItem> = when (screen) {
        MenuScreen.MAIN -> listOf(
            MenuItem(ItemId.PLAY, "PLAY"),
            MenuItem(ItemId.LEVEL, "DIFFICULTY", settings.level.label, adjustable = true),
            MenuItem(ItemId.SETTINGS, "SETTINGS"),
        )
        MenuScreen.SETTINGS -> buildList {
            add(MenuItem(ItemId.STYLE, "STYLE", settings.style.label, adjustable = true))
            add(MenuItem(ItemId.STEREO, "3D GLASSES", settings.stereo.label, adjustable = true))
            add(MenuItem(ItemId.MONO, "MONOCHROME", onOff(settings.mono), adjustable = true))
            add(MenuItem(ItemId.SOUND, "SOUND", onOff(settings.sound), adjustable = true))
            if (settings.level == Level.CUSTOM) {
                add(MenuItem(ItemId.CUSTOM_START, "CUSTOM START", "${(settings.customStart * 100).toInt()}%", adjustable = true))
            }
            add(MenuItem(ItemId.CALIBRATE, "CALIBRATE TILT"))
            add(MenuItem(ItemId.BACK, "BACK"))
        }
        MenuScreen.CALIBRATE -> listOf(
            MenuItem(ItemId.CAPTURE, "SET STRAIGHT"),
            MenuItem(ItemId.BACK, "BACK"),
        )
    }

    fun move(delta: Int) {
        val n = items().size
        selected = ((selected + delta) % n + n) % n
    }

    /** Activates row [index]; [side] is -1/+1 for left/right adjustment, 0 for a plain press. */
    fun press(index: Int = selected, side: Int = 0): MenuAction {
        val item = items().getOrNull(index) ?: return MenuAction.NONE
        selected = index
        val dir = if (side < 0) -1 else 1
        return when (item.id) {
            ItemId.PLAY -> MenuAction.PLAY
            ItemId.LEVEL -> {
                val all = Level.entries
                settings.level = all[((settings.level.ordinal + dir) % all.size + all.size) % all.size]
                MenuAction.CHANGED
            }
            ItemId.SETTINGS -> { open(MenuScreen.SETTINGS); MenuAction.NONE }
            ItemId.STYLE -> { settings.style = cycle(Style.entries, settings.style, dir); MenuAction.CHANGED }
            ItemId.STEREO -> { settings.stereo = cycle(Stereo.entries, settings.stereo, dir); MenuAction.CHANGED }
            ItemId.MONO -> { settings.mono = !settings.mono; MenuAction.CHANGED }
            ItemId.SOUND -> { settings.sound = !settings.sound; MenuAction.CHANGED }
            ItemId.CUSTOM_START -> {
                val steps = (settings.customStart * 10).toInt() + dir
                settings.customStart = steps.coerceIn(0, 9) / 10f
                MenuAction.CHANGED
            }
            ItemId.CALIBRATE -> { open(MenuScreen.CALIBRATE); MenuAction.NONE }
            ItemId.CAPTURE -> { open(MenuScreen.SETTINGS); MenuAction.CAPTURE_NEUTRAL }
            ItemId.BACK -> { back() }
        }
    }

    /** Back key: one level up; on the main screen it asks to leave the app. */
    fun back(): MenuAction {
        when (screen) {
            MenuScreen.MAIN -> return MenuAction.EXIT
            MenuScreen.SETTINGS -> open(MenuScreen.MAIN, ItemId.SETTINGS)
            MenuScreen.CALIBRATE -> open(MenuScreen.SETTINGS, ItemId.CALIBRATE)
        }
        return MenuAction.NONE
    }

    fun reset() = open(MenuScreen.MAIN)

    private fun open(s: MenuScreen, focus: ItemId? = null) {
        screen = s
        selected = focus?.let { id -> items().indexOfFirst { it.id == id }.coerceAtLeast(0) } ?: 0
    }

    private fun <T> cycle(all: List<T>, v: T, dir: Int): T = all[((all.indexOf(v) + dir) % all.size + all.size) % all.size]

    private fun onOff(b: Boolean) = if (b) "ON" else "OFF"
}
