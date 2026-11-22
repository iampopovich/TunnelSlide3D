package io.github.iampopovich.tunnelslide.world.gen

import com.badlogic.gdx.math.MathUtils
import io.github.iampopovich.tunnelslide.GameConfig

/**
 * Obstacle patterns. Each one lays out its route and blocks through [PatternBuilder].
 * Sizes and counts scale with difficulty. Names are ours (see SPEC "Своё").
 */
enum class Pattern(
    /** Lowest difficulty at which the pattern may appear. */
    val minDifficulty: Float,
    /** Relative pick weight at difficulty 0 and 1 (interpolated). */
    val weightEasy: Float,
    val weightHard: Float,
) {
    /** Loose rows of random blocks: the classic warm-up. */
    SCATTER(0f, 5f, 2f) {
        override fun build(b: PatternBuilder) {
            val rows = 4 + b.rng.nextInt(4)
            val spacing = MathUtils.lerp(17f, 11f, b.difficulty)
            val density = MathUtils.lerp(0.30f, 0.65f, b.difficulty)
            repeat(rows) {
                val rowZ = b.z + spacing * 0.5f
                b.go(spacing, b.randomDrift(spacing))
                for (l in 0 until PatternBuilder.LANES) {
                    if (b.chance(density)) b.block(l, rowZ + b.rng.nextFloat() * GameConfig.ROW_Z_JITTER)
                }
            }
        }
    },

    /**
     * Colonnade: full-span pillars from wall to wall, one per depth slot (every pillar crosses
     * the tunnel axis, two at the same depth would intersect). Consecutive pillars turn by a
     * fixed number of lanes, so the colonnade reads as a twisting screw, not a random star.
     */
    PILLARS(0f, 2f, 2f) {
        override fun build(b: PatternBuilder) {
            val count = 8 + b.rng.nextInt(8)
            val spacing = MathUtils.lerp(9f, 5f, b.difficulty)
            val turn = (1 + b.rng.nextInt(3)) * (if (b.chance(0.5f)) 1 else -1)
            var lane = b.rng.nextInt(PatternBuilder.LANES)
            repeat(count) {
                b.go(spacing, b.randomDrift(spacing, 0.7f))
                b.pillar(lane, b.z - spacing * 0.5f)
                lane += turn
            }
        }
    },

    /**
     * Tick-tock: rows of cubes rolling lane by lane on a common beat. On a tube (inside or
     * outside) they circle the whole circumference; on the flat they sweep from edge to edge.
     * Neighbour rows roll in opposite directions / counter-phase.
     */
    TICKTOCK(0.2f, 0f, 3f) {
        override fun build(b: PatternBuilder) {
            val rows = 4 + b.rng.nextInt(5)
            val spacing = MathUtils.lerp(10f, 6f, b.difficulty)
            repeat(rows) { row ->
                b.go(spacing, b.randomDrift(spacing, 0.6f))
                val z = b.z - spacing * 0.5f
                val seamLane = b.seamLaneHere()
                if (seamLane == null) {
                    // Circle: evenly spaced cubes moving together, so they never overlap.
                    val gap = MathUtils.lerp(8f, 3f, b.difficulty).toInt() + b.rng.nextInt(2)
                    val dir = if (row % 2 == 0) 1 else -1
                    val start = b.rng.nextInt(gap)
                    var lane = start
                    while (lane < PatternBuilder.LANES - gap / 2) {
                        b.roller(lane, z, phase = 0f, dir = dir)
                        lane += gap
                    }
                } else {
                    // Flat: the usable width is between the edges; split it into sweep tracks.
                    val width = PatternBuilder.LANES - 2
                    val tracks = if (b.chance(0.4f)) 1 else 2 + b.rng.nextInt(2)
                    val len = width / tracks
                    for (t in 0 until tracks) {
                        val home = seamLane + 1 + t * len
                        b.roller(home, z, phase = b.rng.nextInt(2 * (len - 1)).toFloat(), sweepLanes = len)
                    }
                }
            }
        }
    },

    /** One to three full rings, each with a single gap somewhere else. */
    WALLS(0.05f, 3f, 3f) {
        override fun build(b: PatternBuilder) {
            val walls = 1 + b.rng.nextInt(if (b.difficulty < 0.3f) 2 else 3)
            val approach = MathUtils.lerp(26f, 15f, b.difficulty)
            repeat(walls) {
                b.go(approach, b.randomDrift(approach, MathUtils.lerp(0.6f, 1f, b.difficulty)))
                b.settle()
                b.ring(b.z)
                b.go(GameConfig.WALL_LENGTH + 0.5f)
            }
        }
    },

    /** Slalom: a series of walls whose gaps jump left-right as far as the player can turn. */
    GATES(0.25f, 0f, 4f) {
        override fun build(b: PatternBuilder) {
            val gates = 3 + b.rng.nextInt(4)
            val spacing = MathUtils.lerp(20f, 13f, b.difficulty)
            var side = if (b.chance(0.5f)) 1f else -1f
            repeat(gates) {
                b.go(spacing, side * b.reach(spacing) * 0.9f)
                b.settle()
                b.ring(b.z)
                b.go(GameConfig.WALL_LENGTH + 0.5f)
                side = -side
            }
        }
    },

    /** Walls whose gaps wind steadily around the tunnel: you have to keep turning. */
    SPIRAL(0.35f, 0f, 3f) {
        override fun build(b: PatternBuilder) {
            val steps = 6 + b.rng.nextInt(5)
            val spacing = MathUtils.lerp(12f, 8f, b.difficulty)
            val dir = if (b.chance(0.5f)) 1f else -1f
            repeat(steps) {
                b.go(spacing, dir * b.reach(spacing) * 0.8f)
                b.ring(b.z, length = 1.2f)
            }
            b.go(4f)
        }
    },

    /** A winding channel: the route snakes between two thick banks of blocks. */
    CHANNEL(0.5f, 0f, 3f) {
        override fun build(b: PatternBuilder) {
            val length = 60f + b.rng.nextFloat() * 40f
            val seg = 6f
            val amp = b.reach(seg) * 0.85f
            val phase = b.rng.nextFloat() * MathUtils.PI2
            val bank = 6   // lanes on each side of the route that get filled
            val start = b.z
            var t = 0
            while (b.z < start + length) {
                b.go(seg, amp * MathUtils.sin(phase + t * 0.7f))
                t++
            }
            var z = start
            while (z < b.z) {
                val center = b.laneOf(b.path.uAt(z + 1.5f))
                for (k in -bank..bank) b.block(center + k, z, length = 3f, height = GameConfig.BLOCK_HEIGHT * 0.8f)
                z += 3f
            }
        }
    };

    abstract fun build(b: PatternBuilder)

    fun weight(difficulty: Float): Float =
        if (difficulty < minDifficulty) 0f else MathUtils.lerp(weightEasy, weightHard, difficulty)
}
