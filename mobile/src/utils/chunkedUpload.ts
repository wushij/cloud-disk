import { request, uploadFile, buildUrl, resolveBearer } from '@/api/http'
import { pickChunkSize } from '@/utils/md5'

const MB = 1024 * 1024
const SIMPLE_MAX = 8 * MB
const CONCURRENCY = 4
const RETRIES = 3

interface UploadResult {
  instant?: boolean
  fileId?: number
  uploadId?: string
}

export interface ChunkedUploadOptions {
  existingUploadId?: string
  skipMd5Check?: boolean
  h5File?: File
  mimeType?: string
  onInit?: (session: { uploadId: string; chunkSize: number; totalChunks: number }) => void
}

function parseXhrErrorMessage(xhr: XMLHttpRequest, fallback: string): string {
  try {
    const parsed = JSON.parse(xhr.responseText) as { error?: string; message?: string }
    const msg = parsed.error || parsed.message
    if (msg) return msg
  } catch {
    /* ignore */
  }
  if (xhr.status === 401) return '未登录或登录已过期，请重新登录'
  if (xhr.status === 413) return '上传文件过大'
  return fallback
}

function retryableStatus(status: number): boolean {
  return status === 502 || status === 503 || status === 504 || status === 0
}

function extractErrorStatus(e: unknown): number {
  if (!(e instanceof Error)) return 0
  const matched = e.message.match(/\((\d+)\)/)
  return matched ? Number(matched[1]) : 0
}

async function withRetry<T>(fn: () => Promise<T>, times = RETRIES): Promise<T> {
  let last: unknown
  for (let i = 0; i < times; i++) {
    try {
      return await fn()
    } catch (e) {
      last = e
      const status = extractErrorStatus(e)
      const retryable = status === 0 || retryableStatus(status)
      if (!retryable || i === times - 1) throw e
      await new Promise((r) => setTimeout(r, 800 * (i + 1)))
    }
  }
  throw last
}

function uploadChunkXHR(
  uploadId: string,
  index: number,
  slice: Blob,
  signal?: AbortSignal
): Promise<void> {
  return new Promise((resolve, reject) => {
    const fd = new FormData()
    fd.append('uploadId', uploadId)
    fd.append('chunkIndex', String(index))
    fd.append('file', slice, `part-${index}`)
    const token = resolveBearer()
    const xhr = new XMLHttpRequest()
    xhr.open('POST', buildUrl('/api/upload/chunk'))
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    const onAbort = () => {
      xhr.abort()
      reject(new Error('Canceled'))
    }
    if (signal) signal.addEventListener('abort', onAbort)

    xhr.onload = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(parseXhrErrorMessage(xhr, `分片 ${index} 上传失败 (${xhr.status})`)))
    }
    xhr.onerror = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      reject(new Error(`分片 ${index} 网络错误`))
    }
    xhr.send(fd)
  })
}

/** merge 走 XHR 且不设 timeout，与 PC 端 axios timeout:0 一致 */
function mergeUploadXHR(
  uploadId: string,
  mimeType?: string,
  signal?: AbortSignal
): Promise<{ id?: number }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', buildUrl('/api/upload/merge'))
    xhr.setRequestHeader('Content-Type', 'application/json')
    const token = resolveBearer()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    const onAbort = () => {
      xhr.abort()
      reject(new Error('Canceled'))
    }
    if (signal) signal.addEventListener('abort', onAbort)

    xhr.onload = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as { id?: number })
        } catch {
          reject(new Error('合并响应解析失败'))
        }
        return
      }
      reject(new Error(parseXhrErrorMessage(xhr, `合并失败 (${xhr.status})`)))
    }
    xhr.onerror = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      reject(new Error('合并网络错误，大文件合并时间较长请稍候重试'))
    }
    xhr.send(JSON.stringify({ uploadId, mimeType: mimeType || undefined }))
  })
}

// #ifdef H5
async function loadH5Blob(filePath: string, h5File?: File): Promise<Blob> {
  if (h5File instanceof Blob) return h5File
  const res = await fetch(filePath)
  if (!res.ok) throw new Error('读取本地文件失败')
  return res.blob()
}

