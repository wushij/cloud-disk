import http from './http'
import type { FileVO } from './types'

export interface Md5CheckResult {
  exists: boolean
  instant?: boolean
  fileId?: number
}

export interface UploadInitResult {
  uploadId: string
  chunkSize: number
  totalChunks: number
  uploadedChunks: number[]
}

export interface UploadResumeResult {
  uploadId: string
  chunkSize: number
  totalChunks: number
  uploadedChunks: number[]
  fileName: string
  totalSize: number
}

export const uploadApi = {
  // MD5 秒传检测
  checkMd5: (body: { fileMd5: string; fileName: string; fileSize: number; folderId: number }, signal?: AbortSignal) =>
    http.post<Md5CheckResult>('/api/upload/check-md5', body, { signal }),

  // 简单上传
  simpleUpload: (formData: FormData, options?: { signal?: AbortSignal; timeout?: number; onUploadProgress?: (e: any) => void }) =>
    http.post<FileVO>('/api/files/simple', formData, options),

  // 初始化分片上传
  init: (body: { fileName: string; totalSize: number; chunkSize: number; fileMd5?: string; folderId: number }, signal?: AbortSignal) =>
    http.post<UploadInitResult>('/api/upload/init', body, { signal }),

  // 断点续传恢复查询
  resume: (uploadId: string, signal?: AbortSignal) =>
    http.get<UploadResumeResult>(`/api/upload/${uploadId}/resume`, { signal }),

  // 上传单张分片
  chunk: (formData: FormData, options?: { signal?: AbortSignal; timeout?: number }) =>
    http.post<Record<string, unknown>>('/api/upload/chunk', formData, options),

  // 合并分片
  merge: (body: { uploadId: string; mimeType?: string }, options?: { signal?: AbortSignal; timeout?: number }) =>
    http.post<FileVO>('/api/upload/merge', body, options),
}
