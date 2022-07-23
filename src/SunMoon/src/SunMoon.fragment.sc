$input cpos
#include <bgfx_shader.sh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 SunMoonColor;

#define rain smoothstep(0.6, 0.3, FogAndDistanceControl.x)
#define nightFog pow(clamp(1.0 - FogColor.r * 1.5, 0.0, 1.0), 1.2)
#define duskFog clamp((FogColor.r - 0.15) * 1.25, 0.0, 1.0) * (1.0 - FogColor.b)

vec3 tonemap(vec3 color){
 	color = color / (0.9813 * color + 0.1511);
	return color;
}

void main(){
    vec3 color = mix(mix(vec3(1.0, 0.7, 0.2), vec3(0.8, 1.0, 1.2), nightFog), FogColor.rgb, rain);
    color = pow(color, vec3(2.2, 2.2, 2.2));
    vec3 shape = color * smoothstep(0.8, 0.9, 1.0 - length(cpos.xz * 16.0));
    shape += exp(-length(cpos.xz * 30.0)) * color * 0.1;
    shape = tonemap(shape);
    gl_FragColor = vec4(shape, 1.0) * SunMoonColor;
}
