/**
 * 移动端客户端接口安全配置（SM4 加密 / HMAC-SM3 签名）
 *
 * 【安全原则】密钥材料仅保存在模块闭包内存中，严禁写入 uni.setStorageSync。
 * 页面重启后内存丢失，由初始化流程重新调用 /api/auth/session-sign-init 获取新密钥。
 */
export interface ClientSecurityConfig {
  sm4EncryptEnabled?: boolean
  sm2SignEnabled?: boolean
  sm3SignEnabled?: boolean
  /** SM4 密钥（32位Hex），仅存内存 */
  sm4Key?: string
  /** HMAC-SM3 签名 Key，仅存内存 */
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

/** Tab-scoped random ID. It is not secret; keys remain memory-only. */
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

let securityConfig: ClientSecurityConfig = {}

export function getClientId(): string {
  return _clientId
}

export function setSecurityConfig(next: ClientSecurityConfig): void {
  securityConfig = { ...securityConfig, ...next }
}

export function getSecurityConfig(): Readonly<ClientSecurityConfig> {
  return securityConfig
}

export function resetSecurityConfig(): void {
  clearSignKeys()
}

let refreshTimer: ReturnType<typeof setTimeout> | null = null

function stopHeartbeatTimer(): void {
  if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
}

function startHeartbeatTimer(requestFn: () => Promise<any>): void {
  stopHeartbeatTimer()
  refreshTimer = setTimeout(() => {
    clearSignKeys()
    requestSessionSignKey(requestFn).catch(() => {})
  }, 20 * 60 * 1000)
}

export function clearSignKeys(): void {
  stopHeartbeatTimer()
  securityConfig = {
    sm4EncryptEnabled: securityConfig.sm4EncryptEnabled,
    sm3SignEnabled: securityConfig.sm3SignEnabled,
    sm2SignEnabled: securityConfig.sm2SignEnabled,
  }
  sessionSignPromise = null
  // 自增代次，让进行中的旧协商 Promise resolve 时不再写入（避免覆盖新协商结果）
  sessionSignGeneration += 1
}

/**
 * 强制重置所有安全配置（包括开关状态与密钥），并清空会话协商 Promise 缓存。
 * 适用于应用启动时需从服务端重新拉取最新安全开关的场景（如管理员在 PC 端修改了安全配置）。
 */
export function resetSecurityConfigAndPromise(): void {
  stopHeartbeatTimer()
  securityConfig = {}
  sessionSignPromise = null
  // 自增代次，丢弃进行中的旧协商 Promise 写入
  sessionSignGeneration += 1
}

let sessionSignPromise: Promise<any> | null = null
// 代次计数器：每次 reset/clearSignKeys 自增，用于丢弃"过期 Promise"在 resolve 时的写入，
// 避免旧的 session-sign-init 响应覆盖新的配置（造成 key 与后端 Redis 不一致的偶发 403）。
let sessionSignGeneration = 0

/**
 * 请求初始化会话签名密钥（Promise 锁防并发）。
 * @param requestFn 用于发起 POST /api/auth/session-sign-init 的函数
 */
export function requestSessionSignKey(requestFn: () => Promise<any>): Promise<any> {
  // Existing keys are valid for the current clientId until the server rejects them
  // or the heartbeat refresh clears them. sessionSignPromise is only a concurrent
  // request lock; a resolved promise must not permanently block renegotiation.
  if (securityConfig.sm3SignKey || securityConfig.sm4Key) {
    return Promise.resolve(securityConfig)
  }
  if (sessionSignPromise) return sessionSignPromise

  const myGeneration = sessionSignGeneration
  sessionSignPromise = (async () => {
    try {
      const data = await requestFn()
      // 若在 await 期间已被 reset/clear（代次变更），放弃本次写入，避免用过期响应覆盖新配置
      if (myGeneration !== sessionSignGeneration) {
        return data
      }
      if (data?.enabled) {
        if ((data.sm3SignEnabled && !data.sm3SignKey) || (data.sm4EncryptEnabled && !data.sm4Key)) {
          throw new Error('session-sign-init response missing security keys')
        }
        setSecurityConfig({
          // 服务端同步下发当前开关状态，移动端据此启用签名/加密
          sm3SignEnabled: !!data.sm3SignEnabled,
          sm4EncryptEnabled: !!data.sm4EncryptEnabled,
          sm3SignKey: data.sm3SignKey,
          sm4Key: data.sm4Key,
        })
        startHeartbeatTimer(requestFn)
      } else {
        setSecurityConfig({
          sm3SignEnabled: false,
          sm4EncryptEnabled: false,
        })
      }
      return data
    } catch (err) {
      stopHeartbeatTimer()
      throw err
    } finally {
      // 只有当前代次的 Promise 才清空锁，避免覆盖更新的协商任务
      if (myGeneration === sessionSignGeneration) {
        sessionSignPromise = null
      }
    }
  })()

  return sessionSignPromise
}
