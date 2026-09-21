package com.example.engine.export

import org.junit.Assert.*
import org.junit.Test

class BlankFrameGuardTest {
  @Test fun blackAndWhiteLuma(){assertEquals(0,BlankFrameGuard.luma(0xFF000000.toInt()));assertEquals(255,BlankFrameGuard.luma(0xFFFFFFFF.toInt()))}
  @Test fun blankDetection(){assertTrue(BlankFrameGuard.isBlank(IntArray(64){0xFF000000.toInt()}));assertFalse(BlankFrameGuard.isBlank(intArrayOf(0xFF404040.toInt())))}
  @Test fun emptySamplesAreNotBlank(){assertFalse(BlankFrameGuard.isBlank(IntArray(0)))}
  @Test fun gridStaysInside(){val p=BlankFrameGuard.gridPoints(64,36,8);assertEquals(128,p.size);for(i in p.indices step 2){assertTrue(p[i] in 0..63);assertTrue(p[i+1] in 0..35)}}
  @Test fun probeSchedule(){val g=BlankFrameGuard();assertTrue(g.shouldProbe(0));assertTrue(g.shouldProbe(2));assertFalse(g.shouldProbe(3));assertTrue(g.shouldProbe(90))}
  @Test fun retryThenGiveUp(){val g=BlankFrameGuard();assertEquals(BlankFrameGuard.Verdict.RETRY_WITH_KOTLIN_PATH,g.evaluate(true,false));assertEquals(BlankFrameGuard.Verdict.GIVE_UP,g.evaluate(true,false));assertEquals(BlankFrameGuard.Verdict.OK,g.evaluate(true,true))}
}