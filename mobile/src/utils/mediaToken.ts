import { ref } from 'vue'
import { getSessionBearer } from '@/api/sessionAuth'

/** 供头像/预览等 URL 拼接；与 ensureMediaToken 同步更新 */
export const mediaTokenRef = ref('')

let cache: { token: string; expiresAt: number } | null = null
let inflight: Promise<string> | null = null

function apiOrigin(): string {
  const base = (import.meta.env.VITE_API_BASE || '').trim()
  if (/^https?:\/\//.test(base)) return base.replace(/\/$/, '')
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return ''
}

function buildAuthUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  const normalized = path.startsWith('/') ? path : `/${path}`
  const origin = apiOrigin()
  return origin ? `${origin}${normalized}` : normalized
}

function applyToken(token: string, expiresIn: number) {
  cache = {
    token,
    expiresAt: Date.now() + expiresIn * 1000
  }
  mediaTokenRef.value = token
}

export function clearMediaTokenCache() {
  cache = null
  mediaTokenRef.value = ''
}

export async function refreshMediaToken(): Promise<string> {
  if (!inflight) {
    inflight = new Promise<string>((resolve, reject) => {
      const token = getSessionBearer()
      uni.request({
        url: buildAuthUrl('/api/auth/media-token'),
        method: 'GET',
        header: token ? { Authorization: `Bearer ${token}` } : {},
        success: (res) => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            const data = res.data as { mediaToken: string; expiresIn: number }
            applyToken(data.mediaToken, data.expiresIn)
            resolve(data.mediaToken)
          } else {
            reject(new Error('获取媒体访问凭证失败'))
          }
        },
        fail: reject
      })
    }).finally(() => {
      inflight = null
    })
  }
  return inflight
}

export async function ensureMediaToken(): Promise<string> {
  if (cache && Date.now() < cache.expiresAt - 60_000) {
    mediaTokenRef.value = cache.token
    return cache.token
  }
  return refreshMediaToken()
}

export function mediaTokenQuery(): string {
  return encodeURIComponent(mediaTokenRef.value || cache?.token || '')
}
