import { request } from './http'
import type { ShareVO, MessageVO } from './types'

export const shareApi = {
  // 创建分享
  create: (body: { fileId?: number; folderId?: number; extractCode?: string; expireHours?: number }) =>
    request<ShareVO>({ url: '/api/share', method: 'POST', data: body }),

  // 我的分享列表
  mine: () => request<ShareVO[]>({ url: '/api/share/mine' }),

  // 取消/彻底删除分享
  cancel: (id: number) => request<MessageVO>({ url: `/api/share/${id}`, method: 'DELETE' }),

  // 清除失效/过期分享
  clearExpired: () => request<MessageVO>({ url: '/api/share/expired/clear', method: 'DELETE' }),

  // 公开分享详情
  publicDetail: (code: string) => request<ShareVO>({ url: `/share/${code}` }),

  // 提取码校验与获取文件信息
  publicAccess: (code: string, extractCode?: string) =>
    request<Record<string, unknown>>({ url: `/share/${code}/access`, method: 'POST', data: { extractCode } }),

  // 分享文件夹项查询
  publicFolderItems: (code: string, folderId?: number) =>
    request<Record<string, unknown>>({ url: `/share/${code}/items`, data: { folderId: folderId ?? undefined } }),

  // 公开分享直链与 OnlyOffice
  publicDirectUrl: (code: string, fileId: number) =>
    request<{ url: string }>({ url: `/share/${code}/direct-url`, data: { fileId } }),

  publicOnlyOffice: (code: string, fileId: number) =>
    request<{ documentServerUrl: string; config: Record<string, unknown> }>({ url: `/share/${code}/onlyoffice/${fileId}` }),

  // URL 构造辅助函数
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
