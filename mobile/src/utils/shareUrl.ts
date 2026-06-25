/** 对外分享的统一链接：与 PC 端一致，形如 https://domain/share/{code} */
export function buildPublicShareUrl(shareCode: string, shareUrl?: string | null): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  if (shareUrl) {
    if (shareUrl.startsWith('http')) return shareUrl
    return `${origin}${shareUrl}`
  }
  return `${origin}/share/${encodeURIComponent(shareCode)}`
}

/** 从浏览器地址栏解析分享码（支持 /share/{code} 与旧版 hash 链接） */
export function parsePublicShareCodeFromLocation(): string | null {
  if (typeof window === 'undefined') return null

  const pathname = window.location.pathname || ''
  const pathMatch = pathname.match(/^\/share\/([^/?#]+)/)
  if (pathMatch?.[1]) return decodeURIComponent(pathMatch[1])

  const hash = window.location.hash || ''
  const hashMatch = hash.match(/[?&]code=([^&]+)/)
  if (hashMatch?.[1]) return decodeURIComponent(hashMatch[1])

  return null
}

/** H5：访问 /share/{code} 时跳转到分享页 */
export function redirectPublicSharePathIfNeeded(): void {
  if (typeof window === 'undefined') return
  const code = parsePublicShareCodeFromLocation()
  if (!code) return
  if (!/^\/share\/[^/]+/.test(window.location.pathname || '')) return
  uni.reLaunch({ url: `/pages/share/view?code=${encodeURIComponent(code)}` })
}
