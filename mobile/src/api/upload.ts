import { request } from './http'
import type { FileVO } from './types'

export interface Md5CheckResult {
  hit: boolean
  file?: FileVO
  existParts?: number[]
  uploadId?: string
}

export interface UploadInitResult {
  uploadId: string
  chunkSize: number
  totalParts: number
}

export interface UploadResumeResult {
  uploadId: string
  chunkSize: number
  totalParts: number
  existParts: number[]
}

export const uploadApi = {
  // 检查 MD5 秒传 / 分片断点
  checkMd5: (body: { md5: string; totalSize: number; fileName: string; folderId?: number }) =>
    request<Md5CheckResult>({ url: '/api/upload/check-md5', method: 'POST', data: body }),

  // 简单单文件上传
  simpleUpload: (formData: FormData, folderId = 0) =>
    request<FileVO>({ url: '/api/files/simple', method: 'POST', data: formData, params: { folderId } }),

  // 初始化分片上传
  initChunked: (body: { fileName: string; totalSize: number; md5: string; folderId?: number }) =>
    request<UploadInitResult>({ url: '/api/upload/init', method: 'POST', data: body }),

  // 恢复分片上传状态
  resumeChunked: (uploadId: string) =>
    request<UploadResumeResult>({ url: `/api/upload/${uploadId}/resume` }),

  // 上传单个分片
  uploadChunk: (formData: FormData) =>
    request<Record<string, unknown>>({ url: '/api/upload/chunk', method: 'POST', data: formData }),

  // 合并分片完成上传
  merge: (body: { uploadId: string; mimeType?: string; fileName?: string; folderId?: number; totalSize?: number; md5?: string }, signal?: AbortSignal) =>
    request<FileVO>({ url: '/api/upload/merge', method: 'POST', data: body, timeout: 0, signal } as any),

  // URL 构造辅助函数
  chunkUploadUrl: () => '/api/upload/chunk',
  simpleUploadUrl: () => '/api/files/simple',
}
