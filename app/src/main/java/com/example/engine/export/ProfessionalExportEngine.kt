package com.example.engine.export

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.example.domain.model.Resolution
import com.example.domain.model.Timeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max

/** Immutable, deterministic output-frame schedule. */
data class ExportFramePlan(
  val frameIndex: Long,
  val presentationTimeUs: Long,
  val timelinePositionMs: Long
)

data class ExportPlan(
  val durationMs: Long,
  val totalFrames: Long,
  val frameRate: Int,
  val frames: Sequence<ExportFramePlan>
)

/** Builds output timestamps from the editor timeline without using wall-clock time. */
object ExportRenderPlanner {
  fun build(timeline: Timeline, config: ExportConfig): ExportPlan {
    val durationMs = timeline.totalDurationMs.coerceAtLeast(0L)
    val fps = config.frameRate.fps.coerceAtLeast(1)
    val totalFrames = if (durationMs == 0L) 0L else ceil(durationMs / 1000.0 * fps).toLong()
    val frameDurationUs = 1_000_000L / fps
    val frames = sequence {
      var index = 0L
      while (index < totalFrames) {
        val ptsUs = index * frameDurationUs
        val positionMs = (ptsUs / 1000L).coerceAtMost(max(0L, durationMs - 1L))
        yield(ExportFramePlan(index, ptsUs, positionMs))
        index++
      }
    }
    return ExportPlan(durationMs, totalFrames, fps, frames)
  }

  fun activeVideoClipCount(timeline: Timeline, positionMs: Long): Int =
    timeline.videoClips.count { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs }

  fun activeAudioClipCount(timeline: Timeline, positionMs: Long): Int =
    timeline.audioClips.count { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs }
}

data class ExportCapabilityReport(
  val videoEncoders: List<String>,
  val audioEncoders: List<String>,
  val h264Supported: Boolean,
  val hevcSupported: Boolean,
  val requestedSupported: Boolean,
  val reason: String? = null
)

/** Runtime encoder capability inspection. It never assumes a codec/resolution/FPS is available. */
object ProfessionalCodecCapabilities {
  private const val AAC = "audio/mp4a-latm"

  fun inspect(config: ExportConfig): ExportCapabilityReport {
    val videoMime = when (config.codecProfile) {
      CodecProfile.H265_HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
      CodecProfile.H264_AVC, CodecProfile.AUTO -> MediaFormat.MIMETYPE_VIDEO_AVC
    }
    val dimensions = dimensions(config.resolution)
    val videoEncoders = mutableListOf<String>()
    val audioEncoders = mutableListOf<String>()
    var requested = false
    var h264 = false
    var hevc = false

    return try {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder) continue
        val types = info.supportedTypes
        if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }) h264 = true
        if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) }) hevc = true
        if (types.any { it.equals(AAC, true) }) audioEncoders += info.name
        if (!types.any { it.equals(videoMime, true) }) continue
        val caps = runCatching { info.getCapabilitiesForType(videoMime) }.getOrNull() ?: continue
        val vc = caps.videoCapabilities ?: continue
        val sizeOk = vc.isSizeSupported(dimensions.first, dimensions.second)
        val fpsOk = runCatching { vc.getSupportedFrameRatesFor(dimensions.first, dimensions.second).contains(config.frameRate.fps.toDouble()) }.getOrDefault(false)
        if (sizeOk && fpsOk) {
          videoEncoders += info.name
          if (isHardware(info)) requested = true
        }
      }
      val reason = if (!requested) "No compatible hardware video encoder for ${config.resolution.label} @ ${config.frameRate.fps}fps (${videoMime})." else null
      ExportCapabilityReport(videoEncoders.distinct(), audioEncoders.distinct(), h264, hevc, requested, reason)
    } catch (t: Throwable) {
      ExportCapabilityReport(emptyList(), emptyList(), h264, hevc, false, "Codec capability scan failed: ${t.message ?: "unknown error"}")
    }
  }

  private fun isHardware(info: MediaCodecInfo): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else {
      val n = info.name.lowercase()
      !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains("software") && !n.contains("sw.")
    }

  private fun dimensions(resolution: Resolution): Pair<Int, Int> = when (resolution) {
    Resolution.RES_480P -> 854 to 480
    Resolution.RES_720P -> 1280 to 720
    Resolution.RES_1080P -> 1920 to 1080
    Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 2560 to 1440
    Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 3840 to 2160
    Resolution.RES_SQUARE_2K -> 2048 to 2048
  }
}

data class ExportValidationResult(
  val valid: Boolean,
  val message: String,
  val durationMs: Long = 0L,
  val videoCodec: String? = null,
  val audioCodec: String? = null,
  val width: Int = 0,
  val height: Int = 0,
  val frameRate: Int? = null
)

