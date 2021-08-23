// __multiversion__
#include "vertexVersionSimple.h"
#include "uniformWorldConstants.h"

attribute POS4 POSITION;
varying vec3 pos;

void main(){
	pos = POSITION.xyz;
	gl_Position = WORLDVIEWPROJ * (POSITION * vec4(5, 0, 5, 1));
}
