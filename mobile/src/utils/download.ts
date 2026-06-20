import { fileApiUrl, TOKEN_KEY } from '@/api/http'

/**
 * 移动端打包下载。
 * H5 环境使用 fetch+blob，能正确解析后端 JSON 错误并弹出 Toast；
 * 原生 App 环境使用 uni.downloadFile。
 */
export function downloadZip(path: string) {
  const url = fileApiUrl(path)
  // #ifdef H5
  h5Download(url)
  // #endif
  // #ifndef H5
  nativeDownload(url)
  // #endif
}

function h5Download(url: string) {
  const token = uni.getStorageSync(TOKEN_KEY) || ''
  fetch(url, {
    headers: { Authorization: `Bearer ${token}` }
  }).then(async (resp) => {
    if (!resp.ok) {
      let msg = ''
      try {
        const body = await resp.json()
        msg = body?.error || body?.message || ''
      } catch { /* non-json */ }
      uni.showToast({ title: msg || '下载失败', icon: 'none' })
      return
    }
    const blob = await resp.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    // 尝试从 Content-Disposition 解析文件名
    const disposition = resp.headers.get('Content-Disposition') || ''
    const nameMatch = disposition.match(/filename\*=(?:UTF-8|utf-8)''(.+?)(?:;|$)/i)
    a.download = nameMatch ? decodeURIComponent(nameMatch[1].trim()) : '打包下载.zip'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
  }).catch(() => {
    uni.showToast({ title: '网络异常，请检查网络后重试', icon: 'none' })
  })
}

function nativeDownload(url: string) {
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) {
        uni.saveFile({
          tempFilePath: res.tempFilePath,
          success: () => {
            uni.showToast({ title: '已保存到本地', icon: 'success' })
          }
        })
      } else {
        let msg = '下载失败'
        try {
          // uni.downloadFile 的 data 可能是字符串
          const text = typeof res.tempFilePath === 'string' ? '' : ''
          // 尝试从 header 或 data 解析错误信息
          if ((res as any).data) {
            const body = JSON.parse((res as any).data)
            msg = body?.error || body?.message || msg
          }
        } catch { /* ignore */ }
        uni.showToast({ title: msg, icon: 'none' })
      }
    },
    fail: () => {
      uni.showToast({ title: '网络异常，请检查网络后重试', icon: 'none' })
    }
  })
}
