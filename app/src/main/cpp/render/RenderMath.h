#ifndef AH_RENDER_MATH_H
#define AH_RENDER_MATH_H
#include <cmath>
#include <cstring>
namespace ah_engine::math { inline bool allFinite(const float*p,int n){for(int i=0;i<n;i++)if(!std::isfinite(p[i]))return false;return true;} inline void identity(float*m){std::memset(m,0,16*sizeof(float));m[0]=m[5]=m[10]=m[15]=1.f;} }
#endif