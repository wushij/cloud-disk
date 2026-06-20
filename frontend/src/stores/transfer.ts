import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import http, { TOKEN_KEY } from '@/api/http'
import { usePromptDialogStore } from '@/stores/promptDialog'
import { calcFileMd5 } from '@/utils/md5'
import { uploadFile } from '@/utils/upload'
import { clearFolderCache, ensureFolderPath, fileRelativePath } from '@/utils/folderUpload'
import axios from 'axios'

export interface TransferTask {
  id: string
  type: 'upload' | 'download'
  name: string
  size: number
  progress: number
  loaded: number
  speed: string
  status: 'waiting' | 'running' | 'paused' | 'done' | 'error' | 'instant'
  folderId?: number
  fileId?: number
  /** 上传图片的本地预览地址 */
  coverUrl?: string
  // 运行期控制变量（在内存中维护）
  abortController?: AbortController
  startTime?: number
  fileObj?: File
  md5?: string | null
}

const DOWNLOAD_IN_MEMORY_MAX = 300 * 1024 * 1024 // 300MB

function formatSpeed(bytesPerSec: number): string {
  if (bytesPerSec <= 0) return '0 B/s'
  if (bytesPerSec < 1024) return `${bytesPerSec.toFixed(1)} B/s`
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(1)} KB/s`
  return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`
}

function tokenParam() {
  return encodeURIComponent(localStorage.getItem(TOKEN_KEY) || '')
}

