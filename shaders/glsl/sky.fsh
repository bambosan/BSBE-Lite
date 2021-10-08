// __multiversion__
#include "fragmentVersionSimple.h"
#include "uniformPerFrameConstants.h"
#include "common.glsl"

varying hp float skyh;

void main(){
	vec3 skyc = csc((skyh * skyh) * 4.0);
		skyc = colcor(skyc);
	gl_FragColor = vec4(skyc, 1.0);
}
