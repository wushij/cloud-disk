import type { FileItem } from '@/stores/file'

export type FileKind =
  | 'folder'
  | 'image'
  | 'video'
  | 'audio'
  | 'archive'
  | 'pdf'
  | 'doc'
  | 'sheet'
  | 'slide'
  | 'text'
  | 'code'
  | 'default'

function fileExt(row: FileItem): string {
  const name = row.name || ''
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : ''
}

export function fileTypeKind(row: FileItem): FileKind {
  if (row.type === 'folder') return 'folder'

  const ext = fileExt(row)
  const mime = (row.mimeType || '').toLowerCase()

  if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext)) {
    return 'image'
  }
  if (mime.startsWith('video/') || ['mp4', 'mkv', 'avi', 'mov', 'flv', 'webm', 'wmv'].includes(ext)) {
    return 'video'
  }
  if (mime.startsWith('audio/') || ['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a'].includes(ext)) {
    return 'audio'
  }
  if (
    ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz'].includes(ext) ||
    mime.includes('zip') ||
    mime.includes('rar') ||
    mime.includes('compressed') ||
    mime.includes('archive')
  ) {
    return 'archive'
  }
  if (ext === 'pdf' || mime.includes('pdf')) return 'pdf'
  if (['doc', 'docx'].includes(ext) || mime.includes('word') || mime.includes('document')) return 'doc'
  if (['xls', 'xlsx', 'csv'].includes(ext) || mime.includes('sheet') || mime.includes('excel')) return 'sheet'
  if (['ppt', 'pptx'].includes(ext) || mime.includes('presentation') || mime.includes('powerpoint')) return 'slide'
  if (['html', 'css', 'js', 'ts', 'vue', 'jsx', 'tsx', 'java', 'py', 'go', 'rs'].includes(ext)) return 'code'
  if (['txt', 'md', 'log', 'json', 'xml'].includes(ext) || mime.startsWith('text/')) return 'text'
  return 'default'
}

const COLOR_MAP: Record<FileKind, string> = {
  folder: '#f59e0b',
  image: '#10b981',
  video: '#8b5cf6',
  audio: '#ec4899',
  archive: '#ea580c',
  pdf: '#ef4444',
  doc: '#2563eb',
  sheet: '#059669',
  slide: '#d97706',
  text: '#64748b',
  code: '#6366f1',
  default: '#64748b'
}

const ICON_MAP: Record<FileKind, string> = {
  folder: 'folder',
  image: 'photo',
  video: 'play-circle',
  audio: 'volume',
  archive: 'bag-fill',
  pdf: 'file-text-fill',
  doc: 'file-text-fill',
  sheet: 'file-text-fill',
  slide: 'file-text-fill',
  text: 'file-text-fill',
  code: 'file-text-fill',
  default: 'file-text-fill'
}

export function fileTypeColor(row: FileItem): string {
  return COLOR_MAP[fileTypeKind(row)]
}

export function fileTypeIcon(row: FileItem): string {
  return ICON_MAP[fileTypeKind(row)]
}

export function fileExtLabel(row: FileItem): string {
  if (row.type === 'folder') return ''
  const name = row.name || ''
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toUpperCase().slice(0, 4) : 'FILE'
}
