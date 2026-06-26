<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useFileStore, type FileItem } from '@/stores/file'
import { useTransferStore } from '@/stores/transfer'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileActionBar from '@/components/MobileActionBar.vue'
import BreadcrumbBar from '@/components/BreadcrumbBar.vue'
import FileListItem from '@/components/FileListItem.vue'
import FileGridCard from '@/components/FileGridCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import MobilePromptDialog from '@/components/MobilePromptDialog.vue'
import MobileShareDialog from '@/components/MobileShareDialog.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { fileApiUrl, TOKEN_KEY } from '@/api/http'
import { isImageFile, isVideoFile } from '@/utils/fileCover'
import { isTextFile } from '@/utils/filePreview'
import { resolveFilePreviewUrl } from '@/utils/fileUrl'
import { ensureMediaToken } from '@/utils/mediaToken'
import { subscribeWs, type WsMessage } from '@/utils/ws'
import { useH5BackGuard } from '@/composables/useH5BackGuard'
import { downloadZip } from '@/utils/download'

const auth = useAuthStore()
const fileStore = useFileStore()
const transferStore = useTransferStore()
const { breadcrumb, items, loading, keyword } = storeToRefs(fileStore)
const { activeTaskCount } = storeToRefs(transferStore)

const viewMode = ref<'grid' | 'list'>('list')
const selectMode = ref(false)
const selectedItems = ref<FileItem[]>([])

const deleteDialogVisible = ref(false)
const itemToDelete = ref<FileItem | null>(null)

const batchDeleteDialogVisible = ref(false)

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

watch(breadcrumb, () => {
  clearSelection()
  selectMode.value = false
}, { deep: true })

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
  batchDeleteDialogVisible.value = true
}

