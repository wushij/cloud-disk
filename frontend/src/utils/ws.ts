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
  inviteStatus?: string
  registrationStatus?: string
}

type WsListener = (data: WsMessage) => void

let ws: WebSocket | null = null
const listeners = new Set<WsListener>()

function ensureConnected() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token || ws) return
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/upload?token=${encodeURIComponent(token)}`)
  ws.onmessage = (ev) => {
    try {
      const data = JSON.parse(ev.data) as WsMessage
      listeners.forEach((fn) => fn(data))
    } catch {
      /* ignore */
    }
  }
  ws.onclose = () => {
    ws = null
  }
}

export function subscribeWs(listener: WsListener) {
  listeners.add(listener)
  ensureConnected()
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) {
      ws?.close()
      ws = null
    }
  }
}

/** @deprecated use subscribeWs */
export function connectUploadWs(onProgress: (data: WsMessage) => void) {
  return subscribeWs(onProgress)
}

export function disconnectUploadWs() {
  listeners.clear()
  ws?.close()
  ws = null
}
