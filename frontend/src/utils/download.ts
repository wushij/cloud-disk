import { ElMessage } from 'element-plus'
import { getApiErrorMessage } from '@/utils/error'
import { TOKEN_KEY } from '@/api/http'

/**
 * 通过 fetch + blob 下载 ZIP 文件。
 * 如果后端返回非 2xx（JSON 错误体），则解析错误信息并弹出 Toast，
 * 避免浏览器直接展示原始 JSON 文本。
 */
export async function downloadZip(url: string) {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  try {
    const resp = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` }
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

    // 从 Content-Disposition 中解析文件名
    const disposition = resp.headers.get('Content-Disposition') || ''
    const fileName = parseFileName(disposition) || '打包下载.zip'

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

function parseFileName(disposition: string): string | null {
  // filename*=UTF-8''xxx
  const utf8Match = disposition.match(/filename\*=(?:UTF-8|utf-8)''(.+?)(?:;|$)/i)
  if (utf8Match) {
    try { return decodeURIComponent(utf8Match[1].trim()) } catch { /* fallback */ }
  }
  // filename="xxx"
  const match = disposition.match(/filename="?([^";]+)"?/)
  return match ? decodeURIComponent(match[1].trim()) : null
}

function statusFallback(status: number): string {
  if (status === 401) return '未登录或登录已过期，请重新登录'
  if (status === 429) return '请求过于频繁，请稍后再试'
  if (status >= 500) return '服务器错误，请稍后重试'
  return '下载失败'
}
