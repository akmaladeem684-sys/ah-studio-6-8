import React, { useRef, useState, useCallback, useEffect } from 'react'
import { 
  Eye, 
  EyeOff, 
  Lock, 
  Unlock, 
  Volume2, 
  VolumeX, 
  Video, 
  Music, 
  Type, 
  Layers, 
  Sparkles 
} from 'lucide-react'
import { Clip, Track } from '../types/studio'

interface TimelineProps {
  tracks: Track[]
  currentTimeMs: number
  durationMs: number
  zoom: number
  selectedClipId: string | null
  onSelectClip: (clipId: string | null) => void
  onSeek: (timeMs: number) => void
  onToggleMuteTrack: (trackId: string) => void
  onToggleLockTrack: (trackId: string) => void
  onToggleVisibleTrack: (trackId: string) => void
  onMoveClip: (clipId: string, newStartTimeMs: number) => void
  onTrimClip: (clipId: string, newStartTimeMs: number, newDurationMs: number) => void
}

export const Timeline: React.FC<TimelineProps> = ({
  tracks,
  currentTimeMs,
  durationMs,
  zoom,
  selectedClipId,
  onSelectClip,
  onSeek,
  onToggleMuteTrack,
  onToggleLockTrack,
  onToggleVisibleTrack,
  onMoveClip,
  onTrimClip,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const tracksScrollRef = useRef<HTMLDivElement | null>(null)
  const [isScrubbing, setIsScrubbing] = useState(false)
  const [draggingClipId, setDraggingClipId] = useState<string | null>(null)
  const [dragStartX, setDragStartX] = useState<number>(0)
  const [dragOriginalStartMs, setDragOriginalStartMs] = useState<number>(0)
  const [trimmingClip, setTrimmingClip] = useState<{ id: string; side: 'start' | 'end'; startMs: number; durMs: number; mouseStartX: number } | null>(null)

  // Scale: pixels per millisecond
  // Default: 1 second = 80px * zoom
  const pxPerMs = (80 * zoom) / 1000
  const timelineWidth = Math.max(1200, durationMs * pxPerMs + 300)

  // Track icons
  const getTrackIcon = (type: Track['type']) => {
    switch (type) {
      case 'video':
        return <Video className="w-3.5 h-3.5 text-blue-400" />
      case 'overlay':
        return <Layers className="w-3.5 h-3.5 text-amber-400" />
      case 'audio':
        return <Music className="w-3.5 h-3.5 text-purple-400" />
      case 'text':
        return <Type className="w-3.5 h-3.5 text-indigo-400" />
      default:
        return <Sparkles className="w-3.5 h-3.5 text-neutral-400" />
    }
  }

  // Handle Playhead Scrubbing
  const handleRulerMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!tracksScrollRef.current) return
    setIsScrubbing(true)
    const rect = tracksScrollRef.current.getBoundingClientRect()
    const scrollLeft = tracksScrollRef.current.scrollLeft
    const clickX = e.clientX - rect.left + scrollLeft
    const targetMs = Math.max(0, Math.min(durationMs, clickX / pxPerMs))
    onSeek(targetMs)
  }

  const handleMouseMove = useCallback((e: MouseEvent) => {
    if (isScrubbing && tracksScrollRef.current) {
      const rect = tracksScrollRef.current.getBoundingClientRect()
      const scrollLeft = tracksScrollRef.current.scrollLeft
      const clickX = e.clientX - rect.left + scrollLeft
      const targetMs = Math.max(0, Math.min(durationMs, clickX / pxPerMs))
      onSeek(targetMs)
    } else if (draggingClipId) {
      const deltaX = e.clientX - dragStartX
      const deltaMs = deltaX / pxPerMs
      const newStart = Math.max(0, Math.min(durationMs - 500, dragOriginalStartMs + deltaMs))
      onMoveClip(draggingClipId, Math.round(newStart / 50) * 50) // Snap to 50ms
    } else if (trimmingClip) {
      const deltaX = e.clientX - trimmingClip.mouseStartX
      const deltaMs = deltaX / pxPerMs
      if (trimmingClip.side === 'end') {
        const newDur = Math.max(500, trimmingClip.durMs + deltaMs)
        onTrimClip(trimmingClip.id, trimmingClip.startMs, Math.round(newDur / 50) * 50)
      } else {
        const newStart = Math.max(0, trimmingClip.startMs + deltaMs)
        const durDiff = trimmingClip.startMs - newStart
        const newDur = Math.max(500, trimmingClip.durMs + durDiff)
        onTrimClip(trimmingClip.id, Math.round(newStart / 50) * 50, Math.round(newDur / 50) * 50)
      }
    }
  }, [isScrubbing, draggingClipId, dragStartX, dragOriginalStartMs, trimmingClip, pxPerMs, durationMs, onSeek, onMoveClip, onTrimClip])

  const handleMouseUp = useCallback(() => {
    setIsScrubbing(false)
    setDraggingClipId(null)
    setTrimmingClip(null)
  }, [])

  useEffect(() => {
    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)
    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [handleMouseMove, handleMouseUp])

  // Generate tick marks for time ruler
  const renderRulerTicks = () => {
    const ticks = []
    const totalSeconds = Math.ceil(durationMs / 1000) + 2
    for (let sec = 0; sec <= totalSeconds; sec++) {
      const x = sec * 1000 * pxPerMs
      ticks.push(
        <div key={sec} className="absolute top-0 flex flex-col items-center" style={{ left: `${x}px` }}>
          <div className="h-3 w-px bg-neutral-700" />
          <span className="text-[10px] font-mono text-neutral-400 select-none mt-0.5">
            {sec}s
          </span>
          {/* Sub-second ticks */}
          {zoom >= 1.5 && (
            <>
              <div className="absolute top-0 h-1.5 w-px bg-neutral-800" style={{ left: `${500 * pxPerMs}px` }} />
            </>
          )}
        </div>
      )
    }
    return ticks
  }

  return (
    <div 
      ref={containerRef}
      id="multi-track-timeline" 
      className="h-64 sm:h-72 bg-neutral-900 border-t border-neutral-800 flex flex-col select-none relative overflow-hidden"
    >
      {/* Timeline Ruler & Track Layout */}
      <div className="flex flex-1 h-full min-h-0">
        {/* Left: Track Header Controls Column */}
        <div className="w-44 sm:w-52 bg-neutral-950 border-r border-neutral-800 flex flex-col shrink-0 z-10">
          {/* Header Corner */}
          <div className="h-7 bg-neutral-900/90 border-b border-neutral-800 px-3 flex items-center justify-between text-[11px] font-medium text-neutral-400">
            <span>TRACKS</span>
            <span className="text-[10px] text-neutral-500 font-mono">{tracks.length} Layers</span>
          </div>

          {/* Track Headers List */}
          <div className="flex-1 overflow-y-auto">
            {tracks.map((track) => (
              <div
                key={track.id}
                id={`track-header-${track.id}`}
                className="h-14 border-b border-neutral-800/80 px-3 flex items-center justify-between bg-neutral-950 hover:bg-neutral-900/50 transition text-xs"
              >
                <div className="flex items-center space-x-2 min-w-0 pr-1">
                  {getTrackIcon(track.type)}
                  <span className="text-neutral-200 font-medium truncate text-xs">{track.name}</span>
                </div>

                {/* Track controls: Hide/Mute & Lock */}
                <div className="flex items-center space-x-1 shrink-0">
                  <button
                    onClick={() => onToggleVisibleTrack(track.id)}
                    className={`p-1 rounded hover:bg-neutral-800 transition ${
                      track.isVisible ? 'text-neutral-400 hover:text-white' : 'text-red-400'
                    }`}
                    title={track.isVisible ? 'Hide Track' : 'Show Track'}
                  >
                    {track.isVisible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                  </button>

                  <button
                    onClick={() => onToggleMuteTrack(track.id)}
                    className={`p-1 rounded hover:bg-neutral-800 transition ${
                      !track.isMuted ? 'text-neutral-400 hover:text-white' : 'text-red-400'
                    }`}
                    title={track.isMuted ? 'Unmute Audio' : 'Mute Audio'}
                  >
                    {!track.isMuted ? <Volume2 className="w-3.5 h-3.5" /> : <VolumeX className="w-3.5 h-3.5" />}
                  </button>

                  <button
                    onClick={() => onToggleLockTrack(track.id)}
                    className={`p-1 rounded hover:bg-neutral-800 transition ${
                      track.isLocked ? 'text-amber-400' : 'text-neutral-500 hover:text-white'
                    }`}
                    title={track.isLocked ? 'Unlock Track' : 'Lock Track'}
                  >
                    {track.isLocked ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Right: Scrollable Timeline Canvas & Lanes */}
        <div 
          ref={tracksScrollRef}
          className="flex-1 relative overflow-x-auto overflow-y-auto bg-neutral-950 flex flex-col"
          onClick={(e) => {
            // Click outside clip deselects
            if ((e.target as HTMLElement).id === 'timeline-lanes-bg') {
              onSelectClip(null)
            }
          }}
        >
          {/* Time Ruler Bar */}
          <div
            id="timeline-ruler"
            className="h-7 bg-neutral-900 border-b border-neutral-800 sticky top-0 z-10 cursor-pointer overflow-hidden"
            style={{ width: `${timelineWidth}px` }}
            onMouseDown={handleRulerMouseDown}
          >
            {renderRulerTicks()}
          </div>

          {/* Red/Indigo Playhead Line */}
          <div
            id="timeline-playhead"
            className="absolute top-0 bottom-0 pointer-events-none z-20"
            style={{ left: `${currentTimeMs * pxPerMs}px` }}
          >
            {/* Triangular scrubber head */}
            <div className="w-3 h-3 bg-red-500 -ml-1.5 rotate-45 transform origin-center shadow-lg shadow-red-500/50" />
            <div className="w-0.5 h-full bg-red-500 shadow-md shadow-red-500/80" />
          </div>

          {/* Track Lanes */}
          <div 
            id="timeline-lanes-bg" 
            className="flex-1 flex flex-col" 
            style={{ width: `${timelineWidth}px` }}
          >
            {tracks.map((track) => (
              <div
                key={track.id}
                id={`lane-${track.id}`}
                className={`h-14 border-b border-neutral-800/60 relative flex items-center transition ${
                  track.isLocked ? 'bg-neutral-950/80' : 'bg-neutral-900/30'
                }`}
              >
                {/* Clips in this track */}
                {track.clips.map((clip) => {
                  const clipLeft = clip.startTimeMs * pxPerMs
                  const clipWidth = Math.max(30, clip.durationMs * pxPerMs)
                  const isSelected = selectedClipId === clip.id

                  return (
                    <div
                      key={clip.id}
                      id={`clip-card-${clip.id}`}
                      onMouseDown={(e) => {
                        e.stopPropagation()
                        if (track.isLocked) return
                        onSelectClip(clip.id)
                        setDraggingClipId(clip.id)
                        setDragStartX(e.clientX)
                        setDragOriginalStartMs(clip.startTimeMs)
                      }}
                      className={`absolute h-10 rounded-lg flex items-center justify-between px-2 cursor-grab active:cursor-grabbing border text-xs transition shadow-sm overflow-hidden select-none ${
                        isSelected
                          ? 'border-indigo-400 ring-2 ring-indigo-500/50 shadow-indigo-500/20'
                          : 'border-neutral-700/60 hover:border-neutral-500'
                      }`}
                      style={{
                        left: `${clipLeft}px`,
                        width: `${clipWidth}px`,
                        backgroundColor: clip.color ? `${clip.color}33` : '#3b82f633',
                        borderColor: isSelected ? '#818cf8' : clip.color || '#3b82f6',
                      }}
                      title={`${clip.name} (${(clip.durationMs / 1000).toFixed(1)}s)`}
                    >
                      {/* Left Trim Handle */}
                      {isSelected && (
                        <div
                          className="absolute left-0 top-0 bottom-0 w-2.5 bg-indigo-400 hover:bg-white cursor-ew-resize flex items-center justify-center z-10"
                          onMouseDown={(e) => {
                            e.stopPropagation()
                            setTrimmingClip({
                              id: clip.id,
                              side: 'start',
                              startMs: clip.startTimeMs,
                              durMs: clip.durationMs,
                              mouseStartX: e.clientX,
                            })
                          }}
                        >
                          <div className="w-0.5 h-3 bg-neutral-900 rounded-full" />
                        </div>
                      )}

                      {/* Clip Content & Thumbnail preview */}
                      <div className="flex items-center space-x-1.5 truncate pointer-events-none pl-1">
                        {clip.type === 'video' && clip.thumbnailUrl && (
                          <img
                            src={clip.thumbnailUrl}
                            alt=""
                            className="w-6 h-6 rounded object-cover border border-white/20 shrink-0"
                          />
                        )}
                        <span className="font-semibold text-neutral-100 text-[11px] truncate">
                          {clip.name}
                        </span>
                        {clip.filters.lut !== 'none' && (
                          <span className="text-[9px] px-1 py-0.2 rounded bg-neutral-800/80 text-cyan-300 font-mono">
                            {clip.filters.lut}
                          </span>
                        )}
                      </div>

                      {/* Duration readout */}
                      <span className="text-[10px] font-mono text-neutral-300 shrink-0 ml-1 opacity-80 pointer-events-none">
                        {(clip.durationMs / 1000).toFixed(1)}s
                      </span>

                      {/* Right Trim Handle */}
                      {isSelected && (
                        <div
                          className="absolute right-0 top-0 bottom-0 w-2.5 bg-indigo-400 hover:bg-white cursor-ew-resize flex items-center justify-center z-10"
                          onMouseDown={(e) => {
                            e.stopPropagation()
                            setTrimmingClip({
                              id: clip.id,
                              side: 'end',
                              startMs: clip.startTimeMs,
                              durMs: clip.durationMs,
                              mouseStartX: e.clientX,
                            })
                          }}
                        >
                          <div className="w-0.5 h-3 bg-neutral-900 rounded-full" />
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
