package com.example.engine.audio.phase3

/** Central, immutable-friendly audio timeline container. UI edits should replace this state via commands. */
class AudioTimeline(initialTracks: List<TimelineAudioTrack> = emptyList()) {
  @Volatile private var _tracks: List<TimelineAudioTrack> = normalize(initialTracks)
  val tracks: List<TimelineAudioTrack> get() = _tracks

  fun addTrack(id: String = "audio-${System.nanoTime()}"): TimelineAudioTrack {
    val track = TimelineAudioTrack(id = id, order = (_tracks.maxOfOrNull { it.order } ?: -1) + 1)
    _tracks = normalize(_tracks + track)
    return track
  }

  fun removeTrack(trackId: String) { _tracks = normalize(_tracks.filterNot { it.id == trackId }) }

  fun setTrackState(trackId: String, volume: Float? = null, mute: Boolean? = null, solo: Boolean? = null, enabled: Boolean? = null) {
    _tracks = _tracks.map { if (it.id != trackId) it else it.copy(
      volume = volume?.coerceIn(0f, 4f) ?: it.volume,
      mute = mute ?: it.mute,
      solo = solo ?: it.solo,
      enabled = enabled ?: it.enabled
    ) }
  }

  fun addClip(clip: TimelineAudioClip) {
    _tracks = _tracks.map { if (it.id == clip.trackId) it.copy(clips = it.clips + clip) else it }
  }

  fun updateClip(clip: TimelineAudioClip) {
    _tracks = _tracks.map { if (it.clips.any { c -> c.id == clip.id }) it.copy(clips = it.clips.map { c -> if (c.id == clip.id) clip else c }) else it }
  }

  fun removeClip(clipId: String) { _tracks = _tracks.map { it.copy(clips = it.clips.filterNot { c -> c.id == clipId }) } }

  fun splitClip(clipId: String, atTimelineMs: Long): Pair<TimelineAudioClip, TimelineAudioClip>? {
    val original = _tracks.asSequence().flatMap { it.clips.asSequence() }.firstOrNull { it.id == clipId } ?: return null
    if (atTimelineMs <= original.timelineStartMs || atTimelineMs >= original.timelineEndMs) return null
    val leftDuration = atTimelineMs - original.timelineStartMs
    val rightSource = original.timelineToSourceMs(atTimelineMs)
    val left = original.copy(id = original.id, timelineEndMs = atTimelineMs, sourceEndMs = rightSource)
    val right = original.copy(
      id = "${original.id}-split-${atTimelineMs}",
      timelineStartMs = atTimelineMs,
      sourceStartMs = rightSource
    )
    updateClip(left); addClip(right)
    return left to right
  }

  private fun normalize(input: List<TimelineAudioTrack>): List<TimelineAudioTrack> = input
    .mapIndexed { index, t -> t.copy(order = index, clips = t.clips.sortedWith(compareBy<TimelineAudioClip> { it.timelineStartMs }.thenBy { it.id })) }
    .sortedBy { it.order }
}
