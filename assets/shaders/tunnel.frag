#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_coord;
varying float v_shape;

uniform float u_uOffset;     // seam position, reduced modulo the pattern period
uniform float u_zOffset;     // player z, reduced modulo the pattern period
uniform float u_lanes;
uniform float u_ringLen;
uniform float u_fogDist;
uniform vec3 u_fogColor;

void main() {
    float dist = v_coord.y;
    float lu = (v_coord.x + u_uOffset) * u_lanes;
    float lz = (dist + u_zOffset) / u_ringLen;

    float fog = clamp(dist / u_fogDist, 0.0, 1.0);

    // Panel edges: thin bright lines across and along the tunnel.
    float eu = abs(fract(lu) - 0.5);
    float ez = abs(fract(lz) - 0.5);
    // Thin lines; they fade out with distance before they start to alias.
    float lineU = smoothstep(0.455, 0.49, eu);
    float lineZ = smoothstep(0.47, 0.495, ez);
    // Right under the camera a line covers half the screen: dim it there.
    float nearDim = 0.25 + 0.75 * smoothstep(0.5, 4.0, dist);
    float line = max(lineU, lineZ) * (1.0 - fog) * (1.0 - fog) * nearDim;

    // Slow colour cycle along the tunnel, light checkerboard for depth cues.
    float band = floor(lz);
    float checker = mod(floor(lu) + band, 2.0);
    vec3 a = vec3(0.05, 0.12, 0.32);
    vec3 b = vec3(0.18, 0.05, 0.30);
    vec3 base = mix(a, b, 0.5 + 0.5 * sin(band * 0.39));
    base *= 0.75 + 0.25 * checker;
    vec3 lineColor = vec3(0.35, 0.85, 1.0);
    vec3 col = mix(base, lineColor, line);

    // Open surface: hot red edges along the cut, fading in with the morph.
    float open = clamp(1.0 - abs(v_shape - 1.0) / 0.65, 0.0, 1.0);
    float edgeDist = min(v_coord.x, 1.0 - v_coord.x) * u_lanes;   // in lanes
    float edge = (1.0 - smoothstep(0.0, 0.35, edgeDist)) * open;
    col = mix(col, vec3(1.0, 0.25, 0.2), edge);

    col = mix(col, u_fogColor, fog * fog);
    gl_FragColor = vec4(col, 1.0);
}
