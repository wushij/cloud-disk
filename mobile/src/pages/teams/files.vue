<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useTransferStore } from '@/stores/transfer'
import { request, fileApiUrl } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import MobilePromptDialog from '@/components/MobilePromptDialog.vue'
import BreadcrumbBar from '@/components/BreadcrumbBar.vue'
import FileListItem from '@/components/FileListItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import { isImageFile, isVideoFile } from '@/utils/fileCover'
import type { FileItem } from '@/stores/file'

const auth = useAuthStore()
const transferStore = useTransferStore()

const spaceId = ref(0)
const spaceName = ref('')
const rootFolderId = ref(0)
const currentFolderId = ref(0)
const breadcrumb = ref<{ id: number; name: string }[]>([])
const items = ref<FileItem[]>([])
const loading = ref(false)
const actionVisible = ref(false)
const actionItem = ref<FileItem | null>(null)

// 菜单状态
const myRole = ref('')
const menuVisible = ref(false)
const renameVisible = ref(false)
const renaming = ref(false)

type ConfirmAction = 'dissolve' | 'leave' | 'delete'
const confirmVisible = ref(false)
const confirmAction = ref<ConfirmAction>('dissolve')
const confirmFile = ref<FileItem | null>(null)

const confirmTitle = computed(() => {
  switch (confirmAction.value) {
    case 'dissolve': return '解散团队'
    case 'leave': return '退出团队'
    case 'delete': return '删除确认'
    default: return '确认操作'
  }
})

const confirmMessage = computed(() => {
  switch (confirmAction.value) {
    case 'dissolve':
      return `确认解散团队「${spaceName.value}」吗？所有成员将被移除，团队关联文件将被移入回收站！`
    case 'leave':
      return `确认退出团队「${spaceName.value}」吗？退出后您将无法再访问其共享文件！`
    case 'delete': {
      const f = confirmFile.value
      return f ? `确定删除「${f.name}」吗？文件将移至回收站` : ''
    }
    default: return ''
  }
})

const confirmButtonText = computed(() => {
  switch (confirmAction.value) {
    case 'dissolve': return '解散'
    case 'leave': return '退出'
    case 'delete': return '删除'
    default: return '确定'
  }
})

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

const menuList = computed(() => {
  const list: { name: string; color?: string }[] = [{ name: '成员管理' }]
  if (myRole.value === 'OWNER' || myRole.value === 'ADMIN') {
    list.push({ name: '重命名团队' })
  }
  if (myRole.value === 'OWNER') {
    list.push({ name: '解散团队', color: '#ef4444' })
  } else {
    list.push({ name: '退出团队', color: '#ef4444' })
  }
  return list
})

async function syncSpaceMeta() {
  if (!spaceId.value) return
  try {
    const space = await request<{ name: string }>({ url: `/api/teams/${spaceId.value}` })
    spaceName.value = space.name
    if (breadcrumb.value.length > 0 && breadcrumb.value[0].id === rootFolderId.value) {
      breadcrumb.value[0].name = space.name
    }
  } catch {
    /* handled */
  }
}

onLoad((query) => {
  spaceId.value = Number(query?.spaceId || 0)
  spaceName.value = decodeURIComponent(query?.name || '团队空间')
  rootFolderId.value = Number(query?.rootFolderId || 0)
  currentFolderId.value = rootFolderId.value
  breadcrumb.value = [{ id: rootFolderId.value, name: spaceName.value }]
  myRole.value = query?.myRole || ''
  loadFiles()
})

onShow(() => {
  if (!auth.requireLogin()) return
  syncSpaceMeta()
})

function disbandSpace() {
  confirmAction.value = 'dissolve'
  confirmVisible.value = true
}

function leaveSpace() {
  confirmAction.value = 'leave'
  confirmVisible.value = true
}

async function submitRenameTeam(name: string) {
  const trimmed = name.trim()
  if (!trimmed) {
    uni.showToast({ title: '请输入团队名称', icon: 'none' })
    return
  }
  if (renaming.value) return
  renaming.value = true
  try {
    await request({ url: `/api/teams/${spaceId.value}`, method: 'PUT', data: { name: trimmed } })
    renameVisible.value = false
    uni.showToast({ title: '已重命名', icon: 'success' })
    await syncSpaceMeta()
  } catch {
    /* handled */
  } finally {
    renaming.value = false
  }
}

async function onConfirmAction() {
  try {
    if (confirmAction.value === 'dissolve') {
      await request({ url: `/api/teams/${spaceId.value}`, method: 'DELETE' })
      uni.showToast({ title: '已解散该团队空间', icon: 'success' })
      uni.navigateBack()
      return
    }
    if (confirmAction.value === 'leave') {
      await request({ url: `/api/teams/${spaceId.value}/leave`, method: 'POST' })
      uni.showToast({ title: '已退出该团队空间', icon: 'success' })
      uni.navigateBack()
      return
    }
    if (confirmAction.value === 'delete' && confirmFile.value) {
      const row = confirmFile.value
      const url = row.type === 'folder' ? `/api/folders/${row.id}` : `/api/files/${row.id}`
      await request({ url, method: 'DELETE' })
      uni.showToast({ title: '已移至回收站', icon: 'success' })
      loadFiles()
    }
  } catch {
    /* handled */
  }
}

function onMenuSelect(item: { name: string }) {
  menuVisible.value = false
  if (item.name === '成员管理') {
    uni.navigateTo({
      url: `/pages/teams/members?spaceId=${spaceId.value}&name=${encodeURIComponent(spaceName.value)}&myRole=${myRole.value}`
    })
  } else if (item.name === '重命名团队') {
    renameVisible.value = true
  } else if (item.name === '解散团队') {
    disbandSpace()
  } else if (item.name === '退出团队') {
    leaveSpace()
  }
}

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
      transferStore.addDownloadTask(row.id, row.name, row.size || 0)
      uni.showToast({ title: '已加入下载队列', icon: 'none' })
      break
    }
    case '删除': {
      confirmFile.value = row
      confirmAction.value = 'delete'
      confirmVisible.value = true
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
    >
      <template #right>
        <view class="header-right-group">
          <view class="more-btn cd-pressable" @click="menuVisible = true">
            <u-icon name="more-dot-fill" size="22" color="#000000" bold />
          </view>
        </view>
      </template>
      <template #extra>
        <BreadcrumbBar v-if="breadcrumb.length > 1" :crumbs="breadcrumb" @select="gotoCrumb" />
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

    <!-- 文件操作面板 -->
    <u-action-sheet
      :show="actionVisible"
      :actions="actionList"
      cancel-text="取消"
      round="16"
      @close="actionVisible = false"
      @select="onSheetSelect"
    />

    <!-- 右上角设置菜单面板 -->
    <u-action-sheet
      :show="menuVisible"
      :actions="menuList"
      cancel-text="取消"
      round="16"
      @close="menuVisible = false"
      @select="onMenuSelect"
    />

    <MobilePromptDialog
      v-model:show="renameVisible"
      title="重命名团队"
      placeholder="输入新的团队名称"
      confirm-text="保存"
      :initial-value="spaceName"
      @confirm="submitRenameTeam"
    />

    <MobileConfirmDialog
      v-model:show="confirmVisible"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="confirmButtonText"
      danger
      @confirm="onConfirmAction"
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

.more-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  transition: opacity var(--cd-transition-fast);
}

.more-btn:active {
  opacity: 0.55;
}

.header-right-group {
  display: flex;
  align-items: center;
}
</style>
