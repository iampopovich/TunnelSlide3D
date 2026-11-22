package io.github.iampopovich.tunnelslide

/** Tunable constants. Values are our own guesses from gameplay videos (see docs/SPEC.md). */
object GameConfig {
    // Geometry
    const val TUNNEL_RADIUS = 4f
    const val CAMERA_HEIGHT = 0.75f          // eye height above the surface
    const val VIEW_DISTANCE = 140f           // how far ahead the tunnel is drawn
    const val TUNNEL_SIDES = 72              // mesh columns around the tube
    const val TUNNEL_RINGS = 200             // mesh rows along the tube

    // Surface pattern
    const val LANES = 24f                    // panel columns per revolution
    const val RING_LENGTH = 6f               // panel length along z

    // Motion
    const val START_SPEED = 18f              // units per second
    const val ACCELERATION = 0.22f           // speed gain per second
    const val MAX_SPEED = 75f
    const val MAX_TURN_RATE = 0.55f          // revolutions per second at full tilt
    const val TURN_RESPONSE = 12f            // how fast turn rate follows input, 1/s

    // Camera feel
    const val BASE_FOV = 68f
    const val FOV_PER_SPEED = 0.30f
    const val MAX_FOV = 95f
    const val ROLL_PER_TURN = 14f            // degrees of camera roll at max turn rate

    // Simulation
    const val FIXED_STEP = 1f / 120f
    const val MAX_FRAME_TIME = 0.25f
}
