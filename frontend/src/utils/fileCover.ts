import type { FileItem } from '@/stores/file'
import { mediaApiUrl } from '@/utils/mediaUrl'
import { loadCoverThumb } from '@/utils/coverCache'

function isImageMime(mime?: string | null): boolean {
  return (mime || '').toLowerCase().startsWith('image/')
}

function isVideoFile(mime?: string | null, name?: string): boolean {
  const lowerMime = (mime || '').toLowerCase()
  if (lowerMime.startsWith('video/')) return true
  const lowerName = (name || '').toLowerCase()
  return ['.mp4', '.webm', '.mkv', '.avi', '.mov'].some((ext) => lowerName.endsWith(ext))
}

/** 是否展示真实封面（图片/已有缩略图的视频），而非扩展名占位图标 */
export function fileHasCover(row: FileItem): boolean {
  if (row.type !== 'file') return false
  if (row.hasThumbnail) return true
  if (isImageMime(row.mimeType)) return true
  if (isVideoFile(row.mimeType, row.name) && loadCoverThumb(row.id, 0)) return true
  return false
}

/** 封面统一用 img（含视频海报缩略图） */
export function fileCoverKind(row: FileItem): 'image' | null {
  return fileHasCover(row) ? 'image' : null
}

/** 视频文件且已有缩略图/海报（用于显示播放角标） */
export function fileIsVideoCover(row: FileItem): boolean {
  return (
    row.type === 'file' &&
    isVideoFile(row.mimeType, row.name) &&
    (!!row.hasThumbnail || !!loadCoverThumb(row.id, 0))
  )
}

export function fileCoverUrl(row: FileItem): string {
  if (row.hasThumbnail) {
    return mediaApiUrl(`/api/files/${row.id}/thumbnail`)
  }
  if (isVideoFile(row.mimeType, row.name)) {
    const local = loadCoverThumb(row.id, 0)
    if (local) return local
  }
  if (isImageMime(row.mimeType)) {
    return mediaApiUrl(`/api/files/${row.id}/preview`)
  }
  return ''
}
