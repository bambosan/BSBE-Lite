// __multiversion__
#include "vertexVersionCentroid.h"
#include "uniformWorldConstants.h"
#include "uniformPerFrameConstants.h"
#include "uniformShaderConstants.h"
#include "uniformRenderChunkConstants.h"

#if __VERSION__ >= 300
	#ifndef BYPASS_PIXEL_SHADER
		_centroid out vec2 uv0;
		_centroid out vec2 uv1;
	#endif
#else
	#ifndef BYPASS_PIXEL_SHADER
		out vec2 uv0;
		out vec2 uv1;
	#endif
#endif

#ifdef FOG
	varying float fogr;
#endif

#ifndef BYPASS_PIXEL_SHADER
	varying vec4 color;
	varying vec3 cpos;
	varying	vec3 wpos;
#endif

attribute POS4 POSITION;
attribute vec4 COLOR;
attribute vec2 TEXCOORD_0;
attribute vec2 TEXCOORD_1;

const float rA = 1.0;
const float rB = 1.0;
const vec3 UNIT_Y = vec3(0,1,0);
const float DIST_DESATURATION = 56.0 / 255.0; //WARNING this value is also hardcoded in the water color, don'tchange

#include "common.glsl"

void main(){
	POS4 worldPos;
#ifdef AS_ENTITY_RENDERER
	POS4 pos = WORLDVIEWPROJ * POSITION;
	worldPos = pos;
#else
	worldPos.xyz = (POSITION.xyz * CHUNK_ORIGIN_AND_SCALE.w) + CHUNK_ORIGIN_AND_SCALE.xyz;
	// some stuff in here https://github.com/McbeEringi/esbe-3g/tree/master/ESBE_3G/shaders/glsl
	POS3 ajp = fract(POSITION.xyz * 0.0625) * 16.0, frp = fract(POSITION.xyz);
	highp float gwave = sin(TOTAL_REAL_WORLD_TIME * 4.0 + ajp.x + ajp.z + ajp.y);

	#if !defined(SEASONS) && !defined(ALPHA_TEST)
		if(COLOR.a < 0.7 && COLOR.a > 0.5) worldPos.y += gwave * 0.05 * fract(POSITION.y);
	#endif
	#ifdef ALPHA_TEST
		if((COLOR.r != COLOR.g && COLOR.g != COLOR.b && frp.y != 0.015625) || (frp.y == 0.9375 && (frp.x == 0.0 || frp.z == 0.0))) worldPos.xyz += gwave * 0.03 * (1.-saturate(length(worldPos.xyz) / FAR_CHUNKS_DISTANCE)) * TEXCOORD_1.y;
	#endif
	if(FOG_CONTROL.x == 0.0) worldPos.xyz += gwave * 0.05;
	POS4 pos = WORLDVIEW * vec4(worldPos.xyz, 1.0);
	pos = PROJ * pos;
#endif

	gl_Position = pos;

#ifndef BYPASS_PIXEL_SHADER
	uv0 = TEXCOORD_0;
	uv1 = TEXCOORD_1;
	color = COLOR;
	cpos = POSITION.xyz;
	wpos = worldPos.xyz;
#endif

#ifdef FOG
	float len = length(-worldPos.xyz) / RENDER_DISTANCE;
	#ifdef ALLOW_FADE
		len += RENDER_CHUNK_FOG_ALPHA;
	#endif
	fogr = saturate((len - FOG_CONTROL.x) / (FOG_CONTROL.y - FOG_CONTROL.x));
#endif
}
