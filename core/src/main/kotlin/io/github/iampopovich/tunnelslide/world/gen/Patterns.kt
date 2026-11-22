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

    /** Sparse tall pillars: a breather between hard patterns. */
    PILLARS(0f, 2f, 1f) {
        override fun build(b: PatternBuilder) {
            val length = 50f + b.rng.nextFloat() * 30f
            val end = b.z + length
            val count = (length * MathUtils.lerp(0.25f, 0.6f, b.difficulty)).toInt()
            val start = b.z
            b.go(length * 0.5f, b.randomDrift(length * 0.5f, 0.5f))
            b.go(length * 0.5f, b.randomDrift(length * 0.5f, 0.5f))
            repeat(count) {
                b.block(b.rng.nextInt(PatternBuilder.LANES), MathUtils.lerp(start, end, b.rng.nextFloat()),
                    length = 1.2f, height = GameConfig.BLOCK_HEIGHT * 1.8f)
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
