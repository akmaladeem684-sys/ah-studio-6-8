#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <mutex>
#include <vector>
#include "render/EngineRegistry.h"
#include "render/LayerPacking.h"
#include "render/NextGenGpuCompositionEngine.h"
using ah_engine::NextGenGpuCompositionEngine;
static std::mutex gMutex; static ah_engine::EngineRegistry<NextGenGpuCompositionEngine> gEngines;
static const void* ctx(){EGLContext c=eglGetCurrentContext();return c==EGL_NO_CONTEXT?nullptr:static_cast<const void*>(c);}
static NextGenGpuCompositionEngine* ready(){const void*c=ctx();if(!c)return nullptr;auto*e=gEngines.find(c);return(e&&e->isInitialized())?e:nullptr;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeInit(JNIEnv*,jobject,jint w,jint h){std::lock_guard<std::mutex>l(gMutex);const void*c=ctx();if(!c)return JNI_FALSE;auto*e=gEngines.findOrCreate(c);if(!e->isInitialized()&&!e->init()){gEngines.take(c);return JNI_FALSE;}e->resize(w,h);return JNI_TRUE;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeIsReady(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gMutex);return ready()?JNI_TRUE:JNI_FALSE;}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeResize(JNIEnv*,jobject,jint w,jint h){std::lock_guard<std::mutex>l(gMutex);if(auto*e=ready())e->resize(w,h);}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRenderFrame(JNIEnv*env,jobject,jfloatArray data,jint count){std::lock_guard<std::mutex>l(gMutex);auto*e=ready();if(!e||!data||count<=0||env->GetArrayLength(data)<count*ah_engine::kLayerStride)return;jfloat*p=env->GetFloatArrayElements(data,nullptr);if(!p)return;std::vector<ah_engine::RenderLayer>layers;layers.reserve(count);ah_engine::unpackLayers(p,count,layers);env->ReleaseFloatArrayElements(data,p,JNI_ABORT);e->renderFrame(layers);}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRenderExternalTexture(JNIEnv*env,jobject,jint tex,jfloatArray arr){std::lock_guard<std::mutex>l(gMutex);auto*e=ready();if(!e||tex<=0)return;float m[16];const float*ptr=nullptr;if(arr&&env->GetArrayLength(arr)>=16){jfloat*p=env->GetFloatArrayElements(arr,nullptr);if(p){for(int i=0;i<16;i++)m[i]=p[i];env->ReleaseFloatArrayElements(arr,p,JNI_ABORT);ptr=m;}}e->renderExternalTexture((GLuint)tex,ptr);}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeBeginOffscreen(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gMutex);if(auto*e=ready())e->beginOffscreen();}
extern "C" JNIEXPORT jint JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeEndOffscreen(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gMutex);auto*e=ready();return e?(jint)e->endOffscreen():0;}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeOnContextLost(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gMutex);const void*c=ctx();if(!c)return;if(auto e=gEngines.take(c))e->onContextLost();}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRelease(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gMutex);const void*c=ctx();if(!c)return;if(auto e=gEngines.take(c))e->release();}
