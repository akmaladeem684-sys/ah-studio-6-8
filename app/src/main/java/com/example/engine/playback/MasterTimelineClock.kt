package com.example.engine.playback

/** Monotonic master clock. Timeline position is represented in integer microseconds. */
class MasterTimelineClock {
  private var anchorUs = 0L
  private var anchorRealtimeNs = 0L
  private var speed = 1.0
  private var running = false

  @Synchronized
  fun start(positionUs: Long) {
    anchorUs = positionUs.coerceAtLeast(0L)
    anchorRealtimeNs = System.nanoTime()
    running = true
  }

  @Synchronized
  fun pause(): Long {
    val position = positionUs()
    anchorUs = position
    running = false
    return position
  }

  @Synchronized
  fun seek(positionUs: Long) {
    anchorUs = positionUs.coerceAtLeast(0L)
    anchorRealtimeNs = System.nanoTime()
  }

  @Synchronized
  fun setSpeed(value: Double) {
    val current = positionUs()
    anchorUs = current
    anchorRealtimeNs = System.nanoTime()
    speed = value.coerceIn(0.01, 16.0)
  }

  @Synchronized
  fun positionUs(): Long {
    if (!running) return anchorUs
    val elapsedNs = (System.nanoTime() - anchorRealtimeNs).coerceAtLeast(0L)
    return anchorUs + (elapsedNs.toDouble() * speed / 1_000.0).toLong()
  }

  @Synchronized fun isRunning(): Boolean = running
  @Synchronized fun playbackSpeed(): Double = speed
}
