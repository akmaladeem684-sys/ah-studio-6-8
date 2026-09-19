package com.example.ui.components.effects

import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EffectClip
import com.example.domain.model.EffectType
import com.example.engine.SelectedTrackElement
import com.example.engine.composition.VideoEffectRenderer
import com.example.ui.StudioViewModel
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Global holder for Before/After compare preview toggle state.
 * When true, active visual effects are bypassed during rendering.
 */
var isBeforeAfterComparing by mutableStateOf(false)

/**
 * Navigation state for the Effects Studio system.
 */
enum class EffectsViewType {
  MAIN_HUB,
  VIDEO_EFFECTS,
  BODY_EFFECTS,
  PHOTO_EFFECTS,
  AI_EFFECTS
}

/**
 * Representation of each effect in the library catalog.
 */
data class EffectItem(
  val id: String,
  val name: String,
  val effectType: EffectType?, // null represents "None / Original"
  val category: String = "All",
  val tag: String = "", // "PRO", "AI", "HOT", "NEW", ""
  val accentColor: Color = Color(0xFF00C2FF),
  val defaultIntensity: Float = 0.8f
)

/**
 * Catalogs for each of the 4 dedicated effect categories.
 */
object EffectsCatalog {

  // 1. VIDEO EFFECTS
  val VIDEO_EFFECTS_CATEGORIES = listOf("All", "Trending / Viral", "Motions", "Light", "Texture", "Celebrate", "Party", "Glitch & Distortion", "Retro / Vintage", "Light / Glow Effects", "Blur / Focus Effects", "Color & Filter Effects", "Split / Mirror / 3D", "Transitions")

