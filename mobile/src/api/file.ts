import { request } from './http'
import type { FileVO, DirectUrlVO, PageResult, MessageVO, FolderVO } from './types'

export const fileApi = {
  // 混合文件与文件夹列表
  list: (params: { folderId?: number; page?: number; size?: number; q?: string; fileType?: string }) =>
    request<PageResult<FileVO | FolderVO>>({ url: '/api/files', data: params }),

  // 单文件简单上传
  simpleUpload: (folderId: number, formData: FormData) =>
    request<FileVO>({ url: '/api/files/simple', method: 'POST', data: formData, params: { folderId } }),

  // 文件重命名
  rename: (id: number, name: string) =>
    request<FileVO>({ url: `/api/files/${id}/rename`, method: 'PUT', data: { name } }),

  // 移动文件
  move: (id: number, targetFolderId: number) =>
    request<FileVO>({ url: `/api/files/${id}/move`, method: 'PUT', data: { targetFolderId } }),

  // 复制文件
  copy: (id: number, targetFolderId: number) =>
    request<FileVO>({ url: `/api/files/${id}/copy`, method: 'POST', data: { targetFolderId } }),

  // 移入回收站
  delete: (id: number) => request<MessageVO>({ url: `/api/files/${id}`, method: 'DELETE' }),
  remove: (id: number) => request<MessageVO>({ url: `/api/files/${id}`, method: 'DELETE' }),

  // 保存视频帧封面
  savePoster: (id: number, dataUrl: string) =>
    request<MessageVO>({ url: `/api/files/${id}/poster`, method: 'POST', data: { dataUrl } }),

  // 获取 MinIO 预签名直链
  directUrl: (id: number) => request<DirectUrlVO>({ url: `/api/files/${id}/direct-url` }),

  // 全文检索 (ES)
  search: (params: { keyword: string; fileType?: string; page?: number; size?: number }) =>
    request<PageResult<FileVO | FolderVO>>({ url: '/api/files/search', data: params }),

  // 抓取文本预览内容
  fetchTextContent: (url: string) =>
    request<string>({ url, method: 'GET', header: { Accept: 'text/plain,*/*' } }),

  // URL 构造辅助函数
  previewUrl: (id: number) => `/api/files/${id}/preview`,
  thumbnailUrl: (id: number) => `/api/files/${id}/thumbnail`,
  downloadUrl: (id: number) => `/api/files/${id}/download`,
}
