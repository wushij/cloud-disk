import { fileApiUrl, request } from '@/api/http'

export function filePreviewUrl(fileId: number): string {
  return fileApiUrl(`/api/files/${fileId}/preview`)
}

/** 优先直链，否则回退 preview（与 PC 端一致） */
export async function resolveFilePreviewUrl(fileId: number): Promise<string> {
  try {
    const data = await request<{ url?: string | null }>({
      url: `/api/files/${fileId}/direct-url`,
      skipErrorHandler: true
    })
    if (data?.url) return data.url
  } catch {
    /* 本地存储：走 preview */
  }
  return filePreviewUrl(fileId)
}
