package com.example

import com.example.domain.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfessionalEffectsCatalogTest {
  @Test fun extendedEffectLibraryContainsExactly200Effects() {
    assertEquals(200, EffectType.values().count { it.name.startsWith("VFX_") })
  }
}
