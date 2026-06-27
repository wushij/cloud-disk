import { request } from '@/api/http'

export type WsMessage = {
  type?: string
  taskId?: string
  fileName?: string
  progress?: number
  status?: string
  title?: string
  content?: string
  refId?: string
  notifyType?: string
  notifyId?: string | number
  inviteStatus?: string
  registrationStatus?: string
  quotaStatus?: string
}

type WsListener = (data: WsMessage) => void

let socketTask: UniApp.SocketTask | null = null
const listeners = new Set<WsListener>()
let connecting = false

async function wsUrl(): Promise<string> {
  // #ifdef H5
  const data = await request<{ ticket: string }>({
    url: '/api/auth/ws-ticket',
    method: 'POST'
  })
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws/upload?ticket=${encodeURIComponent(data.ticket)}`
  // #endif
  // #ifndef H5
  return ''
  // #endif
}

async function ensureConnected() {
  // #ifndef H5
  return
  // #endif
  if (socketTask || connecting) return

  const url = await wsUrl()
  if (!url) return

  connecting = true
  const task = uni.connectSocket({
    url,
    complete: () => {
      connecting = false
    }
  })
  socketTask = task

  task.onMessage((ev) => {
    try {
      const data = JSON.parse(String(ev.data)) as WsMessage
      listeners.forEach((fn) => fn(data))
    } catch {
      /* ignore */
    }
  })

  task.onClose(() => {
    socketTask = null
    connecting = false
  })

  task.onError(() => {
    socketTask = null
    connecting = false
  })
}

export function subscribeWs(listener: WsListener) {
  listeners.add(listener)
  void ensureConnected()
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0 && socketTask) {
      socketTask.close({})
      socketTask = null
    }
  }
}

export function disconnectWs() {
  listeners.clear()
  if (socketTask) {
    socketTask.close({})
    socketTask = null
  }
}
