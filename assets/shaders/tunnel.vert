// Tunnel surface. Keep surface() identical to world/TunnelSurface.kt.
attribute vec2 a_coord;      // x = position from the seam in [0, 1], y = distance ahead

uniform mat4 u_projView;
uniform float u_radius;
uniform float u_playerA;     // player position from the seam, [0, 1)
// Surface shape along the view: base + two ramps (start dist, length, delta).
uniform float u_shapeBase;
uniform vec3 u_ramp0;
uniform vec3 u_ramp1;

varying vec2 v_coord;
varying float v_shape;

const float TAU = 6.2831853;

float ramp(vec3 r, float dist) {
    if (r.y <= 0.0) return 0.0;
    return r.z * clamp((dist - r.x) / r.y, 0.0, 1.0);
}

void main() {
    float dist = a_coord.y;
    float shape = u_shapeBase + ramp(u_ramp0, dist) + ramp(u_ramp1, dist);
    float th = (a_coord.x - u_playerA) * TAU;
    float s = sin(th);
    float c = cos(th);
    vec2 tube = vec2(u_radius * s, -u_radius * c);
    vec2 flatPos = vec2(u_radius * th, -u_radius);
    vec2 outside = vec2(u_radius * s, -2.0 * u_radius + u_radius * c);
    vec2 xy = shape <= 1.0 ? mix(tube, flatPos, shape) : mix(flatPos, outside, shape - 1.0);
    v_coord = a_coord;
    v_shape = shape;
    gl_Position = u_projView * vec4(xy, -dist, 1.0);
}
