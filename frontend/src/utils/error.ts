import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

/** 与后端 ApiErrorResponse 对齐 */
export interface ApiErrorBody {
  error?: string
  message?: string
  code?: string
  status?: number
  path?: string
  timestamp?: string
}

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 为 true 时跳过全局错误 Toast（由调用方自行处理） */
    skipErrorHandler?: boolean
  }
}

let lastToast = { message: '', at: 0 }
const DEDUPE_MS = 2000

/** 常见英文错误文案 → 中文（用户可见） */
const EN_MESSAGE_MAP: Record<string, string> = {
  'Network Error': '网络连接失败，请检查网络后重试',
  'timeout of 0ms exceeded': '请求超时，请稍后重试',
  'Request aborted': '请求已取消',
  'Failed to fetch': '网络连接失败，请检查网络后重试',
  cancel: '',
  close: ''
}

function statusToMessage(status: number, fallback = '操作失败'): string {
  switch (status) {
    case 400:
      return '请求参数有误'
    case 401:
      return '未登录或登录已过期，请重新登录'
    case 403:
      return '没有权限执行此操作'
    case 404:
      return '请求的资源不存在'
    case 405:
      return '不支持该操作'
    case 408:
      return '请求超时，请稍后重试'
    case 413:
      return '上传文件过大'
    case 429:
      return '请求过于频繁，请稍后再试'
    case 500:
      return '服务器错误，请稍后重试'
    case 502:
    case 503:
    case 504:
      return '服务暂时不可用，请稍后重试'
    default:
      return fallback
  }
}

/** 将英文/技术向文案转为中文用户提示 */
export function toUserMessage(message: string, fallback = '操作失败'): string {
  const text = message.trim()
  if (!text) return fallback

  if (/^cancel$|^close$/i.test(text)) return ''

  if (EN_MESSAGE_MAP[text]) return EN_MESSAGE_MAP[text]

  const statusFromAxios = text.match(/request failed with status code (\d+)/i)
  if (statusFromAxios) {
    return statusToMessage(Number(statusFromAxios[1]), fallback)
  }

  // 纯英文技术信息不直接展示给用户
  if (/^[\x00-\x7F]+$/.test(text) && /error|failed|request|network|timeout|abort|invalid|unexpected|denied|forbidden|not found/i.test(text)) {
    return fallback
  }

  return text
}

export function showErrorToast(message: string) {
  const text = toUserMessage(message)
  if (!text) return
  const now = Date.now()
  if (text === lastToast.message && now - lastToast.at < DEDUPE_MS) return
  lastToast = { message: text, at: now }
  ElMessage.error(text)
}

export function showWarningToast(message: string) {
  const text = toUserMessage(message, '请注意')
  if (!text) return
  ElMessage.warning(text)
}

export function showSuccessToast(message: string) {
  const text = message.trim()
  if (!text) return
  ElMessage.success(text)
}

/** Element Plus MessageBox 等用户主动取消，不应当作错误提示 */
export function isBenignUserCancel(err: unknown): boolean {
  if (err === 'cancel' || err === 'close') return true
  if (typeof err === 'object' && err !== null) {
    const action = (err as { action?: string }).action
    if (action === 'cancel' || action === 'close') return true
  }
  return false
}

export function getApiErrorMessage(err: unknown, fallback = '操作失败'): string {
  if (isBenignUserCancel(err)) return ''

  if (axios.isAxiosError(err)) {
    const ax = err as AxiosError<ApiErrorBody>
    const data = ax.response?.data

    if (data?.error) return toUserMessage(data.error, fallback)
    if (data?.message) return toUserMessage(data.message, fallback)

    if (!ax.response) {
      if (ax.code === 'ECONNABORTED') return '请求超时，请稍后重试'
      if (ax.code === 'ERR_NETWORK') return '网络连接失败，请检查网络后重试'
      return toUserMessage(ax.message || '', '网络连接失败，请检查网络后重试')
    }

    return statusToMessage(ax.response.status, fallback)
  }

  if (err instanceof Error) {
    return toUserMessage(err.message, fallback)
  }

  if (typeof err === 'string') {
    return toUserMessage(err, fallback)
  }

  return fallback
}

export function shouldShowGlobalError(err: unknown): boolean {
  const ax = err as AxiosError
  if (ax.config?.skipErrorHandler) return false
  return true
}

export function handleHttpError(err: unknown): never {
  if (shouldShowGlobalError(err)) {
    showErrorToast(getApiErrorMessage(err))
  }
  return Promise.reject(err) as never
}
