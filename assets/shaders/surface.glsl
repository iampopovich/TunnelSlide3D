// Shared surface mapping, prepended to every vertex shader that places things on the tunnel.
// MUST stay identical to world/TunnelSurface.kt.

#ifdef GL_ES
#define MEDP mediump
#else
#define MEDP
#endif

uniform float u_radius;
// Current surface shape: 0 tube, 1 flat, 2 outside. One value for the whole view, so the
// tunnel opens and closes around the player in real time.
// Also read by tunnel.frag: on GLES a uniform shared by both stages must have the same
// precision, and fragment shaders default to mediump.
uniform MEDP float u_shape;

const float TAU = 6.2831853;

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
