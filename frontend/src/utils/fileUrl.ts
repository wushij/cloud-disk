import http from '@/api/http'
import { mediaTokenParam } from '@/utils/mediaToken'

/** 经后端鉴权的预览地址（本地存储 / MinIO 无直链时均适用） */
export function filePreviewUrl(fileId: number): string {
  return `/api/files/${fileId}/preview?access_token=${mediaTokenParam()}`
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
