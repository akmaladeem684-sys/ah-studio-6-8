package com.example.domain.model

/**
 * Timeline clip representation used by the command/undo system.
 *
 * This model intentionally keeps the command layer independent from the
 * media-specific clip types used by the renderer.
 */
data class TimelineClip(
    val id: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val sourceStartMs: Long = 0L
)

/** A track containing the clips operated on by timeline commands. */
data class TimelineTrack(
    val id: String,
    val clips: List<TimelineClip> = emptyList()
)
