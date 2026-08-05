/**
 * 客户端接口安全配置（SM4 加密 / HMAC-SM3 签名）
 *
 * 【安全原则】密钥材料仅保存在模块闭包内存中，严禁写入 SessionStorage / LocalStorage。
 * 页面刷新后内存配置丢失，由初始化流程重新调用 POST /api/auth/session-sign-init
 * 获取新的临时会话密钥，无需从本地存储恢复。
 * clientId 为纯内存随机 ID，每次页面加载重新生成，不持久化。
 */
export interface ClientSecurityConfig {
  /** 是否启用 SM4 加密 */
  sm4EncryptEnabled?: boolean
  /** 是否启用 HMAC-SM3 签名 */
  sm2SignEnabled?: boolean
  sm3SignEnabled?: boolean
  /** SM4 对称密钥（16字节/32位Hex），由 session-sign-init 下发，仅存内存 */
  sm4Key?: string
  /** HMAC-SM3 签名 Key，由 session-sign-init 下发，仅存内存 */
  sm3SignKey?: string
}

const CLIENT_ID_STORAGE_KEY = 'cd_security_client_id'

function createClientId(): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  const bytes = new Uint8Array(32)
  if (typeof window !== 'undefined' && window.crypto?.getRandomValues) {
    window.crypto.getRandomValues(bytes)
  } else if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes)
  } else {
    throw new Error('当前环境不支持 crypto.getRandomValues，无法生成安全的 ClientId')
  }
  let id = ''
  for (let i = 0; i < 32; i++) id += chars[bytes[i] % chars.length]
  return id
}

/**
 * Tab-scoped random ID. It is not secret; session keys remain memory-only and
 * are renegotiated after a full reload.
 */
const _clientId: string = (() => {
  if (typeof window !== 'undefined' && window.sessionStorage) {
    const cached = window.sessionStorage.getItem(CLIENT_ID_STORAGE_KEY)
    if (cached && /^[A-Za-z0-9\-_]{8,128}$/.test(cached)) return cached
    const next = createClientId()
    window.sessionStorage.setItem(CLIENT_ID_STORAGE_KEY, next)
    return next
  }
  return createClientId()
})()

/** 内存闭包安全配置（不持久化到任何存储介质） */
let securityConfig: ClientSecurityConfig = {}

/** 获取当前客户端会话 ID */
export function getClientId(): string {
  return _clientId
}

/** 合并更新安全配置（仅更新内存闭包） */
export function setSecurityConfig(next: ClientSecurityConfig): void {
  securityConfig = { ...securityConfig, ...next }
}

/** 读取当前安全配置快照 */
export function getSecurityConfig(): Readonly<ClientSecurityConfig> {
  return securityConfig
}

/** 重置安全配置（登出时调用）—— 仅清除密钥，保留开关避免重登录后签名失效 */
export function resetSecurityConfig(): void {
  clearSignKeys()
}

let refreshTimer: ReturnType<typeof setTimeout> | null = null

function stopHeartbeatTimer(): void {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

function startHeartbeatTimer(axiosInstance: any): void {
  stopHeartbeatTimer()
  // 每 20 分钟静默刷新签名密钥，防止 Redis TTL（120分钟）过期前密钥失效
  refreshTimer = setTimeout(() => {
    clearSignKeys()
    requestSessionSignKey(axiosInstance).catch(() => {})
  }, 20 * 60 * 1000)
}

/**
 * 仅清除会话密钥材料，保留签名/加密开关。
 * 同时清空 Promise 缓存，允许 requestSessionSignKey 重新发起协商。
 */
export function clearSignKeys(): void {
  stopHeartbeatTimer()
  securityConfig = {
    sm4EncryptEnabled: securityConfig.sm4EncryptEnabled,
    sm3SignEnabled: securityConfig.sm3SignEnabled,
    sm2SignEnabled: securityConfig.sm2SignEnabled,
  }
  sessionSignPromise = null
}

let sessionSignPromise: Promise<any> | null = null

/**
 * 请求初始化会话签名密钥（Promise 锁防并发）。
 * 成功后保持已 resolved 的 Promise 缓存，防止后续重复发起请求。
 * 失败时清空 Promise，允许下次重试。
 */
export function requestSessionSignKey(axiosInstance: any): Promise<any> {
  const existing = securityConfig
  if (existing.sm3SignKey || existing.sm4Key) {
    return Promise.resolve(existing)
  }
  // Promise 锁：正在请求中，复用同一个 Promise
  if (sessionSignPromise) return sessionSignPromise

  sessionSignPromise = (async () => {
    try {
      const res = await axiosInstance.post('/api/auth/session-sign-init', undefined, {
        params: { clientId: getClientId() },
      })
      const data = res.data ?? res
      if (data?.enabled) {
        if ((data.sm3SignEnabled && !data.sm3SignKey) || (data.sm4EncryptEnabled && !data.sm4Key)) {
          throw new Error('session-sign-init response missing security keys')
        }
        setSecurityConfig({
          // 服务端同步下发当前开关状态，前端据此启用签名/加密
          sm3SignEnabled: !!data.sm3SignEnabled,
          sm4EncryptEnabled: !!data.sm4EncryptEnabled,
          sm3SignKey: data.sm3SignKey,
          sm4Key: data.sm4Key,
        })
        startHeartbeatTimer(axiosInstance)
      } else {
        // 服务端明确告知未启用，关闭本地开关
        setSecurityConfig({
          sm3SignEnabled: false,
          sm4EncryptEnabled: false,
        })
      }
      return res
    } catch (err) {
      stopHeartbeatTimer()
      throw err
    } finally {
      sessionSignPromise = null
    }
  })()

  return sessionSignPromise
}
