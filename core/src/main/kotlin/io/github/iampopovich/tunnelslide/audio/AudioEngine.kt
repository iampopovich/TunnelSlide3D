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
        running = true
        // The audio thread owns the device and disposes it itself: stop() may return before a
        // blocking writeSamples() does, and disposing under it crashes (AudioTrack released).
        // Any error ends the sound, never the game.
        thread = Thread({
            val buf = FloatArray(BUFFER)
            try {
                while (running) {
                    synth.render(buf, buf.size)
                    dev.writeSamples(buf, 0, buf.size)
                }
            } catch (e: Throwable) {
                Gdx.app.error("TunnelSlide3D", "audio stopped: $e")
            } finally {
                try { dev.dispose() } catch (_: Throwable) {}
            }
        }, "tunnelslide-audio").apply { isDaemon = true; start() }
        Gdx.app.log("TunnelSlide3D", "audio started, latency ${dev.latency} samples")
    }

    fun stop() {
        running = false
        // Wait for the old thread, so a quick resume never runs two renderers on one Synth.
        thread?.join(1500)
        thread = null
    }

    companion object {
        private const val BUFFER = 1024
    }
}
