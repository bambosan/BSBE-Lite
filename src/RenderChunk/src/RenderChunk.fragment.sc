$input v_color0, v_fog, v_texcoord0, v_lightmapUV, wpos, cpos

#include <bgfx_shader.sh>

uniform vec4 FogColor;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;

SAMPLER2D(s_MatTexture, 0);
SAMPLER2D(s_LightMapTexture, 1);
SAMPLER2D(s_SeasonsTexture, 2);

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
    for(int i = 0; i < 3; i++){
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

float calcWave(vec2 pos){
    float wave = 1.0 - noise2d(pos * 1.3 - ViewPositionAndTime.w * 1.5);
    wave += noise2d(pos + ViewPositionAndTime.w);
	return wave;
}

vec3 waterNormal(vec3 flatNormal, vec2 waterPos){
    float wave1 = calcWave(waterPos);
    float wave2 = calcWave(vec2(waterPos.x - 0.02, waterPos.y));
    float wave3 = calcWave(vec2(waterPos.x, waterPos.y - 0.02));

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

void addReflection(inout vec4 diffuse, vec3 flatNormal, vec3 blColor, vec3 wpos, vec2 v_lightmapUV){
	vec3 reflectedVec = reflect(normalize(wpos), flatNormal);
    vec3 viewDirection = normalize(-wpos);

	float nDotV = clamp(dot(flatNormal, viewDirection), 0.0, 1.0);
    float zenith = clamp(reflectedVec.y, 0.0, 1.0);
	float fresnel = fresnelSchlick(0.5, nDotV);

	diffuse = vec4(0.0, 0.0, 0.0, 0.0);
	vec3 skyRef = skyRender(reflectedVec);
	diffuse = mix(diffuse, vec4(skyRef + (blColor * 0.5), 1.0), fresnel);

	reflectedVec = reflectedVec / reflectedVec.y;
    fresnel = fresnelSchlick(0.3, nDotV);

    vec3 cloudColor = mix(mix(mix(vec3(0.85, 1.0, 1.1), vec3(0.9, 0.6, 0.3), duskFog), vec3(0.15, 0.2, 0.29), nightFog), FogColor.rgb * 2.0, rain);
    cloudColor = pow(cloudColor, vec3(2.2, 2.2, 2.2));
	diffuse = mix(diffuse, vec4(cloudColor, 1.0), cloudMap(reflectedVec.xz) * zenith * fresnel);

    float nDotH = clamp(dot(flatNormal, normalize(viewDirection + vec3(-0.98, 0.173, 0.0))), 0.0, 1.0);
	diffuse += pow(nDotH, 230.0) * vec4(skyRef, 1.0) * duskFog;
	diffuse.rgb *= max(v_lightmapUV.x, smoothstep(0.845, 1.0, v_lightmapUV.y) * 0.7 + 0.3);
}

void addIllum(inout vec3 diffuse, vec3 flatNormal, vec3 blColor, float lightmap, vec2 v_lightmapUV){
    float dusk = min(smoothstep(0.3, 0.5, lightmap), smoothstep(1.0, 0.8, lightmap)) * (1.0 - rain);
    float night = clamp(smoothstep(1.0, 0.2, lightmap) * 1.5, 0.0, 1.0);
    float shadowm = mix(mix(mix(1.0, 0.2, clamp(abs(flatNormal.x), 0.0, 1.0)), 0.0, smoothstep(0.95, 0.9, v_lightmapUV.y)), 0.0, rain);
	shadowm = mix(shadowm, 1.0, smoothstep(lightmap * v_lightmapUV.y, 1.0, v_lightmapUV.x));

	vec3 ambLmap = mix(mix(vec3(0.6, 0.6, 0.6), vec3(0.0, 0.0, 0.0), night), vec3(0.5, 0.5, 0.5), rain * (1.0 - night)) * v_lightmapUV.y;
	ambLmap += blColor;
	vec3 ambLcolor = mix(mix(vec3(1.1, 1.1, 0.8), vec3(1.0, 0.5, 0.0), dusk), vec3(0.05, 0.15, 0.4), night) * shadowm;
	ambLmap += ambLcolor;
	diffuse *= ambLmap;
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

    diffuse.rgb = pow(diffuse.rgb, vec3(2.2, 2.2, 2.2));

    bool isWater = false;
#if !defined(SEASONS) && !defined(ALPHA_TEST)
    isWater = v_color0.a < 0.7 && v_color0.a > 0.5;
#endif

    float lightmap = texture2D(s_LightMapTexture, vec2(0, 1)).r;
    float blightmap = max(v_lightmapUV.x * smoothstep(lightmap * v_lightmapUV.y, 1.0, v_lightmapUV.x), v_lightmapUV.x * rain * v_lightmapUV.y);
    vec3 blColor = vec3(1.0, 0.35, 0.0) * blightmap + pow(blightmap, 5.0);

    vec3 flatNormal = normalize(cross(dFdx(cpos), dFdy(cpos)));
    addIllum(diffuse.rgb, flatNormal, blColor, lightmap, v_lightmapUV);

    if(isWater){
        flatNormal = waterNormal(flatNormal, cpos.xz);
        addReflection(diffuse, flatNormal, blColor, wpos, v_lightmapUV);
    }

	if(FogAndDistanceControl.x == 0.0){
        if(!isWater){
            float causMap = calcWave(cpos.xz);
            diffuse.rgb = vec3(0.3, 0.6, 1.0) * diffuse.rgb + diffuse.rgb * max(causMap, 0.0) * v_lightmapUV.y;
        }
        diffuse.rgb += diffuse.rgb * (v_lightmapUV.x * v_lightmapUV.x) * (1.0 - v_lightmapUV.y);
	}

	vec3 nFogColor = skyRender(normalize(wpos));
    diffuse.rgb = mix(diffuse.rgb, nFogColor, v_fog.a);

    diffuse.rgb = tonemap(diffuse.rgb);
    gl_FragColor = diffuse;
}
