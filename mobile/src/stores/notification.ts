import { ref } from 'vue'
import { defineStore } from 'pinia'
import { request } from '@/api/http'

export interface AppNotification {
  id: string
  type: string
  title: string
  content: string
  refId?: string
  read: boolean
  createdAt: number
  inviteStatus?: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED'
  registrationStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
}

interface NotificationDto {
  id: number
  type: string
  title: string
  content: string
  refId?: string
  isRead: number
  createdAt: string
  inviteStatus?: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED'
  registrationStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
}

function toAppNotification(dto: NotificationDto): AppNotification {
  return {
    id: String(dto.id),
    type: dto.type || 'info',
    title: dto.title || '通知',
    content: dto.content || '',
    refId: dto.refId,
    read: dto.isRead === 1,
    createdAt: dto.createdAt ? new Date(dto.createdAt).getTime() : Date.now(),
    inviteStatus: dto.inviteStatus,
    registrationStatus: dto.registrationStatus
  }
}

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<AppNotification[]>([])
  const loaded = ref(false)
  const lastSeenAt = ref(Date.now())

  async function loadFromApi() {
    const data = await request<NotificationDto[]>({
      url: '/api/notifications',
      data: { page: 0, size: 50 }
    })
    items.value = (data || []).map(toAppNotification)
    loaded.value = true
  }

  async function refreshUnreadCount() {
    try {
      const data = await request<{ count?: number }>({ url: '/api/notifications/unread-count' })
      return data.count || 0
    } catch {
      return unreadCount()
    }
  }

  function push(payload: {
    id?: string | number
    type?: string
    title?: string
    content?: string
    refId?: string
    inviteStatus?: AppNotification['inviteStatus']
    registrationStatus?: AppNotification['registrationStatus']
  }) {
    const id = payload.id != null ? String(payload.id) : `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    if (items.value.find((x) => x.id === id)) return

    const type = payload.type || 'info'
    items.value.unshift({
      id,
      type,
      title: payload.title || '通知',
      content: payload.content || '',
      refId: payload.refId,
      read: false,
      createdAt: Date.now(),
      inviteStatus: payload.inviteStatus ?? (type === 'TEAM_INVITED' ? 'PENDING' : undefined),
      registrationStatus: payload.registrationStatus ?? (type === 'USER_REGISTER' ? 'PENDING' : undefined)
    })
    if (items.value.length > 50) {
      items.value.length = 50
    }
  }

  async function markRead(id: string) {
    const n = items.value.find((x) => x.id === id)
    if (!n || n.read) return
    await request({ url: `/api/notifications/${id}/read`, method: 'PUT' })
    n.read = true
  }

  async function markAllRead() {
    await request({ url: '/api/notifications/read-all', method: 'PUT' })
    items.value.forEach((n) => {
      n.read = true
    })
  }

  async function acceptTeamInvite(notification: AppNotification) {
    if (!notification.refId) throw new Error('邀请无效')
    await request({ url: `/api/team-invitations/${notification.refId}/accept`, method: 'POST' })
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.inviteStatus = 'ACCEPTED'
    }
  }

  async function rejectTeamInvite(notification: AppNotification) {
    if (!notification.refId) throw new Error('邀请无效')
    await request({ url: `/api/team-invitations/${notification.refId}/reject`, method: 'POST' })
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.inviteStatus = 'REJECTED'
    }
  }

  async function approveRegistration(notification: AppNotification) {
    if (!notification.refId) throw new Error('申请无效')
    await request({ url: `/api/admin/registrations/${notification.refId}/approve`, method: 'POST' })
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.registrationStatus = 'APPROVED'
    }
  }

  async function rejectRegistration(notification: AppNotification) {
    if (!notification.refId) throw new Error('申请无效')
    await request({ url: `/api/admin/registrations/${notification.refId}/reject`, method: 'POST' })
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.registrationStatus = 'REJECTED'
    }
  }

  async function deleteNotification(id: string) {
    await request({ url: `/api/notifications/${id}`, method: 'DELETE' })
    items.value = items.value.filter((x) => x.id !== id)
  }

  async function clearAllNotifications() {
    await request({ url: '/api/notifications/clear-all', method: 'DELETE' })
    items.value = []
  }

  function markSeen() {
    lastSeenAt.value = Date.now()
  }

  const unreadCount = () => items.value.filter((n) => !n.read && n.createdAt > lastSeenAt.value).length

  return {
    items,
    loaded,
    lastSeenAt,
    loadFromApi,
    refreshUnreadCount,
    push,
    markRead,
    markAllRead,
    markSeen,
    acceptTeamInvite,
    rejectTeamInvite,
    approveRegistration,
    rejectRegistration,
    deleteNotification,
    clearAllNotifications,
    unreadCount
  }
})
