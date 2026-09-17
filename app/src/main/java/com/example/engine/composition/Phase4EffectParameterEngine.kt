package com.example.engine.composition

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.EffectClip
import com.example.domain.model.KeyframeInterpolation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 4 property evaluator for effect parameters.
 *
 * The HTML Phase 4 reference models animation as per-property tracks evaluated
 * from clip-local time. The Android project historically stored keyframes on
 * EffectClip as a single list, so this bridge adds named parameter tracks while
 * keeping EffectClip.intensity as the backward-compatible fallback.
 *
 * Evaluation is deterministic, allocation-light, and independent of UI state.
 */
object Phase4EffectParameterEngine {

  fun evaluate(
    effect: EffectClip,
    property: String,
    timelineTimeMs: Long,
    fallback: Float? = null
  ): Float {
    val track = effect.parameterKeyframes[property]
    if (track.isNullOrEmpty()) return fallback ?: defaultFor(effect, property)
    return evaluateTrack(track, timelineTimeMs - effect.timelineStartMs)
  }

  fun evaluateTrack(keyframes: List<ClipKeyframe>, localTimeMs: Long): Float {
    if (keyframes.isEmpty()) return 0f
    val sorted = keyframes.sortedWith(compareBy<ClipKeyframe> { it.timeMs }.thenBy { it.id })
    if (localTimeMs <= sorted.first().timeMs) return sorted.first().effectParam
    if (localTimeMs >= sorted.last().timeMs) return sorted.last().effectParam

    var low = 0
    var high = sorted.lastIndex
    while (low + 1 < high) {
      val mid = (low + high) ushr 1
      if (sorted[mid].timeMs <= localTimeMs) low = mid else high = mid
    }

    val before = sorted[low]
    val after = sorted[high]
    val span = max(1L, after.timeMs - before.timeMs)
    val raw = ((localTimeMs - before.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    val t = interpolate(before.interpolation, before.customCurvePoints, raw)
    return before.effectParam + (after.effectParam - before.effectParam) * t
  }

  fun upsert(
    keyframes: List<ClipKeyframe>,
    timeMs: Long,
    value: Float,
    interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR,
    toleranceMs: Long = 16L
  ): List<ClipKeyframe> {
    val existing = keyframes.firstOrNull { abs(it.timeMs - timeMs) <= toleranceMs }
    val next = existing?.copy(
      timeMs = timeMs,
      effectParam = value
    ) ?: ClipKeyframe(
      timeMs = timeMs,
      effectParam = value,
      interpolation = interpolation
    )
    return keyframes.filterNot { it.id == existing?.id || abs(it.timeMs - timeMs) <= toleranceMs }
      .plus(next)
      .sortedBy { it.timeMs }
  }

  fun removeAt(keyframes: List<ClipKeyframe>, timeMs: Long, toleranceMs: Long = 16L): List<ClipKeyframe> =
    keyframes.filterNot { abs(it.timeMs - timeMs) <= toleranceMs }

  private fun defaultFor(effect: EffectClip, property: String): Float = when (property) {
    "intensity" -> effect.intensity
    else -> 0f
  }

  private fun interpolate(
    interpolation: KeyframeInterpolation,
    points: List<Float>,
    raw: Float
  ): Float {
    val t = raw.coerceIn(0f, 1f)
    return when (interpolation) {
      KeyframeInterpolation.HOLD -> 0f
      KeyframeInterpolation.LINEAR -> t
      KeyframeInterpolation.EASE_IN -> t * t * t
      KeyframeInterpolation.EASE_OUT -> 1f - (1f - t) * (1f - t) * (1f - t)
      KeyframeInterpolation.EASE_IN_OUT ->
        if (t < 0.5f) 4f * t * t * t else 1f - ((-2f * t + 2f).let { it * it * it } / 2f)
      KeyframeInterpolation.CUBIC_BEZIER,
      KeyframeInterpolation.CUSTOM_CURVE -> solveCubicBezier(
        points.getOrNull(0) ?: 0.42f,
        points.getOrNull(1) ?: 0f,
        points.getOrNull(2) ?: 0.58f,
        points.getOrNull(3) ?: 1f,
        t
      )
    }
  }

  private fun solveCubicBezier(
    p1x: Float,
    p1y: Float,
    p2x: Float,
    p2y: Float,
    targetX: Float
  ): Float {
    if (targetX <= 0f) return 0f
    if (targetX >= 1f) return 1f
    var low = 0f
    var high = 1f
    var t = targetX
    repeat(16) {
      val x = bezier(p1x, p2x, t)
      if (abs(x - targetX) < 0.0005f) return bezier(p1y, p2y, t).coerceIn(0f, 1f)
      if (x < targetX) low = t else high = t
      t = (low + high) * 0.5f
    }
    return bezier(p1y, p2y, t).coerceIn(0f, 1f)
  }

  private fun bezier(p1: Float, p2: Float, t: Float): Float {
    val u = 1f - t
    return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
  }
}