/** Post-export MP4 verification. A file is successful only after this passes. */
object ExportValidator {
  fun validate(file: File, config: ExportConfig, expectedDurationMs: Long, requireAudio: Boolean = true): ExportValidationResult {
    if (!file.exists()) return ExportValidationResult(false, "Output file does not exist.")
    if (file.length() <= 0L) return ExportValidationResult(false, "Output file is empty.")
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(file.absolutePath)
      var videoMime: String? = null
      var audioMime: String? = null
      var width = 0
      var height = 0
      var fps: Int? = null
      var durationMs = 0L
      var videoTrack = -1
      var audioTrack = -1
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        val trackDuration = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L) / 1000L
        durationMs = max(durationMs, trackDuration)
        if (mime.startsWith("video/")) {
          if (videoTrack < 0) videoTrack = i
          videoMime = mime
          width = format.getInteger(MediaFormat.KEY_WIDTH)
          height = format.getInteger(MediaFormat.KEY_HEIGHT)
          if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = format.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else if (mime.startsWith("audio/")) {
          if (audioTrack < 0) audioTrack = i
          audioMime = mime
        }
      }
      if (videoTrack < 0) return ExportValidationResult(false, "MP4 has no video track.")
      if (requireAudio && audioTrack < 0) return ExportValidationResult(false, "MP4 has no audio track.", durationMs, videoMime, audioMime, width, height, fps)
      val expectedMime = when (config.codecProfile) {
        CodecProfile.H265_HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
        else -> MediaFormat.MIMETYPE_VIDEO_AVC
      }
      if (config.codecProfile != CodecProfile.AUTO && videoMime != expectedMime) {
        return ExportValidationResult(false, "Unexpected video codec: $videoMime", durationMs, videoMime, audioMime, width, height, fps)
      }
      val tolerance = max(750L, expectedDurationMs / 100L)
      if (expectedDurationMs > 0L && kotlin.math.abs(durationMs - expectedDurationMs) > tolerance) {
        return ExportValidationResult(false, "Duration mismatch: expected ${expectedDurationMs}ms, got ${durationMs}ms.", durationMs, videoMime, audioMime, width, height, fps)
      }
      extractor.selectTrack(videoTrack)
      val sampleSize = extractor.readSampleData(java.nio.ByteBuffer.allocate(64 * 1024), 0)
      extractor.unselectTrack(videoTrack)
      if (sampleSize <= 0) return ExportValidationResult(false, "Video track cannot be decoded.", durationMs, videoMime, audioMime, width, height, fps)
      ExportValidationResult(true, "Verified", durationMs, videoMime, audioMime, width, height, fps)
    } catch (t: Throwable) {
      ExportValidationResult(false, "MP4 validation failed: ${t.message ?: "unknown error"}")
    } finally {
      extractor.release()
    }
  }
}

enum class ProfessionalExportStage { PREPARING, DECODING, RENDERING, ENCODING_VIDEO, MIXING_AUDIO, MUXING, VERIFYING, COMPLETED, FAILED, CANCELLED }

data class ProfessionalExportProgress(
  val stage: ProfessionalExportStage = ProfessionalExportStage.PREPARING,
  val fraction: Float = 0f,
  val renderedDurationMs: Long = 0L,
  val estimatedRemainingMs: Long? = null,
  val message: String = "Preparing export"
)

/**
 * Production orchestration layer. Existing VideoExporter remains the renderer/compositor implementation;
 * this layer adds deterministic planning, preflight capability checks, cancellation, validation and safe cleanup.
 */
class ProfessionalExportEngine(private val context: Context) {
  private val tag = "ProfessionalExportEngine"
  private val _progress = MutableStateFlow(ProfessionalExportProgress())
  val progress: StateFlow<ProfessionalExportProgress> = _progress.asStateFlow()
  @Volatile private var cancelled = false

  fun cancel() { cancelled = true }

  suspend fun export(
    projectName: String,
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File,
    requireAudio: Boolean = true
  ): Result<File> = withContext(Dispatchers.IO) {
    cancelled = false
    try {
      _progress.value = ProfessionalExportProgress(message = "Checking device encoder capabilities")
      val capability = ProfessionalCodecCapabilities.inspect(config)
      if (!capability.requestedSupported) return@withContext Result.failure(IllegalStateException(capability.reason ?: "Requested export configuration is unsupported."))
      coroutineContext.ensureActive()
      checkCancelled()

      val plan = ExportRenderPlanner.build(timeline, config)
      if (plan.durationMs <= 0L || plan.totalFrames <= 0L) return@withContext Result.failure(IllegalArgumentException("Timeline contains no renderable duration."))
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.PREPARING, 0.02f, message = "Preparing ${plan.totalFrames} deterministic frames")

      val temp = File(context.cacheDir, "ah_export_${System.currentTimeMillis()}_${sanitize(projectName)}.mp4")
      temp.parentFile?.mkdirs()
      temp.delete()

      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.RENDERING, 0.05f, message = "GPU rendering and hardware encoding")
      val exporter = VideoExporter(context)
      val rendered = exporter.exportProject(projectName, timeline, config)
      checkCancelled()
      if (rendered == null || !rendered.exists() || rendered.length() <= 0L) {
        return@withContext Result.failure(IllegalStateException("Render pipeline produced no output."))
      }

      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.VERIFYING, 0.94f, plan.durationMs, message = "Verifying MP4 tracks, duration and decodability")
      val validation = ExportValidator.validate(rendered, config, plan.durationMs, requireAudio)
      if (!validation.valid) {
        rendered.delete()
        return@withContext Result.failure(IllegalStateException(validation.message))
      }
      checkCancelled()

      outputFile.parentFile?.mkdirs()
      rendered.copyTo(outputFile, overwrite = true)
      if (!outputFile.exists() || outputFile.length() <= 0L) {
        outputFile.delete()
        return@withContext Result.failure(IllegalStateException("Final output could not be written."))
      }
      rendered.delete()
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.COMPLETED, 1f, plan.durationMs, message = "Export completed and verified")
      Result.success(outputFile)
    } catch (e: CancellationException) {
      outputFile.delete()
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.CANCELLED, 0f, message = "Export cancelled")
      throw e
    } catch (t: Throwable) {
      outputFile.delete()
      Log.e(tag, "Export failed", t)
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.FAILED, 0f, message = t.message ?: "Export failed")
      Result.failure(t)
    }
  }

  private fun checkCancelled() {
    if (cancelled) throw CancellationException("Export cancelled")
  }

  private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "project" }
}
