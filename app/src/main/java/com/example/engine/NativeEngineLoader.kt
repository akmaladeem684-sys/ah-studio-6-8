package com.example.engine

import android.util.Log

/**
 * Lazy native engine loader.
 *
 * The library is intentionally NOT loaded from object initialization/Application.onCreate.
 * GPU/native initialization must happen from the renderer after a valid EGL context exists.
 */
object NativeEngineLoader {
  @Volatile private var isLoaded = false
  @Volatile private var isInitialized = false

  @Synchronized
  fun loadLibrary(): Boolean {
    if (isLoaded) return true
    return try {
      System.loadLibrary("ah_engine")
      isLoaded = true
      Log.i("NativeEngineLoader", "Successfully loaded libah_engine.so")
      // nativeInit() is only a compatibility probe and does not touch GL state.
      // Keep it guarded; real GPU initialization is renderer/context scoped.
      isInitialized = runCatching { nativeInit() }.getOrDefault(false)
      true
    } catch (e: LinkageError) {
      Log.e("NativeEngineLoader", "Native engine unavailable; continuing with GPU fallback", e)
      false
    } catch (e: Throwable) {
      Log.e("NativeEngineLoader", "Unexpected native loader failure; continuing with fallback", e)
      false
    }
  }

  fun isEngineLoaded(): Boolean = isLoaded
  fun isEngineInitialized(): Boolean = isInitialized

  private external fun nativeInit(): Boolean
}
