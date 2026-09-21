#ifndef AH_FX_ENGINE_H
#define AH_FX_ENGINE_H
#include <GLES3/gl3.h>
#include <unordered_map>
#include <vector>
#include "ChainPacking.h"
namespace ah_fx {
class EffectEngine {
 public:
  EffectEngine()=default; ~EffectEngine(){release();}
  bool init(); bool isInitialized() const{return initialized_;} int failedShaderCount() const{return failed_;}
  int render(GLuint inputTex,int width,int height,float timeMs,const std::vector<Instance>& chain,GLuint outputFbo);
  void onContextLost(); void release();
 private:
  struct Prog{GLuint id=0;GLint uTex=-1,uSrc=-1,uRes=-1,uTime=-1,uInt=-1,uP=-1,uDir=-1;};
  struct Target{GLuint fbo=0,tex=0;};
  Prog* program(int key); bool ensureTargets(int w,int h); void destroyTargets();
  void pass(int key,GLuint tex,GLuint src,GLuint dstFbo,int w,int h,float timeSec,float intensity,const float*p,float dx,float dy);
  void runEffect(const Resolved&e,GLuint cur,GLuint dstFbo,int w,int h,float timeSec);
  bool initialized_=false; int failed_=0; GLuint vao_=0; std::unordered_map<int,Prog> progs_;
  std::unordered_map<int,bool> failedKeys_; Target t_[4]; int tw_=0,th_=0;
};
}
#endif