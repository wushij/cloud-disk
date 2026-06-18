import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { calcFileMd5 } from '@/utils/md5'
import { uploadFile } from '@/utils/upload'
import { clearFolderCache, ensureFolderPath, fileRelativePath } from '@/utils/folderUpload'

export interface UploadTask {
  id: string
  name: string
  progress: number
  status: string
}

export const useUploadStore = defineStore('upload', () => {
  const tasks = ref<UploadTask[]>([])

  function updateProgress(taskId: string, progress: number, status?: string) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task) return
    task.progress = Math.max(task.progress, progress)
    if (status) task.status = status
  }

  function pruneFinished(delayMs = 3000) {
    setTimeout(() => {
      tasks.value = tasks.value.filter((t) => t.status === 'running')
    }, delayMs)
  }

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
      const taskId = String(crypto.randomUUID())
      const task: UploadTask = { id: taskId, name: rel || file.name, progress: 0, status: 'running' }
      tasks.value.unshift(task)
      try {
        task.progress = 0.01
        const md5 = await calcFileMd5(file, (r) => {
          task.progress = r * 0.15
        })
        const result = await uploadFile(file, targetFolderId, md5, (r) => {
          task.progress = 0.15 + r * 0.85
        })
        if (result.uploadId) task.id = result.uploadId
        task.status = result.instant ? 'instant' : 'done'
        task.progress = 1
      } catch {
        task.status = 'error'
      }
    }
    pruneFinished()
    onComplete?.()
  }

  return { tasks, processFiles, updateProgress, pruneFinished }
})

export async function promptCreateFolder(parentId: number): Promise<boolean> {
  const { value } = await ElMessageBox.prompt('请输入文件夹名称', '新建文件夹', {
    confirmButtonText: '创建',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value?.trim()) return false
  await http.post('/api/folders', { folderName: value.trim(), parentId })
  ElMessage.success('创建成功')
  return true
}
