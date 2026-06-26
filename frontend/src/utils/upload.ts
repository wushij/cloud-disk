import axios from 'axios'
import http from '@/api/http'
import { pickChunkSize } from '@/utils/md5'

const MB = 1024 * 1024
const SIMPLE_MAX = 8 * MB
const CONCURRENCY = 4
const RETRIES = 3

export interface UploadSessionInfo {
  uploadId: string
  chunkSize: number
  totalChunks: number
}

export interface UploadResumeOptions {
  /** 已有上传会话 ID，继续传剩余分片 */
  existingUploadId?: string
  /** 跳过秒传 MD5 检查（暂停恢复时） */
  skipMd5Check?: boolean
  /** 分片会话创建后回调，用于保存 uploadId 供断点续传 */
  onInit?: (session: UploadSessionInfo) => void
}

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
  onProgress: (ratio: number) => void,
  signal?: AbortSignal,
  options?: UploadResumeOptions
): Promise<{ instant?: boolean; fileId?: number; uploadId?: string }> {
  const isResume = !!options?.existingUploadId

  if (!isResume) {
    onProgress(0)
  }

  if (fileMd5 && !options?.skipMd5Check) {
    const { data: check } = await http.post('/api/upload/check-md5', {
      fileMd5,
      fileName: file.name,
      fileSize: file.size,
      folderId
    }, { signal })
    if (check.exists && check.instant) {
      onProgress(1)
      return { instant: true, fileId: check.fileId }
    }
  }

  if (file.size <= SIMPLE_MAX) {
    const { data } = await withRetry(() =>
      http.post<{ id?: number }>('/api/files/simple', (() => {
        const fd = new FormData()
        fd.append('file', file)
        fd.append('folderId', String(folderId))
        return fd
      })(), {
        signal,
        onUploadProgress: (e) => {
          if (e.total) onProgress(Math.min(0.99, e.loaded / e.total))
        }
      })
    )
    onProgress(1)
    return { fileId: data?.id }
  }

  let uploadId: string
  let serverChunk: number
  let totalChunks: number
  let uploaded: Set<number>

  if (isResume && options?.existingUploadId) {
    const { data: resume } = await http.get<{
      uploadId: string
      chunkSize: number
      totalChunks: number
      uploadedChunks: number[]
    }>(`/api/upload/${options.existingUploadId}/resume`, { signal })
    uploadId = resume.uploadId
    serverChunk = resume.chunkSize
    totalChunks = resume.totalChunks
    uploaded = new Set<number>(resume.uploadedChunks || [])
    if (totalChunks > 0) {
      onProgress(uploaded.size / totalChunks)
    }
  } else {
    const chunkSize = pickChunkSize(file.size)
    const { data: init } = await http.post('/api/upload/init', {
      fileName: file.name,
      totalSize: file.size,
      chunkSize,
      fileMd5: fileMd5 || undefined,
      folderId
    }, { signal })

    uploaded = new Set<number>((init.uploadedChunks as number[]) || [])
    totalChunks = init.totalChunks as number
    serverChunk = init.chunkSize as number
    uploadId = init.uploadId as string
    options?.onInit?.({ uploadId, chunkSize: serverChunk, totalChunks })
  }

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
      await withRetry(() => http.post('/api/upload/chunk', fd, { signal }))
    },
    (r) => onProgress(0.05 + r * 0.9)
  )

  const { data: record } = await withRetry(() =>
    http.post<{ id?: number }>('/api/upload/merge', { uploadId, mimeType: file.type || undefined }, { signal })
  )
  onProgress(1)
  return { uploadId, fileId: record?.id }
}
