// Batched blocks: every vertex carries its block's tunnel placement, and the cube is
// assembled on the surface here. surface.glsl is prepended at load time.
attribute vec3 a_position;   // corner of a unit cube, [-0.5, 0.5]
attribute vec3 a_normal;     // face normal in block space
attribute vec4 a_block;      // x = position from the seam [0,1), y = near z (relative to u_zBase),
                             // z = length, w = width in world units
attribute vec2 a_extra;      // x = height, y = lift above the surface
attribute vec3 a_color;

uniform mat4 u_projView;
uniform float u_playerA;     // player position from the seam, [0, 1)
uniform float u_zShift;      // u_zBase - playerZ: turns stored z into distance ahead

varying vec3 v_local;
varying vec3 v_normal;
varying float v_dist;
varying vec3 v_color;

void main() {
    float near = a_block.y + u_zShift;
    float len = a_block.z;
    float centre = near + 0.5 * len;
    float th = (a_block.x - u_playerA) * TAU;
    float shape = shapeAt(centre);
    vec2 p = surfacePoint(th, shape);
    vec2 t = surfaceTangent(th, shape);
    vec2 n = vec2(-t.y, t.x);
    float height = a_extra.x;
    float up = a_extra.y + height * (a_position.y + 0.5);
    vec2 xy = p + t * (a_position.x * a_block.w) + n * up;
    float z = -(centre - a_position.z * len);
    v_local = a_position;
    v_normal = a_normal;
    v_dist = -z;
    v_color = a_color;
    gl_Position = u_projView * vec4(xy, z, 1.0);
}