async function handleBatchDeleteConfirm() {
  uni.showLoading({ title: '正在删除...' })
  try {
    for (const item of selectedItems.value) {
      await fileStore.deleteItem(item)
    }
    uni.showToast({ title: '批量删除成功', icon: 'success' })
    clearSelection()
    selectMode.value = false
  } catch {
    uni.showToast({ title: '删除失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}
const actionVisible = ref(false)
const actionItem = ref<FileItem | null>(null)
const renameVisible = ref(false)
const renameTarget = ref<FileItem | null>(null)
const renaming = ref(false)
const createFolderVisible = ref(false)
const creatingFolder = ref(false)
const shareVisible = ref(false)
const shareFileId = ref<number | null>(null)
const shareFolderId = ref<number | null>(null)
const shareItemName = ref('')

function goTransfer() {
  uni.navigateTo({ url: '/pages/transfer/index' })
}

const actionList = computed(() => {
  const row = actionItem.value
  if (!row) return [] as { name: string }[]
  const list: { name: string }[] = []
  if (row.type === 'folder') {
    list.push({ name: '打开' })
    list.push({ name: '打包下载' })
  }
  if (row.type === 'file') {
    if (row.previewable && isImageFile(row)) list.push({ name: '预览图片' })
    if (row.previewable && isVideoFile(row)) list.push({ name: '播放视频' })
    if (row.previewable && isTextFile(row.mimeType, row.name)) list.push({ name: '预览文本' })
    const isPdf = (row.name || '').toLowerCase().endsWith('.pdf') || (row.mimeType || '').toLowerCase() === 'application/pdf'
    if (row.previewable && isPdf) list.push({ name: '预览 PDF' })
    if (row.previewable && row.officeFile) list.push({ name: '预览文档' })
    list.push({ name: '下载' })
  }
  list.push({ name: '分享' })
  list.push({ name: '重命名' })
  list.push({ name: '删除' })
  return list
})

const pageSubtitle = computed(() => `${items.value.length} 项`)

onLoad(async (query) => {
  const folderId = Number(query?.folderId || 0)
  if (folderId > 0) {
    fileStore.currentFolderId = folderId
    await fileStore.loadBreadcrumbs(folderId)
  } else {
    fileStore.currentFolderId = 0
    fileStore.breadcrumb = [{ id: 0, name: '全部文件' }]
  }
})

onShow(async () => {
  uni.hideTabBar({ animation: false }).catch(() => {})
  if (!auth.requireLogin()) return
  try {
    await ensureMediaToken()
  } catch {
    /* 预览/封面稍后重试 */
  }
  if (fileStore.needsRefresh || !fileStore.listInitialized) {
    await fileStore.loadList()
  }
})

onMounted(() => {
  uni.$on('refresh-file-list', refreshList)
  unsubscribeWs = subscribeWs(onWsMessage)
  syncTranscodePoll()
})

useH5BackGuard({
  depth: () => breadcrumb.value.length - 1,
  onAppBack: () => fileStore.goBackFolder()
})

let transcodePollTimer: ReturnType<typeof setInterval> | null = null
let unsubscribeWs: (() => void) | null = null

function onWsMessage(data: WsMessage) {
  if (data.type === 'notification' && data.notifyType === 'TRANSCODE_DONE') {
    fileStore.onTranscodeEvent(data.refId)
  }
}

function stopTranscodePoll() {
  if (transcodePollTimer) {
    clearInterval(transcodePollTimer)
    transcodePollTimer = null
  }
}

function syncTranscodePoll() {
  if (!fileStore.hasActiveTranscode(items.value)) {
    stopTranscodePoll()
    return
  }
  if (transcodePollTimer) return
  transcodePollTimer = setInterval(() => {
    if (!fileStore.hasActiveTranscode(items.value)) {
      stopTranscodePoll()
      return
    }
    void fileStore.loadList()
  }, 5000)
}

watch(items, () => syncTranscodePoll(), { deep: true })

onUnmounted(() => {
  uni.$off('refresh-file-list', refreshList)
  unsubscribeWs?.()
  stopTranscodePoll()
})

async function refreshList() {
  try {
    fileStore.markListStale()
    await fileStore.loadList()
  } catch {
    /* 请求层已提示 */
  }
}

function onSearch() {
  fileStore.loadList()
}

function toggleView() {
  viewMode.value = viewMode.value === 'grid' ? 'list' : 'grid'
}

function openItem(row: FileItem) {
  if (selectMode.value) {
    toggleChecked(row)
    return
  }
  if (row.type === 'folder') {
    fileStore.enterFolder(row)
    return
  }
  if (isImageFile(row)) {
    previewImage(row)
    return
  }
  if (isVideoFile(row)) {
    previewVideo(row)
    return
  }
  if (isTextFile(row.mimeType, row.name)) {
    previewText(row)
    return
  }
  const isPdf = (row.name || '').toLowerCase().endsWith('.pdf') || (row.mimeType || '').toLowerCase() === 'application/pdf'
  if (isPdf) {
    previewPdf(row)
    return
  }
  if (row.officeFile) {
    previewOffice(row)
    return
  }
  showActions(row)
}

function showActions(row: FileItem) {
  actionItem.value = row
  actionVisible.value = true
}

function onSheetSelect(item: { name: string }) {
  onActionSelect(item.name)
}

function onActionSelect(name: string) {
  const row = actionItem.value
  actionVisible.value = false
  if (!row) return
  switch (name) {
    case '打开':
      fileStore.enterFolder(row)
      break
    case '预览图片':
      previewImage(row)
      break
    case '播放视频':
      previewVideo(row)
      break
    case '预览文本':
      previewText(row)
      break
    case '预览 PDF':
      previewPdf(row)
      break
    case '预览文档':
      previewOffice(row)
      break
    case '下载':
    case '打包下载':
      downloadFile(row)
      break
    case '分享':
      openShare(row)
      break
    case '重命名':
      promptRename(row)
      break
    case '删除':
      confirmDelete(row)
      break
  }
}

function openShare(row: FileItem) {
  shareFileId.value = row.type === 'file' ? row.id : null
  shareFolderId.value = row.type === 'folder' ? row.id : null
  shareItemName.value = row.name
  shareVisible.value = true
}

function previewImage(row: FileItem) {
  void openImagePreview(row)
}

async function openImagePreview(row: FileItem) {
  try {
    await ensureMediaToken()
    uni.navigateTo({
      url: `/pages/preview/image?fileId=${row.id}&name=${encodeURIComponent(row.name)}`
    })
  } catch {
    uni.showToast({ title: '无法预览图片', icon: 'none' })
  }
}

function previewVideo(row: FileItem) {
  void openVideoPreview(row)
}

async function openVideoPreview(row: FileItem) {
  try {
    await ensureMediaToken()
    const url = await resolveFilePreviewUrl(row.id)
    uni.navigateTo({
      url: `/pages/preview/video?url=${encodeURIComponent(url)}&name=${encodeURIComponent(row.name)}`
    })
  } catch {
    uni.showToast({ title: '无法播放视频', icon: 'none' })
  }
}

function previewText(row: FileItem) {
  const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
  uni.navigateTo({ url: `/pages/preview/text?url=${url}&name=${encodeURIComponent(row.name)}` })
}

function previewPdf(row: FileItem) {
  const url = fileApiUrl(`/api/files/${row.id}/preview`)
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.showLoading({ title: '加载中...' })
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) {
        uni.openDocument({
          filePath: res.tempFilePath,
          fail: () => {
            uni.showToast({ title: '打开 PDF 失败', icon: 'none' })
          }
        })
      }
    },
    fail: () => {
      uni.showToast({ title: '加载 PDF 失败', icon: 'none' })
    },
    complete: () => uni.hideLoading()
  })
  // #endif
}

