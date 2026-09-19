import React from 'react'
import { Type, Sparkles, Plus, Palette, Smile } from 'lucide-react'
import { Clip } from '../../types/studio'
import { STICKER_ITEMS } from '../../data/sampleMedia'

interface TextStickersPanelProps {
  selectedClip: Clip | null
  onAddTextClip: (preset: { content: string; size: number; color: string; bg: string }) => void
  onAddStickerClip: (sticker: { label: string; bg: string }) => void
  onUpdateTextProps: (updates: { textContent?: string; fontSize?: number; textColor?: string; textBgColor?: string }) => void
}

export const TextStickersPanel: React.FC<TextStickersPanelProps> = ({
  selectedClip,
  onAddTextClip,
  onAddStickerClip,
  onUpdateTextProps,
}) => {
  const isTextSelected = selectedClip?.type === 'text'

  return (
    <div id="panel-text-stickers" className="flex flex-col h-full overflow-y-auto p-4 space-y-5 select-none">
      {/* Selected Text Inspector if active */}
      {isTextSelected && (
        <div className="space-y-3 bg-neutral-900/90 p-3.5 rounded-xl border border-indigo-500/40 shadow-lg shadow-indigo-500/5">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-bold text-indigo-300 flex items-center space-x-1.5">
              <Type className="w-3.5 h-3.5" />
              <span>Edit Selected Text</span>
            </h4>
            <span className="text-[10px] text-neutral-400 font-mono">Layer Active</span>
          </div>

          <div className="space-y-1">
            <label className="text-[11px] text-neutral-400">Content</label>
            <input
              type="text"
              value={selectedClip.textContent || ''}
              onChange={(e) => onUpdateTextProps({ textContent: e.target.value })}
              className="w-full bg-neutral-950 border border-neutral-700 rounded-lg px-2.5 py-1.5 text-xs text-white outline-none focus:border-indigo-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <label className="text-[11px] text-neutral-400">Font Size ({selectedClip.fontSize || 36}px)</label>
              <input
                type="range"
                min="16"
                max="80"
                value={selectedClip.fontSize || 36}
                onChange={(e) => onUpdateTextProps({ fontSize: Number(e.target.value) })}
                className="w-full accent-indigo-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer"
              />
            </div>
            <div className="space-y-1">
              <label className="text-[11px] text-neutral-400">Text Color</label>
              <div className="flex items-center space-x-2">
                <input
                  type="color"
                  value={selectedClip.textColor || '#ffffff'}
                  onChange={(e) => onUpdateTextProps({ textColor: e.target.value })}
                  className="w-8 h-7 bg-transparent border-0 cursor-pointer"
                />
                <span className="text-xs font-mono text-neutral-300">{selectedClip.textColor || '#ffffff'}</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Text Style Presets */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Type className="w-3.5 h-3.5 text-indigo-400" />
            <span>Title & Subtitle Presets</span>
          </h4>
        </div>

        <div className="space-y-2">
          <button
            onClick={() => onAddTextClip({ content: 'BOLD HEADLINE', size: 48, color: '#ffffff', bg: 'rgba(0,0,0,0.6)' })}
            className="w-full flex items-center justify-between p-3 rounded-lg bg-neutral-900 hover:bg-neutral-850 border border-neutral-800 hover:border-indigo-500/60 transition text-left group"
          >
            <div>
              <p className="text-sm font-black tracking-wide text-white">BOLD HEADLINE</p>
              <p className="text-[10px] text-neutral-500">Center Main Title • 48px</p>
            </div>
            <Plus className="w-4 h-4 text-neutral-400 group-hover:text-indigo-400" />
          </button>

          <button
            onClick={() => onAddTextClip({ content: 'Lower-Third Subtitle', size: 28, color: '#fef08a', bg: 'rgba(15,23,42,0.85)' })}
            className="w-full flex items-center justify-between p-3 rounded-lg bg-neutral-900 hover:bg-neutral-850 border border-neutral-800 hover:border-indigo-500/60 transition text-left group"
          >
            <div>
              <p className="text-xs font-semibold text-amber-200">Lower-Third Subtitle</p>
              <p className="text-[10px] text-neutral-500">Narrator & Caption • 28px</p>
            </div>
            <Plus className="w-4 h-4 text-neutral-400 group-hover:text-indigo-400" />
          </button>

          <button
            onClick={() => onAddTextClip({ content: '⚡ CYBER GLOW', size: 40, color: '#06b6d4', bg: 'rgba(6,182,212,0.15)' })}
            className="w-full flex items-center justify-between p-3 rounded-lg bg-neutral-900 hover:bg-neutral-850 border border-neutral-800 hover:border-cyan-500/60 transition text-left group"
          >
            <div>
              <p className="text-xs font-mono font-bold text-cyan-400 tracking-wider">⚡ CYBER GLOW</p>
              <p className="text-[10px] text-neutral-500">Neon Glow Accent • 40px</p>
            </div>
            <Plus className="w-4 h-4 text-neutral-400 group-hover:text-cyan-400" />
          </button>
        </div>
      </div>

      {/* Stickers & Badges */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Smile className="w-3.5 h-3.5 text-pink-400" />
            <span>Stickers & Elements</span>
          </h4>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {STICKER_ITEMS.map((sticker) => (
            <button
              key={sticker.id}
              onClick={() => onAddStickerClip(sticker)}
              className="p-2.5 rounded-lg border border-neutral-800 bg-neutral-900 hover:border-pink-500/50 hover:bg-neutral-850 flex items-center justify-between transition group"
            >
              <span className={`text-[10px] px-2 py-1 rounded-md ${sticker.bg}`}>
                {sticker.label}
              </span>
              <Plus className="w-3.5 h-3.5 text-neutral-400 group-hover:text-pink-400" />
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
