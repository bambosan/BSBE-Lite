// __multiversion__
#include "vertexVersionSimple.h"
#include "uniformWorldConstants.h"

attribute mediump vec4 POSITION;
varying highp float skyh;

void main(){
    vec4 pos = POSITION;
        pos.y -= length(POSITION.xyz) * 0.2;
    gl_Position = WORLDVIEWPROJ * pos;
    skyh = length(POSITION.xz);
}
