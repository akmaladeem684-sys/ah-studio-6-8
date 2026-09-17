package com.example.engine.export

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.domain.model.*
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.gpu.EglCore
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/** Lightweight GPU-owned frame descriptor. It intentionally contains no pixel buffers. */
data class FramePacket(
  val frameIndex: Long,
  val presentationTimeUs: Long,
  val sourceTimestampUs: Long,
  val textureId: Int,
  val textureTarget: Int,
  val transformMatrix: FloatArray,
  val width: Int,
  val height: Int,
  val clipId: String
)

/** Bounded queue used to apply backpressure without unbounded frame retention. */
class FramePacketQueue<T>(capacity: Int) {
  private val queue = ArrayBlockingQueue<T>(capacity.coerceIn(1, 8))
  fun put(value: T, cancelled: AtomicBoolean) {
    while (!cancelled.get()) {
      if (queue.offer(value, 50, TimeUnit.MILLISECONDS)) return
    }
    throw InterruptedException("Frame pipeline cancelled")
  }
  fun take(cancelled: AtomicBoolean): T {
    while (!cancelled.get()) {
      queue.poll(50, TimeUnit.MILLISECONDS)?.let { return it }
    }
    throw InterruptedException("Frame pipeline cancelled")
  }
  fun depth(): Int = queue.size
  fun clear() { queue.clear() }
}

/** Thread-safe instrumentation for the asynchronous pipeline. */
class AsyncFramePipelineMetrics {
  val decodedFrames = AtomicLong()
  val gpuFrames = AtomicLong()
  val encodedFrames = AtomicLong()
  val droppedFrames = AtomicLong()
  val duplicatedFrames = AtomicLong()
  val gpuToCpuCopies = AtomicLong()
  val cpuToGpuCopies = AtomicLong()
  val zeroCopyFrames = AtomicLong()
  val backpressureEvents = AtomicLong()
  val decodeTimeNs = AtomicLong()
  val gpuRenderTimeNs = AtomicLong()
  val encodeTimeNs = AtomicLong()
  val maxQueueDepth = AtomicLong()

  fun snapshot(): Map<String, Long> = mapOf(
    "decodedFrames" to decodedFrames.get(),
    "gpuFrames" to gpuFrames.get(),
    "encodedFrames" to encodedFrames.get(),
    "droppedFrames" to droppedFrames.get(),
    "duplicatedFrames" to duplicatedFrames.get(),
    "gpuToCpuCopies" to gpuToCpuCopies.get(),
    "cpuToGpuCopies" to cpuToGpuCopies.get(),
    "zeroCopyFrames" to zeroCopyFrames.get(),
    "backpressureEvents" to backpressureEvents.get(),
    "maxQueueDepth" to maxQueueDepth.get()
  )
}

/**
 * Persistent MediaCodec decoder whose output is a SurfaceTexture/OES texture.
 * Pixel data never crosses through Bitmap or ByteBuffer in this path.
 */
