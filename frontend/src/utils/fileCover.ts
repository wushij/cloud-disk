import type { FileItem } from '@/stores/file'
import { TOKEN_KEY } from '@/api/http'

function accessToken(): string {
  return encodeURIComponent(localStorage.getItem(TOKEN_KEY) || '')
}

function isImageMime(mime?: string | null): boolean {
  return (mime || '').toLowerCase().startsWith('image/')
}

function isVideoFile(mime?: string | null, name?: string): boolean {
  const lowerMime = (mime || '').toLowerCase()
  if (lowerMime.startsWith('video/')) return true
  const lowerName = (name || '').toLowerCase()
  return ['.mp4', '.webm', '.mkv', '.avi', '.mov'].some((ext) => lowerName.endsWith(ext))
}

/** 是否展示真实封面（图片/视频），而非扩展名占位图标 */
export function fileHasCover(row: FileItem): boolean {
  if (row.type !== 'file') return false
  if (row.hasThumbnail) return true
  return isImageMime(row.mimeType) || isVideoFile(row.mimeType, row.name)
}

/** 封面用 img（含已生成缩略图/海报）还是 video（视频首帧） */
export function fileCoverKind(row: FileItem): 'image' | 'video' | null {
  if (!fileHasCover(row)) return null
  if (row.hasThumbnail || isImageMime(row.mimeType)) return 'image'
  if (isVideoFile(row.mimeType, row.name)) return 'video'
  return null
}

export function fileCoverUrl(row: FileItem): string {
  const token = accessToken()
  if (row.hasThumbnail) {
    return `/api/files/${row.id}/thumbnail?access_token=${token}`
  }
  if (isImageMime(row.mimeType)) {
    return `/api/files/${row.id}/preview?access_token=${token}`
  }
  if (isVideoFile(row.mimeType, row.name)) {
    return `/api/files/${row.id}/preview?access_token=${token}`
  }
  return ''
}
