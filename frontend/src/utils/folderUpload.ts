import http from '@/api/http'

const folderCache = new Map<string, number>()

function cacheKey(parentId: number, name: string) {
  return `${parentId}/${name}`
}

/** 根据 webkitRelativePath 确保目录存在，返回文件应落入的 folderId */
export async function ensureFolderPath(baseFolderId: number, relativePath: string): Promise<number> {
  if (!relativePath.includes('/')) return baseFolderId
  const parts = relativePath.split('/')
  parts.pop()
  let parentId = baseFolderId
  let pathKey = String(baseFolderId)
  for (const name of parts) {
    pathKey += '/' + name
    if (folderCache.has(pathKey)) {
      parentId = folderCache.get(pathKey)!
      continue
    }
    const { data } = await http.post('/api/folders', { parentId, folderName: name })
    parentId = data.id as number
    folderCache.set(pathKey, parentId)
  }
  return parentId
}

export function clearFolderCache() {
  folderCache.clear()
}

export function fileDisplayName(file: File): string {
  const rp = (file as File & { webkitRelativePath?: string }).webkitRelativePath
  if (rp && rp.includes('/')) return rp.slice(rp.lastIndexOf('/') + 1)
  return file.name
}

export function fileRelativePath(file: File): string | null {
  const rp = (file as File & { webkitRelativePath?: string }).webkitRelativePath
  return rp && rp.includes('/') ? rp : null
}
