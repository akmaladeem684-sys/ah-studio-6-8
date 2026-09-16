package com.example.engine.integration

import com.example.domain.model.Timeline

/** Testable layer descriptor using Ah Studio's existing legacy timeline model. */
object MultiLayerCompositor {
  fun activeLayerIds(timeline: Timeline, timeMs: Long): List<String> = buildList {
    timeline.videoClips.filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) }.forEach { add(it.id) }
    timeline.overlayClips.filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) }.forEach { add(it.id) }
    timeline.stickerClips.filter { timeMs in it.timelineStartMs until (it.timelineStartMs + it.durationMs) }.forEach { add(it.id) }
  }
}
