// Tunnel surface. surface.glsl is prepended at load time.
attribute vec2 a_coord;      // x = position from the seam in [0, 1], y = distance ahead

uniform mat4 u_projView;
uniform float u_playerA;     // player position from the seam, [0, 1)

varying vec2 v_coord;
varying float v_shape;

void main() {
    float dist = a_coord.y;
    float shape = shapeAt(dist);
    float th = (a_coord.x - u_playerA) * TAU;
    v_coord = a_coord;
    v_shape = shape;
    gl_Position = u_projView * vec4(surfacePoint(th, shape), -dist, 1.0);
}