export const useTransferStore = defineStore('transfer', () => {
  const tasks = ref<TransferTask[]>([])
  const isCollapsed = ref(false)

  const runningCount = computed(
    () => tasks.value.filter((t) => t.status === 'running' || t.status === 'waiting').length
  )

  /** 通过 store 数组项更新，避免闭包持有原始对象导致 UI 不刷新 */
  function patchTask(taskId: string, patch: Partial<TransferTask>): boolean {
    const idx = tasks.value.findIndex((t) => t.id === taskId)
    if (idx === -1) return false
    const current = tasks.value[idx]
    tasks.value[idx] = { ...current, ...patch }
    return true
  }

  function revokeTaskCover(task?: TransferTask) {
    if (task?.coverUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(task.coverUrl)
    }
  }

  function getTask(taskId: string): TransferTask | undefined {
    return tasks.value.find((t) => t.id === taskId)
  }

  function toggleCollapse(val?: boolean) {
    isCollapsed.value = val !== undefined ? val : !isCollapsed.value
  }

  function updateProgress(taskId: string, progress: number, status?: string) {
    const task = getTask(taskId)
    if (!task) return
    patchTask(taskId, {
      progress: Math.max(task.progress, progress),
      ...(status ? { status: status as TransferTask['status'] } : {})
    })
  }

  // 清除已完成的任务
  function clearCompleted() {
    tasks.value.forEach((t) => {
      if (t.status === 'done' || t.status === 'instant' || t.status === 'error') {
        revokeTaskCover(t)
      }
    })
    tasks.value = tasks.value.filter(
      (t) => t.status !== 'done' && t.status !== 'instant' && t.status !== 'error'
    )
  }

  // 暂停任务（仅上传支持暂停）
  function pauseTask(taskId: string) {
    const task = getTask(taskId)
    if (!task || task.status !== 'running') return

    task.abortController?.abort()
    patchTask(taskId, { status: 'paused', speed: '已暂停' })
  }

  // 恢复任务（重新调用上传流程）
  async function resumeTask(taskId: string) {
    const task = getTask(taskId)
    if (!task || task.status !== 'paused' || !task.fileObj) return

    const abortController = new AbortController()
    patchTask(taskId, {
      status: 'running',
      startTime: Date.now(),
      abortController
    })

    try {
      const onProgress = (ratio: number) => {
        const current = getTask(taskId)
        if (!current || current.status !== 'running') return
        const loaded = Math.round(ratio * current.size)
        const elapsed = (Date.now() - (current.startTime || Date.now())) / 1000
        patchTask(taskId, {
          progress: ratio,
          loaded,
          speed: elapsed > 0 ? formatSpeed(loaded / elapsed) : current.speed
        })
      }

      const result = await uploadFile(
        task.fileObj,
        task.folderId || 0,
        task.md5 || null,
        onProgress,
        abortController.signal
      )

      let nextId = taskId
      if (result.uploadId) {
        patchTask(taskId, { id: result.uploadId })
        nextId = result.uploadId
      }
      patchTask(nextId, {
        status: result.instant ? 'instant' : 'done',
        progress: 1,
        loaded: task.size,
        speed: '已完成'
      })
    } catch (err: any) {
      if (axios.isCancel(err) || (err && err.name === 'CanceledError')) {
        // 主动暂停，状态在 pauseTask 中已改
      } else {
        patchTask(taskId, { status: 'error', speed: '传输失败' })
      }
    }
  }

  // 取消任务
  function cancelTask(taskId: string) {
    const task = getTask(taskId)
    if (!task) return
    revokeTaskCover(task)
    task.abortController?.abort()
    tasks.value = tasks.value.filter((t) => t.id !== taskId)
  }

  // 触发单个文件上传流程
  async function uploadSingleFile(file: File, folderId: number, targetName?: string) {
    let taskId = String(crypto.randomUUID())
    const abortController = new AbortController()
    const coverUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined
    tasks.value.unshift({
      id: taskId,
      type: 'upload',
      name: targetName || file.name,
      size: file.size,
      progress: 0,
      loaded: 0,
      speed: '准备中...',
      status: 'waiting',
      folderId,
      fileObj: file,
      coverUrl,
      abortController,
      startTime: Date.now()
    })

    try {
      patchTask(taskId, { status: 'running', progress: 0.01 })

      const md5 = await calcFileMd5(file, (r) => {
        const current = getTask(taskId)
        if (!current || current.status !== 'running') return
        const progress = r * 0.15
        patchTask(taskId, {
          progress,
          loaded: Math.round(progress * file.size),
          speed: '校验 MD5...'
        })
      })
      patchTask(taskId, { md5, startTime: Date.now() })

      const result = await uploadFile(
        file,
        folderId,
        md5,
        (ratio) => {
          const current = getTask(taskId)
          if (!current || current.status !== 'running') return
          const progress = 0.15 + ratio * 0.85
          const loaded = Math.round(progress * file.size)
          const elapsed = (Date.now() - (current.startTime || Date.now())) / 1000
          patchTask(taskId, {
            progress,
            loaded,
            speed: elapsed > 0 ? formatSpeed((loaded - file.size * 0.15) / elapsed) : current.speed
          })
        },
        abortController.signal
      )

      if (result.uploadId) {
        patchTask(taskId, { id: result.uploadId })
        taskId = result.uploadId
      }
      patchTask(taskId, {
        status: result.instant ? 'instant' : 'done',
        progress: 1,
        loaded: file.size,
        speed: '已完成'
      })
    } catch (err: any) {
      if (axios.isCancel(err) || (err && err.name === 'CanceledError')) {
        // 取消或暂停动作
      } else {
        patchTask(taskId, { status: 'error', speed: '传输失败' })
      }
    }
  }

  // 批量处理上传
  async function processFiles(files: File[], folderId: number, onComplete?: () => void) {
    clearFolderCache()
    for (const file of files) {
      if (file.size === 0) continue
      const rel = fileRelativePath(file)
      let targetFolderId = folderId
      if (rel) {
        try {
          targetFolderId = await ensureFolderPath(folderId, rel)
        } catch {
          continue
        }
      }
      await uploadSingleFile(file, targetFolderId, rel || file.name)
    }
    onComplete?.()
  }

  // 触发文件下载流程（有进度条管理）
  async function addDownloadTask(fileId: number, name: string, size: number) {
    const taskId = String(crypto.randomUUID())
    const abortController = new AbortController()
    tasks.value.unshift({
      id: taskId,
      type: 'download',
      name,
      size,
      progress: 0,
      loaded: 0,
      speed: '准备下载...',
      status: 'waiting',
      fileId,
      abortController,
      startTime: Date.now()
    })

    if (size >= DOWNLOAD_IN_MEMORY_MAX) {
      patchTask(taskId, { status: 'running', progress: 0.5, speed: '浏览器接管' })

      const a = document.createElement('a')
      a.href = `/api/files/${fileId}/download?access_token=${tokenParam()}`
      a.download = name
      a.click()

      setTimeout(() => {
        patchTask(taskId, {
          status: 'done',
          progress: 1,
          loaded: size,
          speed: '已开始下载'
        })
      }, 1000)
      return
    }

    try {
      patchTask(taskId, { status: 'running', speed: '连接中...', startTime: Date.now() })

      let downloadUrl = `/api/files/${fileId}/download?access_token=${tokenParam()}`
      try {
        const { data } = await http.get(`/api/files/${fileId}/direct-url`, { signal: abortController.signal })
        if (data?.url) downloadUrl = data.url
      } catch (err: any) {
        if (axios.isCancel(err) || err?.name === 'CanceledError') throw err
      }

      if (!getTask(taskId)) return

      patchTask(taskId, { speed: '下载中...' })

      const response = await axios({
        url: downloadUrl,
        method: 'GET',
        responseType: 'blob',
        signal: abortController.signal,
        onDownloadProgress: (e) => {
          const current = getTask(taskId)
          if (!current || current.status !== 'running') return
          const loaded = e.loaded
          const progress = e.total ? loaded / e.total : Math.min(0.99, loaded / size)
          const elapsed = (Date.now() - (current.startTime || Date.now())) / 1000
          patchTask(taskId, {
            loaded,
            progress,
            speed: elapsed > 0 ? formatSpeed(loaded / elapsed) : '下载中...'
          })
        }
      })

      if (!getTask(taskId)) return

      const blob = new Blob([response.data], { type: (response.headers['content-type'] as string) || undefined })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = name
      a.click()
      URL.revokeObjectURL(url)

      patchTask(taskId, {
        status: 'done',
        progress: 1,
        loaded: size,
        speed: '已完成'
      })
    } catch (err: any) {
      if (axios.isCancel(err) || err?.name === 'CanceledError') return
      patchTask(taskId, { status: 'error', speed: '下载失败' })
    }
  }

  return {
    tasks,
    isCollapsed,
    runningCount,
    toggleCollapse,
    updateProgress,
    processFiles,
    addDownloadTask,
    pauseTask,
    resumeTask,
    cancelTask,
    clearCompleted
  }
})

export async function promptCreateFolder(parentId: number): Promise<boolean> {
  const value = await usePromptDialogStore().open({
    title: '新建文件夹',
    message: '为当前目录创建一个新文件夹',
    placeholder: '请输入文件夹名称',
    confirmText: '创建',
    icon: 'folder',
    maxlength: 64
  })
  if (!value) return false
  await http.post('/api/folders', { folderName: value, parentId })
  ElMessage.success('创建成功')
  return true
}
