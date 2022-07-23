$input v_color0, cpos
#include <bgfx_shader.sh>

#if !defined(FALLBACK) || !defined(GEOMETRY_PREPASS)
uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;

#define rain smoothstep(0.6, 0.3, FogAndDistanceControl.x)
#define nightFog pow(clamp(1.0 - FogColor.r * 1.5, 0.0, 1.0), 1.2)
#define duskFog clamp((FogColor.r - 0.15) * 1.25, 0.0, 1.0) * (1.0 - FogColor.b)

vec3 tonemap(vec3 color){
 	color = color / (0.9813 * color + 0.1511);
	return color;
}

vec3 skyRender(float horizon){
	vec3 zenithColor = mix(mix(mix(vec3(0.0, 0.4, 0.9), vec3(0.065, 0.15, 0.25), nightFog), vec3(0.5, 0.4, 0.6), duskFog), FogColor.rgb * 2.0, rain);
	vec3 horizonColor = mix(mix(mix(vec3(0.75, 0.98, 1.15), vec3(1.0, 0.4, 0.5), duskFog), zenithColor + 0.15, nightFog), FogColor.rgb * 2.0, rain);

	zenithColor = pow(zenithColor, vec3(2.2, 2.2, 2.2));
    horizonColor = pow(horizonColor, vec3(2.2, 2.2, 2.2));

	zenithColor = mix(zenithColor, horizonColor, horizon);
	if(FogAndDistanceControl.x == 0.0) zenithColor = pow(FogColor.rgb, vec3(2.2, 2.2, 2.2));
	return zenithColor;
}
#endif

void main(){
#if defined(FALLBACK) || defined(GEOMETRY_PREPASS)
	gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
#else
    float horizon = length(cpos);
    vec3 color = skyRender((horizon * horizon) * 4.0);
    color = tonemap(color);
    gl_FragColor = vec4(color, 1.0);
#endif
}
