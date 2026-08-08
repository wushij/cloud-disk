import http from './http'
import type { ShareVO, MessageVO } from './types'

export const shareApi = {
  // 创建分享
  create: (body: { fileId?: number; folderId?: number; extractCode?: string; expireHours?: number }) =>
    http.post<ShareVO>('/api/share', body),

  // 我的分享列表
  mine: () => http.get<ShareVO[]>('/api/share/mine'),

  // 取消分享
  cancel: (id: number) => http.delete<MessageVO>(`/api/share/${id}`),

  // 清除失效/过期分享
  clearExpired: () => http.delete<MessageVO>('/api/share/expired/clear'),

  // 公开分享页接口（不需要 token）
  getByCode: (code: string) => http.get<ShareVO>(`/share/${code}`),
  publicDetail: (code: string) => http.get<ShareVO>(`/share/${code}`),

  // 提取码校验与获取文件信息
  access: (code: string, extractCode?: string) =>
    http.post<Record<string, unknown>>(`/share/${code}/access`, { extractCode }),
  publicAccess: (code: string, extractCode?: string) =>
    http.post<Record<string, unknown>>(`/share/${code}/access`, { extractCode }),

  // 分享文件夹项查询
  folderItems: (code: string, params?: { extractCode?: string; folderId?: number }) =>
    http.get<Record<string, unknown>>(`/share/${code}/items`, { params }),
  publicFolderItems: (code: string, folderId?: number) =>
    http.get<Record<string, unknown>>(`/share/${code}/items`, { params: { folderId: folderId ?? undefined } }),

  // 公开分享直链与 OnlyOffice
  publicDirectUrl: (code: string, fileId: number) =>
    http.get<{ url: string }>(`/share/${code}/direct-url`, { params: { fileId } }),
  publicOnlyOffice: (code: string, fileId: number) =>
    http.get<{ documentServerUrl: string; config: Record<string, unknown> }>(`/share/${code}/onlyoffice/${fileId}`),

  // 构造分享文件的下载/预览/缩略图路径
  downloadUrl: (code: string, extractCode?: string, fileId?: number) => {
    const q = new URLSearchParams()
    if (extractCode) q.set('extractCode', extractCode)
    if (fileId) q.set('fileId', String(fileId))
    const str = q.toString()
    return str ? `/share/${code}/download?${str}` : `/share/${code}/download`
  },

  previewUrl: (code: string, extractCode?: string, fileId?: number) => {
    const q = new URLSearchParams()
    if (extractCode) q.set('extractCode', extractCode)
    if (fileId) q.set('fileId', String(fileId))
    const str = q.toString()
    return str ? `/share/${code}/preview?${str}` : `/share/${code}/preview`
  },

  thumbnailUrl: (code: string, extractCode?: string, fileId?: number) => {
    const q = new URLSearchParams()
    if (extractCode) q.set('extractCode', extractCode)
    if (fileId) q.set('fileId', String(fileId))
    const str = q.toString()
    return str ? `/share/${code}/thumbnail?${str}` : `/share/${code}/thumbnail`
  },
}
