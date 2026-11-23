package io.github.iampopovich.tunnelslide.audio

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The audio thread renders 44100 samples a second; any allocation per sample turns into
 * megabytes of garbage and GC pauses on the phone. Rendering must stay (almost) allocation-free.
 */
class AudioAllocationTest {
    private val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun allocatedWhile(block: () -> Unit): Long {
        val id = @Suppress("DEPRECATION") Thread.currentThread().id
        val before = mx.getThreadAllocatedBytes(id)
        block()
        return mx.getThreadAllocatedBytes(id) - before
    }

    private fun bytesFor(style: MusicStyle): Long {
        val s = Synth(44100); s.style = style
        val buf = FloatArray(1024)
        // Warm-up with effects too: the first cold calls are JIT (de)optimisation, not garbage.
        repeat(1000) { i -> if (i % 40 == 0) Sfx.entries.forEach { s.play(it) }; s.render(buf, buf.size) }
        return allocatedWhile {
            // ~10 s of music with every sound effect on top.
            repeat(430) { i ->
                if (i % 40 == 0) Sfx.entries.forEach { s.play(it) }
                s.render(buf, buf.size)
            }
        }
    }

    @Test
    fun calmMusicRendersWithoutGarbage() {
        // Allowed: new pad voices on chord changes and the queued effect voices (a few KB).
        val bytes = bytesFor(MusicStyle.CALM)
        assertTrue(bytes < 64 * 1024, "calm music allocated $bytes bytes in 10 s")
    }

    @Test
    fun driveMusicRendersWithoutGarbage() {
        val bytes = bytesFor(MusicStyle.DRIVE)
        assertTrue(bytes < 64 * 1024, "drive music allocated $bytes bytes in 10 s")
    }
}
