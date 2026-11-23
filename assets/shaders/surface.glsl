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

// The surface is a curve of constant curvature k = (1 - shape) / R through the player's point
// (0, -R), unrolled with arc length kept: shape 0 is the tube (k = 1/R), 1 the flat ribbon
// (k = 0), 2 the outside of the tube (k = -1/R). In between the circle opens like a rolled
// sheet: the edges at the seam simply part and later meet again, nothing sweeps across.
// With a = k * s (s = R * th is the arc length) this is
//   x = s * sin(a) / a,   y = -R + s * (1 - cos(a)) / a.
vec2 surfacePoint(float th, float shape) {
    float a = (1.0 - shape) * th;
    float s = u_radius * th;
    float sinc;
    float cosc;
    if (abs(a) < 1e-3) {
        // Series near the flat ribbon, where sin(a)/a and (1-cos a)/a are 0/0.
        sinc = 1.0 - a * a / 6.0;
        cosc = a * 0.5;
    } else {
        sinc = sin(a) / a;
        cosc = (1.0 - cos(a)) / a;
    }
    return vec2(s * sinc, -u_radius + s * cosc);
}

vec2 surfaceTangent(float th, float shape) {
    float a = (1.0 - shape) * th;
    return vec2(cos(a), sin(a));
}
