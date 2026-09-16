package com.example.engine.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
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

/**
 * Manages the Jetpack Media3 ExoPlayer instance, low-latency buffering, and hardware/software
 * decoder renderer pipeline.
 *
 * ExoPlayer's hardware-backed clock is the single authoritative source of playback time.
 */
@OptIn(UnstableApi::class)
class PlaybackManager(
  private val context: Context,
  private val onPlaybackStateChanged: (Int) -> Unit = {},
  private val onIsPlayingChanged: (Boolean) -> Unit = {},
  private val onPlayerError: (PlaybackException) -> Unit = {}
) {

  companion object {
    private const val TAG = "PlaybackManager"
  }

  val player: ExoPlayer = ExoPlayer.Builder(
    context.applicationContext,
    DefaultRenderersFactory(context.applicationContext)
      .setEnableDecoderFallback(true)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
  )
    .setLoadControl(
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(
          /* minBufferMs = */ 1000,
          /* maxBufferMs = */ 5000,
          /* bufferForPlaybackMs = */ 200,
          /* bufferForPlaybackAfterRebufferMs = */ 500
        )
        .build()
    )
    .setSeekParameters(SeekParameters.CLOSEST_SYNC)
    .build().apply {
      playWhenReady = false
      repeatMode = Player.REPEAT_MODE_OFF
    }

  private var currentLoadedUri: String? = null
  private var currentLoadedStartMs: Long = 0L

  val isPlaying: Boolean get() = player.isPlaying

  val currentPosition: Long get() = player.currentPosition

  val duration: Long get() = player.duration.coerceAtLeast(0L)

  val bufferedPosition: Long get() = player.bufferedPosition

  val playbackState: Int get() = player.playbackState

  private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
      val stateName = when (state) {
        Player.STATE_IDLE -> "STATE_IDLE"
        Player.STATE_BUFFERING -> "STATE_BUFFERING"
        Player.STATE_READY -> "STATE_READY"
        Player.STATE_ENDED -> "STATE_ENDED"
        else -> "UNKNOWN($state)"
      }
      Log.d(TAG, "ExoPlayer playback state changed: $stateName (playWhenReady=${player.playWhenReady})")
      this@PlaybackManager.onPlaybackStateChanged(state)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      Log.d(TAG, "ExoPlayer isPlaying changed: $isPlaying, pos=${player.currentPosition}ms")
      this@PlaybackManager.onIsPlayingChanged(isPlaying)
    }

    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
      Log.d(TAG, "ExoPlayer videoSize: ${videoSize.width}x${videoSize.height}, unappliedRotation=${videoSize.unappliedRotationDegrees}, pixelAspectRatio=${videoSize.pixelWidthHeightRatio}")
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
      Log.d(TAG, "ExoPlayer surfaceSizeChanged: ${width}x${height}")
    }

    override fun onRenderedFirstFrame() {
      Log.d(TAG, "ExoPlayer rendered FIRST video frame to active surface successfully!")
    }

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
      val hasVideo = tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected }
      val hasAudio = tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected }
      Log.d(TAG, "ExoPlayer tracks changed: hasSelectedVideo=$hasVideo, hasSelectedAudio=$hasAudio")
    }

    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "ExoPlayer playback exception: [${error.errorCodeName}] ${error.message}", error)
      this@PlaybackManager.onPlayerError(error)
    }
  }

  init {
    player.addListener(playerListener)
  }

  fun loadMedia(uri: Uri, startPosMs: Long = 0L, autoPlay: Boolean = false) {
    val uriString = uri.toString()
    if (uriString == currentLoadedUri && player.playbackState != Player.STATE_IDLE) {
      seekTo(startPosMs)
      if (autoPlay) play()
      return
    }

    currentLoadedUri = uriString
    currentLoadedStartMs = startPosMs

    val normalizedUri = if (uri.scheme == "asset") {
      var path = uri.path ?: ""
      if (path.startsWith("/")) path = path.substring(1)
      if (path.isEmpty()) path = uri.authority ?: ""
      Uri.parse("asset:///$path")
    } else if (uri.scheme == null || uri.scheme == "file") {
      val path = uri.path ?: uriString
      val f = java.io.File(path)
      if (f.exists()) Uri.fromFile(f) else uri
    } else {
      uri
    }

    val mediaItem = MediaItem.fromUri(normalizedUri)
    player.setMediaItem(mediaItem, startPosMs)
    player.prepare()
    player.playWhenReady = autoPlay
    Log.d(TAG, "Loaded media URI: $uriString (normalized: $normalizedUri) at ${startPosMs}ms (autoPlay=$autoPlay)")
  }

  fun play() {
    if (player.playbackState == Player.STATE_IDLE && currentLoadedUri != null) {
      player.prepare()
    }
    player.play()
  }

  fun pause() {
    player.pause()
  }

  fun seekTo(positionMs: Long) {
    player.seekTo(positionMs.coerceAtLeast(0L))
  }

  fun setVolume(volume: Float) {
    player.volume = volume.coerceIn(0f, 2f)
  }

  fun setMuted(isMuted: Boolean) {
    player.volume = if (isMuted) 0f else 1f
  }

  fun setPlaybackSpeed(speed: Float) {
    val clampedSpeed = speed.coerceIn(0.1f, 10.0f)
    if (player.playbackParameters.speed != clampedSpeed) {
      player.playbackParameters = PlaybackParameters(clampedSpeed)
    }
  }

  fun setSurface(surface: Surface?) {
    if (surface != null && surface.isValid) {
      player.setVideoSurface(surface)
      Log.d(TAG, "Attached valid Surface to ExoPlayer")
    } else {
      player.clearVideoSurface()
      Log.d(TAG, "Cleared Video Surface from ExoPlayer")
    }
  }

  fun clearSurface() {
    player.clearVideoSurface()
  }

  fun addListener(listener: Player.Listener) {
    player.addListener(listener)
  }

  fun removeListener(listener: Player.Listener) {
    player.removeListener(listener)
  }

  fun release() {
    player.removeListener(playerListener)
    player.stop()
    player.clearVideoSurface()
    player.release()
    Log.d(TAG, "PlaybackManager ExoPlayer cleanly released")
  }
}
