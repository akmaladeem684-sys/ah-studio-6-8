package com.example

import com.example.domain.model.*
import com.example.engine.TimelineEngine
import com.example.engine.audio.AudioPeak
import com.example.engine.audio.AudioWaveformManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioWaveformTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setup() {
    timelineEngine = TimelineEngine()
    AudioWaveformManager.clearCache()
  }

  @Test
  fun testWaveformGeneration_createsValidAmplitudesAndDynamics() {
    val waveform = AudioWaveformManager.generateRichWaveform("test_seed_123", durationMs = 5000L)
    assertTrue("Waveform should have sufficient sample points", waveform.size >= 100)
    waveform.forEach { amp ->
      assertTrue("Amplitude $amp should be >= 0.01f", amp >= 0.01f)
      assertTrue("Amplitude $amp should be <= 1.0f", amp <= 1.0f)
    }
    val minVal = waveform.minOrNull() ?: 0f
    val maxVal = waveform.maxOrNull() ?: 0f
    assertTrue("Dynamic range should be significant", (maxVal - minVal) > 0.4f)
  }

  @Test
  fun testWaveformAnalysis_detectsPeaksAndCalculatesRms() {
    val samples = listOf(
      0.2f, 0.3f, 0.95f, 0.3f, 0.1f,
      0.2f, 0.4f, 0.70f, 0.3f, 0.1f,
      0.1f, 0.2f, 0.88f, 0.2f, 0.1f
    )
    val analysis = AudioWaveformManager.analyzeWaveform(samples, clipDurationMs = 1500L, peakThreshold = 0.60f)
    assertEquals("Should detect 3 peaks", 3, analysis.peaks.size)
    assertEquals("Should detect 2 prominent peaks", 2, analysis.prominentPeaks.size)
    assertEquals(2, analysis.peaks[0].index)
    assertEquals(7, analysis.peaks[1].index)
    assertEquals(12, analysis.peaks[2].index)
    assertTrue("RMS average should be positive", analysis.rmsAverage > 0.2f)
    assertEquals(0.95f, analysis.maxPeak, 0.001f)
  }

  @Test
  fun testSliceForTrim_preservesTrimRatioAndAlignment() {
    val fullWaveform = (0..99).map { it / 100f }
    val sliced = AudioWaveformManager.sliceForTrim(fullWaveform, 2000L, 6000L, 10_000L)
    assertEquals("Trimmed slice should contain 40 samples", 40, sliced.size)
    assertEquals(0.20f, sliced.first(), 0.02f)
    assertEquals(0.59f, sliced.last(), 0.02f)
  }

  @Test
  fun testPeakSnappingAndNavigation() {
    val peaks = listOf(
      AudioPeak(index = 5, timeMs = 500L, amplitude = 0.9f, isProminent = true),
      AudioPeak(index = 10, timeMs = 1000L, amplitude = 0.85f, isProminent = true),
      AudioPeak(index = 15, timeMs = 1500L, amplitude = 0.92f, isProminent = true)
    )
    assertEquals(1000L, AudioWaveformManager.findNearestPeak(1030L, peaks, 80L)?.timeMs)
    assertNull(AudioWaveformManager.findNearestPeak(1120L, peaks, 80L))
    assertEquals(1000L, AudioWaveformManager.findNextPeak(600L, peaks)?.timeMs)
    assertEquals(500L, AudioWaveformManager.findPrevPeak(950L, peaks)?.timeMs)
  }

  @Test
  fun testTimelineEngine_jumpToNextAudioPeak_seeksPlayhead() {
    timelineEngine.setPosition(0L)
    timelineEngine.addAudioClip("Beat Drop Track", durationMs = 6000L)
    assertTrue("Should find and jump to next peak", timelineEngine.jumpToNextAudioPeak())
    assertTrue("Playhead should advance to peak time", timelineEngine.currentPositionMs.value > 0L)
  }

  @Test
  fun testSilenceDetection_identifiesDeadAirRegions() {
    val samples = listOf(
      0.8f, 0.7f, 0.9f, 0.6f, 0.5f,
      0.02f, 0.01f, 0.04f, 0.02f, 0.01f, 0.03f, 0.01f, 0.02f, 0.01f, 0.03f,
      0.7f, 0.85f, 0.9f, 0.8f, 0.75f
    )
    val silences = AudioWaveformManager.detectSilenceRegions(samples, 2000L, 0.05f, 500L)
    assertEquals(1, silences.size)
    val region = silences.first()
    assertEquals(500L, region.startMs)
    assertEquals(1500L, region.endMs)
    assertEquals(1000L, region.durationMs)
    assertEquals(500L, AudioWaveformManager.findNextSilence(200L, silences)?.startMs)
    assertEquals(500L, AudioWaveformManager.findPrevSilence(1800L, silences)?.startMs)
  }

  @Test
  fun testTimelineEngine_removeSilence_splitsAndStitchesAudioClip() {
    val samples = listOf(
      0.8f, 0.7f, 0.9f, 0.6f, 0.5f,
      0.01f, 0.02f, 0.01f, 0.01f, 0.02f, 0.01f, 0.01f, 0.02f, 0.01f, 0.02f,
      0.75f, 0.85f, 0.92f, 0.8f, 0.7f
    )
    val clip = AudioClip(
      id = "clip_vocal_1", uri = "asset:///audio/voiceover.mp3", title = "Voiceover",
      timelineStartMs = 0L, durationMs = 2000L, sourceStartMs = 0L, sourceEndMs = 2000L,
      waveformData = samples
    )
    timelineEngine.loadTimeline(Timeline(audioClips = listOf(clip)))
    assertTrue(timelineEngine.removeSilenceFromAudioClip("clip_vocal_1", 0.05f, 500L))
    val resultingClips = timelineEngine.timeline.value.audioClips
    assertEquals(2, resultingClips.size)
    assertEquals(0L, resultingClips[0].timelineStartMs)
    assertEquals(500L, resultingClips[0].durationMs)
    assertEquals(500L, resultingClips[1].timelineStartMs)
    assertEquals(500L, resultingClips[1].durationMs)
  }
}
