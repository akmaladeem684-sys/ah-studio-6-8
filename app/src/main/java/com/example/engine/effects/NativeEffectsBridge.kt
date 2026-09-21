package com.example.engine.effects

import android.util.Log
import com.example.engine.composition.gpu.NativeRenderBridge

object NativeEffectsBridge {
  private const val TAG = "NativeEffectsBridge"
  private val libraryLoaded: Boolean by lazy { NativeRenderBridge.loadLibrary() }
  fun init(): Boolean = if (!libraryLoaded) false else try { nativeInit() } catch (e: Throwable) { Log.w(TAG, "native effects unavailable", e); false }
  fun isReady(): Boolean = libraryLoaded && !NativeRenderBridge.isKotlinFallbackForced() && try { nativeIsReady() } catch (_: Throwable) { false }
  fun render(inputTex: Int, outputFbo: Int, width: Int, height: Int, timeMs: Float, chain: FloatArray): Boolean {
    if (inputTex <= 0 || chain.size < 2 || !isReady()) return false
    return try { nativeRender(inputTex, outputFbo, width, height, timeMs, chain, chain.size) >= 0 } catch (e: Throwable) { Log.e(TAG, "nativeRender failed", e); false }
  }
  fun onContextLost() { if (libraryLoaded) runCatching { nativeOnContextLost() } }
  fun release() { if (libraryLoaded) runCatching { nativeRelease() } }
  private external fun nativeInit(): Boolean
  private external fun nativeIsReady(): Boolean
  private external fun nativeRender(inputTex: Int, outputFbo: Int, width: Int, height: Int, timeMs: Float, chain: FloatArray, chainLen: Int): Int
  private external fun nativeOnContextLost()
  private external fun nativeRelease()
}