function previewOffice(row: FileItem) {
  uni.navigateTo({ url: `/pages/preview/office?id=${row.id}&name=${encodeURIComponent(row.name)}` })
}

function downloadFile(row: FileItem) {
  if (row.type === 'folder') {
    downloadZip(`/api/files/download/zip?folderIds=${row.id}`)
    return
  }
  transferStore.addDownloadTask(row.id, row.name, row.sizeBytes || 0)
  uni.showToast({ title: '已加入下载队列', icon: 'none' })
}

function promptRename(row: FileItem) {
  renameTarget.value = row
  renameVisible.value = true
}

async function submitRename(name: string) {
  const row = renameTarget.value
  if (!row) return
  if (!name) {
    uni.showToast({ title: '请输入名称', icon: 'none' })
    return
  }
  if (renaming.value) return
  renaming.value = true
  try {
    await fileStore.renameItem(row, name)
    renameVisible.value = false
    renameTarget.value = null
    uni.showToast({ title: '已重命名', icon: 'success' })
  } catch {
    /* handled */
  } finally {
    renaming.value = false
  }
}

function confirmDelete(row: FileItem) {
  itemToDelete.value = row
  deleteDialogVisible.value = true
}

async function handleDeleteConfirm() {
  const row = itemToDelete.value
  if (!row) return
  try {
    await fileStore.deleteItem(row)
    uni.showToast({ title: '已移入回收站', icon: 'success' })
  } catch {
    /* handled */
  } finally {
    itemToDelete.value = null
  }
}

function promptCreateFolder() {
  createFolderVisible.value = true
}

async function submitCreateFolder(name: string) {
  if (!name) {
    uni.showToast({ title: '请输入文件夹名称', icon: 'none' })
    return
  }
  if (creatingFolder.value) return
  creatingFolder.value = true
  try {
    await fileStore.createFolder(name)
    createFolderVisible.value = false
    uni.showToast({ title: '创建成功', icon: 'success' })
  } catch {
    /* handled */
  } finally {
    creatingFolder.value = false
  }
}

