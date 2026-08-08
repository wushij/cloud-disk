import { request } from './http'
import type { FolderVO, FolderTreeNodeVO, BreadcrumbVO, MessageVO } from './types'

export const folderApi = {
  // 目录树
  tree: () => request<FolderTreeNodeVO[]>({ url: '/api/folders/tree' }),

  // 创建文件夹
  create: (body: { folderName: string; parentId?: number }) =>
    request<FolderVO>({ url: '/api/folders', method: 'POST', data: body }),

  // 重命名文件夹
  rename: (id: number, name: string) =>
    request<FolderVO>({ url: `/api/folders/${id}/rename`, method: 'PUT', data: { name } }),

  // 移动文件夹
  move: (id: number, targetFolderId: number) =>
    request<FolderVO>({ url: `/api/folders/${id}/move`, method: 'PUT', data: { targetFolderId } }),

  // 移入回收站
  delete: (id: number) => request<MessageVO>({ url: `/api/folders/${id}`, method: 'DELETE' }),
  remove: (id: number) => request<MessageVO>({ url: `/api/folders/${id}`, method: 'DELETE' }),

  // 获取面包屑路径
  breadcrumbs: (id: number, full = false) =>
    request<BreadcrumbVO[]>({ url: `/api/folders/${id}/breadcrumbs`, data: { full } }),
}
