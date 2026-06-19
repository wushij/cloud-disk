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
}

interface NotificationDto {
  id: number
  type: string
  title: string
  content: string
  refId?: string
  isRead: number
  createdAt: string
}

function toAppNotification(dto: NotificationDto): AppNotification {
  return {
    id: String(dto.id),
    type: dto.type || 'info',
    title: dto.title || '通知',
    content: dto.content || '',
    refId: dto.refId,
    read: dto.isRead === 1,
    createdAt: dto.createdAt ? new Date(dto.createdAt).getTime() : Date.now()
  }
}

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<AppNotification[]>([])
  const loaded = ref(false)

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
  }) {
    const id = payload.id != null ? String(payload.id) : `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    if (items.value.find((x) => x.id === id)) return

    items.value.unshift({
      id,
      type: payload.type || 'info',
      title: payload.title || '通知',
      content: payload.content || '',
      refId: payload.refId,
      read: false,
      createdAt: Date.now()
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

  async function acceptTeamInvite(refId?: string) {
    if (!refId) throw new Error('邀请无效')
    await request({ url: `/api/team-invitations/${refId}/accept`, method: 'POST' })
  }

  async function rejectTeamInvite(refId?: string) {
    if (!refId) throw new Error('邀请无效')
    await request({ url: `/api/team-invitations/${refId}/reject`, method: 'POST' })
  }

  const unreadCount = () => items.value.filter((n) => !n.read).length

  return {
    items,
    loaded,
    loadFromApi,
    refreshUnreadCount,
    push,
    markRead,
    markAllRead,
    acceptTeamInvite,
    rejectTeamInvite,
    unreadCount
  }
})
