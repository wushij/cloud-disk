import { request } from './http'
import type { MessageVO } from './types'

export interface NotificationDto {
  id: number
  userId: number
  title: string
  content: string
  type: string
  readStatus: number
  extraData?: string
  createTime: string
}

export const notificationApi = {
  // 获取站内通知列表
  list: (page = 0, size = 20) =>
    request<NotificationDto[]>({ url: '/api/notifications', data: { page, size } }),

  // 标记单条为已读
  markRead: (id: string | number) =>
    request<MessageVO>({ url: `/api/notifications/${id}/read`, method: 'PUT' }),

  // 全部标为已读
  markAllRead: () => request<MessageVO>({ url: '/api/notifications/read-all', method: 'PUT' }),

  // 删除单条通知
  remove: (id: string | number) =>
    request<MessageVO>({ url: `/api/notifications/${id}`, method: 'DELETE' }),

  // 清空所有通知
  clearAll: () => request<MessageVO>({ url: '/api/notifications/clear-all', method: 'DELETE' }),

  // 接受团队邀请
  acceptTeamInvite: (inviteId: string | number) =>
    request<MessageVO>({ url: `/api/team-invitations/${inviteId}/accept`, method: 'POST' }),

  // 拒绝团队邀请
  rejectTeamInvite: (inviteId: string | number) =>
    request<MessageVO>({ url: `/api/team-invitations/${inviteId}/reject`, method: 'POST' }),
}
