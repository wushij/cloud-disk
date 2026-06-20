<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useTransferStore } from '@/stores/transfer'
import { request, fileApiUrl, TOKEN_KEY } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import MobilePromptDialog from '@/components/MobilePromptDialog.vue'
import MobileShareDialog from '@/components/MobileShareDialog.vue'
import BreadcrumbBar from '@/components/BreadcrumbBar.vue'
import FileListItem from '@/components/FileListItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import { isImageFile, isVideoFile } from '@/utils/fileCover'
import { isTextFile } from '@/utils/filePreview'
import { useH5BackGuard } from '@/composables/useH5BackGuard'
import type { FileItem } from '@/stores/file'
import { downloadZip } from '@/utils/download'

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

const selectMode = ref(false)
const selectedItems = ref<FileItem[]>([])

function toggleSelectMode() {
  selectMode.value = !selectMode.value
  if (!selectMode.value) {
    clearSelection()
  }
}

function exitSelectMode() {
  selectMode.value = false
  clearSelection()
}

function clearSelection() {
  selectedItems.value = []
}

function toggleChecked(row: FileItem) {
  const idx = selectedItems.value.findIndex(item => item.id === row.id && item.type === row.type)
  if (idx >= 0) {
    selectedItems.value.splice(idx, 1)
  } else {
    selectedItems.value.push(row)
  }
}

function isChecked(row: FileItem) {
  return selectedItems.value.some(item => item.id === row.id && item.type === row.type)
}

watch(currentFolderId, () => {
  clearSelection()
  selectMode.value = false
})

function downloadFile(row: FileItem) {
  if (row.type === 'folder') {
    downloadZip(`/api/files/download/zip?folderIds=${row.id}`)
    return
  }
  transferStore.addDownloadTask(row.id, row.name, row.sizeBytes || 0)
  uni.showToast({ title: '已加入下载队列', icon: 'none' })
}

function handleBatchDownload() {
  const folders = selectedItems.value.filter(i => i.type === 'folder').map(i => i.id)
  const files = selectedItems.value.filter(i => i.type === 'file').map(i => i.id)
  if (folders.length === 0 && files.length === 0) return

  let path = '/api/files/download/zip'
  const params: string[] = []
  if (folders.length > 0) {
    params.push(`folderIds=${folders.join(',')}`)
  }
  if (files.length > 0) {
    params.push(`fileIds=${files.join(',')}`)
  }
  if (params.length > 0) {
    path += '?' + params.join('&')
  }
  downloadZip(path)
}

