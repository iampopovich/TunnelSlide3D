package io.github.iampopovich.tunnelslide

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

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
        initialize(TunnelSlideGame(), config)
    }
}
