import { fetchMediaBlob } from '@/utils/fetchMediaBlob'

const MEMORY = new Map<number, string>()
const STORAGE_KEY = 'cd_cover_thumbs'
const MAX_ENTRIES = 150

interface CoverEntry {
  id: number
  v: number
  data: string
}

function readEntries(): CoverEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as CoverEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeEntries(entries: CoverEntry[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_ENTRIES)))
}

function blobToThumbDataUrl(blob: Blob, size = 200): Promise<string> {
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
    localStorage.removeItem(STORAGE_KEY)
    return
  }
  MEMORY.delete(fileId)
  writeEntries(readEntries().filter((e) => e.id !== fileId))
}

export async function cacheCoverFromUrl(fileId: number, version: number, url: string): Promise<string> {
  const blob = await fetchMediaBlob(url)
  const data = await blobToThumbDataUrl(blob)
  return saveCoverThumb(fileId, version, data)
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
