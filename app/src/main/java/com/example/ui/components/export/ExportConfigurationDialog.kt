package com.example.ui.components.export

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AspectRatio
import com.example.domain.model.ExportQuality
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.engine.export.CodecProfile
import com.example.engine.export.ExportConfig
import com.example.ui.components.formatDuration
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Dialog for comprehensive export configuration.
 * Allows users to choose resolution (4K UHD, 2K QHD, 1080p FHD, 720p, 480p),
 * framerate, video codec (Auto, AVC, HEVC), and bitrate (presets and custom slider).
 */
@Composable
fun ExportConfigurationDialog(
  projectName: String,
  totalDurationMs: Long,
  aspectRatio: AspectRatio,
  initialResolution: Resolution = Resolution.RES_1080P,
  initialFps: FrameRate = FrameRate.FPS_30,
  initialQuality: ExportQuality = ExportQuality.HIGH,
  initialBitrateKbps: Int = 12000,
  initialCodec: CodecProfile = CodecProfile.AUTO,
  onDismiss: () -> Unit,
  onConfirmExport: (config: ExportConfig) -> Unit
) {
  var selectedResolution by remember { mutableStateOf(initialResolution) }
  var selectedFps by remember { mutableStateOf(initialFps) }
  var selectedQuality by remember { mutableStateOf(initialQuality) }
  var selectedCodec by remember { mutableStateOf(initialCodec) }
  var customBitrateKbps by remember { mutableIntStateOf(initialBitrateKbps) }
  var isCustomBitrateMode by remember { mutableStateOf(initialQuality == ExportQuality.CUSTOM) }

  // Calculate dimensions based on aspect ratio
  val dimensions = remember(selectedResolution, aspectRatio) {
    calculateExportDimensions(selectedResolution, aspectRatio)
  }

  // Active config representation
  val currentConfig = remember(selectedResolution, selectedFps, selectedQuality, customBitrateKbps, isCustomBitrateMode, selectedCodec) {
    ExportConfig(
      resolution = selectedResolution,
      frameRate = selectedFps,
      quality = if (isCustomBitrateMode) ExportQuality.CUSTOM else selectedQuality,
      customBitrateKbps = customBitrateKbps,
      codecProfile = selectedCodec
    )
  }

  // Live estimated file size calculation
  val estimatedSizeBytes = remember(currentConfig, totalDurationMs) {
    calculateEstimatedSize(totalDurationMs, currentConfig)
  }

  val estimatedMbString = remember(estimatedSizeBytes) {
    val mb = estimatedSizeBytes / (1024f * 1024f)
    if (mb < 1f) {
      String.format("%.2f MB", mb)
    } else {
      String.format("%.1f MB", mb)
    }
  }

  // Effective bitrate in Mbps for display
  val effectiveBitrateMbps = remember(currentConfig) {
    if (isCustomBitrateMode) {
      customBitrateKbps / 1000f
    } else {
      val base = when (selectedResolution) {
        Resolution.RES_480P -> 2.5f
        Resolution.RES_720P -> 5.0f
        Resolution.RES_1080P -> 10.0f
        Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18.0f
        Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35.0f
        Resolution.RES_SQUARE_2K -> 22.0f
      }
      val codecMultiplier = if (selectedCodec == CodecProfile.H265_HEVC) 0.75f else 1.0f
      base * selectedQuality.bitrateMultiplier * (selectedFps.fps / 30f) * codecMultiplier
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.92f)
        .testTag("export_config_dialog"),
      shape = RoundedCornerShape(20.dp),
      color = StudioSurface,
      tonalElevation = 8.dp,
      border = BorderStroke(1.dp, StudioBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp)
      ) {
        // --- Header ---
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SkyBlueContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.VideoSettings,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Export Configuration",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = "High-Performance Video Engine",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = CyanAccent,
                  fontWeight = FontWeight.SemiBold
                )
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("cancel_export_dialog_btn")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Engine & Project Info Banner ---
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          color = StudioSurfaceVariant,
          border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = projectName.ifBlank { "Untitled Project" },
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                ),
                maxLines = 1
              )
              Text(
                text = "Duration: ${formatDuration(totalDurationMs)} • Aspect Ratio: ${aspectRatio.label}",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
              )
            }

            Surface(
              shape = RoundedCornerShape(6.dp),
              color = SkyBlueContainer
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  Icons.Default.Speed,
                  contentDescription = null,
                  tint = CyanAccent,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Hardware Accelerated",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CyanAccentDark,
                    fontSize = 10.sp
                  )
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Scrollable Settings Body ---
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // ==============================
          // 1. Resolution Selection
          // ==============================
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Resolution",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = "${dimensions.first} × ${dimensions.second} px",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = CyanAccent,
                  fontWeight = FontWeight.Bold
                )
              )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(Resolution.values()) { res ->
                val isSelected = selectedResolution == res
                val isRecommended = res == Resolution.RES_1080P
                val isUhd = res == Resolution.RES_4K || res == Resolution.RES_2K
                val resDims = calculateExportDimensions(res, aspectRatio)

                Surface(
                  onClick = { selectedResolution = res },
                  shape = RoundedCornerShape(12.dp),
                  color = if (isSelected) SkyBlueContainer else StudioSurfaceVariant,
                  border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) CyanAccent else StudioBorder
                  ),
                  modifier = Modifier
                    .width(110.dp)
                    .height(68.dp)
                    .testTag("resolution_chip_${res.label}")
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(8.dp),
                    verticalArrangement = Arrangement.Center
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = res.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                          fontWeight = FontWeight.Bold,
                          color = if (isSelected) CyanAccentDark else TextPrimary
                        )
                      )
                      if (isRecommended) {
                        Surface(
                          shape = RoundedCornerShape(4.dp),
                          color = CyanAccent
                        ) {
                          Text(
                            text = "REC",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                              color = Color.White,
                              fontSize = 8.sp,
                              fontWeight = FontWeight.Bold
                            )
                          )
                        }
                      } else if (isUhd) {
                        Surface(
                          shape = RoundedCornerShape(4.dp),
                          color = GoldAccent
                        ) {
                          Text(
                            text = "PRO",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                              color = Color.Black,
                              fontSize = 8.sp,
                              fontWeight = FontWeight.Bold
                            )
                          )
                        }
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "${resDims.first}×${resDims.second}",
                      style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                      )
                    )
                  }
                }
              }
            }
          }

          HorizontalDivider(color = StudioBorder)

          // ==============================
          // 2. Video Codec Selection
          // ==============================
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Video Codec",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = selectedCodec.label,
                style = MaterialTheme.typography.labelMedium.copy(
                  color = GreenAccent,
                  fontWeight = FontWeight.Bold
                )
              )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              CodecProfile.values().forEach { codec ->
                val isSelected = selectedCodec == codec
                Surface(
                  onClick = { selectedCodec = codec },
                  shape = RoundedCornerShape(10.dp),
                  color = if (isSelected) GreenAccent.copy(alpha = 0.15f) else StudioSurfaceVariant,
                  border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) GreenAccent else StudioBorder
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("codec_chip_${codec.name}")
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = when (codec) {
                        CodecProfile.AUTO -> "Auto"
                        CodecProfile.H264_AVC -> "H.264 (AVC)"
                        CodecProfile.H265_HEVC -> "H.265 (HEVC)"
                      },
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) GreenAccent else TextPrimary
                      )
                    )
                  }
                }
              }
            }
          }

          HorizontalDivider(color = StudioBorder)

          // ==============================
          // 3. Frame Rate (FPS) Selection
          // ==============================
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Frame Rate (FPS)",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = "${selectedFps.fps} frames/sec",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = PurpleAccent,
                  fontWeight = FontWeight.Bold
                )
              )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(FrameRate.values()) { fps ->
                val isSelected = selectedFps == fps
                val label = when (fps) {
                  FrameRate.FPS_24 -> "24 Cinematic"
                  FrameRate.FPS_25 -> "25 PAL"
                  FrameRate.FPS_30 -> "30 Standard"
                  FrameRate.FPS_50 -> "50 High"
                  FrameRate.FPS_60 -> "60 Smooth"
                }

                Surface(
                  onClick = { selectedFps = fps },
                  shape = RoundedCornerShape(10.dp),
                  color = if (isSelected) PurpleAccent.copy(alpha = 0.15f) else StudioSurfaceVariant,
                  border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PurpleAccent else StudioBorder
                  ),
                  modifier = Modifier
                    .height(44.dp)
                    .testTag("fps_chip_${fps.fps}")
                ) {
                  Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) PurpleAccent else TextPrimary
                      )
                    )
                  }
                }
              }
            }
          }

          HorizontalDivider(color = StudioBorder)

          // ==============================
          // 4. Bitrate & Quality Settings
          // ==============================
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Bitrate & Encoding Quality",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = String.format("%.1f Mbps", effectiveBitrateMbps),
                style = MaterialTheme.typography.labelMedium.copy(
                  color = CyanAccent,
                  fontWeight = FontWeight.Bold
                )
              )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Quality Presets
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              ExportQuality.values().forEach { quality ->
                val isSelected = if (quality == ExportQuality.CUSTOM) isCustomBitrateMode else (!isCustomBitrateMode && selectedQuality == quality)
                Surface(
                  onClick = {
                    if (quality == ExportQuality.CUSTOM) {
                      isCustomBitrateMode = true
                      selectedQuality = ExportQuality.CUSTOM
                    } else {
                      isCustomBitrateMode = false
                      selectedQuality = quality
                    }
                  },
                  shape = RoundedCornerShape(8.dp),
                  color = if (isSelected) SkyBlueContainer else StudioSurfaceVariant,
                  border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) CyanAccent else StudioBorder
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("quality_preset_${quality.name}")
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = when (quality) {
                        ExportQuality.DRAFT -> "Draft"
                        ExportQuality.LOW -> "Low"
                        ExportQuality.STANDARD, ExportQuality.MEDIUM -> "Standard"
                        ExportQuality.HIGH -> "High"
                        ExportQuality.ULTRA -> "Ultra"
                        ExportQuality.CUSTOM -> "Custom"
                      },
                      style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CyanAccentDark else TextPrimary
                      )
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Bitrate Slider & Quick Shortcuts
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = StudioSurfaceVariant,
              border = BorderStroke(1.dp, StudioBorder)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Bitrate Slider",
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = FontWeight.SemiBold,
                      color = TextSecondary
                    )
                  )
                  Text(
                    text = "${(customBitrateKbps / 1000f).roundToInt()} Mbps (${customBitrateKbps} Kbps)",
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = FontWeight.Bold,
                      color = if (isCustomBitrateMode) CyanAccentDark else TextSecondary
                    )
                  )
                }

                Slider(
                  value = customBitrateKbps.toFloat(),
                  onValueChange = { value ->
                    customBitrateKbps = value.roundToInt()
                    isCustomBitrateMode = true
                    selectedQuality = ExportQuality.CUSTOM
                  },
                  valueRange = 1000f..50000f,
                  steps = 97, // 500 Kbps increments
                  colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = StudioBorder
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bitrate_slider")
                )

                // Quick Bitrate Shortcut Chips
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  listOf(4000 to "4M", 8000 to "8M", 12000 to "12M", 20000 to "20M", 35000 to "35M").forEach { (kbps, label) ->
                    val isChipSelected = isCustomBitrateMode && customBitrateKbps == kbps
                    Surface(
                      onClick = {
                        customBitrateKbps = kbps
                        isCustomBitrateMode = true
                        selectedQuality = ExportQuality.CUSTOM
                      },
                      shape = RoundedCornerShape(6.dp),
                      color = if (isChipSelected) CyanAccent else StudioSurface,
                      border = BorderStroke(1.dp, if (isChipSelected) CyanAccent else StudioBorder),
                      modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .testTag("bitrate_shortcut_$label")
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = label,
                          style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isChipSelected) Color.White else TextSecondary
                          )
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          HorizontalDivider(color = StudioBorder)

          // ==============================
          // 5. Output Summary & Pipeline Card
          // ==============================
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = StudioSurfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, StudioBorder)
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Estimated File Size",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Text(
                  text = estimatedMbString,
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                  )
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Video & Audio Codec",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Text(
                  text = "${selectedCodec.label} • AAC 44.1kHz Stereo",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                  )
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Container Format",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Text(
                  text = "MP4 (MPEG-4 Part 14)",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                  )
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Render Engine",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Text(
                  text = "Hardware Video Engine (4K / 2K Ready)",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = GreenAccent
                  )
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Action Buttons ---
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("dismiss_export_dialog_btn"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, StudioBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
          ) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
          }

          Button(
            onClick = {
              onConfirmExport(currentConfig)
            },
            modifier = Modifier
              .weight(2f)
              .height(48.dp)
              .testTag("confirm_export_dialog_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CyanAccent,
              contentColor = Color.White
            )
          ) {
            Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Start Render",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
          }
        }
      }
    }
  }
}

