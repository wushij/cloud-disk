import http from './http'
import type { MessageVO } from './types'

export interface RecycledItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  deletedAt: string
  mimeType?: string
  hasThumbnail?: boolean
}

export const recycleApi = {
  // 回收站列表
  list: () => http.get<RecycledItem[]>('/api/recycle'),

  // 恢复文件/文件夹
  restore: (type: 'file' | 'folder', id: number) =>
    http.post<MessageVO>(`/api/recycle/restore/${type}/${id}`),

  // 永久删除文件/文件夹
  remove: (type: 'file' | 'folder', id: number) =>
    http.delete<MessageVO>(`/api/recycle/${type}/${id}`),

  // 清空回收站
  clear: () => http.delete<MessageVO>('/api/recycle/clear'),
}
