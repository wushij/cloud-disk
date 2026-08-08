import http from './http'
import type { MessageVO } from './types'

export interface NotificationDto {
  id: number
  type: string
  title: string
  content: string
  refId?: string
  isRead: number
  createdAt: string
  inviteStatus?: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED'
  registrationStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
  quotaStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
}

export const notificationApi = {
  // 通知列表
  list: (page = 0, size = 50) =>
    http.get<NotificationDto[]>('/api/notifications', { params: { page, size } }),

  // 标记单条已读
  markRead: (id: string | number) => http.put<MessageVO>(`/api/notifications/${id}/read`),

  // 标记全部已读
  markAllRead: () => http.put<MessageVO>('/api/notifications/read-all'),

  // 删除通知
  remove: (id: string | number) => http.delete<MessageVO>(`/api/notifications/${id}`),

  // 清空全部通知
  clearAll: () => http.delete<MessageVO>('/api/notifications/clear-all'),

  // 接受团队邀请
  acceptInvite: (inviteId: string | number) =>
    http.post<MessageVO>(`/api/team-invitations/${inviteId}/accept`),

  // 拒绝团队邀请
  rejectInvite: (inviteId: string | number) =>
    http.post<MessageVO>(`/api/team-invitations/${inviteId}/reject`),
}
