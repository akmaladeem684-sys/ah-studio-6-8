#ifndef AH_LAYER_PACKING_H
#define AH_LAYER_PACKING_H
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>
#include "GpuRenderEngine.h"
#include "RenderMath.h"
namespace ah_engine { constexpr int kLayerStride=35; inline uint32_t floatToId(float v){return std::isfinite(v)?static_cast<uint32_t>(static_cast<int64_t>(v)):0;} inline int clampEnum(float v,int m){if(!std::isfinite(v))return 0;int i=(int)v;return i<0?0:(i>m?m:i);} inline void unpackLayers(const float*p,int count,std::vector<RenderLayer>&out){for(int i=0;i<count;i++){const float*q=p+(size_t)i*kLayerStride;if(!std::isfinite(q[1])||q[1]<1||!math::allFinite(q+4,15))continue;RenderLayer l;l.id=floatToId(q[0]);l.textureId=(GLuint)q[1];l.type=(LayerType)clampEnum(q[2],4);l.isVisible=q[3]>.5f;l.zOrder=(int)q[4];l.posX=q[5];l.posY=q[6];l.scaleX=q[7];l.scaleY=q[8];l.rotation=q[9];l.width=q[10];l.height=q[11];l.opacity=q[12];l.uOffset=q[13];l.vOffset=q[14];l.uScale=q[15];l.vScale=q[16];l.blendMode=(BlendMode)clampEnum(q[17],4);math::identity(l.transformMatrix);l.useCustomMatrix=q[18]>.5f;if(l.useCustomMatrix){if(!math::allFinite(q+19,16))continue;std::memcpy(l.transformMatrix,q+19,16*sizeof(float));}out.push_back(l);}}}
#endif