async function chooseAndUpload() {
  // #ifdef H5
  uni.chooseFile({
    count: 9,
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const { normalizeH5Pick } = await import('@/utils/h5Upload')
      const raw = Array.isArray(res.tempFiles) ? res.tempFiles : [res.tempFiles]
      for (const item of raw) {
        const file = normalizeH5Pick(item as File & { path?: string })
        if (!file.size) {
          uni.showToast({ title: '无法读取文件大小', icon: 'none' })
          continue
        }
        await transferStore.addUploadTask(file.path, file.name, file.size, fileStore.currentFolderId, {
          h5File: file.file,
          mimeType: file.mimeType
        })
      }
      uni.showToast({ title: '已添加到上传队列', icon: 'none' })
    },
    fail(err) {
      const msg = String((err as { errMsg?: string })?.errMsg || err || '')
      if (msg.includes('cancel')) return
      uni.showToast({ title: '选择文件失败', icon: 'none' })
    }
  })
  // #endif
  // #ifndef H5
  uni.chooseMessageFile({
    count: 9,
    type: 'all',
    success: async (res) => {
      const files = Array.isArray(res.tempFiles) ? res.tempFiles : [res.tempFiles]
      for (const file of files) {
        await transferStore.addUploadTask((file as any).path, (file as any).name, (file as any).size, fileStore.currentFolderId)
      }
      uni.showToast({ title: '已添加到上传队列', icon: 'none' })
    },
    fail: () => {
      uni.showToast({ title: '请从聊天中选择文件，或使用浏览器访问上传', icon: 'none', duration: 3000 })
    }
  })
  // #endif
}
</script>

<template>
  <view class="disk-page">
    <MobileHeader
      title="我的云盘"
      :subtitle="pageSubtitle"
      gradient
      icon-type="cloud"
    >
      <template #right>
        <view class="header-action-group">
          <!-- 多选切换按钮 -->
          <view class="header-action-btn cd-pressable" @click="toggleSelectMode">
            <u-icon :name="selectMode ? 'checkmark-circle-fill' : 'list-dot'" size="22" :color="selectMode ? 'var(--cd-primary)' : '#111827'" bold />
          </view>
          <view class="header-action-btn cd-pressable" @click="goTransfer">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="display: block;">
              <circle cx="12" cy="12" r="10" stroke="#111827" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M6.5 11L9 8.5L11.5 11M9 8.5V16.5" stroke="#111827" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M12.5 13.5L15 16L17.5 13.5M15 8V16" stroke="#111827" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <view
              v-if="activeTaskCount > 0"
              class="action-badge"
              :class="{ 'has-count': activeTaskCount > 1 }"
            >
              <text v-if="activeTaskCount > 1" class="action-badge-num">{{ activeTaskCount > 9 ? '9+' : activeTaskCount }}</text>
            </view>
          </view>
          <view class="header-action-btn cd-pressable" @click="toggleView">
            <u-icon :name="viewMode === 'grid' ? 'list' : 'grid'" size="22" color="#111827" bold />
          </view>
        </view>
      </template>
      <template #extra>
        <BreadcrumbBar :crumbs="breadcrumb" @select="fileStore.gotoCrumb" />
        <view class="search-wrap">
          <u-search
            v-model="keyword"
            placeholder="搜索文件名"
            bg-color="#f4f7fb"
            color="#0f172a"
            placeholder-color="#b0bdc9"
            :show-action="false"
            shape="round"
            height="38"
            @search="onSearch"
            @clear="onSearch"
          />
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="file-scroll">
      <!-- 加载状态 -->
      <view v-if="loading && !items.length" class="state-box">
        <view class="loading-wrap">
          <u-loading-icon size="28" color="var(--cd-primary)" />
          <text class="loading-text">加载中...</text>
        </view>
      </view>

      <!-- 空状态 -->
      <EmptyState
        v-else-if="!items.length"
        icon="folder"
        title="这里还是空的"
        description="上传照片、文档或视频，随时在手机与 PC 之间同步"
      />

      <!-- 文件列表 -->
      <template v-else>
        <!-- 宫格视图 -->
        <view v-if="viewMode === 'grid'" class="file-grid">
          <view
            v-for="item in items"
            :key="`${item.type}-${item.id}`"
            class="file-grid-item"
          >
            <FileGridCard
              :item="item"
              :select-mode="selectMode"
              :checked="isChecked(item)"
              @click="openItem(item)"
              @longpress="showActions(item)"
              @check-change="toggleChecked(item)"
            />
          </view>
        </view>

        <!-- 列表视图 -->
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
      </template>
    </scroll-view>

    <!-- 上传进度交由传输列表后台管理 -->

    <MobileActionBar v-if="!selectMode" @upload="chooseAndUpload" @folder="promptCreateFolder" />
    <MobileTabBar v-if="!selectMode" active="disk" />

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

    <u-action-sheet
      :show="actionVisible"
      :actions="actionList"
      cancel-text="取消"
      round="16"
      @close="actionVisible = false"
      @select="onSheetSelect"
    />

    <MobilePromptDialog
      v-model:show="renameVisible"
      title="重命名"
      :initial-value="renameTarget?.name || ''"
      :select-stem="renameTarget?.type === 'file'"
      :maxlength="255"
      confirm-text="确定"
      @confirm="submitRename"
      @cancel="renameTarget = null"
    />

    <MobilePromptDialog
      v-model:show="createFolderVisible"
      title="新建文件夹"
      placeholder="文件夹名称"
      :maxlength="64"
      confirm-text="确定"
      @confirm="submitCreateFolder"
    />

    <MobileShareDialog
      v-model:show="shareVisible"
      :file-id="shareFileId"
      :folder-id="shareFolderId"
      :item-name="shareItemName"
    />

    <MobileConfirmDialog
      v-model:show="deleteDialogVisible"
      title="确认删除"
      :message="itemToDelete ? `确定删除「${itemToDelete.name}」？` : ''"
      confirm-text="删除"
      danger
      @confirm="handleDeleteConfirm"
    />

    <MobileConfirmDialog
      v-model:show="batchDeleteDialogVisible"
      title="确认删除"
      :message="`确定删除这 ${selectedItems.length} 个项目吗？`"
      confirm-text="删除"
      danger
      @confirm="handleBatchDeleteConfirm"
    />
  </view>
