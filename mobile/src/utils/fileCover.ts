import type { FileItem } from '@/stores/file'
import { fileApiUrl } from '@/api/http'
import { loadCoverThumb } from '@/utils/coverCache'

function isImage(mime?: string | null) {
  return (mime || '').toLowerCase().startsWith('image/')
}

function isVideo(mime?: string | null, name?: string) {
  const m = (mime || '').toLowerCase()
  if (m.startsWith('video/')) return true
  const lower = (name || '').toLowerCase()
  return ['.mp4', '.webm', '.mkv', '.avi', '.mov'].some((ext) => lower.endsWith(ext))
}

export interface FileCoverContext {
  shareCode?: string
  extractCode?: string
}

function apiOrigin(): string {
  const base = (import.meta.env.VITE_API_BASE || '').trim()
  if (/^https?:\/\//.test(base)) return base.replace(/\/$/, '')
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return ''
}

function shareCoverUrl(
  row: FileItem,
  shareCode: string,
  extractCode?: string,
  kind: 'thumbnail' | 'preview' = 'preview'
): string {
  const origin = apiOrigin()
  const ec = extractCode ? `&extractCode=${encodeURIComponent(extractCode)}` : ''
  return `${origin}/share/${shareCode}/${kind}?fileId=${row.id}${ec}`
}

/** 是否展示真实封面（图片/已有缩略图的视频），而非扩展名占位图标 */
export function fileHasCover(row: FileItem): boolean {
  if (row.type !== 'file') return false
  if (row.hasThumbnail) return true
  if (isImage(row.mimeType)) return true
  if (isVideo(row.mimeType, row.name) && loadCoverThumb(row.id, 0)) return true
  return false
}

export function fileCoverKind(row: FileItem): 'image' | null {
  return fileHasCover(row) ? 'image' : null
}

export function fileIsVideoCover(row: FileItem): boolean {
  return (
    row.type === 'file' &&
    isVideo(row.mimeType, row.name) &&
    (!!row.hasThumbnail || !!loadCoverThumb(row.id, 0))
  )
}

export function fileCoverUrl(row: FileItem, ctx?: FileCoverContext): string {
  if (ctx?.shareCode) {
    if (row.hasThumbnail) {
      return shareCoverUrl(row, ctx.shareCode, ctx.extractCode, 'thumbnail')
    }
    if (isImage(row.mimeType)) {
      return shareCoverUrl(row, ctx.shareCode, ctx.extractCode, 'preview')
    }
    return ''
  }
  if (row.hasThumbnail) {
    return fileApiUrl(`/api/files/${row.id}/thumbnail`)
  }
  if (isVideo(row.mimeType, row.name)) {
    const local = loadCoverThumb(row.id, 0)
    if (local) return local
  }
  if (isImage(row.mimeType)) {
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

export function shareSingleCoverUrl(
  fileId: number,
  mimeType: string | undefined,
  hasThumbnail: boolean | undefined,
  shareCode: string,
  extractCode?: string
): string {
  const row: FileItem = {
    id: fileId,
    name: '',
    type: 'file',
    mimeType,
    hasThumbnail
  }
  return fileCoverUrl(row, { shareCode, extractCode })
}
