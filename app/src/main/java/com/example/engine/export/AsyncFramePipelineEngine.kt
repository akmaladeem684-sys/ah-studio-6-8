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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

/** GPU-only frame descriptor; no Bitmap or pixel ByteBuffer is retained. */
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

class FramePacketQueue<T>(capacity: Int) {
  private val queue = ArrayBlockingQueue<T>(capacity.coerceIn(1, 8))
  fun put(value: T, cancelled: AtomicBoolean): Boolean {
    while (!cancelled.get()) if (queue.offer(value, 50, TimeUnit.MILLISECONDS)) return true
    return false
  }
  fun take(cancelled: AtomicBoolean): T? {
    while (!cancelled.get()) queue.poll(50, TimeUnit.MILLISECONDS)?.let { return it }
    return null
  }
  fun depth(): Int = queue.size
  fun clear() = queue.clear()
}

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

  fun snapshot() = mapOf(
    "decodedFrames" to decodedFrames.get(), "gpuFrames" to gpuFrames.get(),
    "encodedFrames" to encodedFrames.get(), "droppedFrames" to droppedFrames.get(),
    "duplicatedFrames" to duplicatedFrames.get(), "gpuToCpuCopies" to gpuToCpuCopies.get(),
    "cpuToGpuCopies" to cpuToGpuCopies.get(), "zeroCopyFrames" to zeroCopyFrames.get(),
    "backpressureEvents" to backpressureEvents.get(), "maxQueueDepth" to maxQueueDepth.get()
  )
}

/** Persistent MediaCodec decoder feeding a SurfaceTexture/GL_TEXTURE_EXTERNAL_OES. */
private class SurfaceDecoder(
  private val context: Context,
  private val clip: VideoClip,
  private val glHandler: Handler
) : SurfaceTexture.OnFrameAvailableListener {
  private val ready = AtomicBoolean(false)
  private var codec: MediaCodec? = null
  private var extractor: MediaExtractor? = null
  private var surface: Surface? = null
  private var surfaceTexture: SurfaceTexture? = null
  private var textureId = 0
  private var width = clip.width.coerceAtLeast(1)
  private var height = clip.height.coerceAtLeast(1)
  private val matrix = FloatArray(16)
  private var lastRequestUs = Long.MIN_VALUE

  fun start() {
    require(MediaRelinkManager.isRealPlayableMedia(context, clip.uri)) { "Unplayable clip: ${clip.uri}" }
    val ex = MediaExtractor()
    val uri = Uri.parse(clip.uri)
    if (uri.scheme == "content" || uri.scheme == "file") ex.setDataSource(context, uri, null) else ex.setDataSource(clip.uri)
    var track = -1
    var format: MediaFormat? = null
    for (i in 0 until ex.trackCount) {
      val f = ex.getTrackFormat(i)
      if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) { track = i; format = f; break }
    }
    require(track >= 0 && format != null) { "No video track for ${clip.id}" }
    ex.selectTrack(track)
    width = format!!.getInteger(MediaFormat.KEY_WIDTH).coerceAtLeast(1)
    height = format!!.getInteger(MediaFormat.KEY_HEIGHT).coerceAtLeast(1)

    val created = CountDownLatch(1)
    glHandler.post {
      try {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(textureId).also { it.setOnFrameAvailableListener(this, glHandler) }
        surface = Surface(surfaceTexture)
      } finally { created.countDown() }
    }
    check(created.await(5, TimeUnit.SECONDS)) { "SurfaceTexture creation timed out" }
    codec = MediaCodec.createDecoderByType(format!!.getString(MediaFormat.KEY_MIME)!!).also {
      it.configure(format, surface, null, 0)
      it.start()
    }
    extractor = ex
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture) { ready.set(true) }

  /** Runs codec work on the decode executor; updateTexImage is always done on the GPU thread. */
  fun decodeTo(targetUs: Long, cancelled: AtomicBoolean): FramePacket {
    val c = codec ?: error("decoder not started")
    val ex = extractor ?: error("extractor not started")
    if (targetUs < lastRequestUs || lastRequestUs == Long.MIN_VALUE) {
      ex.seekTo(targetUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      ready.set(false)
    }
    lastRequestUs = targetUs
    val info = MediaCodec.BufferInfo()
    var inputEos = false
    var gotOutput = false
    var outputPts = targetUs
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
    while (!gotOutput && !cancelled.get() && System.nanoTime() < deadline) {
      if (!inputEos) {
        val inIndex = c.dequeueInputBuffer(5_000L)
        if (inIndex >= 0) {
          val input = c.getInputBuffer(inIndex)
          if (input != null) {
            input.clear()
            val size = ex.readSampleData(input, 0)
            if (size < 0) {
              c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputEos = true
            } else {
              val pts = ex.sampleTime.coerceAtLeast(0L)
              c.queueInputBuffer(inIndex, 0, size, pts, 0)
              ex.advance()
            }
          }
        }
      }
      val outIndex = c.dequeueOutputBuffer(info, 5_000L)
      if (outIndex >= 0) {
        outputPts = info.presentationTimeUs.coerceAtLeast(0L)
        c.releaseOutputBuffer(outIndex, true)
        if (outputPts >= targetUs || ready.get()) gotOutput = true
        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
      }
    }
    if (!gotOutput) throw IllegalStateException("Decoder timeout for ${clip.id} at ${targetUs}us")
    val st = surfaceTexture ?: error("SurfaceTexture released")
    st.updateTexImage()
    st.getTransformMatrix(matrix)
    val timestamp = st.timestamp.takeIf { it >= 0L } ?: outputPts
    ready.set(false)
    return FramePacket(0, 0, timestamp, textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, matrix.copyOf(), width, height, clip.id)
  }

  fun release() {
    try { codec?.stop() } catch (_: Throwable) {}
    try { codec?.release() } catch (_: Throwable) {}
    try { extractor?.release() } catch (_: Throwable) {}
    try { surface?.release() } catch (_: Throwable) {}
    try { surfaceTexture?.release() } catch (_: Throwable) {}
    codec = null; extractor = null; surface = null; surfaceTexture = null
    if (textureId != 0 && Thread.currentThread() == glHandler.looper.thread) {
      GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
      textureId = 0
    }
  }
}

