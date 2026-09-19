import React, { useState } from 'react'
import { 
  Volume2, 
  VolumeX, 
  Mic, 
  Music, 
  Play, 
  Plus, 
  Sliders, 
  Sparkles,
  Radio
} from 'lucide-react'
import { Clip } from '../../types/studio'
import { SOUND_EFFECTS } from '../../data/sampleMedia'

interface AudioToolsPanelProps {
  selectedClip: Clip | null
  onUpdateAudioProps: (updates: { volume?: number; fadeInMs?: number; fadeOutMs?: number }) => void
  onAddSoundEffectToTimeline: (sfx: { name: string; duration: string; tone: number }) => void
}

export const AudioToolsPanel: React.FC<AudioToolsPanelProps> = ({
  selectedClip,
  onUpdateAudioProps,
  onAddSoundEffectToTimeline,
}) => {
  const [selectedVoiceEffect, setSelectedVoiceEffect] = useState<string>('normal')
  const [playingSfxId, setPlayingSfxId] = useState<string | null>(null)

  // Play sound effect using Web Audio API synthesis
  const playSfxPreview = (id: string, frequency: number) => {
    try {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
      const ctx = new AudioContextClass()
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()

      osc.type = id === 'sfx-3' ? 'sawtooth' : id === 'sfx-4' ? 'triangle' : 'sine'
      osc.frequency.setValueAtTime(frequency, ctx.currentTime)
      if (id === 'sfx-1') {
        osc.frequency.exponentialRampToValueAtTime(120, ctx.currentTime + 0.8)
      } else if (id === 'sfx-4') {
        osc.frequency.exponentialRampToValueAtTime(40, ctx.currentTime + 1.2)
      }

      gain.gain.setValueAtTime(0.3, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.6)

      osc.connect(gain)
      gain.connect(ctx.destination)

      osc.start()
      osc.stop(ctx.currentTime + 0.7)

      setPlayingSfxId(id)
      setTimeout(() => setPlayingSfxId(null), 700)
    } catch {
      // Audio context might be restricted before interaction
    }
  }

  return (
    <div id="panel-audio-tools" className="flex flex-col h-full overflow-y-auto p-4 space-y-5 select-none">
      {/* Clip Volume & Envelopes */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-200 flex items-center space-x-1.5">
            <Sliders className="w-3.5 h-3.5 text-purple-400" />
            <span>Volume & Envelopes</span>
          </h4>
          <span className="text-[10px] text-neutral-500 font-mono">
            {selectedClip ? selectedClip.name : 'No clip selected'}
          </span>
        </div>

        {/* Volume Level */}
        <div className="space-y-1">
          <div className="flex justify-between text-[11px]">
            <span className="text-neutral-400">Master Gain</span>
            <span className="font-mono text-neutral-300">
              {selectedClip ? Math.round(selectedClip.volume * 100) : 100}%
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="1.5"
            step="0.05"
            value={selectedClip?.volume ?? 1}
            disabled={!selectedClip}
            onChange={(e) => onUpdateAudioProps({ volume: Number(e.target.value) })}
            className="w-full accent-purple-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer disabled:opacity-40"
          />
        </div>

        {/* Fade In */}
        <div className="space-y-1">
          <div className="flex justify-between text-[11px]">
            <span className="text-neutral-400">Fade In</span>
            <span className="font-mono text-neutral-300">
              {((selectedClip?.fadeInMs ?? 0) / 1000).toFixed(1)}s
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="3000"
            step="100"
            value={selectedClip?.fadeInMs ?? 0}
            disabled={!selectedClip}
            onChange={(e) => onUpdateAudioProps({ fadeInMs: Number(e.target.value) })}
            className="w-full accent-purple-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer disabled:opacity-40"
          />
        </div>

        {/* Fade Out */}
        <div className="space-y-1">
          <div className="flex justify-between text-[11px]">
            <span className="text-neutral-400">Fade Out</span>
            <span className="font-mono text-neutral-300">
              {((selectedClip?.fadeOutMs ?? 0) / 1000).toFixed(1)}s
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="3000"
            step="100"
            value={selectedClip?.fadeOutMs ?? 0}
            disabled={!selectedClip}
            onChange={(e) => onUpdateAudioProps({ fadeOutMs: Number(e.target.value) })}
            className="w-full accent-purple-500 h-1.5 bg-neutral-800 rounded-lg cursor-pointer disabled:opacity-40"
          />
        </div>
      </div>

      {/* Voice Modulator / Audio Presets */}
      <div className="space-y-3 bg-neutral-900/70 p-3.5 rounded-xl border border-neutral-800">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-200 flex items-center space-x-1.5">
            <Mic className="w-3.5 h-3.5 text-indigo-400" />
            <span>Voice Enhancer & Modulator</span>
          </h4>
          <span className="text-[10px] text-indigo-400 font-mono">DSP FX</span>
        </div>

        <div className="grid grid-cols-3 gap-1.5 text-[11px]">
          {[
            { id: 'normal', name: 'Studio Pure' },
            { id: 'deep', name: 'Deep Cinematic' },
            { id: 'chipmunk', name: 'High Pitch' },
            { id: 'echo', name: 'Concert Hall' },
            { id: 'robot', name: 'Cyber Robot' },
            { id: 'radio', name: 'Walkie-Talkie' },
          ].map((fx) => (
            <button
              key={fx.id}
              onClick={() => setSelectedVoiceEffect(fx.id)}
              className={`py-1.5 px-2 rounded-md border text-center transition ${
                selectedVoiceEffect === fx.id
                  ? 'bg-purple-600/30 border-purple-500 text-purple-300 font-semibold'
                  : 'bg-neutral-800 border-neutral-700 text-neutral-400 hover:text-white'
              }`}
            >
              {fx.name}
            </button>
          ))}
        </div>
      </div>

      {/* Sound FX Catalog */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Music className="w-3.5 h-3.5 text-pink-400" />
            <span>Sound Effects Catalog</span>
          </h4>
          <span className="text-[10px] text-neutral-500 font-mono">Instant SFX</span>
        </div>

        <div className="space-y-1.5">
          {SOUND_EFFECTS.map((sfx) => (
            <div
              key={sfx.id}
              className="flex items-center justify-between p-2 rounded-lg bg-neutral-900/80 hover:bg-neutral-800 border border-neutral-800 transition"
            >
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => playSfxPreview(sfx.id, sfx.tone)}
                  className={`w-7 h-7 rounded-full flex items-center justify-center transition ${
                    playingSfxId === sfx.id ? 'bg-pink-500 text-white animate-pulse' : 'bg-neutral-800 text-pink-400 hover:bg-neutral-700'
                  }`}
                  title="Preview SFX"
                >
                  <Play className="w-3.5 h-3.5 fill-current ml-0.5" />
                </button>
                <div>
                  <p className="text-xs font-medium text-neutral-200">{sfx.name}</p>
                  <p className="text-[10px] text-neutral-500">{sfx.duration}</p>
                </div>
              </div>

              <button
                onClick={() => onAddSoundEffectToTimeline(sfx)}
                className="flex items-center space-x-1 px-2 py-1 bg-neutral-800 hover:bg-purple-600/40 text-neutral-300 hover:text-purple-200 rounded text-[11px] transition border border-neutral-700"
                title="Add SFX to Audio Track"
              >
                <Plus className="w-3 h-3" />
                <span>Add</span>
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
