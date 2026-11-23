package io.github.iampopovich.tunnelslide.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Soundtrack style: CALM is the slow ambient piano (default), DRIVE the fast beat loop. */
enum class MusicStyle(val label: String) { CALM("CALM"), DRIVE("DRIVE") }

/**
 * Calm generative soundtrack: a soft piano over slow pads, with reverb and long pauses,
 * in the spirit of sparse ambient game scores. All material is original: each piece is a
 * chord progression plus a pentatonic random walk, so the melody never repeats exactly,
 * yet rendering is deterministic for a given seed (unit tests rely on that).
 *
 * Speed only colours the music (denser melody, slightly faster, brighter pads); the calm
 * character stays. Oscillators use a sine table: the phone renders ~20 voices in real time.
 */
internal class CalmMusic(private val rate: Int, seed: Int = 0x1F2E3D4C) {

    /** Chords are MIDI notes, the first one is the bass. Melody = tonic + scale degrees. */
    private class Piece(val bpm: Float, val tonic: Int, val scale: IntArray, val chords: Array<IntArray>)

    private class Key {
        var freq = 0f; var active = false
        var p1 = 0f; var p2 = 0f; var p3 = 0f
        var e1 = 0f; var e2 = 0f; var e3 = 0f
        var m1 = 1f; var m2 = 1f; var m3 = 1f
        var attack = 0f
    }

    private class Pad(val freq: Float, val target0: Float) {
        var level = 0f; var target = target0
        var pa = 0f; var pb = 0f; var pc = 0f
    }

    private class Comb(n: Int) {
        val buf = FloatArray(n); var i = 0; var store = 0f
        fun process(x: Float): Float {
            val y = buf[i]
            store = y * (1f - DAMP) + store * DAMP
            buf[i] = x + store * FEEDBACK
            if (++i == buf.size) i = 0
            return y
        }
    }

    private class AllPass(n: Int) {
        val buf = FloatArray(n); var i = 0
        fun process(x: Float): Float {
            val b = buf[i]
            buf[i] = x + b * 0.5f
            if (++i == buf.size) i = 0
            return b - x
        }
    }

    private var rng = seed
    private var pieceIndex = 0
    private var piece = PIECES[0]
    private var melody = IntArray(0)
    private var melodyPos = 0
    private var melodyBar = false
    private var bar = -1
    private var eighth = 7
    private var phase = 1.0
    private var speedK = 0f

    private val keys = Array(MAX_KEYS) { Key() }
    private val pads = ArrayList<Pad>()
    private var padLp = 0f
    private var padCut = 0.1f
    private var lfo = 0f

    private val combs = intArrayOf(1557, 1617, 1491, 1422).map { Comb(scaled(it)) }
    private val allPasses = intArrayOf(556, 441).map { AllPass(scaled(it)) }

    init { startPiece((rnd() * PIECES.size).toInt()) }

    /**
     * One output sample. [k] is the speed share 0..1, [tempo] the game clock rate
     * (time dilation), [pitch] the playback pitch factor.
     */
    fun sample(k: Float, tempo: Float, pitch: Float): Float {
        speedK = k
        phase += piece.bpm * (1f + 0.12f * k) * tempo / 60.0 * 2.0 / rate
        if (phase >= 1.0) { phase -= 1.0; nextEighth() }

        var piano = 0f
        for (key in keys) {
            if (!key.active) continue
            val inc = key.freq * pitch / rate
            key.p1 = wrap(key.p1 + inc); key.p2 = wrap(key.p2 + inc * 2f); key.p3 = wrap(key.p3 + inc * 3f)
            if (key.attack < 1f) key.attack = minOf(1f, key.attack + ATTACK_STEP * 44100f / rate)
            piano += (sine(key.p1) * key.e1 + sine(key.p2) * key.e2 + sine(key.p3) * key.e3) * key.attack
            key.e1 *= key.m1; key.e2 *= key.m2; key.e3 *= key.m3
            if (key.e1 < 1e-4f) key.active = false
        }

        var pad = 0f
        val padStep = 1f / (PAD_FADE_S * rate)
        val it = pads.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.level += (p.target - p.level) * padStep * 3f
            if (p.target == 0f && p.level < 1e-5f) { it.remove(); continue }
            val inc = p.freq * pitch / rate
            p.pa = wrap(p.pa + inc * 1.0015f); p.pb = wrap(p.pb + inc * 0.9985f); p.pc = wrap(p.pc + inc * 2f)
            pad += ((sine(p.pa) + sine(p.pb)) * 0.5f + sine(p.pc) * 0.2f) * p.level
        }
        lfo = wrap(lfo + 0.11f / rate)
        padLp += (pad - padLp) * padCut
        pad = padLp * (0.85f + 0.15f * sine(lfo))

