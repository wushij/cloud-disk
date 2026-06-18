import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '@/stores/notification'

vi.mock('@/api/http', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    put: vi.fn().mockResolvedValue({ data: {} })
  }
}))

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('push adds unread notification', () => {
    const store = useNotificationStore()
    store.push({ id: '1', title: '测试', content: '内容', type: 'info' })
    expect(store.items).toHaveLength(1)
    expect(store.unreadCount()).toBe(1)
  })

  it('markRead clears unread count', async () => {
    const store = useNotificationStore()
    store.push({ id: '2', title: '已读', content: 'x' })
    await store.markRead('2')
    expect(store.unreadCount()).toBe(0)
  })
})
