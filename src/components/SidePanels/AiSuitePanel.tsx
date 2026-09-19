import React, { useState } from 'react'
import { 
  Sparkles, 
  Wand2, 
  Subtitles, 
  Scissors, 
  ScanFace, 
  ShieldCheck, 
  Layers,
  CheckCircle2,
  Loader2
} from 'lucide-react'
import { Clip } from '../../types/studio'

interface AiSuitePanelProps {
  selectedClip: Clip | null
  onGenerateCaptions: (style: 'yellow' | 'minimal' | 'neon') => void
  onApplyAiColorCorrection: () => void
  onApplyBackgroundRemover: () => void
  onDetectScenesAndSplit: () => void
  onToggleMotionTracking: () => void
}

export const AiSuitePanel: React.FC<AiSuitePanelProps> = ({
  selectedClip,
  onGenerateCaptions,
  onApplyAiColorCorrection,
  onApplyBackgroundRemover,
  onDetectScenesAndSplit,
  onToggleMotionTracking,
}) => {
  const [captionStyle, setCaptionStyle] = useState<'yellow' | 'minimal' | 'neon'>('yellow')
  const [isProcessingCaptions, setIsProcessingCaptions] = useState(false)
  const [isProcessingColor, setIsProcessingColor] = useState(false)
  const [isProcessingMatting, setIsProcessingMatting] = useState(false)
  const [isProcessingScenes, setIsProcessingScenes] = useState(false)
  const [motionTrackingActive, setMotionTrackingActive] = useState(false)

  const handleGenerateCaptions = () => {
    setIsProcessingCaptions(true)
    setTimeout(() => {
      onGenerateCaptions(captionStyle)
      setIsProcessingCaptions(false)
    }, 1200)
  }

  const handleColorCorrection = () => {
    setIsProcessingColor(true)
    setTimeout(() => {
      onApplyAiColorCorrection()
      setIsProcessingColor(false)
    }, 800)
  }

  const handleMatting = () => {
    setIsProcessingMatting(true)
    setTimeout(() => {
      onApplyBackgroundRemover()
      setIsProcessingMatting(false)
    }, 1000)
  }

  const handleSceneDetection = () => {
    setIsProcessingScenes(true)
    setTimeout(() => {
      onDetectScenesAndSplit()
      setIsProcessingScenes(false)
    }, 1200)
  }

  return (
    <div id="panel-ai-suite" className="flex flex-col h-full overflow-y-auto p-4 space-y-5 select-none">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-indigo-950/60 to-purple-950/60 border border-indigo-800/40 rounded-xl p-3.5 flex items-start space-x-3">
        <div className="p-2 rounded-lg bg-indigo-500/20 text-indigo-400 mt-0.5 shrink-0">
          <Sparkles className="w-5 h-5" />
        </div>
        <div>
          <h3 className="text-xs font-bold text-neutral-100 flex items-center space-x-1.5">
            <span>AH Neural Video Engine</span>
            <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-mono">v6.8</span>
          </h3>
          <p className="text-[11px] text-neutral-400 mt-1 leading-relaxed">
            AI-powered auto-captioning, real-time subject matting, intelligent scene detection, and neural color grading.
          </p>
        </div>
      </div>

      {/* 1. AI Auto-Captions */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Subtitles className="w-4 h-4 text-amber-400" />
            <h4 className="text-xs font-bold text-neutral-200">Auto Captions & Subtitles</h4>
          </div>
          <span className="text-[10px] text-amber-400/90 font-medium">Whisper Neural</span>
        </div>

        <p className="text-[11px] text-neutral-400">
          Automatically transcribes speech and generates styled, animated subtitles synced to the timeline.
        </p>

        {/* Style Selector */}
        <div className="grid grid-cols-3 gap-1.5 text-[11px]">
          <button
            onClick={() => setCaptionStyle('yellow')}
            className={`py-1.5 px-2 rounded-md border text-center transition ${
              captionStyle === 'yellow'
                ? 'bg-amber-500/20 border-amber-500/80 text-amber-300 font-semibold'
                : 'bg-neutral-800 border-neutral-700 text-neutral-400 hover:text-white'
            }`}
          >
            Bold Yellow
          </button>
          <button
            onClick={() => setCaptionStyle('neon')}
            className={`py-1.5 px-2 rounded-md border text-center transition ${
              captionStyle === 'neon'
                ? 'bg-cyan-500/20 border-cyan-500/80 text-cyan-300 font-semibold'
                : 'bg-neutral-800 border-neutral-700 text-neutral-400 hover:text-white'
            }`}
          >
            Cyber Neon
          </button>
          <button
            onClick={() => setCaptionStyle('minimal')}
            className={`py-1.5 px-2 rounded-md border text-center transition ${
              captionStyle === 'minimal'
                ? 'bg-white/20 border-white/80 text-white font-semibold'
                : 'bg-neutral-800 border-neutral-700 text-neutral-400 hover:text-white'
            }`}
          >
            Minimalist
          </button>
        </div>

        <button
          id="btn-generate-ai-captions"
          onClick={handleGenerateCaptions}
          disabled={isProcessingCaptions}
          className="w-full flex items-center justify-center space-x-2 bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white font-medium py-2 rounded-lg text-xs shadow-md transition disabled:opacity-50"
        >
          {isProcessingCaptions ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Transcribing Timeline Speech...</span>
            </>
          ) : (
            <>
              <Wand2 className="w-3.5 h-3.5" />
              <span>Generate Timed Captions</span>
            </>
          )}
        </button>
      </div>

      {/* 2. AI Smart Color Correction */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Wand2 className="w-4 h-4 text-indigo-400" />
            <h4 className="text-xs font-bold text-neutral-200">Neural Color Correction</h4>
          </div>
          <span className="text-[10px] text-indigo-400 font-medium">HDR Auto-Grade</span>
        </div>

        <p className="text-[11px] text-neutral-400">
          Analyzes histogram, dynamic range, and exposure of {selectedClip ? `"${selectedClip.name}"` : 'selected clip'} to apply cinema-grade tonal grading.
        </p>

        <button
          id="btn-ai-auto-color"
          onClick={handleColorCorrection}
          disabled={isProcessingColor}
          className="w-full flex items-center justify-center space-x-2 bg-neutral-800 hover:bg-neutral-700 active:bg-neutral-600 text-neutral-200 hover:text-white font-medium py-2 rounded-lg text-xs border border-neutral-700 transition disabled:opacity-50"
        >
          {isProcessingColor ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin text-indigo-400" />
              <span>Optimizing Dynamic Curves...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
              <span>Auto-Enhance Selected Clip</span>
            </>
          )}
        </button>
      </div>

      {/* 3. AI Background Remover / Matting */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Layers className="w-4 h-4 text-emerald-400" />
            <h4 className="text-xs font-bold text-neutral-200">AI Background Remover (Matting)</h4>
          </div>
          <span className="text-[10px] text-emerald-400 font-medium">Chroma & Mask</span>
        </div>

        <p className="text-[11px] text-neutral-400">
          Segments subjects and removes green screens or complex video backgrounds without manual rotoscoping.
        </p>

        <button
          id="btn-ai-remove-bg"
          onClick={handleMatting}
          disabled={isProcessingMatting}
          className="w-full flex items-center justify-center space-x-2 bg-neutral-800 hover:bg-neutral-700 active:bg-neutral-600 text-neutral-200 hover:text-white font-medium py-2 rounded-lg text-xs border border-neutral-700 transition disabled:opacity-50"
        >
          {isProcessingMatting ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin text-emerald-400" />
              <span>Isolating Foreground Subject...</span>
            </>
          ) : (
            <>
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              <span>Toggle Smart Subject Isolation</span>
            </>
          )}
        </button>
      </div>

      {/* 4. AI Scene Detector */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Scissors className="w-4 h-4 text-blue-400" />
            <h4 className="text-xs font-bold text-neutral-200">AI Smart Cut & Scene Detection</h4>
          </div>
          <span className="text-[10px] text-blue-400 font-medium">Autocut</span>
        </div>

        <p className="text-[11px] text-neutral-400">
          Scans video timeline for optical camera shifts, flash frames, and cuts clips automatically.
        </p>

        <button
          id="btn-ai-scene-detect"
          onClick={handleSceneDetection}
          disabled={isProcessingScenes}
          className="w-full flex items-center justify-center space-x-2 bg-neutral-800 hover:bg-neutral-700 active:bg-neutral-600 text-neutral-200 hover:text-white font-medium py-2 rounded-lg text-xs border border-neutral-700 transition disabled:opacity-50"
        >
          {isProcessingScenes ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin text-blue-400" />
              <span>Detecting Scene Boundaries...</span>
            </>
          ) : (
            <>
              <Scissors className="w-3.5 h-3.5 text-blue-400" />
              <span>Detect Scenes & Auto-Cut</span>
            </>
          )}
        </button>
      </div>

      {/* 5. Motion Tracking & Face Mesh */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <ScanFace className="w-4 h-4 text-purple-400" />
            <h4 className="text-xs font-bold text-neutral-200">Motion Tracking & Landmark Deform</h4>
          </div>
          <span className="text-[10px] text-purple-400 font-medium">Pose & Mesh</span>
        </div>

        <p className="text-[11px] text-neutral-400">
          Tracks facial features and objects across timeline frames to anchor stickers or titles to moving elements.
        </p>

        <button
          id="btn-ai-motion-track"
          onClick={() => {
            setMotionTrackingActive(!motionTrackingActive)
            onToggleMotionTracking()
          }}
          className={`w-full flex items-center justify-center space-x-2 py-2 rounded-lg text-xs font-medium border transition ${
            motionTrackingActive
              ? 'bg-purple-600/30 border-purple-500 text-purple-300'
              : 'bg-neutral-800 hover:bg-neutral-700 border-neutral-700 text-neutral-200'
          }`}
        >
          {motionTrackingActive ? (
            <>
              <CheckCircle2 className="w-3.5 h-3.5 text-purple-400" />
              <span>Motion Tracking Enabled</span>
            </>
          ) : (
            <>
              <ScanFace className="w-3.5 h-3.5 text-purple-400" />
              <span>Enable Motion Tracking Target</span>
            </>
          )}
        </button>
      </div>
    </div>
  )
}