/**
 * Hardware export path for video-only timelines. Audio-bearing projects continue through the
 * existing stable exporter so sample-accurate audio mixing is preserved.
 */
class AsyncFramePipelineEngine(private val context: Context) {
  private val cancelled = AtomicBoolean(false)
  private val glThread = HandlerThread("AH-GPU-Pipeline").apply { start() }
  private val glHandler = Handler(glThread.looper)
  private val decodeExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-Decode-Stage") }
  private val composition = VideoCompositionEngine(context)
  val metrics = AsyncFramePipelineMetrics()

  fun cancel() { cancelled.set(true) }

  suspend fun export(timeline: Timeline, config: ExportConfig, outputFile: File): File? = withContext(Dispatchers.IO) {
    cancelled.set(false)
    if (timeline.videoClips.isEmpty()) return@withContext null
    val dims = dimensions(config.resolution, timeline.aspectRatio)
    val fps = config.frameRate.fps.coerceAtLeast(1)
    val durationMs = timeline.totalDurationMs.coerceAtLeast(1L)
    val totalFrames = ceil(durationMs * fps / 1000.0).toLong().coerceAtLeast(1L)
    val mime = selectEncoder(config, dims.first, dims.second, fps) ?: return@withContext null

    var encoder: MediaCodec? = null
    var inputSurface: Surface? = null
    var egl: EglCore? = null
    var window: WindowSurface? = null
    var renderer: GpuCompositionRenderer? = null
    var muxer: MediaMuxer? = null
    val decoders = LinkedHashMap<String, SurfaceDecoder>()
    val queue = FramePacketQueue<FrameWork>(3)
    val muxLock = Any()
    var track = -1
    var muxStarted = false
    var eos = false
    val failure = AtomicReference<Throwable?>(null)

    try {
      encoder = MediaCodec.createEncoderByType(mime)
      val format = MediaFormat.createVideoFormat(mime, dims.first, dims.second).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate(config))
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      }
      encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      inputSurface = encoder.createInputSurface()
      egl = EglCore(null, EglCore.FLAG_RECORDABLE)
      window = WindowSurface(egl, inputSurface, false)
      window.makeCurrent()
      renderer = GpuCompositionRenderer(context).also { it.initGl() }
      muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

