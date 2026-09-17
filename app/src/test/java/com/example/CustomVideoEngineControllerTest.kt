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

  @Before fun setUp() {
    context = ApplicationProvider.getApplicationContext(); reportedTimelinePos = -1L; playbackEndedCalled = false
    controller = CustomVideoEngineController(context,{ pos -> reportedTimelinePos = pos },{ playbackEndedCalled = true })
  }
  @Test fun testInitialState() { val state=controller.engineState.value; assertNotNull(state); assertEquals(0L,state.currentPosition); assertEquals(0L,state.duration); assertFalse(state.isPlaying) }
  @Test fun testTimelineUpdateAndDuration() {
    val c1=VideoClip("clip_1","video1.mp4","content://media/video1.mp4",5000L,5000L,0L,isVideo=true)
    val c2=VideoClip("clip_2","video2.mp4","content://media/video2.mp4",7000L,7000L,5000L,isVideo=true)
    controller.updateTimeline(Timeline(videoClips=listOf(c1,c2))); assertEquals(12000L,controller.engineState.value.duration)
  }
  @Test fun testFrameAccurateScrubbingAndSeeking() {
    val c=VideoClip("clip_1","video1.mp4","content://media/video1.mp4",10000L,10000L,0L,isVideo=true)
    controller.updateTimeline(Timeline(videoClips=listOf(c))); controller.startScrubbing(); assertTrue(controller.isScrubbing); controller.scrubTo(3500L); assertEquals(3500L,controller.currentPosition); controller.stopScrubbing(4200L); assertFalse(controller.isScrubbing); assertEquals(4200L,controller.currentPosition)
  }
  @Test fun testPlayPauseToggle() {
    val c=VideoClip("clip_test","sample.mp4","content://media/sample.mp4",10000L,10000L,0L,isVideo=true)
    controller.updateTimeline(Timeline(videoClips=listOf(c)))
    controller.play()
    // Fixture URI does not resolve to a real media source in Robolectric; the controller must not falsely report active playback.
    assertNotEquals(EnginePlaybackState.ERROR,controller.engineState.value.playbackState)
    assertFalse(controller.isPlaying)
    controller.pause(); assertEquals(EnginePlaybackState.PAUSED,controller.engineState.value.playbackState); assertFalse(controller.isPlaying)
  }
  @Test fun testDecoderHardwareCapabilitiesAndFallback() {
    val d=controller.decoderManager; assertNotNull(d.decoderState); assertTrue(d.checkResolutionSupport("video/avc",3840,2160)); assertTrue(d.handleCodecError(IllegalStateException("Simulated hardware codec error"))); assertEquals(DecoderState.SOFTWARE_FALLBACK,d.decoderState); controller.recoverFromError(); assertNull(controller.engineState.value.error)
  }
  @Test fun testRenderCacheGranularLayerInvalidation() {
    val cache=controller.renderCacheManager; val b=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888); cache.putFrame("clip_101",1000L,b); assertNotNull(cache.getFrame("clip_101",1000L)); controller.invalidateClip("clip_101"); assertNull(cache.getFrame("clip_101",1000L)); controller.invalidateAll()
  }
  @Test fun testRapidPlayPauseSeekStressTest() {
    val c=VideoClip("clip_stress","4k_stress.mp4","content://media/4k_stress.mp4",60000L,60000L,0L,isVideo=true); controller.updateTimeline(Timeline(videoClips=listOf(c)))
    for(i in 1..200){ controller.seekTo((i*250L)%60000L); if(i%2==0) controller.play() else controller.pause() }
    assertNotNull(controller.engineState.value); assertNull(controller.engineState.value.error)
  }
  @Test fun test50PlusLayersStressTest() {
    val text=(1..60).map{ i->com.example.domain.model.TextClip("text_$i","Layer $i Title",0L,10000L,fontSizeSp=24f,posX=(i%10)*0.1f,posY=(i%10)*0.1f) }
    val stickers=(1..30).map{ i->com.example.domain.model.StickerClip("sticker_$i","🔥",0L,10000L,posX=0.5f,posY=0.5f) }
    controller.updateTimeline(Timeline(textClips=text,stickerClips=stickers)); for(i in 1..60) controller.invalidateClip("text_$i"); assertEquals(0L,controller.currentPosition)
  }
}
