/** 从本地视频文件截取首帧，用于上传后即时展示封面 */
export async function captureVideoCover(file: File, timeSec = 1): Promise<string> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    video.preload = 'auto'
    video.muted = true
    video.playsInline = true
    const objectUrl = URL.createObjectURL(file)

    const cleanup = () => URL.revokeObjectURL(objectUrl)

    video.onerror = () => {
      cleanup()
      reject(new Error('video load failed'))
    }

    video.onloadeddata = () => {
      const duration = Number.isFinite(video.duration) ? video.duration : timeSec
      const seekTo = duration > 0 ? Math.min(timeSec, Math.max(0, duration - 0.1)) : 0
      video.currentTime = seekTo
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

    video.src = objectUrl
  })
}

export function isVideoUpload(file: File): boolean {
  if ((file.type || '').toLowerCase().startsWith('video/')) return true
  const name = file.name.toLowerCase()
  return ['.mp4', '.webm', '.mkv', '.avi', '.mov'].some((ext) => name.endsWith(ext))
}
