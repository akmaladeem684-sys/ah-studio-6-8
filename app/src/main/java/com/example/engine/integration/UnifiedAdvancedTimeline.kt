package com.example.engine.integration

import com.example.domain.model.Timeline

/**
 * Single authoritative NLE timeline contract for AH Studio.
 *
 * The existing TimelineEngine remains the mutation/playback owner. This contract
 * intentionally exposes one immutable timeline snapshot and one master playhead.
 * Rendering, export, UI and playback integrations should consume this boundary
 * instead of creating secondary timeline containers.
 */
interface UnifiedAdvancedTimeline {
  val timeline: Timeline
  val playheadMs: Long
  val fps: Int

  fun totalDurationMs(): Long = timeline.totalDurationMs

  fun timeToFrame(timeMs: Long): Long =
    Math.round(timeMs.toDouble() * fps.toDouble() / 1000.0).coerceAtLeast(0L)

  fun frameToTime(frameIndex: Long): Long =
    Math.round(frameIndex.toDouble() * 1000.0 / fps.toDouble()).coerceAtLeast(0L)

  fun snapPoints(): List<Long> {
    val points = buildList {
      add(0L)
      timeline.videoClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timeline.overlayClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timeline.audioClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timeline.textClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timeline.stickerClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      timeline.effectClips.forEach { add(it.timelineStartMs); add(it.timelineStartMs + it.durationMs) }
      add(timeline.totalDurationMs)
    }
    return points.distinct().sorted()
  }
}

/**
 * Adapter used at integration boundaries. It does not own timeline state.
 * TimelineEngine is the only state owner; this adapter prevents accidental
 * creation of another timeline clock/model.
 */
class UnifiedAdvancedTimelineAdapter(
  private val source: Timeline,
  override val playheadMs: Long,
  override val fps: Int
) : UnifiedAdvancedTimeline {
  override val timeline: Timeline get() = source
}
