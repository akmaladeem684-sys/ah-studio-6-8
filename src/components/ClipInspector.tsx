import React from 'react'
import { 
  SlidersHorizontal, 
  Move, 
  Maximize2, 
  RotateCw, 
  Gauge, 
  Layers, 
  Sparkles,
  Volume2
} from 'lucide-react'
import { Clip } from '../types/studio'

interface ClipInspectorProps {
  clip: Clip
  onUpdateClip: (updates: Partial<Clip>) => void
}

export const ClipInspector: React.FC<ClipInspectorProps> = ({ clip, onUpdateClip }) => {
  return (
    <div id="clip-inspector-panel" className="h-full overflow-y-auto p-4 space-y-4 text-xs select-none">
      {/* Header with clip identity */}
      <div className="flex items-center justify-between border-b border-neutral-800 pb-2.5">
        <div className="flex items-center space-x-2">
          <div 
            className="w-3 h-3 rounded-full" 
            style={{ backgroundColor: clip.color || '#6366f1' }}
          />
          <h3 className="font-bold text-neutral-100 truncate max-w-[160px]">{clip.name}</h3>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded bg-neutral-800 text-neutral-300 font-mono uppercase">
          {clip.type}
        </span>
      </div>

      {/* Transform Coordinates (X, Y) */}
      <div className="space-y-2.5 bg-neutral-900/60 p-3 rounded-xl border border-neutral-800/80">
        <h4 className="text-[11px] font-bold text-neutral-300 uppercase tracking-wide flex items-center space-x-1.5">
          <Move className="w-3 h-3 text-indigo-400" />
          <span>Transform Position</span>
        </h4>

        <div className="grid grid-cols-2 gap-2">
          <div className="space-y-1">
            <div className="flex justify-between text-[10px] text-neutral-400">
              <span>X Offset</span>
              <span className="font-mono text-neutral-300">{clip.positionX}px</span>
            </div>
            <input
              type="range"
              min="-400"
              max="400"
              value={clip.positionX}
              onChange={(e) => onUpdateClip({ positionX: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1 bg-neutral-800 rounded cursor-pointer"
            />
          </div>

          <div className="space-y-1">
            <div className="flex justify-between text-[10px] text-neutral-400">
              <span>Y Offset</span>
              <span className="font-mono text-neutral-300">{clip.positionY}px</span>
            </div>
            <input
              type="range"
              min="-400"
              max="400"
              value={clip.positionY}
              onChange={(e) => onUpdateClip({ positionY: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1 bg-neutral-800 rounded cursor-pointer"
            />
          </div>
        </div>

        {/* Scale & Rotation */}
        <div className="grid grid-cols-2 gap-2 pt-1">
          <div className="space-y-1">
            <div className="flex justify-between text-[10px] text-neutral-400">
              <span>Scale</span>
              <span className="font-mono text-neutral-300">{(clip.scale).toFixed(2)}x</span>
            </div>
            <input
              type="range"
              min="0.2"
              max="2.5"
              step="0.05"
              value={clip.scale}
              onChange={(e) => onUpdateClip({ scale: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1 bg-neutral-800 rounded cursor-pointer"
            />
          </div>

          <div className="space-y-1">
            <div className="flex justify-between text-[10px] text-neutral-400">
              <span>Rotation</span>
              <span className="font-mono text-neutral-300">{clip.rotation}°</span>
            </div>
            <input
              type="range"
              min="-180"
              max="180"
              step="5"
              value={clip.rotation}
              onChange={(e) => onUpdateClip({ rotation: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1 bg-neutral-800 rounded cursor-pointer"
            />
          </div>
        </div>
      </div>

      {/* Opacity & Blend Mode */}
      <div className="space-y-2.5 bg-neutral-900/60 p-3 rounded-xl border border-neutral-800/80">
        <h4 className="text-[11px] font-bold text-neutral-300 uppercase tracking-wide flex items-center space-x-1.5">
          <Layers className="w-3 h-3 text-cyan-400" />
          <span>Compositing & Blending</span>
        </h4>

        <div className="space-y-1">
          <div className="flex justify-between text-[10px] text-neutral-400">
            <span>Opacity</span>
            <span className="font-mono text-neutral-300">{Math.round(clip.opacity * 100)}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="1"
            step="0.05"
            value={clip.opacity}
            onChange={(e) => onUpdateClip({ opacity: Number(e.target.value) })}
            className="w-full accent-cyan-500 h-1 bg-neutral-800 rounded cursor-pointer"
          />
        </div>

        <div className="space-y-1">
          <label className="text-[10px] text-neutral-400">Blend Mode</label>
          <select
            value={clip.blendMode}
            onChange={(e) => onUpdateClip({ blendMode: e.target.value as Clip['blendMode'] })}
            className="w-full bg-neutral-950 border border-neutral-700 rounded-lg px-2 py-1.5 text-xs text-white outline-none focus:border-indigo-500"
          >
            <option value="normal">Normal (Source Over)</option>
            <option value="screen">Screen (Lighten)</option>
            <option value="multiply">Multiply (Darken)</option>
            <option value="overlay">Overlay (Contrast)</option>
            <option value="additive">Additive (Glow)</option>
          </select>
        </div>
      </div>

      {/* Speed Multiplier */}
      <div className="space-y-2 bg-neutral-900/60 p-3 rounded-xl border border-neutral-800/80">
        <h4 className="text-[11px] font-bold text-neutral-300 uppercase tracking-wide flex items-center space-x-1.5">
          <Gauge className="w-3 h-3 text-amber-400" />
          <span>Playback Speed</span>
        </h4>

        <div className="grid grid-cols-4 gap-1">
          {[0.5, 1.0, 1.5, 2.0].map((rate) => (
            <button
              key={rate}
              onClick={() => onUpdateClip({ speed: rate })}
              className={`py-1 rounded text-[11px] font-mono border transition ${
                clip.speed === rate
                  ? 'bg-amber-500/20 border-amber-500 text-amber-300 font-bold'
                  : 'bg-neutral-800 border-neutral-700 text-neutral-400 hover:text-white'
              }`}
            >
              {rate}x
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
