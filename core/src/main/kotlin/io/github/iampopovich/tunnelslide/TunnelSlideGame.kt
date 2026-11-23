package io.github.iampopovich.tunnelslide

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.TimeUtils
import io.github.iampopovich.tunnelslide.audio.AudioEngine
import io.github.iampopovich.tunnelslide.audio.Sfx
import io.github.iampopovich.tunnelslide.debug.DebugCapture
import io.github.iampopovich.tunnelslide.debug.DebugOptions
import io.github.iampopovich.tunnelslide.input.SteeringInput
import io.github.iampopovich.tunnelslide.render.BlockRenderer
import io.github.iampopovich.tunnelslide.render.Effects
import io.github.iampopovich.tunnelslide.render.Hud
import io.github.iampopovich.tunnelslide.render.MenuRenderer
import io.github.iampopovich.tunnelslide.render.PostProcess
import io.github.iampopovich.tunnelslide.render.Palette
import io.github.iampopovich.tunnelslide.render.SurfaceUniforms
import io.github.iampopovich.tunnelslide.render.TunnelRenderer
import io.github.iampopovich.tunnelslide.ui.Level
import io.github.iampopovich.tunnelslide.ui.MenuAction
import io.github.iampopovich.tunnelslide.ui.MenuModel
import io.github.iampopovich.tunnelslide.ui.MenuScreen
import io.github.iampopovich.tunnelslide.ui.Settings
import io.github.iampopovich.tunnelslide.ui.Stereo
import io.github.iampopovich.tunnelslide.ui.Style
import io.github.iampopovich.tunnelslide.world.Autopilot
import io.github.iampopovich.tunnelslide.world.HazardKind
import io.github.iampopovich.tunnelslide.world.ObstacleField
import io.github.iampopovich.tunnelslide.world.RunEvent
import io.github.iampopovich.tunnelslide.world.RunState
import io.github.iampopovich.tunnelslide.world.ZoneKind
import io.github.iampopovich.tunnelslide.world.gen.Pattern

/** Entry point shared by all platforms. */
class TunnelSlideGame(private val debug: DebugOptions = DebugOptions.NONE) : ApplicationAdapter() {
    private val capture = DebugCapture.from(debug)

    private lateinit var camera: PerspectiveCamera
    private lateinit var tunnel: TunnelRenderer
    private lateinit var blocks: BlockRenderer
    private lateinit var hud: Hud
    private lateinit var steering: SteeringInput
    private lateinit var prefs: Preferences
    private val run = RunState()
    private val fx = Effects()
    private val audio = AudioEngine()
    private val settings = Settings()
    private val menu = MenuModel(settings)
    private lateinit var menuView: MenuRenderer
    private lateinit var post: PostProcess
    /** Menu is up: the tunnel behind it is an attract-mode run driven by the bot. */
    private var inMenu = true
    private var clock = 0f
    /** Back from the platform (Android back gesture), consumed on the render thread. */
    @Volatile private var backRequested = false

    fun requestBack() { backRequested = true }

