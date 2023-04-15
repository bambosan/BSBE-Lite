#version 300 es
#pragma optimize(on)
precision highp float;

uniform vec4 FOG_COLOR;
uniform vec2 FOG_CONTROL;
uniform float TOTAL_REAL_WORLD_TIME;

uniform sampler2D TEXTURE_0;
uniform sampler2D TEXTURE_1;
uniform sampler2D TEXTURE_2;

#ifndef BYPASS_PIXEL_SHADER
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
        sunAngle = clamp(sunAngle + 0.1, 0.0, 1.0);
        return vec3((1.0 - sunAngle) + sunAngle, sunAngle, sunAngle * sunAngle) * exp2(log2(sunAngle) * 0.6);
    }

    vec3 moonColor(float sunAngle){
        sunAngle = clamp(-sunAngle, 0.0, 1.0);
        return vec3((1.0 - sunAngle) * 0.2 + sunAngle, sunAngle, sunAngle) * exp2(log2(sunAngle) * 0.6) * 0.05;
    }

    vec3 zenithColor(float sunAngle){
        sunAngle = clamp(sunAngle, 0.0, 1.0);
        return vec3(0.0, sunAngle * 0.13 + 0.003, sunAngle * 0.5 + 0.01);
    }

    in vec4 color;
    in vec3 position;
    in vec3 worldpos;
    centroid in vec2 uv0;
    centroid in vec2 uv1;
#endif

#ifdef FOG
    in float fogr;
#endif

out vec4 fragcolor;

void main(){
#ifdef BYPASS_PIXEL_SHADER
    fragcolor = vec4(0, 0, 0, 0);
    return;
#else
    vec4 albedo = texture(TEXTURE_0, uv0);
    #ifdef SEASONS_FAR
        albedo.a = 1.0;
    #endif
    #ifdef ALPHA_TEST
        if(albedo.a < 0.05) discard;
    #endif
    vec4 inColor = color;
    #if defined(BLEND)
        albedo.a *= inColor.a;
    #endif
    #ifndef SEASONS
        #if !defined(USE_ALPHA_TEST) && !defined(BLEND)
            albedo.a = inColor.a;
        #endif
        albedo.rgb *= inColor.rgb;
    #else
        albedo.rgb *= mix(vec3(1.0, 1.0, 1.0), texture(TEXTURE_2, inColor.xy).rgb * 2.0, inColor.b);
        albedo.rgb *= inColor.aaa;
        albedo.a = 1.0;
    #endif

        albedo.rgb = linColor(albedo.rgb);

    float rain = smoothstep(0.6, 0.3, FOG_CONTROL.x);
    float lightVis = texture(TEXTURE_1, vec2(0.0, 1.0)).r;

    vec3 fnormal = normalize(cross(dFdx(position), dFdy(position)));
    float sunAngle = fogTime(FOG_COLOR.g);
    vec3 sunPos = normalize(vec3(cos(sunAngle), sin(sunAngle), 0.0));
    vec3 lightPos = sunPos.y > 0.0 ? sunPos : -sunPos;
    vec3 ambLight = texture(TEXTURE_1, vec2(0.0, uv1.y)).rgb * 0.2;
        ambLight += float(textureLod(TEXTURE_0, uv0, 0.0).a > 0.91 && textureLod(TEXTURE_0, uv0, 0.0).a < 0.93) * 3.0;
        ambLight += vec3(1.0, 0.8, 0.6) * max(uv1.x * smoothstep(lightVis * uv1.y, 1.0, uv1.x), uv1.x * rain * uv1.y);

    float shadowMap = mix(mix(mix(clamp(dot(lightPos, fnormal), 0.0, 1.0) * (2.0 - clamp(lightPos.y, 0.0, 1.0)), 0.0, smoothstep(0.87, 0.84, uv1.y)), 0.0, rain), 1.0, smoothstep(lightVis * uv1.y, 1.0, uv1.x));
        ambLight += (mix(sunColor(sunPos.y) * vec3(1.2, 1.0, 0.8), linColor(FOG_COLOR.rgb), rain) * shadowMap);
        albedo.rgb = albedo.rgb * ambLight;

    vec3 npos = normalize(worldpos);
    vec3 fogColor = mix(zenithColor(sunPos.y), saturation(sunColor(sunPos.y) + moonColor(sunPos.y), 0.5), exp(-clamp(npos.y, 0.0, 1.0) * 4.0));
        fogColor += sunColor(sunPos.y) * exp(-distance(npos, sunPos) * 2.0) * exp(-clamp(npos.y, 0.0, 1.0) * 2.0) * 5.0;
        fogColor += moonColor(sunPos.y) * exp(-distance(npos, -sunPos) * 2.0) * exp(-clamp(npos.y, 0.0, 1.0) * 2.0) * 5.0;
        fogColor = mix(fogColor, linColor(FOG_COLOR.rgb), max(step(FOG_CONTROL.x, 0.0), rain));

    if(FOG_CONTROL.x > 0.0){
        albedo.rgb = mix(albedo.rgb, zenithColor(sunPos.y) * 2.0, clamp(length(-worldpos.xyz) * 0.01, 0.0, 1.0) * 0.01);
    }

    #ifdef FOG
        albedo.rgb = mix(albedo.rgb, fogColor, (FOG_CONTROL.x <= 0.0) ?  (fogr * fogr) : fogr);
    #endif

        albedo.rgb = albedo.rgb * (Bayer64(gl_FragCoord.xy) * 0.5 + 0.5);
        albedo.rgb = jodieTonemap(albedo.rgb * 5.0);
        albedo.rgb = saturation(albedo.rgb, 1.1);
        albedo.rgb = pow(albedo.rgb, vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));

    fragcolor = albedo;
#endif
}
