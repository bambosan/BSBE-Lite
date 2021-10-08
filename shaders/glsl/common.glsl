#define hp highp
#define max0(x) max(0.0, x)
#define stre(x) clamp(x, 0.0, 1.0)
#define csmooth(x) x * x * (3.0 - 2.0 * x)
#define rain smoothstep(0.65, 0.2, FOG_CONTROL.x)
#define nfog stre(1.0 - FOG_COLOR.r * 1.5)
#define dfog stre(FOG_COLOR.r - smoothstep(0.3, 1.0, FOG_COLOR.b))

uniform highp float TOTAL_REAL_WORLD_TIME;

float sqr3(float x){ return x * x * x; }
float sqr4(float x){ return x * x * x * x; }
float sqr5(float x){ return x * x * x * x * x; }

float hash(highp float n){ return fract(sin(n) * 43758.5453); }
float noise(highp vec2 pos){
	highp vec2 ip = floor(pos), fp = csmooth(fract(pos));
	highp float n = ip.x + ip.y * 57.0;
	return mix(mix(hash(n), hash(n + 1.0), fp.x), mix(hash(n + 57.0), hash(n + 58.0), fp.x), fp.y);
}

float cmap(hp vec2 pos){
	float tot = 0.0, den = stre(1.0 - rain);
	pos *= 1.5;
	pos += TOTAL_REAL_WORLD_TIME * 0.001;
	for(int i = 0; i < 4; i++){
		tot += noise(pos) * den;
		den *= 0.5;
		pos *= 2.5;
		pos += tot;
		pos += TOTAL_REAL_WORLD_TIME * 0.1;
	}
	return 1.0 - pow(0.2, max0(1.0 - tot));
}

vec3 tl(vec3 col){
	return col * (col * (col * 0.305306011 + 0.682171111) + 0.012522878);
}
vec3 colcor(vec3 col){
	col = col / (col + 0.187) * 1.035;
return mix(vec3(length(col)), col, 1.1);
}

vec3 ccc(){
	vec3 cloudc = mix(mix(mix(vec3(0.8, 0.98, 1.0), vec3(0.9, 0.6, 0.3), dfog), vec3(0.2, 0.3, 0.4), nfog), FOG_COLOR.rgb, rain);
	return tl(cloudc);
}
vec3 csc(hp float skyh){
	vec3 skyc = mix(mix(mix(vec3(0.0, 0.43, 0.9), vec3(0.07, 0.16, 0.25), nfog), vec3(0.0, 0.3, 0.7), dfog), FOG_COLOR.rgb, rain);
		skyc = tl(skyc);
	vec3 scc = mix(mix(mix(vec3(0.6, 0.8, 1.0), vec3(1.0, 0.5, 0.4), dfog), vec3(0.2, 0.3, 0.4), nfog), FOG_COLOR.rgb, rain);
		scc = tl(scc);
	return mix(skyc, scc, skyh);
}
vec3 sr(hp vec3 npos){
	hp float hor = max0((sqr4(1.0 - abs(npos.y)) * 0.9 + 0.2) + sqr5(1.0 - length(npos.zy)) * 6.0 * dfog);
	return csc(hor);
}
