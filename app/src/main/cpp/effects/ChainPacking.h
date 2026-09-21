#ifndef AH_FX_CHAIN_PACKING_H
#define AH_FX_CHAIN_PACKING_H
#include <cstddef>
#include <vector>
#include <cmath>
#include <algorithm>
#include "EffectCatalog.h"
#include "Keyframes.h"
namespace ah_fx {
constexpr int kMaxInstances=32,kMaxTracks=9,kMaxKeys=64;
struct Track{int param=-1;std::vector<Key>keys;}; struct Instance{int id=0;float startMs=0,endMs=-1,intensity=1;float base[kMaxParams]={0};std::vector<Track>tracks;}; struct Resolved{int id=0;float startMs=0,intensity=1;float p[kMaxParams]={0};};
namespace detail{struct Reader{const float*d;size_t n,i=0;bool ok=true;bool has(size_t k)const{return ok&&i+k<=n;}float next(){if(!has(1)){ok=false;return 0;}return d[i++];}};inline int toInt(float v){return std::isfinite(v)?static_cast<int>(v):-1;}}
inline bool parseChain(const float*data,size_t len,std::vector<Instance>&out){detail::Reader r{data,len};if(!data||len<2||detail::toInt(r.next())!=1)return false;int count=detail::toInt(r.next());if(count<0||count>kMaxInstances)return false;for(int n=0;n<count;n++){Instance inst;inst.id=detail::toInt(r.next());inst.startMs=r.next();inst.endMs=r.next();inst.intensity=r.next();for(int p=0;p<kMaxParams;p++)inst.base[p]=r.next();int tracks=detail::toInt(r.next());if(!r.ok||tracks<0||tracks>kMaxTracks)return false;for(int t=0;t<tracks;t++){Track tr;tr.param=detail::toInt(r.next());int keys=detail::toInt(r.next());if(!r.ok||keys<0||keys>kMaxKeys)return false;for(int k=0;k<keys;k++){Key key{r.next(),r.next(),detail::toInt(r.next())};if(!r.ok)return false;if(std::isfinite(key.t)&&std::isfinite(key.v))tr.keys.push_back(key);}std::stable_sort(tr.keys.begin(),tr.keys.end(),[](const Key&a,const Key&b){return a.t<b.t;});if(tr.param>=-1&&tr.param<kMaxParams)inst.tracks.push_back(std::move(tr));}if(!r.ok)return false;if(validEffectId(inst.id))out.push_back(std::move(inst));}return true;}
inline bool resolveInstance(const Instance&in,float timeMs,Resolved&out){if(!std::isfinite(timeMs)||timeMs<in.startMs||(in.endMs>=0&&timeMs>=in.endMs))return false;float rel=timeMs-in.startMs;out.id=in.id;out.startMs=in.startMs;float intensity=std::isfinite(in.intensity)?in.intensity:1;for(int p=0;p<kMaxParams;p++)out.p[p]=in.base[p];for(const auto&t:in.tracks){if(t.param<0)intensity=evalTrack(t.keys,rel,intensity);else out.p[t.param]=evalTrack(t.keys,rel,out.p[t.param]);}out.intensity=std::isfinite(intensity)?std::clamp(intensity,0.f,1.f):1.f;for(int p=0;p<kMaxParams;p++)out.p[p]=sanitizeParam(in.id,p,out.p[p]);return true;}
}
#endif