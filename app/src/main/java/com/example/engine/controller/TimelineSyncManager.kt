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

/** Timeline projection layer. PlaybackController owns the authoritative media clock. */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long, Boolean) -> Unit,
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

  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    setPosition(_timelinePositionMs.value.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L)))
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
          val active = activeClip ?: findClipAt(_timelinePositionMs.value)
          if (active != null && active.isVideo) {
            val playerPos = playbackController.sampleClockPositionMs()
            val speed = active.speed.coerceAtLeast(0.01f)
            val sourceOffset = (playerPos - active.sourceStartMs).coerceAtLeast(0L)
            val offsetInClip = (sourceOffset / speed).toLong()
            val calculatedTimeline = active.timelineStartMs + offsetInClip
            if (calculatedTimeline >= active.timelineStartMs + active.durationMs) handleClipEnd(active)
            else publishPosition(calculatedTimeline.coerceIn(0L, currentTimeline.totalDurationMs))
          } else {
            val next = (_timelinePositionMs.value + SYNC_INTERVAL_MS).coerceAtMost(currentTimeline.totalDurationMs)
            publishPosition(next)
            val nextClip = findClipAt(next)
            if (nextClip != null && nextClip.id != active?.id) {
              activeClip = nextClip
              onClipTransition(nextClip, next, true)
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

  /**
   * Called by Media3 when the currently loaded source item reaches STATE_ENDED.
   * An individual source item ending must transition to the next project clip
   * instead of ending the whole timeline preview.
   */
  fun handlePlayerEnded() {
    val endedClip = activeClip ?: findClipAt(_timelinePositionMs.value)
    val boundary = endedClip?.let { it.timelineStartMs + it.durationMs } ?: _timelinePositionMs.value
    val nextClip = currentTimeline.videoClips
      .asSequence()
      .filter { it.timelineStartMs >= boundary }
      .sortedBy { it.timelineStartMs }
      .firstOrNull()

    if (nextClip == null) {
      finishPlayback()
      return
    }

    activeClip = nextClip
    publishPosition(nextClip.timelineStartMs)
    onClipTransition(nextClip, nextClip.timelineStartMs, true)
  }

  private fun handleClipEnd(endedClip: VideoClip) {
    val boundary = endedClip.timelineStartMs + endedClip.durationMs
    val nextClip = currentTimeline.videoClips
      .asSequence()
      .filter { it.timelineStartMs >= boundary }
      .sortedBy { it.timelineStartMs }
      .firstOrNull()

    if (nextClip == null) {
      finishPlayback()
      return
    }

    activeClip = nextClip
    publishPosition(nextClip.timelineStartMs)
    onClipTransition(nextClip, nextClip.timelineStartMs, true)
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
