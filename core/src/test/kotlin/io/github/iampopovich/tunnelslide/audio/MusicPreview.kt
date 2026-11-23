package io.github.iampopovich.tunnelslide.audio

import io.github.iampopovich.tunnelslide.GameConfig
import java.io.File
import java.io.RandomAccessFile

/**
 * Renders the soundtrack to WAV files to listen to without running the game:
 *   ./gradlew :core:musicPreview   -> core/build/music-preview/
 * The calm track speeds up from the start to the top speed, as in a long run.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "build/music-preview").apply { mkdirs() }
    for ((name, style, seconds) in listOf(Triple("calm", MusicStyle.CALM, 240), Triple("drive", MusicStyle.DRIVE, 40))) {
        val synth = Synth(44100).also { it.style = style }
        val total = seconds * synth.rate
        val pcm = ShortArray(total)
        val buf = FloatArray(1024)
        var i = 0
        val t0 = System.nanoTime()
        while (i < total) {
            synth.speed = GameConfig.START_SPEED + (GameConfig.MAX_SPEED - GameConfig.START_SPEED) * i / total
            val n = minOf(buf.size, total - i)
            synth.render(buf, n)
            for (j in 0 until n) pcm[i + j] = (buf[j] * 32767f).toInt().toShort()
            i += n
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val f = File(dir, "$name.wav")
        writeWav(f, pcm, synth.rate)
        println("${f.absolutePath}: ${seconds}s rendered in ${ms}ms (${seconds * 1000 / maxOf(1, ms)}x real time)")
    }
}

private fun writeWav(f: File, pcm: ShortArray, rate: Int) {
    RandomAccessFile(f, "rw").use { w ->
        w.setLength(0)
        fun le32(v: Int) { w.write(v and 0xFF); w.write(v shr 8 and 0xFF); w.write(v shr 16 and 0xFF); w.write(v shr 24 and 0xFF) }
        fun le16(v: Int) { w.write(v and 0xFF); w.write(v shr 8 and 0xFF) }
        val data = pcm.size * 2
        w.writeBytes("RIFF"); le32(36 + data); w.writeBytes("WAVEfmt ")
        le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
        w.writeBytes("data"); le32(data)
        val bytes = ByteArray(data)
        for (k in pcm.indices) { bytes[2 * k] = pcm[k].toByte(); bytes[2 * k + 1] = (pcm[k].toInt() shr 8).toByte() }
        w.write(bytes)
    }
}
