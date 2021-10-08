// __multiversion__
#include "fragmentVersionSimple.h"
#include "uniformPerFrameConstants.h"
#include "common.glsl"

varying hp vec3 pos;

void main(){
	hp vec3 ajp = normalize(vec3(pos.x, -pos.y + 0.128, -pos.z));
	hp vec3 dpos = ajp / ajp.y;
	hp float zen = max0(ajp.y), cm = cmap(dpos.xz);

	vec4 color = vec4(sr(ajp), sqr5(1.0 - zen));
		color = mix(color, vec4(ccc(), 1.0), cm * smoothstep(1.0, 0.95, length(ajp.xz)) * float(zen > 0.0));
		color.rgb = colcor(color.rgb);
	gl_FragColor = color;
}
