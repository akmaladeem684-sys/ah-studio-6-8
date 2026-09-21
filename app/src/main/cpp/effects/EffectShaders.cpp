#include "EffectShaders.h"
#include <string>
#include <unordered_map>
#include <mutex>
namespace ah_fx {
static const char* V=R"(#version 300 es
out vec2 vUv;
void main(){vec2 p=vec2(float(gl_VertexID&1),float((gl_VertexID>>1)&1));vUv=p;gl_Position=vec4(p*2.0-1.0,0,1);}
)";
static const char* H=R"(#version 300 es
precision highp float;in vec2 vUv;out vec4 fragColor;uniform sampler2D uTex,uSrc;uniform vec2 uRes,uDir;uniform float uTime,uIntensity,uP[8];
float l(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}float h(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}vec4 T(vec2 u){return texture(uTex,clamp(u,0.,1.));}mat2 R(float a){float c=cos(a),s=sin(a);return mat2(c,s,-s,c);}
vec4 fx(vec2 u);
void main(){vec4 s=texture(uSrc,vUv),e=fx(vUv);fragColor=mix(s,e,clamp(uIntensity,0.,1.));}
)";
static const char* body(int k){
switch(k){
case 0:return "vec4 fx(vec2 u){return T(u);}";
case 1:return "vec4 fx(vec2 u){float r=uP[0]/uRes.x;vec4 c=vec4(0);for(int i=-8;i<=8;i++)c+=T(u+vec2(float(i)*r,0.))*exp(-float(i*i)/8.);return c/5.0;}";
case 2:return "vec4 fx(vec2 u){float a=radians(uP[1]);vec2 d=vec2(cos(a),sin(a))*uP[0];vec4 c=vec4(0);for(int i=0;i<12;i++)c+=T(u+d*(float(i)/11.-.5));return c/12.;}";
case 3:return "vec4 fx(vec2 u){vec2 c=vec2(uP[1],uP[2]);vec2 v=u-c;vec4 s=vec4(0);for(int i=0;i<12;i++)s+=T(c+v*(1.-uP[0]*float(i)/11.));return s/12.;}";
case 4:return "vec4 fx(vec2 u){vec4 c=T(u);float b=max(l(c.rgb)-uP[0],0.)*uP[2];return c+vec4(b*vec3(1.1,1.0,.8),0);}";
case 5:return "vec4 fx(vec2 u){vec4 c=T(u);float d=length((u-.5)*vec2(1.,uRes.y/uRes.x));return vec4(c.rgb*(1.-smoothstep(.35,1.0,d)*uP[0]),c.a);}";
case 6:return "vec4 fx(vec2 u){vec4 c=T(u);c.rgb*=exp2(uP[0]);c.rgb=(c.rgb-.5)*uP[1]+.5;float y=l(c.rgb);c.rgb=mix(vec3(y),c.rgb,uP[2]);c.rgb+=vec3(uP[3]*.1,0.,-uP[3]*.1);return vec4(clamp(c.rgb,0.,1.),c.a);}";
case 7:return "vec4 fx(vec2 u){vec4 c=T(u);float y=l(c.rgb);float a=radians(uP[0]);return vec4(clamp(y+vec3(cos(a),sin(a),-sin(a))*.3,0.,1.),c.a);}";
case 8:return "vec4 fx(vec2 u){vec4 c=T(u);float y=l(c.rgb);return vec4(mix(vec3(uP[0],uP[1],uP[2]),vec3(uP[3],uP[4],uP[5]),y),c.a);}";
case 9:return "vec4 fx(vec2 u){vec4 c=T(u);float n=max(uP[0]-1.,1.);return vec4(floor(c.rgb*n+.5)/n,c.a);}";
case 10:return "vec4 fx(vec2 u){vec4 c=T(u);return vec4(1.-c.rgb,c.a);}";
case 11:return "vec4 fx(vec2 u){vec4 c=T(u);vec3 s=vec3(dot(c.rgb,vec3(.393,.769,.189)),dot(c.rgb,vec3(.349,.686,.168)),dot(c.rgb,vec3(.272,.534,.131)));return vec4(mix(c.rgb,s,uP[0]),c.a);}";
case 12:return "vec4 fx(vec2 u){return T(u+vec2(sin(u.y*uP[1]+uTime*uP[2]),cos(u.x*uP[1]+uTime*uP[2]))*uP[0]);}";
case 13:return "vec4 fx(vec2 u){vec2 c=vec2(uP[3],uP[4]),p=u-c;float d=length(p);return T(u+normalize(p+.00001)*sin(d*uP[1]-uTime*uP[2])*uP[0]);}";
case 14:return "vec4 fx(vec2 u){vec2 c=vec2(uP[2],uP[3]),p=u-c;float f=max(0.,1.-length(p)/uP[1]);return T(R(uP[0]*f*f)*p+c);}";
case 15:return "vec4 fx(vec2 u){vec2 c=vec2(uP[2],uP[3]),p=u-c;float d=length(p)/uP[1];if(d<1.)p*=1.-uP[0]*(1.-d*d);return T(p+c);}";
case 16:return "vec4 fx(vec2 u){int m=int(uP[0]+.5);if(m==0&&u.x>.5)u.x=1.-u.x;if(m==1&&u.x<.5)u.x=1.-u.x;if(m==2&&u.y<.5)u.y=1.-u.y;if(m==3&&u.y>.5)u.y=1.-u.y;return T(u);}";
case 17:return "vec4 fx(vec2 u){vec2 p=u-.5;float a=atan(p.y,p.x)+radians(uP[1]);float seg=6.283/uP[0];a=abs(mod(a,seg)-seg*.5);return T(vec2(cos(a),sin(a))*length(p)/uP[2]+.5);}";
case 18:return "vec4 fx(vec2 u){vec2 c=uRes/max(uP[0],1.);return T((floor(u*c)+.5)/c);}";
case 19:return "vec4 fx(vec2 u){vec2 d=vec2(uP[0]);return vec4(T(u+d).r,T(u).g,T(u-d).b,T(u).a);}";
case 20:return "vec4 fx(vec2 u){float q=floor(u.y*uP[1]),n=h(vec2(q,floor(uTime*uP[2])));float x=(n-.5)*uP[0]*.15;return vec4(T(u+vec2(x+.01,0)).r,T(u+vec2(x,0)).g,T(u+vec2(x-.01,0)).b,1);}";
case 21:return "vec4 fx(vec2 u){float w=sin(u.y*40.+uTime*8.)*uP[0];vec2 v=u+vec2(w,0);vec3 c=vec3(T(v+vec2(uP[1],0)).r,T(v).g,T(v-vec2(uP[1],0)).b);c+=(h(u*uRes+uTime)-.5)*uP[2];return vec4(clamp(c,0.,1.),1);}";
case 22:return "vec4 fx(vec2 u){vec4 c=T(u);c.rgb*=1.-uP[0]*(.5+.5*sin(u.y*uRes.y*3.14159));return c;}";
case 23:return "vec4 fx(vec2 u){vec4 c=T(u);c.rgb+=vec3(h(floor(u*uRes/uP[1])+uTime*31.)-.5)*uP[0];return c;}";
case 24:return "vec4 fx(vec2 u){float s=uTime*uP[1];vec2 o=vec2(sin(s*1.3),cos(s*1.7))*uP[0];return T(u+o);}";
case 25:return "vec4 fx(vec2 u){vec2 c=vec2(uP[2],uP[3]);float z=1.+uP[0]*(.5+.5*sin(uTime*uP[1]));return T((u-c)/z+c);}";
case 26:return "vec4 fx(vec2 u){vec2 p=R(radians(uP[0]+uP[2]*uTime))*(u-.5)/uP[1];return T(p+.5);}";
case 27:return "vec4 fx(vec2 u){vec2 p=1./uRes;float gx=l(T(u+p*vec2(1,0)).rgb)-l(T(u-p*vec2(1,0)).rgb);float gy=l(T(u+p*vec2(0,1)).rgb)-l(T(u-p*vec2(0,1)).rgb);float e=length(vec2(gx,gy))*uP[0];return vec4(vec3(e)+T(u).rgb*uP[1],1);}";
case 28:return "vec4 fx(vec2 u){vec2 p=uP[1]/uRes;vec4 c=T(u),b=(T(u+p)+T(u-p)+T(u+vec2(0,p.y))+T(u-vec2(0,p.y)))*.25;return vec4(clamp(c.rgb+(c.rgb-b.rgb)*uP[0],0.,1.),c.a);}";
case 29:return "vec4 fx(vec2 u){vec4 c=T(u);vec3 k=vec3(uP[0],uP[1],uP[2]);float a=smoothstep(uP[3],uP[3]+max(uP[4],.001),distance(c.rgb,k));return vec4(c.rgb*a,c.a*a);}";
case 30:return "vec4 fx(vec2 u){vec2 p=(u-.5);float ax=radians(uP[0]),ay=radians(uP[1]);p*=1.+.2*sin(ax)+.2*sin(ay);p/=uP[4];return T(p+.5);}";
case 31:return "vec4 fx(vec2 u){vec4 c=T(u);float f=pow(1.-fract(uTime*uP[1]),3.)*uP[0];return vec4(mix(c.rgb,vec3(1),f),c.a);}";
case 32:return "vec4 fx(vec2 u){vec2 g=u*uRes/uP[0],f=fract(g)-.5;vec3 c=T((floor(g)+.5)*uP[0]/uRes).rgb;float m=smoothstep(.4,.0,length(f));return vec4(c*m,1);}";
case 33:return "vec4 fx(vec2 u){vec4 c=T(u);float t=uTime*uP[0];vec3 q=vec3(1,.5,.2)*smoothstep(.7,0.,distance(u,vec2(.1+.1*sin(t),.15)));return vec4(c.rgb+q*uP[1],c.a);}";
case 34:return "vec4 fx(vec2 u){vec4 c=T(u);vec2 L=vec2(uP[0],uP[1]);float d=length(u-L);vec3 f=vec3(1,.9,.6)*pow(max(0.,1.-d*3.),2.);return vec4(c.rgb+f*uP[2],c.a);}";
case 200:return "vec4 fx(vec2 u){vec4 c=T(u);float k=smoothstep(uP[0]-.1,uP[0]+.1,max(max(c.r,c.g),c.b));return vec4(c.rgb*k,c.a);}";
case 201:return "vec4 fx(vec2 u){vec4 s=texture(uSrc,u);return vec4(s.rgb+T(u).rgb*uP[2],s.a);}";
default:return nullptr;}}
const char* vertexSource(){return V;}
const char* fragmentSource(int key){static std::mutex m;static std::unordered_map<int,std::string>c;std::lock_guard<std::mutex>l(m);auto i=c.find(key);if(i!=c.end())return i->second.c_str();const char*b=body(key);if(!b)return nullptr;auto&s=c[key];s=std::string(H)+b;return s.c_str();}
}