async function resolveUploadSize(
  filePath: string,
  declaredSize: number,
  options?: ChunkedUploadOptions
): Promise<number> {
  if (options?.h5File instanceof Blob && options.h5File.size > 0) {
    return options.h5File.size
  }
  if (declaredSize > SIMPLE_MAX) return declaredSize
  try {
    const blob = await loadH5Blob(filePath, options?.h5File)
    if (blob.size > 0) return blob.size
  } catch {
    /* ignore */
  }
  return declaredSize
}

async function getH5UploadSource(
  filePath: string,
  options?: ChunkedUploadOptions
): Promise<Blob> {
  if (options?.h5File instanceof Blob) return options.h5File
  return loadH5Blob(filePath, options?.h5File)
}

function uploadSimpleFileH5(
  filePath: string,
  fileName: string,
  fileSize: number,
  folderId: number,
  onProgress?: (ratio: number) => void,
  signal?: AbortSignal,
  options?: ChunkedUploadOptions
): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    void (async () => {
      try {
        const source = await getH5UploadSource(filePath, options)
        const file =
          source instanceof File && source.name
            ? source
            : new File([source], fileName, {
                type: options?.mimeType || source.type || 'application/octet-stream'
              })
        const fd = new FormData()
        fd.append('file', file, file.name)
        fd.append('folderId', String(folderId))

        const xhr = new XMLHttpRequest()
        xhr.open('POST', buildUrl('/api/files/simple'))
        const token = resolveBearer()
        if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

        const onAbort = () => {
          xhr.abort()
          reject(new Error('Canceled'))
        }
        if (signal) signal.addEventListener('abort', onAbort)

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable && onProgress) {
            onProgress(e.loaded / (e.total || fileSize || e.loaded))
          }
        }
        xhr.onload = () => {
          if (signal) signal.removeEventListener('abort', onAbort)
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              const data = JSON.parse(xhr.responseText) as { id?: number }
              onProgress?.(1)
              resolve({ fileId: data?.id })
            } catch {
              reject(new Error('上传响应解析失败'))
            }
            return
          }
          reject(new Error(parseXhrErrorMessage(xhr, `上传失败 (${xhr.status})`)))
        }
        xhr.onerror = () => {
          if (signal) signal.removeEventListener('abort', onAbort)
          reject(new Error('上传网络错误'))
        }
        xhr.send(fd)
      } catch (err) {
        reject(err)
      }
    })()
  })
}
// #endif

