import http from '@/api/http'
import { mediaApiUrl } from '@/utils/mediaUrl'

/** 经后端鉴权的预览地址（同域 Cookie，URL 稳定） */
export function filePreviewUrl(fileId: number): string {
  return mediaApiUrl(`/api/files/${fileId}/preview`)
}

/** 优先 MinIO/CDN 直链，否则回退 preview 代理 */
export async function resolveFilePreviewUrl(fileId: number): Promise<string> {
  try {
    const { data } = await http.get<{ url?: string | null }>(`/api/files/${fileId}/direct-url`, {
      skipErrorHandler: true
    })
    if (data?.url) return data.url
  } catch {
    /* 本地存储或旧版后端：走 preview */
  }
  return filePreviewUrl(fileId)
}
