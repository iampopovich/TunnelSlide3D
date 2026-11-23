#ifdef GL_ES
precision mediump float;
#endif

// Full-screen composite: stereo anaglyph, gravity-well swirl, Classic motion blur.
uniform sampler2D u_left;     // scene (left eye when stereo)
uniform sampler2D u_right;    // right eye (same as left when not stereo)
uniform sampler2D u_history;  // previous composited frame
uniform float u_stereo;       // 0 off, 1 red/cyan, 2 green/magenta
uniform float u_blur;         // share of the previous frame kept, 0..1
uniform float u_swirl;        // gravity distortion, signed
uniform float u_aspect;
varying vec2 v_uv;

vec2 swirl(vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(u_aspect, 1.0);
    float r = length(p);
    // Twist grows towards the centre (the vanishing point), fades at the edges.
    float a = u_swirl * exp(-r * 3.0);
    float c = cos(a), s = sin(a);
    p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
    return p / vec2(u_aspect, 1.0) + 0.5;
}

void main() {
    vec2 uv = swirl(v_uv);
    vec3 l = texture2D(u_left, uv).rgb;
    vec3 col = l;
    if (u_stereo > 0.5) {
        vec3 r = texture2D(u_right, uv).rgb;
        // Dubois-like grey mix keeps colours from rivalling between the eyes.
        float gl = dot(l, vec3(0.30, 0.59, 0.11));
        float gr = dot(r, vec3(0.30, 0.59, 0.11));
        col = u_stereo < 1.5 ? vec3(gl, gr, gr) : vec3(gr, gl, gr);
    }
    vec3 prev = texture2D(u_history, v_uv).rgb;
    gl_FragColor = vec4(mix(col, prev, u_blur), 1.0);
}
