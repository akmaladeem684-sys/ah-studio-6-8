package com.example

import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.domain.model.AudioClip
import com.example.domain.model.TextClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NleMultiTrackLaneModelTest {
  @Test
  fun explicit_subtrack_indexes_are_preserved_across_all_clip_roles() {
    val overlay1 = VideoClip(id = "o1", name = "Overlay 1", trackIndex = 1)
    val overlay2 = VideoClip(id = "o2", name = "Overlay 2", trackIndex = 2)
    val audio1 = AudioClip(id = "a1", uri = "a1", title = "Audio 1", trackIndex = 1)
    val audio2 = AudioClip(id = "a2", uri = "a2", title = "Audio 2", trackIndex = 2)
    val text1 = TextClip(id = "t1", trackIndex = 1)
    val text2 = TextClip(id = "t2", trackIndex = 2)
    val sticker1 = StickerClip(id = "s1", trackIndex = 1)
    val sticker2 = StickerClip(id = "s2", trackIndex = 2)
    val effect1 = EffectClip(id = "fx1", trackIndex = 1)
    val effect2 = EffectClip(id = "fx2", trackIndex = 2)

    val timeline = Timeline(
      videoClips = listOf(VideoClip(id = "v", name = "Main")),
      overlayClips = listOf(overlay1, overlay2),
      audioClips = listOf(audio1, audio2),
      textClips = listOf(text1, text2),
      stickerClips = listOf(sticker1, sticker2),
      effectClips = listOf(effect1, effect2)
    )

    assertEquals(listOf(1, 2), timeline.overlayClips.map { it.trackIndex })
    assertEquals(listOf(1, 2), timeline.audioClips.map { it.trackIndex })
    assertEquals(listOf(1, 2), timeline.textClips.map { it.trackIndex })
    assertEquals(listOf(1, 2), timeline.stickerClips.map { it.trackIndex })
    assertEquals(listOf(1, 2), timeline.effectClips.map { it.trackIndex })
    assertTrue(timeline.totalDurationMs >= 3000L)
  }

  @Test
  fun many_track_indexes_remain_valid_without_row_compression() {
    val overlays = (1..100).map { lane ->
      VideoClip(id = "o$lane", name = "Overlay $lane", trackIndex = lane)
    }
    assertEquals(100, overlays.maxOf { it.trackIndex })
    assertEquals(100, overlays.map { it.trackIndex }.distinct().size)
  }
}
