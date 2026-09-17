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

/** Timeline projection layer; never manipulates MediaCodec/ExoPlayer directly. */
class TimelineSyncManager(
  private val playbackController: PlaybackController,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {
  companion object { private const val TAG = "TimelineSyncManager"; private const val TICK_MS = 16L }
  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private var syncJob: Job? = null
  private var currentTimeline = Timeline()
  private var activeClip: VideoClip? = null
  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  fun updateTimeline(timeline: Timeline) { currentTimeline = timeline; setPosition(_timelinePositionMs.value) }
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
            val source = playbackController.sampleClockPositionMs()
            val speed = active.speed.coerceAtLeast(0.01f)
            val timelinePos = active.timelineStartMs + ((source - active.sourceStartMs).coerceAtLeast(0L) / speed).toLong()
            if (timelinePos >= active.timelineStartMs + active.durationMs) handleClipEnd(active) else publish(timelinePos)
          }
        }
        delay(TICK_MS)
      }
    }
  }

  private fun publish(pos: Long) {
    val bounded = pos.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    if (_timelinePositionMs.value != bounded) {
      _timelinePositionMs.value = bounded
      playbackController.updateTimelinePosition(bounded)
      onTimelinePositionUpdated(bounded)
    }
  }

  private fun handleClipEnd(ended: VideoClip) {
    val next = ended.timelineStartMs + ended.durationMs
    if (next >= currentTimeline.totalDurationMs) {
      Log.d(TAG, "Timeline playback completed")
      playbackController.pause()
      publish(0L)
      onPlaybackEnded()
    } else {
      activeClip = findClipAt(next)
      publish(next)
      onClipTransition(activeClip, next)
    }
  }

  fun findClipAt(positionMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs
  }
  fun stopSyncLoop() { syncJob?.cancel(); syncJob = null }
  fun release() { stopSyncLoop(); scope.cancel() }
}
