import type { FileItem } from '@/stores/file'
import { fileApiUrl } from '@/api/http'

function isImage(mime?: string | null) {
  return (mime || '').toLowerCase().startsWith('image/')
}

function isVideo(mime?: string | null, name?: string) {
  const m = (mime || '').toLowerCase()
  if (m.startsWith('video/')) return true
  const lower = (name || '').toLowerCase()
  return ['.mp4', '.webm', '.mkv', '.avi', '.mov'].some((ext) => lower.endsWith(ext))
}

export function fileHasCover(row: FileItem): boolean {
  if (row.type !== 'file') return false
  if (row.hasThumbnail) return true
  if (isImage(row.mimeType)) return true
  // 视频需等缩略图生成后再展示封面（H5 列表内 video 预览不稳定）
  if (isVideo(row.mimeType, row.name)) return false
  return false
}

export function fileCoverKind(row: FileItem): 'image' | 'video' | null {
  if (!fileHasCover(row)) return null
  if (row.hasThumbnail || isImage(row.mimeType)) return 'image'
  return null
}

export function fileCoverUrl(row: FileItem): string {
  if (row.hasThumbnail) {
    return fileApiUrl(`/api/files/${row.id}/thumbnail`)
  }
  if (isImage(row.mimeType)) {
    return fileApiUrl(`/api/files/${row.id}/preview`)
  }
  if (isVideo(row.mimeType, row.name)) {
    return fileApiUrl(`/api/files/${row.id}/preview`)
  }
  return ''
}

export function fileIconText(row: FileItem): string {
  if (row.type === 'folder') return '📁'
  const name = row.name || ''
  const dot = name.lastIndexOf('.')
  if (dot > 0) return name.substring(dot + 1).toUpperCase().slice(0, 4)
  return 'FILE'
}

export function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export function isImageFile(row: FileItem) {
  return row.type === 'file' && isImage(row.mimeType)
}

export function isVideoFile(row: FileItem) {
  return row.type === 'file' && isVideo(row.mimeType, row.name)
}
