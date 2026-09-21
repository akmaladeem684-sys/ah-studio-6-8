package com.example

import android.app.Application
import android.util.Log
import com.example.domain.StudioAccountManager

class StudioApplication : Application() {
  companion object {
    lateinit var instance: StudioApplication
      private set
  }

  override fun onCreate() {
    super.onCreate()
    instance = this

    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("StudioApplication", "Uncaught exception on thread: ${thread.name}", throwable)
      defaultHandler?.uncaughtException(thread, throwable)
    }

    // Keep the native GPU library lazy. Loading libah_engine.so during Application
    // startup can occur before an EGL/GL context exists and can make the launcher
    // path fail on devices with incompatible native GPU/runtime capabilities.
    // NativeRenderBridge loads it only when the renderer has a real GL context.
    try {
      StudioAccountManager.init(this)
    } catch (t: Throwable) {
      Log.w("StudioApplication", "StudioAccountManager init skipped/failed", t)
    }
  }
}
