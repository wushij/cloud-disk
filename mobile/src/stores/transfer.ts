import { ref, reactive, computed } from 'vue'
import { defineStore } from 'pinia'
import { smartUpload } from '@/utils/chunkedUpload'
import { fileApiUrl } from '@/api/http'
import { createTaskId } from '@/utils/uuid'
import { cacheCoverFromDataUrl } from '@/utils/coverCache'
import { captureVideoCoverFromPath, isVideoUploadName } from '@/utils/videoCover'

export interface TransferTask {
  id: string
  type: 'upload' | 'download'
  name: string
  size: number
  progress: number
  loaded: number
  speed: string
  status: 'waiting' | 'running' | 'paused' | 'done' | 'error' | 'instant'
  // 上传/下载专有参数
  filePath?: string
  folderId?: number
  fileId?: number
  /** 本地图片预览地址（上传图片时使用 filePath） */
  coverUrl?: string
  // 控制句柄
  abortController?: AbortController // 上传使用
  downloadTask?: UniApp.DownloadTask // 原生下载使用
  startTime?: number
  speedBaseLoaded?: number
  speedBaseTime?: number
  uploadId?: string
  chunkSize?: number
  totalChunks?: number
}

function formatSpeed(bytesPerSec: number): string {
  if (bytesPerSec <= 0) return '0 B/s'
  if (bytesPerSec < 1024) return `${bytesPerSec.toFixed(1)} B/s`
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(1)} KB/s`
  return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`
}

function calcTransferSpeed(
  loaded: number,
  speedBaseLoaded: number | undefined,
  speedBaseTime: number | undefined,
  fallback: string
): string {
  const baseLoaded = speedBaseLoaded ?? 0
  const baseTime = speedBaseTime ?? Date.now()
  const elapsed = (Date.now() - baseTime) / 1000
  if (elapsed < 0.5) return fallback
  const delta = loaded - baseLoaded
  if (delta <= 0) return '0 B/s'
  return formatSpeed(delta / elapsed)
}

function speedBaseline(loaded: number): Pick<TransferTask, 'speedBaseLoaded' | 'speedBaseTime'> {
  return { speedBaseLoaded: loaded, speedBaseTime: Date.now() }
}

