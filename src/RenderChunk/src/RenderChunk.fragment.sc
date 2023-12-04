$input v_color0, v_fog, v_position, v_worldpos, v_texcoord0, v_lightmapUV
#include <bgfx_shader.sh>

uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;

SAMPLER2D(s_MatTexture, 0);
SAMPLER2D(s_SeasonsTexture, 1);
SAMPLER2D(s_LightMapTexture, 2);

const float pi = 3.1415926;
const float tau = 6.28318531;

// bayer dither by Jodie
// used it for reduce strange color banding
float Bayer2(vec2 a) {
    a = floor(a);
    return fract(dot(a, vec2(0.5, a.y * 0.75)));
}

#define Bayer4(a) (Bayer2(0.5 * (a)) * 0.25 + Bayer2(a))
#define Bayer8(a) (Bayer4(0.5 * (a)) * 0.25 + Bayer2(a))
#define Bayer16(a) (Bayer8(0.5 * (a)) * 0.25 + Bayer2(a))
#define Bayer32(a) (Bayer16(0.5 * (a)) * 0.25 + Bayer2(a))
#define Bayer64(a) (Bayer32(0.5 * (a)) * 0.25 + Bayer2(a))

// https://github.com/bWFuanVzYWth/OriginShader
float fogTime(float fogColorG){
    return clamp(((349.305545 * fogColorG - 159.858192) * fogColorG + 30.557216) * fogColorG - 1.628452, -1.0, 1.0);
}

float getLum(vec3 color){
    return dot(color, vec3(0.2125, 0.7154, 0.0721));
}

vec3 linColor(vec3 color){
    return pow(color, vec3(2.2, 2.2, 2.2));
}

vec3 saturation(vec3 color, float sat){
    float gray = getLum(color);
    return mix(vec3(gray, gray, gray), color, sat);
}

vec3 jodieTonemap(vec3 c){
    vec3 tc = c / (c + 1.0);
    return mix(c / (getLum(c) + 1.0), tc, tc);
}

vec3 sunColor(float sunAngle){
    sunAngle = clamp(sin(sunAngle) + 0.1, 0.0, 1.0);
    return vec3((1.0 - sunAngle) + sunAngle, sunAngle, sunAngle * sunAngle) * exp2(log2(sunAngle) * 0.6);
}

vec3 moonColor(float sunAngle){
    sunAngle = clamp(-sin(sunAngle), 0.0, 1.0);
    return vec3((1.0 - sunAngle) * 0.2 + sunAngle, sunAngle, sunAngle) * exp2(log2(sunAngle) * 0.6) * 0.05;
}

vec3 zenithColor(float sunAngle){
    sunAngle = clamp(sin(sunAngle), 0.0, 1.0);
    return vec3(0.0, sunAngle * 0.13 + 0.003, sunAngle * 0.5 + 0.01);
}

void main() {
    vec4 diffuse;

#if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY)
    diffuse.rgb = vec3(1.0, 1.0, 1.0);
#else
    diffuse = texture2D(s_MatTexture, v_texcoord0);

#if defined(ALPHA_TEST)
    if (diffuse.a < 0.5) {
        discard;
    }
#endif

#if defined(SEASONS) && (defined(OPAQUE) || defined(ALPHA_TEST))
    diffuse.rgb *=
        mix(vec3(1.0, 1.0, 1.0),
            texture2D(s_SeasonsTexture, v_color0.xy).rgb * 2.0, v_color0.b);
    diffuse.rgb *= v_color0.aaa;
#else
    diffuse *= v_color0;
#endif
#endif

#ifndef TRANSPARENT
    diffuse.a = 1.0;
#endif

        diffuse.rgb = linColor(diffuse.rgb);

    float rain = smoothstep(0.6, 0.3, FogAndDistanceControl.x);
    float lightVis = texture2D(s_LightMapTexture, vec2(0.0, 1.0)).r;

    vec3 fnormal = normalize(cross(dFdx(v_position), dFdy(v_position)));
    float sunAngle = fogTime(FogColor.g);
    vec3 sunPos = normalize(vec3(cos(sunAngle), sin(sunAngle), 0.0));
    vec3 lightPos = sunPos.y > 0.0 ? sunPos : -sunPos;
    vec3 ambLight = texture2D(s_LightMapTexture, vec2(0.0, v_lightmapUV.y)).rgb * 0.2;
        ambLight += float(texture2DLod(s_MatTexture, v_texcoord0, 0.0).a > 0.91 && texture2DLod(s_MatTexture, v_texcoord0, 0.0).a < 0.93) * 3.0;
        ambLight += vec3(1.0, 0.8, 0.6) * max(v_lightmapUV.x * smoothstep(lightVis * v_lightmapUV.y, 1.0, v_lightmapUV.x), v_lightmapUV.x * rain * v_lightmapUV.y);

    float shadowMap = mix(mix(mix(clamp(dot(lightPos, fnormal), 0.0, 1.0) * (2.0 - clamp(lightPos.y, 0.0, 1.0)), 0.0, smoothstep(0.87, 0.84, v_lightmapUV.y)), 0.0, rain), 1.0, smoothstep(lightVis * v_lightmapUV.y, 1.0, v_lightmapUV.x));
        ambLight += (mix(sunColor(sunAngle) * vec3(1.2, 1.0, 0.8), linColor(FogColor.rgb), rain) * shadowMap);
        diffuse.rgb = diffuse.rgb * ambLight;

    vec3 npos = normalize(v_worldpos);
    vec3 fogColor = mix(zenithColor(sunAngle), saturation(sunColor(sunAngle) + moonColor(sunAngle), 0.5), exp(-clamp(npos.y, 0.0, 1.0) * 4.0));
        fogColor += sunColor(sunAngle) * exp(-distance(npos, sunPos) * 2.0) * exp(-clamp(npos.y, 0.0, 1.0) * 2.0) * 5.0;
        fogColor += moonColor(sunAngle) * exp(-distance(npos, -sunPos) * 2.0) * exp(-clamp(npos.y, 0.0, 1.0) * 2.0) * 5.0;
        fogColor = mix(fogColor, linColor(FogColor.rgb), max(step(FogAndDistanceControl.x, 0.0), rain));

    if(FogAndDistanceControl.x > 0.0){
        diffuse.rgb = mix(diffuse.rgb, zenithColor(sunAngle) * 2.0, clamp(length(-v_worldpos) * 0.01, 0.0, 1.0) * 0.01);
    }
        diffuse.rgb = mix(diffuse.rgb, fogColor, (FogAndDistanceControl.x <= 0.0) ?  (v_fog.a * v_fog.a) : v_fog.a);
        diffuse.rgb = diffuse.rgb * (Bayer64(gl_FragCoord.xy) * 0.5 + 0.5);
        diffuse.rgb = jodieTonemap(diffuse.rgb * 5.0);
        diffuse.rgb = saturation(diffuse.rgb, 1.1);
        diffuse.rgb = pow(diffuse.rgb, vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));

    gl_FragColor = diffuse;
}