private class SurfaceTextureDecoder(
  private val context: Context,
  private val clip: VideoClip,
  private val glHandler: Handler
) : SurfaceTexture.OnFrameAvailableListener {
  private val tag = "SurfaceTextureDecoder"
  private val lock = Object()
  private val frameAvailable = AtomicBoolean(false)
  private val started = AtomicBoolean(false)
  private var decoder: MediaCodec? = null
  private var extractor: MediaExtractor? = null
  private var outputSurface: Surface? = null
  private var surfaceTexture: SurfaceTexture? = null
  private var oesTextureId = 0
  private var lastRequestedUs = Long.MIN_VALUE
  private var lastRenderedUs = Long.MIN_VALUE
  private var width = clip.width.coerceAtLeast(1)
  private var height = clip.height.coerceAtLeast(1)
  private val matrix = FloatArray(16)

  fun start() {
    if (started.get()) return
    if (!MediaRelinkManager.isRealPlayableMedia(context, clip.uri)) {
      throw IllegalArgumentException("Clip is not playable: ${clip.uri}")
    }
    val extractor = MediaExtractor()
    val uri = Uri.parse(clip.uri)
    if (uri.scheme == "content" || uri.scheme == "file") extractor.setDataSource(context, uri, null)
    else extractor.setDataSource(clip.uri)
    var track = -1
    var format: MediaFormat? = null
    for (i in 0 until extractor.trackCount) {
      val f = extractor.getTrackFormat(i)
      val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
      if (mime.startsWith("video/")) { track = i; format = f; break }
    }
    require(track >= 0 && format != null) { "No video track: ${clip.uri}" }
    extractor.selectTrack(track)
    width = format!!.getInteger(MediaFormat.KEY_WIDTH, width).coerceAtLeast(1)
    height = format!!.getInteger(MediaFormat.KEY_HEIGHT, height).coerceAtLeast(1)

    val mime = format!!.getString(MediaFormat.KEY_MIME)!!
    val codec = MediaCodec.createDecoderByType(mime)

    // GL texture creation must occur on the same EGL/GL thread as updateTexImage().
    val latch = CountDownLatch(1)
    glHandler.post {
      try {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        oesTextureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener(this, glHandler)
        surfaceTexture = st
        outputSurface = Surface(st)
        latch.countDown()
      } catch (t: Throwable) {
        Log.e(tag, "Failed to create decoder SurfaceTexture", t)
        latch.countDown()
        throw t
      }
    }
    if (!latch.await(5, TimeUnit.SECONDS)) throw IllegalStateException("Timed out creating decoder SurfaceTexture")
    codec.configure(format, outputSurface, null, 0)
    codec.start()
    decoder = codec
    this.extractor = extractor
    started.set(true)
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
    frameAvailable.set(true)
    synchronized(lock) { lock.notifyAll() }
  }

  /** Decode until a frame at or immediately after the requested source timestamp is rendered. */
  fun renderAt(sourceUs: Long, cancelled: AtomicBoolean): FramePacket {
    check(started.get()) { "Decoder not started" }
    if (cancelled.get()) throw InterruptedException("cancelled")
    val targetUs = sourceUs.coerceAtLeast(0L)
    if (targetUs < lastRequestedUs || lastRequestedUs == Long.MIN_VALUE) {
      extractor!!.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      lastRenderedUs = Long.MIN_VALUE
      frameAvailable.set(false)
    }
    lastRequestedUs = targetUs

    val codec = decoder!!
    val info = MediaCodec.BufferInfo()
    var inputEos = false
    var rendered = false
    var renderedUs = targetUs
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)

    while (!rendered && System.nanoTime() < deadline && !cancelled.get()) {
      if (!inputEos) {
        val inIndex = codec.dequeueInputBuffer(5_000L)
        if (inIndex >= 0) {
          val input = codec.getInputBuffer(inIndex)
          if (input != null) {
            input.clear()
            val size = extractor!!.readSampleData(input, 0)
            if (size < 0) {
              codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputEos = true
            } else {
              val pts = extractor!!.sampleTime.coerceAtLeast(0L)
              codec.queueInputBuffer(inIndex, 0, size, pts, 0)
              extractor!!.advance()
            }
          }
        }
      }

      var outputIndex = codec.dequeueOutputBuffer(info, 5_000L)
      if (outputIndex >= 0) {
        renderedUs = info.presentationTimeUs.coerceAtLeast(0L)
        codec.releaseOutputBuffer(outputIndex, true)
        if (renderedUs >= targetUs) rendered = true
        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
      } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        // Output surface dimensions are established by the decoder format; no CPU copy is needed.
      }

      if (!rendered && frameAvailable.get()) {
        rendered = true
      }
    }
    if (!rendered) throw IllegalStateException("Decoder timed out at ${targetUs}us for ${clip.id}")

    // updateTexImage() is executed only on the GL thread.
    if (Thread.currentThread() != glHandler.looper.thread) {
      throw IllegalStateException("SurfaceTextureDecoder.renderAt must run on the GL thread")
    }
    val st = surfaceTexture ?: error("SurfaceTexture released")
    st.updateTexImage()
    st.getTransformMatrix(matrix)
    renderedUs = st.timestamp.takeIf { it >= 0L } ?: renderedUs
    frameAvailable.set(false)
    lastRenderedUs = renderedUs
    return FramePacket(0L, 0L, renderedUs, oesTextureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, matrix.copyOf(), width, height, clip.id)
  }

  fun release() {
    started.set(false)
    try { decoder?.stop() } catch (_: Throwable) {}
    try { decoder?.release() } catch (_: Throwable) {}
    decoder = null
    try { extractor?.release() } catch (_: Throwable) {}
    extractor = null
    try { outputSurface?.release() } catch (_: Throwable) {}
    outputSurface = null
    try { surfaceTexture?.release() } catch (_: Throwable) {}
    surfaceTexture = null
    if (oesTextureId != 0 && Thread.currentThread() == glHandler.looper.thread) {
      GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
      oesTextureId = 0
    }
  }
}

