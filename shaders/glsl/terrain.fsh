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

vec3 gett(vec3 n){
	vec3 t = vec3(0, 0, 0);
	if(n.x > 0.0){ t = vec3(0, 0, -1); } else if(n.x < -0.5){ t = vec3(0, 0, 1);
	} else if(n.y > 0.0){ t = vec3(1, 0, 0); } else if(n.y < -0.5){ t = vec3(1, 0, 0);
	} else if(n.z > 0.0){ t = vec3(1, 0, 0); } else if(n.z < -0.5){ t = vec3(-1, 0, 0); }
	return t;
}
float cwav(hp vec2 pos){
	hp float wave = (1.0 - noise(pos * 1.3 - TOTAL_REAL_WORLD_TIME * 1.5)) + noise(pos + TOTAL_REAL_WORLD_TIME);
	return wave;
}
vec3 calcnw(vec3 n){
	hp float w1 = cwav(cpos.xz), w2 = cwav(vec2(cpos.x - 0.02, cpos.z)), w3 = cwav(vec2(cpos.x, cpos.z - 0.02));
	hp float dx = w1 - w2, dy = w1 - w3;
	vec3 wn = normalize(vec3(dx, dy, 1.0)) * 0.5 + 0.5;
	vec3 t = gett(n), b = normalize(cross(t, n));
	mat3 tbn = transpose(mat3(t, b, n));
		wn = wn * 2.0 - 1.0;
		wn = normalize(wn * tbn);
	return wn;
}

float fschlick(float f0, hp float ndv){
	return f0 + (1.0 - f0) * sqr5(1.0 - ndv);
}
vec4 reflection(vec4 diff, vec3 n, vec3 lsc){
	vec3 rv = reflect(normalize(wpos), n), vdir = normalize(-wpos);
	float ndv = max0(dot(n, vdir)), zen = max0(rv.y) * 1.2;
	float fresnel = fschlick(0.5, ndv);
	diff = vec4(0.1);
	vec3 skyc = sr(rv);
	diff = mix(diff, vec4(skyc + (lsc * 0.5), 1.0), fresnel);
		rv = rv / rv.y;
	diff = mix(diff, vec4(ccc(), 1.0), cmap(rv.xz) * zen * fresnel);
	hp float ndh = max0(dot(n, normalize(vdir + vec3(-0.98, 0.173, 0.0))));
	diff += pow(ndh, 230.0) * vec4(skyc, 1.0) * dfog;
	diff.rgb *= max(uv1.x, smoothstep(0.845, 1.0, uv1.y));
	return diff;
}

vec3 illum(vec3 diff, vec3 n, vec3 lsc, float lmb){
	float dusk = min(smoothstep(0.3, 0.5, lmb), smoothstep(1.0, 0.8, lmb)) * (1.0 - rain), night = saturate(smoothstep(1.0, 0.2, lmb) * 1.5);
	hp float smap = mix(mix(mix(1.0, 0.2, max0(abs(n.x))), 0.0, smoothstep(0.87, 0.845, uv1.y)), 0.0, rain);
		smap = mix(smap, 1.0, smoothstep(lmb * uv1.y, 1.0, uv1.x));
	vec3 almap = mix(mix(vec3(0.3, 0.55, 1.0), vec3(0.0), night), vec3(0.5), rain * (1.0 - night)) * uv1.y;
		almap += lsc;
	vec3 ambc = mix(mix(vec3(1.1, 1.1, 0.8), vec3(1.0, 0.5, 0.0), dusk), vec3(0.05, 0.15, 0.4), night) * smap;
		almap += ambc;
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
	diffuse.rgb *= (inColor.r != inColor.g && inColor.g != inColor.b) ? normalize(inColor.rgb) * length(sqrt(inColor.rgb)) : sqrt(inColor.rgb) * 1.2;
#else
	diffuse.rgb *= mix(vec3(1.0), texture2D(TEXTURE_2, inColor.xy).rgb * 2.0, inColor.b);
	diffuse.rgb *= inColor.aaa;
	diffuse.a = 1.0;
#endif
	diffuse.rgb = tl(diffuse.rgb);

	bool waterd = false;
#if !defined(SEASONS) && !defined(ALPHA_TEST)
	waterd = inColor.a < 0.7 && inColor.a > 0.5;
#endif

	vec3 n = normalize(cross(dFdx(cpos.xyz), dFdy(cpos.xyz)));
	float lmb = texture2D(TEXTURE_1, vec2(0, 1)).r;
	float bls = max(uv1.x * smoothstep(lmb * uv1.y, 1.0, uv1.x), uv1.x * rain * uv1.y);
	vec3 lsc = vec3(1.0, 0.35, 0.0) * bls + sqr5(bls);
	diffuse.rgb = illum(diffuse.rgb, n, lsc, lmb);
	if(waterd){
		n = calcnw(n);
		diffuse = reflection(diffuse, n, lsc);
	}
	if(FOG_CONTROL.x == 0.0){
		hp float caus = cwav(cpos.xz);
		if(!waterd) diffuse.rgb = vec3(0.3, 0.6, 1.0) * diffuse.rgb + diffuse.rgb * max0(caus) * uv1.y;
		diffuse.rgb += diffuse.rgb * (uv1.x * uv1.x) * (1.0 - uv1.y);
	}
	vec3 newfc = sr(normalize(wpos));
	diffuse.rgb = mix(diffuse.rgb, newfc, max0(length(wpos) * (0.001 + 0.005 * rain)));
#ifdef FOG
	diffuse.rgb = mix(diffuse.rgb, newfc, sqr5(fogr));
#endif
	diffuse.rgb = colcor(diffuse.rgb);

	gl_FragColor = diffuse;
#endif
}
