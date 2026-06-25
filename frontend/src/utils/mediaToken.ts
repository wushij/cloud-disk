import { ref } from 'vue'
import http from '@/api/http'

/** 供头像/预览等 URL 拼接；与 ensureMediaToken 同步更新 */
export const mediaTokenRef = ref('')

let cache: { token: string; expiresAt: number } | null = null
let inflight: Promise<string> | null = null

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
    inflight = http
      .get<{ mediaToken: string; expiresIn: number }>('/api/auth/media-token')
      .then(({ data }) => {
        applyToken(data.mediaToken, data.expiresIn)
        return data.mediaToken
      })
      .finally(() => {
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

export function mediaTokenParam(): string {
  return encodeURIComponent(mediaTokenRef.value || cache?.token || '')
}

export function currentMediaToken(): string {
  return mediaTokenRef.value || cache?.token || ''
}
