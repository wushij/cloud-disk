import { getSessionBearer } from '@/api/sessionAuth'
import { ensureMediaToken } from '@/utils/mediaToken'

const MEMORY = new Map<number, string>()
const STORAGE_KEY = 'cd_cover_thumbs'
const MAX_ENTRIES = 100

interface CoverEntry {
  id: number
  v: number
  data: string
}

function readEntries(): CoverEntry[] {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(String(raw)) as CoverEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeEntries(entries: CoverEntry[]) {
  uni.setStorageSync(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_ENTRIES)))
}

function blobToThumbDataUrl(blob: Blob, size = 200): Promise<string> {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(blob)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(objectUrl)
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
      URL.revokeObjectURL(objectUrl)
      reject(new Error('image decode failed'))
    }
    img.src = objectUrl
  })
}

async function fetchCoverBlobH5(url: string): Promise<Blob> {
  await ensureMediaToken()
  const token = getSessionBearer()
  const res = await fetch(url, {
    credentials: 'include',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    throw new Error(`封面加载失败 (${res.status})`)
  }
  return res.blob()
}

function cacheCoverFromUrlH5(fileId: number, version: number, url: string): Promise<string> {
  return fetchCoverBlobH5(url)
    .then((blob) => blobToThumbDataUrl(blob))
    .then((data) => saveCoverThumb(fileId, version, data))
}

function cacheCoverFromUrlNative(fileId: number, version: number, url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`cover download ${res.statusCode}`))
          return
        }
        const fs = uni.getFileSystemManager?.()
        if (!fs) {
          reject(new Error('file system unavailable'))
          return
        }
        fs.readFile({
          filePath: res.tempFilePath,
          encoding: 'base64',
          success: (fileRes) => {
            const data = `data:image/jpeg;base64,${fileRes.data}`
            resolve(saveCoverThumb(fileId, version, data))
          },
          fail: reject
        })
      },
      fail: reject
    })
  })
}

export function coverCacheVersion(hasThumbnail?: boolean): number {
  return hasThumbnail ? 1 : 0
}

export function loadCoverThumb(fileId: number, version = 0): string {
  const mem = MEMORY.get(fileId)
  if (mem) return mem
  const hit = readEntries().find((e) => e.id === fileId && e.v === version)
  if (hit?.data) {
    MEMORY.set(fileId, hit.data)
    return hit.data
  }
  return ''
}

export function clearCoverThumb(fileId?: number) {
  if (fileId == null) {
    MEMORY.clear()
    uni.removeStorageSync(STORAGE_KEY)
    return
  }
  MEMORY.delete(fileId)
  writeEntries(readEntries().filter((e) => e.id !== fileId))
}

export function cacheCoverFromUrl(fileId: number, version: number, url: string): Promise<string> {
  if (url.startsWith('data:')) {
    return Promise.resolve(cacheCoverFromDataUrl(fileId, version, url))
  }
  if (typeof uni.getFileSystemManager === 'function') {
    return cacheCoverFromUrlNative(fileId, version, url)
  }
  return cacheCoverFromUrlH5(fileId, version, url)
}

export function cacheCoverFromDataUrl(fileId: number, version: number, dataUrl: string): string {
  return saveCoverThumb(fileId, version, dataUrl)
}

function saveCoverThumb(fileId: number, version: number, data: string): string {
  MEMORY.set(fileId, data)
  const entries = readEntries().filter((e) => e.id !== fileId)
  entries.unshift({ id: fileId, v: version, data })
  writeEntries(entries)
  return data
}
