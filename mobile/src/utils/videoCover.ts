/** H5 从本地视频截取首帧，上传后即时展示封面 */
export async function captureVideoCoverFromPath(
  filePath: string,
  timeSec = 1,
  h5File?: File | Blob
): Promise<string> {
  // #ifdef H5
  let objectUrl = ''
  const src = h5File instanceof Blob ? (objectUrl = URL.createObjectURL(h5File)) : filePath
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    video.preload = 'auto'
    video.muted = true
    video.playsInline = true
    video.crossOrigin = 'anonymous'

    const cleanup = () => {
      video.removeAttribute('src')
      video.load()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }

    video.onerror = () => {
      cleanup()
      reject(new Error('video load failed'))
    }

    video.onloadeddata = () => {
      const duration = Number.isFinite(video.duration) ? video.duration : timeSec
      video.currentTime = duration > 0 ? Math.min(timeSec, Math.max(0, duration - 0.1)) : 0
    }

    video.onseeked = () => {
      try {
        const w = video.videoWidth
        const h = video.videoHeight
        if (!w || !h) {
          cleanup()
          reject(new Error('empty video frame'))
          return
        }
        const max = 320
        const scale = Math.min(1, max / Math.max(w, h))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(w * scale))
        canvas.height = Math.max(1, Math.round(h * scale))
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          cleanup()
          reject(new Error('canvas unavailable'))
          return
        }
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
        const dataUrl = canvas.toDataURL('image/jpeg', 0.82)
        cleanup()
        resolve(dataUrl)
      } catch (err) {
        cleanup()
        reject(err)
      }
    }

    video.src = src
  })
  // #endif
  // #ifndef H5
  return Promise.reject(new Error('video cover only on H5'))
  // #endif
}

export function isVideoUploadName(name: string): boolean {
  const lower = (name || '').toLowerCase()
  return ['.mp4', '.webm', '.mkv', '.avi', '.mov'].some((ext) => lower.endsWith(ext))
}