function handleBatchDelete() {
  if (selectedItems.value.length === 0) return
  uni.showModal({
    title: '确认删除',
    content: `确定删除这 ${selectedItems.value.length} 个项目吗？`,
    success: async (res) => {
      if (!res.confirm) return
      uni.showLoading({ title: '正在删除...' })
      try {
        for (const item of selectedItems.value) {
          const url = item.type === 'folder' ? `/api/folders/${item.id}` : `/api/files/${item.id}`
          await request({ url, method: 'DELETE' })
        }
        uni.showToast({ title: '批量删除成功', icon: 'success' })
        clearSelection()
        selectMode.value = false
        loadFiles()
      } catch {
        uni.showToast({ title: '删除失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

// 菜单状态
const myRole = ref('')
const menuVisible = ref(false)
const renameVisible = ref(false)
const renaming = ref(false)

type ConfirmAction = 'dissolve' | 'leave' | 'delete'
const confirmVisible = ref(false)
const confirmAction = ref<ConfirmAction>('dissolve')
const confirmFile = ref<FileItem | null>(null)
const shareVisible = ref(false)
const shareFileId = ref<number | null>(null)
const shareFolderId = ref<number | null>(null)
const shareItemName = ref('')

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
  if (row.type === 'folder') {
    list.push({ name: '打开' })
    list.push({ name: '打包下载' })
  }
  if (row.type === 'file') {
    if (row.previewable && isImageFile(row)) list.push({ name: '预览图片' })
    if (row.previewable && isVideoFile(row)) list.push({ name: '播放视频' })
    if (row.previewable && isTextFile(row.mimeType, row.name)) list.push({ name: '预览文本' })
    list.push({ name: '下载' })
  }
  if (myRole.value === 'OWNER' || myRole.value === 'ADMIN') {
    list.push({ name: '分享' })
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
  clearSelection()
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
  if (selectMode.value) {
    toggleChecked(row)
    return
  }
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
  if (isTextFile(row.mimeType, row.name)) {
    const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
    uni.navigateTo({ url: `/pages/preview/text?url=${url}&name=${encodeURIComponent(row.name)}` })
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

function goBackFolder() {
  if (breadcrumb.value.length <= 1) return
  gotoCrumb(breadcrumb.value.length - 2)
}

useH5BackGuard({
  depth: () => breadcrumb.value.length - 1,
  onAppBack: () => goBackFolder(),
  // H5 刷新后 uni.navigateBack() 页面栈丢失，不定义 onRootBack
  // 由 guard 内部原生 history.back() 处理根级返回，兼容刷新场景
})

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
    case '预览文本': {
      const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
      uni.navigateTo({ url: `/pages/preview/text?url=${url}&name=${encodeURIComponent(row.name)}` })
      break
    }
    case '下载':
    case '打包下载': {
      downloadFile(row)
      break
    }
    case '分享': {
      openShare(row)
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

function openShare(row: FileItem) {
  shareFileId.value = row.type === 'file' ? row.id : null
  shareFolderId.value = row.type === 'folder' ? row.id : null
  shareItemName.value = row.name
  shareVisible.value = true
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
          <!-- 多选切换按钮 -->
          <view class="more-btn cd-pressable" style="margin-right: 16rpx;" @click="toggleSelectMode">
            <u-icon :name="selectMode ? 'checkmark-circle-fill' : 'list-dot'" size="22" :color="selectMode ? 'var(--cd-primary)' : '#000000'" bold />
          </view>
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
          :select-mode="selectMode"
          :checked="isChecked(item)"
          @click="openItem(item)"
          @longpress="showActions(item)"
          @check-change="toggleChecked(item)"
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

    <MobileShareDialog
      v-model:show="shareVisible"
      :file-id="shareFileId"
      :folder-id="shareFolderId"
      :item-name="shareItemName"
    />

    <!-- 移动端批量操作栏 -->
    <view v-if="selectMode" class="batch-footer-bar">
      <view class="batch-footer-info">
        <text class="info-label">已选择</text>
        <text class="batch-count">{{ selectedItems.length }}</text>
        <text class="info-label">项</text>
      </view>
      <view class="batch-footer-actions">
        <view class="batch-action-btn download-btn cd-pressable" @click="handleBatchDownload">
          <u-icon name="download" size="20" color="var(--cd-primary)" />
          <text class="btn-text">打包下载</text>
        </view>
        <view class="batch-action-btn delete-btn cd-pressable" @click="handleBatchDelete">
          <u-icon name="trash" size="20" color="#ef4444" />
          <text class="btn-text">删除</text>
        </view>
        <view class="batch-action-btn cancel-btn cd-pressable" @click="exitSelectMode">
          <u-icon name="close" size="20" color="#64748b" />
          <text class="btn-text">取消</text>
        </view>
      </view>
    </view>
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

/* 移动端批量操作栏 */
.batch-footer-bar {
  position: fixed;
  bottom: calc(24rpx + env(safe-area-inset-bottom));
  left: 24rpx;
  right: 24rpx;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1rpx solid rgba(255, 255, 255, 0.6);
  border-radius: 40rpx;
  padding: 20rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 999;
  box-shadow: 0 16rpx 48rpx rgba(15, 23, 42, 0.12), 0 2rpx 10rpx rgba(15, 23, 42, 0.04);
  animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(150rpx);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.batch-footer-info {
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--cd-text, #0f172a);
}

.info-label {
  color: var(--cd-text-secondary, #475569);
  font-size: 24rpx;
}

.batch-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: linear-gradient(135deg, var(--cd-primary, #6366f1) 0%, #4f46e5 100%);
  min-width: 44rpx;
  height: 44rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 800;
  box-shadow: 0 6rpx 16rpx rgba(99, 102, 241, 0.35);
}

.batch-footer-actions {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.batch-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  width: 104rpx;
  height: 94rpx;
  border-radius: 24rpx;
  background: rgba(148, 163, 184, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.5);
  transition: all var(--cd-transition-bounce, 0.25s);

  &.download-btn {
    background: rgba(99, 102, 241, 0.07);
    border-color: rgba(99, 102, 241, 0.12);
    
    .btn-text {
      color: var(--cd-primary, #6366f1);
      font-weight: 700;
    }
  }

  &.delete-btn {
    background: rgba(239, 68, 68, 0.07);
    border-color: rgba(239, 68, 68, 0.12);

    .btn-text {
      color: #ef4444;
      font-weight: 700;
    }
  }

  &.cancel-btn {
    background: rgba(100, 116, 139, 0.07);
    border-color: rgba(100, 116, 139, 0.12);

    .btn-text {
      color: #64748b;
      font-weight: 600;
    }
  }

  &:active {
    transform: scale(0.9);
    
    &.download-btn {
      background: rgba(99, 102, 241, 0.15);
    }
    &.delete-btn {
      background: rgba(239, 68, 68, 0.15);
    }
    &.cancel-btn {
      background: rgba(100, 116, 139, 0.15);
    }
  }
}

.btn-text {
  font-size: 19rpx;
}
</style>
