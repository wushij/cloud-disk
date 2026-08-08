export const USER_KEY = 'cd_username'
export const NICKNAME_KEY = 'cd_nickname'
export const ROLE_KEY = 'cd_role'

import { getSessionBearer, setSessionBearer, clearLegacyToken } from '@/api/sessionAuth'
import { mediaTokenQuery } from '@/utils/mediaToken'
import { useAuthStore } from '@/stores/auth'
import { generateNonce, getTimestamp, encryptSm4, decryptSm4, signHmacSm3 } from '@/utils/crypto'
import {
  getSecurityConfig,
  getClientId,
  setSecurityConfig,
  requestSessionSignKey,
  clearSignKeys,
} from '@/utils/security-config'

const BASE_URL = import.meta.env.VITE_API_BASE || ''

function apiOrigin(): string {
  const base = BASE_URL.trim()
  if (/^https?:\/\//.test(base)) return base.replace(/\/$/, '')
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return ''
}

interface RequestOptions {
  url: string
  method?: UniApp.RequestOptions['method']
  data?: unknown
  params?: Record<string, unknown>
  header?: Record<string, string>
  skipAuth?: boolean
  skipErrorHandler?: boolean
  _isRetrySign?: boolean
  /** 毫秒；大文件 merge 等长耗时接口需显式加长 */
  timeout?: number
}

export class ApiError extends Error {
  statusCode: number
  data: unknown

  constructor(message: string, statusCode: number, data?: unknown) {
    super(message)
    this.statusCode = statusCode
    this.data = data
  }
}

export function buildUrl(url: string) {
  if (/^https?:\/\//.test(url)) return url
  const path = url.startsWith('/') ? url : `/${url}`
  const origin = apiOrigin()
  return origin ? `${origin}${path}` : path
}

function appendQuery(url: string, params?: Record<string, unknown>): string {
  if (!params) return url
  const parts: string[] = []
  Object.entries(params).forEach(([key, val]) => {
    if (val !== undefined && val !== null && val !== '') {
      parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(val))}`)
    }
  })
  if (!parts.length) return url
  return `${url}${url.includes('?') ? '&' : '?'}${parts.join('&')}`
}

/** 常见英文错误消息 → 中文翻译 */
const EN_MSG_MAP: Record<string, string> = {
  'Network Error': '网络连接失败，请检查网络后重试',
  'Request aborted': '请求已取消',
  'Failed to fetch': '网络连接失败，请检查网络后重试',
  'timeout': '请求超时，请稍后重试',
  'cancel': '',
  'canceled': '',
  'close': ''
}

const STATUS_MSG_MAP: Record<number, string> = {
  400: '请求参数有误',
  401: '未登录或登录已过期，请重新登录',
  403: '没有权限执行此操作',
  404: '请求的资源不存在',
  405: '不支持该操作',
  408: '请求超时，请稍后重试',
  413: '上传文件过大',
  429: '请求过于频繁，请稍后再试',
  500: '服务器错误，请稍后重试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
  504: '服务暂时不可用，请稍后重试'
}

function translateMessage(msg: string, status?: number): string {
  const text = msg.trim()
  if (!text) return status ? (STATUS_MSG_MAP[status] || '操作失败') : '操作失败'
  // 直接命中英文映射
  if (EN_MSG_MAP[text] !== undefined) return EN_MSG_MAP[text]
  // 纯英文技术信息不直接展示
  if (/^[\x00-\x7F]+$/.test(text) && /error|failed|request|network|timeout|abort|invalid|unexpected|denied|forbidden|not found/i.test(text)) {
    return status ? (STATUS_MSG_MAP[status] || '操作失败') : '操作失败'
  }
  return text
}

/** 从各类异常对象提取可读上传/请求错误文案 */
export function resolveErrorMessage(e: unknown, fallback = '操作失败'): string {
  if (e instanceof ApiError && e.message) return e.message
  if (e instanceof Error) {
    if (!e.message || e.message === 'Canceled') return fallback
    if (/[\u4e00-\u9fa5]/.test(e.message)) return e.message
    return translateMessage(e.message, e instanceof ApiError ? e.statusCode : undefined)
  }
  if (e && typeof e === 'object') {
    const errMsg = String((e as { errMsg?: string }).errMsg || '')
    if (errMsg) {
      if (/timeout/i.test(errMsg)) return '请求超时，大文件处理时间较长，请稍后重试'
      if (/request:fail/i.test(errMsg)) return '网络请求失败，请检查网络与后端服务'
      return translateMessage(errMsg)
    }
  }
  return fallback
}

function getMessage(data: unknown, fallback: string, status?: number) {
  if (data && typeof data === 'object') {
    const body = data as { error?: string; message?: string }
    if (body.error) return translateMessage(body.error, status)
    if (body.message) return translateMessage(body.message, status)
  }
  return translateMessage(fallback, status)
}

/** 优先 sessionStorage，回退 Pinia，避免 HMR/多标签页导致 Bearer 丢失 */
export function resolveBearer(): string | null {
  const cached = getSessionBearer()
  if (cached) return cached
  try {
    const auth = useAuthStore()
    const fromStore = auth.token?.trim()
    if (fromStore) {
      setSessionBearer(fromStore)
      return fromStore
    }
  } catch {
    /* Pinia 尚未就绪 */
  }
  return null
}

function clearSessionOnUnauthorized(hadToken: boolean) {
  if (!hadToken) return
  try {
    useAuthStore().clearSessionLocal()
  } catch {
    setSessionBearer(null)
    clearLegacyToken()
    uni.removeStorageSync(USER_KEY)
    uni.removeStorageSync(NICKNAME_KEY)
    uni.removeStorageSync(ROLE_KEY)
    uni.removeStorageSync('cd_avatar_version')
    uni.removeStorageSync('cd_has_avatar')
  }
}

function redirectToLoginIfNeeded() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  if (!current?.route?.includes('login')) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
}

export async function request<T>(options: RequestOptions): Promise<T> {
  const token = options.skipAuth ? null : resolveBearer()

  // ── 竞态保护：若未就绪，自动 await 协商会话签名密钥（防并发导致缺失签名头 403）───
  if (
    !options.url?.includes('/api/auth/session-sign-init') &&
    !options.url?.includes('/api/auth/logout') &&
    !options.url?.includes('/api/auth/config')
  ) {
    const curSec = getSecurityConfig()
    if (!curSec.sm3SignKey && !curSec.sm4Key) {
      const requestFn = () =>
        request<any>({
          url: `/api/auth/session-sign-init?clientId=${getClientId()}`,
          method: 'POST',
          data: undefined,
          skipErrorHandler: true,
        } as any)
      try {
        await requestSessionSignKey(requestFn)
      } catch {
        /* 静默忽略：密钥协商失败不阻塞业务请求 */
      }
    }
  }

  const secConfig = getSecurityConfig()

  // ── 时间戳 + Nonce + ClientId ──────────────────────────────────
  const timestamp = getTimestamp()
  const nonce = generateNonce()

  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Client-Id': getClientId(),
    ...options.header,
  }
  if (!options.skipAuth && token) {
    header.Authorization = `Bearer ${token}`
  }

  // ── SM4-CBC 请求体加密 ─────────────────────────────────────────
  // 排除"修改安全配置自身"接口：避免配置热更新与请求体加解密状态之间的竞态。
  // 后端 ApiSecurityFilter 对 /api/admin/security/config 已早退放行，
  // 这里也保持请求体明文 JSON 形态，便于 controller 直接 ObjectMapper 解析写入 Redis。
  const isSecurityConfigUpdateUrl =
    typeof options.url === 'string' && options.url.includes('/api/admin/security/config')
  const isSessionSignInitUrl =
    typeof options.url === 'string' && options.url.includes('/api/auth/session-sign-init')
  let requestData: unknown = options.data
  let requestUrl = options.url
  const requestMethod = (options.method || 'GET').toUpperCase()
  if (requestMethod === 'GET') {
    const queryParams: Record<string, unknown> = { ...(options.params || {}) }
    if (requestData && typeof requestData === 'object' && !Array.isArray(requestData)) {
      Object.assign(queryParams, requestData as Record<string, unknown>)
    }
    requestUrl = appendQuery(requestUrl, queryParams)
    requestData = undefined
  } else if (options.params) {
    requestUrl = appendQuery(requestUrl, options.params)
  }
  let isEncrypted = false
  if (secConfig.sm4EncryptEnabled && secConfig.sm4Key && requestData && !isSecurityConfigUpdateUrl && !isSessionSignInitUrl) {
    try {
      const plain = typeof requestData === 'string' ? requestData : JSON.stringify(requestData)
      requestData = encryptSm4(plain, secConfig.sm4Key)
      header['X-Encrypted'] = '1'
      header['X-Accept-Encrypted'] = '1'
      isEncrypted = true
    } catch (err) {
      console.error('SM4 加密失败:', err)
    }
  } else if (secConfig.sm4EncryptEnabled && secConfig.sm4Key && !isSessionSignInitUrl) {
    header['X-Accept-Encrypted'] = '1'
  }

  // ── HMAC-SM3 数字签名 ──────────────────────────────────────────
  const isSignEnabled = (secConfig.sm3SignEnabled || secConfig.sm2SignEnabled) && secConfig.sm3SignKey
  if (isSignEnabled && !isSecurityConfigUpdateUrl && !isSessionSignInitUrl) {
    const methodUpper = requestMethod
    let bodyStr = ''
    if (requestData && !isEncrypted && methodUpper !== 'GET') {
      bodyStr = typeof requestData === 'string' ? requestData : JSON.stringify(requestData)
      bodyStr = bodyStr.trim()
      if (bodyStr.startsWith('"') && bodyStr.endsWith('"') && bodyStr.length > 2) {
        bodyStr = bodyStr.substring(1, bodyStr.length - 1)
      }
    } else if (isEncrypted && requestData && methodUpper !== 'GET') {
      bodyStr = typeof requestData === 'string' ? requestData : ''
    }

    // 构建完整的 URI（与 PC 前端及后端保持一致：剥离协议与 /api 前缀，规范化 query）
    let pathOnly = requestUrl || ''
    if (/^https?:\/\//i.test(pathOnly)) {
      const match = pathOnly.match(/^https?:\/\/[^\/]+(\/.*)?$/i)
      pathOnly = (match && match[1]) ? match[1] : '/'
    }

    let existingQs = ''
    const qIndex = pathOnly.indexOf('?')
    if (qIndex !== -1) {
      existingQs = pathOnly.substring(qIndex + 1)
      pathOnly = pathOnly.substring(0, qIndex)
    }

    if (pathOnly.startsWith('/api/')) {
      pathOnly = pathOnly.substring(4)
    } else if (pathOnly === '/api') {
      pathOnly = '/'
    } else if (!pathOnly.startsWith('/')) {
      pathOnly = '/' + pathOnly
    }

    let fullPath = pathOnly

    const queryObj: Record<string, any> = {}
    if (existingQs) {
      existingQs.split('&').forEach((pair) => {
        const [k, v] = pair.split('=')
        if (k) {
          try {
            queryObj[decodeURIComponent(k)] = v ? decodeURIComponent(v) : ''
          } catch {
            queryObj[k] = v || ''
          }
        }
      })
    }
    const queryParts: string[] = []
    Object.entries(queryObj).forEach(([key, val]) => {
      if (val !== undefined && val !== null) {
        queryParts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(val))}`)
      }
    })
    const qs = queryParts.join('&')
    if (qs) fullPath += '?' + qs
    try { fullPath = decodeURIComponent(fullPath) } catch {}

    const tokenHeader = (header['Authorization'] as string) || (token ? `Bearer ${token}` : '')
    const tokenSummary = tokenHeader.length > 16 ? tokenHeader.substring(tokenHeader.length - 16) : tokenHeader

    const signContent = `${requestMethod}\n${fullPath}\n${timestamp}\n${nonce}\n${getClientId()}\n${tokenSummary}\n${bodyStr}`
    const signature = signHmacSm3(signContent, secConfig.sm3SignKey || '')
    if (signature) header['X-Signature'] = signature
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl(requestUrl),
      method: options.method || 'GET',
      data: requestData as UniApp.RequestOptions['data'],
      header,
      timeout: options.timeout,
      success: (res) => {
        const status = res.statusCode || 0
        if (status === 401 && !options.skipAuth) {
          clearSessionOnUnauthorized(!!token)
          redirectToLoginIfNeeded()
        }

        // ── SM4-CBC 响应解密 ─────────────────────────────────────
        let resData = res.data
        const resHeader = res.header || {}
        const resEncrypted =
          resHeader['x-encrypted'] === '1' ||
          resHeader['X-Encrypted'] === '1'
        if (resEncrypted && typeof resData === 'string' && secConfig.sm4Key) {
          try {
            const plain = decryptSm4(resData, secConfig.sm4Key)
            try { resData = JSON.parse(plain) } catch { resData = plain }
          } catch (decryptErr) {
            console.error('移动端 SM4 响应解密失败:', decryptErr)
          }
        }

        if (status >= 200 && status < 300) {
          if (resData && typeof resData === 'object' && 'code' in (resData as any)) {
            if ((resData as any).code === 0) {
              resData = (resData as any).data
            }
          }
          resolve(resData as T)
          return
        }

        // ── 签名密钥失效 / 验证失败：自动清空密钥、重新协商并重试请求 ───────
        if (status === 403) {
          const msg = getMessage(resData, '', status)
          // 响应体被 SM4 加密但解密失败时，resData 是密文字符串，msg 不含"签名"关键字，
          // 因此不仅检查关键字，403 + 非 session-sign-init 请求都尝试重新协商。
          const isLikelySignError =
            msg.includes('签名') ||
            msg.includes('X-Signature') ||
            msg.includes('数字签名') ||
            typeof resData === 'string' // 密文场景：resData 是字符串（解密失败）
          if (
            isLikelySignError &&
            !options._isRetrySign &&
            !options.url?.includes('/api/auth/session-sign-init')
          ) {
            options._isRetrySign = true
            clearSignKeys()
            const requestFn = () =>
              request<any>({
                url: `/api/auth/session-sign-init?clientId=${getClientId()}`,
                method: 'POST',
                data: undefined,
                skipErrorHandler: true,
              } as any)
            requestSessionSignKey(requestFn)
              .then(() => {
                request<T>(options).then(resolve).catch(reject)
              })
              .catch(() => {
                const message = getMessage(resData, `请求失败 (${status})`, status)
                if (!options.skipErrorHandler) {
                  uni.showToast({ title: message, icon: 'none' })
                }
                reject(new ApiError(message, status, resData))
              })
            return
          }
        }

        const message = getMessage(resData, `请求失败 (${status})`, status)
        if (!options.skipErrorHandler) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new ApiError(message, status, resData))
      },
      fail: (err) => {
        const message = resolveErrorMessage(err, '网络异常，请稍后重试')
        if (!options.skipErrorHandler) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new ApiError(message, 0, err))
      }
    })
  })
}

