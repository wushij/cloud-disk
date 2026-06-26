import axios from 'axios'
import { getSessionBearer } from '@/api/sessionAuth'
import { currentMediaToken } from '@/utils/mediaToken'
import { appendQueryParam } from '@/utils/mediaUrl'

/** 带 Cookie / Bearer / media-token 拉取媒体二进制 */
export async function fetchMediaBlob(url: string): Promise<Blob> {
  let path = url.replace(/^https?:\/\/[^/]+/, '') || url
  if (!path.includes('access_token=') && currentMediaToken()) {
    path = appendQueryParam(path, 'access_token', currentMediaToken())
  }
  const headers: Record<string, string> = {}
  const bearer = getSessionBearer()
  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`
  }
  const res = await axios.get(path, {
    responseType: 'blob',
    withCredentials: true,
    headers,
    validateStatus: (s) => s >= 200 && s < 300
  })
  return res.data as Blob
}
