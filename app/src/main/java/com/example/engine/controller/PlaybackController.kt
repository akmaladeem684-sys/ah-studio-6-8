package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Single owner of Media3 commands and the shared master timeline position. */
class PlaybackController(
  context: Context,
  private val onTimelinePositionChanged: (Long) -> Unit = {},
  private val onPlaybackEnded: () -> Unit = {},
  private val onPlayerError: (PlaybackException) -> Unit = {}
) {
  companion object {
    private const val TAG = "PlaybackController"
    private const val LOAD_SEEK_THRESHOLD_MS = 100L
    private const val PLAY_SEEK_THRESHOLD_MS = 50L
  }

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
  private val masterClock = MasterPlaybackClock(scope)
  private val commandGeneration = AtomicLong(0L)
  private val commandMutex = Mutex()
  private val _state = MutableStateFlow(EnginePlaybackState.IDLE)
  val state: StateFlow<EnginePlaybackState> = _state.asStateFlow()
  val positionMs: StateFlow<Long> = masterClock.positionMs
  val timelinePositionMs: StateFlow<Long> = positionMs
  val isPlaying: StateFlow<Boolean> = masterClock.isPlaying
  private val _lastCommandAtMs = MutableStateFlow(0L)
  val lastCommandAtMs: StateFlow<Long> = _lastCommandAtMs.asStateFlow()

  private lateinit var _playbackManager: PlaybackManager
  val playbackManager: PlaybackManager get() = _playbackManager
  val player get() = playbackManager.player
  private var currentLoadedUri: String? = null
  private var disposed = false
  private var pendingCommand: Job? = null

  init {
    _playbackManager = PlaybackManager(
      context = appContext,
      onPlaybackStateChanged = { playbackState ->
        if (!disposed) when (playbackState) {
          Player.STATE_IDLE -> _state.value = EnginePlaybackState.IDLE
          Player.STATE_BUFFERING -> _state.value = EnginePlaybackState.BUFFERING
          Player.STATE_READY -> _state.value = if (playbackManager.isPlaying) EnginePlaybackState.PLAYING else EnginePlaybackState.READY
          Player.STATE_ENDED -> {
            scope.launch { masterClock.pause() }
            _state.value = EnginePlaybackState.COMPLETED
            onPlaybackEnded()
          }
        }
      },
      onIsPlayingChanged = { playing ->
        if (!disposed) {
          scope.launch {
            if (playing) masterClock.play(masterClock.positionMs.value) else masterClock.pause()
          }
          _state.value = if (playing) EnginePlaybackState.PLAYING else EnginePlaybackState.PAUSED
        }
      },
      onPlayerError = { error ->
        scope.launch { masterClock.pause() }
        if (!disposed) _state.value = EnginePlaybackState.ERROR
        onPlayerError(error)
      }
    )
  }

  val currentPosition: Long get() = positionMs.value
  val duration: Long get() = if (disposed) 0L else playbackManager.duration
  val bufferedPosition: Long get() = if (disposed) 0L else playbackManager.bufferedPosition

  /** Loads a URI only when it is actually different; repeated calls never reset the decoder. */
  fun loadMedia(uri: Uri, startPosMs: Long = 0L, autoPlay: Boolean = false) = enqueue("load") {
    if (disposed) return@enqueue
    val normalized = normalizeUri(uri)
    val key = normalized.toString()
    val alreadyLoaded = key == currentLoadedUri && playbackManager.playbackState != Player.STATE_IDLE

    if (alreadyLoaded) {
      // Loading is not a seek command. Only correct a materially different position.
      if (abs(playbackManager.currentPosition - startPosMs) > LOAD_SEEK_THRESHOLD_MS) {
        masterClock.seekTo(startPosMs)
        playbackManager.seekTo(startPosMs)
      }
      if (autoPlay && !playbackManager.isPlaying) playbackManager.play()
      return@enqueue
    }

    currentLoadedUri = key
    _state.value = EnginePlaybackState.PREPARING
    masterClock.seekTo(startPosMs)
    playbackManager.loadMedia(normalized, startPosMs, autoPlay)
    // Timeline audio is rendered by the centralized mixer, not Media3's audio sink.
    playbackManager.player.volume = 0f
  }

  /** Resumes without reloading or seeking unless the target is materially different. */
  fun handlePlayPress(targetPositionMs: Long) = enqueue("play") {
    if (disposed || masterClock.isPlaying.value) return@enqueue
    val target = targetPositionMs.coerceAtLeast(0L)
    val current = playbackManager.currentPosition
    if (abs(current - target) > PLAY_SEEK_THRESHOLD_MS) {
      playbackManager.seekTo(target)
      masterClock.seekTo(target)
    } else {
      masterClock.seekTo(current)
    }
    if (playbackManager.playbackState == Player.STATE_IDLE && playbackManager.player.mediaItemCount > 0) {
      playbackManager.prepare()
    }
    playbackManager.play()
    masterClock.play(masterClock.positionMs.value)
    _state.value = EnginePlaybackState.PLAYING
  }

  fun handlePausePress() = enqueue("pause") {
    if (disposed || !masterClock.isPlaying.value) return@enqueue
    masterClock.pause()
    if (playbackManager.isPlaying) playbackManager.pause()
    _state.value = EnginePlaybackState.PAUSED
  }

  fun play() = handlePlayPress(positionMs.value)
  fun pause() = handlePausePress()

  fun seekTo(positionMs: Long, resumeAfter: Boolean = false, exact: Boolean = false, generation: Long = commandGeneration.incrementAndGet()) = enqueue("seek#$generation") {
    if (disposed || generation != commandGeneration.get()) return@enqueue
    val target = positionMs.coerceAtLeast(0L)
    val wasPlaying = masterClock.isPlaying.value
    _state.value = EnginePlaybackState.SEEKING
    if (abs(playbackManager.currentPosition - target) > PLAY_SEEK_THRESHOLD_MS || exact) {
      masterClock.seekTo(target)
      if (exact) playbackManager.seekToExact(target) else playbackManager.seekTo(target)
    } else {
      masterClock.seekTo(target)
    }
    if (resumeAfter || wasPlaying) {
      if (!playbackManager.isPlaying) playbackManager.play()
      masterClock.play(target)
      _state.value = EnginePlaybackState.PLAYING
    } else {
      masterClock.pause()
      _state.value = EnginePlaybackState.PAUSED
    }
  }

  fun invalidatePendingSeeks(): Long = commandGeneration.incrementAndGet()
  fun setPlaybackSpeed(speed: Float) = enqueue("speed") { if (!disposed) playbackManager.setPlaybackSpeed(speed) }
  fun setVolume(volume: Float) = enqueue("volume") { if (!disposed) playbackManager.setVolume(volume) }
  fun setMuted(muted: Boolean) = enqueue("mute") { if (!disposed) playbackManager.setMuted(muted) }
  fun setSurface(surface: Surface?) = enqueue("surface") { if (!disposed) playbackManager.setSurface(surface) }
  fun clearSurface() = enqueue("clearSurface") { if (!disposed) playbackManager.clearSurface() }
  fun setRepeatMode(mode: Int) = enqueue("repeat") { if (!disposed) playbackManager.player.repeatMode = mode }

  /** External timeline projection must not seek Media3 during normal playback. */
  fun updateTimelinePosition(positionMs: Long) {
    if (!disposed) {
      scope.launch {
        masterClock.seekTo(positionMs.coerceAtLeast(0L))
        onTimelinePositionChanged(masterClock.positionMs.value)
      }
    }
  }

  fun sampleClockPositionMs(): Long = positionMs.value

  fun release() {
    if (disposed) return
    disposed = true
    pendingCommand?.cancel()
    scope.cancel()
    playbackManager.release()
    _state.value = EnginePlaybackState.RELEASED
  }

  private fun enqueue(name: String, block: suspend () -> Unit) {
    if (disposed) return
    pendingCommand = scope.launch {
      commandMutex.withLock {
        if (disposed) return@withLock
        _lastCommandAtMs.value = SystemClock.elapsedRealtime()
        try { block() } catch (t: Throwable) {
          if (!disposed) {
            _state.value = EnginePlaybackState.ERROR
            android.util.Log.e(TAG, "Command $name failed", t)
          }
        }
      }
    }
  }

  private fun normalizeUri(uri: Uri): Uri = when {
    uri.scheme == "asset" -> {
      var path = uri.path ?: ""
      if (path.startsWith("/")) path = path.substring(1)
      if (path.isEmpty()) path = uri.authority ?: ""
      Uri.parse("asset:///$path")
    }
    uri.scheme == null || uri.scheme == "file" -> {
      val path = uri.path ?: uri.toString()
      val file = java.io.File(path)
      if (file.exists()) Uri.fromFile(file) else uri
    }
    else -> uri
  }
}
