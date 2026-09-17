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

/** Timeline projection layer. Media3/master clock owns time; this class only projects it. */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {
  companion object { private const val TAG = "TimelineSyncManager"; private const val SYNC_INTERVAL_MS = 16L }

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

  fun setActiveClip(clip: VideoClip?) { activeClip = clip }

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
          // Never advance time here. The master clock is already advanced/re-anchored by PlaybackController.
          val position = playbackController.sampleClockPositionMs()
          val active = activeClip ?: findClipAt(position)
          if (position >= currentTimeline.totalDurationMs && currentTimeline.totalDurationMs > 0L) {
            finishPlayback()
          } else {
            publishPosition(position)
            val next = findClipAt(position)
            if (next?.id != active?.id && next != null && lastTransitionPosition != position) {
              lastTransitionPosition = position
              activeClip = next
              onClipTransition(next, position)
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
      onTimelinePositionUpdated(bounded)
    }
  }

  private fun finishPlayback() {
    val end = currentTimeline.totalDurationMs.coerceAtLeast(0L)
    Log.d(TAG, "Timeline playback completed at ${end}ms")
    playbackController.pause()
    publishPosition(end)
    activeClip = findClipAt((end - 1L).coerceAtLeast(0L))
    onPlaybackEnded()
  }

  fun findClipAt(positionMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs
  }

  fun stopSyncLoop() { syncJob?.cancel(); syncJob = null }
  fun release() { stopSyncLoop(); scope.cancel() }
}
