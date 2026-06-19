/**
 * 文件元数据工具函数（统一管理 fmtSize / fileIconColor / transcodeLabel）
 * 解决原有在 md5.ts、Disk.vue、FileGridView.vue、TeamSpace.vue、Admin.vue 中的重复定义
 */
import type { FileItem } from '@/stores/file'

/** 文件大小格式化 */
export function fmtSize(bytes: number): string {
  if (bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 根据 MIME 类型返回对应的 CSS 变量颜色 */
export function fileIconColor(row: FileItem): string {
  if (row.type === 'folder') return 'var(--cd-file-folder)'
  const mime = (row.mimeType || '').toLowerCase()
  if (mime.startsWith('image/')) return 'var(--cd-file-image)'
  if (mime.startsWith('video/')) return 'var(--cd-file-video)'
  if (mime.includes('pdf')) return 'var(--cd-file-pdf)'
  if (mime.includes('word') || mime.includes('document')) return 'var(--cd-file-doc)'
  if (mime.includes('sheet') || mime.includes('excel')) return 'var(--cd-file-excel)'
  if (mime.includes('presentation') || mime.includes('powerpoint')) return 'var(--cd-file-ppt)'
  if (mime.includes('zip') || mime.includes('rar') || mime.includes('7z') || mime.includes('tar')) return 'var(--cd-file-archive)'
  return 'var(--cd-file-default)'
}

/** 转码状态中文标签 */
export function transcodeLabel(status?: string): string {
  switch (status) {
    case 'PENDING':
    case 'PROCESSING':
      return '转码中'
    case 'DONE':
      return '已转码'
    case 'FAILED':
      return '转码失败'
    default:
      return ''
  }
}

/**
 * ISO 时间格式化（中文 locale）
 */
export function fmtTime(iso?: string): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}
