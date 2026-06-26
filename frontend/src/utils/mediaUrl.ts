/** PC 媒体资源 URL：同域 Cookie + 稳定登录 token 查询参数（供 img/video 鉴权） */
import { TOKEN_KEY } from '@/api/http'

export function mediaApiUrl(path: string): string {
  if (!path?.trim()) return ''
  const p = path.startsWith('/') ? path : `/${path}`
  const base = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  const url = base ? `${base}${p}` : p
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return url
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}access_token=${encodeURIComponent(token)}`
}