  val VIDEO_EFFECTS: List<EffectItem> = listOf(
    EffectItem("ve_none", "None", null, "All", "", Color(0xFF64748B), 0.0f),
    
    // Motions
    EffectItem("ve_shake", "Camera Shake", EffectType.SHAKE, "Motions", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("ve_zoom", "Zoom Pulse", EffectType.ZOOM, "Motions", "HOT", Color(0xFF8B5CF6), 0.8f),
    EffectItem("ve_skater", "Skater Zoom", EffectType.SKATER_ZOOM, "Motions", "PRO", Color(0xFF06B6D4), 0.85f),
    EffectItem("ve_vertigo", "Vertigo Dolly", EffectType.VERTIGO_DOLLY, "Motions", "PRO", Color(0xFFA855F7), 0.8f),
    EffectItem("ve_spin", "360 Spin", EffectType.SPIN, "Motions", "", Color(0xFF3B82F6), 0.75f),
    EffectItem("ve_wander", "Wander Pan", EffectType.CAMERA_MOVEMENT, "Motions", "", Color(0xFF10B981), 0.8f),
    EffectItem("ve_warp", "Warp Speed", EffectType.WARP_SPEED, "Motions", "PRO", Color(0xFFEC4899), 0.9f),
    EffectItem("ve_ripple", "Shockwave", EffectType.RIPPLE, "Motions", "", Color(0xFFF43F5E), 0.85f),
    EffectItem("ve_wave", "Wave Ripple", EffectType.WAVE, "Motions", "", Color(0xFF00C2FF), 0.8f),

    // Light
    EffectItem("ve_glow", "Glow", EffectType.GLOW, "Light", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("ve_flare", "Lens Flare", EffectType.LENS_FLARE, "Light", "PRO", Color(0xFFF59E0B), 0.85f),
    EffectItem("ve_leak", "Light Leak", EffectType.LIGHT_LEAK, "Light", "HOT", Color(0xFFFB923C), 0.75f),
    EffectItem("ve_solar", "Solar Flare", EffectType.SOLAR_FLARE, "Light", "PRO", Color(0xFFFACC15), 0.9f),
    EffectItem("ve_bokeh", "Bokeh Dreams", EffectType.BOKEH, "Light", "PRO", Color(0xFFE879F9), 0.75f),
    EffectItem("ve_golden", "Golden Hour", EffectType.GOLDEN_HOUR, "Light", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("ve_flash", "White Flash", EffectType.FLASH, "Light", "", Color(0xFFFFFFFF), 0.9f),
    EffectItem("ve_halo", "Halo Glow", EffectType.HALO_GLOW, "Light", "", Color(0xFF38BDF8), 0.8f),
    EffectItem("ve_laser", "Laser Grid", EffectType.LASER_GRID, "Light", "PRO", Color(0xFF22C55E), 0.9f),
    EffectItem("ve_strobe", "RGB Strobe", EffectType.STROBE, "Light", "PRO", Color(0xFF38BDF8), 0.9f),

    // Texture
    EffectItem("ve_vhs", "1998 VHS", EffectType.VHS_VINTAGE, "Texture", "HOT", Color(0xFFF59E0B), 0.8f),
    EffectItem("ve_crt", "CRT Screen", EffectType.CRT_TV, "Texture", "", Color(0xFF10B981), 0.75f),
    EffectItem("ve_noise", "Noise Grain", EffectType.NOISE, "Texture", "", Color(0xFF94A3B8), 0.75f),
    EffectItem("ve_vignette", "Vignette Dark", EffectType.VIGNETTE, "Texture", "", Color(0xFF64748B), 0.8f),
    EffectItem("ve_blur", "Blur", EffectType.BLUR, "Texture", "", Color(0xFF38BDF8), 0.7f),
    EffectItem("ve_mblur", "Motion Blur", EffectType.MOTION_BLUR, "Texture", "", Color(0xFF6366F1), 0.75f),
    EffectItem("ve_soft", "Soft Focus", EffectType.SOFT_FOCUS, "Texture", "", Color(0xFFF472B6), 0.8f),
    EffectItem("ve_sharpen", "Sharpen", EffectType.SHARPEN, "Texture", "", Color(0xFF00E5FF), 0.85f),

    // Celebrate
    EffectItem("ve_confetti", "Golden Confetti", EffectType.CELEBRATE_CONFETTI, "Celebrate", "HOT", Color(0xFFFACC15), 0.9f),
    EffectItem("ve_fireworks", "Neon Fireworks", EffectType.CELEBRATE_FIREWORKS, "Celebrate", "PRO", Color(0xFFEC4899), 0.9f),
    EffectItem("ve_sparks", "Fire Sparks", EffectType.FIRE_SPARK, "Celebrate", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("ve_halo_c", "Halo Sparkle", EffectType.HALO_GLOW, "Celebrate", "", Color(0xFF38BDF8), 0.8f),

    // Party
    EffectItem("ve_rgbsplit", "RGB Split", EffectType.RGB_SPLIT, "Party", "HOT", Color(0xFF3B82F6), 0.8f),
    EffectItem("ve_glitch", "Glitch Scan", EffectType.GLITCH, "Party", "HOT", Color(0xFFEF4444), 0.85f),
    EffectItem("ve_acid", "Acid Trip", EffectType.ACID_TRIP, "Party", "HOT", Color(0xFFD946EF), 0.85f),
    EffectItem("ve_mirror", "Mirror Prism", EffectType.MIRROR, "Party", "", Color(0xFFEC4899), 0.8f),
    EffectItem("ve_fisheye", "Fisheye Lens", EffectType.FISHEYE, "Party", "", Color(0xFF10B981), 0.8f),
    EffectItem("ve_prism", "Prism Rainbow", EffectType.PARTY_PRISM, "Party", "PRO", Color(0xFF8B5CF6), 0.85f),
    EffectItem("ve_confused", "Confused Wobble", EffectType.PARTY_CONFUSED, "Party", "", Color(0xFFF59E0B), 0.8f),

    // Extended professional 200-effect library
    EffectItem("vfx_viral_1", "Zoom Blur", EffectType.VFX_VIRAL_1, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_2", "Speed Ramp", EffectType.VFX_VIRAL_2, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_3", "Bullet Time", EffectType.VFX_VIRAL_3, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_4", "Glitch Pop", EffectType.VFX_VIRAL_4, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_5", "RGB Split", EffectType.VFX_VIRAL_5, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_6", "Shake Zoom", EffectType.VFX_VIRAL_6, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_7", "Freeze Frame", EffectType.VFX_VIRAL_7, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_8", "Time Warp", EffectType.VFX_VIRAL_8, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_9", "Datamosh", EffectType.VFX_VIRAL_9, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_10", "Pixel Sort", EffectType.VFX_VIRAL_10, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_11", "Flash Transition", EffectType.VFX_VIRAL_11, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_12", "Heartbeat Zoom", EffectType.VFX_VIRAL_12, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_13", "Echo Trail", EffectType.VFX_VIRAL_13, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_14", "Motion Blur Punch", EffectType.VFX_VIRAL_14, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_15", "Camera Shake", EffectType.VFX_VIRAL_15, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_16", "Zoom Punch In", EffectType.VFX_VIRAL_16, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_17", "Zoom Punch Out", EffectType.VFX_VIRAL_17, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_18", "Slide Reveal", EffectType.VFX_VIRAL_18, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_19", "Whip Pan", EffectType.VFX_VIRAL_19, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_20", "Spin Zoom", EffectType.VFX_VIRAL_20, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_21", "Aura Glow", EffectType.VFX_VIRAL_21, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_22", "VN Style Glow", EffectType.VFX_VIRAL_22, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_23", "Cinematic Flicker", EffectType.VFX_VIRAL_23, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_24", "Light Leak Pass", EffectType.VFX_VIRAL_24, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_25", "Particle Burst", EffectType.VFX_VIRAL_25, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_26", "Confetti Pop", EffectType.VFX_VIRAL_26, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_27", "Screen Crack", EffectType.VFX_VIRAL_27, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_28", "Kaleidoscope", EffectType.VFX_VIRAL_28, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_29", "Chromatic Aberration", EffectType.VFX_VIRAL_29, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_30", "Old TV Static", EffectType.VFX_VIRAL_30, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_31", "Signal Loss", EffectType.VFX_VIRAL_31, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_32", "Double Exposure", EffectType.VFX_VIRAL_32, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_33", "Ghost Trail", EffectType.VFX_VIRAL_33, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_34", "Speed Blur Streak", EffectType.VFX_VIRAL_34, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_35", "Bounce Zoom", EffectType.VFX_VIRAL_35, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_36", "Flash Bang", EffectType.VFX_VIRAL_36, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_37", "Neon Trace", EffectType.VFX_VIRAL_37, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_38", "Fireworks Burst", EffectType.VFX_VIRAL_38, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_39", "Rain Overlay", EffectType.VFX_VIRAL_39, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_viral_40", "Snowfall Overlay", EffectType.VFX_VIRAL_40, "Trending / Viral", "HOT", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_1", "VHS Glitch", EffectType.VFX_GLITCH_1, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_2", "Digital Noise", EffectType.VFX_GLITCH_2, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_3", "Screen Tear", EffectType.VFX_GLITCH_3, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_4", "Wave Distortion", EffectType.VFX_GLITCH_4, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_5", "Liquid Melt", EffectType.VFX_GLITCH_5, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_6", "Static Interference", EffectType.VFX_GLITCH_6, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_7", "Broken Signal", EffectType.VFX_GLITCH_7, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_8", "Analog Glitch", EffectType.VFX_GLITCH_8, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_9", "Corrupted Frame", EffectType.VFX_GLITCH_9, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_10", "Pixel Stretch", EffectType.VFX_GLITCH_10, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_11", "Scan Line Flicker", EffectType.VFX_GLITCH_11, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_12", "Color Bleed", EffectType.VFX_GLITCH_12, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_13", "Frame Skip", EffectType.VFX_GLITCH_13, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_14", "Jitter Shake", EffectType.VFX_GLITCH_14, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_15", "Warp Distort", EffectType.VFX_GLITCH_15, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_16", "Bad Signal Roll", EffectType.VFX_GLITCH_16, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_17", "TV Turn Off", EffectType.VFX_GLITCH_17, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_18", "CRT Curve", EffectType.VFX_GLITCH_18, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_19", "Frame Drop Stutter", EffectType.VFX_GLITCH_19, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_glitch_20", "Data Corruption", EffectType.VFX_GLITCH_20, "Glitch & Distortion", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_1", "VHS Tape", EffectType.VFX_RETRO_1, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_2", "Old Film Grain", EffectType.VFX_RETRO_2, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_3", "Sepia Fade", EffectType.VFX_RETRO_3, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_4", "Vintage 90s", EffectType.VFX_RETRO_4, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_5", "8mm Film", EffectType.VFX_RETRO_5, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_6", "Polaroid Frame", EffectType.VFX_RETRO_6, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_7", "Film Burn", EffectType.VFX_RETRO_7, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_8", "Dust & Scratches", EffectType.VFX_RETRO_8, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_9", "Faded Color", EffectType.VFX_RETRO_9, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_10", "Retro TV Frame", EffectType.VFX_RETRO_10, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_11", "Disco Ball", EffectType.VFX_RETRO_11, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
    EffectItem("vfx_retro_12", "Neon 80s", EffectType.VFX_RETRO_12, "Retro / Vintage", "", Color(0xFF00C2FF), 0.82f),
/**
 * Main Effects Studio View Panel Composable.
 */
@Composable
fun VideoEffectsToolPanel(
  viewModel: StudioViewModel,
  onDismiss: () -> Unit
) {
  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }

  val allEffects = EffectsCatalog.VIDEO_EFFECTS + EffectsCatalog.BODY_EFFECTS

  val filteredEffects = remember(selectedCategory, searchQuery) {
    allEffects.filter { effect ->
      val matchesCategory = if (selectedCategory == "All") true else effect.category.contains(selectedCategory, ignoreCase = true)
      val matchesSearch = if (searchQuery.isBlank()) true else effect.name.contains(searchQuery, ignoreCase = true)
      matchesCategory && matchesSearch
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(420.dp)
      .background(StudioSurface)
      .padding(top = 10.dp)
  ) {
    // Header Handle & Title
    Box(
      modifier = Modifier
        .width(36.dp)
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(Color(0xFF444444))
        .align(Alignment.CenterHorizontally)
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Top Action Row (Title & Close)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Visual Effects",
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
      IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Category Tabs (Horizontal Scrollable)
    LazyRow(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(EffectsCatalog.VIDEO_EFFECTS_CATEGORIES) { cat ->
        val isSelected = cat == selectedCategory
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else Color(0xFF222222))
            .clickable { selectedCategory = cat }
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Text(
            text = cat,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 4-Column Grid of 200 Effects
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      items(filteredEffects) { effect ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clickable {
              effect.effectType?.let { type ->
                // Apply effect via ViewModel engine
                viewModel.applyEffect(type)
              }
            }
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(72.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF252525))
              .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
          ) {
            Text(
              text = effect.name.take(2).uppercase(),
              color = effect.accentColor,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold
            )

            if (effect.tag.isNotEmpty()) {
              Box(
                modifier = Modifier
                  .padding(4.dp)
                  .align(Alignment.TopStart)
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (effect.tag == "AI") Color(0xFF9C27B0) else Color(0xFFEF4444))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text(
                  text = effect.tag,
                  color = Color.White,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = effect.name,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}