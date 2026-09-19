import React, { useState, useEffect } from 'react'
import { 
  X, 
  Download, 
  CheckCircle2, 
  Film, 
  Sparkles, 
  Cpu, 
  Gauge, 
  Layers,
  Loader2 
} from 'lucide-react'
import { AspectRatio, Track } from '../types/studio'

interface ExportModalProps {
  isOpen: boolean
  onClose: () => void
  projectName: string
  aspectRatio: AspectRatio
  durationMs: number
  tracks: Track[]
}

export const ExportModal: React.FC<ExportModalProps> = ({
  isOpen,
  onClose,
  projectName,
  aspectRatio,
  durationMs,
  tracks,
}) => {
  const [resolution, setResolution] = useState<'720p' | '1080p' | '4k'>('1080p')
  const [fps, setFps] = useState<number>(30)
  const [format, setFormat] = useState<'mp4' | 'webm'>('mp4')
  const [bitrate, setBitrate] = useState<'balanced' | 'high' | 'ultra'>('high')

  const [isExporting, setIsExporting] = useState(false)
  const [exportProgress, setExportProgress] = useState(0)
  const [currentPass, setCurrentPass] = useState<'compositing' | 'encoding' | 'muxing' | 'complete'>('compositing')
  const [downloadUrl, setDownloadUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!isOpen) {
      setIsExporting(false)
      setExportProgress(0)
      setCurrentPass('compositing')
      setDownloadUrl(null)
    }
  }, [isOpen])

  if (!isOpen) return null

  const handleStartExport = () => {
    setIsExporting(true)
    setExportProgress(0)
    setCurrentPass('compositing')

    // Simulate multi-pass GPU hardware export
    const interval = setInterval(() => {
      setExportProgress((prev) => {
        if (prev < 40) {
          setCurrentPass('compositing')
          return prev + 4
        } else if (prev < 85) {
          setCurrentPass('encoding')
          return prev + 3
        } else if (prev < 99) {
          setCurrentPass('muxing')
          return prev + 2
        } else {
          clearInterval(interval)
          setCurrentPass('complete')
          // Generate simulated downloadable video file blob
          const blob = new Blob(
            [`AH Studio Render Output: ${projectName} (${resolution} ${fps}fps)`],
            { type: format === 'mp4' ? 'video/mp4' : 'video/webm' }
          )
          setDownloadUrl(URL.createObjectURL(blob))
          return 100
        }
      })
    }, 120)
  }

  const estimatedSizeMb = () => {
    const durSec = durationMs / 1000
    const bitrates = { balanced: 6, high: 12, ultra: 24 }
    const resMultiplier = resolution === '4k' ? 3.5 : resolution === '1080p' ? 1.5 : 1
    return ((durSec * bitrates[bitrate] * resMultiplier) / 8).toFixed(1)
  }

  return (
    <div 
      id="export-modal-backdrop" 
      className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 select-none"
    >
      <div 
        id="export-modal-card"
        className="bg-neutral-900 border border-neutral-700/80 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl flex flex-col"
      >
        {/* Modal Header */}
        <div className="px-5 py-4 border-b border-neutral-800 flex items-center justify-between bg-neutral-950">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-600/20 text-indigo-400 flex items-center justify-center">
              <Download className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-neutral-100">Export Video</h3>
              <p className="text-[11px] text-neutral-400 font-mono">{projectName} • {aspectRatio}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-5 space-y-4">
          {!isExporting ? (
            <>
              {/* Resolution Options */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-neutral-300">Resolution Preset</label>
                <div className="grid grid-cols-3 gap-2">
                  {[
                    { id: '720p', label: '720p HD', desc: 'Fast & Lightweight' },
                    { id: '1080p', label: '1080p FHD', desc: 'YouTube & Reels' },
                    { id: '4k', label: '4K Ultra HD', desc: 'Master Quality' },
                  ].map((item) => (
                    <button
                      key={item.id}
                      onClick={() => setResolution(item.id as typeof resolution)}
                      className={`p-2.5 rounded-xl border text-left transition ${
                        resolution === item.id
                          ? 'bg-indigo-600/20 border-indigo-500 text-white shadow-md'
                          : 'bg-neutral-950 border-neutral-800 text-neutral-400 hover:border-neutral-700'
                      }`}
                    >
                      <p className="text-xs font-bold">{item.label}</p>
                      <p className="text-[10px] text-neutral-500 mt-0.5">{item.desc}</p>
                    </button>
                  ))}
                </div>
              </div>

              {/* Frame Rate & Format */}
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-neutral-300">Frame Rate</label>
                  <div className="grid grid-cols-3 gap-1 bg-neutral-950 p-1 rounded-xl border border-neutral-800">
                    {[24, 30, 60].map((f) => (
                      <button
                        key={f}
                        onClick={() => setFps(f)}
                        className={`py-1 rounded-lg text-xs font-mono transition ${
                          fps === f ? 'bg-indigo-600 text-white font-bold' : 'text-neutral-400 hover:text-white'
                        }`}
                      >
                        {f}fps
                      </button>
                    ))}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-neutral-300">Container Format</label>
                  <div className="grid grid-cols-2 gap-1 bg-neutral-950 p-1 rounded-xl border border-neutral-800">
                    {(['mp4', 'webm'] as const).map((fmt) => (
                      <button
                        key={fmt}
                        onClick={() => setFormat(fmt)}
                        className={`py-1 rounded-lg text-xs font-mono uppercase transition ${
                          format === fmt ? 'bg-indigo-600 text-white font-bold' : 'text-neutral-400 hover:text-white'
                        }`}
                      >
                        {fmt}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Quality & Estimated Size info card */}
              <div className="bg-neutral-950 p-3 rounded-xl border border-neutral-800 flex items-center justify-between text-xs">
                <div className="space-y-0.5">
                  <span className="text-neutral-400 text-[11px]">Hardware Acceleration</span>
                  <p className="font-semibold text-emerald-400 flex items-center space-x-1">
                    <Cpu className="w-3.5 h-3.5" />
                    <span>GPU WebGL / WebCodecs</span>
                  </p>
                </div>
                <div className="text-right space-y-0.5">
                  <span className="text-neutral-400 text-[11px]">Est. File Size</span>
                  <p className="font-mono font-bold text-neutral-200">~{estimatedSizeMb()} MB</p>
                </div>
              </div>
            </>
          ) : (
            /* Active Export Progress Screen */
            <div className="py-6 flex flex-col items-center justify-center space-y-5">
              {currentPass !== 'complete' ? (
                <div className="relative w-24 h-24 flex items-center justify-center">
                  <div className="absolute inset-0 rounded-full border-4 border-neutral-800" />
                  <div 
                    className="absolute inset-0 rounded-full border-4 border-indigo-500 border-t-transparent animate-spin" 
                  />
                  <span className="text-lg font-black text-white font-mono">{exportProgress}%</span>
                </div>
              ) : (
                <div className="w-20 h-20 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center animate-bounce">
                  <CheckCircle2 className="w-10 h-10" />
                </div>
              )}

              <div className="text-center space-y-1">
                <h4 className="text-sm font-bold text-neutral-100">
                  {currentPass === 'compositing' && 'Compositing Timeline Layers...'}
                  {currentPass === 'encoding' && `Encoding ${resolution} ${fps}fps Video...`}
                  {currentPass === 'muxing' && 'Muxing AAC Stereo Audio Tracks...'}
                  {currentPass === 'complete' && 'Render Completed Successfully!'}
                </h4>
                <p className="text-xs text-neutral-400 font-mono">
                  {currentPass !== 'complete' ? `${exportProgress}% rendered` : `${projectName}.${format} ready for download`}
                </p>
              </div>

              {/* Progress Bar */}
              <div className="w-full bg-neutral-950 h-2 rounded-full overflow-hidden border border-neutral-800">
                <div 
                  className="h-full bg-gradient-to-r from-indigo-500 to-cyan-400 transition-all duration-150 rounded-full"
                  style={{ width: `${exportProgress}%` }}
                />
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-5 py-4 border-t border-neutral-800 flex items-center justify-end space-x-2 bg-neutral-950">
          {!isExporting ? (
            <>
              <button
                onClick={onClose}
                className="px-4 py-2 text-xs font-medium text-neutral-400 hover:text-white rounded-lg transition hover:bg-neutral-800"
              >
                Cancel
              </button>
              <button
                id="btn-render-export"
                onClick={handleStartExport}
                className="flex items-center space-x-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs rounded-lg shadow-lg shadow-indigo-600/30 transition active:scale-95"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Start Render</span>
              </button>
            </>
          ) : currentPass === 'complete' && downloadUrl ? (
            <a
              id="btn-download-rendered-video"
              href={downloadUrl}
              download={`${projectName.toLowerCase().replace(/\s+/g, '-')}.${format}`}
              className="flex items-center space-x-2 px-6 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs rounded-lg shadow-lg shadow-emerald-600/30 transition active:scale-95"
            >
              <Download className="w-4 h-4" />
              <span>Download {format.toUpperCase()}</span>
            </a>
          ) : (
            <span className="text-xs text-neutral-500 animate-pulse">Rendering in progress...</span>
          )}
        </div>
      </div>
    </div>
  )
}
