import http from './http'
import type { TeamSpaceVO, PageResult, FileVO, FolderVO, FolderTreeNodeVO, MessageVO } from './types'

export interface TeamMemberItem {
  id: number
  userId: number
  username: string
  nickname: string
  avatar: string
  role: string
  joinTime: string
}

export const teamApi = {
  // 我加入的团队列表
  list: () => http.get<TeamSpaceVO[]>('/api/teams'),

  // 获取团队详情
  detail: (id: number) => http.get<TeamSpaceVO>(`/api/teams/${id}`),

  // 创建团队空间
  create: (name: string) => http.post<TeamSpaceVO>('/api/teams', { name }),

  // 重命名团队空间
  rename: (id: number, name: string) => http.put<TeamSpaceVO>(`/api/teams/${id}`, { name }),

  // 设置团队存储配额
  setQuota: (id: number, maxSize: number) =>
    http.put<TeamSpaceVO>(`/api/teams/${id}/quota`, { maxSize }),

  // 删除/解散团队空间
  delete: (id: number) => http.delete<MessageVO>(`/api/teams/${id}`),

  // 退出团队空间
  leave: (id: number) => http.post<MessageVO>(`/api/teams/${id}/leave`),

  // 列出团队内的文件列表
  files: (id: number, folderId = 0) =>
    http.get<PageResult<FileVO | FolderVO>>(`/api/teams/${id}/files`, { params: { folderId } }),

  // 列出团队成员
  members: (id: number) => http.get<TeamMemberItem[]>(`/api/teams/${id}/members`),

  // 邀请/添加成员
  inviteMember: (id: number, username: string, role = 'MEMBER') =>
    http.post<Record<string, unknown>>(`/api/teams/${id}/members`, { username, role }),

  // 调整成员角色
  setMemberRole: (id: number, userId: number, role: string) =>
    http.put<MessageVO>(`/api/teams/${id}/members/${userId}/role`, { role }),

  // 移除团队成员
  removeMember: (id: number, userId: number) =>
    http.delete<MessageVO>(`/api/teams/${id}/members/${userId}`),

  // 上传团队头像
  uploadAvatar: (id: number, formData: FormData) =>
    http.post<{ avatar: string }>(`/api/teams/${id}/avatar`, formData),

  // 团队/成员头像地址构造函数
  avatarUrl: (id: number, v?: string) =>
    v ? `/api/teams/${id}/avatar?v=${v}` : `/api/teams/${id}/avatar`,
  memberAvatarUrl: (id: number, userId: number) => `/api/teams/${id}/members/${userId}/avatar`,
}
