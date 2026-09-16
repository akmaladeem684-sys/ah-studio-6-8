package com.example.ui.components.timeline

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.media.VideoThumbnailManager
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

/**
 * Small frame thumbnail chip displayed at periodic ruler intervals.
 */
@Composable
fun RulerFrameThumbnail(
  videoUri: String,
  sourceTimeMs: Long,
  widthDp: Dp = 32.dp,
  heightDp: Dp = 20.dp,
  isActive: Boolean = false,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val widthPx = remember(widthDp, density) { with(density) { widthDp.roundToPx() } }
  val heightPx = remember(heightDp, density) { with(density) { heightDp.roundToPx() } }

  val cacheKey = remember(videoUri, sourceTimeMs, widthPx, heightPx) {
    VideoThumbnailManager.makeKey(videoUri, sourceTimeMs, widthPx, heightPx)
  }

  var bitmap by remember(cacheKey) {
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(cacheKey))
  }

  LaunchedEffect(cacheKey) {
    if (bitmap == null && videoUri.isNotBlank()) {
      val loaded = VideoThumbnailManager.getThumbnail(
        context = context,
        uri = videoUri,
        sourceTimeMs = sourceTimeMs,
        targetWidth = widthPx,
        targetHeight = heightPx
      )
      if (loaded != null && !loaded.isRecycled) {
        bitmap = loaded
      }
    }
  }

  Surface(
    shape = RoundedCornerShape(3.dp),
    color = Color(0xFF0D1018),
    border = BorderStroke(
      width = if (isActive) 1.5.dp else 0.75.dp,
      color = if (isActive) CyanAccent else Color.White.copy(alpha = 0.25f)
    ),
    modifier = modifier
      .size(widthDp, heightDp)
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
  ) {
    Box(contentAlignment = Alignment.Center) {
      val bmp = bitmap
      if (bmp != null && !bmp.isRecycled) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "Ruler frame thumbnail",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141824))
        )
      }
    }
  }
}

/**
 * Floating Live CTI Frame Preview Card that updates dynamically during scrubbing and playback.
 */
@Composable
fun CTIFramePreviewCard(
  timeline: Timeline,
  currentPosMs: Long,
  fps: Int,
  isScrubbing: Boolean,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  val activeClip = remember(timeline, currentPosMs) {
    timeline.videoClips.find { clip ->
      currentPosMs >= clip.timelineStartMs && currentPosMs < (clip.timelineStartMs + clip.durationMs)
    } ?: timeline.videoClips.firstOrNull()
  }

  val activeOverlay = remember(timeline, currentPosMs, activeClip) {
    if (activeClip == null) {
      timeline.overlayClips.find { clip ->
        clip.isVideo && currentPosMs >= clip.timelineStartMs && currentPosMs < (clip.timelineStartMs + clip.durationMs)
      }
    } else null
  }

  val uri = activeClip?.uri ?: activeOverlay?.uri ?: ""
  val sourceTimeMs = when {
    activeClip != null -> activeClip.timelineToSourceMs(currentPosMs)
    activeOverlay != null -> activeOverlay.timelineToSourceMs(currentPosMs)
    else -> 0L
  }

  val widthDp = 64.dp
  val heightDp = 38.dp
  val widthPx = remember(density) { with(density) { widthDp.roundToPx() } }
  val heightPx = remember(density) { with(density) { heightDp.roundToPx() } }

  val cacheKey = remember(uri, sourceTimeMs) {
    VideoThumbnailManager.makeKey(uri, sourceTimeMs, widthPx, heightPx)
  }

  var bitmap by remember(cacheKey) {
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(cacheKey))
  }

  LaunchedEffect(cacheKey) {
    if (uri.isNotBlank()) {
      val loaded = VideoThumbnailManager.getThumbnail(
        context = context,
        uri = uri,
        sourceTimeMs = sourceTimeMs,
        targetWidth = widthPx,
        targetHeight = heightPx
      )
      if (loaded != null && !loaded.isRecycled) {
        bitmap = loaded
      }
    }
  }

  val frameNum = (currentPosMs / (1000.0 / fps)).toLong()

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color(0xFF080B12),
      border = BorderStroke(
        width = if (isScrubbing) 1.5.dp else 1.25.dp,
        color = if (isScrubbing) AmberAccent else CyanAccent
      ),
      shadowElevation = 6.dp,
      modifier = Modifier.width(68.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(Color(0xFF101420)),
          contentAlignment = Alignment.Center
        ) {
          val bmp = bitmap
          if (bmp != null && !bmp.isRecycled) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "CTI Frame Preview",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Text(
              text = formatDurationShort(currentPosMs),
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
              )
            )
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF04060A))
            .padding(horizontal = 4.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = formatDurationShort(currentPosMs),
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = if (isScrubbing) AmberAccent else Color.White
            )
          )
          Text(
            text = "F$frameNum",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 7.5.sp,
              fontWeight = FontWeight.Bold,
              color = CyanAccent
            )
          )
        }
      }
    }
  }
}

