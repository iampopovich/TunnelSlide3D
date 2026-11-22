package io.github.iampopovich.tunnelslide.debug

/**
 * Dev flags (TS_SEED, TS_AUTOPILOT, TS_DIFFICULTY, TS_ZONE, TS_SCREENSHOT...).
 * Desktop reads them from environment variables, Android from launch intent extras:
 *   adb shell am start -n <pkg>/.AndroidLauncher --es TS_AUTOPILOT 1 --es TS_SEED 5
 */
class DebugOptions(private val values: Map<String, String>) {
    operator fun get(key: String): String? = values[key]
    fun has(key: String) = values.containsKey(key)

    companion object {
        fun fromEnv() = DebugOptions(System.getenv().filterKeys { it.startsWith("TS_") })
        val NONE = DebugOptions(emptyMap())
    }
}