/**
 * Calculates export pixel dimensions honoring project aspect ratio and target resolution.
 */
internal fun calculateExportDimensions(res: Resolution, aspect: AspectRatio): Pair<Int, Int> {
  val (w, h) = when (res) {
    Resolution.RES_SQUARE_2K -> Pair(2048, 2048)
    Resolution.RES_VERTICAL_2K -> Pair(1440, 2560)
    Resolution.RES_VERTICAL_4K -> Pair(2160, 3840)
    else -> {
      val shortSide = minOf(res.width, res.height)
      val longSide = maxOf(res.width, res.height)
      when (aspect) {
        AspectRatio.RATIO_9_16 -> Pair(shortSide, longSide)
        AspectRatio.RATIO_16_9 -> Pair(longSide, shortSide)
        AspectRatio.RATIO_1_1 -> Pair(longSide, longSide)
        AspectRatio.RATIO_4_5 -> Pair((shortSide * 4) / 5, shortSide)
        AspectRatio.RATIO_3_4 -> Pair((shortSide * 3) / 4, shortSide)
        AspectRatio.CUSTOM -> Pair(longSide, shortSide)
      }
    }
  }
  val alignedW = (w / 2) * 2
  val alignedH = (h / 2) * 2
  return Pair(alignedW.coerceIn(320, 3840), alignedH.coerceIn(320, 3840))
}

/**
 * Computes estimated file size in bytes based on duration and export config.
 */
internal fun calculateEstimatedSize(durationMs: Long, config: ExportConfig): Long {
  val durationSec = (durationMs / 1000f).coerceAtLeast(1f)
  val effectiveBitrate = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
    config.customBitrateKbps * 1000L
  } else {
    val baseBitrate = when (config.resolution) {
      Resolution.RES_480P -> 2_500_000L
      Resolution.RES_720P -> 5_000_000L
      Resolution.RES_1080P -> 10_000_000L
      Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000L
      Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000L
      Resolution.RES_SQUARE_2K -> 22_000_000L
    }
    val codecMultiplier = if (config.codecProfile == CodecProfile.H265_HEVC) 0.75f else 1.0f
    (baseBitrate * config.quality.bitrateMultiplier * (config.frameRate.fps / 30f) * codecMultiplier).toLong()
  }
  return (effectiveBitrate * durationSec / 8).toLong()
}
