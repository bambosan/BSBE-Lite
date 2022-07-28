$input v_fog, cubePos, fogControl, frameTime
#include <bgfx_shader.sh>

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

float cloudMap(vec2 pos, float ftime, float rain){
    float tot = 0.0;
    float den = saturate(1.0 - rain);
    pos *= 1.6;
    pos += ftime * 0.001;
    for(int i = 0; i < 4; i++){
        tot += noise2d(pos) * den;
        den *= 0.5;
        pos *= 2.5;
        pos += tot;
        pos += ftime * 0.1;
    }
    return 1.0 - pow(0.1, clamp(1.0 - tot, 0.0, 1.0));
}

vec3 unchartedModified(vec3 color){
	float A = 0.25;		
	float B = 0.29;
	float C = 0.10;			
	float D = 0.2;		
	float E = 0.03;
	float F = 0.35;
	return ((color * (A * color + C * B) + D * E) / (color * (A * color + B) + D * F)) - E / F;
}

vec3 skyRender(vec3 pos, vec3 fogcolor, vec2 fogcontrol, float rain, float nightFog, float duskFog){
	vec3 zenithColor = mix(mix(mix(vec3(0.0, 0.38, 0.9), vec3(0.065, 0.15, 0.25), nightFog), vec3(0.5, 0.4, 0.6), duskFog), fogcolor.rgb * 2.0, rain);
	vec3 horizonColor = mix(mix(mix(vec3(0.75, 0.98, 1.15), vec3(1.0, 0.4, 0.5), duskFog), zenithColor + 0.15, nightFog), fogcolor.rgb * 2.0, rain);

	zenithColor = pow(zenithColor, vec3(2.2, 2.2, 2.2));
    horizonColor = pow(horizonColor, vec3(2.2, 2.2, 2.2));

    float horizon = exp(-saturate(pos.y) * 4.5);
	zenithColor = mix(zenithColor, horizonColor, horizon);
	if(fogcontrol.x == 0.0) zenithColor = pow(fogcolor.rgb, vec3(2.2, 2.2, 2.2));
	return zenithColor;
}

void main(){
    float rain = smoothstep(0.6, 0.3, fogControl.x);
    float nightFog = pow(saturate(1.0 - v_fog.r * 1.5), 1.2);
    float duskFog = saturate((v_fog.r - 0.15) * 1.25) * (1.0 - v_fog.b);

    vec3 adjPos = normalize(vec3(cubePos.x, -cubePos.y + 0.23, -cubePos.z));
    vec3 coudPos = adjPos / adjPos.y;
    float cm = cloudMap(coudPos.xz, frameTime, rain);

    vec3 cloudColor = mix(mix(mix(vec3(0.85, 1.0, 1.1), vec3(0.9, 0.6, 0.3), duskFog), vec3(0.15, 0.2, 0.29), nightFog), v_fog.rgb * 2.0, rain);
    cloudColor = pow(cloudColor, vec3(2.2, 2.2, 2.2));
    vec3 newSkyColor = skyRender(adjPos, v_fog.rgb, fogControl, rain, nightFog, duskFog);
    
	vec4 color = vec4(newSkyColor, exp(-saturate(adjPos.y) * 5.0));
	color = mix(color, vec4(cloudColor, cm), cm * smoothstep(1.0, 0.95, length(adjPos.xz)) * step(0.0, adjPos.y));
	
    vec3 curr = unchartedModified(color.rgb * 7.0);
	color.rgb = pow(curr / unchartedModified(vec3(15.0, 15.0, 15.0)), vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
	gl_FragColor = color;
}
