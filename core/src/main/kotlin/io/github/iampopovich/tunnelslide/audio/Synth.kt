package io.github.iampopovich.tunnelslide.audio

import io.github.iampopovich.tunnelslide.GameConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

enum class Sfx { PICKUP, SHIELD, QUAKE, SLOWMO, FOLD, VORTEX, CRASH, CLOSE_CALL }

/**
 * Procedural soundtrack and effects, rendered sample by sample (no audio files).
 * Music, by [style]: CALM - generative ambient piano ([CalmMusic]); DRIVE - a 4-bar loop
 * (Am - F - C - G) of kick, hi-hat, bass and arpeggio whose tempo follows the speed.
 * Pure Kotlin, no libGDX: rendered on the audio thread and unit tested.
 *
 * Thread safety: the game thread only writes the @Volatile fields and queues effects.
 */
class Synth(val rate: Int = 44100) {
    @Volatile var speed = GameConfig.START_SPEED
    /** Game clock rate: time dilation slows the music and drops its pitch. */
    @Volatile var timeScale = 1f
    @Volatile var musicOn = true
    @Volatile var volume = 0.8f
    @Volatile var style = MusicStyle.CALM

    private val calm = CalmMusic(rate)

    // Effects requested by the game thread; guarded by its own monitor (a plain deque +
    // synchronized works on every backend, java.util.concurrent is missing in TeaVM).
    private val queue = ArrayDeque<Sfx>()
    fun play(s: Sfx) { synchronized(queue) { queue.addLast(s) } }

    /** Tempo in beats per minute for a given speed. */
    fun bpm(speed: Float): Float =
        (BPM_MIN + (speed - GameConfig.START_SPEED) * BPM_PER_SPEED).coerceIn(BPM_MIN, BPM_MAX)

    // --- sequencer state ---
    private var stepPhase = 0.0      // 0..1 inside the current 16th
    private var step = 0             // 0..63 over the 4-bar loop
    private var musicGain = 1f       // fades music in/out

    // --- music voices ---
    private var kickT = 1e9f; private var kickPhase = 0f
    private var hatT = 1e9f
    private var bassT = 1e9f; private var bassPhase = 0f; private var bassFreq = 55f; private var bassLp = 0f
    private var arpT = 1e9f; private var arpPhase = 0f; private var arpFreq = 440f
    private var humPhase = 0f
    private var noise = 0x2545F491

    private class Voice(val kind: Sfx) { var t = 0f; var phase = 0f }
    private val voices = ArrayList<Voice>()

    fun render(out: FloatArray, n: Int) {
        synchronized(queue) {
            while (queue.isNotEmpty()) { val s = queue.removeFirst(); if (voices.size < 8) voices.add(Voice(s)) }
        }
        val dt = 1f / rate
        val scale = timeScale
        val pitch = if (scale < 1f) 0.75f else 1f
        val stepsPerSample = bpm(speed) * scale / 60.0 * 4.0 / rate
        val targetGain = if (musicOn) 1f else 0f
        val drive = style == MusicStyle.DRIVE
        val k = ((speed - GameConfig.START_SPEED) / (GameConfig.MAX_SPEED - GameConfig.START_SPEED)).coerceIn(0f, 1f)
        for (i in 0 until n) {
            musicGain += (targetGain - musicGain) * 0.0002f
            var m = if (drive) driveSample(dt, pitch, stepsPerSample) else calm.sample(k, scale, pitch)
            // Engine hum, a little higher the faster you go (quieter under the calm music).
            humPhase = (humPhase + (40f + speed * 0.9f) * pitch * dt) % 1f
            m += sin(2f * PI.toFloat() * humPhase) * if (drive) 0.06f else 0.025f

            var s = m * musicGain
            for (j in 0 until voices.size) s += sfx(voices[j], dt)
            // Soft clip keeps |out| < 1 no matter how many effects overlap.
            val x = s * volume
            out[i] = x / (1f + abs(x))
        }
        for (j in voices.size - 1 downTo 0) if (voices[j].t >= duration(voices[j].kind)) voices.removeAt(j)
    }

