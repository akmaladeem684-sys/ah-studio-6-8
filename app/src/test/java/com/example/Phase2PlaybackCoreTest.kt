package com.example

import com.example.engine.playback.MasterTimelineClock
import com.example.engine.playback.PlaybackStateMachine
import com.example.engine.playback.PreviewPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2PlaybackCoreTest {
  @Test fun stateMachineAllowsDeterministicPlayPause() {
    val machine = PlaybackStateMachine()
    assertTrue(machine.transition(PreviewPlaybackState.READY))
    assertTrue(machine.transition(PreviewPlaybackState.PLAYING))
    assertTrue(machine.transition(PreviewPlaybackState.PAUSED))
    assertEquals(PreviewPlaybackState.PAUSED, machine.state.value)
  }

  @Test fun stateMachineRejectsInvalidTransition() {
    val machine = PlaybackStateMachine()
    assertFalse(machine.transition(PreviewPlaybackState.PLAYING))
    assertEquals(PreviewPlaybackState.IDLE, machine.state.value)
  }

  @Test fun masterClockIsExactWhenPausedAndSeeked() {
    val clock = MasterTimelineClock()
    clock.seek(10_000_000L)
    assertEquals(10_000_000L, clock.positionUs())
    clock.start(20_000_000L)
    assertTrue(clock.isRunning())
    val paused = clock.pause()
    assertTrue(paused >= 20_000_000L)
    assertFalse(clock.isRunning())
    clock.seek(30_000_000L)
    assertEquals(30_000_000L, clock.positionUs())
  }
}
