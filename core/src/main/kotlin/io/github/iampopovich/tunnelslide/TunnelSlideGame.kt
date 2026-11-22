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
import io.github.iampopovich.tunnelslide.input.SteeringInput
import io.github.iampopovich.tunnelslide.render.BlockRenderer
import io.github.iampopovich.tunnelslide.render.Hud
import io.github.iampopovich.tunnelslide.render.Palette
import io.github.iampopovich.tunnelslide.render.TunnelRenderer
import io.github.iampopovich.tunnelslide.world.Autopilot
import io.github.iampopovich.tunnelslide.world.ObstacleField
import io.github.iampopovich.tunnelslide.world.RunState

/** Entry point shared by all platforms. */
class TunnelSlideGame : ApplicationAdapter() {
    private val capture = DebugCapture.fromEnv()

    private lateinit var camera: PerspectiveCamera
    private lateinit var tunnel: TunnelRenderer
    private lateinit var blocks: BlockRenderer
    private lateinit var hud: Hud
    private lateinit var steering: SteeringInput
    private lateinit var prefs: Preferences
    private val run = RunState()
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
        if (System.getenv("TS_AUTOPILOT") != null) {
            steering.autopilot = { _ -> Autopilot.steer(run, field) }
        }
        newRun()
    }

    private fun newRun() {
        run.reset()
        field.reset(System.getenv("TS_SEED")?.toLongOrNull() ?: TimeUtils.millis())
        field.update(run.playerZ, run.speed)
        accumulator = 0f
        crashAge = -1f
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        hud.resize(width, height)
    }

    override fun render() {
        val frame = minOf(Gdx.graphics.deltaTime, GameConfig.MAX_FRAME_TIME)
        if (run.crashed) {
            crashAge += frame
            val wantsRestart = Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            if (crashAge > Hud.RESTART_DELAY && wantsRestart) newRun()
        } else {
            accumulator += frame
            val steer = steering.read(frame, run.time)
            while (accumulator >= GameConfig.FIXED_STEP && !run.crashed) {
                run.step(GameConfig.FIXED_STEP, steer, field)
                accumulator -= GameConfig.FIXED_STEP
            }
            if (run.crashed) onCrash()
        }

        updateCamera()
        Gdx.gl.glClearColor(Palette.FOG.r, Palette.FOG.g, Palette.FOG.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        tunnel.render(camera, run)
        blocks.render(camera, run, field.obstacles)
        hud.render(run, best, crashAge)
        capture?.onFrameRendered()
    }

    private fun onCrash() {
        crashAge = 0f
        Gdx.input.vibrate(120)
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
        camera.position.set(0f, eyeY, 0f)
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
    }
}
