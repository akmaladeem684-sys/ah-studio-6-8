package com.example.engine.integration

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import org.junit.Assert.*
import org.junit.Test

class AutocutSystemsIntegrationTest {
  private fun timeline() = Timeline(videoClips = listOf(VideoClip(id = "v1", name = "one", durationMs = 2_000, timelineStartMs = 0), VideoClip(id = "v2", name = "two", durationMs = 2_000, timelineStartMs = 1_000)), overlayClips = listOf(VideoClip(id = "o1", name = "overlay", durationMs = 1_000, timelineStartMs = 1_000)))

  @Test fun indexingIntervalsTracksAndSnapping() {
    val index = AdvancedTimelineIndex.build(timeline())
    assertEquals("v1", index.findClip("v1")?.id)
    assertEquals(setOf("v1", "v2", "o1"), index.getClipsAt(1_500).map { it.id }.toSet())
    assertEquals(1, index.findOverlaps("video").size)
    assertEquals(1_000L, index.snapIndex.findClosest(995, 10))
  }
  @Test fun keyframesAndTimeMapping() {
    val frames = listOf(ClipKeyframe(timeMs = 0, scale = 1f), ClipKeyframe(timeMs = 1_000, scale = 2f))
    assertEquals(1.5f, KeyframeAnimationEngine.evaluate(frames, 500).scaleX, .001f)
    val mapper = CompoundTimelineTimeMapper(100, 1_000, 0, 2_000, speed = 2.0)
    assertEquals(1_000L, mapper.parentToSource(600))
    assertEquals(600L, mapper.sourceToParent(1_000))
  }
  @Test fun commandSystemIsReversibleAndCompositingPreservesOrder() {
    val before = timeline(); val after = before.copy(videoClips = before.videoClips.drop(1)); val commands = TimelineCommandSystem()
    commands.execute("remove", before, after); assertEquals(before, commands.undo(after)); assertEquals(after, commands.redo(before))
    assertEquals(listOf("v1", "v2", "o1"), MultiLayerCompositor.activeLayerIds(before, 1_500))
  }
}
