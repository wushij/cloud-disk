import axios from 'axios'
import { TOKEN_KEY } from '@/api/http'

/** 带 Bearer + Cookie 拉取媒体二进制（供封面/头像缓存使用） */
export async function fetchMediaBlob(url: string): Promise<Blob> {
  const path = url.replace(/^https?:\/\/[^/]+/, '') || url
  const token = localStorage.getItem(TOKEN_KEY)
  const res = await axios.get(path, {
    responseType: 'blob',
    withCredentials: true,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    validateStatus: (s) => s >= 200 && s < 300
  })
  return res.data as Blob
}
