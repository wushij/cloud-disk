import http from './http'
import type { FileVO, DirectUrlVO, PageResult, MessageVO, FolderVO } from './types'

export const fileApi = {
  // 获取文件与文件夹混合列表
  list: (params: { folderId?: number; page?: number; size?: number; q?: string; fileType?: string }) =>
    http.get<PageResult<FileVO | FolderVO>>('/api/files', { params }),

  // 简单单文件上传
  simpleUpload: (formData: FormData, folderId = 0) =>
    http.post<FileVO>('/api/files/simple', formData, { params: { folderId } }),

  // 重命名文件
  rename: (id: number, name: string) =>
    http.put<FileVO>(`/api/files/${id}/rename`, { name }),

  // 移动文件
  move: (id: number, targetFolderId: number) =>
    http.put<FileVO>(`/api/files/${id}/move`, { targetFolderId }),

  // 复制文件
  copy: (id: number, targetFolderId: number) =>
    http.post<FileVO>(`/api/files/${id}/copy`, { targetFolderId }),

  // 移入回收站
  delete: (id: number) => http.delete<MessageVO>(`/api/files/${id}`),
  remove: (id: number) => http.delete<MessageVO>(`/api/files/${id}`),

  // 保存视频封面
  savePoster: (id: number, dataUrl: string) =>
    http.post<MessageVO>(`/api/files/${id}/poster`, { dataUrl }),

  // 获取 MinIO 预签名直链
  directUrl: (id: number) => http.get<DirectUrlVO>(`/api/files/${id}/direct-url`),

  // 全文检索 (ElasticSearch)
  search: (params: { keyword: string; fileType?: string; page?: number; size?: number }) =>
    http.get<PageResult<FileVO | FolderVO>>('/api/files/search', { params }),

  // 辅助函数：构造预览/缩略图/下载静态 URL 字符串
  previewUrl: (id: number) => `/api/files/${id}/preview`,
  thumbnailUrl: (id: number) => `/api/files/${id}/thumbnail`,
  downloadUrl: (id: number) => `/api/files/${id}/download`,
}
