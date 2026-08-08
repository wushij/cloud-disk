import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fileApi, folderApi } from '@/api'
import { updateUrlQueryParam } from '@/utils/navUrlHelper'
import type { FileItem } from '@/api/types'

export const useFileStore = defineStore('file', () => {
  const currentFolderId = ref(0)
  const breadcrumb = ref<{ id: number; name: string }[]>([{ id: 0, name: '全部文件' }])
  const items = ref<FileItem[]>([])
  const loading = ref(false)
  const keyword = ref('')
  const fileType = ref('')
  const listInitialized = ref(false)
  const needsRefresh = ref(false)

  function reset() {
    currentFolderId.value = 0
    breadcrumb.value = [{ id: 0, name: '全部文件' }]
    items.value = []
    keyword.value = ''
    fileType.value = ''
    listInitialized.value = false
    needsRefresh.value = false
  }

  function markListStale() {
    needsRefresh.value = true
  }

  function onTranscodeEvent(fileId?: string | number | null) {
    const id = fileId != null ? Number(fileId) : NaN
    if (!Number.isNaN(id) && items.value.some((item) => item.id === id)) {
      void loadList()
      return
    }
    markListStale()
  }

  function hasActiveTranscode(itemsList: FileItem[] = items.value): boolean {
    return itemsList.some(
      (item) =>
        item.type === 'file' &&
        (item.transcodeStatus === 'PENDING' || item.transcodeStatus === 'PROCESSING')
    )
  }

  async function loadList() {
    loading.value = true
    try {
      const data = await fileApi.list({
        folderId: currentFolderId.value,
        page: 0,
        size: 200,
        q: keyword.value.trim() || undefined,
        fileType: fileType.value || undefined
      })
      items.value = (data.content || []) as any
      listInitialized.value = true
      needsRefresh.value = false
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
      const data = await folderApi.breadcrumbs(folderId, true)
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
    fileType.value = ''
    updateUrlQueryParam({ folderId: row.id })
    return loadList()
  }

  function gotoCrumb(idx: number) {
    const target = breadcrumb.value[idx]
    breadcrumb.value = breadcrumb.value.slice(0, idx + 1)
    currentFolderId.value = target.id
    fileType.value = ''
    updateUrlQueryParam({ folderId: target.id === 0 ? null : target.id })
    return loadList()
  }

  function goBackFolder() {
    if (breadcrumb.value.length <= 1) return
    gotoCrumb(breadcrumb.value.length - 2)
  }

  async function createFolder(name: string) {
    await folderApi.create({ folderName: name, parentId: currentFolderId.value })
    await loadList()
  }

  async function renameItem(row: FileItem, newName: string) {
    if (row.type === 'folder') {
      await folderApi.rename(row.id, newName)
    } else {
      await fileApi.rename(row.id, newName)
    }
    await loadList()
  }

  async function deleteItem(row: FileItem) {
    if (row.type === 'folder') {
      await folderApi.delete(row.id)
    } else {
      await fileApi.delete(row.id)
    }
    await loadList()
  }

  return {
    currentFolderId,
    breadcrumb,
    items,
    loading,
    keyword,
    fileType,
    listInitialized,
    needsRefresh,
    reset,
    markListStale,
    onTranscodeEvent,
    hasActiveTranscode,
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
