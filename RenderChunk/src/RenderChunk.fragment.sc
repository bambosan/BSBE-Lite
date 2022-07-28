$input v_color0, v_fog, v_texcoord0, v_lightmapUV, worldPosi, chunkPos, frameTime, fogControl

#include <bgfx_shader.sh>

SAMPLER2D(s_MatTexture, 0);
SAMPLER2D(s_LightMapTexture, 1);
SAMPLER2D(s_SeasonsTexture, 2);

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
    for(int i = 0; i < 3; i++){
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

vec3 skyRender(vec3 pos, vec3 fogcolor, vec2 fogcontrol, float rain, float duskFog, float nightFog){
	vec3 zenithColor = mix(mix(mix(vec3(0.0, 0.38, 0.9), vec3(0.065, 0.15, 0.25), nightFog), vec3(0.5, 0.4, 0.6), duskFog), fogcolor.rgb * 2.0, rain);
	vec3 horizonColor = mix(mix(mix(vec3(0.75, 0.98, 1.15), vec3(1.0, 0.4, 0.5), duskFog), zenithColor + 0.15, nightFog), fogcolor.rgb * 2.0, rain);

	zenithColor = pow(zenithColor, vec3(2.2, 2.2, 2.2));
    horizonColor = pow(horizonColor, vec3(2.2, 2.2, 2.2));

    float horizon = exp(-saturate(pos.y) * 4.5);
	zenithColor = mix(zenithColor, horizonColor, horizon);
	if(fogcontrol.x == 0.0) zenithColor = pow(fogcolor.rgb, vec3(2.2, 2.2, 2.2));
	return zenithColor;
}

float calcWave(vec2 pos, float ftime){
    float wave = 1.0 - noise2d(pos * 1.3 - ftime * 1.5);
    wave += noise2d(pos + ftime);
	return wave;
}

vec3 waterNormal(vec3 flatNormal, vec2 waterPos, float ftime){
    float wave1 = calcWave(waterPos, ftime);
    float wave2 = calcWave(vec2(waterPos.x - 0.02, waterPos.y), ftime);
    float wave3 = calcWave(vec2(waterPos.x, waterPos.y - 0.02), ftime);

	float dx = wave1 - wave2;
    float dy = wave1 - wave3;
	vec3 rwaterN = normalize(vec3(dx, dy, 1.0)) * 0.5 + 0.5;

	mat3 fakeTBN = mat3(abs(flatNormal.y) + flatNormal.z, 0.0, flatNormal.x, 0.0, 0.0, flatNormal.y, -flatNormal.x, flatNormal.y, flatNormal.z);

	rwaterN = rwaterN * 2.0 - 1.0;
	rwaterN = normalize(mul(rwaterN, fakeTBN));
	return rwaterN;
}

float fresnelSchlick(float reflectance, float nDotV){
	return reflectance + (1.0 - reflectance) * pow(1.0 - nDotV, 5.0);
}

void main() {
    vec4 diffuse;

#if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY)
    diffuse.rgb = vec3(1.0, 1.0, 1.0);
#else
    diffuse = texture2D(s_MatTexture, v_texcoord0);

    #if defined(ALPHA_TEST) || defined(DEPTH_ONLY)
        if (diffuse.a < 0.5) {
            discard;
        }
    #endif

    #if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
        diffuse.rgb *= mix(vec3(1.0, 1.0, 1.0), texture2D(s_SeasonsTexture, v_color0.xy).rgb * 2.0, v_color0.b);
        diffuse.rgb *= v_color0.aaa;
    #else
        diffuse *= v_color0;
    #endif
#endif

#ifndef TRANSPARENT
    diffuse.a = 1.0;
#endif

#if !defined(DEPTH_ONLY_OPAQUE) || !defined(DEPTH_ONLY)
    diffuse.rgb = pow(diffuse.rgb, vec3(2.2, 2.2, 2.2));

    bool isWater = false;
#if !defined(SEASONS) && !defined(ALPHA_TEST)
    isWater = v_color0.a < 0.7 && v_color0.a > 0.5;
#endif

    float rain = smoothstep(0.6, 0.3, fogControl.x);
    float nightFog = pow(saturate(1.0 - v_fog.r * 1.5), 1.2);
    float duskFog = saturate((v_fog.r - 0.15) * 1.25) * (1.0 - v_fog.b);
    float lightmap = texture2D(s_LightMapTexture, vec2(0, 1)).r;
    float blightmap = max(v_lightmapUV.x * smoothstep(lightmap * v_lightmapUV.y, 1.0, v_lightmapUV.x), v_lightmapUV.x * rain * v_lightmapUV.y);
    
    vec3 flatNormal = normalize(cross(dFdx(chunkPos), dFdy(chunkPos)));
    float dusk = min(smoothstep(0.3, 0.5, lightmap), smoothstep(1.0, 0.8, lightmap)) * (1.0 - rain);
    float night = saturate(smoothstep(1.0, 0.2, lightmap) * 1.5);
    float indoor = smoothstep(0.95, 0.9, v_lightmapUV.y);
    float shadowm = mix(mix(mix(1.0, 0.2, saturate(abs(flatNormal.x))), 0.0, indoor), 0.0, rain);
	shadowm = mix(shadowm, 1.0, smoothstep(lightmap * v_lightmapUV.y, 1.0, v_lightmapUV.x));

	vec3 ambLmap = texture2D(s_LightMapTexture, vec2(0.0, v_lightmapUV.y)).rgb * lightmap * 0.5;
    vec3 blColor = vec3(1.0, 0.35, 0.0) * blightmap + pow(blightmap, 5.0);
	ambLmap = ambLmap + blColor;
	vec3 ambLcolor =  mix(mix(vec3(0.9, 0.8, 0.6), vec3(0.9, 0.3, 0.0), dusk), vec3(0.0, 0.05, 0.2), night) * shadowm;
	ambLmap = ambLmap + ambLcolor;
	diffuse.rgb = diffuse.rgb * ambLmap;

    if(isWater){
        diffuse = vec4(diffuse.rgb * 0.3, indoor * 0.5 + 0.15);
        flatNormal = waterNormal(flatNormal, chunkPos.xz, frameTime);
        vec3 reflectedVec = reflect(normalize(worldPosi), flatNormal);
        vec3 viewDirection = normalize(-worldPosi);

        float nDotV = saturate(dot(flatNormal, viewDirection));
        float zenith = saturate(reflectedVec.y);
        float fresnel = fresnelSchlick(0.5, nDotV);
        fresnel *= saturate(1.0 - indoor);

        vec3 skyRef = skyRender(reflectedVec, v_fog.rgb, fogControl, rain, duskFog, nightFog);
        diffuse = mix(diffuse, vec4(skyRef + (blColor * 0.5), 1.0), fresnel);

        reflectedVec = reflectedVec / reflectedVec.y;
        fresnel = fresnelSchlick(0.3, nDotV);
        fresnel *= saturate(1.0 - indoor);

        vec3 cloudColor = mix(mix(mix(vec3(0.85, 1.0, 1.1), vec3(0.9, 0.6, 0.3), duskFog), vec3(0.15, 0.2, 0.29), nightFog), v_fog.rgb * 2.0, rain);
        cloudColor = pow(cloudColor, vec3(2.2, 2.2, 2.2));
        float cm = cloudMap(reflectedVec.xz, frameTime, rain);
        diffuse = mix(diffuse, vec4(cloudColor, 1.0), cm * zenith * fresnel);
        diffuse.rgb *= max(v_lightmapUV.x, v_lightmapUV.y);
    }

	if(fogControl.x == 0.0){
        if(!isWater){
            float causMap = calcWave(chunkPos.xz, frameTime);
            diffuse.rgb += diffuse.rgb * saturate(causMap) * v_lightmapUV.y;
        }
        diffuse.rgb += diffuse.rgb * (v_lightmapUV.x * v_lightmapUV.x) * (1.0 - v_lightmapUV.y);
	}

	vec3 nFogColor = skyRender(normalize(worldPosi), v_fog.rgb, fogControl, rain, duskFog, nightFog);
    diffuse.rgb = mix(diffuse.rgb, nFogColor, v_fog.a);

    vec3 curr = unchartedModified(diffuse.rgb * 6.0);
	diffuse.rgb = pow(curr / unchartedModified(vec3(15.0, 15.0, 15.0)), vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
#endif

    gl_FragColor = diffuse;
}