        val dry = piano * PIANO_GAIN + pad
        var wet = 0f
        val inp = dry * 0.3f
        for (c in combs) wet += c.process(inp)
        for (a in allPasses) wet = a.process(wet)
        return dry + wet * 0.45f
    }

    private fun nextEighth() {
        eighth = (eighth + 1) % 8
        if (eighth == 0) nextBar()
        if (bar >= playBars()) return
        val chord = piece.chords[(bar / BARS_PER_CHORD) % piece.chords.size]
        if (eighth == 0 && bar % BARS_PER_CHORD == 0) {
            setPad(chord)
            strike(chord[0], 0.42f)
        }
        // Left hand: a chord tone here and there.
        if ((eighth == 2 || eighth == 5) && chance(0.65f)) strike(chord[1 + (rnd() * (chord.size - 1)).toInt()], 0.2f)
        // Melody: a slow random walk on the piece's pentatonic scale, sparser off the beat.
        if (melodyBar) {
            val p = (if (eighth % 4 == 0) 0.5f else 0.22f) * (0.8f + 0.6f * speedK)
            if (chance(p)) {
                val r = rnd()
                val stepBy = when {
                    r < 0.15f -> -2; r < 0.45f -> -1; r < 0.55f -> 0; r < 0.85f -> 1; else -> 2
                }
                melodyPos = (melodyPos + stepBy).coerceIn(0, melody.size - 1)
                // Lean back towards the middle so the line doesn't stick to an edge.
                if (melodyPos == 0 || melodyPos == melody.size - 1) melodyPos += if (melodyPos == 0) 1 else -1
                strike(melody[melodyPos], 0.3f + 0.14f * rnd())
            }
        }
    }

    private fun nextBar() {
        bar++
        if (bar >= playBars() + REST_BARS) {
            var next = (rnd() * PIECES.size).toInt()
            if (next == pieceIndex) next = (next + 1) % PIECES.size
            startPiece(next)
            bar = 0
        }
        if (bar == playBars()) for (p in pads) p.target = 0f
        // The first chord is an intro without melody; later bars sometimes rest.
        melodyBar = bar >= BARS_PER_CHORD && chance(0.78f)
    }

    private fun startPiece(i: Int) {
        pieceIndex = i
        piece = PIECES[i]
        val notes = ArrayList<Int>()
        for (oct in -1..1) for (d in piece.scale) {
            val n = piece.tonic + 12 * oct + d
            if (n >= piece.tonic - 5 && n <= piece.tonic + 14) notes.add(n)
        }
        melody = notes.toIntArray()
        melodyPos = melody.size / 2
    }

    private fun playBars() = piece.chords.size * BARS_PER_CHORD * 2

    private fun setPad(chord: IntArray) {
        for (p in pads) p.target = 0f
        val upper = chord.size - 1
        for (j in 1 until chord.size) pads.add(Pad(freq(chord[j]), PAD_GAIN / upper))
    }

    private fun strike(midi: Int, vel: Float) {
        var key = keys.firstOrNull { !it.active }
        if (key == null) key = keys.minBy { it.e1 }
        val f = freq(midi)
        // Low notes ring longer; overtones die faster than the fundamental, like a piano.
        val d = 0.7f + f / 800f
        key.freq = f; key.active = true
        key.p1 = 0f; key.p2 = 0f; key.p3 = 0f
        key.e1 = vel; key.e2 = vel * 0.35f; key.e3 = vel * 0.12f
        key.m1 = exp(-d / rate); key.m2 = exp(-(d * 2.5f + 1f) / rate); key.m3 = exp(-(d * 5f + 2f) / rate)
        key.attack = 0f
        padCut = 1f - exp(-2f * PI.toFloat() * (700f + 900f * speedK) / rate)
    }

    private fun scaled(n: Int) = maxOf(1, n * rate / 44100)
    private fun chance(p: Float) = rnd() < p

    /** Deterministic uniform [0, 1) (xorshift). */
    private fun rnd(): Float {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rng = x
        return (x ushr 8) / 16777216f
    }

    companion object {
        private const val MAX_KEYS = 14
        private const val BARS_PER_CHORD = 2
        private const val REST_BARS = 2
        private const val PIANO_GAIN = 0.55f
        private const val PAD_GAIN = 0.11f
        private const val PAD_FADE_S = 2.2f
        private const val ATTACK_STEP = 1f / (0.006f * 44100f)
        private const val FEEDBACK = 0.83f
        private const val DAMP = 0.35f

        private val MAJOR_PENTA = intArrayOf(0, 2, 4, 7, 9)
        private val MINOR_PENTA = intArrayOf(0, 3, 5, 7, 10)

        private val PIECES = arrayOf(
            // "Drift", D major: Dmaj7 - Bm7 - Gmaj7 - Asus4
            Piece(68f, 74, MAJOR_PENTA, arrayOf(
                intArrayOf(50, 62, 66, 69, 73), intArrayOf(47, 59, 62, 66, 69),
                intArrayOf(43, 59, 62, 66), intArrayOf(45, 57, 62, 64, 69))),
            // "Haze", F lydian: Fmaj7 - G/F - Em7 - Am7
            Piece(62f, 72, MAJOR_PENTA, arrayOf(
                intArrayOf(41, 57, 60, 64), intArrayOf(41, 55, 59, 62),
                intArrayOf(40, 55, 59, 62, 64), intArrayOf(45, 57, 60, 64, 67))),
            // "Low orbit", A dorian: Am9 - Dm9 - Fmaj7 - Em7
            Piece(72f, 69, MINOR_PENTA, arrayOf(
                intArrayOf(45, 55, 59, 60, 64), intArrayOf(38, 53, 57, 60, 64),
                intArrayOf(41, 57, 60, 64), intArrayOf(40, 55, 59, 62))),
            // "Glass", E major: Emaj9 - C#m7 - Amaj7 - Bsus4
            Piece(66f, 76, MAJOR_PENTA, arrayOf(
                intArrayOf(40, 56, 59, 63, 66), intArrayOf(37, 56, 59, 61, 64),
                intArrayOf(45, 56, 61, 64), intArrayOf(47, 54, 59, 64))),
            // "Tide", C major: Cmaj7 - Am7 - Fmaj9 - G6
            Piece(70f, 72, MAJOR_PENTA, arrayOf(
                intArrayOf(48, 59, 64, 67), intArrayOf(45, 60, 64, 67),
                intArrayOf(41, 57, 60, 64, 67), intArrayOf(43, 59, 62, 64))),
        )

        private const val TABLE = 4096
        private val SINE = FloatArray(TABLE + 1) { sin(2.0 * PI * it / TABLE).toFloat() }

        private fun freq(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)
        private fun wrap(p: Float) = if (p >= 1f) p - 1f else p

        /** Table sine of a phase in [0, 1), linearly interpolated. */
        private fun sine(p: Float): Float {
            val x = p * TABLE
            val i = x.toInt()
            val f = x - i
            return SINE[i] + (SINE[i + 1] - SINE[i]) * f
        }
    }
}