@Composable
fun AccurateTimecodeRuler(
  totalDurationMs: Long,
  currentPosMs: Long,
  msPerPixel: Float,
  onSeek: (Long) -> Unit,
  fps: Int = 30,
  isFrameSnapping: Boolean = false,
  onDoubleTapSnap: (() -> Unit)? = null,
  onScrubStart: () -> Unit = {},
  onScrubStop: () -> Unit = {},
  timeline: Timeline? = null,
  modifier: Modifier = Modifier
) {
  val msPerDp = msPerPixel
  val safeTotalDuration = totalDurationMs.coerceAtLeast(1000L)
  val rulerWidthDp = (safeTotalDuration / msPerDp).dp
  val frameDurationMs = 1000.0 / fps
  val density = LocalDensity.current

  var isScrubbing by remember { mutableStateOf(false) }
  var scrubPreviewMs by remember { mutableLongStateOf(currentPosMs) }

  // Dynamic tick calculation based on zoom level (msPerDp)
  val (majorIntervalMs, minorIntervalMs, showFrameTicks) = remember(msPerDp, fps) {
    when {
      msPerDp <= 3f -> Triple(500L, (1000L / fps).coerceAtLeast(1L), true)
      msPerDp <= 8f -> Triple(1000L, (1000L / fps).coerceAtLeast(1L), true)
      msPerDp <= 18f -> Triple(1000L, 200L, false)
      msPerDp <= 35f -> Triple(2000L, 500L, false)
      msPerDp <= 80f -> Triple(5000L, 1000L, false)
      else -> Triple(10000L, 2000L, false)
    }
  }

  val hasVideoClips = remember(timeline) {
    timeline?.videoClips?.isNotEmpty() == true || timeline?.overlayClips?.any { it.isVideo } == true
  }

  Box(
    modifier = modifier
      .width(rulerWidthDp)
      .height(if (hasVideoClips) 54.dp else 34.dp)
      .background(Color(0xFF080A0F))
      .testTag("timeline_timecode_ruler")
      .pointerInput(safeTotalDuration, msPerDp, isFrameSnapping, fps, density) {
        detectTapGestures(
          onDoubleTap = { offset ->
            if (onDoubleTapSnap != null) {
              onDoubleTapSnap()
            } else {
              val xDp = offset.x / density.density
              val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
              val targetMs = if (isFrameSnapping) {
                (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
              } else rawMs
              onSeek(targetMs)
            }
          },
          onTap = { offset ->
            val xDp = offset.x / density.density
            val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
            val targetMs = if (isFrameSnapping) {
              (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
            } else rawMs
            onSeek(targetMs)
          }
        )
      }
      .pointerInput(safeTotalDuration, msPerDp, isFrameSnapping, fps, density) {
        detectDragGestures(
          onDragStart = { offset ->
            isScrubbing = true
            onScrubStart()
            val xDp = offset.x / density.density
            val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
            val targetMs = if (isFrameSnapping) {
              (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
            } else rawMs
            scrubPreviewMs = targetMs
            onSeek(targetMs)
          },
          onDragEnd = {
            isScrubbing = false
            onScrubStop()
          },
          onDragCancel = {
            isScrubbing = false
            onScrubStop()
          },
          onDrag = { change, _ ->
            change.consume()
            val xDp = change.position.x / density.density
            val rawMs = (xDp * msPerDp).toLong().coerceIn(0L, safeTotalDuration)
            val targetMs = if (isFrameSnapping) {
              (Math.round(rawMs / frameDurationMs) * frameDurationMs).toLong()
            } else rawMs
            scrubPreviewMs = targetMs
            onSeek(targetMs)
          }
        )
      }
  ) {
    // 1. Frame Thumbnails Row along the top of the ruler bar
    if (hasVideoClips && timeline != null) {
      val totalMajorIntervals = (safeTotalDuration / majorIntervalMs).toInt()
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(22.dp)
          .padding(top = 2.dp)
      ) {
        for (i in 0..totalMajorIntervals) {
          val tickTimeMs = i * majorIntervalMs
          val videoClip = timeline.videoClips.find { clip ->
            tickTimeMs >= clip.timelineStartMs && tickTimeMs < (clip.timelineStartMs + clip.durationMs)
          }
          val overlayClip = if (videoClip == null) {
            timeline.overlayClips.find { clip ->
              clip.isVideo && tickTimeMs >= clip.timelineStartMs && tickTimeMs < (clip.timelineStartMs + clip.durationMs)
            }
          } else null

          val uri = videoClip?.uri ?: overlayClip?.uri ?: ""
          if (uri.isNotBlank()) {
            val sourceMs = when {
              videoClip != null -> videoClip.timelineToSourceMs(tickTimeMs)
              overlayClip != null -> overlayClip.timelineToSourceMs(tickTimeMs)
              else -> 0L
            }
            val xDp = (tickTimeMs / msPerDp).dp
            val isActive = kotlin.math.abs(tickTimeMs - currentPosMs) <= (majorIntervalMs / 2)

            RulerFrameThumbnail(
              videoUri = uri,
              sourceTimeMs = sourceMs,
              widthDp = 32.dp,
              heightDp = 20.dp,
              isActive = isActive,
              onClick = { onSeek(tickTimeMs) },
              modifier = Modifier.offset(x = xDp - 16.dp)
            )
          }
        }
      }
    }

    // 2. Ruler Ticks & Timecode Numbers
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(30.dp)
        .align(Alignment.BottomCenter)
    ) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      // Subtle ruler top gradient background
      drawRect(
        brush = Brush.verticalGradient(
          listOf(Color(0xFF040508), Color(0xFF0C0F17))
        )
      )

      // Bottom border line
      drawLine(
        color = Color(0xFF1B202D),
        start = Offset(0f, canvasHeight),
        end = Offset(canvasWidth, canvasHeight),
        strokeWidth = 1.dp.toPx()
      )

      val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 9.5.sp.toPx()
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
      }

      val frameTextPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 0, 229, 255)
        textSize = 8.sp.toPx()
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
      }

      val totalTicks = (safeTotalDuration / minorIntervalMs).toInt()

      for (i in 0..totalTicks) {
        val tickTimeMs = i * minorIntervalMs
        val x = (tickTimeMs / msPerDp).dp.toPx()
        val isMajor = tickTimeMs % majorIntervalMs == 0L
        val isHalfMajor = tickTimeMs % (majorIntervalMs / 2) == 0L

        val tickHeight = when {
          isMajor -> canvasHeight * 0.55f
          isHalfMajor -> canvasHeight * 0.35f
          showFrameTicks -> canvasHeight * 0.20f
          else -> canvasHeight * 0.25f
        }

        val tickColor = when {
          isMajor -> Color.White.copy(alpha = 0.95f)
          isHalfMajor -> Color.White.copy(alpha = 0.6f)
          showFrameTicks && (i % 5 == 0) -> CyanAccent.copy(alpha = 0.7f)
          else -> Color.White.copy(alpha = 0.3f)
        }

        drawLine(
          color = tickColor,
          start = Offset(x, canvasHeight - tickHeight),
          end = Offset(x, canvasHeight),
          strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
        )

        if (isMajor) {
          val label = formatTimecodeRuler(tickTimeMs)
          drawContext.canvas.nativeCanvas.drawText(
            label,
            x + 4f,
            canvasHeight - tickHeight - 4f,
            textPaint
          )
        } else if (showFrameTicks && isHalfMajor && msPerDp <= 5f) {
          val frameNum = msToFrameIndex(tickTimeMs, fps)
          drawContext.canvas.nativeCanvas.drawText(
            "F$frameNum",
            x + 3f,
            canvasHeight - tickHeight - 3f,
            frameTextPaint
          )
        }
      }
    }
  }
}

private fun formatTimecodeRuler(ms: Long): String {
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  val millis = ms % 1000
  return if (minutes > 0) {
    String.format("%02d:%02d", minutes, seconds)
  } else {
    String.format("%d.%ds", seconds, millis / 100)
  }
}
