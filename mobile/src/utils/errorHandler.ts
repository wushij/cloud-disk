import type { App } from 'vue'
import { ApiError } from '@/api/http'

const EN_MSG_MAP: Record<string, string> = {
  'Network Error': '网络连接失败，请检查网络后重试',
  'Request aborted': '请求已取消',
  'Failed to fetch': '网络连接失败，请检查网络后重试',
  timeout: '请求超时，请稍后重试',
  cancel: '',
  canceled: '',
  close: '',
  'chooseFile:fail cancel': '',
  'chooseImage:fail cancel': '',
  'chooseVideo:fail cancel': ''
}

function humanizeError(reason: unknown): string {
  if (!reason) return '操作失败，请稍后重试'
  if (reason instanceof ApiError) return reason.message || '操作失败'
  if (typeof reason === 'string') {
    if (EN_MSG_MAP[reason] !== undefined) return EN_MSG_MAP[reason]
    if (reason.includes('cancel')) return ''
    return /[\u4e00-\u9fa5]/.test(reason) ? reason : '操作失败，请稍后重试'
  }
  if (reason instanceof Error) {
    const msg = reason.message || ''
    if (EN_MSG_MAP[msg] !== undefined) return EN_MSG_MAP[msg]
    if (msg.includes('cancel')) return ''
    if (/[\u4e00-\u9fa5]/.test(msg)) return msg
    if (/error|failed|request|network|timeout|abort|invalid|unexpected|denied|forbidden|not found|load/i.test(msg)) {
      return '操作失败，请稍后重试'
    }
    return msg || '操作失败，请稍后重试'
  }
  return '操作失败，请稍后重试'
}

/** 注册 Vue / 未捕获 Promise 的全局错误处理（uni-app 环境） */
export function setupGlobalErrorHandlers(app: App) {
  app.config.errorHandler = (err, _instance, info) => {
    console.error('[Vue Error]', info, err)
    const msg = humanizeError(err)
    if (!msg) return
    uni.showToast({ title: msg, icon: 'none' })
  }

  // #ifdef H5
  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    if (reason && typeof reason === 'object' && 'statusCode' in reason) {
      return
    }
    console.error('[Unhandled Rejection]', reason)
    const msg = humanizeError(reason)
    if (!msg) {
      event.preventDefault()
      return
    }
    uni.showToast({ title: msg, icon: 'none' })
    event.preventDefault()
  })
  // #endif
}
