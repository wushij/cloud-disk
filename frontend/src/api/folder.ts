import http from './http'
import type { FolderVO, FolderTreeNodeVO, BreadcrumbVO, MessageVO } from './types'

export const folderApi = {
  // 获取目录树
  tree: () => http.get<FolderTreeNodeVO[]>('/api/folders/tree'),

  // 创建文件夹
  create: (body: { folderName: string; parentId?: number }) =>
    http.post<FolderVO>('/api/folders', body),

  // 重命名文件夹
  rename: (id: number, name: string) =>
    http.put<FolderVO>(`/api/folders/${id}/rename`, { name }),

  // 移动文件夹
  move: (id: number, targetFolderId: number) =>
    http.put<FolderVO>(`/api/folders/${id}/move`, { targetFolderId }),

  // 删除文件夹至回收站
  delete: (id: number) => http.delete<MessageVO>(`/api/folders/${id}`),
  remove: (id: number) => http.delete<MessageVO>(`/api/folders/${id}`),

  // 获取面包屑路径
  getBreadcrumbs: (id: number, full = false) =>
    http.get<BreadcrumbVO[]>(`/api/folders/${id}/breadcrumbs`, { params: { full } }),
}
