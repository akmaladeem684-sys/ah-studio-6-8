package com.example.engine.controller

import android.util.Log
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Timeline projection layer. It never manipulates MediaCodec/ExoPlayer directly;
 * all player mutations are routed through PlaybackController.
 */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {
  companion object {
    private const val TAG = "TimelineSyncManager"
    private const val SYNC_INTERVAL_MS = 16L
  }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var syncJob: Job? = null
  private var currentTimeline: Timeline = Timeline()
  private var activeClip: VideoClip? = null
  private var lastTransitionPosition = Long.MIN_VALUE

  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    val bounded = _timelinePositionMs.value.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    setPosition(bounded)
  }

  fun setActiveClip(clip: VideoClip?) {
    activeClip = clip
  }

  fun setPosition(positionMs: Long) {
    val bounded = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    _timelinePositionMs.value = bounded
    playbackController.updateTimelinePosition(bounded)
    onTimelinePositionUpdated(bounded)
  }

  fun startSyncLoop() {
    stopSyncLoop()
    syncJob = scope.launch {
      while (isActive) {
        if (playbackController.isPlaying) {
          val active = activeClip ?: findClipAt(_timelinePositionMs.value)
          if (active != null && active.isVideo) {
            // ExoPlayer/Media3 owns the actual A/V media clock. We only project it onto the NLE timeline.
            val playerPos = playbackController.sampleClockPositionMs()
            val speed = active.speed.coerceAtLeast(0.01f)
            val sourceOffset = (playerPos - active.sourceStartMs).coerceAtLeast(0L)
            val offsetInClip = (sourceOffset / speed).toLong()
            val calculatedTimeline = active.timelineStartMs + offsetInClip
            val clipEnd = active.timelineStartMs + active.durationMs

            if (calculatedTimeline >= clipEnd) {
              handleClipEnd(active)
            } else {
              publishPosition(calculatedTimeline.coerceIn(0L, currentTimeline.totalDurationMs))
            }
          } else {
            // No media clock is available for a non-video gap. Advance from the last projected position
            // without touching the player; the next real clip will re-anchor to Media3's clock.
            val next = (_timelinePositionMs.value + SYNC_INTERVAL_MS)
              .coerceAtMost(currentTimeline.totalDurationMs)
            publishPosition(next)
            val nextClip = findClipAt(next)
            if (nextClip != null && nextClip.id != active?.id) {
              activeClip = nextClip
              onClipTransition(nextClip, next)
            }
            if (next >= currentTimeline.totalDurationMs) {
              finishPlayback()
              break
            }
          }
        }
        delay(SYNC_INTERVAL_MS)
      }
    }
  }

  private fun publishPosition(positionMs: Long) {
    val bounded = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    if (_timelinePositionMs.value != bounded) {
      _timelinePositionMs.value = bounded
      playbackController.updateTimelinePosition(bounded)
      onTimelinePositionUpdated(bounded)
    }
  }

  private fun handleClipEnd(endedClip: VideoClip) {
    val nextTimelinePos = (endedClip.timelineStartMs + endedClip.durationMs)
      .coerceAtMost(currentTimeline.totalDurationMs)
    if (nextTimelinePos >= currentTimeline.totalDurationMs) {
      finishPlayback()
      return
    }

    val nextClip = findClipAt(nextTimelinePos)
    activeClip = nextClip
    lastTransitionPosition = nextTimelinePos
    publishPosition(nextTimelinePos)
    onClipTransition(nextClip, nextTimelinePos)
  }

  private fun finishPlayback() {
    Log.d(TAG, "Timeline playback completed")
    playbackController.pause()
    publishPosition(0L)
    activeClip = findClipAt(0L)
    onPlaybackEnded()
  }

  fun findClipAt(positionMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs
  }

  fun stopSyncLoop() {
    syncJob?.cancel()
    syncJob = null
  }

  fun release() {
    stopSyncLoop()
    scope.cancel()
  }
}
