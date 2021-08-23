// __multiversion__
#include "fragmentVersionCentroid.h"
#include "uniformShaderConstants.h"
#include "uniformPerFrameConstants.h"
#include "common.glsl"

varying hp vec3 pos;

void main(){
	vec3 color = mix(mix(vec3(1.0, 0.65, 0.2), vec3(0.8, 1.0, 1.2), nfog), FOG_COLOR.rgb, rain);
		color = tl(color);
	vec3 shape = color * smoothstep(0.8, 0.9, 1.0 - length(pos.xz * 16.0));
		shape += exp(-length(pos.xz * 30.0)) * color * 0.3;
		shape = colcor(shape);
	gl_FragColor = vec4(shape, 1.0) * CURRENT_COLOR;
}
