package com.example.engine.playback

import android.content.Context
import android.graphics.ColorMatrix
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.effect.DefaultVideoFrameProcessor
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.composition.ColorFilterGenerator
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Video playback engine.
 *
 * Important synchronization rule: timeline state updates must not continuously seek the
 * decoder. During playback the ExoPlayer clock is authoritative; timeline updates only seek
 * when the requested source position is materially different from the current decoder position.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackEngine(
  private val context: Context,
  private val onTimelinePositionChanged: (Long) -> Unit,
  private val onPlaybackEnded: () -> Unit,
  private val proxyEngine: ProxyMediaEngine? = null
) {
  companion object {
    private const val TAG = "VideoPlaybackEngine"
    private const val FRAME_INTERVAL_60FPS_MS = 16L
    private const val SEEK_SYNC_TOLERANCE_MS = 50L
  }

  val engineController: com.example.engine.controller.CustomVideoEngineController =
    com.example.engine.controller.CustomVideoEngineController(
      context = context,
      onTimelinePositionChanged = onTimelinePositionChanged,
      onPlaybackEnded = onPlaybackEnded
    )

  val player: ExoPlayer = engineController.playbackManager.player

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

  private val _activeClip = MutableStateFlow<VideoClip?>(null)
  val activeClip: StateFlow<VideoClip?> = _activeClip.asStateFlow()

  private val _playerError = MutableStateFlow<String?>(null)
  val playerError: StateFlow<String?> = _playerError.asStateFlow()

  private val _trimPlaybackPositionMs = MutableStateFlow(0L)
  val trimPlaybackPositionMs: StateFlow<Long> = _trimPlaybackPositionMs.asStateFlow()

  private var currentTimeline: Timeline = Timeline()
  private var currentPosMs: Long = 0L
  private var loadedClipId: String? = null
  private var loadedUri: String? = null
  private var isSyncingFromPlayer = false

  private var playStartTimelineMs = 0L
  private var playStartSourceMs = 0L
  private var playStartRealtimeMs = 0L

  private var isScrubbingMode = false
  private var wasPlayingBeforeScrub = false
  private val seekSequence = AtomicLong(0L)
  private val pendingSeekPosUs = AtomicLong(-1L)
  private var coalescedSeekJob: Job? = null

  val isScrubbing: Boolean get() = isScrubbingMode

  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var progressSyncJob: Job? = null

  init {
    player.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) startProgressSync() else progressSyncJob?.cancel()
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) handleClipEnded()
      }

      override fun onPlayerError(error: PlaybackException) {
        // Never restart playback from an error callback. Restarting here can create a
        // play -> error -> stop -> play loop and repeated audio buffers/clicks.
        Log.e(TAG, "ExoPlayer playback error: [${error.errorCodeName}] ${error.message}", error)
        _playerError.value = error.message ?: error.errorCodeName
        _isPlaying.value = false
        progressSyncJob?.cancel()
        loadedClipId = null
        loadedUri = null
      }
    })
  }

  fun isPlayableInPlayer(uriString: String?): Boolean =
    MediaRelinkManager.isRealPlayableMedia(context, uriString)

  fun applyVideoFilter(colorMatrix: ColorMatrix?) {
    // GPU/preview composition remains owned by the existing composition pipeline.
  }

  private val overlayPlayers = java.util.concurrent.ConcurrentHashMap<String, ExoPlayer>()
  private val overlayLoadedUris = java.util.concurrent.ConcurrentHashMap<String, String>()

  fun getOverlayPlayer(clipId: String): ExoPlayer? = overlayPlayers[clipId]

  fun syncOverlayPlayers(posMs: Long) {
    if (isTrimPreviewMode) return
    val activeOverlays = currentTimeline.overlayClips.filter {
      it.isVideo && !it.isHidden && MediaRelinkManager.isRealPlayableMedia(context, it.uri)
    }
    val activeIds = activeOverlays.map { it.id }.toSet()

    overlayPlayers.keys.toList().forEach { id ->
      if (!activeIds.contains(id)) {
        overlayPlayers.remove(id)?.let { p ->
          try { p.stop(); p.release() } catch (e: Exception) { Log.w(TAG, "Failed to release overlay player $id", e) }
        }
        overlayLoadedUris.remove(id)
      }
    }

    for (overlay in activeOverlays) {
      val effectiveUri = proxyEngine?.getProxyUri(overlay) ?: overlay.uri
      var p = overlayPlayers[overlay.id]
      if (p == null) {
        try {
          p = ExoPlayer.Builder(
            context.applicationContext,
            DefaultRenderersFactory(context.applicationContext)
              .setEnableDecoderFallback(true)
              .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
          ).setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(1000, 5000, 200, 500).build()
          ).setSeekParameters(SeekParameters.CLOSEST_SYNC).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
          }
          overlayPlayers[overlay.id] = p
        } catch (e: Exception) {
          Log.w(TAG, "Failed to create overlay ExoPlayer for ${overlay.id}", e)
          continue
        }
      }

      val loaded = overlayLoadedUris[overlay.id]
      if (loaded != effectiveUri || p.mediaItemCount == 0 || p.playbackState == Player.STATE_IDLE) {
        try {
          val parsedUri = Uri.parse(effectiveUri)
          val normalizedUri = if (parsedUri.scheme == "asset") {
            var path = parsedUri.path ?: ""
            if (path.startsWith("/")) path = path.substring(1)
            if (path.isEmpty()) path = parsedUri.authority ?: ""
            Uri.parse("asset:///$path")
          } else if (parsedUri.scheme == null || parsedUri.scheme == "file") {
            val path = parsedUri.path ?: effectiveUri
            val f = java.io.File(path)
            if (f.exists()) Uri.fromFile(f) else parsedUri
          } else parsedUri
          p.setMediaItem(MediaItem.fromUri(normalizedUri))
          p.prepare()
          overlayLoadedUris[overlay.id] = effectiveUri
        } catch (e: Exception) {
          Log.w(TAG, "Failed to set media item for overlay ${overlay.id}", e)
        }
      }

      p.playbackParameters = PlaybackParameters(overlay.speed.coerceAtLeast(0.01f))
      p.volume = if (overlay.isMuted) 0f else overlay.volume
      val isTimeActive = posMs >= overlay.timelineStartMs && posMs < overlay.timelineStartMs + overlay.durationMs
      val targetSourceMs = overlay.timelineToSourceMs(posMs)
      if (isTimeActive) {
        if (_isPlaying.value) {
          val drift = kotlin.math.abs(p.currentPosition - targetSourceMs)
          if (drift > 100L || !p.isPlaying) {
            p.seekTo(targetSourceMs)
            p.play()
          }
        } else {
          if (p.isPlaying) p.pause()
          if (kotlin.math.abs(p.currentPosition - targetSourceMs) > SEEK_SYNC_TOLERANCE_MS) p.seekTo(targetSourceMs)
        }
      } else {
        if (p.isPlaying) p.pause()
        if (kotlin.math.abs(p.currentPosition - targetSourceMs) > SEEK_SYNC_TOLERANCE_MS) {
          p.seekTo(targetSourceMs.coerceIn(0L, overlay.durationMs))
        }
      }
    }
  }

  fun updateTimeline(timeline: Timeline) {
    currentTimeline = timeline
    currentPosMs = currentPosMs.coerceIn(0L, timeline.totalDurationMs.coerceAtLeast(0L))
    _currentPositionMs.value = currentPosMs
    val clip = findClipAt(currentPosMs)
    _activeClip.value = clip
    syncWithPosition(currentPosMs, forceReload = false)
    syncOverlayPlayers(currentPosMs)
  }

  fun startScrubbing() {
    if (_isPlaying.value || player.isPlaying) {
      wasPlayingBeforeScrub = true
      pause()
    } else wasPlayingBeforeScrub = false
    isScrubbingMode = true
  }

  fun stopScrubbing(finalPosMs: Long? = null) {
    coalescedSeekJob?.cancel()
    val targetPos = finalPosMs ?: currentPosMs
    isScrubbingMode = false
    val seq = proxyEngine?.nextSeekSequence() ?: seekSequence.incrementAndGet()
    val boundedPos = targetPos.coerceIn(0L, currentTimeline.totalDurationMs)
    currentPosMs = boundedPos
    _currentPositionMs.value = boundedPos
    val active = findClipAt(boundedPos)
    _activeClip.value = active
    if (active != null && active.isVideo) {
      val sourcePosMs = active.timelineToSourceMs(boundedPos)
      ensureClipLoaded(active)
      if (player.playbackState != Player.STATE_IDLE && kotlin.math.abs(player.currentPosition - sourcePosMs) > SEEK_SYNC_TOLERANCE_MS) {
        player.seekTo(sourcePosMs)
      }
      proxyEngine?.requestFrameAsync(active, sourcePosMs, seq) { _, _ -> }
    } else player.pause()
    if (wasPlayingBeforeScrub) {
      wasPlayingBeforeScrub = false
      play()
    }
  }

  fun scrubTo(timelinePosMs: Long) {
    if (!isScrubbingMode) startScrubbing()
    val seq = proxyEngine?.nextSeekSequence() ?: seekSequence.incrementAndGet()
    val boundedPos = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs)
    currentPosMs = boundedPos
    _currentPositionMs.value = boundedPos
    val active = findClipAt(boundedPos)
    _activeClip.value = active
    pendingSeekPosUs.set(active?.timelineToSourceMs(boundedPos) ?: -1L)
    scheduleCoalescedSeek(active, pendingSeekPosUs.get(), seq)
  }

  fun seekTo(timelinePosMs: Long) = seekTo(timelinePosMs, false)

  fun seekTo(timelinePosMs: Long, isScrubbing: Boolean) {
    if (isScrubbing) { scrubTo(timelinePosMs); return }
    isScrubbingMode = false
    val seq = proxyEngine?.nextSeekSequence() ?: seekSequence.incrementAndGet()
    val boundedPos = timelinePosMs.coerceIn(0L, currentTimeline.totalDurationMs)
    currentPosMs = boundedPos
    _currentPositionMs.value = boundedPos
    val active = findClipAt(boundedPos)
    _activeClip.value = active
    if (active != null && active.isVideo) {
      val sourcePosMs = active.timelineToSourceMs(boundedPos)
      coalescedSeekJob?.cancel()
      syncWithPosition(boundedPos, forceReload = false)
      proxyEngine?.requestFrameAsync(active, sourcePosMs, seq) { _, _ -> }
    } else player.pause()
  }

  private fun scheduleCoalescedSeek(clip: VideoClip?, targetSourcePosMs: Long, sequence: Long) {
    if (clip == null || targetSourcePosMs < 0L) return
    if (coalescedSeekJob?.isActive == true) return
    coalescedSeekJob = scope.launch {
      delay(FRAME_INTERVAL_60FPS_MS)
      val latest = pendingSeekPosUs.getAndSet(-1L)
      val currentSeq = proxyEngine?.getCurrentSequence() ?: seekSequence.get()
      if (latest >= 0L && sequence >= currentSeq && clip.id == findClipAt(currentPosMs)?.id) {
        ensureClipLoaded(clip)
        if (player.playbackState != Player.STATE_IDLE && kotlin.math.abs(player.currentPosition - latest) > SEEK_SYNC_TOLERANCE_MS) {
          player.seekTo(latest)
        }
        proxyEngine?.requestFrameAsync(clip, latest, sequence) { _, _ -> }
        proxyEngine?.prefetchFramesAround(clip, latest, 1500L)
      }
      coalescedSeekJob = null
    }
  }

  fun play() {
    _playerError.value = null
    if (currentTimeline.totalDurationMs <= 0L) return
    if (currentPosMs >= currentTimeline.totalDurationMs) {
      currentPosMs = 0L
      _currentPositionMs.value = 0L
      seekTo(0L)
      onTimelinePositionChanged(0L)
    }
    val clip = findClipAt(currentPosMs)
    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      val effectiveUri = proxyEngine?.getProxyUri(clip) ?: clip.uri
      val isLoaded = loadedClipId == clip.id && loadedUri == effectiveUri && player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE
      if (!isLoaded) ensureClipLoaded(clip)
      val sourcePosMs = clip.timelineToSourceMs(currentPosMs)
      if (kotlin.math.abs(player.currentPosition - sourcePosMs) > SEEK_SYNC_TOLERANCE_MS) player.seekTo(sourcePosMs)
      player.playbackParameters = PlaybackParameters(clip.speed.coerceAtLeast(0.01f))
      player.volume = if (clip.isMuted) 0f else clip.volume
      player.play()
      _isPlaying.value = true
      startProgressSync()
    } else {
      player.pause()
      startSyntheticPlaybackLoop()
    }
  }

  fun pause() {
    _isPlaying.value = false
    progressSyncJob?.cancel()
    player.pause()
    overlayPlayers.values.forEach { p -> try { p.pause() } catch (_: Exception) {} }
    syncOverlayPlayers(currentPosMs)
    val active = _activeClip.value
    if (active != null && active.isVideo && isPlayableInPlayer(active.uri) && player.playbackState == Player.STATE_READY) {
      val playerPos = player.currentPosition
      val speed = active.speed.coerceAtLeast(0.01f)
      val calculatedTimeline = (active.timelineStartMs + ((playerPos - active.sourceStartMs) / speed).toLong())
        .coerceIn(active.timelineStartMs, active.timelineStartMs + active.durationMs)
      if (kotlin.math.abs(calculatedTimeline - currentPosMs) < 300L) {
        currentPosMs = calculatedTimeline
        _currentPositionMs.value = calculatedTimeline
        onTimelinePositionChanged(calculatedTimeline)
      }
    }
  }

  fun togglePlayPause() {
    if (isTrimPreviewMode) { toggleTrimPlayPause(); return }
    if (_isPlaying.value || player.isPlaying) pause() else play()
  }

  private var isTrimPreviewMode = false
  private var trimPreviewClip: VideoClip? = null
  private var trimRangeStartMs = 0L
  private var trimRangeEndMs = 0L
  val isTrimPreview: Boolean get() = isTrimPreviewMode

  fun previewTrimRange(clip: VideoClip, startMs: Long, endMs: Long, loop: Boolean = true) {
    isTrimPreviewMode = true
    trimPreviewClip = clip
    trimRangeStartMs = startMs.coerceAtLeast(0L)
    trimRangeEndMs = endMs.coerceAtLeast(trimRangeStartMs + 50L)
    _trimPlaybackPositionMs.value = trimRangeStartMs
    progressSyncJob?.cancel()
    if (!isPlayableInPlayer(clip.uri)) return
    try {
      val parsedUri = Uri.parse(clip.uri)
      val normalizedUri = if (parsedUri.scheme == "asset") {
        var path = parsedUri.path ?: ""
        if (path.startsWith("/")) path = path.substring(1)
        if (path.isEmpty()) path = parsedUri.authority ?: ""
        Uri.parse("asset:///$path")
      } else parsedUri
      val clippingConfig = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(trimRangeStartMs).setEndPositionMs(trimRangeEndMs).setStartsAtKeyFrame(false).build()
      val mediaItem = MediaItem.Builder().setUri(normalizedUri).setClippingConfiguration(clippingConfig).build()
      player.stop(); player.clearMediaItems(); player.setMediaItem(mediaItem)
      player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
      player.playbackParameters = PlaybackParameters(clip.speed)
      player.volume = if (clip.isMuted) 0f else clip.volume
      player.prepare(); player.play(); loadedClipId = "trim_${clip.id}"
    } catch (e: Exception) { Log.w(TAG, "Failed to preview trim", e) }
  }

  fun seekTrimPreview(offsetFromStartMs: Long) {
    if (isTrimPreviewMode) {
      val offset = offsetFromStartMs.coerceIn(0L, (trimRangeEndMs - trimRangeStartMs).coerceAtLeast(0L))
      player.seekTo(offset); _trimPlaybackPositionMs.value = trimRangeStartMs + offset
    }
  }

  fun seekTrimPreviewToSourceMs(sourceTimeMs: Long) {
    if (isTrimPreviewMode) {
      val target = sourceTimeMs.coerceIn(trimRangeStartMs, trimRangeEndMs)
      player.seekTo((target - trimRangeStartMs).coerceAtLeast(0L)); _trimPlaybackPositionMs.value = target
    }
  }

  fun stepTrimFrame(forward: Boolean, fps: Int = 30) {
    if (isTrimPreviewMode) {
      pauseTrimPreview(); val frameMs = 1000L / fps.coerceAtLeast(1)
      seekTrimPreviewToSourceMs(_trimPlaybackPositionMs.value + if (forward) frameMs else -frameMs)
    }
  }

  fun pauseTrimPreview() { if (isTrimPreviewMode) player.pause() }
  fun playTrimPreview() { if (isTrimPreviewMode) player.play() }
  fun toggleTrimPlayPause() { if (isTrimPreviewMode) if (player.isPlaying) player.pause() else player.play() }

  fun exitTrimPreview() {
    if (isTrimPreviewMode) {
      isTrimPreviewMode = false; trimPreviewClip = null; player.repeatMode = Player.REPEAT_MODE_OFF
      loadedClipId = null; loadedUri = null; syncWithPosition(currentPosMs, forceReload = true)
    }
  }

  fun stepFrame(forward: Boolean, fps: Int = 30) {
    pause()
    val frame = (currentPosMs.toDouble() * fps / 1000.0).toLong()
    val targetFrame = (frame + if (forward) 1L else -1L).coerceAtLeast(0L)
    seekTo((targetFrame * 1000L) / fps.coerceAtLeast(1))
    onTimelinePositionChanged(currentPosMs)
  }

  private fun findClipAt(posMs: Long): VideoClip? = currentTimeline.videoClips.firstOrNull {
    posMs >= it.timelineStartMs && posMs < it.timelineStartMs + it.durationMs
  } ?: currentTimeline.videoClips.lastOrNull()

  private fun syncWithPosition(posMs: Long, forceReload: Boolean = false) {
    val clip = findClipAt(posMs)
    _activeClip.value = clip
    if (clip != null && clip.isVideo && isPlayableInPlayer(clip.uri)) {
      val effectiveUri = proxyEngine?.getProxyUri(clip) ?: clip.uri
      val needsReload = forceReload || loadedClipId != clip.id || loadedUri != effectiveUri || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE
      if (needsReload) ensureClipLoaded(clip)
      val sourcePosMs = clip.timelineToSourceMs(posMs)
      // Critical: do not seek on every timeline-state update. Repeated seeks were able to
      // interrupt decoder/audio buffers and produce the reported tuk-tuk/restart behaviour.
      if (!isSyncingFromPlayer && kotlin.math.abs(player.currentPosition - sourcePosMs) > SEEK_SYNC_TOLERANCE_MS) {
        player.seekTo(sourcePosMs)
      }
      player.playbackParameters = PlaybackParameters(clip.speed.coerceAtLeast(0.01f))
      player.volume = if (clip.isMuted) 0f else clip.volume
    } else player.pause()
  }

  private fun ensureClipLoaded(clip: VideoClip) {
    val effectiveUri = proxyEngine?.getProxyUri(clip) ?: clip.uri
    if (loadedClipId == clip.id && loadedUri == effectiveUri && player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE) return
    if (!isPlayableInPlayer(effectiveUri)) {
      try { player.stop(); player.clearMediaItems() } catch (_: Exception) {}
      loadedClipId = null; loadedUri = null; return
    }
    try {
      val parsedUri = Uri.parse(effectiveUri)
      val normalizedUri = if (parsedUri.scheme == "asset") {
        var path = parsedUri.path ?: ""
        if (path.startsWith("/")) path = path.substring(1)
        if (path.isEmpty()) path = parsedUri.authority ?: ""
        Uri.parse("asset:///$path")
      } else if (parsedUri.scheme == null || parsedUri.scheme == "file") {
        val path = parsedUri.path ?: effectiveUri
        val f = java.io.File(path); if (f.exists()) Uri.fromFile(f) else parsedUri
      } else parsedUri
      player.setMediaItem(MediaItem.fromUri(normalizedUri))
      player.playbackParameters = PlaybackParameters(clip.speed.coerceAtLeast(0.01f))
      player.volume = if (clip.isMuted) 0f else clip.volume
      player.prepare()
      if (_isPlaying.value) player.play()
      loadedClipId = clip.id; loadedUri = effectiveUri
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load clip URI: $effectiveUri", e)
      try { player.stop(); player.clearMediaItems() } catch (_: Exception) {}
      loadedClipId = null; loadedUri = null
    }
  }

  private fun handleClipEnded() {
    if (isTrimPreviewMode) { if (player.repeatMode == Player.REPEAT_MODE_OFF) pauseTrimPreview(); return }
    val active = _activeClip.value ?: return
    val nextPos = active.timelineStartMs + active.durationMs
    if (nextPos >= currentTimeline.totalDurationMs) {
      pause(); seekTo(0L); _currentPositionMs.value = 0L; onTimelinePositionChanged(0L); onPlaybackEnded()
    } else {
      currentPosMs = nextPos; _currentPositionMs.value = nextPos; onTimelinePositionChanged(nextPos)
      val nextClip = findClipAt(nextPos); _activeClip.value = nextClip
      if (nextClip != null && nextClip.isVideo && isPlayableInPlayer(nextClip.uri)) {
        ensureClipLoaded(nextClip); val sourcePosMs = nextClip.timelineToSourceMs(nextPos)
        playStartTimelineMs = nextPos; playStartSourceMs = sourcePosMs; playStartRealtimeMs = android.os.SystemClock.elapsedRealtime()
        if (kotlin.math.abs(player.currentPosition - sourcePosMs) > SEEK_SYNC_TOLERANCE_MS) player.seekTo(sourcePosMs)
        player.play(); _isPlaying.value = true; startProgressSync()
      } else startSyntheticPlaybackLoop()
    }
  }

  private fun startProgressSync() {
    progressSyncJob?.cancel()
    val startActive = _activeClip.value
    if (playStartRealtimeMs == 0L || kotlin.math.abs(playStartTimelineMs - currentPosMs) > 100L) {
      playStartTimelineMs = currentPosMs; playStartSourceMs = startActive?.timelineToSourceMs(currentPosMs) ?: 0L
      playStartRealtimeMs = android.os.SystemClock.elapsedRealtime()
    }
    progressSyncJob = scope.launch {
      while (isActive && (_isPlaying.value || player.isPlaying)) {
        if (isTrimPreviewMode) {
          val calculated = (trimRangeStartMs + player.currentPosition).coerceIn(trimRangeStartMs, trimRangeEndMs)
          _trimPlaybackPositionMs.value = calculated; onTimelinePositionChanged(calculated)
        } else {
          val active = _activeClip.value
          if (active != null && active.isVideo && isPlayableInPlayer(active.uri)) {
            val speed = active.speed.coerceAtLeast(0.01f)
            val playerPos = player.currentPosition
            val offsetInClip = ((playerPos - active.sourceStartMs) / speed).toLong()
            val calculatedTimeline = active.timelineStartMs + offsetInClip
            if (calculatedTimeline >= active.timelineStartMs + active.durationMs) { handleClipEnded(); break }
            val bounded = calculatedTimeline.coerceIn(active.timelineStartMs, currentTimeline.totalDurationMs.coerceAtLeast(0L))
            isSyncingFromPlayer = true
            currentPosMs = bounded; _currentPositionMs.value = bounded; onTimelinePositionChanged(bounded)
            syncOverlayPlayers(bounded)
            isSyncingFromPlayer = false
          } else { startSyntheticPlaybackLoop(); break }
        }
        delay(FRAME_INTERVAL_60FPS_MS)
      }
    }
  }

  private fun startSyntheticPlaybackLoop() {
    progressSyncJob?.cancel(); _isPlaying.value = true
    val startPos = currentPosMs; val startRealtime = android.os.SystemClock.elapsedRealtime()
    progressSyncJob = scope.launch {
      while (isActive && _isPlaying.value) {
        val next = startPos + (android.os.SystemClock.elapsedRealtime() - startRealtime)
        if (next >= currentTimeline.totalDurationMs) {
          pause(); seekTo(0L); _currentPositionMs.value = 0L; onTimelinePositionChanged(0L); onPlaybackEnded(); break
        }
        currentPosMs = next; _currentPositionMs.value = next; onTimelinePositionChanged(next); syncOverlayPlayers(next)
        val nextClip = findClipAt(next)
        if (nextClip != null && nextClip.id != _activeClip.value?.id) {
          syncWithPosition(next)
          if (nextClip.isVideo && isPlayableInPlayer(nextClip.uri)) { player.play(); startProgressSync(); break }
        }
        delay(FRAME_INTERVAL_60FPS_MS)
      }
    }
  }

  fun release() {
    _isPlaying.value = false; coalescedSeekJob?.cancel(); progressSyncJob?.cancel(); scope.cancel()
    overlayPlayers.values.forEach { p -> try { p.stop(); p.release() } catch (_: Exception) {} }
    overlayPlayers.clear(); overlayLoadedUris.clear(); engineController.release()
  }
}
