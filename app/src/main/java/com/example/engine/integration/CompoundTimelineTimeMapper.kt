package com.example.engine.integration

import kotlin.math.roundToLong

data class CompoundTimeMapping(val parentUs: Long, val localUs: Long, val sourceUs: Long)

/** Bidirectional mapping for compound clips; callers can chain instances for nested timelines. */
class CompoundTimelineTimeMapper(private val timelineStartUs: Long, private val timelineDurationUs: Long, private val sourceInUs: Long, private val sourceOutUs: Long, private val speed: Double = 1.0, private val reversed: Boolean = false) {
  fun parentToSource(parentUs: Long): Long? {
    if (parentUs !in timelineStartUs until (timelineStartUs + timelineDurationUs)) return null
    val local = (parentUs - timelineStartUs).coerceIn(0, timelineDurationUs)
    val delta = (local * speed).roundToLong()
    return if (reversed) (sourceOutUs - delta).coerceIn(sourceInUs, sourceOutUs) else (sourceInUs + delta).coerceIn(sourceInUs, sourceOutUs)
  }
  fun sourceToParent(sourceUs: Long): Long = timelineStartUs + (((if (reversed) sourceOutUs - sourceUs else sourceUs - sourceInUs) / speed.coerceAtLeast(.001)).roundToLong()).coerceIn(0, timelineDurationUs)
  fun map(parentUs: Long): CompoundTimeMapping? = parentToSource(parentUs)?.let { CompoundTimeMapping(parentUs, parentUs - timelineStartUs, it) }
}
