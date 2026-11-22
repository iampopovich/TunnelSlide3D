attribute vec3 a_position;   // unit cube, [-0.5, 0.5]
attribute vec3 a_normal;

uniform mat4 u_projView;
uniform mat4 u_model;

varying vec3 v_local;
varying vec3 v_normal;
varying float v_dist;

void main() {
    vec4 world = u_model * vec4(a_position, 1.0);
    v_local = a_position;
    v_normal = a_normal;
    v_dist = -world.z;
    gl_Position = u_projView * world;
}