      // Prepare persistent surface decoders for every real video clip. All pixel transport is OES.
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (clip.isVideo && clip.uri.isNotBlank() && MediaRelinkManager.isRealPlayableMedia(context, clip.uri)) {
          val d = SurfaceDecoder(context, clip, glHandler)
          d.start()
          decoders[clip.id] = d
        }
      }
      if (decoders.isEmpty()) return@withContext null
      encoder.start()

      // Encoder drain is independent from GPU rendering; muxer writes are synchronized.
      val drainDone = CountDownLatch(1)
      val drain = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-Encode-Drain") }
      drain.execute {
        try {
          val info = MediaCodec.BufferInfo()
          while (!cancelled.get() && !eos) {
            val index = encoder!!.dequeueOutputBuffer(info, 10_000L)
            when {
              index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxLock) {
                if (track < 0) track = muxer!!.addTrack(encoder!!.outputFormat)
                if (!muxStarted) { muxer!!.start(); muxStarted = true }
              }
              index >= 0 -> {
                val out = encoder!!.getOutputBuffer(index)
                if (out != null && info.size > 0 && muxStarted && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                  synchronized(muxLock) { muxer!!.writeSampleData(track, out, MediaCodec.BufferInfo().apply { set(info.offset, info.size, info.presentationTimeUs.coerceAtLeast(0L), info.flags) }) }
                  metrics.encodedFrames.incrementAndGet()
                }
                val end = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                encoder!!.releaseOutputBuffer(index, false)
                if (end) eos = true
              }
            }
          }
        } catch (t: Throwable) { failure.set(t); cancelled.set(true) }
        finally { drainDone.countDown() }
      }

      data class FrameWork(val index: Long, val ptsUs: Long, val positionMs: Long)
      val producer = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-Frame-Producer") }
      val consumer = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-GPU-Consumer") }
      val producerDone = CountDownLatch(1)
      producer.execute {
        try {
          for (i in 0 until totalFrames) {
            if (cancelled.get()) break
            val pts = i * 1_000_000L / fps
            val work = FrameWork(i, pts, (pts / 1000L).coerceAtMost(durationMs - 1L))
            if (queue.depth() >= 3) metrics.backpressureEvents.incrementAndGet()
            if (!queue.put(work, cancelled)) break
            metrics.maxQueueDepth.updateAndGet { maxOf(it, queue.depth().toLong()) }
          }
        } catch (t: Throwable) { failure.set(t); cancelled.set(true) }
        finally { producerDone.countDown() }
      }

      consumer.execute {
        try {
          while (!cancelled.get()) {
            val work = queue.take(cancelled) ?: break
            val frame = composition.evaluateFrame(timeline, work.positionMs)
            val main = frame.activeClip?.let { decoders[it.id] }
              ?: throw IllegalStateException("No hardware decoder for active main clip")
            val decodeStart = System.nanoTime()
            val mainPacket = decodeExecutor.submit<FramePacket> { main.decodeTo(frame.clipSourcePosMs * 1000L, cancelled) }.get()
            metrics.decodeTimeNs.addAndGet(System.nanoTime() - decodeStart)
            metrics.decodedFrames.incrementAndGet(); metrics.zeroCopyFrames.incrementAndGet()
            val overlays = HashMap<String, Int>()
            for (overlay in frame.activeOverlays) {
              decoders[overlay.clip.id]?.let { d ->
                val p = decodeExecutor.submit<FramePacket> { d.decodeTo(overlay.sourcePosMs * 1000L, cancelled) }.get()
                overlays[overlay.clip.id] = p.textureId
              }
            }
            val renderStart = System.nanoTime()
            renderer!!.render(frame, mainPacket.textureId, true, mainPacket.transformMatrix, overlays, dims.first, dims.second, timeline.adjustments, timeline.filter, timeline.chromaKey)
            window!!.setPresentationTime(work.ptsUs * 1000L)
            if (!window!!.swapBuffers()) throw IllegalStateException("EGL encoder swap failed at frame ${work.index}")
            metrics.gpuRenderTimeNs.addAndGet(System.nanoTime() - renderStart)
            metrics.gpuFrames.incrementAndGet()
          }
        } catch (t: Throwable) { failure.set(t); cancelled.set(true) }
      }

      producerDone.await()
      consumer.shutdown()
      consumer.awaitTermination(30, TimeUnit.SECONDS)
      producer.shutdown()
      if (!cancelled.get()) encoder!!.signalEndOfInputStream()
      drainDone.await(30, TimeUnit.SECONDS)
      if (failure.get() != null) throw failure.get()!!
      if (cancelled.get() || !muxStarted) { outputFile.delete(); return@withContext null }
      outputFile.takeIf { it.exists() && it.length() > 0L }
    } catch (t: Throwable) {
      outputFile.delete()
      null
    } finally {
      cancelled.set(true); queue.clear()
      decoders.values.forEach { runCatching { it.release() } }
      runCatching { renderer?.release() }; runCatching { window?.release() }; runCatching { egl?.release() }
      runCatching { inputSurface?.release() }; runCatching { encoder?.stop() }; runCatching { encoder?.release() }
      if (muxStarted) runCatching { muxer?.stop() }; runCatching { muxer?.release() }
    }
  }

  private data class FrameWork(val index: Long, val ptsUs: Long, val positionMs: Long)

  private fun selectEncoder(config: ExportConfig, w: Int, h: Int, fps: Int): String? {
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
      if (vc.isSizeSupported(w, h) && runCatching { vc.getSupportedFrameRatesFor(w, h).contains(fps.toDouble()) }.getOrDefault(false)) return mime
    }
    return null
  }

  private fun isHardware(info: MediaCodecInfo) = if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else {
    val n = info.name.lowercase(); !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains("software")
  }

  private fun bitrate(config: ExportConfig): Int = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) config.customBitrateKbps * 1000 else when (config.resolution) {
    Resolution.RES_480P -> 2_500_000
    Resolution.RES_720P -> 5_000_000
    Resolution.RES_1080P -> 10_000_000
    Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000
    Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000
    Resolution.RES_SQUARE_2K -> 22_000_000
  }

  private fun dimensions(resolution: Resolution, aspect: AspectRatio): Pair<Int, Int> {
    val vertical = aspect.ratio < 1f
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
