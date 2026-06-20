import { TOKEN_KEY } from '@/api/http'

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
}

type WsListener = (data: WsMessage) => void

let socketTask: UniApp.SocketTask | null = null
const listeners = new Set<WsListener>()
let connecting = false

function wsUrl(token: string) {
  // #ifdef H5
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws/upload?token=${encodeURIComponent(token)}`
  // #endif
  // #ifndef H5
  return ''
  // #endif
}

function ensureConnected() {
  // #ifndef H5
  return
  // #endif
  const token = uni.getStorageSync(TOKEN_KEY)
  if (!token || socketTask || connecting) return

  const url = wsUrl(token)
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
  ensureConnected()
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
