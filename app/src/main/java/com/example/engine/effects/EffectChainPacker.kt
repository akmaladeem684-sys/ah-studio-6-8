package com.example.engine.effects

data class FxKey(val timeMs: Float, val value: Float, val ease: Int = EASE_LINEAR) {
  companion object {
    const val EASE_LINEAR = 0
    const val EASE_IN = 1
    const val EASE_OUT = 2
    const val EASE_IN_OUT = 3
    const val EASE_HOLD = 4
  }
}
data class FxTrack(val param: Int, val keys: List<FxKey>)
class FxInstance(
  val id: Int, val startMs: Float = 0f, val endMs: Float = -1f, val intensity: Float = 1f,
  val params: FloatArray = FloatArray(EffectChainPacker.MAX_PARAMS) { Float.NaN },
  val tracks: List<FxTrack> = emptyList()
)
object EffectChainPacker {
  const val VERSION = 1f
  const val MAX_PARAMS = 8
  const val MAX_INSTANCES = 32
  const val MAX_TRACKS = 9
  const val MAX_KEYS = 64
  fun pack(instances: List<FxInstance>): FloatArray {
    val list = instances.take(MAX_INSTANCES)
    val out = ArrayList<Float>(2 + list.size * 16)
    out.add(VERSION); out.add(list.size.toFloat())
    for (inst in list) {
      out.add(inst.id.toFloat()); out.add(inst.startMs); out.add(inst.endMs); out.add(inst.intensity)
      for (p in 0 until MAX_PARAMS) out.add(if (p < inst.params.size) inst.params[p] else Float.NaN)
      val tracks = inst.tracks.take(MAX_TRACKS); out.add(tracks.size.toFloat())
      for (t in tracks) {
        val keys = t.keys.take(MAX_KEYS); out.add(t.param.toFloat()); out.add(keys.size.toFloat())
        for (k in keys) { out.add(k.timeMs); out.add(k.value); out.add(k.ease.toFloat()) }
      }
    }
    return out.toFloatArray()
  }
  private val nameRules = listOf(
    "MOTION_BLUR" to NativeEffectId.DIRECTIONAL_BLUR, "ZOOM_BLUR" to NativeEffectId.ZOOM_BLUR,
    "SOFT_FOCUS" to NativeEffectId.GAUSSIAN_BLUR, "BLUR" to NativeEffectId.GAUSSIAN_BLUR,
    "RGB_SPLIT" to NativeEffectId.RGB_SPLIT, "GLITCH" to NativeEffectId.GLITCH,
    "VHS" to NativeEffectId.VHS, "RETRO" to NativeEffectId.VHS, "CRT" to NativeEffectId.CRT,
    "LENS_FLARE" to NativeEffectId.LENS_FLARE, "SOLAR_FLARE" to NativeEffectId.LENS_FLARE,
    "LIGHT_LEAK" to NativeEffectId.LIGHT_LEAK, "GOLDEN" to NativeEffectId.LIGHT_LEAK,
    "FLASH" to NativeEffectId.FLASH, "STROBE" to NativeEffectId.FLASH,
    "SHAKE" to NativeEffectId.SHAKE, "CAMERA_MOVEMENT" to NativeEffectId.SHAKE,
    "SPIN" to NativeEffectId.ROTATE, "SKATER_ZOOM" to NativeEffectId.ZOOM_PULSE,
    "ZOOM" to NativeEffectId.ZOOM_PULSE, "DOLLY" to NativeEffectId.ZOOM_PULSE,
    "WARP_SPEED" to NativeEffectId.ZOOM_BLUR, "RIPPLE" to NativeEffectId.RIPPLE,
    "FISHEYE" to NativeEffectId.BULGE, "SWIRL" to NativeEffectId.SWIRL, "MIRROR" to NativeEffectId.MIRROR,
    "KALEIDO" to NativeEffectId.KALEIDOSCOPE, "PIXEL" to NativeEffectId.PIXELATE, "MOSAIC" to NativeEffectId.PIXELATE,
    "GRAIN" to NativeEffectId.FILM_GRAIN, "VIGNETTE" to NativeEffectId.VIGNETTE, "SHARPEN" to NativeEffectId.SHARPEN,
    "SEPIA" to NativeEffectId.SEPIA, "INVERT" to NativeEffectId.INVERT, "EDGE" to NativeEffectId.EDGE_DETECT,
    "SKETCH" to NativeEffectId.EDGE_DETECT, "CHARCOAL" to NativeEffectId.EDGE_DETECT, "HALFTONE" to NativeEffectId.HALFTONE,
    "POP_ART" to NativeEffectId.HALFTONE, "DUOTONE" to NativeEffectId.DUOTONE, "POSTER" to NativeEffectId.POSTERIZE,
    "3D" to NativeEffectId.TRANSFORM_3D, "DISTORT" to NativeEffectId.WAVE, "WAVE" to NativeEffectId.WAVE, "ALIEN" to NativeEffectId.WAVE
  )
  fun nativeIdForEffectName(name: String): Int {
    val upper = name.uppercase()
    for ((needle, id) in nameRules) if (upper.contains(needle)) return id
    return NativeEffectId.BLOOM
  }
  fun forEffectTypeName(name: String, intensity: Float, timelineStartMs: Float): FxInstance {
    val id = nativeIdForEffectName(name)
    val params = FloatArray(MAX_PARAMS) { Float.NaN }
    if (id == NativeEffectId.ROTATE) { params[0] = 0f; params[2] = 90f }
    return FxInstance(id, timelineStartMs, -1f, intensity, params)
  }
}