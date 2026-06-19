export const TOKEN_KEY = 'cd_token'
export const USER_KEY = 'cd_username'
export const NICKNAME_KEY = 'cd_nickname'
export const ROLE_KEY = 'cd_role'

const BASE_URL = import.meta.env.VITE_API_BASE || ''

interface RequestOptions {
  url: string
  method?: UniApp.RequestOptions['method']
  data?: unknown
  header?: Record<string, string>
  skipAuth?: boolean
  skipErrorHandler?: boolean
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

function buildUrl(url: string) {
  if (/^https?:\/\//.test(url)) return url
  return `${BASE_URL}${url}`
}

function getMessage(data: unknown, fallback: string) {
  if (data && typeof data === 'object' && 'message' in data) {
    const msg = (data as { message?: string }).message
    if (msg) return msg
  }
  return fallback
}

export function request<T>(options: RequestOptions): Promise<T> {
  const token = uni.getStorageSync(TOKEN_KEY)
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    ...options.header
  }
  if (!options.skipAuth && token) {
    header.Authorization = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl(options.url),
      method: options.method || 'GET',
      data: options.data as UniApp.RequestOptions['data'],
      header,
      success: (res) => {
        const status = res.statusCode || 0
        if (status === 401 && !options.skipAuth) {
          uni.removeStorageSync(TOKEN_KEY)
          uni.removeStorageSync(USER_KEY)
          uni.removeStorageSync(NICKNAME_KEY)
          uni.removeStorageSync(ROLE_KEY)
          const pages = getCurrentPages()
          const current = pages[pages.length - 1]
          if (!current?.route?.includes('login')) {
            uni.reLaunch({ url: '/pages/login/index' })
          }
        }
        if (status >= 200 && status < 300) {
          resolve(res.data as T)
          return
        }
        const message = getMessage(res.data, `请求失败 (${status})`)
        if (!options.skipErrorHandler) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new ApiError(message, status, res.data))
      },
      fail: (err) => {
        if (!options.skipErrorHandler) {
          uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        }
        reject(err)
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
}): Promise<unknown> {
  const token = uni.getStorageSync(TOKEN_KEY)
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
          message = getMessage(parsed, message)
        } catch {
          /* ignore */
        }
        uni.showToast({ title: message, icon: 'none' })
        reject(new ApiError(message, res.statusCode, res.data))
      },
      fail: reject
    })
    task.onProgressUpdate?.((event) => {
      if (options.onProgress && event.totalBytesExpectedToSend > 0) {
        options.onProgress(event.totalBytesSent / event.totalBytesExpectedToSend)
      }
    })
  })
}

export function tokenQuery(): string {
  const token = uni.getStorageSync(TOKEN_KEY)
  return encodeURIComponent(token || '')
}

export function fileApiUrl(path: string): string {
  const token = tokenQuery()
  const join = path.includes('?') ? '&' : '?'
  return `${buildUrl(path)}${join}access_token=${token}`
}
