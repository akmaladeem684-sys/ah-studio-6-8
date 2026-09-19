import React, { useRef } from 'react'
import { Plus, Upload, Film, Music, Image as ImageIcon } from 'lucide-react'
import { STOCK_VIDEOS, STOCK_AUDIO, StockMediaItem } from '../../data/sampleMedia'

interface MediaLibraryPanelProps {
  onAddMediaToTimeline: (media: StockMediaItem) => void
}

export const MediaLibraryPanel: React.FC<MediaLibraryPanelProps> = ({ onAddMediaToTimeline }) => {
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const isVideo = file.type.startsWith('video')
    const isAudio = file.type.startsWith('audio')
    const fileUrl = URL.createObjectURL(file)

    const customMedia: StockMediaItem = {
      id: `uploaded-${Date.now()}`,
      name: file.name.replace(/\.[^/.]+$/, ''),
      type: isVideo ? 'video' : isAudio ? 'audio' : 'image',
      durationMs: 8000,
      url: fileUrl,
      thumbnail: 'https://images.unsplash.com/photo-1574717024653-61fd2cf4d44d?w=300&q=80',
      category: 'Uploaded',
    }

    onAddMediaToTimeline(customMedia)
  }

  return (
    <div id="panel-media-library" className="flex flex-col h-full overflow-y-auto p-4 space-y-5 select-none">
      {/* Upload Drag/Drop Section */}
      <div
        onClick={() => fileInputRef.current?.click()}
        className="border-2 border-dashed border-neutral-700 hover:border-indigo-500 rounded-xl p-4 flex flex-col items-center justify-center text-center cursor-pointer transition bg-neutral-900/50 hover:bg-neutral-850 group"
      >
        <div className="w-10 h-10 rounded-full bg-indigo-500/10 text-indigo-400 group-hover:bg-indigo-500 group-hover:text-white flex items-center justify-center mb-2 transition">
          <Upload className="w-5 h-5" />
        </div>
        <p className="text-xs font-semibold text-neutral-200">Upload Video or Audio</p>
        <p className="text-[11px] text-neutral-500 mt-0.5">MP4, WebM, MOV, MP3, WAV</p>
        <input
          ref={fileInputRef}
          type="file"
          accept="video/*,audio/*,image/*"
          className="hidden"
          onChange={handleFileUpload}
        />
      </div>

      {/* Stock Cinematic Videos */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Film className="w-3.5 h-3.5 text-blue-400" />
            <span>Stock Clips</span>
          </h4>
          <span className="text-[10px] text-neutral-500 font-mono">4K Footage</span>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {STOCK_VIDEOS.map((video) => (
            <div
              key={video.id}
              className="group relative rounded-lg overflow-hidden border border-neutral-800 bg-neutral-900 hover:border-indigo-500/60 transition cursor-pointer"
              onClick={() => onAddMediaToTimeline(video)}
            >
              <div className="aspect-video relative overflow-hidden bg-neutral-950">
                <img
                  src={video.thumbnail}
                  alt={video.name}
                  className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                />
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition">
                  <div className="w-7 h-7 rounded-full bg-indigo-600 text-white flex items-center justify-center shadow-lg">
                    <Plus className="w-4 h-4" />
                  </div>
                </div>
                <span className="absolute bottom-1 right-1 bg-black/80 px-1 py-0.5 rounded text-[9px] font-mono text-neutral-300">
                  {video.durationMs / 1000}s
                </span>
              </div>
              <div className="p-1.5">
                <p className="text-[11px] font-medium text-neutral-200 truncate">{video.name}</p>
                <p className="text-[10px] text-neutral-500">{video.category}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Audio Soundtracks */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-bold text-neutral-300 tracking-wide uppercase flex items-center space-x-1.5">
            <Music className="w-3.5 h-3.5 text-purple-400" />
            <span>Soundtracks & Beats</span>
          </h4>
        </div>

        <div className="space-y-1.5">
          {STOCK_AUDIO.map((audio) => (
            <div
              key={audio.id}
              onClick={() => onAddMediaToTimeline(audio)}
              className="flex items-center justify-between p-2 rounded-lg bg-neutral-900/80 hover:bg-neutral-800 border border-neutral-800 hover:border-purple-500/50 transition cursor-pointer"
            >
              <div className="flex items-center space-x-2.5 min-w-0">
                <div className="w-8 h-8 rounded-md bg-purple-500/10 text-purple-400 flex items-center justify-center shrink-0">
                  <Music className="w-4 h-4" />
                </div>
                <div className="truncate">
                  <p className="text-xs font-medium text-neutral-200 truncate">{audio.name}</p>
                  <p className="text-[10px] text-neutral-500">{audio.category} • {(audio.durationMs / 1000).toFixed(0)}s</p>
                </div>
              </div>
              <button className="p-1 text-neutral-400 hover:text-white rounded hover:bg-neutral-700">
                <Plus className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
