#version 300 es
#pragma optimize(on)
precision highp float;

uniform vec4 FOG_COLOR;
uniform vec2 FOG_CONTROL;
uniform float TOTAL_REAL_WORLD_TIME;

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

float fbm(vec2 pos){
    float s = 0.0;
    float den = 1.0;
    pos *= 1.2;
    pos += TOTAL_REAL_WORLD_TIME * 0.001;
    for(int i = 0; i < 4; i++){
        s += noise2d(pos) * den;
        den *= 0.5;
        pos *= 2.5;
        pos += s;
        pos -= TOTAL_REAL_WORLD_TIME * 0.1;
    }
    return smoothstep(1.0, 0.0, s) * 0.5;
}

in vec3 position;
out vec4 fragcolor;

void main(){    
    float sunAngle = fogTime(FOG_COLOR.g);
    vec3 sunPos = normalize(vec3(cos(sunAngle), sin(sunAngle), 0.0));
    vec3 pos = normalize(vec3(position.x, -position.y + 0.127, -position.z));

    vec3 color = mix(zenithColor(sunPos.y), saturation(sunColor(sunPos.y) + moonColor(sunPos.y), 0.5), exp(-clamp(pos.y, 0.0, 1.0) * 4.0));

        color += sunColor(sunPos.y) * exp(-distance(pos, sunPos) * 2.0) * exp(-clamp(pos.y, 0.0, 1.0) * 2.0) * 5.0;
        color += moonColor(sunPos.y) * exp(-distance(pos, -sunPos) * 2.0) * exp(-clamp(pos.y, 0.0, 1.0) * 2.0) * 5.0;

        color += sunColor(sunPos.y) * smoothstep(0.999, 1.0, dot(pos, sunPos)) * 100.0 * pow(clamp(pos.y, 0.0, 1.0), 0.7);
        color += moonColor(sunPos.y) * smoothstep(0.999, 1.0, dot(pos, -sunPos)) * 100.0 * pow(clamp(pos.y, 0.0, 1.0), 0.7);

        color = mix(color,  sunColor(sunPos.y) + moonColor(sunPos.y), fbm(pos.xz / pos.y) * smoothstep(0.0, 0.6, pos.y));
        color = mix(color, linColor(FOG_COLOR.rgb), max(step(FOG_CONTROL.x, 0.0), smoothstep(0.6, 0.3, FOG_CONTROL.x)));

        color = color * (Bayer64(gl_FragCoord.xy) * 0.5 + 0.5);
        color = jodieTonemap(color * 5.0);
        color = saturation(color, 1.1);
        color = pow(color, vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));

    fragcolor = vec4(color, 1.0);
}
