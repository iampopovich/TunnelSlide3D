package io.github.iampopovich.tunnelslide

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.window.OnBackInvokedDispatcher
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import io.github.iampopovich.tunnelslide.debug.DebugOptions

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = true
            // Tilt steering never touches the screen: keep it from dimming during a run.
            useWakelock = true
            // Default SENSOR_DELAY_GAME = 20 ms between readings; steering wants every one.
            sensorDelay = android.hardware.SensorManager.SENSOR_DELAY_FASTEST
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
        val game = TunnelSlideGame(debug)
        this.game = game
        initialize(game, config)
        // Android 16+ (target SDK 36+) no longer delivers KEYCODE_BACK to the app: back is a
        // system gesture with OnBackInvokedCallback. The game decides what back means
        // (menu up / leave a run / exit) on its own thread.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                game.requestBack()
            }
        }
    }

    private var game: TunnelSlideGame? = null

    /** Older Android (and hardware keys): the back key arrives as a key event. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) game?.requestBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