</template>

<style scoped lang="scss">
.disk-page {
  min-height: 100vh;
  padding-bottom: var(--cd-page-bottom);
  background: var(--cd-bg);
}

.view-toggle {
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition);
  &:active {
    background: rgba(255, 255, 255, 0.18);
    transform: scale(0.88);
  }
}

.search-wrap {
  margin-top: 6rpx;
}

.search-wrap :deep(.u-search) {
  padding: 0 !important;
}

.search-wrap :deep(.u-search__content) {
  border: none !important;
  background: #f4f7fb !important;
  border-radius: 999rpx !important;
}

.search-wrap :deep(.u-search__input-wrap) {
  padding: 0 16rpx !important;
}

.search-wrap :deep(.u-search__input-wrap .u-icon) {
  transform: scale(0.75);
}

.file-scroll {
  height: calc(100vh - 340rpx);
  padding-top: 16rpx;
}

/* ============ 宫格视图 ============ */
.file-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
  padding: 16rpx 24rpx 48rpx;
}

.file-grid-item {
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}

/* ============ 列表视图 ============ */
.file-list {
  padding-top: 8rpx;
  padding-bottom: 48rpx;
}

/* ============ 加载状态 ============ */
.state-box {
  padding: 140rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.loading-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.loading-text {
  font-size: 26rpx;
  color: var(--cd-text-muted);
  font-weight: 500;
}

/* ============ 上传提示条 ============ */
.upload-toast {
  position: fixed;
  left: 28rpx;
  right: 28rpx;
  bottom: calc(var(--cd-page-bottom) + 12rpx);
  z-index: 95;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  padding: 22rpx 28rpx;
  box-shadow: var(--cd-shadow-lg);
  border: 1rpx solid var(--cd-border-light);
  animation: fadeInUp 0.3s ease-out;
}

.upload-toast-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 14rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--cd-text-secondary);
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ============ 顶部操作区 ============ */
.header-action-group {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.header-action-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: transparent;
  transition: opacity var(--cd-transition-fast);
  color: #111827 !important;

  &:active {
    opacity: 0.55;
  }
}

.header-action-btn :deep(.u-icon__icon:not(.u-icon__icon--checkmark-circle-fill)) {
  color: #111827 !important;
}

.action-badge {
  position: absolute;
  top: 8rpx;
  right: 6rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 2rpx rgba(1, 7, 16, 0.55);
  pointer-events: none;
}

.action-badge.has-count {
  top: 2rpx;
  right: 0;
  width: auto;
  min-width: 24rpx;
  height: 24rpx;
  padding: 0 5rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-badge-num {
  font-size: 18rpx;
  font-weight: 700;
  color: #fff;
  line-height: 1;
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

