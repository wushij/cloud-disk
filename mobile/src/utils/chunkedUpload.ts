import { request, uploadFile, TOKEN_KEY } from '@/api/http'

const MB = 1024 * 1024
const SIMPLE_MAX = 8 * MB    // 小于 8MB 使用简单上传
const CHUNK_SIZE = 4 * MB     // 移动端分片大小 4MB（比 PC 小，适配手机内存）
const CONCURRENCY = 2         // 移动端并发数（避免占用过多网络资源）

interface UploadResult {
  instant?: boolean
  fileId?: number
  uploadId?: string
}

export interface ChunkedUploadOptions {
  existingUploadId?: string
  skipMd5Check?: boolean
  onInit?: (session: { uploadId: string; chunkSize: number; totalChunks: number }) => void
}

interface FileInfo {
  filePath: string
  size: number
  name: string
}

/**
 * 获取文件信息
 */
function getFileInfo(filePath: string): Promise<FileInfo> {
  return new Promise((resolve, reject) => {
    // #ifdef H5
    // H5 环境下通过 fetch 获取文件大小
    const name = filePath.split('/').pop() || 'file'
    resolve({ filePath, size: 0, name })
    // #endif
    // #ifndef H5
    uni.getFileInfo({
      filePath,
      success: (res) => {
        const name = filePath.split('/').pop() || 'file'
        resolve({ filePath, size: res.size, name })
      },
      fail: reject
    })
    // #endif
  })
}

/**
 * 分片上传文件（支持秒传 + 断点续传）
 *
 * 流程：
 * 1. 检查 MD5 → 秒传
 * 2. 初始化分片上传 → 获取 uploadId + 已上传分片
 * 3. 并发上传剩余分片
 * 4. 合并分片
 */
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

  if (!isResume) {
    onProgress?.(0)
  }

  if (signal?.aborted) throw new Error('Canceled')

  // 步骤 1: 秒传检查
  if (fileMd5 && !options?.skipMd5Check) {
    try {
      const check = await request<{ exists: boolean; instant: boolean; fileId: number }>({
        url: '/api/upload/check-md5',
        method: 'POST',
        data: {
          fileMd5,
          fileName,
          fileSize,
          folderId
        }
      })
      if (check.exists && check.instant) {
        onProgress?.(1)
        return { instant: true, fileId: check.fileId }
      }
    } catch {
      // 秒传检查失败，继续普通上传
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
    // 步骤 2: 初始化分片上传
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
        chunkSize: CHUNK_SIZE,
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

  // 步骤 3: 并发上传分片
  let done = 0

  const uploadChunk = async (index: number): Promise<void> => {
    if (uploadedSet.has(index)) return
    if (signal?.aborted) throw new Error('Canceled')

    const start = index * serverChunkSize
    const end = Math.min(start + serverChunkSize, fileSize)

    // 在 H5 环境下使用 Blob.slice 上传
    // #ifdef H5
    const response = await fetch(filePath)
    const blob = await response.blob()
    const slice = blob.slice(start, end)
    const fd = new FormData()
    fd.append('uploadId', uploadId)
    fd.append('chunkIndex', String(index))
    fd.append('file', slice, `part-${index}`)
    const token = uni.getStorageSync(TOKEN_KEY)
    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', '/api/upload/chunk')
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      
      const onAbort = () => {
        xhr.abort()
        reject(new Error('Canceled'))
      }
      if (signal) signal.addEventListener('abort', onAbort)
      
      xhr.onload = () => {
        if (signal) signal.removeEventListener('abort', onAbort)
        if (xhr.status >= 200 && xhr.status < 300) resolve()
        else reject(new Error(`分片 ${index} 上传失败: ${xhr.status}`))
      }
      xhr.onerror = () => {
        if (signal) signal.removeEventListener('abort', onAbort)
        reject(new Error(`分片 ${index} 网络错误`))
      }
      xhr.send(fd)
    })
    // #endif

    // #ifndef H5
    // 非 H5 环境：读取文件分片并写入临时文件后上传
    const fs = uni.getFileSystemManager()
    const tempPath = `${(uni as any).env?.USER_DATA_PATH || uni.getStorageSync('temp_path') || '/tmp'}/chunk_${uploadId}_${index}`
    let activeTask: UniApp.UploadTask | null = null
    const onAbort = () => {
      activeTask?.abort()
    }
    if (signal) signal.addEventListener('abort', onAbort)

    try {
      const buffer = fs.readFileSync(filePath, 'binary', start, end - start) as unknown as ArrayBuffer
      fs.writeFileSync(tempPath, buffer, 'binary')
      await uploadFile({
        url: '/api/upload/chunk',
        filePath: tempPath,
        name: 'file',
        formData: {
          uploadId,
          chunkIndex: String(index)
        },
        onTaskCreated: (t) => {
          activeTask = t
          if (signal?.aborted) t.abort()
        }
      })
      // 清理临时文件
      try { fs.unlinkSync(tempPath) } catch { /* ignore */ }
    } catch (e) {
      try { fs.unlinkSync(tempPath) } catch { /* ignore */ }
      throw e
    } finally {
      if (signal) signal.removeEventListener('abort', onAbort)
    }
    // #endif
  }

  // 并发控制
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
  await Promise.all(
    Array.from({ length: Math.min(CONCURRENCY, totalChunks) }, () => worker())
  )

  if (signal?.aborted) throw new Error('Canceled')

  // 步骤 4: 合并分片
  await request({
    url: '/api/upload/merge',
    method: 'POST',
    data: { uploadId, mimeType: undefined }
  })

  onProgress?.(1)
  return { uploadId }
}

/**
 * 智能上传：根据文件大小自动选择简单上传或分片上传
 */
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
  if (fileSize <= SIMPLE_MAX) {
    // 小文件使用简单上传
    let activeTask: UniApp.UploadTask | null = null
    const onAbort = () => activeTask?.abort()
    if (signal) signal.addEventListener('abort', onAbort)
    try {
      await uploadFile({
        url: '/api/files/simple',
        filePath,
        name: 'file',
        formData: { folderId: String(folderId) },
        onProgress,
        onTaskCreated: (t) => {
          activeTask = t
          if (signal?.aborted) t.abort()
        }
      })
    } finally {
      if (signal) signal.removeEventListener('abort', onAbort)
    }
    return {}
  }
  // 大文件使用分片上传
  return uploadChunkedFile(filePath, fileName, fileSize, folderId, fileMd5, onProgress, signal, options)
}
