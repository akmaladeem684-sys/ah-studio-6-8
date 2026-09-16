package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.controller.CustomVideoEngineController
import com.example.engine.controller.DecoderState
import com.example.engine.controller.EnginePlaybackState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomVideoEngineControllerTest {

  private lateinit var context: Context
  private lateinit var controller: CustomVideoEngineController
  private var reportedTimelinePos: Long = -1L
  private var playbackEndedCalled: Boolean = false

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    reportedTimelinePos = -1L
    playbackEndedCalled = false

    controller = CustomVideoEngineController(
      context = context,
      onTimelinePositionChanged = { pos -> reportedTimelinePos = pos },
      onPlaybackEnded = { playbackEndedCalled = true }
    )
  }

  @Test
  fun testInitialState() {
    val state = controller.engineState.value
    assertNotNull(state)
    assertEquals(0L, state.currentPosition)
    assertEquals(0L, state.duration)
    assertFalse(state.isPlaying)
  }

  @Test
  fun testTimelineUpdateAndDuration() {
    val clip1 = VideoClip(
      id = "clip_1",
      name = "video1.mp4",
      uri = "content://media/video1.mp4",
      sourceTotalDurationMs = 5000L,
      durationMs = 5000L,
      timelineStartMs = 0L,
      isVideo = true
    )
    val clip2 = VideoClip(
      id = "clip_2",
      name = "video2.mp4",
      uri = "content://media/video2.mp4",
      sourceTotalDurationMs = 7000L,
      durationMs = 7000L,
      timelineStartMs = 5000L,
      isVideo = true
    )

    val timeline = Timeline(
      videoClips = listOf(clip1, clip2)
    )

    controller.updateTimeline(timeline)
    assertEquals(12000L, controller.engineState.value.duration)
  }

  @Test
  fun testFrameAccurateScrubbingAndSeeking() {
    val clip1 = VideoClip(
      id = "clip_1",
      name = "video1.mp4",
      uri = "content://media/video1.mp4",
      sourceTotalDurationMs = 10000L,
      durationMs = 10000L,
      timelineStartMs = 0L,
      isVideo = true
    )
    val timeline = Timeline(videoClips = listOf(clip1))
    controller.updateTimeline(timeline)

    // Start scrubbing
    controller.startScrubbing()
    assertTrue(controller.isScrubbing)

    // Scrub to 3500ms
    controller.scrubTo(3500L)
    assertEquals(3500L, controller.currentPosition)

    // Stop scrubbing at 4200ms
    controller.stopScrubbing(4200L)
    assertFalse(controller.isScrubbing)
    assertEquals(4200L, controller.currentPosition)
  }

  @Test
  fun testPlayPauseToggle() {
    val clip = VideoClip(
      id = "clip_test",
      name = "sample.mp4",
      uri = "content://media/sample.mp4",
      sourceTotalDurationMs = 10000L,
      durationMs = 10000L,
      timelineStartMs = 0L,
      isVideo = true
    )
    controller.updateTimeline(Timeline(videoClips = listOf(clip)))

    controller.play()
    assertEquals(EnginePlaybackState.PLAYING, controller.engineState.value.playbackState)
    assertTrue(controller.isPlaying)

    controller.pause()
    assertEquals(EnginePlaybackState.PAUSED, controller.engineState.value.playbackState)
    assertFalse(controller.isPlaying)
  }

  @Test
  fun testDecoderHardwareCapabilitiesAndFallback() {
    val decoderManager = controller.decoderManager
    assertNotNull(decoderManager.decoderState)

    // Verify 1080p / 2K / 4K resolution support query
    val is4kSupported = decoderManager.checkResolutionSupport("video/avc", 3840, 2160)
    assertTrue(is4kSupported)

    // Verify graceful fallback upon codec failure
    val handled = decoderManager.handleCodecError(IllegalStateException("Simulated hardware codec error"))
    assertTrue(handled)
    assertEquals(DecoderState.SOFTWARE_FALLBACK, decoderManager.decoderState)

    // Reset and recover
    controller.recoverFromError()
    assertNull(controller.engineState.value.error)
  }

  @Test
  fun testRenderCacheGranularLayerInvalidation() {
    val cache = controller.renderCacheManager

    val fakeBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    cache.putFrame("clip_101", 1000L, fakeBitmap)
    assertNotNull(cache.getFrame("clip_101", 1000L))

    // Invalidate clip_101 only
    controller.invalidateClip("clip_101")
    assertNull(cache.getFrame("clip_101", 1000L))

    // Invalidate all
    controller.invalidateAll()
  }

  @Test
  fun testRapidPlayPauseSeekStressTest() {
    val clip = VideoClip(
      id = "clip_stress",
      name = "4k_stress.mp4",
      uri = "content://media/4k_stress.mp4",
      sourceTotalDurationMs = 60000L, // 1 minute
      durationMs = 60000L,
      timelineStartMs = 0L,
      isVideo = true
    )
    controller.updateTimeline(Timeline(videoClips = listOf(clip)))

    // Stress test: 200 consecutive rapid play/pause/seek operations
    for (i in 1..200) {
      val targetPos = (i * 250L) % 60000L
      controller.seekTo(targetPos)
      if (i % 2 == 0) {
        controller.play()
      } else {
        controller.pause()
      }
    }

    assertNotNull(controller.engineState.value)
    assertNull(controller.engineState.value.error)
  }

  @Test
  fun test50PlusLayersStressTest() {
    val textClips = (1..60).map { idx ->
      com.example.domain.model.TextClip(
        id = "text_$idx",
        text = "Layer $idx Title",
        timelineStartMs = 0L,
        durationMs = 10000L,
        fontSizeSp = 24f,
        posX = (idx % 10) * 0.1f,
        posY = (idx % 10) * 0.1f
      )
    }

    val stickerClips = (1..30).map { idx ->
      com.example.domain.model.StickerClip(
        id = "sticker_$idx",
        emojiOrAsset = "🔥",
        timelineStartMs = 0L,
        durationMs = 10000L,
        posX = 0.5f,
        posY = 0.5f
      )
    }

    val timeline = Timeline(
      textClips = textClips,
      stickerClips = stickerClips
    )

    controller.updateTimeline(timeline)

    // Repeatedly invalidate text and effect layers without restarting playback engine
    for (idx in 1..60) {
      controller.invalidateClip("text_$idx")
    }

    assertEquals(0L, controller.currentPosition)
  }
}
