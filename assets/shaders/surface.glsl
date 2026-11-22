// Shared surface mapping, prepended to every vertex shader that places things on the tunnel.
// MUST stay identical to world/TunnelSurface.kt.

uniform float u_radius;
// Surface shape along the view: base + two ramps (start dist, length, delta).
uniform float u_shapeBase;
uniform vec3 u_ramp0;
uniform vec3 u_ramp1;

const float TAU = 6.2831853;

float ramp(vec3 r, float dist) {
    if (r.y <= 0.0) return 0.0;
    return r.z * clamp((dist - r.x) / r.y, 0.0, 1.0);
}

float shapeAt(float dist) {
    return u_shapeBase + ramp(u_ramp0, dist) + ramp(u_ramp1, dist);
}

vec2 surfacePoint(float th, float shape) {
    float s = sin(th);
    float c = cos(th);
    vec2 tube = vec2(u_radius * s, -u_radius * c);
    vec2 flatPos = vec2(u_radius * th, -u_radius);
    vec2 outside = vec2(u_radius * s, -2.0 * u_radius + u_radius * c);
    return shape <= 1.0 ? mix(tube, flatPos, shape) : mix(flatPos, outside, shape - 1.0);
}

vec2 surfaceTangent(float th, float shape) {
    float s = sin(th);
    float c = cos(th);
    vec2 t = shape <= 1.0 ? mix(vec2(c, s), vec2(1.0, 0.0), shape)
                          : mix(vec2(1.0, 0.0), vec2(c, -s), shape - 1.0);
    return normalize(t);
}
