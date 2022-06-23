// __multiversion__
#include "fragmentVersionSimple.h"
#include "uniformPerFrameConstants.h"
#include "common.glsl"

varying highp vec3 pos;

void main(){
	highp vec3 ajp = normalize(vec3(pos.x, -pos.y + 0.128, -pos.z));
	highp vec3 dpos = ajp / ajp.y;
	highp float cm = cmap(dpos.xz);
	vec4 color = vec4(sr(ajp), exp(-saturate(ajp.y) * 5.0));
	color = mix(color, vec4(ccc(), cm), cm * smoothstep(1.0, 0.95, length(ajp.xz)) * step(0.0, ajp.y));
	color.rgb = colcor(color.rgb);
	gl_FragColor = color;
}
