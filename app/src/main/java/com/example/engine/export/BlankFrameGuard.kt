package com.example.engine.export

class BlankFrameGuard(private val maxNativeRetries: Int = 1, private val probeEveryNFrames: Int = 90, private val alwaysProbeFirstFrames: Int = 3) {
  enum class Verdict { OK, RETRY_WITH_KOTLIN_PATH, GIVE_UP }
  private var retries = 0
  fun shouldProbe(frameIndex: Int): Boolean = frameIndex < alwaysProbeFirstFrames || (probeEveryNFrames > 0 && frameIndex % probeEveryNFrames == 0)
  fun evaluate(outputBlank: Boolean, sourceBlank: Boolean): Verdict {
    if (!outputBlank || sourceBlank) return Verdict.OK
    if (retries < maxNativeRetries) { retries++; return Verdict.RETRY_WITH_KOTLIN_PATH }
    return Verdict.GIVE_UP
  }
  companion object {
    fun luma(argb: Int): Int { val r=(argb shr 16) and 255; val g=(argb shr 8) and 255; val b=argb and 255; return (r*299+g*587+b*114)/1000 }
    fun isBlank(argbSamples: IntArray, maxLuma: Int = 6): Boolean { if(argbSamples.isEmpty()) return false; return argbSamples.all { luma(it) <= maxLuma } }
    fun gridPoints(width: Int, height: Int, grid: Int = 8): IntArray {
      if(width<=0||height<=0||grid<=0)return IntArray(0); val out=IntArray(grid*grid*2); var i=0
      for(gy in 0 until grid) for(gx in 0 until grid){out[i++]=((gx+.5f)*width/grid).toInt().coerceIn(0,width-1);out[i++]=((gy+.5f)*height/grid).toInt().coerceIn(0,height-1)}
      return out
    }
  }
}