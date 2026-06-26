/** PC 媒体资源 URL：同域 Cookie + 稳定登录 token 查询参数（供 img/video 鉴权） */
import { currentMediaToken } from '@/utils/mediaToken'

export function appendQueryParam(url: string, key: string, value: string | number): string {
  if (!url) return ''
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`
}

export function mediaApiUrl(path: string): string {
  if (!path?.trim()) return ''
  const p = path.startsWith('/') ? path : `/${path}`
  const base = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  const url = base ? `${base}${p}` : p
  const token = currentMediaToken()
  if (!token) return url
  return appendQueryParam(url, 'access_token', token)
}

/** 管理员查看用户头像 URL（需 media token 就绪） */
export function adminUserAvatarUrl(userId: number, version?: number): string {
  if (!userId || !currentMediaToken()) return ''
  const v = version ?? userId
  return appendQueryParam(mediaApiUrl(`/api/admin/users/${userId}/avatar`), 'v', v)
}
