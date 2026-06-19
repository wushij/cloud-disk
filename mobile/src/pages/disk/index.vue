<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useFileStore, type FileItem } from '@/stores/file'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileActionBar from '@/components/MobileActionBar.vue'
import BreadcrumbBar from '@/components/BreadcrumbBar.vue'
import FileListItem from '@/components/FileListItem.vue'
import FileGridCard from '@/components/FileGridCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { uploadSimpleFile } from '@/utils/upload'
import { fileApiUrl } from '@/api/http'
import { isImageFile, isVideoFile } from '@/utils/fileCover'

const auth = useAuthStore()
const fileStore = useFileStore()
const { breadcrumb, items, loading, keyword } = storeToRefs(fileStore)

const viewMode = ref<'grid' | 'list'>('list')
const actionVisible = ref(false)
const actionItem = ref<FileItem | null>(null)
const uploading = ref(false)
const uploadPercent = ref(0)

const actionList = computed(() => {
  const row = actionItem.value
  if (!row) return [] as { name: string }[]
  const list: { name: string }[] = []
  if (row.type === 'folder') list.push({ name: '打开' })
  if (row.type === 'file') {
    if (row.previewable && isImageFile(row)) list.push({ name: '预览图片' })
    if (row.previewable && isVideoFile(row)) list.push({ name: '播放视频' })
    list.push({ name: '下载' })
  }
  list.push({ name: '重命名' })
  list.push({ name: '删除' })
  return list
})

const pageSubtitle = computed(() => `${items.value.length} 项`)

onShow(async () => {
  if (!auth.requireLogin()) return
  await fileStore.loadList()
})

function onSearch() {
  fileStore.loadList()
}

function toggleView() {
  viewMode.value = viewMode.value === 'grid' ? 'list' : 'grid'
}

function openItem(row: FileItem) {
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
    case '下载':
      downloadFile(row)
      break
    case '重命名':
      promptRename(row)
      break
    case '删除':
      confirmDelete(row)
      break
  }
}

function previewImage(row: FileItem) {
  const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
  uni.navigateTo({ url: `/pages/preview/image?url=${url}&name=${encodeURIComponent(row.name)}` })
}

function previewVideo(row: FileItem) {
  const url = encodeURIComponent(fileApiUrl(`/api/files/${row.id}/preview`))
  uni.navigateTo({ url: `/pages/preview/video?url=${url}&name=${encodeURIComponent(row.name)}` })
}

function downloadFile(row: FileItem) {
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
}

function promptRename(row: FileItem) {
  uni.showModal({
    title: '重命名',
    editable: true,
    placeholderText: row.name,
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return
      await fileStore.renameItem(row, res.content.trim())
      uni.showToast({ title: '已重命名', icon: 'success' })
    }
  })
}

function confirmDelete(row: FileItem) {
  uni.showModal({
    title: '确认删除',
    content: `确定删除「${row.name}」？`,
    success: async (res) => {
      if (!res.confirm) return
      await fileStore.deleteItem(row)
      uni.showToast({ title: '已移入回收站', icon: 'success' })
    }
  })
}

function promptCreateFolder() {
  uni.showModal({
    title: '新建文件夹',
    editable: true,
    placeholderText: '文件夹名称',
    success: async (res) => {
      if (!res.confirm || !res.content?.trim()) return
      await fileStore.createFolder(res.content.trim())
      uni.showToast({ title: '创建成功', icon: 'success' })
    }
  })
}

async function chooseAndUpload() {
  // #ifdef H5
  uni.chooseFile({
    count: 9,
    success: async (res) => {
      const paths = res.tempFilePaths as string[]
      for (let i = 0; i < paths.length; i++) {
        await doUpload(paths[i])
      }
    }
  })
  // #endif
  // #ifndef H5
  uni.chooseMessageFile({
    count: 9,
    type: 'all',
    success: async (res) => {
      for (const file of res.tempFiles) {
        await doUpload(file.path)
      }
    },
    fail: () => {
      uni.chooseImage({
        count: 9,
        success: async (res) => {
          for (const path of res.tempFilePaths) {
            await doUpload(path)
          }
        }
      })
    }
  })
  // #endif
}

async function doUpload(path: string) {
  uploading.value = true
  uploadPercent.value = 0
  try {
    await uploadSimpleFile(path, '', fileStore.currentFolderId, (ratio) => {
      uploadPercent.value = Math.round(ratio * 100)
    })
    uni.showToast({ title: '上传成功', icon: 'success' })
    await fileStore.loadList()
  } catch {
    /* handled */
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}
</script>

<template>
  <view class="disk-page">
    <MobileHeader
      title="我的云盘"
      :subtitle="pageSubtitle"
      gradient
      icon-type="cloud"
      :show-back="breadcrumb.length > 1"
      @back="fileStore.goBackFolder()"
    >
      <template #right>
        <view class="view-toggle" @click="toggleView">
          <u-icon :name="viewMode === 'grid' ? 'list' : 'grid'" size="20" color="rgba(255,255,255,0.9)" />
        </view>
      </template>
      <template #extra>
        <BreadcrumbBar :crumbs="breadcrumb" @select="fileStore.gotoCrumb" />
        <view class="search-wrap">
          <view class="search-box">
            <u-icon name="search" size="16" color="rgba(255,255,255,0.5)" class="search-icon" />
            <u-search
              v-model="keyword"
              placeholder="搜索文件名"
              bg-color="transparent"
              color="#fff"
              placeholder-color="rgba(255,255,255,0.42)"
              :show-action="false"
              shape="round"
              @search="onSearch"
              @clear="onSearch"
            />
          </view>
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="file-scroll">
      <!-- 加载状态 -->
      <view v-if="loading" class="state-box">
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
              @click="openItem(item)"
              @longpress="showActions(item)"
            />
          </view>
        </view>

        <!-- 列表视图 -->
        <view v-else class="file-list">
          <FileListItem
            v-for="item in items"
            :key="`${item.type}-${item.id}`"
            :item="item"
            @click="openItem(item)"
            @longpress="showActions(item)"
          />
        </view>
      </template>
    </scroll-view>

    <!-- 上传进度条 -->
    <view v-if="uploading" class="upload-toast">
      <view class="upload-toast-head">
        <u-loading-icon size="18" />
        <text>上传中 {{ uploadPercent }}%</text>
      </view>
      <u-line-progress :percentage="uploadPercent" active-color="#010710" height="6" />
    </view>

    <MobileActionBar @upload="chooseAndUpload" @folder="promptCreateFolder" />
    <MobileTabBar active="disk" />

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
  margin-top: 18rpx;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 14rpx;
  background: rgba(255, 255, 255, 0.07);
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  border-radius: 999rpx;
  padding: 0 28rpx 0 22rpx;
  height: 74rpx;
  overflow: hidden;
}

.search-icon {
  flex-shrink: 0;
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
</style>
