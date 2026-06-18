import type { App } from 'vue'
import type { Router } from 'vue-router'
import axios from 'axios'
import { getApiErrorMessage, showErrorToast } from '@/utils/error'

/** 注册 Vue / Router / 未捕获 Promise 的全局错误处理 */
export function setupGlobalErrorHandlers(app: App, router: Router) {
  app.config.errorHandler = (err, _instance, info) => {
    console.error('[Vue Error]', info, err)
    showErrorToast(getApiErrorMessage(err, '页面运行异常，请刷新后重试'))
  }

  router.onError((err) => {
    console.error('[Router Error]', err)
    showErrorToast('页面加载失败，请重试')
  })

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    if (axios.isAxiosError(reason)) {
      // HTTP 错误已由 axios 拦截器处理
      return
    }
    console.error('[Unhandled Rejection]', reason)
    showErrorToast(getApiErrorMessage(reason, '发生未知错误'))
    event.preventDefault()
  })
}
