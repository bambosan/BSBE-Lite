$input a_color0, a_position, a_texcoord0, a_texcoord1
#ifdef INSTANCING
    $input i_data0, i_data1, i_data2
#endif
$output v_color0, v_fog, v_position, v_worldpos, v_texcoord0, v_lightmapUV

#include <bgfx_shader.sh>

uniform vec4 RenderChunkFogAlpha;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;

vec3 calcwave(vec3 pos, float fm, float mm, float ma, float f0, float f1, float f2, float f3, float f4, float f5){
    float PI48 = 150.796447372;
    float pi2wt = PI48 * ViewPositionAndTime.w;
    float mag = sin(dot(vec4(pi2wt * fm, pos.x, pos.z, pos.y), vec4(0.5, 0.5, 0.5, 0.5))) * mm + ma;
    vec3 d012 = sin(pi2wt * vec3(f0, f1, f2));
    vec3 ret = sin(pi2wt * vec3(f3, f4, f5) + vec3(d012.x + d012.y, d012.y + d012.z, d012.z + d012.x) - pos) * mag;
    return ret;
}

vec3 calcmove(vec3 pos, float f0, float f1, float f2, float f3, float f4, float f5, vec3 amp1, vec3 amp2){
    vec3 move1 = calcwave(pos, 0.0054, 0.0400, 0.0400, 0.0127, 0.0089, 0.0114, 0.0063, 0.0224, 0.0015) * amp1;
    vec3 move2 = calcwave(pos + move1, 0.07, 0.0400, 0.0400, f0, f1, f2, f3, f4, f5) * amp2;
    return move1 + move2;
}

void main() {
    mat4 model;
#ifdef INSTANCING
    model = mtxFromCols(i_data0, i_data1, i_data2, vec4(0, 0, 0, 1));
#else
    model = u_model[0];
#endif

    vec3 worldPos = mul(model, vec4(a_position, 1.0)).xyz;
    vec4 color;
#ifdef RENDER_AS_BILLBOARDS
    worldPos += vec3(0.5, 0.5, 0.5);
    vec3 viewDir = normalize(worldPos - ViewPositionAndTime.xyz);
    vec3 boardPlane = normalize(vec3(viewDir.z, 0.0, -viewDir.x));
    worldPos = (worldPos -
        ((((viewDir.yzx * boardPlane.zxy) - (viewDir.zxy * boardPlane.yzx)) *
        (a_color0.z - 0.5)) +
        (boardPlane * (a_color0.x - 0.5))));
    color = vec4(1.0, 1.0, 1.0, 1.0);
#else
    color = a_color0;
#endif
    v_position = a_position.xyz;
    v_worldpos = worldPos.xyz;

    vec3 modelCamPos = (ViewPositionAndTime.xyz - worldPos);
    float camDis = length(modelCamPos);
    vec4 fogColor;
    fogColor.rgb = FogColor.rgb;
    fogColor.a = clamp((((camDis / FogAndDistanceControl.z) - FogAndDistanceControl.x) / (FogAndDistanceControl.y - FogAndDistanceControl.x)), 0.0, 1.0);

    v_texcoord0 = a_texcoord0;
    v_lightmapUV = a_texcoord1;
    v_color0 = color;
    v_fog = fogColor;

    // https://github.com/McbeEringi/esbe-2g
    vec3 ajp = vec3(a_position.x == 16.0 ? 0.0 : a_position.x, abs(a_position.y - 8.0), a_position.z == 16.0 ? 0.0 : a_position.z);

    #ifdef ALPHA_TEST
        if(color.r != color.g && color.g != color.b){
            worldPos.xyz += calcmove(ajp, 0.0040, 0.0064, 0.0043, 0.0035, 0.0037, 0.0041, vec3(1.0, 0.2, 1.0), vec3(0.5, 0.1, 0.5)) * 1.4 * (1.0 - clamp(length(worldPos.xyz) / FogAndDistanceControl.w, 0.0, 1.0)) * a_texcoord1.y;
        }
    #endif

    #if !defined(SEASONS) && !defined(ALPHA_TEST)
        if(color.a > 0.4 && color.a < 0.6){
            worldPos.y += sin(ViewPositionAndTime.w * 4.0 + ajp.x + ajp.z + ajp.y) * 0.06 * fract(a_position.y);
        }
    #endif

    if(FogAndDistanceControl.x <= 0.0){
        worldPos.xyz += sin(ViewPositionAndTime.w * 4.0 + ajp.x + ajp.z + ajp.y) * 0.03;
    }

    gl_Position = mul(u_viewProj, vec4(worldPos, 1.0));
}
