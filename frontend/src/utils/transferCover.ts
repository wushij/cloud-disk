import { mediaApiUrl } from '@/utils/mediaUrl'
import { loadCoverThumb } from '@/utils/coverCache'
import type { TransferTask } from '@/stores/transfer'

const imageExts = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp']
const videoExts = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'webm']

function fileExt(name: string): string {
  return name.split('.').pop()?.toLowerCase() || ''
}

function isImageName(name: string): boolean {
  return imageExts.includes(fileExt(name))
}

function isVideoName(name: string): boolean {
  return videoExts.includes(fileExt(name))
}

export function taskCoverKind(t: TransferTask): 'image' | null {
  if (t.coverUrl) return 'image'
  if (t.fileObj?.type.startsWith('image/')) return 'image'
  if (!t.fileId) return null
  if (isImageName(t.name) || isVideoName(t.name)) return 'image'
  return null
}

export function taskHasCover(t: TransferTask): boolean {
  if (t.coverUrl) return true
  if (t.fileObj?.type.startsWith('image/')) return true
  if (!t.fileId) return false
  if (isImageName(t.name)) return true
  if (isVideoName(t.name)) return !!loadCoverThumb(t.fileId, 0)
  return false
}

export function taskIsVideoCover(t: TransferTask): boolean {
  return !!t.fileId && isVideoName(t.name)
}

export function taskCoverSrc(t: TransferTask, usePreviewFallback = false): string {
  if (t.coverUrl) return t.coverUrl
  if (!t.fileId) return ''
  if (isVideoName(t.name)) {
    return loadCoverThumb(t.fileId, 0) || ''
  }
  if (isImageName(t.name)) {
    if (usePreviewFallback) {
      return mediaApiUrl(`/api/files/${t.fileId}/preview`)
    }
    return mediaApiUrl(`/api/files/${t.fileId}/thumbnail`)
  }
  return ''
}
