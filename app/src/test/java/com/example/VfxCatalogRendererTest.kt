package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.domain.model.EffectType
import com.example.engine.composition.VfxCatalogRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VfxCatalogRendererTest {

  @Test
  fun everyVfxCatalogEntryHasConcreteRendererRouting() {
    val vfx = EffectType.values().filter { it.name.startsWith("VFX_") }
    assertEquals(196, vfx.size)

    val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    try {
      vfx.forEach { effect ->
        assertTrue("No catalog renderer for " + effect.name, VfxCatalogRenderer.supports(effect))
        assertTrue(
          "Catalog effect rendered no concrete operation: " + effect.name,
          VfxCatalogRenderer.render(canvas, effect, 0.8f, 500L, 320, 180)
        )
      }
    } finally {
      bitmap.recycle()
    }
  }

  @Test
  fun allThirtyBodyCatalogEffectsUseMlBridge() {
    val body = EffectType.values().filter { it.name.startsWith("VFX_BODY_") }
    assertEquals(30, body.size)
    assertTrue(body.all(VfxCatalogRenderer::requiresMlDeformation))
  }
}
