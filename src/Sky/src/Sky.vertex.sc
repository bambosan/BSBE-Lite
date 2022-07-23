$input a_position
$output v_color0, cpos
#include <bgfx_shader.sh>

void main(){
#if defined(FALLBACK) || defined(GEOMETRY_PREPASS)
    gl_Position = vec4(0.0, 0.0, 0.0, 0.0);
#else
    cpos = a_position.xyz;
    vec3 npos = a_position.xyz;
    npos.y -= length(a_position.xyz) * 0.2;
    gl_Position = mul(u_modelViewProj, vec4(npos, 1.0));
#endif
}