/**
 * Real asynchronous decode -> GPU composition/effects -> surface encoder pipeline.
 * The GPU stage owns all video pixels; CPU fallback remains in VideoExporter.
 */
class AsyncFramePipelineEngine(private val context: Context) {
  private val tag = "AsyncFramePipelineEngine"
  private val cancelled = AtomicBoolean(false)
  private val decodeThread = HandlerThread("AH-FrameDecode").apply { start() }
  private val gpuThread = HandlerThread("AH-FrameGPU").apply { start() }
  private val decodeHandler = Handler(decodeThread.looper)
  private val gpuHandler = Handler(gpuThread.looper)
  private val decodeQueue = FramePacketQueue<FrameRequest>(3)
  private val metrics = AsyncFramePipelineMetrics()
  private val compositionEngine = VideoCompositionEngine(context)

  private data class FrameRequest(
    val frameIndex: Long,
    val ptsUs: Long,
    val timelinePosMs: Long,
    val result: java.util.concurrent.CompletableFuture<FramePacket>()
  )

  data class Result(val file: File, val metrics: Map<String, Long>)

  fun cancel() { cancelled.set(true); decodeQueue.clear() }

  suspend fun export(
    projectName: String,
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File,
    requireAudio: Boolean = true
  ): Result? = withContext(Dispatchers.IO) {
    cancelled.set(false)
    if (timeline.videoClips.isEmpty()) return@withContext null
    val dims = resolveDimensions(config.resolution, timeline.aspectRatio)
    val fps = config.frameRate.fps.coerceAtLeast(1)
    val durationMs = timeline.totalDurationMs.coerceAtLeast(1L)
    val totalFrames = ceil(durationMs / 1000.0 * fps).toLong().coerceAtLeast(1L)
    val videoMime = selectVideoMime(config, dims.first, dims.second, fps) ?: return@withContext null

    val audioProcessor = AudioExportProcessor(context)
    val hasAudio = requireAudio && audioProcessor.hasActiveAudio(timeline)
    val masterPcm = if (hasAudio) audioProcessor.mixTimelineAudio(timeline, durationMs) { cancelled.get() } else ShortArray(0)
    if (cancelled.get()) return@withContext null

    var encoder: MediaCodec? = null
    var muxer: MediaMuxer? = null
    var encoderSurface: Surface? = null
    var egl: EglCore? = null
    var window: WindowSurface? = null
    var renderer: GpuCompositionRenderer? = null
    var audioEncoder: MediaCodec? = null
    var videoTrack = -1
    var audioTrack = -1
    var muxerStarted = false
    val decoders = mutableMapOf<String, SurfaceTextureDecoder>()
    val outputInfo = MediaCodec.BufferInfo()

    try {
      val videoFormat = MediaFormat.createVideoFormat(videoMime, dims.first, dims.second).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate(config, durationMs))
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        try { setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) } catch (_: Throwable) {}
      }
      encoder = MediaCodec.createEncoderByType(videoMime)
      encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      encoderSurface = encoder.createInputSurface()
      egl = EglCore(null, EglCore.FLAG_RECORDABLE)
      window = WindowSurface(egl, encoderSurface, false)
      window.makeCurrent()
      renderer = GpuCompositionRenderer(context).also { it.initGl() }

      // Decoder sessions are created lazily on the GPU thread so their SurfaceTextures share the EGL context.
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (clip.isVideo && clip.uri.isNotBlank() && MediaRelinkManager.isRealPlayableMedia(context, clip.uri)) {
          val decoder = SurfaceTextureDecoder(context, clip, gpuHandler)
          decoders[clip.id] = decoder
          decoder.start()
        }
      }
      if (decoders.isEmpty()) return@withContext null

      encoder.start()
      val muxerFile = outputFile.absolutePath
      muxer = MediaMuxer(muxerFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

      // Continuous encoder drain keeps MediaCodec output flowing while GPU renders.
      val drainExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "AH-EncoderDrain") }
      val drainDone = java.util.concurrent.CountDownLatch(1)
      val drainFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
      val eosSeen = AtomicBoolean(false)
      drainExecutor.execute {
        try {
          var idle = 0
          while (!eosSeen.get() && !cancelled.get()) {
            val index = encoder!!.dequeueOutputBuffer(outputInfo, 10_000L)
            when {
              index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                videoTrack = muxer!!.addTrack(encoder!!.outputFormat)
                if (!hasAudio || audioTrack >= 0) {
                  if (!muxerStarted) { muxer!!.start(); muxerStarted = true }
                }
              }
              index >= 0 -> {
                val out = encoder!!.getOutputBuffer(index)
                if (out != null && outputInfo.size > 0 && muxerStarted && (outputInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                  val info = MediaCodec.BufferInfo().apply { set(outputInfo.offset, outputInfo.size, outputInfo.presentationTimeUs.coerceAtLeast(0L), outputInfo.flags) }
                  muxer!!.writeSampleData(videoTrack, out, info)
                  metrics.encodedFrames.incrementAndGet()
                }
                val eos = (outputInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                encoder!!.releaseOutputBuffer(index, false)
                if (eos) eosSeen.set(true)
                idle = 0
              }
              else -> {
                idle++
                if (idle > 100 && cancelled.get()) break
              }
            }
          }
        } catch (t: Throwable) { drainFailure.set(t); cancelled.set(true) }
        finally { drainDone.countDown() }
      }

      // Audio is encoded on a bounded worker and added to the same muxer before video samples are written.
      // The PCM itself is generated by the existing sample-accurate audio mixer; video PTS remains timeline-driven.
      if (hasAudio && masterPcm.isNotEmpty()) {
        audioEncoder = createAacEncoder(audioProcessor.sampleRate, audioProcessor.channelCount)
        audioEncoder!!.start()
        val audioFormatReady = CountDownLatch(1)
        val audioInfo = MediaCodec.BufferInfo()
        var audioEos = false
        var audioFormat: MediaFormat? = null
        var pcmOffset = 0
        while (!audioEos && !cancelled.get()) {
          val inIndex = audioEncoder!!.dequeueInputBuffer(10_000L)
          if (inIndex >= 0) {
            val input = audioEncoder!!.getInputBuffer(inIndex)
            if (input != null) {
              input.clear()
              val remaining = masterPcm.size - pcmOffset
              if (remaining <= 0) {
                audioEncoder!!.queueInputBuffer(inIndex, 0, 0, durationMs * 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                audioEos = true
              } else {
                val samples = minOf(remaining, input.capacity() / 2)
                input.asShortBuffer().put(masterPcm, pcmOffset, samples)
                val pts = (pcmOffset / audioProcessor.channelCount).toLong() * 1_000_000L / audioProcessor.sampleRate
                audioEncoder!!.queueInputBuffer(inIndex, 0, samples * 2, pts, 0)
                pcmOffset += samples
              }
            }
          }
          var outIndex = audioEncoder!!.dequeueOutputBuffer(audioInfo, 0L)
          if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audioFormat = audioEncoder!!.outputFormat
            audioTrack = muxer!!.addTrack(audioFormat!!)
            audioFormatReady.countDown()
            if (videoTrack >= 0 && !muxerStarted) { muxer!!.start(); muxerStarted = true }
          } else if (outIndex >= 0) {
            val out = audioEncoder!!.getOutputBuffer(outIndex)
            if (out != null && audioInfo.size > 0 && muxerStarted && (audioInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
              muxer!!.writeSampleData(audioTrack, out, audioInfo)
            }
            audioEncoder!!.releaseOutputBuffer(outIndex, false)
          }
        }
      }

      // Producer/consumer decode queue. Requests are bounded to three frames, preventing decoder runaway.
      val producer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "AH-DecodeStage") }
      val gpu = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "AH-GpuStage") }
      val requests = FramePacketQueue<FrameRequest>(3)
      val producerDone = CountDownLatch(1)
      producer.execute {
        try {
          for (i in 0 until totalFrames) {
            if (cancelled.get()) break
            val ptsUs = i * 1_000_000L / fps
            val posMs = (ptsUs / 1000L).coerceAtMost(durationMs - 1L)
            val future = java.util.concurrent.CompletableFuture<FramePacket>()
            if (requests.depth() >= 3) metrics.backpressureEvents.incrementAndGet()
            requests.put(FrameRequest(i, ptsUs, posMs, future), cancelled)
            metrics.maxQueueDepth.updateAndGet { maxOf(it, requests.depth().toLong()) }
          }
        } catch (_: Throwable) { if (!cancelled.get()) cancelled.set(true) }
        finally { producerDone.countDown() }
      }

      gpu.execute {
        try {
          while (!cancelled.get()) {
            val request = try { requests.take(cancelled) } catch (_: Throwable) { break }
            val composed = compositionEngine.evaluateFrame(timeline, request.timelinePosMs)
            val mainClip = composed.activeClip
            val mainDecoder = mainClip?.let { decoders[it.id] }
            if (mainDecoder == null) {
              request.result.completeExceptionally(IllegalStateException("No hardware decoder for active clip"))
              cancelled.set(true)
              break
            }
            val decodeStart = System.nanoTime()
            val mainPacket = mainDecoder.renderAt(composed.clipSourcePosMs * 1000L, cancelled).copy(
              frameIndex = request.frameIndex,
              presentationTimeUs = request.ptsUs
            )
            metrics.decodeTimeNs.addAndGet(System.nanoTime() - decodeStart)
            metrics.decodedFrames.incrementAndGet()
            metrics.zeroCopyFrames.incrementAndGet()

            val overlays = mutableMapOf<String, Int>()
            for (overlay in composed.activeOverlays) {
              val decoder = decoders[overlay.clip.id] ?: continue
              val p = decoder.renderAt(overlay.clipSourcePosMs * 1000L, cancelled)
              overlays[overlay.clip.id] = p.textureId
            }

            val renderStart = System.nanoTime()
            renderer!!.render(
              frame = composed,
              mainTextureId = mainPacket.textureId,
              isMainOes = true,
              mainTexMatrix = mainPacket.transformMatrix,
              overlayTextures = overlays,
              viewportWidth = dims.first,
              viewportHeight = dims.second,
              timelineAdjustments = timeline.adjustments,
              timelineFilter = timeline.filter,
              chromaKey = timeline.chromaKey
            )
            window!!.setPresentationTime(request.ptsUs * 1000L)
            if (!window!!.swapBuffers()) throw IllegalStateException("Encoder EGL swap failed at frame ${request.frameIndex}")
            metrics.gpuRenderTimeNs.addAndGet(System.nanoTime() - renderStart)
            metrics.gpuFrames.incrementAndGet()
            request.result.complete(mainPacket)
          }
        } catch (t: Throwable) {
          Log.e(tag, "GPU stage failed", t)
          cancelled.set(true)
        }
      }

      producerDone.await()
      while (!cancelled.get() && !eosSeen.get()) Thread.sleep(5)
      if (!cancelled.get()) encoder!!.signalEndOfInputStream()
      drainDone.await(10, TimeUnit.SECONDS)
      if (drainFailure.get() != null) throw drainFailure.get()!!
      if (cancelled.get()) return@withContext null
      Result(outputFile, metrics.snapshot())
    } catch (t: Throwable) {
      Log.e(tag, "Asynchronous pipeline failed", t)
      outputFile.delete()
      null
    } finally {
      cancelled.set(true)
      decodeQueue.clear()
      decoders.values.forEach { runCatching { it.release() } }
      runCatching { renderer?.release() }
      runCatching { window?.release() }
      runCatching { egl?.release() }
      runCatching { encoderSurface?.release() }
      runCatching { encoder?.stop() }
      runCatching { encoder?.release() }
      runCatching { audioEncoder?.stop() }
      runCatching { audioEncoder?.release() }
      if (muxerStarted) runCatching { muxer?.stop() }
      runCatching { muxer?.release() }
    }
  }

  private fun createAacEncoder(sampleRate: Int, channels: Int): MediaCodec {
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
    }
    return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
      it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }
  }

  private fun selectVideoMime(config: ExportConfig, width: Int, height: Int, fps: Int): String? {
    val requested = when (config.codecProfile) {
      CodecProfile.H265_HEVC -> listOf(MediaFormat.MIMETYPE_VIDEO_HEVC)
      CodecProfile.H264_AVC -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
      CodecProfile.AUTO -> listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
    }
    for (mime in requested) for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
      if (!info.isEncoder || !isHardware(info) || !info.supportedTypes.any { it.equals(mime, true) }) continue
      val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
      if (!caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)) continue
      val vc = caps.videoCapabilities ?: continue
      if (vc.isSizeSupported(width, height) && runCatching { vc.getSupportedFrameRatesFor(width, height).contains(fps.toDouble()) }.getOrDefault(false)) return mime
    }
    return null
  }

  private fun isHardware(info: MediaCodecInfo): Boolean = if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else {
    val n = info.name.lowercase()
    !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains("software")
  }

  private fun bitrate(config: ExportConfig, durationMs: Long): Int = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
    (config.customBitrateKbps * 1000L).toInt()
  } else {
    val base = when (config.resolution) {
      Resolution.RES_480P -> 2_500_000L
      Resolution.RES_720P -> 5_000_000L
      Resolution.RES_1080P -> 10_000_000L
      Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000L
      Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000L
      Resolution.RES_SQUARE_2K -> 22_000_000L
    }
    (base * config.quality.bitrateMultiplier).toInt().coerceIn(1_500_000, 50_000_000)
  }

  private fun resolveDimensions(resolution: Resolution, aspect: AspectRatio): Pair<Int, Int> {
    val vertical = aspect == AspectRatio.VERTICAL || resolution == Resolution.RES_VERTICAL_2K || resolution == Resolution.RES_VERTICAL_4K
    return when (resolution) {
      Resolution.RES_480P -> if (vertical) 480 to 854 else 854 to 480
      Resolution.RES_720P -> if (vertical) 720 to 1280 else 1280 to 720
      Resolution.RES_1080P -> if (vertical) 1080 to 1920 else 1920 to 1080
      Resolution.RES_2K -> if (vertical) 1440 to 2560 else 2560 to 1440
      Resolution.RES_VERTICAL_2K -> 1440 to 2560
      Resolution.RES_4K -> if (vertical) 2160 to 3840 else 3840 to 2160
      Resolution.RES_VERTICAL_4K -> 2160 to 3840
      Resolution.RES_SQUARE_2K -> 2048 to 2048
    }
  }
}
