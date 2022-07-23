$input a_position
$output cpos
#include <bgfx_shader.sh>

void main(){
    cpos = a_position.xyz;
    gl_Position = mul(u_modelViewProj, vec4(a_position.xyz * vec3(5.0, 0.0, 5.0), 1.0));
}
