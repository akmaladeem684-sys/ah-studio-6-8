package com.example.engine.controller

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates a user timeline tap across the CTI, playback clock, and preview.
 *
 * Timeline taps are serialized so a rapid series of taps cannot let an older
 * seek overwrite the latest one. PlaybackController already coalesces stale
 * seek generations and only seeks the current media item when necessary; this
 * handler therefore never reloads media or resets the decoder itself.
 */
class TimelineTouchSyncHandler(
    private val masterClock: MasterPlaybackClock,
    private val playbackController: PlaybackController
) {
    private val tapMutex = Mutex()

    suspend fun onTimelineTapped(clickX: Float, pixelsPerMs: Float) {
        require(pixelsPerMs > 0f) { "pixelsPerMs must be greater than zero" }

        val targetTimeMs = (clickX / pixelsPerMs)
            .toLong()
            .coerceAtLeast(0L)

        tapMutex.withLock {
            // Pause first so the old clock position cannot continue advancing
            // while the preview is being moved to the tapped frame.
            playbackController.handlePausePress()

            // Seek the preview through the controller. Its threshold prevents
            // unnecessary decoder seeks for taps at the current position.
            playbackController.seekTo(targetTimeMs, resumeAfter = false)

            // Publish the CTI position from the same tap target. The controller
            // owns the playback clock internally, while this reference keeps
            // external timeline projections synchronized as well.
            masterClock.seekTo(targetTimeMs)
        }
    }
}
