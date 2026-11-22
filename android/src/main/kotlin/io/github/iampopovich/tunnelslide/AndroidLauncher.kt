package io.github.iampopovich.tunnelslide

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import io.github.iampopovich.tunnelslide.debug.DebugOptions

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = true
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            numSamples = 2
        }
        // Dev flags come as string extras (debug builds only).
        val extras = intent?.extras
        val debug = if (BuildConfig.DEBUG && extras != null) {
            DebugOptions(extras.keySet().filter { it.startsWith("TS_") }
                .associateWith { extras.getString(it) ?: "1" })
        } else DebugOptions.NONE
        initialize(TunnelSlideGame(debug), config)
    }
}
