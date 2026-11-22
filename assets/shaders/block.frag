#ifdef GL_ES
precision mediump float;
#endif

varying vec3 v_local;
varying vec3 v_normal;
varying float v_dist;

uniform vec3 u_color;
uniform float u_trail;       // 1 = warning trail, 0 = solid block
uniform float u_fogDist;
uniform vec3 u_fogColor;

void main() {
    float fog = clamp(v_dist / u_fogDist, 0.0, 1.0);
    if (u_trail > 0.5) {
        // Strongest next to the block (far end, local z = -0.5), fading towards the player.
        float a = (0.5 - v_local.z) * 0.55 * (1.0 - fog);
        gl_FragColor = vec4(u_color * a, a);
        return;
    }
    // Neon edges: a fragment is on an edge if two of its coordinates are near a face.
    vec3 d = 0.5 - abs(v_local);
    float e1 = step(d.x, 0.06) + step(d.y, 0.06) + step(d.z, 0.06);
    float edge = step(2.0, e1);
    // Face shading from a fixed light, in block space (good enough for flat boxes).
    float light = 0.45 + 0.55 * max(dot(v_normal, normalize(vec3(0.3, 0.8, 0.6))), 0.0);
    vec3 col = mix(u_color * light * 0.55, vec3(1.0) * 0.4 + u_color * 0.8, edge);
    col = mix(col, u_fogColor, fog * fog);
    gl_FragColor = vec4(col, 1.0);
}
