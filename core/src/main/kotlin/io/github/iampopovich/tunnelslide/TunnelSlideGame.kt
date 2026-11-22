package io.github.iampopovich.tunnelslide

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.TimeUtils
import io.github.iampopovich.tunnelslide.debug.DebugCapture
import io.github.iampopovich.tunnelslide.debug.DebugOptions
import io.github.iampopovich.tunnelslide.input.SteeringInput
import io.github.iampopovich.tunnelslide.render.BlockRenderer
import io.github.iampopovich.tunnelslide.render.Effects
import io.github.iampopovich.tunnelslide.render.Hud
import io.github.iampopovich.tunnelslide.render.Palette
import io.github.iampopovich.tunnelslide.render.SurfaceUniforms
import io.github.iampopovich.tunnelslide.render.TunnelRenderer
import io.github.iampopovich.tunnelslide.world.Autopilot
import io.github.iampopovich.tunnelslide.world.ObstacleField
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
        steering = SteeringInput()
        prefs = Gdx.app.getPreferences("tunnelslide3d")
        best = prefs.getInteger(PREF_BEST, 0)
        surface.mono = debug.has("TS_MONO") || prefs.getBoolean(PREF_MONO, false)
        if (debug.has("TS_AUTOPILOT")) {
            steering.autopilot = { _ -> Autopilot.steer(run, field) }
        }
        field.difficultyOverride = debug["TS_DIFFICULTY"]?.toFloatOrNull()
        field.forcedPattern = debug["TS_PATTERN"]?.let { name ->
            Pattern.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
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
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        hud.resize(width, height)
    }

    override fun render() {
        val frame = minOf(Gdx.graphics.deltaTime, GameConfig.MAX_FRAME_TIME)
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            // Monochrome toggle; goes into the settings menu in the menu batch.
            surface.mono = !surface.mono
            prefs.putBoolean(PREF_MONO, surface.mono).flush()
        }
        if (run.crashed) {
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
            run.event?.let { fx.trigger(it); run.event = null }
        }
        fx.update(frame)

        updateCamera()
        Gdx.gl.glClearColor(Palette.FOG.r, Palette.FOG.g, Palette.FOG.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        surface.update(run, field.zones)
        tunnel.render(camera, run, surface)
        blocks.render(camera, run, field, surface)
        hud.render(run, best, crashAge, fx)
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

    private fun onCrash() {
        crashAge = 0f
        Gdx.input.vibrate(120)
        fx.reset()
        if (run.score > best) {
            best = run.score
            prefs.putInteger(PREF_BEST, best).flush()
        }
    }

    private fun updateCamera() {
        val eyeY = -GameConfig.TUNNEL_RADIUS + GameConfig.CAMERA_HEIGHT
        camera.fieldOfView = minOf(
            GameConfig.MAX_FOV,
            GameConfig.BASE_FOV + (run.speed - GameConfig.START_SPEED) * GameConfig.FOV_PER_SPEED,
        )
        camera.position.set(fx.shakeX, eyeY + fx.shakeY, 0f)
        camera.direction.set(0f, -0.06f, -1f).nor()
        camera.up.set(0f, 1f, 0f)
        // Roll slightly into the turn: makes steering readable even in a symmetric tube.
        val roll = run.turnRate / GameConfig.MAX_TURN_RATE * GameConfig.ROLL_PER_TURN
        camera.rotate(camera.direction, roll)
        camera.update()
    }

    override fun dispose() {
        tunnel.dispose()
        blocks.dispose()
        hud.dispose()
    }

    companion object {
        const val TAG = "TunnelSlide3D"
        private const val PREF_BEST = "best"
        private const val PREF_MONO = "mono"
    }
}
