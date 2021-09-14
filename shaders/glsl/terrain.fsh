// __multiversion__
#include "fragmentVersionCentroid.h"
#include "uniformShaderConstants.h"
#include "uniformPerFrameConstants.h"

#if __VERSION__ >= 300
	#ifndef BYPASS_PIXEL_SHADER
		#if defined(TEXEL_AA) && defined(TEXEL_AA_FEATURE)
			_centroid in highp vec2 uv0;
			_centroid in highp vec2 uv1;
		#else
			_centroid in vec2 uv0;
			_centroid in vec2 uv1;
		#endif
	#endif
#else
	#ifndef BYPASS_PIXEL_SHADER
		varying vec2 uv0;
		varying vec2 uv1;
	#endif
#endif

#ifdef FOG
	varying float fogr;
#endif

varying vec4 color;
#include "util.h"

LAYOUT_BINDING(0) uniform sampler2D TEXTURE_0;
LAYOUT_BINDING(1) uniform sampler2D TEXTURE_1;
LAYOUT_BINDING(2) uniform sampler2D TEXTURE_2;

#include "common.glsl"
varying hp vec3 cpos;
varying hp vec3 wpos;


float cwav(hp vec2 pos){
	hp float wave = noise(pos * 1.5 - TOTAL_REAL_WORLD_TIME) + noise(pos * 2.0 + TOTAL_REAL_WORLD_TIME * 1.3);
	return max0(wave);
}
vec3 calcnw(vec3 n){
	hp float w1 = cwav(cpos.xz), w2 = cwav(vec2(cpos.x - 0.015, cpos.z)), w3 = cwav(vec2(cpos.x, cpos.z - 0.015));
	vec3 wn = normalize(vec3(w1 - w2, w1 - w3, 1.0)) * 0.5 + 0.5;
		wn = wn * 2.0 - 1.0;
	mat3 ftbn = mat3(abs(n.y) + n.z, 0.0, n.x, 0.0, 0.0, n.y, -n.x, n.y, n.z);
	return normalize(wn * ftbn);
}

float fschlick(float f0, hp float ndv){ return f0 + (1.0 - f0) * sqr5(1.0 - ndv); }
vec4 reflection(vec4 diff, vec3 n, vec3 lsc){
	hp vec3 rv = reflect(normalize(wpos), n), vdir = normalize(-wpos);
	float ndv = max0(dot(n, vdir)), zen = max0(rv.y);
	float fresnel = fschlick(0.04, ndv);
	diff = vec4(diff.rgb, fresnel);
	vec3 skyc = sr(rv);
		fresnel = fschlick(0.2, ndv);
	diff = mix(diff, vec4(skyc + (lsc * 0.5), 1.0), fresnel);
        rv /= rv.y;
	diff = mix(diff, vec4(ccc(), 1.0), cmap(rv.xz) * zen * 2.0 * fresnel);
	hp float ndh = max0(dot(n, normalize(vdir + vec3(-0.98, 0.173, 0.0))));
         ndh = pow(ndh, 230.0);
	diff += ndh * vec4(skyc, 1.0) * dfog;
	diff.rgb *= max(uv1.x, smoothstep(0.8, 1.0, uv1.y) * 0.8 + 0.2);
	return diff;
}

vec3 illum(vec3 diff, vec3 n, vec3 lsc, float lmb, bool endw, bool nether){
	float dusk = min(smoothstep(0.4, 1.0, lmb), smoothstep(1.0, 0.8, lmb)) * (1.0 - rain), night = smoothstep(1.0, 0.2, lmb);
	float smap = mix(mix(mix(mix(1.0, 0.2, abs(n.x)), 0.0, smoothstep(0.87, 0.845, uv1.y)), 0.0, rain), 1.0, smoothstep(lmb * sqr3(uv1.y), 1.0, uv1.x));
	vec3 almap = mix(mix(mix(mix(vec3(0.3, 0.55, 1.0), vec3(0.0), night), vec3(0.5), rain * (1.0 - night)) * uv1.y, vec3(0.15, 0.1, 0.2), float(endw)), vec3(0.4), float(nether));
		almap += lsc;
	vec3 ambc = mix(mix(vec3(1.1, 1.1, 0.9), vec3(1.0, 0.5, 0.0), dusk), vec3(0.05, 0.12, 0.4), night);
		almap += (nether || endw) ? vec3(0.0) : ambc * smap;
	return diff * almap;
}

