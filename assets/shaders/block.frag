#ifdef GL_ES
precision mediump float;
#endif

varying vec3 v_local;
varying vec3 v_normal;
varying float v_dist;
varying vec3 v_color;

uniform float u_trail;       // 1 = warning trails pass, 0 = solid blocks
uniform float u_fogDist;
uniform vec3 u_fogColor;
uniform float u_mono;        // 1 = monochrome mode
uniform float u_invertBlocks; // dark-nebula hazard, 0..1
uniform float u_invertTunnel; // blackout: fog turns light, blocks fade into it
uniform float u_hueAmount;
uniform float u_hueAngle;

// Rotates the hue of c by angle a (Rodrigues rotation around the grey axis).
vec3 hueRotate(vec3 c, float a) {
    vec3 k = vec3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

vec3 mono(vec3 c) {
    c = mix(c, clamp(hueRotate(c, u_hueAngle * 1.7), 0.0, 1.0), u_hueAmount);
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    return mix(c, vec3(clamp(lum * 1.4, 0.0, 1.0)), u_mono);
}

void main() {
    if (v_dist < 0.0) discard;               // already passed
    float fog = clamp(v_dist / u_fogDist, 0.0, 1.0);
    if (u_trail > 0.5) {
        // Strongest next to the block (far end, local z = -0.5), fading towards the player.
        float a = (0.5 - v_local.z) * 0.55 * (1.0 - fog);
        gl_FragColor = vec4(mono(v_color) * a, a);
        return;
    }
    // Neon edges: a fragment is on an edge if two of its coordinates are near a face.
    vec3 d = 0.5 - abs(v_local);
    float e1 = step(d.x, 0.06) + step(d.y, 0.06) + step(d.z, 0.06);
    float edge = step(2.0, e1);
    float light = 0.45 + 0.55 * max(dot(v_normal, normalize(vec3(0.3, 0.8, 0.6))), 0.0);
    vec3 col = mix(v_color * light * 0.55, vec3(1.0) * 0.4 + v_color * 0.8, edge);
    col = mono(col);
    col = mix(col, vec3(1.0) - col, u_invertBlocks);
    vec3 fogColor = mix(u_fogColor, vec3(0.85), u_invertTunnel);
    col = mix(col, fogColor, fog * fog);
    gl_FragColor = vec4(col, 1.0);
}
