package com.example.engine.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.composition.ColorFilterGenerator
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Production-ready single coordinator for video playback, hardware decoding,
 * GPU composition rendering, surface lifecycles, and timeline synchronization.
 */
class CustomVideoEngineController(
  private val context: Context,
  private val onTimelinePositionChanged: (Long) -> Unit,
  private val onPlaybackEnded: () -> Unit = {}
) {

  companion object {
    private const val TAG = "CustomVideoEngineCtrl"
  }

  // Coroutine scope for controller lifecycle
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  // Sub-managers
  val decoderManager = DecoderManager()
  val renderCacheManager = RenderCacheManager()
  private val compositionEngine = VideoCompositionEngine(context)
  val gpuRenderManager = GpuRenderManager(context, compositionEngine, renderCacheManager)

  val playbackManager = PlaybackManager(
    context = context,
    onPlaybackStateChanged = { state -> handlePlaybackStateChanged(state) },
    onIsPlayingChanged = { isPlaying -> handleIsPlayingChanged(isPlaying) },
    onPlayerError = { error -> handlePlayerError(error) }
  )

  val surfaceManager = SurfaceManager(playbackManager)

  val timelineSyncManager = TimelineSyncManager(
    playbackManager = playbackManager,
    onTimelinePositionUpdated = { posMs ->
      _engineState.value = _engineState.value.copy(
        currentPosition = posMs,
        isPlaying = playbackManager.isPlaying
      )
      onTimelinePositionChanged(posMs)
    },
    onClipTransition = { clip, timelinePosMs ->
      handleClipTransition(clip, timelinePosMs)
    },
    onPlaybackEnded = {
      _engineState.value = _engineState.value.copy(
        playbackState = EnginePlaybackState.PAUSED,
        isPlaying = false,
        currentPosition = 0L
      )
      onPlaybackEnded()
    }
  )

  // Unified reactive state
  private val _engineState = MutableStateFlow(
    VideoEngineState(
      playbackState = EnginePlaybackState.IDLE,
      decoderState = decoderManager.decoderState,
      renderingState = RenderingState.IDLE
    )
  )
  val engineState: StateFlow<VideoEngineState> = _engineState.asStateFlow()

  // Timeline tracking
  private var currentTimeline: Timeline = Timeline()
  private var activeClip: VideoClip? = null
  private var loadedClipId: String? = null
  private var loadedUri: String? = null
  private var currentPosMs: Long = 0L

  // Scrubbing & Coalescing
  private var isScrubbingMode = false
  private var wasPlayingBeforeScrub = false
  private val seekSequence = AtomicLong(0L)
  private var coalescedSeekJob: Job? = null

  // Trim Preview Mode
  private var isTrimPreviewMode = false
  private var trimRangeStartMs = 0L
  private var trimRangeEndMs = 0L
  private var trimLoop = false
  private val _trimPlaybackPositionMs = MutableStateFlow(0L)
  val trimPlaybackPositionMs: StateFlow<Long> = _trimPlaybackPositionMs.asStateFlow()

  val isScrubbing: Boolean get() = isScrubbingMode
  val isPlaying: Boolean get() = _engineState.value.isPlaying || playbackManager.isPlaying
  val currentPosition: Long get() = _engineState.value.currentPosition

  init {
    surfaceManager.onSurfaceAvailabilityChanged = { available ->
      _engineState.value = _engineState.value.copy(surfaceAvailable = available)
      if (available && activeClip != null && !isPlaying) {
        // Restore last frame immediately upon surface recreation
        ensureClipLoaded(activeClip!!)
        val sourcePosMs = activeClip!!.timelineToSourceMs(currentPosMs)
        playbackManager.seekTo(sourcePosMs)
      }
    }
  }

  fun updateTimeline(timeline: Timeline) {
    this.currentTimeline = timeline
    timelineSyncManager.updateTimeline(timeline)
    _engineState.value = _engineState.value.copy(
      duration = timeline.totalDurationMs
    )

    val boundedPos = currentPosMs.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = boundedPos

    val clip = timelineSyncManager.findClipAt(boundedPos)
    activeClip = clip
    timelineSyncManager.setActiveClip(clip)

    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      ensureClipLoaded(clip)
      val sourcePosMs = clip.timelineToSourceMs(boundedPos)
      playbackManager.seekTo(sourcePosMs)
      playbackManager.setPlaybackSpeed(clip.speed)
      playbackManager.setVolume(if (clip.isMuted) 0f else clip.volume)
      _engineState.value = _engineState.value.copy(
        playbackState = if (playbackManager.isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.READY,
        isReady = true
      )
    } else {
      if (clip == null) {
        playbackManager.pause()
      }
      _engineState.value = _engineState.value.copy(
        playbackState = EnginePlaybackState.READY,
        isReady = true
      )
    }
  }

  fun seekTo(timelinePosMs: Long) {
    val boundedPos = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = boundedPos
    timelineSyncManager.setPosition(boundedPos)

    val clip = timelineSyncManager.findClipAt(boundedPos)
    activeClip = clip
    timelineSyncManager.setActiveClip(clip)

    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      ensureClipLoaded(clip)
      val sourcePosMs = clip.timelineToSourceMs(boundedPos)
      playbackManager.seekTo(sourcePosMs)
      playbackManager.setPlaybackSpeed(clip.speed)
    }

    _engineState.value = _engineState.value.copy(
      currentPosition = boundedPos,
      playbackState = if (isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED
    )
  }

  fun startScrubbing() {
    isScrubbingMode = true
    wasPlayingBeforeScrub = isPlaying
    if (wasPlayingBeforeScrub) {
      pause()
    }
  }

  fun scrubTo(timelinePosMs: Long) {
    val boundedPos = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs.coerceAtLeast(0L))
    currentPosMs = boundedPos
    timelineSyncManager.setPosition(boundedPos)

    val clip = timelineSyncManager.findClipAt(boundedPos)
    activeClip = clip
    timelineSyncManager.setActiveClip(clip)

    val seq = seekSequence.incrementAndGet()
    coalescedSeekJob?.cancel()
    coalescedSeekJob = scope.launch {
      delay(8L) // 120Hz coalescing gate
      if (seq == seekSequence.get()) {
        if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
          ensureClipLoaded(clip)
          val sourcePosMs = clip.timelineToSourceMs(boundedPos)
          playbackManager.seekTo(sourcePosMs)
        }
      }
    }
  }

  fun stopScrubbing(finalPosMs: Long) {
    isScrubbingMode = false
    coalescedSeekJob?.cancel()
    seekTo(finalPosMs)
    if (wasPlayingBeforeScrub) {
      play()
    }
  }

  fun play() {
    if (currentTimeline.totalDurationMs <= 0L) return

    if (currentPosMs >= currentTimeline.totalDurationMs) {
      currentPosMs = 0L
      seekTo(0L)
    }

    val clip = timelineSyncManager.findClipAt(currentPosMs)
    activeClip = clip
    timelineSyncManager.setActiveClip(clip)

    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      ensureClipLoaded(clip)
      val sourcePosMs = clip.timelineToSourceMs(currentPosMs)
      playbackManager.seekTo(sourcePosMs)
      playbackManager.setPlaybackSpeed(clip.speed)
      playbackManager.play()
    } else {
      playbackManager.pause()
    }

    timelineSyncManager.startSyncLoop()
    _engineState.value = _engineState.value.copy(
      playbackState = EnginePlaybackState.PLAYING,
      isPlaying = true
    )
  }

  fun pause() {
    timelineSyncManager.stopSyncLoop()
    playbackManager.pause()

    val active = activeClip
    if (active != null && active.isVideo && isPlayableInPlayer(active.uri)) {
      val playerPos = playbackManager.currentPosition
      val speed = active.speed.coerceAtLeast(0.01f)
      val offsetInClip = ((playerPos - active.sourceStartMs) / speed).toLong()
      val calculatedTimeline = (active.timelineStartMs + offsetInClip)
        .coerceIn(active.timelineStartMs, active.timelineStartMs + active.durationMs)
      currentPosMs = calculatedTimeline
      timelineSyncManager.setPosition(calculatedTimeline)
    }

    _engineState.value = _engineState.value.copy(
      playbackState = EnginePlaybackState.PAUSED,
      isPlaying = false,
      currentPosition = currentPosMs
    )
  }

  fun togglePlayPause() {
    if (isPlaying || _engineState.value.isPlaying) {
      pause()
    } else {
      play()
    }
  }

  fun attachSurfaceView(surfaceView: SurfaceView) {
    surfaceManager.attachSurfaceView(surfaceView)
  }

  fun attachTextureView(textureView: TextureView) {
    surfaceManager.attachTextureView(textureView)
  }

  fun invalidateClip(clipId: String) {
    renderCacheManager.invalidateClip(clipId)
    gpuRenderManager.invalidateClip(clipId)
  }

  fun invalidateAll() {
    renderCacheManager.clear()
    gpuRenderManager.invalidateAll()
  }

  private fun handleClipTransition(nextClip: VideoClip?, nextTimelinePos: Long) {
    if (nextClip != null && nextClip.isVideo && isPlayableInPlayer(nextClip.uri)) {
      ensureClipLoaded(nextClip)
      val sourcePosMs = nextClip.timelineToSourceMs(nextTimelinePos)
      playbackManager.seekTo(sourcePosMs)
      playbackManager.setPlaybackSpeed(nextClip.speed)
      playbackManager.setVolume(if (nextClip.isMuted) 0f else nextClip.volume)
      if (isPlaying || _engineState.value.isPlaying) {
        playbackManager.play()
      }
    } else {
      playbackManager.pause()
    }
  }

  private fun ensureClipLoaded(clip: VideoClip) {
    val relinkedUri = clip.uri
    if (clip.id == loadedClipId && relinkedUri == loadedUri && playbackManager.playbackState != Player.STATE_IDLE) {
      return
    }

    loadedClipId = clip.id
    loadedUri = relinkedUri

    val uri = try {
      Uri.parse(relinkedUri)
    } catch (e: Exception) {
      Log.e(TAG, "Invalid URI for clip: ${clip.uri}", e)
      return
    }

    val sourceStartMs = clip.sourceStartMs.coerceAtLeast(0L)
    playbackManager.loadMedia(uri, sourceStartMs, autoPlay = isPlaying)
    _engineState.value = _engineState.value.copy(
      playbackState = EnginePlaybackState.PREPARING
    )
  }

  private fun isPlayableInPlayer(uriString: String): Boolean {
    return MediaRelinkManager.isRealPlayableMedia(context, uriString)
  }

  private fun handlePlaybackStateChanged(state: Int) {
    when (state) {
      Player.STATE_READY -> {
        _engineState.value = _engineState.value.copy(
          playbackState = if (playbackManager.isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.READY,
          isReady = true,
          error = null
        )
      }
      Player.STATE_BUFFERING -> {
        _engineState.value = _engineState.value.copy(
          bufferedPosition = playbackManager.bufferedPosition
        )
      }
      Player.STATE_ENDED -> {
        _engineState.value = _engineState.value.copy(
          playbackState = EnginePlaybackState.PAUSED,
          isPlaying = false
        )
      }
      Player.STATE_IDLE -> {
        // Idle state
      }
    }
  }

  private fun handleIsPlayingChanged(isPlaying: Boolean) {
    _engineState.value = _engineState.value.copy(
      isPlaying = isPlaying,
      playbackState = if (isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED
    )
  }

  private fun handlePlayerError(error: PlaybackException) {
    Log.e(TAG, "Player exception: ${error.errorCodeName}", error)
    val handled = decoderManager.handleCodecError(error)
    _engineState.value = _engineState.value.copy(
      decoderState = decoderManager.decoderState,
      error = error.message ?: error.errorCodeName
    )
    if (handled) {
      // Automatic error recovery: reload active clip with fallback settings
      activeClip?.let { ensureClipLoaded(it) }
    }
  }

  fun recoverFromError() {
    decoderManager.reset()
    _engineState.value = _engineState.value.copy(
      error = null,
      decoderState = decoderManager.decoderState
    )
    activeClip?.let { ensureClipLoaded(it) }
    seekTo(currentPosMs)
  }

  fun release() {
    timelineSyncManager.release()
    playbackManager.release()
    surfaceManager.release()
    gpuRenderManager.release()
    renderCacheManager.clear()
    scope.cancel()
    _engineState.value = _engineState.value.copy(
      playbackState = EnginePlaybackState.RELEASED,
      isPlaying = false
    )
    Log.d(TAG, "CustomVideoEngineController fully released")
  }
}
