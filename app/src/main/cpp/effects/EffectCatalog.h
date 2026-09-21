#ifndef AH_FX_CATALOG_H
#define AH_FX_CATALOG_H
#include <cmath>
namespace ah_fx {
enum EffectId:int{FX_NONE=0,FX_GAUSSIAN_BLUR=1,FX_DIRECTIONAL_BLUR=2,FX_ZOOM_BLUR=3,FX_BLOOM=4,FX_VIGNETTE=5,FX_COLOR_GRADE=6,FX_HUE_SHIFT=7,FX_DUOTONE=8,FX_POSTERIZE=9,FX_INVERT=10,FX_SEPIA=11,FX_WAVE=12,FX_RIPPLE=13,FX_SWIRL=14,FX_BULGE=15,FX_MIRROR=16,FX_KALEIDOSCOPE=17,FX_PIXELATE=18,FX_RGB_SPLIT=19,FX_GLITCH=20,FX_VHS=21,FX_CRT=22,FX_FILM_GRAIN=23,FX_SHAKE=24,FX_ZOOM_PULSE=25,FX_ROTATE=26,FX_EDGE_DETECT=27,FX_SHARPEN=28,FX_CHROMA_KEY=29,FX_TRANSFORM_3D=30,FX_FLASH=31,FX_HALFTONE=32,FX_LIGHT_LEAK=33,FX_LENS_FLARE=34,FX_COUNT=35};
constexpr int kMaxParams=8;
struct ParamSpec{const char*name;float def,lo,hi;}; struct EffectSpec{int id;const char*name;int paramCount;ParamSpec params[kMaxParams];bool preservesLayout;};
#define P_(n,d,l,h){n,d,l,h}
inline const EffectSpec* catalog(){static const EffectSpec t[FX_COUNT]={
{0,"none",0,{},true},{1,"gaussian_blur",1,{P_("radius",8,0,32)},true},{2,"directional_blur",2,{P_("length",.04f,0,.3f),P_("angle",0,-180,180)},true},
{3,"zoom_blur",3,{P_("strength",.15f,0,.6f),P_("cx",.5f,0,1),P_("cy",.5f,0,1)},true},{4,"bloom",3,{P_("threshold",.6f,0,1),P_("radius",12,1,32),P_("strength",1.2f,0,4)},true},
{5,"vignette",3,{P_("amount",.8f,0,1),P_("softness",.6f,.01f,1),P_("roundness",1,0,1)},true},{6,"color_grade",7,{P_("exposure",0,-3,3),P_("contrast",1.25f,0,3),P_("saturation",1.3f,0,3),P_("temperature",.2f,-1,1),P_("tint",0,-1,1),P_("gamma",1,.2f,3),P_("fade",0,0,1)},true},
{7,"hue_shift",1,{P_("degrees",90,-180,180)},false},{8,"duotone",6,{P_("shadow_r",.05f,0,1),P_("shadow_g",0,0,1),P_("shadow_b",.25f,0,1),P_("high_r",1,0,1),P_("high_g",.75f,0,1),P_("high_b",.3f,0,1)},false},
{9,"posterize",1,{P_("levels",4,2,32)},true},{10,"invert",0,{},false},{11,"sepia",1,{P_("tone",1,0,1)},false},{12,"wave",4,{P_("amplitude",.02f,0,.1f),P_("frequency",6,.5f,40),P_("speed",4,-20,20),P_("vertical",.5f,0,1)},true},
{13,"ripple",5,{P_("amplitude",.02f,0,.1f),P_("frequency",40,1,120),P_("speed",8,-30,30),P_("cx",.5f,0,1),P_("cy",.5f,0,1)},true},
{14,"swirl",4,{P_("angle",2,-8,8),P_("radius",.6f,.05f,1.5f),P_("cx",.5f,0,1),P_("cy",.5f,0,1)},false},
{15,"bulge",4,{P_("strength",.6f,-1,1),P_("radius",.6f,.05f,1.5f),P_("cx",.5f,0,1),P_("cy",.5f,0,1)},false},
{16,"mirror",1,{P_("mode",0,0,4)},false},{17,"kaleidoscope",3,{P_("segments",6,2,16),P_("rotation",0,-180,180),P_("zoom",1,.2f,4)},false},
{18,"pixelate",1,{P_("size",12,1,128)},true},{19,"rgb_split",2,{P_("amount",.015f,0,.1f),P_("angle",0,-180,180)},true},
{20,"glitch",3,{P_("strength",1,0,2),P_("slices",24,2,128),P_("speed",12,0,60)},true},{21,"vhs",3,{P_("wobble",.004f,0,.05f),P_("chroma",.006f,0,.05f),P_("noise",.15f,0,1)},true},
{22,"crt",3,{P_("scanlines",.35f,0,1),P_("curvature",.15f,0,.6f),P_("mask",.3f,0,1)},false},{23,"film_grain",2,{P_("amount",.2f,0,1),P_("size",1.5f,1,8)},true},
{24,"shake",3,{P_("amplitude",.012f,0,.1f),P_("speed",30,0,100),P_("rotation",.8f,0,10)},true},{25,"zoom_pulse",4,{P_("amount",.2f,0,1),P_("speed",5,0,30),P_("cx",.5f,0,1),P_("cy",.5f,0,1)},true},
{26,"rotate",3,{P_("angle",15,-360,360),P_("zoom",1,.1f,4),P_("spin",0,-720,720)},false},{27,"edge_detect",2,{P_("strength",2,0,8),P_("keep_image",0,0,1)},false},
{28,"sharpen",2,{P_("amount",1.5f,0,5),P_("radius",1,.5f,4)},true},{29,"chroma_key",6,{P_("key_r",0,0,1),P_("key_g",1,0,1),P_("key_b",0,0,1),P_("similarity",.35f,0,1.7f),P_("smoothness",.1f,0,1),P_("spill",.6f,0,1)},false},
{30,"transform_3d",7,{P_("rot_x",0,-180,180),P_("rot_y",25,-180,180),P_("rot_z",0,-180,180),P_("distance",3,1.2f,12),P_("scale",1,.1f,4),P_("pos_x",0,-2,2),P_("pos_y",0,-2,2)},false},
{31,"flash",2,{P_("strength",1,0,1),P_("rate",3,.1f,30)},true},{32,"halftone",1,{P_("size",10,3,64)},false},{33,"light_leak",2,{P_("speed",1,0,10),P_("strength",.9f,0,2)},false},{34,"lens_flare",3,{P_("x",.3f,0,1),P_("y",.7f,0,1),P_("strength",1,0,3)},false}};
return t;}
#undef P_
inline bool validEffectId(int id){return id>0&&id<FX_COUNT;}
inline const EffectSpec* specFor(int id){return id>=0&&id<FX_COUNT?&catalog()[id]:nullptr;}
inline float sanitizeParam(int id,int index,float v){const auto*s=specFor(id);if(!s||index<0||index>=s->paramCount)return 0;const auto&p=s->params[index];if(!std::isfinite(v))return p.def;return v<p.lo?p.lo:(v>p.hi?p.hi:v);}
}
#endif