    private fun backPressed(): Boolean {
        val b = backRequested || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)
        backRequested = false
        return b
    }
    private val surface = SurfaceUniforms()
    private val field = ObstacleField(seed = 1L)
    private var accumulator = 0f
    private var crashAge = -1f
    private var best = 0

    override fun create() {
        Gdx.app.log(TAG, "TunnelSlide3D started on ${Gdx.app.type}")
        camera = PerspectiveCamera(GameConfig.BASE_FOV, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.near = 0.05f
        camera.far = GameConfig.VIEW_DISTANCE + 10f
        tunnel = TunnelRenderer()
        blocks = BlockRenderer()
        hud = Hud()
        menuView = MenuRenderer(hud)
        post = PostProcess()
        steering = SteeringInput()
        // Back is routed by the platform launcher into requestBack(): libGDX's own catch would
        // register a second predictive-back callback on Android 13+.
        prefs = Gdx.app.getPreferences("tunnelslide3d")
        loadSettings()
        if (debug.has("TS_MONO")) settings.mono = true
        if (debug.has("TS_NOAUDIO")) settings.sound = false
        if (debug.has("TS_CLASSIC")) settings.style = Style.CLASSIC
        debug["TS_STEREO"]?.let { n -> Stereo.entries.firstOrNull { it.name.equals(n, true) }?.let { settings.stereo = it } }
        debug["TS_LEVEL"]?.let { n -> Level.entries.firstOrNull { it.name.equals(n, true) }?.let { settings.level = it } }
        applySettings(save = false)
        // Automated runs (autopilot, screenshots) skip the menu unless TS_MENU asks for it.
        inMenu = debug.has("TS_MENU") || !(debug.has("TS_AUTOPILOT") || debug.has("TS_SCREENSHOT"))
        if (debug.has("TS_AUTOPILOT")) {
            steering.autopilot = { _ -> Autopilot.steer(run, field) }
        }
        field.difficultyOverride = debug["TS_DIFFICULTY"]?.toFloatOrNull()
        field.forcedPattern = debug["TS_PATTERN"]?.let { name ->
            Pattern.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        field.hazards.forcedFirst = debug["TS_HAZARD"]?.let { name ->
            HazardKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        field.zones.forcedFirst = debug["TS_ZONE"]?.let { name ->
            ZoneKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        newRun()
    }

    private fun newRun() {
        run.reset()
        field.reset(debug["TS_SEED"]?.toLongOrNull() ?: TimeUtils.millis())
        field.update(run.playerZ, run.speed)
        accumulator = 0f
        crashAge = -1f
        fx.reset()
        if (::post.isInitialized) post.restart()
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        hud.resize(width, height)
        menuView.resize(width, height)
        post.resize(width, height)
    }

    private fun loadSettings() {
        settings.level = Level.entries.firstOrNull { it.name == prefs.getString(PREF_LEVEL, "") } ?: Level.NORMAL
        settings.customStart = prefs.getFloat(PREF_CUSTOM, 0.5f)
        settings.mono = prefs.getBoolean(PREF_MONO, false)
        settings.sound = prefs.getBoolean(PREF_SOUND, true)
        settings.neutral = prefs.getFloat(PREF_NEUTRAL, 0f)
        settings.style = Style.entries.firstOrNull { it.name == prefs.getString(PREF_STYLE, "") } ?: Style.VIBRANT
        settings.stereo = Stereo.entries.firstOrNull { it.name == prefs.getString(PREF_STEREO, "") } ?: Stereo.OFF
    }

    /** Pushes the settings into the game systems and (optionally) saves them. */
    private fun applySettings(save: Boolean = true) {
        surface.mono = settings.mono
        steering.neutral = settings.neutral
        field.curve = { z -> settings.difficultyAt(z, GameConfig.DIFFICULTY_RAMP_DISTANCE) }
        best = prefs.getInteger(bestKey(), 0)
        if (settings.sound) audio.start() else audio.stop()
        if (!save) return
        prefs.putString(PREF_LEVEL, settings.level.name)
        prefs.putFloat(PREF_CUSTOM, settings.customStart)
        prefs.putBoolean(PREF_MONO, settings.mono)
        prefs.putBoolean(PREF_SOUND, settings.sound)
        prefs.putFloat(PREF_NEUTRAL, settings.neutral)
        prefs.putString(PREF_STYLE, settings.style.name)
        prefs.putString(PREF_STEREO, settings.stereo.name)
        prefs.flush()
    }

    /** Best distance is kept per difficulty level (NORMAL keeps the old key). */
    private fun bestKey() = if (settings.level == Level.NORMAL) PREF_BEST else "${PREF_BEST}_${settings.level.name}"

    private fun handleMenuInput(back: Boolean) {
        val keys = Gdx.input
        if (keys.isKeyJustPressed(Input.Keys.UP)) menu.move(-1)
        if (keys.isKeyJustPressed(Input.Keys.DOWN)) menu.move(1)
        var action = MenuAction.NONE
        when {
            keys.isKeyJustPressed(Input.Keys.ENTER) || keys.isKeyJustPressed(Input.Keys.SPACE) -> action = menu.press()
            keys.isKeyJustPressed(Input.Keys.LEFT) && menu.items()[menu.selected].adjustable -> action = menu.press(side = -1)
            keys.isKeyJustPressed(Input.Keys.RIGHT) && menu.items()[menu.selected].adjustable -> action = menu.press(side = 1)
            back -> action = menu.back()
            keys.justTouched() -> {
                val i = menuView.hit(keys.x, keys.y)
                if (i >= 0) action = menu.press(i, menuView.side(i, keys.x))
            }
        }
        when (action) {
            MenuAction.PLAY -> startGame()
            MenuAction.CHANGED -> { applySettings(); audio.synth.play(Sfx.PICKUP) }
            MenuAction.CAPTURE_NEUTRAL -> {
                settings.neutral = steering.tiltReading()
                applySettings()
                audio.synth.play(Sfx.FOLD)
            }
            MenuAction.EXIT -> Gdx.app.exit()
            MenuAction.NONE -> {}
        }
    }

    private fun startGame() {
        inMenu = false
        newRun()
    }

    private fun openMenu() {
        inMenu = true
        menu.reset()
        newRun()
    }

    override fun render() {
        val frame = minOf(Gdx.graphics.deltaTime, GameConfig.MAX_FRAME_TIME)
        clock += frame
        // Desktop shortcuts for the two display/sound settings.
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) { settings.mono = !settings.mono; applySettings() }
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) { settings.sound = !settings.sound; applySettings() }
        val back = backPressed()
        if (inMenu) {
            handleMenuInput(back)
            stepAttract(frame)
        } else if (back) {
            openMenu()
        } else if (run.crashed) {
            crashAge += frame
            val wantsRestart = Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            if (crashAge > Hud.RESTART_DELAY && wantsRestart) newRun()
        } else {
            // Bonus: SPACE on desktop, a tap on phones (tilt steers there).
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || (steering.tilt && Gdx.input.justTouched()) ||
                debug.has("TS_AUTOBONUS") && run.slot != null) {
                run.activate(field)
            }
            // Time dilation slows the game clock itself, so obstacle timing stays consistent.
            accumulator += frame * run.timeScale
            val steer = steering.read(frame, run.time)
            logSensors(frame, steer)
            while (accumulator >= GameConfig.FIXED_STEP && !run.crashed) {
                run.step(GameConfig.FIXED_STEP, steer, field)
                accumulator -= GameConfig.FIXED_STEP
            }
            if (run.crashed) onCrash()
            run.event?.let { fx.trigger(it); audio.synth.play(sfxFor(it)); run.event = null }
        }
        fx.update(frame)
        audio.synth.speed = run.speed
        audio.synth.timeScale = run.timeScale
        audio.synth.musicOn = !run.crashed

        surface.update(run, field.zones)
        surface.updateHazards(run, field.hazards)
        val stereo = settings.stereo.ordinal
        val blur = if (settings.style == Style.CLASSIC) classicBlur() else 0f
        if (stereo == 0 && blur < MIN_BLUR && surface.swirl == 0f) {
            drawScene(0f)
            // The history buffer is stale now; the blur must not start from an old frame.
            post.restart()
        } else {
            post.captureEye(0) { drawScene(if (stereo > 0) -EYE_SEPARATION / 2f else 0f) }
            if (stereo > 0) post.captureEye(1) { drawScene(EYE_SEPARATION / 2f) }
            post.present(stereo, blur, surface.swirl)
        }
        if (inMenu) {
            val hint = if (menu.screen == MenuScreen.CALIBRATE) {
                if (steering.tilt) "HOLD THE PHONE AS YOU PLAY, THEN TAP SET STRAIGHT   (TILT %+.1f)".format(steering.tiltReading() - settings.neutral)
                else "NO TILT SENSOR ON THIS DEVICE"
            } else if (menu.screen == MenuScreen.MAIN) {
                if (steering.tilt) "TILT TO STEER  -  TAP TO USE A BONUS" else "ARROWS TO STEER  -  SPACE FOR BONUS  -  ESC FOR MENU"
            } else null
            menuView.render(menu, best, hint, clock)
        } else {
            hud.render(run, best, crashAge, fx, field.hazards)
        }
        capture?.onFrameRendered()
    }

    private var sensorLogTimer = 0f

    /** TS_SENSORLOG: logs rotation, accelerometer and steering once a second. */
    private fun logSensors(frame: Float, steer: Float) {
        if (!debug.has("TS_SENSORLOG")) return
        sensorLogTimer += frame
        if (sensorLogTimer < 1f) return
        sensorLogTimer = 0f
        Gdx.app.log(TAG, "sensors rot=${Gdx.input.rotation} ax=%.2f ay=%.2f az=%.2f right=%.2f steer=%.2f fps=%d".format(
            Gdx.input.accelerometerX, Gdx.input.accelerometerY, Gdx.input.accelerometerZ,
            steering.screenRightAccel(), steer, Gdx.graphics.framesPerSecond))
    }

    private fun sfxFor(e: RunEvent) = when (e) {
        RunEvent.PICKUP -> Sfx.PICKUP
        RunEvent.SHIELD_HIT -> Sfx.SHIELD
        RunEvent.QUAKE -> Sfx.QUAKE
        RunEvent.SLOWMO -> Sfx.SLOWMO
        RunEvent.FOLD -> Sfx.FOLD
        RunEvent.VORTEX -> Sfx.VORTEX
    }

    override fun pause() { audio.stop() }

    override fun resume() { if (settings.sound) audio.start() }

    /** Menu background: the bot plays; when it crashes a new run starts silently. */
    private fun stepAttract(frame: Float) {
        if (run.crashed) { newRun(); return }
        if (run.slot != null) run.activate(field)
        accumulator += frame * run.timeScale
        while (accumulator >= GameConfig.FIXED_STEP && !run.crashed) {
            run.step(GameConfig.FIXED_STEP, Autopilot.steer(run, field), field)
            accumulator -= GameConfig.FIXED_STEP
        }
        run.event = null
    }

    private fun onCrash() {
        crashAge = 0f
        audio.synth.play(Sfx.CRASH)
        Gdx.input.vibrate(120)
        fx.reset()
        if (run.score > best) {
            best = run.score
            prefs.putInteger(PREF_BEST, best).flush()
        }
    }

    /**
     * Classic style: share of the previous frame kept. None at the start, then it grows with
     * speed (eased in), so the trails themselves show the acceleration.
     */
    private fun classicBlur(): Float {
        val k = ((run.speed - GameConfig.START_SPEED) / (GameConfig.MAX_SPEED - GameConfig.START_SPEED)).coerceIn(0f, 1f)
        val eased = k * k * (3f - 2f * k)
        return CLASSIC_MAX_BLUR * eased
    }

    /** One view of the 3D scene; [eyeOffset] shifts the camera sideways for stereo. */
    private fun drawScene(eyeOffset: Float) {
        updateCamera(eyeOffset)
        // In a blackout the background (fog) turns light as well.
        val bg = MathUtils.lerp(Palette.FOG.r, 0.85f, surface.invertTunnel)
        Gdx.gl.glClearColor(bg, MathUtils.lerp(Palette.FOG.g, 0.85f, surface.invertTunnel), MathUtils.lerp(Palette.FOG.b, 0.85f, surface.invertTunnel), 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        tunnel.render(camera, run, surface)
        blocks.render(camera, run, field, surface)
    }

    private fun updateCamera(eyeOffset: Float = 0f) {
        val eyeY = -GameConfig.TUNNEL_RADIUS + GameConfig.CAMERA_HEIGHT
        camera.fieldOfView = minOf(
            GameConfig.MAX_FOV,
            GameConfig.BASE_FOV + (run.speed - GameConfig.START_SPEED) * GameConfig.FOV_PER_SPEED,
        )
        camera.position.set(fx.shakeX, eyeY + fx.shakeY, 0f)
        camera.direction.set(0f, -0.06f, -1f).nor()
        camera.up.set(0f, 1f, 0f)
        // Roll slightly into the turn: makes steering readable even in a symmetric tube.
        val roll = run.turnRate / GameConfig.MAX_TURN_RATE * GameConfig.ROLL_PER_TURN + surface.hazardRoll
        camera.rotate(camera.direction, roll)
        if (eyeOffset != 0f) {
            // Shift along the camera's own right axis (it may be rolled), then turn both eyes
            // to a common point ahead (toe-in): things at that distance sit on the screen
            // plane, nearer ones pop out. Parallel eyes would put the whole tunnel in front
            // of the screen with huge disparity on the near walls.
            val focus = camera.position.cpy().mulAdd(camera.direction, STEREO_FOCUS)
            val right = camera.direction.cpy().crs(camera.up).nor()
            camera.position.mulAdd(right, eyeOffset)
            camera.direction.set(focus).sub(camera.position).nor()
        }
        camera.update()
    }

    override fun dispose() {
        audio.stop()
        tunnel.dispose()
        blocks.dispose()
        hud.dispose()
        menuView.dispose()
        post.dispose()
    }

    companion object {
        const val TAG = "TunnelSlide3D"
        private const val PREF_BEST = "best"
        private const val PREF_MONO = "mono"
        private const val PREF_SOUND = "sound"
        private const val PREF_LEVEL = "level"
        private const val PREF_CUSTOM = "customStart"
        private const val PREF_NEUTRAL = "neutral"
        private const val PREF_STYLE = "style"
        private const val PREF_STEREO = "stereo"
        /** Distance between the two stereo cameras, world units (tunnel radius is the scale). */
        private const val EYE_SEPARATION = 0.22f
        /** Distance at which the two eyes converge (zero parallax). */
        private const val STEREO_FOCUS = 10f
        /** Classic blur at top speed; below MIN_BLUR the post pass is skipped. */
        private const val CLASSIC_MAX_BLUR = 0.82f
        private const val MIN_BLUR = 0.02f
    }
}
