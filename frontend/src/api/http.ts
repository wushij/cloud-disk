import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { getApiErrorMessage, shouldShowGlobalError, showErrorToast } from '@/utils/error'
import { getSessionBearer } from '@/api/sessionAuth'
import { generateNonce, getTimestamp, encryptSm4, decryptSm4, signHmacSm3 } from '@/utils/crypto'
import {
  getSecurityConfig,
  getClientId,
  requestSessionSignKey,
  clearSignKeys,
  setSecurityConfig,
} from '@/utils/security-config'

const TOKEN_KEY = 'cd_token'
const USER_KEY = 'cd_username'
const NICKNAME_KEY = 'cd_nickname'
const ROLE_KEY = 'cd_role'
const AVATAR_VERSION_KEY = 'cd_avatar_v'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 15000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(NICKNAME_KEY)
  localStorage.removeItem(ROLE_KEY)
}

function redirectToLogin() {
  const path = window.location.pathname
  if (!path.startsWith('/login') && !path.startsWith('/share')) {
    window.location.href = '/login'
  }
}

/** 公开路径：无需等待会话密钥协商 */
function isPublicUrl(url?: string): boolean {
  if (!url) return false
  const path = url.split('?')[0]
  return (
    path.includes('/api/auth/login') ||
    path.includes('/api/auth/register') ||
    path.includes('/api/auth/captcha') ||
    path.includes('/api/auth/providers') ||
    path.includes('/api/auth/session-sign-init') ||
    path.includes('/api/auth/sso') ||
    path.includes('/api/auth/ldap/login') ||
    path.includes('/share/')
  )
}

/** 签名密钥过期判断 */
function isSignKeyExpiredMessage(text?: string): boolean {
  if (!text) return false
  return text.includes('签名密钥') || text.includes('签名验证失败') || text.includes('X-Signature')
}

/** 密钥过期时重新协商并重试请求 */
async function retryRequestWithNewSignKey(cfg: any): Promise<any> {
  cfg._isRetrySign = true
  clearSignKeys()
  try {
    await requestSessionSignKey(http)
    if (cfg._rawBody !== undefined) cfg.data = cfg._rawBody
    return http(cfg)
  } catch (err) {
    return Promise.reject(err)
  }
}

