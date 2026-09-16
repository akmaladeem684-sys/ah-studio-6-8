package com.example.engine.composition.gpu

import android.util.Log

enum class NativeBlendMode(val id: Int) {
  NORMAL(0),
  ADDITIVE(1),
  MULTIPLY(2),
  SCREEN(3),
  PREMULTIPLIED(4)
}

enum class NativeLayerType(val id: Int) {
  BASE_VIDEO(0),
  VIDEO(1),
  IMAGE_STICKER(2),
  EFFECT_OVERLAY(3),
  TEXT(4)
}

data class NativeLayer(
  val id: Long = 0L,
  val textureId: Int = 0,
  val type: NativeLayerType = NativeLayerType.BASE_VIDEO,
  val isVisible: Boolean = true,
  val zOrder: Int = 0,
  val posX: Float = 0.0f,
  val posY: Float = 0.0f,
  val scaleX: Float = 1.0f,
  val scaleY: Float = 1.0f,
  val rotation: Float = 0.0f,
  val width: Float = 1.0f,
  val height: Float = 1.0f,
  val opacity: Float = 1.0f,
  val uOffset: Float = 0.0f,
  val vOffset: Float = 0.0f,
  val uScale: Float = 1.0f,
  val vScale: Float = 1.0f,
  val blendMode: NativeBlendMode = NativeBlendMode.NORMAL,
  val useCustomMatrix: Boolean = false,
  val transformMatrix: FloatArray? = null
)

object NativeRenderBridge {
  private const val TAG = "NativeRenderBridge"
  private const val LAYER_STRIDE = 35

  private var isLibraryLoaded = false
  private var isInitialized = false

  // Reusable FloatArray buffer to avoid allocation during render loop
  private var bufferCapacityLayers = 64
  private var renderBuffer = FloatArray(bufferCapacityLayers * LAYER_STRIDE)

  init {
    loadLibrary()
  }

  @Synchronized
  fun loadLibrary(): Boolean {
    if (isLibraryLoaded) return true
    return try {
      System.loadLibrary("ah_engine")
      isLibraryLoaded = true
      Log.i(TAG, "libah_engine.so successfully loaded into NativeRenderBridge")
      true
    } catch (e: UnsatisfiedLinkError) {
      Log.e(TAG, "Failed to load libah_engine.so in NativeRenderBridge", e)
      false
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error loading libah_engine.so", e)
      false
    }
  }

  @Synchronized
  fun init(width: Int, height: Int): Boolean {
    if (!isLibraryLoaded && !loadLibrary()) return false
    return try {
      isInitialized = nativeInit(width, height)
      Log.i(TAG, "NativeRenderBridge initialized (${width}x${height}), result=$isInitialized")
      isInitialized
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeInit", e)
      false
    }
  }

  @Synchronized
  fun resize(width: Int, height: Int) {
    if (!isInitialized) return
    try {
      nativeResize(width, height)
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeResize", e)
    }
  }

  @Synchronized
  fun renderFrame(layers: List<NativeLayer>) {
    if (!isInitialized || layers.isEmpty()) return

    val layerCount = layers.size
    ensureBufferCapacity(layerCount)

    var offset = 0
    for (i in 0 until layerCount) {
      val layer = layers[i]
      renderBuffer[offset + 0] = layer.id.toFloat()
      renderBuffer[offset + 1] = layer.textureId.toFloat()
      renderBuffer[offset + 2] = layer.type.id.toFloat()
      renderBuffer[offset + 3] = if (layer.isVisible) 1.0f else 0.0f
      renderBuffer[offset + 4] = layer.zOrder.toFloat()

      renderBuffer[offset + 5] = layer.posX
      renderBuffer[offset + 6] = layer.posY
      renderBuffer[offset + 7] = layer.scaleX
      renderBuffer[offset + 8] = layer.scaleY
      renderBuffer[offset + 9] = layer.rotation
      renderBuffer[offset + 10] = layer.width
      renderBuffer[offset + 11] = layer.height
      renderBuffer[offset + 12] = layer.opacity

      renderBuffer[offset + 13] = layer.uOffset
      renderBuffer[offset + 14] = layer.vOffset
      renderBuffer[offset + 15] = layer.uScale
      renderBuffer[offset + 16] = layer.vScale

      renderBuffer[offset + 17] = layer.blendMode.id.toFloat()
      renderBuffer[offset + 18] = if (layer.useCustomMatrix && layer.transformMatrix != null) 1.0f else 0.0f

      if (layer.useCustomMatrix && layer.transformMatrix != null && layer.transformMatrix.size >= 16) {
        System.arraycopy(layer.transformMatrix, 0, renderBuffer, offset + 19, 16)
      }

      offset += LAYER_STRIDE
    }

    try {
      nativeRenderFrame(renderBuffer, layerCount)
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeRenderFrame", e)
    }
  }

  @Synchronized
  fun beginOffscreen() {
    if (!isInitialized) return
    try {
      nativeBeginOffscreen()
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeBeginOffscreen", e)
    }
  }

  @Synchronized
  fun endOffscreen(): Int {
    if (!isInitialized) return 0
    return try {
      nativeEndOffscreen()
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeEndOffscreen", e)
      0
    }
  }

  @Synchronized
  fun onContextLost() {
    try {
      nativeOnContextLost()
      isInitialized = false
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeOnContextLost", e)
    }
  }

  @Synchronized
  fun release() {
    try {
      if (isInitialized) {
        nativeRelease()
        isInitialized = false
        Log.i(TAG, "NativeRenderBridge released")
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Error in nativeRelease", e)
    }
  }

  private fun ensureBufferCapacity(requiredLayers: Int) {
    if (requiredLayers > bufferCapacityLayers) {
      bufferCapacityLayers = requiredLayers + 32
      renderBuffer = FloatArray(bufferCapacityLayers * LAYER_STRIDE)
    }
  }

  // JNI External Declarations
  private external fun nativeInit(width: Int, height: Int): Boolean
  private external fun nativeResize(width: Int, height: Int)
  private external fun nativeRenderFrame(layerData: FloatArray, layerCount: Int)
  private external fun nativeBeginOffscreen()
  private external fun nativeEndOffscreen(): Int
  private external fun nativeOnContextLost()
  private external fun nativeRelease()
}
