/** 对外分享的统一链接：与移动端一致，形如 https://domain/share/{code} */
export function buildPublicShareUrl(shareCode: string, shareUrl?: string | null): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  if (shareUrl) {
    if (shareUrl.startsWith('http')) return shareUrl
    return `${origin}${shareUrl}`
  }
  return `${origin}/share/${encodeURIComponent(shareCode)}`
}

/** 兼容旧版移动端 hash 分享链接，重定向到 /share/{code} */
export function redirectLegacyMobileShareLink(): void {
  if (typeof window === 'undefined') return
  const hash = window.location.hash || ''
  const m = hash.match(/^#\/pages\/share\/view\?(?:.*&)?code=([^&]+)/)
    || hash.match(/^#\/pages\/share\/view\?code=([^&]+)/)
  if (!m?.[1]) return
  const code = decodeURIComponent(m[1])
  window.location.replace(buildPublicShareUrl(code))
}
