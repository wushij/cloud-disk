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
  }) {
    const id = payload.id != null ? String(payload.id) : `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const existing = items.value.find((x) => x.id === id)
    if (existing) return

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
    await http.put(`/api/notifications/${id}/read`)
    n.read = true
  }

  async function markAllRead() {
    await http.put('/api/notifications/read-all')
    items.value.forEach((n) => {
      n.read = true
    })
  }

  const unreadCount = () => items.value.filter((n) => !n.read).length

  return { items, loaded, loadFromApi, push, markRead, markAllRead, unreadCount }
})
