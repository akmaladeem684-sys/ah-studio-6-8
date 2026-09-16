package com.example.domain.command

import com.example.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class TimelineTransactionManager(
    initialTracks: List<Track> = emptyList()
) {
    private val undoStack = ArrayDeque<TimelineCommand>()
    private val redoStack = ArrayDeque<TimelineCommand>()

    private val _tracksState = MutableStateFlow(initialTracks)
    val tracksState: StateFlow<List<Track>> = _tracksState.asStateFlow()

    fun executeCommand(command: TimelineCommand): Boolean {
        val currentTracks = _tracksState.value
        val newTracks = command.execute(currentTracks)
        if (newTracks != currentTracks) {
            undoStack.push(command)
            redoStack.clear()
            _tracksState.value = newTracks
            return true
        }
        return false
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val command = undoStack.pop()
        val newTracks = command.undo(_tracksState.value)
        redoStack.push(command)
        _tracksState.value = newTracks
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val command = redoStack.pop()
        val newTracks = command.execute(_tracksState.value)
        undoStack.push(command)
        _tracksState.value = newTracks
        return true
    }

    fun setTracksDirectly(tracks: List<Track>) {
        undoStack.clear()
        redoStack.clear()
        _tracksState.value = tracks
    }

    fun getCurrentSnapshot(): List<Track> = _tracksState.value
}
