package io.github.iampopovich.tunnelslide.audio

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SynthTest {
    private fun render(seconds: Float, setup: (Synth) -> Unit = {}): FloatArray {
        val s = Synth(22050); setup(s)
        val out = FloatArray((seconds * s.rate).toInt())
        var i = 0
        val buf = FloatArray(512)
        while (i < out.size) {
            val n = minOf(buf.size, out.size - i)
            s.render(buf, n); System.arraycopy(buf, 0, out, i, n); i += n
        }
        return out
    }

    private fun rms(a: FloatArray) = sqrt(a.sumOf { (it * it).toDouble() } / a.size).toFloat()

    @Test
    fun musicIsAudibleAndNeverClips() {
        val a = render(4f) { it.play(Sfx.QUAKE); it.play(Sfx.CRASH); it.play(Sfx.SHIELD) }
        assertTrue(a.all { abs(it) < 1f })
        assertTrue(rms(a) > 0.05f, "rms ${rms(a)}")
    }

    @Test
    fun renderingIsDeterministic() {
        assertContentEquals(render(1f), render(1f))
    }

    @Test
    fun effectsAreHeardOnTopOfTheMusic() {
        val quiet = render(0.5f) { it.musicOn = false }
        val fx = render(0.5f) { it.musicOn = false; it.play(Sfx.PICKUP) }
        assertTrue(rms(fx) > rms(quiet) * 1.5f)
    }

    @Test
    fun tempoFollowsSpeed() {
        val s = Synth()
        assertTrue(s.bpm(GameConfig.START_SPEED) == Synth.BPM_MIN)
        assertTrue(s.bpm(40f) > s.bpm(25f))
        assertTrue(s.bpm(GameConfig.MAX_SPEED) <= Synth.BPM_MAX)
    }
}
