export const TOKEN_KEY = 'cd_token'
export const USER_KEY = 'cd_username'
export const NICKNAME_KEY = 'cd_nickname'
export const ROLE_KEY = 'cd_role'

import { mediaTokenQuery } from '@/utils/mediaToken'

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

export function buildUrl(url: string) {
  if (/^https?:\/\//.test(url)) return url
  const path = url.startsWith('/') ? url : `/${url}`
  const origin = apiOrigin()
  return origin ? `${origin}${path}` : path
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

function getMessage(data: unknown, fallback: string, status?: number) {
  if (data && typeof data === 'object') {
    const body = data as { error?: string; message?: string }
    if (body.error) return translateMessage(body.error, status)
    if (body.message) return translateMessage(body.message, status)
  }
  return translateMessage(fallback, status)
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
        const message = getMessage(res.data, `请求失败 (${status})`, status)
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
  onTaskCreated?: (task: UniApp.UploadTask) => void
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
  const join = path.includes('?') ? '&' : '?'
  return `${buildUrl(path)}${join}access_token=${token}`
}
