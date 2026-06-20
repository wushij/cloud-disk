import type { App } from 'vue'

/** 注册 Vue / 未捕获 Promise 的全局错误处理（uni-app 环境） */
export function setupGlobalErrorHandlers(app: App) {
  app.config.errorHandler = (err, _instance, info) => {
    console.error('[Vue Error]', info, err)
    const msg = err instanceof Error
      ? (err.message && !/^[\x00-\x7F]+$/.test(err.message) ? err.message : '页面运行异常，请刷新后重试')
      : '页面运行异常，请刷新后重试'
    uni.showToast({ title: msg || '页面运行异常，请刷新后重试', icon: 'none' })
  }

  // 未捕获的 Promise rejection
  // uni-app 中通过全局事件监听
  // #ifdef H5
  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    // 如果已经是 ApiError，说明 HTTP 层已处理过
    if (reason && typeof reason === 'object' && 'statusCode' in reason) {
      return
    }
    console.error('[Unhandled Rejection]', reason)
    const msg = reason instanceof Error
      ? (reason.message && !/^[\x00-\x7F]+$/.test(reason.message) ? reason.message : '发生未知错误')
      : '发生未知错误'
    uni.showToast({ title: msg || '发生未知错误', icon: 'none' })
    event.preventDefault()
  })
  // #endif
}
