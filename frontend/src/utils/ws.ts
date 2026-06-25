import http from '@/api/http'

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

let ws: WebSocket | null = null
let connectPromise: Promise<void> | null = null
const listeners = new Set<WsListener>()

async function connectWs() {
  const { data } = await http.post<{ ticket: string }>('/api/auth/ws-ticket')
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/upload?ticket=${encodeURIComponent(data.ticket)}`)
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

async function ensureConnected() {
  if (ws) return
  if (!connectPromise) {
    connectPromise = connectWs().finally(() => {
      connectPromise = null
    })
  }
  await connectPromise
}

export function subscribeWs(listener: WsListener) {
  listeners.add(listener)
  void ensureConnected()
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
