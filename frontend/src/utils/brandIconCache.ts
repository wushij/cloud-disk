import { ref } from 'vue'

const ICON_PATH = '/theme-brand-icon-transparent.png'
const STORAGE_KEY = 'cd_brand_icon_v1'

function readStored(): string {
  try {
    const cached = localStorage.getItem(STORAGE_KEY)
    if (cached?.startsWith('data:')) return cached
  } catch {
    /* ignore */
  }
  return ''
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

/** 登录页/侧栏 Logo 展示地址，命中缓存时为 data URL，可即时渲染 */
export const brandIconSrc = ref(readStored() || ICON_PATH)

let warming: Promise<string> | null = null

/** 首次访问拉取并持久化，后续刷新直接读本地缓存 */
export function warmBrandIconCache(): Promise<string> {
  if (brandIconSrc.value.startsWith('data:')) {
    return Promise.resolve(brandIconSrc.value)
  }
  if (warming) return warming

  warming = fetch(ICON_PATH)
    .then((res) => {
      if (!res.ok) throw new Error('brand icon fetch failed')
      return res.blob()
    })
    .then(blobToDataUrl)
    .then((dataUrl) => {
      brandIconSrc.value = dataUrl
      try {
        localStorage.setItem(STORAGE_KEY, dataUrl)
      } catch {
        /* quota exceeded — memory cache still works this session */
      }
      return dataUrl
    })
    .catch(() => brandIconSrc.value)
    .finally(() => {
      warming = null
    })

  return warming
}
