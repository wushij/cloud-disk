import { ref } from 'vue'
import { defineStore } from 'pinia'
import { request } from '@/api/http'
import { updateUrlQueryParam } from '@/utils/navUrlHelper'

export interface FileItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  mimeType?: string | null
  previewable?: boolean
  hasThumbnail?: boolean
  transcodeStatus?: string
  createdAt?: string
  officeFile?: boolean
}

export const useFileStore = defineStore('file', () => {
  const currentFolderId = ref(0)
  const breadcrumb = ref<{ id: number; name: string }[]>([{ id: 0, name: '全部文件' }])
  const items = ref<FileItem[]>([])
  const loading = ref(false)
  const keyword = ref('')

  async function loadList() {
    loading.value = true
    try {
      const data = await request<{ content: FileItem[] }>({
        url: '/api/files',
        data: {
          folderId: currentFolderId.value,
          page: 0,
          size: 200,
          q: keyword.value.trim() || undefined
        }
      })
      items.value = data.content || []
    } finally {
      loading.value = false
    }
  }

  async function loadBreadcrumbs(folderId: number) {
    if (folderId <= 0) {
      breadcrumb.value = [{ id: 0, name: '全部文件' }]
      return
    }
    try {
      const data = await request<{ id: number; name: string }[]>({
        url: `/api/folders/${folderId}/breadcrumbs?full=true`
      })
      if (Array.isArray(data)) {
        breadcrumb.value = data
      }
    } catch {
      breadcrumb.value = [{ id: 0, name: '全部文件' }]
    }
  }

  function enterFolder(row: FileItem) {
    if (row.type !== 'folder') return
    breadcrumb.value.push({ id: row.id, name: row.name })
    currentFolderId.value = row.id
    keyword.value = ''
    updateUrlQueryParam({ folderId: row.id })
    return loadList()
  }

  function gotoCrumb(idx: number) {
    const target = breadcrumb.value[idx]
    breadcrumb.value = breadcrumb.value.slice(0, idx + 1)
    currentFolderId.value = target.id
    updateUrlQueryParam({ folderId: target.id === 0 ? null : target.id })
    return loadList()
  }

  function goBackFolder() {
    if (breadcrumb.value.length <= 1) return
    gotoCrumb(breadcrumb.value.length - 2)
  }

  async function createFolder(name: string) {
    await request({
      url: '/api/folders',
      method: 'POST',
      data: { folderName: name, parentId: currentFolderId.value }
    })
    await loadList()
  }

  async function renameItem(row: FileItem, newName: string) {
    const url = row.type === 'folder' ? `/api/folders/${row.id}/rename` : `/api/files/${row.id}/rename`
    await request({ url, method: 'PUT', data: { name: newName } })
    await loadList()
  }

  async function deleteItem(row: FileItem) {
    const url = row.type === 'folder' ? `/api/folders/${row.id}` : `/api/files/${row.id}`
    await request({ url, method: 'DELETE' })
    await loadList()
  }

  return {
    currentFolderId,
    breadcrumb,
    items,
    loading,
    keyword,
    loadList,
    loadBreadcrumbs,
    enterFolder,
    gotoCrumb,
    goBackFolder,
    createFolder,
    renameItem,
    deleteItem
  }
})
