package com.example.domain.command

import com.example.domain.model.Clip
import com.example.domain.model.Track

object TimelineCommands {

    data class SplitClipCommand(
        val trackId: String,
        val clipId: String,
        val splitTimeMs: Long
    ) : TimelineCommand {
        override val description: String = "Split clip $clipId at $splitTimeMs ms"

        override fun execute(tracks: List<Track>): List<Track> {
            return tracks.map { track ->
                if (track.id != trackId) return@map track
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex == -1) return@map track

                val clip = track.clips[clipIndex]
                val offset = splitTimeMs - clip.startTimeMs
                if (offset <= 0 || offset >= clip.durationMs) return@map track

                val firstHalf = clip.copy(
                    durationMs = offset
                )
                val secondHalf = clip.copy(
                    id = "${clip.id}_split_${System.currentTimeMillis()}",
                    startTimeMs = splitTimeMs,
                    durationMs = clip.durationMs - offset,
                    sourceStartMs = clip.sourceStartMs + offset
                )

                val updatedClips = track.clips.toMutableList().apply {
                    removeAt(clipIndex)
                    add(clipIndex, firstHalf)
                    add(clipIndex + 1, secondHalf)
                }
                track.copy(clips = updatedClips)
            }
        }

        override fun undo(tracks: List<Track>): List<Track> {
            return tracks.map { track ->
                if (track.id != trackId) return@map track
                val firstIndex = track.clips.indexOfFirst { it.id == clipId }
                if (firstIndex == -1 || firstIndex >= track.clips.size - 1) return@map track

                val firstClip = track.clips[firstIndex]
                val secondClip = track.clips[firstIndex + 1]

                val mergedClip = firstClip.copy(
                    durationMs = firstClip.durationMs + secondClip.durationMs
                )

                val updatedClips = track.clips.toMutableList().apply {
                    removeAt(firstIndex + 1)
                    removeAt(firstIndex)
                    add(firstIndex, mergedClip)
                }
                track.copy(clips = updatedClips)
            }
        }
    }

    data class TrimClipCommand(
        val trackId: String,
        val clipId: String,
        val newStartMs: Long,
        val newDurationMs: Long,
        val oldStartMs: Long,
        val oldDurationMs: Long
    ) : TimelineCommand {
        override val description: String = "Trim clip $clipId"

        override fun execute(tracks: List<Track>): List<Track> {
            return applyTrim(tracks, newStartMs, newDurationMs)
        }

        override fun undo(tracks: List<Track>): List<Track> {
            return applyTrim(tracks, oldStartMs, oldDurationMs)
        }

        private fun applyTrim(tracks: List<Track>, start: Long, duration: Long): List<Track> {
            return tracks.map { track ->
                if (track.id != trackId) return@map track
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(startTimeMs = start, durationMs = duration) else clip
                }
                track.copy(clips = updatedClips)
            }
        }
    }
}
