$input cpos
#include <bgfx_shader.sh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;

#define rain smoothstep(0.6, 0.3, FogAndDistanceControl.x)
#define nightFog pow(clamp(1.0 - FogColor.r * 1.5, 0.0, 1.0), 1.2)
#define duskFog clamp((FogColor.r - 0.15) * 1.25, 0.0, 1.0) * (1.0 - FogColor.b)

float hash(float n){
    return fract(sin(n) * 43758.5453);
}

float noise2d(vec2 pos){
    vec2 ip = floor(pos);
    vec2 fp = fract(pos);
    fp = fp * fp * (3.0 - 2.0 * fp);
    float n = ip.x + ip.y * 57.0;
    return mix(mix(hash(n), hash(n + 1.0), fp.x), mix(hash(n + 57.0), hash(n + 58.0), fp.x), fp.y);
}

float cloudMap(vec2 pos){
    float tot = 0.0;
    float den = saturate(1.0 - rain);
    pos *= 1.6;
    pos += ViewPositionAndTime.w * 0.001;
    for(int i = 0; i < 4; i++){
        tot += noise2d(pos) * den;
        den *= 0.5;
        pos *= 2.5;
        pos += tot;
        pos += ViewPositionAndTime.w * 0.1;
    }
    return 1.0 - pow(0.1, clamp(1.0 - tot, 0.0, 1.0));
}

vec3 tonemap(vec3 color){
 	color = color / (0.9813 * color + 0.1511);
	return color;
}

vec3 skyRender(vec3 pos){
	vec3 zenithColor = mix(mix(mix(vec3(0.0, 0.4, 0.9), vec3(0.065, 0.15, 0.25), nightFog), vec3(0.5, 0.4, 0.6), duskFog), FogColor.rgb * 2.0, rain);
	vec3 horizonColor = mix(mix(mix(vec3(0.75, 0.98, 1.15), vec3(1.0, 0.4, 0.5), duskFog), zenithColor + 0.15, nightFog), FogColor.rgb * 2.0, rain);

	zenithColor = pow(zenithColor, vec3(2.2, 2.2, 2.2));
    horizonColor = pow(horizonColor, vec3(2.2, 2.2, 2.2));

    float horizon = exp(-clamp(pos.y, 0.0, 1.0) * 4.5) + (exp(-clamp(length(pos.zy), 0.0, 1.0) * 4.0) * duskFog);
	zenithColor = mix(zenithColor, horizonColor, horizon);
	if(FogAndDistanceControl.x == 0.0) zenithColor = pow(FogColor.rgb, vec3(2.2, 2.2, 2.2));
	return zenithColor;
}

void main(){
    vec3 adjPos = normalize(vec3(cpos.x, -cpos.y + 0.23, -cpos.z));
    vec3 coudPos = adjPos / adjPos.y;
    float cm = cloudMap(coudPos.xz);

    vec3 cloudColor = mix(mix(mix(vec3(0.85, 1.0, 1.1), vec3(0.9, 0.6, 0.3), duskFog), vec3(0.15, 0.2, 0.29), nightFog), FogColor.rgb * 2.0, rain);
    cloudColor = pow(cloudColor, vec3(2.2, 2.2, 2.2));

	vec4 color = vec4(skyRender(adjPos), exp(-clamp(adjPos.y, 0.0, 1.0) * 5.0));
	color = mix(color, vec4(cloudColor, cm), cm * smoothstep(1.0, 0.95, length(adjPos.xz)) * step(0.0, adjPos.y));
	color.rgb = tonemap(color.rgb);
	gl_FragColor = color;
}