// ─── 请求拦截器 ──────────────────────────────────────────────────────────────
http.interceptors.request.use(
  async (config) => {
    const customCfg = config as InternalAxiosRequestConfig & { _rawBody?: any; _isRetrySign?: boolean }

    // 保存原始 body（用于签名密钥过期重试场景）
    if (customCfg._rawBody === undefined) {
      customCfg._rawBody = config.data
    } else {
      config.data = customCfg._rawBody
    }

    const path = config.url ?? ''
    const isAuth = path.includes('/api/auth/login') || path.includes('/api/auth/register')

    // 跳过签名/加密时仍需注入 Token
    if (isAuth) {
      ;(config as any).skipErrorHandler = true
    } else {
      const bearer = getSessionBearer()
      if (bearer) config.headers.Authorization = `Bearer ${bearer}`
    }

    // ── 时间戳 + Nonce + ClientId ──────────────────────────────────
    const timestamp = getTimestamp()
    const nonce = generateNonce()
    config.headers['X-Timestamp'] = timestamp
    config.headers['X-Nonce'] = nonce
    config.headers['X-Client-Id'] = getClientId()

    // ── 竞态保护：等待会话密钥协商完成 ────────────────────────────
    if (
      !isPublicUrl(config.url) &&
      !config.url?.includes('/api/auth/session-sign-init') &&
      !config.url?.includes('/api/auth/logout')
    ) {
      const cur = getSecurityConfig()
      if (!cur.sm3SignKey && !cur.sm4Key) {
        try {
          await requestSessionSignKey(http)
        } catch {
          /* 静默忽略：密钥协商失败不阻塞业务请求 */
        }
      }
    }

    const secConfig = getSecurityConfig()
    const isFormData = config.data instanceof FormData

    // ── SM4-CBC 请求体加密 ─────────────────────────────────────────
    // 排除"修改安全配置自身"接口：避免配置热更新与请求体加解密状态之间的竞态，
    // 该接口 body 是 4 个布尔值，敏感度低且后端已跳过 ApiSecurityFilter 的加解密。
    const isSecurityConfigUpdateUrl =
      typeof config.url === 'string' && config.url.includes('/api/admin/security/config')
    const isSessionSignInitUrl =
      typeof config.url === 'string' && config.url.includes('/api/auth/session-sign-init')
    if (secConfig.sm4EncryptEnabled && secConfig.sm4Key && !isSecurityConfigUpdateUrl && !isSessionSignInitUrl) {
      config.headers['X-Accept-Encrypted'] = '1'
      if (config.data && !isFormData) {
        const plainStr =
          typeof config.data === 'string' ? config.data : JSON.stringify(config.data)
        config.data = encryptSm4(plainStr, secConfig.sm4Key)
        config.headers['X-Encrypted'] = '1'
        // 加密后 body 已变成密文字符串，axios 在 dispatchRequest 阶段对 POST/PUT/PATCH
        // 会调用 setContentType('application/x-www-form-urlencoded', false)，
        // 当 Content-Type 原本为空时会被覆盖成 x-www-form-urlencoded，
        // 后端 Spring MVC 找不到能解析 application/x-www-form-urlencoded + @RequestBody 的 converter，
        // 进而抛 HttpMediaTypeNotSupportedException(415)。
        // 显式固定为 application/json，避免这种隐性回退；明文路径无需处理。
        config.headers['Content-Type'] = 'application/json'
      }
    }

    // ── HMAC-SM3 数字签名 ──────────────────────────────────────────
    // "修改安全配置自身"接口：避免与后端配置热更新产生竞态（同上）
    const isSignEnabled =
      (secConfig.sm3SignEnabled || secConfig.sm2SignEnabled) && secConfig.sm3SignKey
    if (isSignEnabled && !isSecurityConfigUpdateUrl && !isSessionSignInitUrl) {
      let bodyStr = ''
      if (config.data && !isFormData) {
        bodyStr = typeof config.data === 'string' ? config.data : JSON.stringify(config.data)
        bodyStr = bodyStr.trim()
        if (bodyStr.startsWith('"') && bodyStr.endsWith('"') && bodyStr.length > 2) {
          bodyStr = bodyStr.substring(1, bodyStr.length - 1)
        }
      }

      // 构建完整的 URI（含 QueryString）
      let fullPath = config.url || ''
      if (fullPath.startsWith('/api/')) fullPath = fullPath.substring(4)
      else if (!fullPath.startsWith('/')) fullPath = '/' + fullPath

      if (config.params && typeof config.params === 'object') {
        const queryParts: string[] = []
        Object.entries(config.params).forEach(([key, val]) => {
          if (val !== undefined && val !== null) {
            queryParts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(val))}`)
          }
        })
        const qs = queryParts.join('&')
        if (qs) fullPath += (fullPath.includes('?') ? '&' : '?') + qs
      }
      try { fullPath = decodeURIComponent(fullPath) } catch {}

      const tokenHeader = (config.headers['Authorization'] as string) || (getSessionBearer() ? `Bearer ${getSessionBearer()}` : '')
      const tokenSummary = tokenHeader.length > 16 ? tokenHeader.substring(tokenHeader.length - 16) : tokenHeader

      const signContent = `${config.method?.toUpperCase()}\n${fullPath}\n${timestamp}\n${nonce}\n${getClientId()}\n${tokenSummary}\n${bodyStr}`
      const signature = signHmacSm3(signContent, secConfig.sm3SignKey || '')
      if (signature) config.headers['X-Signature'] = signature
    }

    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  },
)

// ─── 响应拦截器 ──────────────────────────────────────────────────────────────
function isPublicAuthRequest(url: string) {
  return /\/api\/auth\/(login|register|captcha|providers|ldap\/login|sso)/.test(url)
}

http.interceptors.response.use(
  (response) => {
    const secConfig = getSecurityConfig()

    // ── SM4-CBC 响应体解密 ──────────────
    // 仅依赖明确的 x-encrypted 响应头，避免对明文字符串响应发起误解密
    const isEncrypted = response?.headers?.['x-encrypted'] === '1'
    if (isEncrypted && typeof response.data === 'string' && secConfig.sm4Key) {
      try {
        const plain = decryptSm4(response.data, secConfig.sm4Key)
        try { response.data = JSON.parse(plain) } catch { response.data = plain }
      } catch (decryptErr) {
        console.error('SM4 响应解密失败:', decryptErr)
        return Promise.reject(new Error('响应解密失败，数据可能已被篡改'))
      }
    }

    return response
  },
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ''
      const onLoginPage = window.location.pathname.startsWith('/login')
      const isPublic = isPublicAuthRequest(url) || (error.config as any)?.skipErrorHandler

      if (!isPublic && !onLoginPage && shouldShowGlobalError(error)) {
        showErrorToast(getApiErrorMessage(error, '未登录或登录已过期，请重新登录'))
      }
      if (!isPublic && !onLoginPage && !(error.config as any)?.skipErrorHandler) {
        clearAuth()
        redirectToLogin()
      }
    } else if (error.response?.status === 403) {
      // 签名密钥过期 → 自动重新协商并重试一次
      const cfg = error.config as any
      const text = getApiErrorMessage(error)
      if (isSignKeyExpiredMessage(text) && !cfg?._isRetrySign && !cfg?.url?.includes('/api/auth/session-sign-init')) {
        return retryRequestWithNewSignKey(cfg)
      }
      if (shouldShowGlobalError(error)) showErrorToast(getApiErrorMessage(error))
    } else if (shouldShowGlobalError(error)) {
      // SM4 解密错误响应体（加上 try-catch，防止解密异常抹掉原始错误消息）
      const secConfig = getSecurityConfig()
      if (
        error.response?.headers?.['x-encrypted'] === '1' &&
        error.response?.data &&
        typeof error.response.data === 'string' &&
        secConfig.sm4Key
      ) {
        try {
          const plain = decryptSm4(error.response.data, secConfig.sm4Key)
          try { error.response.data = JSON.parse(plain) } catch { error.response.data = plain }
        } catch (decryptErr) {
          console.warn('错误响应体解密失败，保留原响应:', decryptErr)
        }
      }
      showErrorToast(getApiErrorMessage(error))
    }
    return Promise.reject(error)
  },
)

/**
 * 初始化安全配置开关（应用启动时调用 /api/auth/config 后调用此方法）
 * 告知前端服务端是否启用了签名/加密，再按需获取密钥
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
  // 若任意加密开关打开，立即触发密钥协商
  if (serverConfig.sm3SignEnabled || serverConfig.sm2SignEnabled || serverConfig.sm4EncryptEnabled) {
    requestSessionSignKey(http).catch(() => {})
  }
}

const HAS_AVATAR_KEY = 'cd_has_avatar'

export { TOKEN_KEY, USER_KEY, NICKNAME_KEY, ROLE_KEY, AVATAR_VERSION_KEY, HAS_AVATAR_KEY }
export default http
