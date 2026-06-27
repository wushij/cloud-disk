import { buildUrl } from '@/api/http'
import { getSessionBearer } from '@/api/sessionAuth'

const MB = 1024 * 1024
const CHUNK_SIZE = 4 * MB
const isH5 = import.meta.env.UNI_PLATFORM === 'h5'

function authHeaders(rangeStart: number, rangeEnd: number): Record<string, string> {
  const headers: Record<string, string> = {
    Range: `bytes=${rangeStart}-${rangeEnd}`
  }
  const token = getSessionBearer()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function fetchRangeH5(
  url: string,
  rangeStart: number,
  rangeEnd: number,
  signal?: AbortSignal
): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('GET', buildUrl(url))
    const headers = authHeaders(rangeStart, rangeEnd)
    Object.entries(headers).forEach(([key, value]) => xhr.setRequestHeader(key, value))
    xhr.responseType = 'arraybuffer'

    const onAbort = () => xhr.abort()
    if (signal) signal.addEventListener('abort', onAbort)

    xhr.onload = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      if (xhr.status === 200 || xhr.status === 206) {
        resolve(xhr.response as ArrayBuffer)
        return
      }
      reject(new Error(`下载失败 (${xhr.status})`))
    }
    xhr.onerror = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      reject(new Error('下载网络错误'))
    }
    xhr.onabort = () => {
      if (signal) signal.removeEventListener('abort', onAbort)
      reject(new Error('Canceled'))
    }
    xhr.send()
  })
}

function fetchRangeNative(
  url: string,
  rangeStart: number,
  rangeEnd: number,
  signal?: AbortSignal
): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new Error('Canceled'))
      return
    }
    const headers = authHeaders(rangeStart, rangeEnd)
    const requestTask = uni.request({
      url: buildUrl(url),
      method: 'GET',
      header: headers,
      responseType: 'arraybuffer',
      success: (res) => {
        if (res.statusCode === 200 || res.statusCode === 206) {
          const data = res.data
          if (data instanceof ArrayBuffer) {
            resolve(data)
            return
          }
          reject(new Error('无法读取下载数据'))
          return
        }
        reject(new Error(`下载失败 (${res.statusCode})`))
      },
      fail: (err) => {
        if (signal?.aborted) {
          reject(new Error('Canceled'))
          return
        }
        reject(err || new Error('下载网络错误'))
      }
    })
    if (signal) {
      signal.addEventListener('abort', () => requestTask.abort?.(), { once: true })
    }
  })
}

function fetchRange(
  url: string,
  rangeStart: number,
  rangeEnd: number,
  signal?: AbortSignal
): Promise<ArrayBuffer> {
  return isH5
    ? fetchRangeH5(url, rangeStart, rangeEnd, signal)
    : fetchRangeNative(url, rangeStart, rangeEnd, signal)
}

export function mergeDownloadParts(parts: ArrayBuffer[]): ArrayBuffer {
  const totalLength = parts.reduce((sum, part) => sum + part.byteLength, 0)
  const merged = new Uint8Array(totalLength)
  let offset = 0
  for (const part of parts) {
    merged.set(new Uint8Array(part), offset)
    offset += part.byteLength
  }
  return merged.buffer
}

export function triggerH5FileSave(buffer: ArrayBuffer, fileName: string, mimeType?: string) {
  const blob = new Blob([buffer], { type: mimeType || 'application/octet-stream' })
  const blobUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = fileName
  a.click()
  URL.revokeObjectURL(blobUrl)
}

export function saveDownloadNative(buffer: ArrayBuffer, fileName: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const fs = uni.getFileSystemManager()
    const base = (uni as any).env?.USER_DATA_PATH as string | undefined
    const tempPath = base ? `${base}/download_${Date.now()}_${fileName}` : `_doc/download_${Date.now()}_${fileName}`
    fs.writeFile({
      filePath: tempPath,
      data: buffer,
      success: () => {
        uni.saveFile({
          tempFilePath: tempPath,
          success: () => resolve(),
          fail: (err) => reject(err || new Error('保存失败'))
        })
      },
      fail: (err) => reject(err || new Error('写入临时文件失败'))
    })
  })
}

export async function downloadFileResumable(options: {
  url: string
  totalSize: number
  existingParts?: ArrayBuffer[]
  signal?: AbortSignal
  onProgress?: (loaded: number, total: number, parts: ArrayBuffer[]) => void
}): Promise<ArrayBuffer[]> {
  const parts = [...(options.existingParts || [])]
  let loaded = parts.reduce((sum, part) => sum + part.byteLength, 0)
  const total = options.totalSize > 0 ? options.totalSize : loaded

  while (total <= 0 || loaded < total) {
    if (options.signal?.aborted) throw new Error('Canceled')
    const rangeEnd = total > 0 ? Math.min(loaded + CHUNK_SIZE - 1, total - 1) : loaded + CHUNK_SIZE - 1
    const chunk = await fetchRange(options.url, loaded, rangeEnd, options.signal)
    if (!chunk.byteLength) break
    parts.push(chunk)
    loaded += chunk.byteLength
    options.onProgress?.(loaded, total > 0 ? total : loaded, parts)
    if (total > 0 && loaded >= total) break
    if (chunk.byteLength < CHUNK_SIZE) break
  }

  return parts
}
