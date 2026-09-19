export type TrackType = 'video' | 'audio' | 'text' | 'overlay' | 'effect'

export type AspectRatio = '16:9' | '9:16' | '1:1' | '4:5'

export interface Keyframe {
  id: string
  timeMs: number
  opacity?: number
  scale?: number
  positionX?: number
  positionY?: number
}

export interface ClipEffect {
  id: string
  type: string
  name: string
  intensity: number
  enabled: boolean
}

export interface VideoFilter {
  brightness: number // 0-200, default 100
  contrast: number // 0-200, default 100
  saturation: number // 0-200, default 100
  hueRotate: number // 0-360, default 0
  blur: number // 0-20, default 0
  vignette: number // 0-100, default 0
  lut: string // 'none' | 'vintage' | 'cyberpunk' | 'warm' | 'cool' | 'bnw' | 'teal_orange'
}

export interface Clip {
  id: string
  trackId: string
  name: string
  type: 'video' | 'audio' | 'text' | 'image'
  startTimeMs: number
  durationMs: number
  sourceDurationMs: number
  trimStartMs: number
  
  // Media source
  mediaUrl?: string
  thumbnailUrl?: string
  color: string
  
  // Transform & Visuals
  positionX: number
  positionY: number
  scale: number
  rotation: number
  opacity: number
  blendMode: 'normal' | 'multiply' | 'screen' | 'overlay' | 'additive'
  
  // Speed & Audio
  speed: number
  volume: number
  fadeInMs: number
  fadeOutMs: number
  
  // Text specific
  textContent?: string
  fontSize?: number
  textColor?: string
  fontFamily?: string
  textBgColor?: string
  
  // Effects & Filters
  filters: VideoFilter
  effects: ClipEffect[]
  keyframes: Keyframe[]
}

export interface Track {
  id: string
  name: string
  type: TrackType
  isMuted: boolean
  isLocked: boolean
  isVisible: boolean
  height: number
  clips: Clip[]
}

export interface ProjectSettings {
  name: string
  aspectRatio: AspectRatio
  fps: number
  resolution: { width: number; height: number }
  durationMs: number
}

export type ActiveToolTab = 'media' | 'effects' | 'audio' | 'text' | 'ai' | 'stickers'
