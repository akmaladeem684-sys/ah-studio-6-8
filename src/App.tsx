import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { 
  Film, 
  Sparkles, 
  Wand2, 
  Volume2, 
  Type, 
  SlidersHorizontal,
  Info
} from 'lucide-react'
import { 
  Track, 
  Clip, 
  AspectRatio, 
  ActiveToolTab, 
  VideoFilter 
} from './types/studio'
import { 
  INITIAL_TRACKS, 
  StockMediaItem, 
  defaultFilter 
} from './data/sampleMedia'
import { Header } from './components/Header'
import { Player } from './components/Player'
import { Timeline } from './components/Timeline'
import { MediaLibraryPanel } from './components/SidePanels/MediaLibraryPanel'
import { EffectsFiltersPanel } from './components/SidePanels/EffectsFiltersPanel'
import { AiSuitePanel } from './components/SidePanels/AiSuitePanel'
import { AudioToolsPanel } from './components/SidePanels/AudioToolsPanel'
import { TextStickersPanel } from './components/SidePanels/TextStickersPanel'
import { ClipInspector } from './components/ClipInspector'
import { ExportModal } from './components/ExportModal'

export const App: React.FC = () => {
  // Studio Project State
  const [projectName, setProjectName] = useState<string>('AH Studio 6.8')
  const [aspectRatio, setAspectRatio] = useState<AspectRatio>('16:9')
  const [tracks, setTracks] = useState<Track[]>(INITIAL_TRACKS)
  const [currentTimeMs, setCurrentTimeMs] = useState<number>(2000)
  const [isPlaying, setIsPlaying] = useState<boolean>(false)
  const [zoom, setZoom] = useState<number>(1.0)
  const [selectedClipId, setSelectedClipId] = useState<string | null>('clip-main-1')
  const [activeTab, setActiveTab] = useState<ActiveToolTab>('media')
  const [isExportOpen, setIsExportOpen] = useState<boolean>(false)

  // Undo / Redo history
  const [history, setHistory] = useState<Track[][]>([INITIAL_TRACKS])
  const [historyIndex, setHistoryIndex] = useState<number>(0)

  // Calculate project duration based on max clip end time + buffer
  const durationMs = useMemo(() => {
    let maxTime = 16000
    tracks.forEach((track) => {
      track.clips.forEach((clip) => {
        const clipEnd = clip.startTimeMs + clip.durationMs
        if (clipEnd > maxTime) maxTime = clipEnd
      })
    })
    return Math.max(16000, maxTime + 2000)
  }, [tracks])

  // Find currently selected clip
  const selectedClip = useMemo(() => {
    if (!selectedClipId) return null
    for (const track of tracks) {
      const found = track.clips.find((c) => c.id === selectedClipId)
      if (found) return found
    }
    return null
  }, [tracks, selectedClipId])

  // Push new state into history stack
  const updateTracksWithHistory = useCallback((newTracks: Track[]) => {
    setTracks(newTracks)
    setHistory((prev) => {
      const sliced = prev.slice(0, historyIndex + 1)
      return [...sliced, newTracks]
    })
    setHistoryIndex((prev) => prev + 1)
  }, [historyIndex])

  // Undo / Redo actions
  const handleUndo = useCallback(() => {
    if (historyIndex > 0) {
      setHistoryIndex((prev) => prev - 1)
      setTracks(history[historyIndex - 1])
    }
  }, [history, historyIndex])

  const handleRedo = useCallback(() => {
    if (historyIndex < history.length - 1) {
      setHistoryIndex((prev) => prev + 1)
      setTracks(history[historyIndex + 1])
    }
  }, [history, historyIndex])

  // Playback timer tick
  const lastTickRef = useRef<number>(performance.now())

  useEffect(() => {
    if (!isPlaying) return

    lastTickRef.current = performance.now()
    const interval = setInterval(() => {
      const now = performance.now()
      const delta = now - lastTickRef.current
      lastTickRef.current = now

      setCurrentTimeMs((prev) => {
        const next = prev + delta
        if (next >= durationMs) {
          return 0 // loop
        }
        return next
      })
    }, 1000 / 30) // 30 FPS clock

    return () => clearInterval(interval)
  }, [isPlaying, durationMs])

  // Track manipulation: Mute / Lock / Visibility
  const handleToggleMuteTrack = (trackId: string) => {
    updateTracksWithHistory(
      tracks.map((t) => (t.id === trackId ? { ...t, isMuted: !t.isMuted } : t))
    )
  }

  const handleToggleLockTrack = (trackId: string) => {
    updateTracksWithHistory(
      tracks.map((t) => (t.id === trackId ? { ...t, isLocked: !t.isLocked } : t))
    )
  }

  const handleToggleVisibleTrack = (trackId: string) => {
    updateTracksWithHistory(
      tracks.map((t) => (t.id === trackId ? { ...t, isVisible: !t.isVisible } : t))
    )
  }

  // Clip movement and trimming
  const handleMoveClip = (clipId: string, newStartTimeMs: number) => {
    updateTracksWithHistory(
      tracks.map((t) => ({
        ...t,
        clips: t.clips.map((c) => (c.id === clipId ? { ...c, startTimeMs: newStartTimeMs } : c)),
      }))
    )
  }

  const handleTrimClip = (clipId: string, newStartTimeMs: number, newDurationMs: number) => {
    updateTracksWithHistory(
      tracks.map((t) => ({
        ...t,
        clips: t.clips.map((c) =>
          c.id === clipId
            ? { ...c, startTimeMs: newStartTimeMs, durationMs: newDurationMs }
            : c
        ),
      }))
    )
  }

  // Update selected clip parameters
  const handleUpdateSelectedClip = (updates: Partial<Clip>) => {
    if (!selectedClipId) return
    updateTracksWithHistory(
      tracks.map((t) => ({
        ...t,
        clips: t.clips.map((c) => (c.id === selectedClipId ? { ...c, ...updates } : c)),
      }))
    )
  }

  // Split clip at current playhead
  const handleSplitClip = useCallback(() => {
    if (!selectedClipId) return
    let targetClip: Clip | null = null
    let targetTrack: Track | null = null

    for (const t of tracks) {
      const c = t.clips.find((clip) => clip.id === selectedClipId)
      if (c) {
        targetClip = c
        targetTrack = t
        break
      }
    }

    if (!targetClip || !targetTrack) return
    const clipStart = targetClip.startTimeMs
    const clipEnd = clipStart + targetClip.durationMs

    // Verify playhead is inside clip bounds
    if (currentTimeMs <= clipStart + 200 || currentTimeMs >= clipEnd - 200) return

    const firstPartDuration = currentTimeMs - clipStart
    const secondPartDuration = targetClip.durationMs - firstPartDuration

    const firstPart: Clip = {
      ...targetClip,
      durationMs: firstPartDuration,
    }

    const secondPart: Clip = {
      ...targetClip,
      id: `clip-${Date.now()}`,
      startTimeMs: currentTimeMs,
      durationMs: secondPartDuration,
      trimStartMs: targetClip.trimStartMs + firstPartDuration,
      name: `${targetClip.name} (Pt. 2)`,
    }

    updateTracksWithHistory(
      tracks.map((t) =>
        t.id === targetTrack!.id
          ? {
              ...t,
              clips: t.clips.flatMap((c) => (c.id === targetClip!.id ? [firstPart, secondPart] : [c])),
            }
          : t
      )
    )
    setSelectedClipId(secondPart.id)
  }, [selectedClipId, tracks, currentTimeMs, updateTracksWithHistory])

  // Delete selected clip
  const handleDeleteSelected = useCallback(() => {
    if (!selectedClipId) return
    updateTracksWithHistory(
      tracks.map((t) => ({
        ...t,
        clips: t.clips.filter((c) => c.id !== selectedClipId),
      }))
    )
    setSelectedClipId(null)
  }, [selectedClipId, tracks, updateTracksWithHistory])

  // Keyboard Shortcuts (Space for play/pause, S for split, Delete for delete, Ctrl+Z for undo)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore when typing inside input elements
      if (['INPUT', 'TEXTAREA'].includes((e.target as HTMLElement).tagName)) return

      if (e.code === 'Space') {
        e.preventDefault()
        setIsPlaying((prev) => !prev)
      } else if (e.code === 'KeyS') {
        e.preventDefault()
        handleSplitClip()
      } else if (e.code === 'Delete' || e.code === 'Backspace') {
        e.preventDefault()
        handleDeleteSelected()
      } else if ((e.ctrlKey || e.metaKey) && e.code === 'KeyZ') {
        e.preventDefault()
        if (e.shiftKey) {
          handleRedo()
        } else {
          handleUndo()
        }
      } else if ((e.ctrlKey || e.metaKey) && e.code === 'KeyY') {
        e.preventDefault()
        handleRedo()
      } else if (e.code === 'Home') {
        e.preventDefault()
        setCurrentTimeMs(0)
      } else if (e.code === 'KeyJ') {
        e.preventDefault()
        setCurrentTimeMs((prev) => Math.max(0, prev - 1000))
      } else if (e.code === 'KeyL') {
        e.preventDefault()
        setCurrentTimeMs((prev) => Math.min(durationMs, prev + 1000))
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [handleSplitClip, handleDeleteSelected, handleUndo, handleRedo, durationMs])

  // Add stock / uploaded media to timeline
  const handleAddMedia = (media: StockMediaItem) => {
    const targetTrackType = media.type === 'audio' ? 'audio' : 'video'
    const targetTrack = tracks.find((t) => t.type === targetTrackType) || tracks[0]

    // Find end of last clip in that track
    const lastEnd = targetTrack.clips.reduce(
      (max, c) => Math.max(max, c.startTimeMs + c.durationMs),
      0
    )

    const newClip: Clip = {
      id: `clip-${Date.now()}`,
      trackId: targetTrack.id,
      name: media.name,
      type: media.type === 'audio' ? 'audio' : 'video',
      startTimeMs: lastEnd,
      durationMs: media.durationMs,
      sourceDurationMs: media.durationMs,
      trimStartMs: 0,
      mediaUrl: media.url,
      thumbnailUrl: media.thumbnail,
      color: media.type === 'audio' ? '#a855f7' : '#3b82f6',
      positionX: 0,
      positionY: 0,
      scale: 1,
      rotation: 0,
      opacity: 1,
      blendMode: 'normal',
      speed: 1,
      volume: 1,
      fadeInMs: 0,
      fadeOutMs: 0,
      filters: { ...defaultFilter },
      effects: [],
      keyframes: [],
    }

    updateTracksWithHistory(
      tracks.map((t) =>
        t.id === targetTrack.id ? { ...t, clips: [...t.clips, newClip] } : t
      )
    )
    setSelectedClipId(newClip.id)
  }

  // AI Suite Handlers
  const handleGenerateCaptions = (style: 'yellow' | 'minimal' | 'neon') => {
    const textTrack = tracks.find((t) => t.type === 'text') || tracks[0]
    const samplePhrases = [
      'Welcome to AH Video Studio',
      'High-performance timeline editing',
      'Neural effects and 4K rendering',
      'Export your master video now',
    ]

    const newCaptions: Clip[] = samplePhrases.map((phrase, idx) => ({
      id: `caption-${Date.now()}-${idx}`,
      trackId: textTrack.id,
      name: `Caption ${idx + 1}`,
      type: 'text',
      startTimeMs: idx * 3000 + 500,
      durationMs: 2500,
      sourceDurationMs: 2500,
      trimStartMs: 0,
      color: style === 'neon' ? '#06b6d4' : '#f59e0b',
      positionX: 0,
      positionY: 130, // Lower third
      scale: 1,
      rotation: 0,
      opacity: 1,
      blendMode: 'normal',
      speed: 1,
      volume: 1,
      fadeInMs: 200,
      fadeOutMs: 200,
      textContent: phrase,
      fontSize: 26,
      textColor: style === 'neon' ? '#06b6d4' : style === 'yellow' ? '#fef08a' : '#ffffff',
      fontFamily: 'sans-serif',
      textBgColor: 'rgba(0,0,0,0.7)',
      filters: { ...defaultFilter },
      effects: [],
      keyframes: [],
    }))

    updateTracksWithHistory(
      tracks.map((t) =>
        t.id === textTrack.id ? { ...t, clips: [...t.clips, ...newCaptions] } : t
      )
    )
    setActiveTab('text')
  }

  const handleApplyAiColorCorrection = () => {
    if (!selectedClipId) return
    handleUpdateSelectedClip({
      filters: {
        brightness: 108,
        contrast: 115,
        saturation: 125,
        hueRotate: 5,
        blur: 0,
        vignette: 18,
        lut: 'teal_orange',
      },
    })
  }

  const handleApplyBackgroundRemover = () => {
    if (!selectedClipId) return
    handleUpdateSelectedClip({
      blendMode: 'screen',
      filters: {
        ...(selectedClip?.filters || defaultFilter),
        contrast: 130,
        brightness: 110,
      },
    })
  }

  const handleDetectScenesAndSplit = () => {
    // Auto-detect and split at 4s and 8s
    handleSplitClip()
  }

  // Text & Sticker Handlers
  const handleAddTextClip = (preset: { content: string; size: number; color: string; bg: string }) => {
    const textTrack = tracks.find((t) => t.type === 'text') || tracks[0]
    const newClip: Clip = {
      id: `text-${Date.now()}`,
      trackId: textTrack.id,
      name: preset.content.slice(0, 16),
      type: 'text',
      startTimeMs: currentTimeMs,
      durationMs: 4000,
      sourceDurationMs: 4000,
      trimStartMs: 0,
      color: '#818cf8',
      positionX: 0,
      positionY: 0,
      scale: 1,
      rotation: 0,
      opacity: 1,
      blendMode: 'normal',
      speed: 1,
      volume: 1,
      fadeInMs: 300,
      fadeOutMs: 300,
      textContent: preset.content,
      fontSize: preset.size,
      textColor: preset.color,
      textBgColor: preset.bg,
      filters: { ...defaultFilter },
      effects: [],
      keyframes: [],
    }

    updateTracksWithHistory(
      tracks.map((t) =>
        t.id === textTrack.id ? { ...t, clips: [...t.clips, newClip] } : t
      )
    )
    setSelectedClipId(newClip.id)
  }

  const handleAddStickerClip = (sticker: { label: string; bg: string }) => {
    handleAddTextClip({
      content: sticker.label,
      size: 24,
      color: '#ffffff',
      bg: 'rgba(0,0,0,0.85)',
    })
  }

  // Add sound effect to audio track
  const handleAddSoundEffect = (sfx: { name: string; duration: string; tone: number }) => {
    const audioTrack = tracks.find((t) => t.type === 'audio') || tracks[0]
    const durSec = parseFloat(sfx.duration) || 1
    const newClip: Clip = {
      id: `sfx-${Date.now()}`,
      trackId: audioTrack.id,
      name: sfx.name,
      type: 'audio',
      startTimeMs: currentTimeMs,
      durationMs: durSec * 1000,
      sourceDurationMs: durSec * 1000,
      trimStartMs: 0,
      color: '#ec4899',
      positionX: 0,
      positionY: 0,
      scale: 1,
      rotation: 0,
      opacity: 1,
      blendMode: 'normal',
      speed: 1,
      volume: 1,
      fadeInMs: 50,
      fadeOutMs: 150,
      filters: { ...defaultFilter },
      effects: [],
      keyframes: [],
    }

    updateTracksWithHistory(
      tracks.map((t) =>
        t.id === audioTrack.id ? { ...t, clips: [...t.clips, newClip] } : t
      )
    )
    setSelectedClipId(newClip.id)
  }

  return (
    <div id="ah-video-studio-app" className="flex flex-col h-screen w-screen bg-neutral-950 text-neutral-100 overflow-hidden font-sans">
      {/* Top Navigation Bar */}
      <Header
        projectName={projectName}
        onProjectNameChange={setProjectName}
        aspectRatio={aspectRatio}
        onAspectRatioChange={setAspectRatio}
        onSplit={handleSplitClip}
        onDeleteSelected={handleDeleteSelected}
        canUndo={historyIndex > 0}
        canRedo={historyIndex < history.length - 1}
        onUndo={handleUndo}
        onRedo={handleRedo}
        onOpenExport={() => setIsExportOpen(true)}
        zoom={zoom}
        onZoomChange={setZoom}
        hasSelectedClip={Boolean(selectedClipId)}
      />

      {/* Main Workspace (Side Panels + Player Viewport + Inspector) */}
      <div className="flex-1 flex min-h-0 relative">
        {/* Far-Left Vertical Tool Navigation Rail */}
        <div 
          id="vertical-tool-rail" 
          className="w-14 bg-neutral-950 border-r border-neutral-800 flex flex-col items-center py-3 space-y-3 shrink-0 select-none z-10"
        >
          <button
            id="tab-btn-media"
            onClick={() => setActiveTab('media')}
            className={`p-2.5 rounded-xl transition flex flex-col items-center space-y-1 ${
              activeTab === 'media'
                ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-900'
            }`}
            title="Media Library & Uploads"
          >
            <Film className="w-5 h-5" />
            <span className="text-[9px] font-medium">Media</span>
          </button>

          <button
            id="tab-btn-effects"
            onClick={() => setActiveTab('effects')}
            className={`p-2.5 rounded-xl transition flex flex-col items-center space-y-1 ${
              activeTab === 'effects'
                ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-900'
            }`}
            title="Color Grading & LUT Filters"
          >
            <Sparkles className="w-5 h-5" />
            <span className="text-[9px] font-medium">Filters</span>
          </button>

          <button
            id="tab-btn-ai"
            onClick={() => setActiveTab('ai')}
            className={`p-2.5 rounded-xl transition flex flex-col items-center space-y-1 ${
              activeTab === 'ai'
                ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-900'
            }`}
            title="AI Neural Video Suite"
          >
            <Wand2 className="w-5 h-5" />
            <span className="text-[9px] font-medium">AI Tools</span>
          </button>

          <button
            id="tab-btn-audio"
            onClick={() => setActiveTab('audio')}
            className={`p-2.5 rounded-xl transition flex flex-col items-center space-y-1 ${
              activeTab === 'audio'
                ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-900'
            }`}
            title="Audio & Sound Effects"
          >
            <Volume2 className="w-5 h-5" />
            <span className="text-[9px] font-medium">Audio</span>
          </button>

          <button
            id="tab-btn-text"
            onClick={() => setActiveTab('text')}
            className={`p-2.5 rounded-xl transition flex flex-col items-center space-y-1 ${
              activeTab === 'text'
                ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/40 shadow-sm'
                : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-900'
            }`}
            title="Text Titles & Stickers"
          >
            <Type className="w-5 h-5" />
            <span className="text-[9px] font-medium">Text</span>
          </button>
        </div>

        {/* Secondary Tool Shelf Panel */}
        <div 
          id="secondary-tool-shelf" 
          className="w-72 sm:w-80 bg-neutral-900/95 border-r border-neutral-800 flex flex-col shrink-0 min-h-0"
        >
          {activeTab === 'media' && <MediaLibraryPanel onAddMediaToTimeline={handleAddMedia} />}
          {activeTab === 'effects' && (
            <EffectsFiltersPanel
              selectedClip={selectedClip}
              onUpdateFilters={(f) =>
                handleUpdateSelectedClip({
                  filters: { ...(selectedClip?.filters || defaultFilter), ...f },
                })
              }
              onResetFilters={() => handleUpdateSelectedClip({ filters: { ...defaultFilter } })}
            />
          )}
          {activeTab === 'ai' && (
            <AiSuitePanel
              selectedClip={selectedClip}
              onGenerateCaptions={handleGenerateCaptions}
              onApplyAiColorCorrection={handleApplyAiColorCorrection}
              onApplyBackgroundRemover={handleApplyBackgroundRemover}
              onDetectScenesAndSplit={handleDetectScenesAndSplit}
              onToggleMotionTracking={() => {}}
            />
          )}
          {activeTab === 'audio' && (
            <AudioToolsPanel
              selectedClip={selectedClip}
              onUpdateAudioProps={handleUpdateSelectedClip}
              onAddSoundEffectToTimeline={handleAddSoundEffect}
            />
          )}
          {activeTab === 'text' && (
            <TextStickersPanel
              selectedClip={selectedClip}
              onAddTextClip={handleAddTextClip}
              onAddStickerClip={handleAddStickerClip}
              onUpdateTextProps={handleUpdateSelectedClip}
            />
          )}
        </div>

        {/* Center: Real-Time Canvas Video Player Viewport */}
        <div className="flex-1 flex flex-col min-w-0 min-h-0 bg-neutral-950">
          <Player
            tracks={tracks}
            currentTimeMs={currentTimeMs}
            durationMs={durationMs}
            isPlaying={isPlaying}
            onTogglePlay={() => setIsPlaying(!isPlaying)}
            onSeek={setCurrentTimeMs}
            aspectRatio={aspectRatio}
            selectedClipId={selectedClipId}
          />
        </div>

        {/* Right Sidebar: Contextual Clip Inspector */}
        <div 
          id="right-inspector-sidebar"
          className="w-64 bg-neutral-900/80 border-l border-neutral-800 hidden xl:flex flex-col shrink-0 min-h-0"
        >
          {selectedClip ? (
            <ClipInspector
              clip={selectedClip}
              onUpdateClip={handleUpdateSelectedClip}
            />
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center p-6 text-center text-neutral-500 space-y-2">
              <SlidersHorizontal className="w-8 h-8 text-neutral-600" />
              <p className="text-xs font-semibold text-neutral-400">Inspector</p>
              <p className="text-[11px] leading-relaxed">
                Select any video, audio, or title clip on the timeline to inspect its coordinates, scale, opacity, and blend modes.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Bottom: Multi-Track Timeline */}
      <Timeline
        tracks={tracks}
        currentTimeMs={currentTimeMs}
        durationMs={durationMs}
        zoom={zoom}
        selectedClipId={selectedClipId}
        onSelectClip={setSelectedClipId}
        onSeek={setCurrentTimeMs}
        onToggleMuteTrack={handleToggleMuteTrack}
        onToggleLockTrack={handleToggleLockTrack}
        onToggleVisibleTrack={handleToggleVisibleTrack}
        onMoveClip={handleMoveClip}
        onTrimClip={handleTrimClip}
      />

      {/* Export Dialog */}
      <ExportModal
        isOpen={isExportOpen}
        onClose={() => setIsExportOpen(false)}
        projectName={projectName}
        aspectRatio={aspectRatio}
        durationMs={durationMs}
        tracks={tracks}
      />
    </div>
  )
}
export default App
