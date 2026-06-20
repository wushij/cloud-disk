/**
 * 静默修改 H5 的 URL 参数而不触发 Vue Router / uni-app 路由加载页面
 */
export function updateUrlQueryParam(params: Record<string, any>) {
  // #ifdef H5
  if (typeof window === 'undefined' || !window || typeof history === 'undefined' || !history) {
    return
  }
  const hash = window.location.hash
  const parts = hash.split('?')
  const basePath = parts[0]
  const searchParams = new URLSearchParams(parts[1] || '')
  
  for (const [key, val] of Object.entries(params)) {
    if (val === undefined || val === null) {
      searchParams.delete(key)
    } else {
      searchParams.set(key, String(val))
    }
  }
  
  const searchStr = searchParams.toString()
  const newHash = searchStr ? `${basePath}?${searchStr}` : basePath
  const newUrl = window.location.pathname + window.location.search + newHash
  
  history.replaceState(history.state, '', newUrl)
  // #endif
}
