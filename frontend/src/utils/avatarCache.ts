import { fetchMediaBlob } from '@/utils/fetchMediaBlob'

const CACHE_KEY = 'cd_avatar_thumb'

interface AvatarThumbCache {
  user: string
  v: number
  data: string
}

/** 由服务端头像存储路径生成稳定版本号，避免登录后仍命中 v=0 的旧缓存 */
export function avatarVersionFromPath(path?: string | null): number {
  if (!path?.trim()) return 0
  let h = 0
  for (let i = 0; i < path.length; i++) {
    h = (Math.imul(31, h) + path.charCodeAt(i)) | 0
  }
  return h >>> 0
}

function readCache(): AvatarThumbCache | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AvatarThumbCache
    if (!parsed?.user || !parsed?.data) return null
    return parsed
  } catch {
    return null
  }
}

export function loadAvatarThumb(username: string, version: number): string {
  const cached = readCache()
  if (!cached || cached.user !== username || cached.v !== version) return ''
  return cached.data
}

export function clearAvatarThumb() {
  localStorage.removeItem(CACHE_KEY)
}

function saveAvatarThumb(username: string, version: number, data: string) {
  const payload: AvatarThumbCache = { user: username, v: version, data }
  localStorage.setItem(CACHE_KEY, JSON.stringify(payload))
}

function blobToThumbDataUrl(blob: Blob, size = 96): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(blob)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('canvas unavailable'))
        return
      }
      const scale = Math.max(size / img.width, size / img.height)
      const w = img.width * scale
      const h = img.height * scale
      ctx.drawImage(img, (size - w) / 2, (size - h) / 2, w, h)
      resolve(canvas.toDataURL('image/jpeg', 0.82))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('image decode failed'))
    }
    img.src = url
  })
}

export async function cacheAvatarFromBlob(username: string, version: number, blob: Blob) {
  const data = await blobToThumbDataUrl(blob)
  saveAvatarThumb(username, version, data)
  return data
}

export async function cacheAvatarFromFile(username: string, version: number, file: File) {
  return cacheAvatarFromBlob(username, version, file)
}

export async function cacheAvatarFromUrl(username: string, version: number, url: string) {
  const blob = await fetchMediaBlob(url)
  return cacheAvatarFromBlob(username, version, blob)
}
