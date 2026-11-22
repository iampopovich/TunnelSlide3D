package io.github.iampopovich.tunnelslide.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice

/**
 * Streams [Synth] into a libGDX AudioDevice on its own thread. writeSamples() blocks until
 * the device has room, which paces the thread. Stopped on pause (Android lifecycle), so no
 * audio runs in the background.
 */
class AudioEngine {
    val synth = Synth()
    private var device: AudioDevice? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (thread != null) return
        val dev = try {
            Gdx.audio.newAudioDevice(synth.rate, true)
        } catch (e: Throwable) {
            Gdx.app.error("TunnelSlide3D", "audio unavailable: ${e.message}")
            return
        }
        device = dev
        running = true
        thread = Thread({
            val buf = FloatArray(BUFFER)
            while (running) {
                synth.render(buf, buf.size)
                dev.writeSamples(buf, 0, buf.size)
            }
        }, "tunnelslide-audio").apply { isDaemon = true; start() }
        Gdx.app.log("TunnelSlide3D", "audio started, latency ${dev.latency} samples")
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        device?.dispose()
        device = null
    }

    companion object {
        private const val BUFFER = 1024
    }
}
