import { request } from './http'
import type { TeamSpaceVO, FileVO, FolderVO, PageResult, MessageVO, FolderTreeNodeVO } from './types'

export interface TeamMemberItem {
  userId: number
  username?: string
  nickname?: string
  avatar?: string
  hasAvatar?: boolean
  role: string
  joinTime: string
}

export const teamApi = {
  // 团队列表
  list: () => request<TeamSpaceVO[]>({ url: '/api/teams' }),

  // 团队详情
  detail: (id: number) => request<TeamSpaceVO>({ url: `/api/teams/${id}` }),

  // 创建团队
  create: (name: string) => request<TeamSpaceVO>({ url: '/api/teams', method: 'POST', data: { name } }),

  // 重命名团队
  rename: (id: number, name: string) =>
    request<TeamSpaceVO>({ url: `/api/teams/${id}`, method: 'PUT', data: { name } }),

  // 设置团队配额
  setQuota: (id: number, maxSize: number) =>
    request<TeamSpaceVO>({ url: `/api/teams/${id}/quota`, method: 'PUT', data: { maxSize } }),

  // 解散团队
  delete: (id: number) => request<MessageVO>({ url: `/api/teams/${id}`, method: 'DELETE' }),

  // 退出团队
  leave: (id: number) => request<MessageVO>({ url: `/api/teams/${id}/leave`, method: 'POST' }),

  // 团队文件列表
  files: (id: number, folderId?: number) =>
    request<PageResult<FileVO | FolderVO>>({ url: `/api/teams/${id}/files`, data: { folderId } }),

  // 团队成员列表
  members: (id: number) => request<TeamMemberItem[]>({ url: `/api/teams/${id}/members` }),

  // 邀请团队成员
  inviteMember: (id: number, username: string, role = 'MEMBER') =>
    request<Record<string, unknown>>({ url: `/api/teams/${id}/members`, method: 'POST', data: { username, role } }),

  // 修改团队成员角色
  setMemberRole: (id: number, userId: number, role: string) =>
    request<MessageVO>({ url: `/api/teams/${id}/members/${userId}/role`, method: 'PUT', data: { role } }),

  // 移除团队成员
  removeMember: (id: number, userId: number) =>
    request<MessageVO>({ url: `/api/teams/${id}/members/${userId}`, method: 'DELETE' }),

  // 团队目录树
  tree: (id: number) => request<FolderTreeNodeVO[]>({ url: `/api/teams/${id}/folders/tree` }),

  // 团队头像 URL 构造
  avatarUrl: (id: number, v?: string | number) =>
    v ? `/api/teams/${id}/avatar?v=${v}` : `/api/teams/${id}/avatar`,
}
