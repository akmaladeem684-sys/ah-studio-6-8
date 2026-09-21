package com.example.engine.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectChainPackerTest {
  @Test
  fun emptyChainIsJustHeader() {
    assertEquals(listOf(1f, 0f), EffectChainPacker.pack(emptyList()).toList())
  }

  @Test
  fun wireLayoutMatchesNativeParser() {
    val params = FloatArray(8) { Float.NaN }.also { it[0] = 12f }
    val inst = FxInstance(
      id = NativeEffectId.GAUSSIAN_BLUR, startMs = 100f, endMs = 900f, intensity = 0.5f, params = params,
      tracks = listOf(FxTrack(-1, listOf(FxKey(0f, 1f), FxKey(1000f, 0f, FxKey.EASE_HOLD))))
    )
    val w = EffectChainPacker.pack(listOf(inst))
    assertEquals(23, w.size)
    assertEquals(1f, w[0], 0f); assertEquals(1f, w[1], 0f)
    assertEquals(1f, w[2], 0f); assertEquals(100f, w[3], 0f); assertEquals(900f, w[4], 0f); assertEquals(0.5f, w[5], 0f)
    assertEquals(12f, w[6], 0f); assertTrue(w[7].isNaN())
    assertEquals(1f, w[14], 0f)
    assertEquals(-1f, w[15], 0f); assertEquals(2f, w[16], 0f)
    assertEquals(listOf(0f, 1f, 0f, 1000f, 0f, 4f), w.slice(17..22))
  }

  @Test
  fun limitsAreEnforced() {
    val many = List(50) { FxInstance(NativeEffectId.INVERT) }
    assertEquals(32f, EffectChainPacker.pack(many)[1], 0f)
    val keys = List(200) { FxKey(it.toFloat(), 0f) }
    val w = EffectChainPacker.pack(listOf(FxInstance(NativeEffectId.INVERT, tracks = List(20) { FxTrack(0, keys) })))
    assertEquals(9f, w[14], 0f)
    assertEquals(64f, w[16], 0f)
  }

  @Test
  fun appEffectNamesMapToRealEffects() {
    assertEquals(NativeEffectId.DIRECTIONAL_BLUR, EffectChainPacker.nativeIdForEffectName("MOTION_BLUR"))
    assertEquals(NativeEffectId.GAUSSIAN_BLUR, EffectChainPacker.nativeIdForEffectName("VFX_BLUR_3"))
    assertEquals(NativeEffectId.GLITCH, EffectChainPacker.nativeIdForEffectName("VFX_GLITCH_7"))
    assertEquals(NativeEffectId.RGB_SPLIT, EffectChainPacker.nativeIdForEffectName("rgb_split"))
    assertEquals(NativeEffectId.ROTATE, EffectChainPacker.nativeIdForEffectName("SPIN"))
    assertEquals(NativeEffectId.ZOOM_PULSE, EffectChainPacker.nativeIdForEffectName("SKATER_ZOOM"))
    assertEquals(NativeEffectId.LENS_FLARE, EffectChainPacker.nativeIdForEffectName("LENS_FLARE"))
    assertEquals(NativeEffectId.VHS, EffectChainPacker.nativeIdForEffectName("VHS_VINTAGE"))
    assertEquals(NativeEffectId.BLOOM, EffectChainPacker.nativeIdForEffectName("FIRE_AURA"))
    assertEquals(NativeEffectId.BLOOM, EffectChainPacker.nativeIdForEffectName(""))
  }

  @Test
  fun spinGetsContinuousRotationAndOthersUseDefaults() {
    val spin = EffectChainPacker.forEffectTypeName("SPIN", 0.7f, 250f)
    assertEquals(90f, spin.params[2], 0f)
    assertEquals(250f, spin.startMs, 0f)
    assertEquals(0.7f, spin.intensity, 0f)
    assertTrue(EffectChainPacker.forEffectTypeName("GLOW", 1f, 0f).params.all { it.isNaN() })
  }
}
