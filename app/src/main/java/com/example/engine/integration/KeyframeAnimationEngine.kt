package com.example.engine.integration

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import kotlin.math.abs

/** UI-independent keyframe evaluator/adaptor used by playback, preview and export callers. */
object KeyframeAnimationEngine {
  data class Value(val positionX: Float, val positionY: Float, val scaleX: Float, val scaleY: Float, val rotation: Float, val opacity: Float)

  fun evaluate(keyframes: List<ClipKeyframe>, timeMs: Long): Value {
    if (keyframes.isEmpty()) return Value(0f, 0f, 1f, 1f, 0f, 1f)
    val sorted = keyframes.sortedBy { it.timeMs }
    if (timeMs <= sorted.first().timeMs) return value(sorted.first())
    if (timeMs >= sorted.last().timeMs) return value(sorted.last())
    val afterIndex = sorted.indexOfFirst { it.timeMs >= timeMs }
    val before = sorted[afterIndex - 1]; val after = sorted[afterIndex]
    val raw = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val t = easing(before.interpolation, raw)
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    return Value(lerp(before.posX, after.posX), lerp(before.posY, after.posY), lerp(before.scaleX, after.scaleX), lerp(before.scaleY, after.scaleY), lerp(before.rotation, after.rotation), lerp(before.opacity, after.opacity).coerceIn(0f, 1f))
  }
  fun insertOrUpdate(keyframes: List<ClipKeyframe>, keyframe: ClipKeyframe, toleranceMs: Long = 1): List<ClipKeyframe> = (keyframes.filterNot { it.id == keyframe.id || abs(it.timeMs - keyframe.timeMs) <= toleranceMs } + keyframe).sortedBy { it.timeMs }
  fun delete(keyframes: List<ClipKeyframe>, id: String): List<ClipKeyframe> = keyframes.filterNot { it.id == id }
  private fun value(k: ClipKeyframe) = Value(k.posX, k.posY, k.scaleX, k.scaleY, k.rotation, k.opacity)
  private fun easing(kind: KeyframeInterpolation, t: Float) = when (kind) {
    KeyframeInterpolation.HOLD -> 0f
    KeyframeInterpolation.EASE_IN -> t * t
    KeyframeInterpolation.EASE_OUT -> 1f - (1f - t) * (1f - t)
    KeyframeInterpolation.EASE_IN_OUT -> if (t < .5f) 2f*t*t else 1f - (-2f*t+2f).let { it*it }/2f
    else -> t
  }
}
