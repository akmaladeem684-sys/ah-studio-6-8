package com.example

import com.example.domain.model.*
import com.example.engine.TimelineEngine
import com.example.engine.audio.AudioPeak
import com.example.engine.audio.AudioWaveformManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioWaveformTest {
  private lateinit var timelineEngine: TimelineEngine
  @Before fun setup() { timelineEngine = TimelineEngine(); AudioWaveformManager.clearCache() }

  @Test fun testWaveformGeneration_createsValidAmplitudesAndDynamics() {
    val waveform = AudioWaveformManager.generateRichWaveform("test_seed_123",5000L)
    assertTrue(waveform.size >= 100); waveform.forEach { assertTrue(it >= 0.01f); assertTrue(it <= 1f) }
    assertTrue((waveform.maxOrNull()!! - waveform.minOrNull()!!) > 0.4f)
  }
  @Test fun testWaveformAnalysis_detectsPeaksAndCalculatesRms() {
    val samples=listOf(0.2f,0.3f,0.95f,0.3f,0.1f,0.2f,0.4f,0.70f,0.3f,0.1f,0.1f,0.2f,0.88f,0.2f,0.1f)
    val a=AudioWaveformManager.analyzeWaveform(samples,1500L,0.60f)
    assertEquals(3,a.peaks.size); assertEquals(2,a.prominentPeaks.size); assertEquals(2,a.peaks[0].index); assertEquals(7,a.peaks[1].index); assertEquals(12,a.peaks[2].index); assertTrue(a.rmsAverage>0.2f); assertEquals(0.95f,a.maxPeak,0.001f)
  }
  @Test fun testSliceForTrim_preservesTrimRatioAndAlignment() {
    val s=AudioWaveformManager.sliceForTrim((0..99).map{it/100f},2000L,6000L,10000L)
    assertEquals(40,s.size); assertEquals(0.20f,s.first(),0.02f); assertEquals(0.59f,s.last(),0.02f)
  }
  @Test fun testPeakSnappingAndNavigation() {
    val p=listOf(AudioPeak(5,500L,0.9f,true),AudioPeak(10,1000L,0.85f,true),AudioPeak(15,1500L,0.92f,true))
    assertEquals(1000L,AudioWaveformManager.findNearestPeak(1030L,p,80L)?.timeMs); assertNull(AudioWaveformManager.findNearestPeak(1120L,p,80L)); assertEquals(1000L,AudioWaveformManager.findNextPeak(600L,p)?.timeMs); assertEquals(500L,AudioWaveformManager.findPrevPeak(950L,p)?.timeMs)
  }
  @Test fun testTimelineEngine_jumpToNextAudioPeak_seeksPlayhead() { timelineEngine.setPosition(0L); timelineEngine.addAudioClip("Beat Drop Track",6000L); assertTrue(timelineEngine.jumpToNextAudioPeak()); assertTrue(timelineEngine.currentPositionMs.value>0L) }
  @Test fun testSilenceDetection_identifiesDeadAirRegions() {
    val s=listOf(0.8f,0.7f,0.9f,0.6f,0.5f,0.02f,0.01f,0.04f,0.02f,0.01f,0.03f,0.01f,0.02f,0.01f,0.03f,0.7f,0.85f,0.9f,0.8f,0.75f)
    val r=AudioWaveformManager.detectSilenceRegions(s,2000L,0.05f,500L); assertEquals(1,r.size); assertEquals(500L,r.first().startMs); assertEquals(1500L,r.first().endMs); assertEquals(1000L,r.first().durationMs); assertEquals(500L,AudioWaveformManager.findNextSilence(200L,r)?.startMs); assertEquals(500L,AudioWaveformManager.findPrevSilence(1800L,r)?.startMs)
  }
  @Test fun testTimelineEngine_removeSilence_splitsAndStitchesAudioClip() {
    val s=listOf(0.8f,0.7f,0.9f,0.6f,0.5f,0.01f,0.02f,0.01f,0.01f,0.02f,0.01f,0.01f,0.02f,0.01f,0.02f,0.75f,0.85f,0.92f,0.8f,0.7f)
    val c=AudioClip("clip_vocal_1","asset:///audio/voiceover.mp3","Voiceover",0L,2000L,0L,2000L,s); timelineEngine.loadTimeline(Timeline(audioClips=listOf(c)); assertTrue(timelineEngine.removeSilenceFromAudioClip("clip_vocal_1",0.05f,500L)); val r=timelineEngine.timeline.value.audioClips; assertEquals(2,r.size); assertEquals(0L,r[0].timelineStartMs); assertEquals(500L,r[0].durationMs); assertEquals(500L,r[1].timelineStartMs); assertEquals(500L,r[1].durationMs)
  }
}
