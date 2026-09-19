import React, { useRef, useEffect, useState, useMemo } from 'react'
import { 
  Play, 
  Pause, 
  SkipBack, 
  SkipForward, 
  Maximize, 
  Minimize, 
  Volume2, 
  VolumeX,
  RotateCcw,
  Sparkles
} from 'lucide-react'
import { AspectRatio, Clip, Track } from '../types/studio'
import { LUT_PRESETS } from '../data/sampleMedia'

interface PlayerProps {
  tracks: Track[]
  currentTimeMs: number
  durationMs: number
  isPlaying: boolean
  onTogglePlay: () => void
  onSeek: (timeMs: number) => void
  aspectRatio: AspectRatio
  selectedClipId: string | null
  onUpdateClipTransform?: (clipId: string, updates: Partial<Clip>) => void
}

export const Player: React.FC<PlayerProps> = ({
  tracks,
  currentTimeMs,
  durationMs,
  isPlaying,
  onTogglePlay,
  onSeek,
  aspectRatio,
  selectedClipId,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [volumeMuted, setVolumeMuted] = useState(false)

  // Find currently visible clips at currentTimeMs
  const activeClips = useMemo(() => {
    const clips: { clip: Clip; track: Track }[] = []
    tracks.forEach((track) => {
      if (!track.isVisible) return
      track.clips.forEach((clip) => {
        const clipEnd = clip.startTimeMs + clip.durationMs
        if (currentTimeMs >= clip.startTimeMs && currentTimeMs <= clipEnd) {
          clips.push({ clip, track })
        }
      })
    })
    return clips
  }, [tracks, currentTimeMs])

  // Format timecode: HH:MM:SS:FF
  const formatTimecode = (ms: number) => {
    const totalSeconds = Math.max(0, ms / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = Math.floor(totalSeconds % 60)
    const frames = Math.floor((totalSeconds % 1) * 30) // 30fps frames
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}:${String(frames).padStart(2, '0')}`
  }

  // Determine aspect ratio canvas size
  const targetResolution = useMemo(() => {
    switch (aspectRatio) {
      case '9:16':
        return { width: 720, height: 1280, ratioClass: 'aspect-[9/16]' }
      case '1:1':
        return { width: 1080, height: 1080, ratioClass: 'aspect-square' }
      case '4:5':
        return { width: 864, height: 1080, ratioClass: 'aspect-[4/5]' }
      case '16:9':
      default:
        return { width: 1280, height: 720, ratioClass: 'aspect-video' }
    }
  }, [aspectRatio])

  // Draw scene onto canvas
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const { width, height } = targetResolution
    canvas.width = width
    canvas.height = height

    // Clear background
    ctx.fillStyle = '#050505'
    ctx.fillRect(0, 0, width, height)

    // Render layers from bottom to top (audio ignored for canvas)
    // Order: video -> overlay -> text
    const layerOrder = ['video', 'overlay', 'text']
    const sorted = [...activeClips].sort((a, b) => {
      return layerOrder.indexOf(a.track.type) - layerOrder.indexOf(b.track.type)
    })

    sorted.forEach(({ clip, track }) => {
      ctx.save()

      // Calculate fade in/out opacity
      const localTime = currentTimeMs - clip.startTimeMs
      let alpha = clip.opacity

      if (clip.fadeInMs > 0 && localTime < clip.fadeInMs) {
        alpha *= localTime / clip.fadeInMs
      }
      const timeLeft = clip.durationMs - localTime
      if (clip.fadeOutMs > 0 && timeLeft < clip.fadeOutMs) {
        alpha *= Math.max(0, timeLeft / clip.fadeOutMs)
      }

      ctx.globalAlpha = Math.max(0, Math.min(1, alpha))

      // Center of canvas
      const centerX = width / 2 + clip.positionX
      const centerY = height / 2 + clip.positionY

      ctx.translate(centerX, centerY)
      ctx.rotate((clip.rotation * Math.PI) / 180)
      ctx.scale(clip.scale, clip.scale)

      // Apply CSS Filters / Color adjustments
      const f = clip.filters
      const filterParts: string[] = []
      if (f.brightness !== 100) filterParts.push(`brightness(${f.brightness}%)`)
      if (f.contrast !== 100) filterParts.push(`contrast(${f.contrast}%)`)
      if (f.saturation !== 100) filterParts.push(`saturate(${f.saturation}%)`)
      if (f.hueRotate !== 0) filterParts.push(`hue-rotate(${f.hueRotate}deg)`)
      if (f.blur > 0) filterParts.push(`blur(${f.blur}px)`)

      const preset = LUT_PRESETS.find((l) => l.id === f.lut)
      if (preset && preset.filter) {
        filterParts.push(preset.filter)
      }

      if (filterParts.length > 0) {
        ctx.filter = filterParts.join(' ')
      }

      if (clip.type === 'video') {
        // Draw stylized video frame simulation
        const clipW = track.type === 'overlay' ? width * 0.45 : width
        const clipH = track.type === 'overlay' ? height * 0.45 : height

        // Draw background gradient simulating video scene
        const gradient = ctx.createLinearGradient(-clipW / 2, -clipH / 2, clipW / 2, clipH / 2)
        if (clip.color === '#3b82f6') {
          gradient.addColorStop(0, '#1e3a8a')
          gradient.addColorStop(0.5, '#2563eb')
          gradient.addColorStop(1, '#0284c7')
        } else if (clip.color === '#10b981') {
          gradient.addColorStop(0, '#064e3b')
          gradient.addColorStop(0.5, '#059669')
          gradient.addColorStop(1, '#10b981')
        } else if (clip.color === '#f59e0b') {
          gradient.addColorStop(0, '#78350f')
          gradient.addColorStop(0.5, '#d97706')
          gradient.addColorStop(1, '#fbbf24')
        } else {
          gradient.addColorStop(0, '#18181b')
          gradient.addColorStop(1, '#3f3f46')
        }

        ctx.fillStyle = gradient
        ctx.fillRect(-clipW / 2, -clipH / 2, clipW, clipH)

        // Draw cinematic dynamic grid / waveform lines to indicate motion
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)'
        ctx.lineWidth = 2
        ctx.strokeRect(-clipW / 2 + 10, -clipH / 2 + 10, clipW - 20, clipH - 20)

        // Dynamic light sweep
        const sweepX = ((currentTimeMs % 4000) / 4000) * clipW - clipW / 2
        const sweepGrad = ctx.createRadialGradient(sweepX, 0, 10, sweepX, 0, clipW / 2)
        sweepGrad.addColorStop(0, 'rgba(255,255,255,0.25)')
        sweepGrad.addColorStop(1, 'rgba(255,255,255,0)')
        ctx.fillStyle = sweepGrad
        ctx.fillRect(-clipW / 2, -clipH / 2, clipW, clipH)

        // Watermark / Clip label
        ctx.font = 'bold 24px system-ui, sans-serif'
        ctx.fillStyle = 'rgba(255, 255, 255, 0.85)'
        ctx.textAlign = 'center'
        ctx.fillText(`${clip.name}`, 0, 10)

        // Vignette effect if configured
        if (f.vignette > 0) {
          const vigGrad = ctx.createRadialGradient(0, 0, clipW * 0.3, 0, 0, clipW * 0.7)
          vigGrad.addColorStop(0, 'rgba(0,0,0,0)')
          vigGrad.addColorStop(1, `rgba(0,0,0,${f.vignette / 100})`)
          ctx.fillStyle = vigGrad
          ctx.fillRect(-clipW / 2, -clipH / 2, clipW, clipH)
        }
      } else if (clip.type === 'text') {
        const text = clip.textContent || 'AH STUDIO'
        const fontSize = clip.fontSize || 36
        ctx.font = `bold ${fontSize}px ${clip.fontFamily || 'sans-serif'}`
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'

        // Measure text for background box
        const metrics = ctx.measureText(text)
        const textWidth = metrics.width + 30
        const textHeight = fontSize * 1.5

        if (clip.textBgColor && clip.textBgColor !== 'transparent') {
          ctx.fillStyle = clip.textBgColor
          ctx.roundRect(-textWidth / 2, -textHeight / 2, textWidth, textHeight, 8)
          ctx.fill()
        }

        // Text shadow & glow
        ctx.shadowColor = 'rgba(0, 0, 0, 0.8)'
        ctx.shadowBlur = 12
        ctx.shadowOffsetX = 2
        ctx.shadowOffsetY = 2

        ctx.fillStyle = clip.textColor || '#ffffff'
        ctx.fillText(text, 0, 0)
      }

      ctx.restore()
    })

    // If a clip is selected and active, draw bounding box indicator
    if (selectedClipId) {
      const selected = activeClips.find((ac) => ac.clip.id === selectedClipId)
      if (selected) {
        ctx.save()
        const clip = selected.clip
        const centerX = width / 2 + clip.positionX
        const centerY = height / 2 + clip.positionY
        ctx.translate(centerX, centerY)
        ctx.rotate((clip.rotation * Math.PI) / 180)
        ctx.scale(clip.scale, clip.scale)

        const boxW = selected.track.type === 'overlay' ? width * 0.45 : clip.type === 'text' ? 300 : width * 0.95
        const boxH = selected.track.type === 'overlay' ? height * 0.45 : clip.type === 'text' ? 80 : height * 0.95

        ctx.strokeStyle = '#6366f1'
        ctx.lineWidth = 3
        ctx.setLineDash([8, 6])
        ctx.strokeRect(-boxW / 2, -boxH / 2, boxW, boxH)

        // Corner handles
        const handleSize = 10
        ctx.fillStyle = '#ffffff'
        const corners = [
          [-boxW / 2, -boxH / 2],
          [boxW / 2, -boxH / 2],
          [-boxW / 2, boxH / 2],
          [boxW / 2, boxH / 2],
        ]
        corners.forEach(([x, y]) => {
          ctx.fillRect(x - handleSize / 2, y - handleSize / 2, handleSize, handleSize)
        })

        ctx.restore()
      }
    }
  }, [activeClips, currentTimeMs, targetResolution, selectedClipId])

  const toggleFullscreen = () => {
    if (!containerRef.current) return
    if (!document.fullscreenElement) {
      containerRef.current.requestFullscreen().catch(() => {})
      setIsFullscreen(true)
    } else {
      document.exitFullscreen().catch(() => {})
      setIsFullscreen(false)
    }
  }

  return (
    <div 
      ref={containerRef}
      id="video-player-viewport" 
      className="flex flex-col h-full bg-neutral-950 border-b border-neutral-800 relative overflow-hidden"
    >
      {/* Player Display Stage */}
      <div className="flex-1 flex items-center justify-center p-3 relative min-h-0 bg-radial from-neutral-900 to-neutral-950">
        <div 
          className={`relative max-h-full max-w-full ${targetResolution.ratioClass} rounded-lg overflow-hidden shadow-2xl border border-neutral-800 flex items-center justify-center bg-black`}
        >
          <canvas
            ref={canvasRef}
            id="main-preview-canvas"
            className="w-full h-full object-contain block"
          />

          {/* Active overlays counter indicator */}
          <div className="absolute top-2 left-2 flex items-center space-x-1.5 bg-neutral-900/80 backdrop-blur-md px-2 py-1 rounded-md border border-neutral-700/60 text-[11px] text-neutral-300 font-mono">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span>{aspectRatio}</span>
            <span className="text-neutral-500">•</span>
            <span>{activeClips.length} active layers</span>
          </div>

          {/* Center Play Watermark overlay when paused */}
          {!isPlaying && (
            <button
              onClick={onTogglePlay}
              className="absolute inset-0 m-auto w-14 h-14 rounded-full bg-neutral-900/70 hover:bg-neutral-900/90 text-white flex items-center justify-center backdrop-blur-sm border border-neutral-700/70 shadow-xl transition transform hover:scale-110 active:scale-95"
              title="Click to Play"
            >
              <Play className="w-6 h-6 fill-white ml-0.5" />
            </button>
          )}
        </div>
      </div>

      {/* Control Bar: Timecode & Transport Buttons */}
      <div 
        id="player-control-bar"
        className="h-12 bg-neutral-900/90 backdrop-blur-md border-t border-neutral-800 px-4 flex items-center justify-between select-none"
      >
        {/* Left: Timecode readout */}
        <div className="flex items-center space-x-2 font-mono text-xs">
          <span className="text-indigo-400 font-semibold">{formatTimecode(currentTimeMs)}</span>
          <span className="text-neutral-600">/</span>
          <span className="text-neutral-400">{formatTimecode(durationMs)}</span>
        </div>

        {/* Center: Playback Controls */}
        <div className="flex items-center space-x-2">
          <button
            id="btn-jump-start"
            onClick={() => onSeek(0)}
            className="p-1.5 text-neutral-400 hover:text-white hover:bg-neutral-800 rounded-md transition"
            title="Jump to Start (Home)"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
          <button
            id="btn-step-backward"
            onClick={() => onSeek(Math.max(0, currentTimeMs - 1000))}
            className="p-1.5 text-neutral-400 hover:text-white hover:bg-neutral-800 rounded-md transition"
            title="Step Back 1s (J)"
          >
            <SkipBack className="w-4 h-4" />
          </button>
          <button
            id="btn-play-pause"
            onClick={onTogglePlay}
            className="p-2.5 bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white rounded-full shadow-lg shadow-indigo-600/30 transition transform active:scale-95"
            title="Play / Pause (Space)"
          >
            {isPlaying ? (
              <Pause className="w-4 h-4 fill-white" />
            ) : (
              <Play className="w-4 h-4 fill-white ml-0.5" />
            )}
          </button>
          <button
            id="btn-step-forward"
            onClick={() => onSeek(Math.min(durationMs, currentTimeMs + 1000))}
            className="p-1.5 text-neutral-400 hover:text-white hover:bg-neutral-800 rounded-md transition"
            title="Step Forward 1s (L)"
          >
            <SkipForward className="w-4 h-4" />
          </button>
        </div>

        {/* Right: Audio Mute & Fullscreen */}
        <div className="flex items-center space-x-2">
          <button
            id="btn-toggle-audio-mute"
            onClick={() => setVolumeMuted(!volumeMuted)}
            className="p-1.5 text-neutral-400 hover:text-white hover:bg-neutral-800 rounded-md transition"
            title={volumeMuted ? 'Unmute Audio' : 'Mute Audio'}
          >
            {volumeMuted ? <VolumeX className="w-4 h-4 text-red-400" /> : <Volume2 className="w-4 h-4" />}
          </button>
          <button
            id="btn-toggle-fullscreen"
            onClick={toggleFullscreen}
            className="p-1.5 text-neutral-400 hover:text-white hover:bg-neutral-800 rounded-md transition"
            title="Fullscreen (F)"
          >
            {isFullscreen ? <Minimize className="w-4 h-4" /> : <Maximize className="w-4 h-4" />}
          </button>
        </div>
      </div>
    </div>
  )
}
