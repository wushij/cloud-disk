import { ref } from 'vue'
import { defineStore } from 'pinia'
import http from '@/api/http'

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

  async function loadFromApi() {
    const { data } = await http.get<NotificationDto[]>('/api/notifications', {
      params: { page: 0, size: 50 }
    })
    items.value = (data || []).map(toAppNotification)
    loaded.value = true
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
    const existing = items.value.find((x) => x.id === id)
    if (existing) return

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
    await http.put(`/api/notifications/${id}/read`)
    n.read = true
  }

  async function markAllRead() {
    await http.put('/api/notifications/read-all')
    items.value.forEach((n) => {
      n.read = true
    })
  }

  async function remove(id: string) {
    await http.delete(`/api/notifications/${id}`)
    items.value = items.value.filter((n) => n.id !== id)
  }

  async function clearAll() {
    await http.delete('/api/notifications/clear-all')
    items.value = []
  }

  async function acceptTeamInvite(notification: AppNotification) {
    if (!notification.refId) throw new Error('邀请无效')
    await http.post(`/api/team-invitations/${notification.refId}/accept`)
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.inviteStatus = 'ACCEPTED'
    }
  }

  async function rejectTeamInvite(notification: AppNotification) {
    if (!notification.refId) throw new Error('邀请无效')
    await http.post(`/api/team-invitations/${notification.refId}/reject`)
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.inviteStatus = 'REJECTED'
    }
  }

  async function approveRegistration(notification: AppNotification) {
    if (!notification.refId) throw new Error('申请无效')
    await http.post(`/api/admin/registrations/${notification.refId}/approve`)
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.registrationStatus = 'APPROVED'
    }
  }

  async function rejectRegistration(notification: AppNotification) {
    if (!notification.refId) throw new Error('申请无效')
    await http.post(`/api/admin/registrations/${notification.refId}/reject`)
    await markRead(notification.id)
    const n = items.value.find((x) => x.id === notification.id)
    if (n) {
      n.registrationStatus = 'REJECTED'
    }
  }

  const unreadCount = () => items.value.filter((n) => !n.read).length

  return { items, loaded, loadFromApi, push, markRead, markAllRead, remove, clearAll, acceptTeamInvite, rejectTeamInvite, approveRegistration, rejectRegistration, unreadCount }
})
