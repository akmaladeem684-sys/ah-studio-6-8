#ifndef AH_FX_KEYFRAMES_H
#define AH_FX_KEYFRAMES_H
#include <vector>
#include <algorithm>
#include <cmath>
namespace ah_fx {
enum Ease:int{EASE_LINEAR=0,EASE_IN=1,EASE_OUT=2,EASE_IN_OUT=3,EASE_HOLD=4};
struct Key{float t;float v;int ease;};
inline float applyEase(int ease,float x){x=x<0?0:(x>1?1:x);switch(ease){case EASE_IN:return x*x*x;case EASE_OUT:{float y=1-x;return 1-y*y*y;}case EASE_IN_OUT:return x<.5f?4*x*x*x:1-std::pow(-2*x+2,3)/2;default:return x;}}
inline float evalTrack(const std::vector<Key>&k,float t,float fallback){if(k.empty())return fallback;if(t<=k.front().t)return k.front().v;if(t>=k.back().t)return k.back().v;size_t i=0;while(i+1<k.size()&&k[i+1].t<=t)i++;const Key&a=k[i];const Key&b=k[i+1];if(a.ease==EASE_HOLD)return a.v;float span=b.t-a.t;float x=span>0?(t-a.t)/span:1;return a.v+(b.v-a.v)*applyEase(a.ease,x);}
}
#endif