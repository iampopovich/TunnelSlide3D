package io.github.iampopovich.tunnelslide.world

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

/**
 * Builds the model matrix that puts a unit cube (centred at the origin) onto the tunnel
 * surface: local X follows the surface across the tunnel, local Y is the surface normal,
 * local Z runs along the tunnel. The block is therefore glued to its spot and turns only
 * together with the tunnel, never on its own.
 */
object BlockPlacement {
    private val p = Vector3()
    private val t = Vector2()
    private val n = Vector2()

    /**
     * @param uRel   centre across the tunnel, relative to the player
     * @param near   distance of the block's near face from the player
     * @param lift   gap between the surface and the block's bottom face
     */
    fun compose(
        uRel: Float, near: Float, length: Float, width: Float, height: Float, lift: Float,
        morph: Float, radius: Float, out: Matrix4,
    ): Matrix4 {
        TunnelSurface.point(uRel, near + length * 0.5f, morph, radius, p)
        TunnelSurface.tangent(uRel, morph, t)
        TunnelSurface.normal(uRel, morph, n)
        val up = lift + height * 0.5f
        p.add(n.x * up, n.y * up, 0f)

        // Column-major: column 0 = local X axis * width, column 1 = local Y * height,
        // column 2 = local Z * length, column 3 = translation.
        // NOTE: Matrix4.set(xAxis, yAxis, zAxis, pos) writes the axes as ROWS (transposed
        // rotation), which made cubes tilt and spin on the walls, so we fill it by hand.
        val v = out.`val`
        v[Matrix4.M00] = t.x * width;  v[Matrix4.M01] = n.x * height; v[Matrix4.M02] = 0f;     v[Matrix4.M03] = p.x
        v[Matrix4.M10] = t.y * width;  v[Matrix4.M11] = n.y * height; v[Matrix4.M12] = 0f;     v[Matrix4.M13] = p.y
        v[Matrix4.M20] = 0f;           v[Matrix4.M21] = 0f;           v[Matrix4.M22] = length; v[Matrix4.M23] = p.z
        v[Matrix4.M30] = 0f;           v[Matrix4.M31] = 0f;           v[Matrix4.M32] = 0f;     v[Matrix4.M33] = 1f
        return out
    }
}
