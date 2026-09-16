package com.example.engine.integration

import com.example.domain.model.AudioClip
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.domain.model.TextClip
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Immutable interval used by the indexed, legacy-compatible timeline facade. */
data class TimelineIndexItem(
  val id: String,
  val track: String,
  val startMs: Long,
  val endMs: Long,
  val layer: Int = 0
) {
  init { require(endMs >= startMs) { "Timeline interval must not have a negative duration" } }
  fun contains(timeMs: Long): Boolean = timeMs >= startMs && timeMs < endMs
  fun intersects(start: Long, end: Long): Boolean = start < endMs && end > startMs
}

/**
 * Read-only interval tree. Ah Studio's existing Timeline remains the source of truth;
 * this structure is rebuilt from a snapshot after an edit and is never persisted.
 */
class IntervalTree private constructor(private val root: Node?) {
  private data class Node(val item: TimelineIndexItem, val maxEndMs: Long, val left: Node?, val right: Node?)

  companion object {
    fun build(items: Collection<TimelineIndexItem>): IntervalTree {
      val sorted = items.sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.id }))
      fun make(lo: Int, hi: Int): Node? {
        if (lo > hi) return null
        val mid = (lo + hi) ushr 1
        val left = make(lo, mid - 1)
        val right = make(mid + 1, hi)
        return Node(sorted[mid], maxOf(sorted[mid].endMs, left?.maxEndMs ?: Long.MIN_VALUE, right?.maxEndMs ?: Long.MIN_VALUE), left, right)
      }
      return IntervalTree(make(0, sorted.lastIndex))
    }
  }

  fun queryPoint(timeMs: Long): List<TimelineIndexItem> = buildList { point(root, timeMs, this) }
  private fun point(node: Node?, time: Long, out: MutableList<TimelineIndexItem>) {
    if (node == null || node.maxEndMs <= time) return
    if (node.left?.maxEndMs ?: Long.MIN_VALUE > time) point(node.left, time, out)
    if (node.item.contains(time)) out += node.item
    if (node.item.startMs <= time) point(node.right, time, out)
  }
  fun queryRange(startMs: Long, endMs: Long): List<TimelineIndexItem> = buildList { range(root, startMs, endMs, this) }
  private fun range(node: Node?, start: Long, end: Long, out: MutableList<TimelineIndexItem>) {
    if (node == null || start >= end || node.maxEndMs <= start) return
    range(node.left, start, end, out)
    if (node.item.intersects(start, end)) out += node.item
    if (node.item.startMs < end) range(node.right, start, end, out)
  }
}

class TimelineSnapIndex private constructor(private val points: LongArray) {
  fun findClosest(candidateMs: Long, thresholdMs: Long, additionalPoints: LongArray = LongArray(0)): Long? {
    var best: Long? = null
    var distance = thresholdMs + 1
    val insertion = Arrays.binarySearch(points, candidateMs).let { if (it >= 0) it else -it - 1 }
    val from = maxOf(0, insertion - 2)
    val to = minOf(points.lastIndex, insertion + 2)
    for (i in from..to) {
      val d = abs(points[i] - candidateMs)
      if (d <= thresholdMs && d < distance) { distance = d; best = points[i] }
    }
    for (point in additionalPoints) {
      val d = abs(point - candidateMs)
      if (d <= thresholdMs && d < distance) { distance = d; best = point }
    }
    return best
  }
  companion object { fun build(points: Collection<Long>) = TimelineSnapIndex(points.toSortedSet().toLongArray()) }
}

/** Master/track indexes and the adapter from Ah Studio's legacy Timeline model. */
class AdvancedTimelineIndex private constructor(
  private val items: List<TimelineIndexItem>,
  private val byId: Map<String, TimelineIndexItem>,
  private val tree: IntervalTree,
  val snapIndex: TimelineSnapIndex
) {
  fun findClip(id: String): TimelineIndexItem? = byId[id]
  fun getClipsAt(timeMs: Long): List<TimelineIndexItem> = tree.queryPoint(timeMs)
  fun getVisibleRange(startMs: Long, endMs: Long): List<TimelineIndexItem> = tree.queryRange(startMs, endMs)
  fun getTrackClips(track: String, startMs: Long, endMs: Long): List<TimelineIndexItem> = getVisibleRange(startMs, endMs).filter { it.track == track }
  fun findOverlaps(track: String): List<Pair<TimelineIndexItem, TimelineIndexItem>> {
    val sorted = items.filter { it.track == track }.sortedBy { it.startMs }
    return buildList { sorted.forEachIndexed { i, a -> for (b in sorted.drop(i + 1)) { if (b.startMs >= a.endMs) break; if (a.intersects(b.startMs, b.endMs)) add(a to b) } } }
  }
  companion object {
    private val cache = ConcurrentHashMap<Timeline, AdvancedTimelineIndex>()
    fun from(timeline: Timeline): AdvancedTimelineIndex = cache.computeIfAbsent(timeline) { build(it) }
    fun build(timeline: Timeline): AdvancedTimelineIndex {
      val all = mutableListOf<TimelineIndexItem>()
      fun video(list: List<VideoClip>, track: String) = list.forEachIndexed { i, c -> all += TimelineIndexItem(c.id, track, c.timelineStartMs, c.timelineStartMs + c.durationMs, i) }
      video(timeline.videoClips, "video"); video(timeline.overlayClips, "overlay")
      timeline.audioClips.forEach { c -> all += TimelineIndexItem(c.id, "audio", c.timelineStartMs, c.timelineStartMs + c.durationMs) }
      timeline.textClips.forEach { c -> all += TimelineIndexItem(c.id, "text", c.timelineStartMs, c.timelineStartMs + c.durationMs) }
      timeline.stickerClips.forEach { c -> all += TimelineIndexItem(c.id, "sticker", c.timelineStartMs, c.timelineStartMs + c.durationMs) }
      timeline.effectClips.forEach { c -> all += TimelineIndexItem(c.id, "effect", c.timelineStartMs, c.timelineStartMs + c.durationMs) }
      return AdvancedTimelineIndex(all, all.associateBy { it.id }, IntervalTree.build(all), TimelineSnapIndex.build(all.flatMap { listOf(it.startMs, it.endMs) }))
    }
  }
}
