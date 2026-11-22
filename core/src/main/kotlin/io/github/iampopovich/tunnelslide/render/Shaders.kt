package io.github.iampopovich.tunnelslide.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram

object Shaders {
    init {
        // Shared per-frame uniforms are set on every program; a program that doesn't use one
        // of them must not throw.
        ShaderProgram.pedantic = false
    }

    /** Loads a program whose vertex shader uses the shared surface mapping. */
    fun withSurface(name: String): ShaderProgram {
        val common = Gdx.files.internal("shaders/surface.glsl").readString()
        val vert = Gdx.files.internal("shaders/$name.vert").readString()
        val frag = Gdx.files.internal("shaders/$name.frag").readString()
        return ShaderProgram(common + "\n" + vert, frag).also {
            require(it.isCompiled) { "$name shader: ${it.log}" }
        }
    }
}
