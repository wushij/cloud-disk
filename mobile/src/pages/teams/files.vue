<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request, fileApiUrl } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import BreadcrumbBar from '@/components/BreadcrumbBar.vue'
import FileListItem from '@/components/FileListItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import { isImageFile, isVideoFile } from '@/utils/fileCover'
import type { FileItem } from '@/stores/file'

const auth = useAuthStore()

const spaceId = ref(0)
const spaceName = ref('')
const rootFolderId = ref(0)
const currentFolderId = ref(0)
const breadcrumb = ref<{ id: number; name: string }[]>([])
const items = ref<FileItem[]>([])
const loading = ref(false)
const actionVisible = ref(false)
const actionItem = ref<FileItem | null>(null)

const actionList = computed(() => {
  const row = actionItem.value
  if (!row) return [] as { name: string }[]
  const list: { name: string; color?: string }[] = []
  if (row.type === 'folder') list.push({ name: '打开' })
  if (row.type === 'file') {
    if (row.previewable && isImageFile(row)) list.push({ name: '预览图片' })
    if (row.previewable && isVideoFile(row)) list.push({ name: '播放视频' })
    list.push({ name: '下载' })
  }
  list.push({ name: '删除', color: '#ef4444' })
  return list
})

onLoad((query) => {
  spaceId.value = Number(query?.spaceId || 0)
  spaceName.value = decodeURIComponent(query?.name || '团队空间')
  rootFolderId.value = Number(query?.rootFolderId || 0)
  currentFolderId.value = rootFolderId.value
  breadcrumb.value = [{ id: rootFolderId.value, name: spaceName.value }]
  loadFiles()
})

async function loadFiles() {
  if (!auth.requireLogin()) return
  loading.value = true
  try {
    const params: Record<string, unknown> = {}
    if (currentFolderId.value !== rootFolderId.value) {
      params.folderId = currentFolderId.value
    }
    const data = await request<{ items: FileItem[] }>({
      url: `/api/teams/${spaceId.value}/files`,
      data: params
    })
    items.value = data.items || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function openItem(row: FileItem) {
  if (row.type === 'folder') {
    breadcrumb.value.push({ id: row.id, name: row.name })
    currentFolderId.value = row.id
    loadFiles()
    return
  }
  if (isImageFile(row)) {
    const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
    uni.navigateTo({ url: `/pages/preview/image?url=${url}&name=${encodeURIComponent(row.name)}` })
    return
  }
  if (isVideoFile(row)) {
    const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
    uni.navigateTo({ url: `/pages/preview/video?url=${url}&name=${encodeURIComponent(row.name)}` })
    return
  }
  showActions(row)
}

function goBack() {
  if (breadcrumb.value.length <= 1) {
    uni.navigateBack()
    return
  }
  const target = breadcrumb.value[breadcrumb.value.length - 2]
  breadcrumb.value = breadcrumb.value.slice(0, -1)
  currentFolderId.value = target.id
  loadFiles()
}

function gotoCrumb(idx: number) {
  const target = breadcrumb.value[idx]
  breadcrumb.value = breadcrumb.value.slice(0, idx + 1)
  currentFolderId.value = target.id
  loadFiles()
}

function showActions(row: FileItem) {
  actionItem.value = row
  actionVisible.value = true
}

function onSheetSelect(item: { name: string }) {
  onActionSelect(item)
}

function onActionSelect(item: { name: string }) {
  const row = actionItem.value
  actionVisible.value = false
  if (!row) return
  switch (item.name) {
    case '打开':
      openItem(row)
      break
    case '预览图片': {
      const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
      uni.navigateTo({ url: `/pages/preview/image?url=${url}&name=${encodeURIComponent(row.name)}` })
      break
    }
    case '播放视频': {
      const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
      uni.navigateTo({ url: `/pages/preview/video?url=${url}&name=${encodeURIComponent(row.name)}` })
      break
    }
    case '下载': {
      const url = fileApiUrl(`/api/files/${row.id}/download`)
      // #ifdef H5
      window.open(url, '_blank')
      // #endif
      // #ifndef H5
      uni.showLoading({ title: '下载中' })
      uni.downloadFile({
        url,
        success: (res) => {
          if (res.statusCode === 200) {
            uni.saveFile({
              tempFilePath: res.tempFilePath,
              success: () => uni.showToast({ title: '已保存', icon: 'success' }),
              fail: () => uni.showToast({ title: '保存失败', icon: 'none' })
            })
          }
        },
        complete: () => uni.hideLoading()
      })
      // #endif
      break
    }
    case '删除': {
      uni.showModal({
        title: '删除确认',
        content: `确定删除「${row.name}」吗？文件将移至回收站`,
        success: async (res) => {
          if (!res.confirm) return
          try {
            const url = row.type === 'folder' ? `/api/folders/${row.id}` : `/api/files/${row.id}`
            await request({ url, method: 'DELETE' })
            uni.showToast({ title: '已移至回收站', icon: 'success' })
            loadFiles()
          } catch {
            /* handled */
          }
        }
      })
      break
    }
  }
}
</script>

<template>
  <view class="page">
    <MobileHeader
      :title="spaceName"
      :subtitle="`${items.length} 项`"
      gradient
      :show-back="true"
      @back="goBack"
    >
      <template #extra>
        <BreadcrumbBar :crumbs="breadcrumb" @select="gotoCrumb" />
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="file-scroll">
      <view v-if="loading" class="state-box">
        <u-loading-icon text="加载中" color="var(--cd-primary)" />
      </view>
      <EmptyState
        v-else-if="!items.length"
        icon="folder"
        title="团队空间为空"
        description="还没有文件，在 PC 端上传后即可同步查看"
      />
      <view v-else class="file-list">
        <FileListItem
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          :item="item"
          @click="openItem(item)"
          @longpress="showActions(item)"
        />
      </view>
    </scroll-view>

    <u-action-sheet
      :show="actionVisible"
      :actions="actionList"
      cancel-text="取消"
      round="16"
      @close="actionVisible = false"
      @select="onSheetSelect"
    />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(env(safe-area-inset-bottom) + 20rpx);
  background: var(--cd-bg);
}

.file-scroll {
  height: calc(100vh - 280rpx);
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}

.file-list {
  padding-top: 4rpx;
  padding-bottom: 32rpx;
}
</style>
