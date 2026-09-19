import React from 'react'
import { 
  Scissors, 
  RotateCcw, 
  RotateCw, 
  Download, 
  Sparkles, 
  Trash2, 
  Maximize2, 
  Tv, 
  Smartphone, 
  Square,
  ZoomIn,
  ZoomOut
} from 'lucide-react'
import { AspectRatio } from '../types/studio'

interface HeaderProps {
  projectName: string
  onProjectNameChange: (name: string) => void
  aspectRatio: AspectRatio
  onAspectRatioChange: (ratio: AspectRatio) => void
  onSplit: () => void
  onDeleteSelected: () => void
  canUndo: boolean
  canRedo: boolean
  onUndo: () => void
  onRedo: () => void
  onOpenExport: () => void
  zoom: number
  onZoomChange: (newZoom: number) => void
  hasSelectedClip: boolean
}

export const Header: React.FC<HeaderProps> = ({
  projectName,
  onProjectNameChange,
  aspectRatio,
  onAspectRatioChange,
  onSplit,
  onDeleteSelected,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  onOpenExport,
  zoom,
  onZoomChange,
  hasSelectedClip,
}) => {
  return (
    <header 
      id="studio-header" 
      className="h-14 bg-neutral-900 border-b border-neutral-800 px-4 flex items-center justify-between select-none z-20"
    >
      {/* Brand & Project Title */}
      <div className="flex items-center space-x-3">
        <div className="flex items-center space-x-2">
          <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-black text-white text-base shadow-lg shadow-indigo-500/20">
            AH
          </div>
          <span className="font-bold tracking-tight text-neutral-100 text-sm hidden sm:inline">
            Studio <span className="text-xs px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-400 font-mono ml-1">6.8</span>
          </span>
        </div>

        <div className="h-4 w-px bg-neutral-800" />

        <input
          id="project-name-input"
          type="text"
          value={projectName}
          onChange={(e) => onProjectNameChange(e.target.value)}
          className="bg-transparent hover:bg-neutral-800/80 focus:bg-neutral-800 text-xs font-medium text-neutral-200 px-2 py-1 rounded border border-transparent focus:border-neutral-700 transition outline-none w-36 sm:w-48"
          title="Click to rename project"
        />
      </div>

      {/* Center Tools: Aspect Ratio & Timeline Quick Cuts */}
      <div className="flex items-center space-x-2">
        {/* Aspect Ratio Selector */}
        <div className="flex items-center bg-neutral-950 p-0.5 rounded-lg border border-neutral-800 text-xs">
          <button
            id="aspect-16-9"
            onClick={() => onAspectRatioChange('16:9')}
            className={`flex items-center space-x-1 px-2 py-1 rounded-md transition ${
              aspectRatio === '16:9' ? 'bg-neutral-800 text-white font-medium shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
            }`}
            title="16:9 Landscape (YouTube/TV)"
          >
            <Tv className="w-3.5 h-3.5" />
            <span className="hidden md:inline">16:9</span>
          </button>
          <button
            id="aspect-9-16"
            onClick={() => onAspectRatioChange('9:16')}
            className={`flex items-center space-x-1 px-2 py-1 rounded-md transition ${
              aspectRatio === '9:16' ? 'bg-neutral-800 text-white font-medium shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
            }`}
            title="9:16 Portrait (TikTok/Reels/Shorts)"
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span className="hidden md:inline">9:16</span>
          </button>
          <button
            id="aspect-1-1"
            onClick={() => onAspectRatioChange('1:1')}
            className={`flex items-center space-x-1 px-2 py-1 rounded-md transition ${
              aspectRatio === '1:1' ? 'bg-neutral-800 text-white font-medium shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
            }`}
            title="1:1 Square (Instagram)"
          >
            <Square className="w-3.5 h-3.5" />
            <span className="hidden md:inline">1:1</span>
          </button>
        </div>

        <div className="h-4 w-px bg-neutral-800" />

        {/* Edit Actions: Split & Delete */}
        <button
          id="action-split-clip"
          onClick={onSplit}
          className="flex items-center space-x-1 px-2.5 py-1.5 bg-neutral-800 hover:bg-neutral-700 active:bg-neutral-600 text-neutral-200 hover:text-white rounded-md text-xs font-medium transition border border-neutral-700/60"
          title="Split Clip at Playhead (Hotkey: S)"
        >
          <Scissors className="w-3.5 h-3.5 text-amber-400" />
          <span className="hidden lg:inline">Split</span>
        </button>

        <button
          id="action-delete-clip"
          onClick={onDeleteSelected}
          disabled={!hasSelectedClip}
          className={`flex items-center space-x-1 px-2.5 py-1.5 rounded-md text-xs font-medium transition border ${
            hasSelectedClip
              ? 'bg-neutral-800 hover:bg-red-950/40 text-red-400 hover:text-red-300 border-red-500/30'
              : 'bg-neutral-900/50 text-neutral-600 border-neutral-800 cursor-not-allowed'
          }`}
          title="Delete selected clip (Hotkey: Del / Backspace)"
        >
          <Trash2 className="w-3.5 h-3.5" />
          <span className="hidden lg:inline">Delete</span>
        </button>

        {/* Undo / Redo */}
        <div className="flex items-center space-x-1">
          <button
            id="action-undo"
            onClick={onUndo}
            disabled={!canUndo}
            className={`p-1.5 rounded-md transition ${
              canUndo ? 'text-neutral-300 hover:bg-neutral-800 hover:text-white' : 'text-neutral-600 cursor-not-allowed'
            }`}
            title="Undo (Ctrl+Z)"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
          <button
            id="action-redo"
            onClick={onRedo}
            disabled={!canRedo}
            className={`p-1.5 rounded-md transition ${
              canRedo ? 'text-neutral-300 hover:bg-neutral-800 hover:text-white' : 'text-neutral-600 cursor-not-allowed'
            }`}
            title="Redo (Ctrl+Y)"
          >
            <RotateCw className="w-4 h-4" />
          </button>
        </div>

        {/* Zoom Controls */}
        <div className="hidden sm:flex items-center space-x-1 bg-neutral-950 px-2 py-0.5 rounded-md border border-neutral-800 text-xs">
          <button
            id="zoom-out-btn"
            onClick={() => onZoomChange(Math.max(0.5, zoom - 0.25))}
            className="text-neutral-400 hover:text-white p-0.5"
            title="Zoom Out Timeline"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>
          <span className="text-[11px] font-mono text-neutral-400 w-8 text-center">{Math.round(zoom * 100)}%</span>
          <button
            id="zoom-in-btn"
            onClick={() => onZoomChange(Math.min(3.0, zoom + 0.25))}
            className="text-neutral-400 hover:text-white p-0.5"
            title="Zoom In Timeline"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Right Side: Export Engine Button */}
      <div className="flex items-center space-x-2">
        <button
          id="open-export-btn"
          onClick={onOpenExport}
          className="flex items-center space-x-1.5 bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white font-semibold px-3.5 py-1.5 rounded-lg text-xs shadow-md shadow-indigo-600/30 transition active:scale-95"
        >
          <Download className="w-3.5 h-3.5" />
          <span>Export 4K/HD</span>
        </button>
      </div>
    </header>
  )
}