    private fun driveSample(dt: Float, pitch: Float, stepsPerSample: Double): Float {
            stepPhase += stepsPerSample
            if (stepPhase >= 1.0) { stepPhase -= 1.0; step = (step + 1) % 64; trigger(step) }

            var m = 0f
            // Kick: pitch sweep 140 -> 45 Hz.
            kickT += dt
            if (kickT < 0.35f) {
                val f = 45f + 95f * exp(-kickT * 30f)
                kickPhase += f * dt
                m += sin(2f * PI.toFloat() * kickPhase) * exp(-kickT * 9f) * 0.9f
            }
            // Hi-hat: short noise burst.
            hatT += dt
            if (hatT < 0.05f) m += rnd() * exp(-hatT * 90f) * 0.18f
            // Bass: low-passed saw.
            bassT += dt
            if (bassT < 0.25f) {
                bassPhase = (bassPhase + bassFreq * pitch * dt) % 1f
                val saw = bassPhase * 2f - 1f
                bassLp += (saw - bassLp) * 0.08f
                m += bassLp * exp(-bassT * 6f) * 0.5f
            }
            // Arpeggio: soft square, gated.
            arpT += dt
            if (arpT < 0.09f) {
                arpPhase = (arpPhase + arpFreq * pitch * dt) % 1f
                val sq = if (arpPhase < 0.5f) 1f else -1f
                m += sq * exp(-arpT * 25f) * 0.09f
            }
            return m
    }

    private fun trigger(step: Int) {
        val inBar = step % 16
        if (inBar % 4 == 0) { kickT = 0f; kickPhase = 0f }
        if (inBar % 4 == 2) hatT = 0f
        val root = CHORDS[step / 16]
        if (inBar % 2 == 0) {
            bassT = 0f
            bassFreq = note(root - 24 + if (inBar % 8 == 6) 7 else 0)
        }
        arpT = 0f
        arpFreq = note(root + ARP[inBar % ARP.size])
    }

    private fun sfx(v: Voice, dt: Float): Float {
        val d = duration(v.kind)
        val x = (v.t / d).coerceIn(0f, 1f)
        val env = (1f - x) * (1f - x)
        val o = v.kind.ordinal
        val f0 = if (v.kind == Sfx.VORTEX) SFX_F0[o] + 120f * sin(v.t * 40f) else SFX_F0[o]
        val f1 = SFX_F1[o]; val noiseMix = SFX_NOISE[o]
        val f = f0 * (f1 / f0).pow(x)
        v.phase = (v.phase + f * dt) % 1f
        val tone = if (v.kind == Sfx.SHIELD || v.kind == Sfx.VORTEX) (if (v.phase < 0.5f) 1f else -1f) * 0.6f
                   else sin(2f * PI.toFloat() * v.phase)
        v.t += dt
        return (tone * (1f - noiseMix) + rnd() * noiseMix) * env * 0.7f
    }

    private fun duration(k: Sfx) = when (k) {
        Sfx.PICKUP -> 0.15f; Sfx.SHIELD -> 0.3f; Sfx.QUAKE -> 0.9f; Sfx.SLOWMO -> 0.6f
        Sfx.FOLD -> 0.5f; Sfx.VORTEX -> 0.5f; Sfx.CRASH -> 0.8f; Sfx.CLOSE_CALL -> 0.12f
    }

    /** Deterministic white noise in [-1, 1] (xorshift). */
    private fun rnd(): Float {
        var x = noise
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        noise = x
        return (x and 0xFFFF) / 32767.5f - 1f
    }

    companion object {
        // Per-effect pitch sweep start/end and noise share, indexed by Sfx.ordinal
        // (a lookup instead of a Triple per sample: the audio thread must not allocate).
        private val SFX_F0 = FloatArray(Sfx.entries.size)
        private val SFX_F1 = FloatArray(Sfx.entries.size)
        private val SFX_NOISE = FloatArray(Sfx.entries.size)
        private fun sfxTone(k: Sfx, f0: Float, f1: Float, noise: Float) {
            SFX_F0[k.ordinal] = f0; SFX_F1[k.ordinal] = f1; SFX_NOISE[k.ordinal] = noise
        }
        init {
            sfxTone(Sfx.PICKUP, 700f, 1500f, 0f)
            sfxTone(Sfx.SHIELD, 320f, 140f, 0.3f)
            sfxTone(Sfx.QUAKE, 90f, 30f, 0.7f)
            sfxTone(Sfx.SLOWMO, 900f, 180f, 0f)
            sfxTone(Sfx.FOLD, 250f, 1000f, 0.1f)
            sfxTone(Sfx.VORTEX, 200f, 120f, 0.2f)       // + wobble in sfx()
            sfxTone(Sfx.CRASH, 120f, 40f, 0.8f)
            sfxTone(Sfx.CLOSE_CALL, 1400f, 2600f, 0.35f) // short airy swish
        }
        const val BPM_MIN = 112f
        const val BPM_MAX = 168f
        const val BPM_PER_SPEED = 1.0f
        /** MIDI roots of the loop: A4, F4, C5, G4. */
        private val CHORDS = intArrayOf(69, 65, 72, 67)
        /** Minor/major triad arpeggio over two octaves (semitones from the root). */
        private val ARP = intArrayOf(0, 3, 7, 12, 7, 3, 0, -5)
        private fun note(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)
    }
}