export const useTransferStore = defineStore('transfer', () => {
  const tasks = ref<TransferTask[]>([])

  const activeTaskCount = computed(
    () => tasks.value.filter((t) => t.status === 'running' || t.status === 'waiting').length
  )

  function pushTask(data: TransferTask): TransferTask {
    const task = reactive(data) as TransferTask
    tasks.value.unshift(task)
    return task
  }

  function patchTask(taskId: string, patch: Partial<TransferTask>): TransferTask | undefined {
    const idx = tasks.value.findIndex((t) => t.id === taskId)
    if (idx === -1) return undefined
    const current = tasks.value[idx]
    Object.assign(current, patch)
    // 替换数组项以触发 uni-app 列表即时刷新
    tasks.value[idx] = current
    tasks.value = tasks.value.slice()
    return current
  }

  // 清理完成任务
  function clearCompleted() {
    tasks.value = tasks.value.filter(
      (t) => t.status !== 'done' && t.status !== 'instant' && t.status !== 'error'
    )
  }

  // 暂停上传任务
  function pauseUpload(taskId: string) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task || task.status !== 'running' || task.type !== 'upload') return

    if (task.abortController) {
      task.abortController.abort()
    }
    patchTask(taskId, { status: 'paused', speed: '已暂停' })
  }

  // 恢复上传任务（断点续传）
  async function resumeUpload(taskId: string) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task || task.status !== 'paused' || !task.filePath) return

    const baseProgress = task.progress
    const resumeLoaded = task.loaded
    const abortController = new AbortController()
    patchTask(taskId, {
      status: 'running',
      speed: '继续传输...',
      startTime: Date.now(),
      speedBaseLoaded: resumeLoaded,
      speedBaseTime: Date.now(),
      abortController
    })
    if (isVideoUploadName(task.name)) {
      void captureVideoCoverFromPath(task.filePath)
        .then((cover) => patchTask(taskId, { coverUrl: cover }))
        .catch(() => {})
    }

    try {
      await smartUpload(
        task.filePath,
        task.name,
        task.size,
        task.folderId || 0,
        undefined,
        (ratio) => {
          const current = tasks.value.find((t) => t.id === taskId)
          if (!current || current.status !== 'running') return
          const progress = Math.max(baseProgress, ratio)
          const loaded = Math.round(progress * current.size)
          patchTask(taskId, {
            progress,
            loaded,
            speed: calcTransferSpeed(loaded, current.speedBaseLoaded, current.speedBaseTime, '继续传输...')
          })
        },
        abortController.signal,
        {
          existingUploadId: task.uploadId,
          skipMd5Check: true,
          onInit: (session) => {
            patchTask(taskId, {
              uploadId: session.uploadId,
              chunkSize: session.chunkSize,
              totalChunks: session.totalChunks
            })
          }
        }
      )

      patchTask(taskId, {
        status: 'done',
        progress: 1,
        loaded: task.size,
        speed: '已完成'
      })

      uni.$emit('refresh-file-list')
    } catch (e: any) {
      if (e?.message === 'Canceled') {
        const current = tasks.value.find((t) => t.id === taskId)
        if (current?.status !== 'paused') return
      } else {
        patchTask(taskId, { status: 'error', speed: '传输失败' })
      }
    }
  }

  // 取消任务
  function cancelTask(taskId: string) {
    const idx = tasks.value.findIndex((t) => t.id === taskId)
    if (idx === -1) return
    const task = tasks.value[idx]

    if (task.type === 'upload' && task.abortController) {
      task.abortController.abort()
    } else if (task.type === 'download' && task.downloadTask) {
      try {
        task.downloadTask.abort()
      } catch (err) {
        /* ignore */
      }
    }

    tasks.value.splice(idx, 1)
  }

  // 图片扩展名
  const imageExts = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp']

  // 添加上传任务
  async function addUploadTask(
    filePath: string,
    name: string,
    size: number,
    folderId: number,
    extra?: { h5File?: File; mimeType?: string }
  ) {
    const taskId = createTaskId()
    const ext = name.split('.').pop()?.toLowerCase() || ''
    const task = pushTask({
      id: taskId,
      type: 'upload',
      name,
      size,
      progress: 0,
      loaded: 0,
      speed: '等待上传...',
      status: 'waiting',
      filePath,
      folderId,
      coverUrl: imageExts.includes(ext) ? filePath : undefined,
      abortController: new AbortController(),
      startTime: Date.now()
    })

    const isVideo =
      isVideoUploadName(name) || (extra?.mimeType || '').toLowerCase().startsWith('video/')
    if (isVideo) {
      void captureVideoCoverFromPath(filePath)
        .then((cover) => patchTask(taskId, { coverUrl: cover }))
        .catch(() => {})
    }

    try {
      task.status = 'running'
      task.startTime = Date.now()
      Object.assign(task, speedBaseline(0))

      const result = await smartUpload(
        filePath,
        name,
        size,
        folderId,
        undefined,
        (ratio) => {
          if (task.status !== 'running') return
          task.progress = ratio
          task.loaded = Math.round(ratio * size)
          task.speed = calcTransferSpeed(task.loaded, task.speedBaseLoaded, task.speedBaseTime, task.speed)
        },
        task.abortController?.signal,
        {
          h5File: extra?.h5File,
          mimeType: extra?.mimeType,
          onInit: (session) => {
            task.uploadId = session.uploadId
            task.chunkSize = session.chunkSize
            task.totalChunks = session.totalChunks
          }
        }
      )
      if (result.fileId) task.fileId = result.fileId

      if (result.fileId && isVideo) {
        try {
          const current = tasks.value.find((t) => t.id === taskId)
          let cover = current?.coverUrl
          if (!cover || !cover.startsWith('data:')) {
            cover = await captureVideoCoverFromPath(filePath)
          }
          cacheCoverFromDataUrl(result.fileId, 0, cover)
          patchTask(taskId, { coverUrl: cover, fileId: result.fileId })
        } catch {
          /* ignore */
        }
      }

      task.status = 'done'
      task.progress = 1
      task.loaded = size
      task.speed = '已完成'
      
      // 触发页面刷新事件
      uni.$emit('refresh-file-list')
    } catch (e: any) {
      if (e?.message === 'Canceled') {
        // 主动取消或暂停
      } else {
        task.status = 'error'
        task.speed = '传输失败'
        const msg =
          e instanceof Error && e.message && /[\u4e00-\u9fa5]/.test(e.message)
            ? e.message
            : '上传失败，请稍后重试'
        uni.showToast({ title: msg, icon: 'none' })
      }
    }
  }

  // 添加下载任务
  async function addDownloadTask(fileId: number, name: string, size: number) {
    const taskId = createTaskId()
    const task = pushTask({
      id: taskId,
      type: 'download',
      name,
      size,
      progress: 0,
      loaded: 0,
      speed: '等待下载...',
      status: 'waiting',
      fileId,
      startTime: Date.now()
    })

    const url = fileApiUrl(`/api/files/${fileId}/download`)

    // #ifdef H5
    // H5 环境：直接打开窗口或 XHR 流式下载
    if (size >= 100 * 1024 * 1024) {
      // 超大文件直接使用浏览器接管
      task.status = 'running'
      task.progress = 0.5
      task.speed = '浏览器接管'
      window.open(url, '_blank')
      
      setTimeout(() => {
        task.status = 'done'
        task.progress = 1
        task.loaded = size
        task.speed = '下载已开始'
      }, 1000)
    } else {
      task.status = 'running'
      task.startTime = Date.now()
      Object.assign(task, speedBaseline(0))
      
      const xhr = new XMLHttpRequest()
      xhr.open('GET', url)
      xhr.responseType = 'blob'
      
      // 记录以支持取消
      const controller = new AbortController()
      task.abortController = controller
      controller.signal.addEventListener('abort', () => xhr.abort())

      xhr.onprogress = (e) => {
        if (task.status !== 'running') return
        if (e.total) {
          task.progress = e.loaded / e.total
          task.loaded = e.loaded
        } else {
          task.loaded = e.loaded
          task.progress = Math.min(0.99, e.loaded / size)
        }
        const elapsed = (Date.now() - (task.startTime || Date.now())) / 1000
        if (elapsed > 0) {
          task.speed = calcTransferSpeed(task.loaded, task.speedBaseLoaded, task.speedBaseTime, '下载中...')
        }
      }

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          const blob = new Blob([xhr.response], { type: xhr.getResponseHeader('Content-Type') || 'application/octet-stream' })
          const blobUrl = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = blobUrl
          a.download = name
          a.click()
          URL.revokeObjectURL(blobUrl)

          task.status = 'done'
          task.progress = 1
          task.loaded = size
          task.speed = '已完成'
        } else {
          task.status = 'error'
          task.speed = '下载失败'
        }
      }

      xhr.onerror = () => {
        task.status = 'error'
        task.speed = '下载失败'
      }

      xhr.send()
    }
    // #endif

    // #ifndef H5
    // 移动端小程序/App 原生环境：使用 uni.downloadFile，能够真正显示进度且不占用堆内存
    task.status = 'running'
    task.startTime = Date.now()

    const downloadTask = uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode === 200) {
          uni.saveFile({
            tempFilePath: res.tempFilePath,
            success: () => {
              task.status = 'done'
              task.progress = 1
              task.loaded = size
              task.speed = '已保存'
              uni.showToast({ title: '文件已保存到本地', icon: 'success' })
            },
            fail: () => {
              task.status = 'error'
              task.speed = '保存失败'
            }
          })
        } else {
          task.status = 'error'
          task.speed = `错误 ${res.statusCode}`
        }
      },
      fail: (err) => {
        if (err.errMsg?.includes('abort')) {
          // 主动取消
        } else {
          task.status = 'error'
          task.speed = '下载失败'
        }
      }
    })

    task.downloadTask = downloadTask
    downloadTask.onProgressUpdate((res) => {
      if (task.status !== 'running') return
      task.progress = res.progress / 100
      task.loaded = res.totalBytesWritten
      const elapsed = (Date.now() - (task.startTime || Date.now())) / 1000
      if (elapsed > 0) {
        task.speed = formatSpeed(task.loaded / elapsed)
      }
    })
    // #endif
  }

  return {
    tasks,
    activeTaskCount,
    clearCompleted,
    pauseUpload,
    resumeUpload,
    cancelTask,
    addUploadTask,
    addDownloadTask
  }
})