export function uploadFile(options: {
  url: string
  filePath: string
  name?: string
  formData?: Record<string, string>
  onProgress?: (ratio: number) => void
  onTaskCreated?: (task: UniApp.UploadTask) => void
}): Promise<unknown> {
  const token = resolveBearer()
  return new Promise((resolve, reject) => {
    const task = uni.uploadFile({
      url: buildUrl(options.url),
      filePath: options.filePath,
      name: options.name || 'file',
      formData: options.formData,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(res.data))
          } catch {
            resolve(res.data)
          }
          return
        }
        let message = `上传失败 (${res.statusCode})`
        try {
          const parsed = JSON.parse(res.data)
          message = getMessage(parsed, message, res.statusCode)
        } catch {
          /* ignore */
        }
        uni.showToast({ title: message, icon: 'none' })
        reject(new ApiError(message, res.statusCode, res.data))
      },
      fail: reject
    })
    options.onTaskCreated?.(task)
    task.onProgressUpdate?.((event) => {
      if (options.onProgress && event.totalBytesExpectedToSend > 0) {
        options.onProgress(event.totalBytesSent / event.totalBytesExpectedToSend)
      }
    })
  })
}

export function tokenQuery(): string {
  return mediaTokenQuery()
}

export async function refreshMediaAccessToken(): Promise<string> {
  const { refreshMediaToken } = await import('@/utils/mediaToken')
  return refreshMediaToken()
}

