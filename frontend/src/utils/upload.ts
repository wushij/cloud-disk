import axios from 'axios'
import http from '@/api/http'
import { pickChunkSize } from '@/utils/md5'

const MB = 1024 * 1024
const SIMPLE_MAX = 8 * MB
const CONCURRENCY = 4
const RETRIES = 3

function retryable(e: unknown): boolean {
  if (!axios.isAxiosError(e)) return false
  if (!e.response) return true
  return [502, 503, 504].includes(e.response.status)
}

async function withRetry<T>(fn: () => Promise<T>, times = RETRIES): Promise<T> {
  let last: unknown
  for (let i = 0; i < times; i++) {
    try {
      return await fn()
    } catch (e) {
      last = e
      if (!retryable(e) || i === times - 1) throw e
      await new Promise((r) => setTimeout(r, 800 * (i + 1)))
    }
  }
  throw last
}

async function uploadChunksParallel(
  total: number,
  uploadOne: (i: number) => Promise<void>,
  onProgress: (ratio: number) => void
) {
  let done = 0
  let next = 0
  const workers = Math.min(CONCURRENCY, total)
  const worker = async () => {
    for (;;) {
      const i = next++
      if (i >= total) return
      await uploadOne(i)
      done++
      onProgress(done / total)
    }
  }
  await Promise.all(Array.from({ length: workers }, () => worker()))
}

export async function uploadFile(
  file: File,
  folderId: number,
  fileMd5: string | null,
  onProgress: (ratio: number) => void
): Promise<{ instant?: boolean; fileId?: number; uploadId?: string }> {
  onProgress(0)

  if (fileMd5) {
    const { data: check } = await http.post('/api/upload/check-md5', {
      fileMd5,
      fileName: file.name,
      fileSize: file.size,
      folderId
    })
    if (check.exists && check.instant) {
      onProgress(1)
      return { instant: true, fileId: check.fileId }
    }
  }

  if (file.size <= SIMPLE_MAX) {
    await withRetry(() =>
      http.post('/api/files/simple', (() => {
        const fd = new FormData()
        fd.append('file', file)
        fd.append('folderId', String(folderId))
        return fd
      })(), {
        onUploadProgress: (e) => {
          if (e.total) onProgress(Math.min(0.99, e.loaded / e.total))
        }
      })
    )
    onProgress(1)
    return {}
  }

  const chunkSize = pickChunkSize(file.size)
  const { data: init } = await http.post('/api/upload/init', {
    fileName: file.name,
    totalSize: file.size,
    chunkSize,
    fileMd5: fileMd5 || undefined,
    folderId
  })

  const uploaded = new Set<number>((init.uploadedChunks as number[]) || [])
  const totalChunks = init.totalChunks as number
  const serverChunk = init.chunkSize as number
  const uploadId = init.uploadId as string

  await uploadChunksParallel(
    totalChunks,
    async (i) => {
      if (uploaded.has(i)) return
      const start = i * serverChunk
      const end = Math.min(start + serverChunk, file.size)
      const fd = new FormData()
      fd.append('uploadId', uploadId)
      fd.append('chunkIndex', String(i))
      fd.append('file', file.slice(start, end), `part-${i}`)
      await withRetry(() => http.post('/api/upload/chunk', fd))
    },
    (r) => onProgress(0.05 + r * 0.9)
  )

  await withRetry(() =>
    http.post('/api/upload/merge', { uploadId, mimeType: file.type || undefined })
  )
  onProgress(1)
  return { uploadId }
}
