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
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(String(raw)) as EntityAvatarEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeEntries(entries: EntityAvatarEntry[]) {
  uni.setStorageSync(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_ENTRIES)))
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
    uni.removeStorageSync(STORAGE_KEY)
    return
  }
  for (const k of [...MEMORY.keys()]) {
    if (k.startsWith(`${key}@`)) MEMORY.delete(k)
  }
  writeEntries(readEntries().filter((e) => e.key !== key && !e.key.startsWith(`${key}:`)))
}

function saveEntityAvatarThumb(key: string, version: number | string, data: string) {
  const v = versionKey(version)
  MEMORY.set(`${key}@${v}`, data)
  const entries = readEntries().filter((e) => !(e.key === key && e.v === v))
  entries.unshift({ key, v, data })
  writeEntries(entries)
}

export function cacheEntityAvatarFromUrl(
  key: string,
  version: number | string,
  url: string
): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`avatar download ${res.statusCode}`))
          return
        }
        uni.getFileSystemManager().readFile({
          filePath: res.tempFilePath,
          encoding: 'base64',
          success: (fileRes) => {
            const data = `data:image/jpeg;base64,${fileRes.data}`
            saveEntityAvatarThumb(key, version, data)
            resolve(data)
          },
          fail: reject
        })
      },
      fail: reject
    })
  })
}

export async function cacheEntityAvatarFromPath(
  key: string,
  version: number | string,
  filePath: string
): Promise<string> {
  const res = await fetch(filePath)
  const blob = await res.blob()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const data = String(reader.result || '')
      if (!data) {
        reject(new Error('empty avatar'))
        return
      }
      saveEntityAvatarThumb(key, version, data)
      resolve(data)
    }
    reader.onerror = () => reject(new Error('read failed'))
    reader.readAsDataURL(blob)
  })
}
