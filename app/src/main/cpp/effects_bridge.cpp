#include <jni.h>
#include <EGL/egl.h>
#include <memory>
#include <mutex>
#include <vector>
#include "render/EngineRegistry.h"
#include "effects/EffectEngine.h"
static std::mutex gFxMutex; static ah_engine::EngineRegistry<ah_fx::EffectEngine> gFx;
static const void* fxContext(){EGLContext c=eglGetCurrentContext();return c==EGL_NO_CONTEXT?nullptr:static_cast<const void*>(c);}
static ah_fx::EffectEngine* fxReady(){const void*ctx=fxContext();if(!ctx)return nullptr;auto*e=gFx.find(ctx);return(e&&e->isInitialized())?e:nullptr;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_engine_effects_NativeEffectsBridge_nativeInit(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gFxMutex);const void*ctx=fxContext();if(!ctx)return JNI_FALSE;auto*e=gFx.findOrCreate(ctx);if(!e->isInitialized()&&!e->init()){gFx.take(ctx);return JNI_FALSE;}return JNI_TRUE;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_engine_effects_NativeEffectsBridge_nativeIsReady(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gFxMutex);return fxReady()?JNI_TRUE:JNI_FALSE;}
extern "C" JNIEXPORT jint JNICALL Java_com_example_engine_effects_NativeEffectsBridge_nativeRender(JNIEnv*env,jobject,jint inputTex,jint outputFbo,jint width,jint height,jfloat timeMs,jfloatArray chain,jint chainLen){std::lock_guard<std::mutex>l(gFxMutex);auto*e=fxReady();if(!e||inputTex<=0||outputFbo<0||!chain||chainLen<2||env->GetArrayLength(chain)<chainLen)return -1;jfloat*p=env->GetFloatArrayElements(chain,nullptr);if(!p)return -1;std::vector<ah_fx::Instance>instances;bool ok=ah_fx::parseChain(p,(size_t)chainLen,instances);env->ReleaseFloatArrayElements(chain,p,JNI_ABORT);if(!ok)return -1;return e->render((GLuint)inputTex,width,height,timeMs,instances,(GLuint)outputFbo);}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_effects_NativeEffectsBridge_nativeOnContextLost(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gFxMutex);const void*ctx=fxContext();if(!ctx)return;if(auto e=gFx.take(ctx))e->onContextLost();}
extern "C" JNIEXPORT void JNICALL Java_com_example_engine_effects_NativeEffectsBridge_nativeRelease(JNIEnv*,jobject){std::lock_guard<std::mutex>l(gFxMutex);const void*ctx=fxContext();if(!ctx)return;if(auto e=gFx.take(ctx))e->release();}
