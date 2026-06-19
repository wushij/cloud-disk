import type { FileItem } from '@/stores/file'

export function fileTypeColor(row: FileItem): string {
  if (row.type === 'folder') return 'var(--cd-file-folder)'
  const mime = (row.mimeType || '').toLowerCase()
  if (mime.startsWith('image/')) return 'var(--cd-file-image)'
  if (mime.startsWith('video/')) return 'var(--cd-file-video)'
  if (mime.includes('pdf')) return 'var(--cd-file-pdf)'
  if (mime.includes('word') || mime.includes('document')) return 'var(--cd-file-doc)'
  if (mime.includes('sheet') || mime.includes('excel')) return 'var(--cd-file-image)'
  if (mime.includes('zip') || mime.includes('rar')) return 'var(--cd-file-default)'
  return 'var(--cd-file-default)'
}

export function fileTypeIcon(row: FileItem): string {
  if (row.type === 'folder') return 'folder'
  const mime = (row.mimeType || '').toLowerCase()
  if (mime.startsWith('image/')) return 'photo'
  if (mime.startsWith('video/')) return 'play-circle'
  if (mime.includes('pdf')) return 'file-text'
  return 'file-text'
}

export function fileExtLabel(row: FileItem): string {
  if (row.type === 'folder') return ''
  const name = row.name || ''
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.substring(dot + 1).toUpperCase().slice(0, 4) : 'FILE'
}
