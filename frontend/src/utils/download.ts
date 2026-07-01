import { ElMessage } from 'element-plus'
import { getSessionBearer } from '@/api/sessionAuth'
import { getApiErrorMessage } from '@/utils/error'

const MB = 1024 * 1024

/** 构建打包下载查询串（Spring 需重复参数：fileIds=1&fileIds=2） */
export function buildZipDownloadUrl(folderIds: number[], fileIds: number[]): string {
  const params = new URLSearchParams()
  folderIds.forEach((id) => params.append('folderIds', String(id)))
  fileIds.forEach((id) => params.append('fileIds', String(id)))
  const qs = params.toString()
  return qs ? `/api/files/download/zip?${qs}` : '/api/files/download/zip'
}

export function buildZipDisplayName(items: Array<{ name: string }>): string {
  if (items.length === 1) return `${items[0].name}.zip`
  return `打包下载 (${items.length}项).zip`
}

export function resolveDownloadUrl(url: string): string {
  const path = url.startsWith('/') ? url : `/${url}`
  const base = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  return base ? `${base}${path}` : path
}

/** 流式下载无 Content-Length 时的估算进度（完成前最高 95%） */
export function calcStreamProgress(loaded: number): number {
  if (loaded <= 0) return 0
  return Math.min(0.95, loaded / (loaded + 16 * MB))
}

export function parseDownloadFileName(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback
  const utf8Match = disposition.match(/filename\*=(?:UTF-8|utf-8)''(.+?)(?:;|$)/i)
  if (utf8Match) {
    try { return decodeURIComponent(utf8Match[1].trim()) } catch { /* fallback */ }
  }
  const match = disposition.match(/filename="?([^";]+)"?/)
  return match ? decodeURIComponent(match[1].trim()) : fallback
}

function statusFallback(status: number): string {
  if (status === 401) return '未登录或登录已过期，请重新登录'
  if (status === 429) return '请求过于频繁，请稍后再试'
  if (status >= 500) return '服务器错误，请稍后重试'
  return '下载失败'
}

/** @deprecated 请使用 transferStore.addZipDownloadTask */
export async function downloadZip(url: string) {
  try {
    const headers: Record<string, string> = {}
    const bearer = getSessionBearer()
    if (bearer) headers.Authorization = `Bearer ${bearer}`

    const resp = await fetch(resolveDownloadUrl(url), {
      credentials: 'include',
      headers
    })

    if (!resp.ok) {
      let msg = ''
      try {
        const body = await resp.json()
        msg = body?.error || body?.message || ''
      } catch {
        /* 非 JSON 响应体 */
      }
      ElMessage.error(msg || statusFallback(resp.status))
      return
    }

    const disposition = resp.headers.get('Content-Disposition') || ''
    const fileName = parseDownloadFileName(disposition, '打包下载.zip')

    const blob = await resp.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
  } catch (err) {
    ElMessage.error(getApiErrorMessage(err, '下载失败，请检查网络后重试') as string)
  }
}