export function fileApiUrl(path: string): string {
  if (/[?&]access_token=/.test(path)) {
    return buildUrl(path)
  }
  const token = mediaTokenQuery()
  if (!token) {
    // 媒体 token 未就绪时不拼空的 access_token=，避免后端 400
    return buildUrl(path)
  }
  const join = path.includes('?') ? '&' : '?'
  return `${buildUrl(path)}${join}access_token=${token}`
}

/**
 * 初始化安全配置开关（应用启动时调用，从服务端获取加密开关状态）
 */
export function applySecurityConfig(serverConfig: {
  sm3SignEnabled?: boolean
  sm2SignEnabled?: boolean
  sm4EncryptEnabled?: boolean
}) {
  setSecurityConfig({
    sm3SignEnabled: serverConfig.sm3SignEnabled,
    sm2SignEnabled: serverConfig.sm2SignEnabled,
    sm4EncryptEnabled: serverConfig.sm4EncryptEnabled,
  })
  const needKey =
    serverConfig.sm3SignEnabled || serverConfig.sm2SignEnabled || serverConfig.sm4EncryptEnabled
  if (needKey) {
    // 移动端通过 request 函数发起密钥协商
    const requestFn = () =>
      request<any>({
        url: `/api/auth/session-sign-init?clientId=${getClientId()}`,
        method: 'POST',
        data: undefined,
        skipAuth: true,
        skipErrorHandler: true,
      } as any)
    requestSessionSignKey(requestFn).catch(() => {})
  }
}
