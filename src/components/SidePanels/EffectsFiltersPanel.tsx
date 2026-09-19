import React from 'react'
import { Sliders, Sparkles, RotateCcw, Palette } from 'lucide-react'
import { Clip, VideoFilter } from '../../types/studio'
import { LUT_PRESETS, defaultFilter } from '../../data/sampleMedia'

interface EffectsFiltersPanelProps {
  selectedClip: Clip | null
  onUpdateFilters: (filters: Partial<VideoFilter>) => void
  onResetFilters: () => void
}

export const EffectsFiltersPanel: React.FC<EffectsFiltersPanelProps> = ({
  selectedClip,
  onUpdateFilters,
  onResetFilters,
}) => {
  const currentFilters = selectedClip?.filters || defaultFilter

  return (
    <div id="panel-effects-filters" className="flex flex-col h-full overflow-y-auto p-4 space-y-5 select-none">
      {!selectedClip && (
        <div className="bg-neutral-900/80 border border-neutral-800 rounded-lg p-3 text-xs text-neutral-400">
          💡 Select a video or overlay clip on the timeline to customize color grading and cinematic filters in real-time.
        </div>
      )}

      {/* Cinematic LUT Presets */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Palette className="w-3.5 h-3.5 text-indigo-400" />
            <span>Cinematic Color Grades</span>
          </h4>
          <span className="text-[10px] text-neutral-500 font-mono">LUT Engine</span>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {LUT_PRESETS.map((preset) => {
            const isSelected = currentFilters.lut === preset.id
            return (
              <button
                key={preset.id}
                onClick={() => onUpdateFilters({ lut: preset.id })}
                className={`p-2 rounded-lg border text-left flex flex-col space-y-1.5 transition ${
                  isSelected
                    ? 'border-indigo-400 bg-neutral-800 ring-1 ring-indigo-500'
                    : 'border-neutral-800 bg-neutral-900/60 hover:bg-neutral-800 hover:border-neutral-700'
                }`}
              >
                <div className={`h-8 w-full rounded ${preset.previewBg} flex items-center justify-center`}>
                  <Sparkles className="w-3.5 h-3.5 text-white/50" />
                </div>
                <span className="text-[11px] font-medium text-neutral-200 truncate">{preset.name}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Color Grading Sliders */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Sliders className="w-3.5 h-3.5 text-cyan-400" />
            <span>Color & Lighting</span>
          </h4>
          <button
            onClick={onResetFilters}
            className="flex items-center space-x-1 text-[11px] text-neutral-400 hover:text-white transition"
            title="Reset to defaults"
          >
            <RotateCcw className="w-3 h-3" />
            <span>Reset</span>
          </button>
        </div>

        <div className="space-y-3.5 bg-neutral-900/60 p-3 rounded-xl border border-neutral-800/80">
          {/* Brightness */}
          <div className="space-y-1">
            <div className="flex justify-between text-[11px]">
              <span className="text-neutral-400">Brightness</span>
              <span className="font-mono text-neutral-300">{currentFilters.brightness}%</span>
            </div>
            <input
              type="range"
              min="20"
              max="180"
              value={currentFilters.brightness}
              onChange={(e) => onUpdateFilters({ brightness: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
            />
          </div>

          {/* Contrast */}
          <div className="space-y-1">
            <div className="flex justify-between text-[11px]">
              <span className="text-neutral-400">Contrast</span>
              <span className="font-mono text-neutral-300">{currentFilters.contrast}%</span>
            </div>
            <input
              type="range"
              min="40"
              max="200"
              value={currentFilters.contrast}
              onChange={(e) => onUpdateFilters({ contrast: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
            />
          </div>

          {/* Saturation */}
          <div className="space-y-1">
            <div className="flex justify-between text-[11px]">
              <span className="text-neutral-400">Saturation</span>
              <span className="font-mono text-neutral-300">{currentFilters.saturation}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="250"
              value={currentFilters.saturation}
              onChange={(e) => onUpdateFilters({ saturation: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
            />
          </div>

          {/* Hue Rotation */}
          <div className="space-y-1">
            <div className="flex justify-between text-[11px]">
              <span className="text-neutral-400">Hue Shift</span>
              <span className="font-mono text-neutral-300">{currentFilters.hueRotate}°</span>
            </div>
            <input
              type="range"
              min="0"
              max="360"
              value={currentFilters.hueRotate}
              onChange={(e) => onUpdateFilters({ hueRotate: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
            />
          </div>

          {/* Vignette */}
          <div className="space-y-1">
            <div className="flex justify-between text-[11px]">
              <span className="text-neutral-400">Cinematic Vignette</span>
              <span className="font-mono text-neutral-300">{currentFilters.vignette}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={currentFilters.vignette}
              onChange={(e) => onUpdateFilters({ vignette: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
            />
          </div>

          {/* Blur */}
          <div className="space-y-1">
            <div className="flex justify-between text-[11px]">
              <span className="text-neutral-400">Defocus / Blur</span>
              <span className="font-mono text-neutral-300">{currentFilters.blur}px</span>
            </div>
            <input
              type="range"
              min="0"
              max="15"
              value={currentFilters.blur}
              onChange={(e) => onUpdateFilters({ blur: Number(e.target.value) })}
              className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
            />
          </div>
        </div>
      </div>
    </div>
  )
}
