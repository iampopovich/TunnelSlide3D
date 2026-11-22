package io.github.iampopovich.tunnelslide.world

/**
 * A block lying on the tunnel surface, in tunnel coordinates.
 * [u] is the centre across the tunnel (absolute, [0, 1)), [z] is the near edge.
 */
class Obstacle(
    val u: Float,
    val halfWidth: Float,
    val z: Float,
    val length: Float,
    val height: Float,
    val colorIndex: Int,
)
