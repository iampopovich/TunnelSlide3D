package io.github.iampopovich.tunnelslide

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.debug.DebugCapture
import io.github.iampopovich.tunnelslide.input.SteeringInput
import io.github.iampopovich.tunnelslide.render.Hud
import io.github.iampopovich.tunnelslide.render.Palette
import io.github.iampopovich.tunnelslide.render.TunnelRenderer
import io.github.iampopovich.tunnelslide.world.RunState

/** Entry point shared by all platforms. */
class TunnelSlideGame : ApplicationAdapter() {
    private val capture = DebugCapture.fromEnv()

    private lateinit var camera: PerspectiveCamera
    private lateinit var tunnel: TunnelRenderer
    private lateinit var hud: Hud
    private lateinit var steering: SteeringInput
    private val run = RunState()
    private var accumulator = 0f

    override fun create() {
        Gdx.app.log(TAG, "TunnelSlide3D started on ${Gdx.app.type}")
        camera = PerspectiveCamera(GameConfig.BASE_FOV, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.near = 0.05f
        camera.far = GameConfig.VIEW_DISTANCE + 10f
        tunnel = TunnelRenderer()
        hud = Hud()
        steering = SteeringInput()
        if (System.getenv("TS_AUTOPILOT") != null) {
            steering.autopilot = { t -> MathUtils.sin(t * 0.9f) }
        }
    }

    override fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        hud.resize(width, height)
    }

    override fun render() {
        val frame = minOf(Gdx.graphics.deltaTime, GameConfig.MAX_FRAME_TIME)
        accumulator += frame
        val steer = steering.read(frame, run.time)
        while (accumulator >= GameConfig.FIXED_STEP) {
            run.step(GameConfig.FIXED_STEP, steer)
            accumulator -= GameConfig.FIXED_STEP
        }

        updateCamera()
        Gdx.gl.glClearColor(Palette.FOG.r, Palette.FOG.g, Palette.FOG.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        tunnel.render(camera, run)
        hud.render(run)
        capture?.onFrameRendered()
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
        hud.dispose()
    }

    companion object {
        const val TAG = "TunnelSlide3D"
    }
}
