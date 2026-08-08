import { fileApiUrl } from '@/api/http'
import { fileApi } from '@/api'

export function filePreviewUrl(fileId: number): string {
  return fileApiUrl(fileApi.previewUrl(fileId))
}

/** 优先直链，否则回退 preview（与 PC 端一致） */
export async function resolveFilePreviewUrl(fileId: number): Promise<string> {
  try {
    const data = await fileApi.directUrl(fileId)
    if (data?.url) return data.url
  } catch {
    /* 本地存储：走 preview */
  }
  return filePreviewUrl(fileId)
}
