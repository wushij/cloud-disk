import { fetchMediaBlob } from '@/utils/fetchMediaBlob'

const MEMORY = new Map<string, string>()
const STORAGE_KEY = 'cd_entity_avatar_thumbs'
const MAX_ENTRIES = 80

interface EntityAvatarEntry {
  key: string
  v: string
  data: string
}

function readEntries(): EntityAvatarEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as EntityAvatarEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeEntries(entries: EntityAvatarEntry[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_ENTRIES)))
}

function versionKey(version: number | string): string {
  return String(version)
}

export function teamAvatarCacheKey(teamId: number): string {
  return `team:${teamId}`
}

export function memberAvatarCacheKey(teamId: number, userId: number): string {
  return `member:${teamId}:${userId}`
}

export function memberAvatarVersion(member: { avatar?: string | null; userId?: number }): string {
  return member.avatar?.trim() || String(member.userId ?? 0)
}

export function loadEntityAvatarThumb(key: string, version: number | string): string {
  const v = versionKey(version)
  const mem = MEMORY.get(`${key}@${v}`)
  if (mem) return mem
  const hit = readEntries().find((e) => e.key === key && e.v === v)
  if (hit?.data) {
    MEMORY.set(`${key}@${v}`, hit.data)
    return hit.data
  }
  return ''
}

export function clearEntityAvatarThumb(key?: string) {
  if (!key) {
    MEMORY.clear()
    localStorage.removeItem(STORAGE_KEY)
    return
  }
  for (const k of [...MEMORY.keys()]) {
    if (k.startsWith(`${key}@`)) MEMORY.delete(k)
  }
  writeEntries(readEntries().filter((e) => e.key !== key && !e.key.startsWith(`${key}:`)))
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

function saveEntityAvatarThumb(key: string, version: number | string, data: string) {
  const v = versionKey(version)
  MEMORY.set(`${key}@${v}`, data)
  const entries = readEntries().filter((e) => !(e.key === key && e.v === v))
  entries.unshift({ key, v, data })
  writeEntries(entries)
}

export async function cacheEntityAvatarFromUrl(
  key: string,
  version: number | string,
  url: string
): Promise<string> {
  const blob = await fetchMediaBlob(url)
  const data = await blobToThumbDataUrl(blob)
  saveEntityAvatarThumb(key, version, data)
  return data
}

export async function cacheEntityAvatarFromFile(
  key: string,
  version: number | string,
  file: File
): Promise<string> {
  const data = await blobToThumbDataUrl(file)
  saveEntityAvatarThumb(key, version, data)
  return data
}
