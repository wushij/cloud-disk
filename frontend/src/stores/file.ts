import { ref } from 'vue'
import { defineStore } from 'pinia'
import http from '@/api/http'

export interface FileItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  mimeType?: string | null
  previewable?: boolean
  officeFile?: boolean
  hasThumbnail?: boolean
  transcodeStatus?: string
  highlightName?: string
  parentId?: number
  createdAt?: string
  ownerId?: number
  canDelete?: boolean
  canModify?: boolean
  canEdit?: boolean
}

const DEFAULT_PAGE_SIZE = 50

export const useFileStore = defineStore('file', () => {
  const currentFolderId = ref(0)
  const breadcrumb = ref<{ id: number; name: string }[]>([{ id: 0, name: '全部文件' }])
  const items = ref<FileItem[]>([])
  const loading = ref(false)
  const keyword = ref('')
  const fileType = ref('')
  const page = ref(0)
  const totalElements = ref(0)
  const hasMore = ref(false)

  const pageSize = DEFAULT_PAGE_SIZE

  async function loadList(resetPage = true) {
    loading.value = true
    try {
      if (resetPage) page.value = 0
      const { data } = await http.get('/api/files', {
        params: {
          folderId: currentFolderId.value,
          page: page.value,
          size: pageSize,
          q: keyword.value.trim() || undefined,
          fileType: fileType.value || undefined
        }
      })
      if (resetPage) {
        items.value = data.content
      } else {
        items.value = [...items.value, ...data.content]
      }
      totalElements.value = data.totalElements
      hasMore.value = items.value.length < totalElements.value
    } finally {
      loading.value = false
    }
  }

  async function loadMore() {
    if (loading.value || !hasMore.value) return
    page.value++
    await loadList(false)
  }

  function navigateToFolder(id: number, name: string, crumbs?: { id: number; name: string }[]) {
    currentFolderId.value = id
    if (crumbs) {
      breadcrumb.value = crumbs
    } else if (id === 0) {
      breadcrumb.value = [{ id: 0, name: '全部文件' }]
    }
    keyword.value = ''
    return loadList()
  }

  function onTreeSelect(id: number, label: string) {
    return navigateToFolder(id, label, [{ id: 0, name: '全部文件' }, ...(id === 0 ? [] : [{ id, name: label }])])
  }

  function enterFolder(row: FileItem) {
    if (row.type !== 'folder') return
    breadcrumb.value.push({ id: row.id, name: row.name })
    currentFolderId.value = row.id
    keyword.value = ''
    return loadList()
  }

  function gotoCrumb(idx: number) {
    const target = breadcrumb.value[idx]
    breadcrumb.value = breadcrumb.value.slice(0, idx + 1)
    currentFolderId.value = target.id
    return loadList()
  }

  return {
    currentFolderId,
    breadcrumb,
    items,
    loading,
    keyword,
    fileType,
    page,
    totalElements,
    hasMore,
    pageSize,
    loadList,
    loadMore,
    navigateToFolder,
    onTreeSelect,
    enterFolder,
    gotoCrumb
  }
})
