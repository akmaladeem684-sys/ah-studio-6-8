package com.example.domain.command

import com.example.domain.model.Track

interface TimelineCommand {
    val description: String
    fun execute(tracks: List<Track>): List<Track>
    fun undo(tracks: List<Track>): List<Track>
}
