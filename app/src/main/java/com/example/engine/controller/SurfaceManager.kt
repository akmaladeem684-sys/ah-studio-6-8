package com.example.engine.controller

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView

/**
 * Centralizes SurfaceView and SurfaceTexture lifecycle management.
 * Handles surface creation, changes, and destruction while preserving the underlying
 * player state across UI recompositions and tool panel transitions.
 */
class SurfaceManager(
  private val playbackManager: PlaybackManager
) {

  companion object {
    private const val TAG = "SurfaceManager"
  }

  private var activeSurface: Surface? = null
  private var isSurfaceAvailable: Boolean = false
  private var lastValidFrame: Bitmap? = null

  val isAvailable: Boolean get() = isSurfaceAvailable && (activeSurface?.isValid == true)

  var onSurfaceAvailabilityChanged: ((Boolean) -> Unit)? = null

  /**
   * Attaches a SurfaceView with automatic lifecycle callback wiring.
   */
  fun attachSurfaceView(surfaceView: SurfaceView) {
    surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {
        handleSurfaceCreated(holder.surface)
      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        handleSurfaceChanged(holder.surface, width, height)
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
        handleSurfaceDestroyed(holder.surface)
      }
    })

    if (surfaceView.holder.surface.isValid) {
      handleSurfaceCreated(surfaceView.holder.surface)
    }
  }

  /**
   * Attaches a TextureView with automatic SurfaceTextureListener wiring.
   */
  fun attachTextureView(textureView: TextureView) {
    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(surfaceTexture)
        handleSurfaceCreated(surface)
      }

      override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

      override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        activeSurface?.let { handleSurfaceDestroyed(it) }
        return true
      }

      override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
    }

    if (textureView.isAvailable && textureView.surfaceTexture != null) {
      val surface = Surface(textureView.surfaceTexture)
      handleSurfaceCreated(surface)
    }
  }

  fun handleSurfaceCreated(surface: Surface) {
    if (!surface.isValid) {
      Log.w(TAG, "surfaceCreated called with invalid surface")
      return
    }
    activeSurface = surface
    isSurfaceAvailable = true
    playbackManager.setSurface(surface)
    onSurfaceAvailabilityChanged?.invoke(true)
    Log.d(TAG, "Surface successfully attached to active player")
  }

  fun handleSurfaceChanged(surface: Surface, width: Int, height: Int) {
    if (surface.isValid && surface != activeSurface) {
      activeSurface = surface
      isSurfaceAvailable = true
      playbackManager.setSurface(surface)
    }
  }

  fun handleSurfaceDestroyed(surface: Surface) {
    if (activeSurface == surface || !surface.isValid) {
      isSurfaceAvailable = false
      playbackManager.clearSurface()
      activeSurface = null
      onSurfaceAvailabilityChanged?.invoke(false)
      Log.d(TAG, "Surface destroyed and detached cleanly; player state preserved")
    }
  }

  fun storeLastValidFrame(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) {
      lastValidFrame = bitmap
    }
  }

  fun getLastValidFrame(): Bitmap? = lastValidFrame

  fun release() {
    isSurfaceAvailable = false
    activeSurface = null
    lastValidFrame = null
  }
}
