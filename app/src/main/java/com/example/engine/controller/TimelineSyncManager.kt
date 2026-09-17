package com.example.engine.controller

import android.util.Log
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Projection layer for the editor timeline.
 *
 * PlaybackController owns the master clock. This class observes its position flow and never
 * advances time, samples a second clock, or runs an independent periodic synchronization loop.
 */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {
  companion object { private const val TAG = "TimelineSyncManager" }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var positionCollectionJob: Job? = null
  private var currentTimeline: Timeline = Timeline()
  private var activeClip: VideoClip? = null
  private var lastTransitionClipId: String? = null
  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    val bounded = _timelinePositionMs.value.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    publishPosition(bounded)
  }

  fun setActiveClip(clip: VideoClip?) {
    activeClip = clip
    lastTransitionClipId = clip?.id
  }

  /** Explicit user seek/edit operation; normal playback must use the master position flow. */
  fun setPosition(positionMs: Long) {
    val bounded = positionMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    playbackController.updateTimelinePosition(bounded)
    publishPosition(bounded)
  }

  /** Starts one flow collector; there is deliberately no 16 ms timer or advancement loop. */
  fun startSyncLoop() {
    stopSyncLoop()
    positionCollectionJob = scope.launch {
      playbackController.positionMs.collectLatest { position ->
        if (!playbackController.isPlaying.value) return@collectLatest
        projectMasterPosition(position)
      }
    }
  }

  private fun projectMasterPosition(positionMs: Long) {
    val total = currentTimeline.totalDurationMs.coerceAtLeast(0L)
    if (total > 0L && positionMs >= total) {
      finishPlayback()
      return
    }

    val bounded = positionMs.coerceIn(0L, total)
    val previous = activeClip
    val next = findClipAt(bounded)
    publishPosition(bounded)

    if (next?.id != previous?.id) {
      activeClip = next
      if (next != null && lastTransitionClipId != next.id) {
        lastTransitionClipId = next.id
        onClipTransition(next, bounded)
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
    stopSyncLoop()
    playbackController.pause()
    publishPosition(end)
    activeClip = findClipAt((end - 1L).coerceAtLeast(0L))
    lastTransitionClipId = activeClip?.id
    onPlaybackEnded()
  }

  fun findClipAt(positionMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs
  }

  fun stopSyncLoop() {
    positionCollectionJob?.cancel()
    positionCollectionJob = null
  }

  fun release() {
    stopSyncLoop()
    scope.cancel()
  }
}
