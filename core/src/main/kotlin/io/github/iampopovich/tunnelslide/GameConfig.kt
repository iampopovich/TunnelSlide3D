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

    // Player body in tunnel coordinates
    const val PLAYER_HALF_WIDTH = 0.006f     // in u (~0.15 world units)
    const val PLAYER_LENGTH = 0.3f

    // Obstacles and generator
    const val FIRST_ROW_DISTANCE = 60f
    const val WALL_LENGTH = 1.5f
    const val PATTERN_PAUSE_EASY = 30f       // empty stretch between patterns
    const val PATTERN_PAUSE_HARD = 10f
    const val ROUTE_MARGIN_EASY = 1.2f       // free space around the route, in lanes
    const val ROUTE_MARGIN_HARD = 0.35f
    const val DIFFICULTY_RAMP_DISTANCE = 6000f  // distance at which difficulty reaches 1
    const val BLOCK_LENGTH = 2f
    const val ROW_Z_JITTER = 4f              // blocks of one row are spread over this depth
    const val BLOCK_HEIGHT = 1.1f
    const val BLOCK_WIDTH_FILL = 0.9f        // block width as a share of the lane
    const val PILLAR_WIDTH_FILL = 0.7f
    const val PILLAR_LENGTH = 0.9f
    const val REACH_SAFETY = 0.55f           // share of max turn the gap may drift per row
    const val TRAIL_LENGTH = 9f              // coloured warning strip in front of a block

    // Surface zones (tube / flat / outside)
    const val ZONE_FIRST_SPECIAL = 700f      // first flat/outside episode starts around here
    const val ZONE_TUBE_MIN = 500f
    const val ZONE_TUBE_MAX = 1000f
    const val ZONE_SPECIAL_MIN = 250f
    const val ZONE_SPECIAL_MAX = 450f
    const val ZONE_TRANSITION = 60f          // distance over which the whole surface morphs
    const val ZONE_CHAIN_CHANCE = 0.3f       // flat -> outside (or back) without a tube between
    const val SETTLE_TIME = 0.18f             // straight run before a wall, seconds
    const val EDGE_MARGIN_LANES = 4f         // route keeps this far from an open edge

    // Moving obstacles
    const val ROLLER_STEP = 0.6f             // seconds per tick: rest, then roll one lane over
    const val ROLLER_REST = 0.55f            // share of a tick the roller stands still
    const val ROUTE_TIME_WINDOW = 0.15f      // +- seconds checked around the player's arrival time

    // Bonuses
    const val PICKUP_CHANCE = 0.45f          // chance to place a bonus in the pause after a pattern
    const val PICKUP_LENGTH = 2.2f
    const val PICKUP_HALF_WIDTH = 1f / 48f   // one lane wide
    const val MAX_SHIELDS = 3
    const val SLOWMO_SCALE = 0.5f            // game clock speed while time dilation is on
    const val SLOWMO_DURATION = 2.5f         // game seconds (5 real seconds at half speed)

    // Simulation
    const val FIXED_STEP = 1f / 120f
    const val MAX_FRAME_TIME = 0.25f
}
