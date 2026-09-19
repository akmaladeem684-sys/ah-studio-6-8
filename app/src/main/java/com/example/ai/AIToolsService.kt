package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import com.example.ai.providers.*
import com.example.ai.providers.secure.AISecurityConfig
import com.example.domain.model.*
import com.example.engine.audio.AudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

typealias VideoHighlightSegment = com.example.ai.providers.VideoHighlightSegment

/**
 * High-level AI Tools Service coordinating all 6 AI provider interfaces:
 * - SpeechToText
 * - Translation
 * - BackgroundRemoval
 * - NoiseReduction
 * - TextToSpeech
 * - HighlightDetection
 *
 * Enforces real processing on actual media without generating fake/mock results.
 */
class AIToolsService(private val context: Context? = null) {
  val speechToText: SpeechToTextProvider = GeminiAIProvider(context)
  val translation: TranslationProvider = GeminiAIProvider(context)
  val backgroundRemoval: BackgroundRemovalProvider = SystemBackgroundRemovalProvider()
  val noiseReduction: NoiseReductionProvider? = context?.let { SystemNoiseReductionProvider(it.applicationContext) }
  val textToSpeech: TextToSpeechProvider? = context?.let { AndroidTextToSpeechProvider(it.applicationContext) }
  val highlightDetection: HighlightDetectionProvider = GeminiAIProvider(context)
  private val audioEngine: AudioEngine? = context?.let { AudioEngine(it.applicationContext) }

  val isAIConfigured: Boolean
    get() = AISecurityConfig.isConfigured(context)

  fun getSecurityStatus(): String {
    return context?.let { AISecurityConfig.getConnectionModeDescription(it) }
      ?: if (isAIConfigured) "Configured" else "Offline / Not Configured"
  }

  /**
   * AI Auto Captions: Analyzes actual imported media audio and generates synchronized captions.
   * If media has no audio or API is unavailable, returns clear failure without fake captions.
   */
  suspend fun generateAutoCaptions(
    timeline: Timeline,
    language: String = "English"
  ): Result<List<TextClip>> = withContext(Dispatchers.IO) {
    if (timeline.videoClips.isEmpty() && timeline.audioClips.isEmpty()) {
      return@withContext Result.failure(
        IllegalStateException("No media on timeline: Please import a video or audio clip first to generate captions.")
      )
    }

    val targetAudio = audioEngine?.extractTimelineAudio(timeline)
    if (targetAudio == null || targetAudio.length() == 0L) {
      return@withContext Result.failure(
        IllegalStateException("Selected media contains no audible audio track.")
      )
    }

    val sttResult = speechToText.transcribeAudio(targetAudio, language)
    sttResult.fold(
      onSuccess = { transcription ->
        if (transcription.segments.isEmpty()) {
          Result.failure(IllegalStateException("No speech detected in audio track."))
        } else {
          val textClips = transcription.segments.map { segment ->
            TextClip(
              id = "caption_${UUID.randomUUID()}",
              text = segment.text,
              startTimeMs = segment.startTimeMs,
              durationMs = segment.endTimeMs - segment.startTimeMs,
              fontSize = 24f,
              textColor = 0xFFFFFFFF,
              backgroundColor = 0xAA000000,
              posY = 0.8f
            )
          }
          Result.success(textClips)
        }
      },
      onFailure = { error ->
        Result.failure(error)
      }
    )
  }

  /**
   * AI Translation of Text Clips.
   */
  suspend fun translateText(
    text: String,
    targetLanguage: String
  ): Result<String> = withContext(Dispatchers.IO) {
    if (text.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Text cannot be empty"))
    }
    translation.translateText(text, targetLanguage)
  }

  /**
   * AI Background Removal: Processes actual input bitmap.
   */
  suspend fun removeBackground(input: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
    backgroundRemoval.removeBackground(input)
  }

  /**
   * AI Noise Reduction: Processes actual audio file.
   */
  suspend fun reduceNoise(audioFile: File): Result<File> = withContext(Dispatchers.IO) {
    val reducer = noiseReduction ?: return@withContext Result.failure(IllegalStateException("Noise reduction provider is unavailable"))
    reducer.reduceNoise(audioFile)
  }

  /**
   * AI Text-to-Speech: Generates real speech audio.
   */
  suspend fun synthesizeSpeech(
    text: String,
    pitch: Float = 1.0f,
    speed: Float = 1.0f
  ): Result<File> = withContext(Dispatchers.IO) {
    val tts = textToSpeech ?: return@withContext Result.failure(IllegalStateException("Text to speech provider is unavailable"))
    tts.synthesizeSpeech(text, pitch, speed)
  }

  /**
   * AI Smart Highlights: Real video analysis.
   */
  suspend fun detectHighlights(
    videoFile: File,
    targetDurationMs: Long = 15000L
  ): Result<List<VideoHighlightSegment>> = withContext(Dispatchers.IO) {
    highlightDetection.detectHighlights(videoFile, targetDurationMs)
  }
}
