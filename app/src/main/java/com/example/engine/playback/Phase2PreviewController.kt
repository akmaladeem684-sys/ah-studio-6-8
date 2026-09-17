package com.example.engine.playback

import android.content.Context
import android.net.Uri
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Central preview coordinator using the existing Media3/Surface pipeline. */
class Phase2PreviewController(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(Dispatchers.Main.immediate)
  private val seekGeneration = AtomicLong(0L)
  private var seekJob: Job? = null
  private var timeline = Timeline()
  private var activeClip: VideoClip? = null
  private var positionMs = 0L
  private var loopStartMs: Long? = null
  private var loopEndMs: Long? = null

  val playback = PlaybackManager(appContext)
  val stateMachine = PlaybackStateMachine()
  val masterClock = MasterTimelineClock()
  val state: StateFlow<PreviewPlaybackState> = stateMachine.state

  private val _positionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _positionMs.asStateFlow()

  fun updateTimeline(value: Timeline) {
    timeline = value
    positionMs = positionMs.coerceIn(0L, value.totalDurationMs.coerceAtLeast(0L))
    _positionMs.value = positionMs
    activeClip = findClip(positionMs)
  }

  fun play() {
    if (timeline.totalDurationMs <= 0L) return
    if (positionMs >= timeline.totalDurationMs) {
      seek(0L)
      return
    }
    val clip = findClip(positionMs) ?: return
    if (!MediaRelinkManager.isRealPlayableMedia(appContext, clip.uri)) {
      stateMachine.transition(PreviewPlaybackState.ERROR)
      return
    }
    activeClip = clip
    stateMachine.transition(PreviewPlaybackState.LOADING)
    playback.loadMedia(Uri.parse(clip.uri), clip.timelineToSourceMs(positionMs), autoPlay = false)
    playback.setPlaybackSpeed(clip.speed)
    playback.setVolume(if (clip.isMuted) 0f else clip.volume)
    playback.play()
    masterClock.setSpeed(clip.speed.toDouble())
    masterClock.start(positionMs * 1000L)
    stateMachine.transition(PreviewPlaybackState.PLAYING)
  }

  fun pause() {
    val p = masterClock.pause() / 1000L
    positionMs = p.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    _positionMs.value = positionMs
    playback.pause()
    stateMachine.transition(PreviewPlaybackState.PAUSED)
  }

  fun stop() {
    playback.pause()
    masterClock.pause()
    masterClock.seek(0L)
    positionMs = 0L
    _positionMs.value = 0L
    stateMachine.transition(PreviewPlaybackState.STOPPED)
  }

  fun seek(targetMs: Long) {
    val generation = seekGeneration.incrementAndGet()
    val resume = stateMachine.state.value == PreviewPlaybackState.PLAYING || masterClock.isRunning()
    seekJob?.cancel()
    seekJob = scope.launch {
      stateMachine.transition(PreviewPlaybackState.SEEKING)
      delay(8L)
      if (generation != seekGeneration.get()) return@launch
      val target = targetMs.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
      positionMs = target
      _positionMs.value = target
      masterClock.seek(target * 1000L)
      activeClip = findClip(target)
      val clip = activeClip
      if (clip != null && clip.isVideo && MediaRelinkManager.isRealPlayableMedia(appContext, clip.uri)) {
        playback.loadMedia(Uri.parse(clip.uri), clip.timelineToSourceMs(target), autoPlay = false)
        playback.setPlaybackSpeed(clip.speed)
        playback.setVolume(if (clip.isMuted) 0f else clip.volume)
        playback.seekTo(clip.timelineToSourceMs(target))
        if (resume) {
          playback.play()
          masterClock.setSpeed(clip.speed.toDouble())
          masterClock.start(target * 1000L)
          stateMachine.transition(PreviewPlaybackState.PLAYING)
        } else {
          stateMachine.transition(PreviewPlaybackState.PAUSED)
        }
      } else {
        playback.pause()
        stateMachine.transition(PreviewPlaybackState.PAUSED)
      }
    }
  }

  fun scrubTo(targetMs: Long) = seek(targetMs)

  fun stepFrame(forward: Boolean, fps: Int = 30) {
    pause()
    val safeFps = fps.coerceAtLeast(1)
    val currentFrame = (positionMs * safeFps) / 1000L
    val targetFrame = (currentFrame + if (forward) 1L else -1L).coerceAtLeast(0L)
    seek((targetFrame * 1000L) / safeFps)
  }

  fun setPlaybackSpeed(speed: Float) {
    val safe = speed.coerceIn(0.25f, 2f)
    playback.setPlaybackSpeed(safe)
    masterClock.setSpeed(safe.toDouble())
  }

  fun setLoop(startMs: Long?, endMs: Long?) {
    if (startMs == null || endMs == null || endMs <= startMs) {
      loopStartMs = null
      loopEndMs = null
    } else {
      loopStartMs = startMs.coerceAtLeast(0L)
      loopEndMs = endMs.coerceAtMost(timeline.totalDurationMs)
    }
  }

  fun pollMasterClock() {
    if (!masterClock.isRunning()) return
    val target = masterClock.positionUs() / 1000L
    val end = loopEndMs
    if (end != null && target >= end) {
      seek(loopStartMs ?: 0L)
      return
    }
    positionMs = target.coerceAtMost(timeline.totalDurationMs)
    _positionMs.value = positionMs
  }

  fun release() {
    seekJob?.cancel()
    scope.cancel()
    masterClock.pause()
    playback.release()
    stateMachine.reset()
  }

  private fun findClip(position: Long): VideoClip? = timeline.videoClips.firstOrNull {
    position >= it.timelineStartMs && position < it.timelineStartMs + it.durationMs
  }
}
