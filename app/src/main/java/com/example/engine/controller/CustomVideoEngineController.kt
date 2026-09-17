package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Project-level preview coordinator; PlaybackController is the only player command owner. */
class CustomVideoEngineController(
  private val context: Context,
  private val onTimelinePositionChanged: (Long) -> Unit,
  private val onPlaybackEnded: () -> Unit = {}
) {
  companion object { private const val TAG = "CustomVideoEngineCtrl" }

  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  val decoderManager = DecoderManager()
  val renderCacheManager = RenderCacheManager()
  private val compositionEngine = VideoCompositionEngine(context)
  val gpuRenderManager = GpuRenderManager(context, compositionEngine, renderCacheManager)

  val playbackController = PlaybackController(
    context = context,
    onTimelinePositionChanged = onTimelinePositionChanged,
    onPlaybackEnded = onPlaybackEnded,
    onPlayerError = { error -> handlePlayerError(error) }
  )

  val playbackManager: PlaybackManager get() = playbackController.playbackManager
  val player get() = playbackController.player
  val surfaceManager = SurfaceManager(playbackController)

  val timelineSyncManager = TimelineSyncManager(
    playbackController = playbackController,
    onTimelinePositionUpdated = { posMs ->
      _engineState.value = _engineState.value.copy(currentPosition = posMs, isPlaying = playbackController.isPlaying)
      onTimelinePositionChanged(posMs)
    },
    onClipTransition = { clip, pos -> handleClipTransition(clip, pos) },
    onPlaybackEnded = {
      _engineState.value = _engineState.value.copy(playbackState = EnginePlaybackState.COMPLETED, isPlaying = false)
      onPlaybackEnded()
    }
  )

  private val _engineState = MutableStateFlow(
    VideoEngineState(
      playbackState = EnginePlaybackState.IDLE,
      decoderState = decoderManager.decoderState,
      renderingState = RenderingState.IDLE
    )
  )
  val engineState: StateFlow<VideoEngineState> = _engineState.asStateFlow()

  private var currentTimeline = Timeline()
  private var activeClip: VideoClip? = null
  private var loadedClipId: String? = null
  private var loadedUri: String? = null
  private var currentPosMs = 0L
  private var isScrubbingMode = false
  private var wasPlayingBeforeScrub = false
  private val seekSequence = AtomicLong(0L)
  private var coalescedSeekJob: Job? = null
