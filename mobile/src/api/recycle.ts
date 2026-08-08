import { request } from './http'
import type { MessageVO } from './types'

export interface RecycledItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  mimeType?: string | null
  deleteTime?: string
}

export const recycleApi = {
  // 回收站列表
  list: () => request<RecycledItem[]>({ url: '/api/recycle' }),

  // 还原
  restore: (type: 'file' | 'folder', id: number) =>
    request<MessageVO>({ url: `/api/recycle/restore/${type}/${id}`, method: 'POST' }),

  // 彻底删除
  delete: (type: 'file' | 'folder', id: number) =>
    request<MessageVO>({ url: `/api/recycle/${type}/${id}`, method: 'DELETE' }),

  // 清空回收站
  clear: () => request<MessageVO>({ url: '/api/recycle/clear', method: 'DELETE' }),
}
