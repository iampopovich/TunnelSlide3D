// Tunnel surface. Keep surface() identical to world/TunnelSurface.kt.
attribute vec2 a_coord;      // x = uRel in [-0.5, 0.5], y = distance ahead

uniform mat4 u_projView;
uniform float u_radius;
uniform float u_morph;       // 0 = tube, 1 = flat ribbon

varying vec2 v_coord;

const float TAU = 6.2831853;

void main() {
    float th = a_coord.x * TAU;
    vec2 tube = vec2(u_radius * sin(th), -u_radius * cos(th));
    vec2 flatPos = vec2(u_radius * th, -u_radius);
    vec2 xy = mix(tube, flatPos, u_morph);
    v_coord = a_coord;
    gl_Position = u_projView * vec4(xy, -a_coord.y, 1.0);
}
