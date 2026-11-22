// Batched obstacles: every vertex carries its obstacle's tunnel placement, and the cube is
// assembled on the surface here. surface.glsl is prepended at load time.
attribute vec3 a_position;   // corner of a unit cube, [-0.5, 0.5]
attribute vec3 a_normal;     // face normal in block space
attribute vec4 a_block;      // x = position from the seam [0,1), y = near z (relative to zBase),
                             // z = length, w = width in world units
attribute vec4 a_extra;      // x = height, y = lift, z = mode (0 block, 1 roller, 2 pillar,
                             // 3 roller trail: slides along instead of tumbling),
                             // w = roller clock offset in ticks
attribute vec2 a_roll;       // x = sweep lanes (0 = around the whole tube), y = circling direction

uniform mat4 u_projView;
uniform float u_playerA;     // player position from the seam, [0, 1)
uniform float u_zShift;      // zBase - playerZ: turns stored z into distance ahead
uniform float u_clock;       // game time in roller ticks (vertex shaders are highp)
uniform float u_rest;        // share of a tick rollers stand still
uniform float u_lanes;

attribute vec3 a_color;

varying vec3 v_local;
varying vec3 v_normal;
varying float v_dist;
varying vec3 v_color;

// Ping-pong over n positions; must match Obstacle.tri().
float tri(float k, float n) {
    float p = 2.0 * (n - 1.0);
    float r = mod(k, p);
    return r <= n - 1.0 ? r : p - r;
}

void main() {
    float near = a_block.y + u_zShift;
    float len = a_block.z;
    float centre = near + 0.5 * len;
    float th = (a_block.x - u_playerA) * TAU;
    float w = a_block.w;
    float height = a_extra.x;
    float lift = a_extra.y;
    float mode = a_extra.z;
    vec2 xy;
    vec2 nrm = a_normal.xy;

    if (mode > 1.5 && mode < 2.5) {
        // Pillar: a straight box along the surface normal. Bending it like small blocks
        // would make its side faces cross each other at the tunnel axis.
        vec2 p = surfacePoint(th, u_shape);
        vec2 t = surfaceTangent(th, u_shape);
        vec2 n = vec2(-t.y, t.x);
        xy = p + t * (a_position.x * w) + n * (lift + height * (a_position.y + 0.5));
    } else {
        // Cross-section position: x along the surface (arc length), y up from it.
        vec2 lp = vec2(a_position.x * w, lift + height * (a_position.y + 0.5));
        if (mode > 0.5) {
            // Tick-tock: rest, then roll one lane over the bottom edge in the travel direction.
            float s = u_clock + a_extra.w;
            float k = floor(s);
            float q = s - k;
            float l0;
            float l1;
            if (a_roll.x < 1.5) {
                l0 = mod(a_roll.y * k, u_lanes);
                l1 = l0 + a_roll.y;
            } else {
                l0 = tri(k, a_roll.x);
                l1 = tri(k + 1.0, a_roll.x);
            }
            float dir = l1 - l0;
            float f = smoothstep(u_rest, 1.0, q);
            if (mode > 2.5) {
                lp.x += mix(l0, l1, f) * w;           // trail: slide with the cube
            } else {
                float ang = -dir * f * 1.5707963;
                vec2 pivot = vec2((l0 + 0.5 * dir) * w, lift);
                vec2 r = vec2((a_position.x + l0) * w, lift + height * (a_position.y + 0.5));
                mat2 rot = mat2(cos(ang), sin(ang), -sin(ang), cos(ang));
                lp = pivot + rot * (r - pivot);
                nrm = rot * nrm;
            }
        }
        // Bend the cross-section onto the surface, so a roll ends exactly on the next lane.
        float th2 = th + lp.x / u_radius;
        vec2 base = surfacePoint(th2, u_shape);
        vec2 t = surfaceTangent(th2, u_shape);
        xy = base + vec2(-t.y, t.x) * lp.y;
    }
    float z = -(centre - a_position.z * len);
    v_local = a_position;
    v_normal = vec3(nrm, a_normal.z);
    v_dist = -z;
    v_color = a_color;
    gl_Position = u_projView * vec4(xy, z, 1.0);
}