void main(){
#ifdef BYPASS_PIXEL_SHADER
	gl_FragColor = vec4(0, 0, 0, 0);
	return;
#else

#if USE_TEXEL_AA
	vec4 diffuse = texture2D_AA(TEXTURE_0, uv0);
#else
	vec4 diffuse = texture2D(TEXTURE_0, uv0);
#endif

#ifdef SEASONS_FAR
	diffuse.a = 1.0;
#endif

#if USE_ALPHA_TEST
	#ifdef ALPHA_TO_COVERAGE
	#define ALPHA_THRESHOLD 0.05
	#else
	#define ALPHA_THRESHOLD 0.5
	#endif
	if(diffuse.a < ALPHA_THRESHOLD) discard;
#endif

vec4 inColor = color;

#if defined(BLEND)
	diffuse.a *= inColor.a;
#endif

#ifndef SEASONS
	#if !USE_ALPHA_TEST && !defined(BLEND)
		diffuse.a = inColor.a;
	#endif
	diffuse.rgb *= (abs(color.r - color.g) < 2e-5 && abs(color.g - color.b) < 2e-5) ? sqrt(inColor.rgb) * 1.2 : normalize(inColor.rgb) * length(sqrt(inColor.rgb));
#else
	diffuse.rgb *= mix(vec3(1.0), texture2D(TEXTURE_2, inColor.xy).rgb * 2.0, inColor.b);
	diffuse.rgb *= inColor.aaa;
	diffuse.a = 1.0;
#endif

	diffuse.rgb = tl(diffuse.rgb);
	highp vec3 lmb = texture2D(TEXTURE_1, vec2(0, 1)).rgb;

	bool waterd = false, endw = lmb.r >= 0.4 && lmb.r <= 0.5 && lmb.g >= 0.5 && lmb.g < 0.6 && lmb.b >= 0.4 && lmb.b < 0.5, nether = FOG_CONTROL.x > 0.0 && FOG_CONTROL.x <= 0.06 && FOG_CONTROL.y >= 0.5 && FOG_CONTROL.y <= 0.55, undw = FOG_CONTROL.x == 0.0;
#if !defined(SEASONS) && !defined(ALPHA_TEST)
	waterd = inColor.a < 0.7 && inColor.a > 0.5;
#endif

	vec3 n = normalize(cross(dFdx(cpos.xyz), dFdy(cpos.xyz)));
	if(waterd) n = calcnw(n);
	float bls = max(uv1.x * smoothstep(lmb.r * sqr3(uv1.y), 1.0, uv1.x), uv1.x * rain * uv1.y);
	vec3 lsc = endw ? vec3(0.6, 0.2, 0.0) * (sqr3(uv1.x) + sqr5(uv1.x)) : vec3(1.0, 0.4, 0.0) * bls + sqr5(bls);
	diffuse.rgb = illum(diffuse.rgb, n, lsc, lmb.r, endw, nether);

	if(waterd) diffuse = reflection(diffuse, n, lsc); else if(undw){
		diffuse.rgb += diffuse.rgb * cwav(cpos.xz) * uv1.y;
		diffuse.rgb += diffuse.rgb * uv1.x * (1.0 - uv1.y);
	}
#ifdef FOG
	diffuse.rgb = mix(diffuse.rgb, sr(normalize(wpos)), undw ? sqr5(fogr) * 0.8 + 0.2 : fogr);
#endif
	diffuse.rgb = colcor(diffuse.rgb);

	gl_FragColor = diffuse;
#endif
}