export async function uploadChunkedFile(
  filePath: string,
  fileName: string,
  fileSize: number,
  folderId: number,
  fileMd5?: string,
  onProgress?: (ratio: number) => void,
  signal?: AbortSignal,
  options?: ChunkedUploadOptions
): Promise<UploadResult> {
  const isResume = !!options?.existingUploadId
  if (!isResume) onProgress?.(0)
  if (signal?.aborted) throw new Error('Canceled')

  if (fileMd5 && !options?.skipMd5Check) {
    try {
      const check = await request<{ exists: boolean; instant: boolean; fileId: number }>({
        url: '/api/upload/check-md5',
        method: 'POST',
        data: { fileMd5, fileName, fileSize, folderId }
      })
      if (check.exists && check.instant) {
        onProgress?.(1)
        return { instant: true, fileId: check.fileId }
      }
    } catch {
      /* 秒传失败则继续普通上传 */
    }
  }

  if (signal?.aborted) throw new Error('Canceled')

  let uploadId: string
  let serverChunkSize: number
  let totalChunks: number
  let uploadedSet: Set<number>

  if (isResume && options?.existingUploadId) {
    const resume = await request<{
      uploadId: string
      chunkSize: number
      totalChunks: number
      uploadedChunks: number[]
    }>({
      url: `/api/upload/${options.existingUploadId}/resume`,
      method: 'GET'
    })
    uploadId = resume.uploadId
    serverChunkSize = resume.chunkSize
    totalChunks = resume.totalChunks
    uploadedSet = new Set(resume.uploadedChunks || [])
    if (totalChunks > 0) {
      onProgress?.(0.05 + (uploadedSet.size / totalChunks) * 0.9)
    }
  } else {
    const chunkSize = pickChunkSize(fileSize)
    const init = await request<{
      uploadId: string
      chunkSize: number
      totalChunks: number
      uploadedChunks: number[]
    }>({
      url: '/api/upload/init',
      method: 'POST',
      data: {
        fileName,
        totalSize: fileSize,
        chunkSize,
        fileMd5: fileMd5 || undefined,
        folderId
      }
    })
    uploadId = init.uploadId
    serverChunkSize = init.chunkSize
    totalChunks = init.totalChunks
    uploadedSet = new Set(init.uploadedChunks || [])
    options?.onInit?.({ uploadId, chunkSize: serverChunkSize, totalChunks })
  }

  let done = uploadedSet.size

  // #ifdef H5
  const h5Source = await getH5UploadSource(filePath, options)
  // #endif

  const uploadChunk = async (index: number): Promise<void> => {
    if (uploadedSet.has(index)) return
    if (signal?.aborted) throw new Error('Canceled')

    const start = index * serverChunkSize
    const end = Math.min(start + serverChunkSize, fileSize)

    // #ifdef H5
    const slice = h5Source.slice(start, end)
    await withRetry(() => uploadChunkXHR(uploadId, index, slice, signal))
    // #endif

    // #ifndef H5
    const fs = uni.getFileSystemManager()
    const tempPath = `${(uni as any).env?.USER_DATA_PATH || uni.getStorageSync('temp_path') || '/tmp'}/chunk_${uploadId}_${index}`
    let activeTask: UniApp.UploadTask | null = null
    const onAbort = () => activeTask?.abort()
    if (signal) signal.addEventListener('abort', onAbort)
    try {
      const buffer = fs.readFileSync(filePath, 'binary', start, end - start) as unknown as ArrayBuffer
      fs.writeFileSync(tempPath, buffer, 'binary')
      await uploadFile({
        url: '/api/upload/chunk',
        filePath: tempPath,
        name: 'file',
        formData: { uploadId, chunkIndex: String(index) },
        onTaskCreated: (t) => {
          activeTask = t
          if (signal?.aborted) t.abort()
        }
      })
      try {
        fs.unlinkSync(tempPath)
      } catch {
        /* ignore */
      }
    } catch (e) {
      try {
        fs.unlinkSync(tempPath)
      } catch {
        /* ignore */
      }
      throw e
    } finally {
      if (signal) signal.removeEventListener('abort', onAbort)
    }
    // #endif
  }

  let next = 0
  const worker = async () => {
    for (;;) {
      if (signal?.aborted) throw new Error('Canceled')
      const i = next++
      if (i >= totalChunks) return
      await uploadChunk(i)
      if (signal?.aborted) throw new Error('Canceled')
      done++
      onProgress?.(0.05 + (done / totalChunks) * 0.9)
    }
  }

  const workers = Math.min(CONCURRENCY, Math.max(1, totalChunks - uploadedSet.size))
  await Promise.all(Array.from({ length: workers }, () => worker()))

  if (signal?.aborted) throw new Error('Canceled')

  const record = await withRetry(() =>
    mergeUploadXHR(uploadId, options?.mimeType, signal)
  )

  onProgress?.(1)
  return { uploadId, fileId: record?.id }
}

export async function smartUpload(
  filePath: string,
  fileName: string,
  fileSize: number,
  folderId: number,
  fileMd5?: string,
  onProgress?: (ratio: number) => void,
  signal?: AbortSignal,
  options?: ChunkedUploadOptions
): Promise<UploadResult> {
  let actualSize = fileSize
  // #ifdef H5
  actualSize = await resolveUploadSize(filePath, fileSize, options)
  // #endif

  if (!actualSize || actualSize <= 0) {
    throw new Error('文件大小无效，请重新选择')
  }

  if (actualSize <= SIMPLE_MAX) {
    // #ifdef H5
    return uploadSimpleFileH5(filePath, fileName, actualSize, folderId, onProgress, signal, options)
    // #endif
    // #ifndef H5
    let activeTask: UniApp.UploadTask | null = null
    const onAbort = () => activeTask?.abort()
    signal?.addEventListener('abort', onAbort)
    try {
      const data = (await uploadFile({
        url: '/api/files/simple',
        filePath,
        name: 'file',
        formData: { folderId: String(folderId) },
        onProgress,
        onTaskCreated: (t) => {
          activeTask = t
          if (signal?.aborted) t.abort()
        }
      })) as { id?: number }
      return { fileId: data?.id }
    } finally {
      signal?.removeEventListener('abort', onAbort)
    }
    // #endif
  }

  return uploadChunkedFile(
    filePath,
    fileName,
    actualSize,
    folderId,
    fileMd5,
    onProgress,
    signal,
    options
  )
}
