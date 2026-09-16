package com.example.engine.integration

import com.example.domain.model.Timeline
import java.util.ArrayDeque

/** Lightweight structural, non-destructive history. Media is never copied or modified. */
class TimelineCommandSystem(private val maxHistorySize: Int = 100) {
  private data class Entry(val before: Timeline, val after: Timeline, val name: String)
  private val undo = ArrayDeque<Entry>(); private val redo = ArrayDeque<Entry>()
  val canUndo get() = undo.isNotEmpty(); val canRedo get() = redo.isNotEmpty()
  fun execute(name: String, before: Timeline, after: Timeline): Timeline { if (before != after) { undo.addLast(Entry(before, after, name)); while (undo.size > maxHistorySize) undo.removeFirst(); redo.clear() }; return after }
  fun undo(current: Timeline): Timeline? = if (undo.isEmpty()) null else undo.removeLast().also { redo.addLast(it) }.before
  fun redo(current: Timeline): Timeline? = if (redo.isEmpty()) null else redo.removeLast().also { undo.addLast(it) }.after
  fun clear() { undo.clear(); redo.clear() }
}
