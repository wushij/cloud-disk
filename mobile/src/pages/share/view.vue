<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import FileListItem from '@/components/FileListItem.vue'
import type { FileItem } from '@/stores/file'
import { isImageFile, isVideoFile, shareSingleCoverUrl } from '@/utils/fileCover'

const code = ref('')
const extractInput = ref('')
const verified = ref(false)
const needExtract = ref(false)
const info = ref<Record<string, unknown> | null>(null)
const items = ref<FileItem[]>([])
const loading = ref(false)
const actionVisible = ref(false)
const actionItem = ref<FileItem | null>(null)

const headerTitle = computed(() => {
  const raw = String(info.value?.fileName || info.value?.folderName || '分享详情')
  const name = raw.split('/').filter(Boolean).pop() || raw
  return name.length > 28 ? `${name.slice(0, 28)}…` : name
})

const actionList = computed(() => {
  const row = actionItem.value
  if (!row || row.type !== 'file') return [] as { name: string }[]
  const list: { name: string }[] = []
  if (isImageFile(row)) list.push({ name: '预览图片' })
  if (isVideoFile(row)) list.push({ name: '播放视频' })
  const isPdf = (row.name || '').toLowerCase().endsWith('.pdf') || (row.mimeType || '').toLowerCase() === 'application/pdf'
  if (row.previewable && isPdf) list.push({ name: '预览 PDF' })
  list.push({ name: '下载' })
  return list
})

onLoad((query) => {
  code.value = (query?.code as string) || ''
  if (code.value) void loadInfo()
})

function apiBase(path: string) {
  const base = import.meta.env.VITE_API_BASE || ''
  return `${base}${path}`
}

async function loadInfo() {
  loading.value = true
  try {
    const data = await request<Record<string, unknown>>({
      url: `/share/${code.value}`,
      skipAuth: true
    })
    info.value = data
    needExtract.value = !!data.needExtractCode
    if (!needExtract.value) {
      verified.value = true
      await loadItems()
    }
  } catch {
    uni.showToast({ title: '分享无效或已过期', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadItems() {
  if (!info.value) return
  if (info.value.shareType === 'FOLDER') {
    const q = extractInput.value ? `?extractCode=${encodeURIComponent(extractInput.value)}` : ''
    const data = await request<{ items?: FileItem[] }>({
      url: `/share/${code.value}/items${q}`,
      skipAuth: true
    })
    items.value = data.items || []
    return
  }
  items.value = [
    {
      id: Number(info.value.fileId),
      name: String(info.value.fileName || '文件'),
      type: 'file',
      sizeBytes: Number(info.value.fileSize || 0),
      mimeType: String(info.value.mimeType || ''),
      previewable: !!info.value.previewable,
      hasThumbnail: !!info.value.hasThumbnail
    }
  ]
}

const isSingleFileShare = computed(() => info.value?.shareType !== 'FOLDER')

const singleCoverUrl = computed(() => {
  if (!isSingleFileShare.value || !info.value?.fileId) return ''
  return shareSingleCoverUrl(
    Number(info.value.fileId),
    String(info.value.mimeType || ''),
    !!info.value.hasThumbnail,
    code.value,
    extractInput.value || undefined
  )
})

const showSingleCover = computed(() => {
  if (needExtract.value && !verified.value) return false
  if (!singleCoverUrl.value || !info.value?.fileId) return false
  const mime = String(info.value.mimeType || '')
  const name = String(info.value.fileName || '')
  const row = {
    id: Number(info.value.fileId),
    name,
    type: 'file' as const,
    mimeType: mime,
    hasThumbnail: !!info.value.hasThumbnail
  }
  return isImageFile(row) || isVideoFile(row) || !!info.value.hasThumbnail
})

const singleCoverIsImage = computed(() => {
  if (!info.value?.fileId) return false
  const row = {
    id: Number(info.value.fileId),
    name: String(info.value.fileName || ''),
    type: 'file' as const,
    mimeType: String(info.value.mimeType || ''),
    hasThumbnail: !!info.value.hasThumbnail
  }
  return !!info.value.hasThumbnail || isImageFile(row)
})

async function submitExtract() {
  if (!extractInput.value.trim()) {
    uni.showToast({ title: '请输入提取码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await request({
      url: `/share/${code.value}/access`,
      method: 'POST',
      data: { extractCode: extractInput.value.trim() },
      skipAuth: true
    })
    verified.value = true
    await loadItems()
  } catch {
    uni.showToast({ title: '提取码错误，请重新输入', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function shareQuery() {
  return extractInput.value ? `&extractCode=${encodeURIComponent(extractInput.value)}` : ''
}

function openItem(item: FileItem) {
  if (item.type === 'folder') {
    uni.showToast({ title: '文件夹浏览请使用 PC 端', icon: 'none' })
    return
  }
  if (isImageFile(item)) {
    previewImage(item)
    return
  }
  if (isVideoFile(item)) {
    previewVideo(item)
    return
  }
  const isPdf = (item.name || '').toLowerCase().endsWith('.pdf') || (item.mimeType || '').toLowerCase() === 'application/pdf'
  if (isPdf) {
    previewPdf(item)
    return
  }
  showActions(item)
}

function previewImage(item: FileItem) {
  const url = encodeURIComponent(apiBase(`/share/${code.value}/preview?fileId=${item.id}${shareQuery()}`))
  uni.navigateTo({ url: `/pages/preview/image?url=${url}&name=${encodeURIComponent(item.name)}` })
}

function previewVideo(item: FileItem) {
  const url = encodeURIComponent(apiBase(`/share/${code.value}/preview?fileId=${item.id}${shareQuery()}`))
  uni.navigateTo({ url: `/pages/preview/video?url=${url}&name=${encodeURIComponent(item.name)}` })
}

function previewPdf(item: FileItem) {
  const url = apiBase(`/share/${code.value}/preview?fileId=${item.id}${shareQuery()}`)
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
          fail: () => uni.showToast({ title: '打开 PDF 失败', icon: 'none' })
        })
      }
    },
    fail: () => uni.showToast({ title: '加载 PDF 失败', icon: 'none' }),
    complete: () => uni.hideLoading()
  })
  // #endif
}

function downloadFile(item: FileItem) {
  const url = apiBase(`/share/${code.value}/download?fileId=${item.id}${shareQuery()}`)
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

function showActions(item: FileItem) {
  actionItem.value = item
  actionVisible.value = true
}

function onSheetSelect(item: { name: string }) {
  const row = actionItem.value
  actionVisible.value = false
  if (!row) return
  switch (item.name) {
    case '预览图片':
      previewImage(row)
      break
    case '播放视频':
      previewVideo(row)
      break
    case '预览 PDF':
      previewPdf(row)
      break
    case '下载':
      downloadFile(row)
      break
  }
}
</script>

<template>
  <view class="page">
    <MobileHeader
      :title="headerTitle"
    />

    <view v-if="needExtract && !verified" class="extract-panel">
      <view class="extract-icon">
        <u-icon name="lock-fill" size="36" color="#ffffff" />
      </view>
      <text class="extract-title">此分享需要提取码</text>
      <input v-model="extractInput" class="input" password placeholder="请输入提取码" />
      <button class="extract-btn" @click="submitExtract">访问分享</button>
    </view>

    <scroll-view v-else scroll-y class="scroll">
      <view v-if="showSingleCover" class="share-cover-wrap">
        <image
          v-if="singleCoverIsImage"
          :src="singleCoverUrl"
          class="share-cover-img"
          mode="aspectFill"
        />
        <video
          v-else
          :src="singleCoverUrl"
          class="share-cover-img"
          muted
          :show-center-play-btn="false"
          :controls="false"
          object-fit="cover"
        />
      </view>
      <view v-if="loading" class="state-box"><u-loading-icon text="加载中" color="var(--cd-primary)" /></view>
      <view v-else-if="!items.length" class="state-box">
        <u-empty mode="list" text="暂无文件" />
      </view>
      <view v-else class="list-wrap">
        <FileListItem
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          :item="item"
          :share-code="code"
          :extract-code="extractInput || undefined"
          @click="openItem(item)"
          @more="showActions(item)"
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
  background: var(--cd-bg);
}

.extract-panel {
  margin: 80rpx 44rpx;
  padding: 72rpx 48rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-xl);
  box-shadow: var(--cd-shadow-lg);
  border: 1rpx solid var(--cd-border-light);
  text-align: center;
}

.extract-icon {
  width: 112rpx;
  height: 112rpx;
  margin: 0 auto 36rpx;
  border-radius: 36rpx;
  background: var(--cd-primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 20rpx rgba(1, 7, 16, 0.2);
}

.extract-title {
  display: block;
  margin-bottom: 40rpx;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
}

.input {
  height: 98rpx;
  margin-bottom: 32rpx;
  padding: 0 32rpx;
  background: var(--cd-bg-surface);
  border-radius: var(--cd-radius-lg);
  border: 1rpx solid var(--cd-border);
  font-size: 28rpx;
  text-align: center;
  transition: all var(--cd-transition-fast);
}

.input:focus {
  border-color: var(--cd-primary-light);
  background: #fff;
  box-shadow: 0 6rpx 20rpx rgba(1, 7, 16, 0.05);
}

.extract-btn {
  height: 90rpx;
  line-height: 90rpx;
  border-radius: var(--cd-radius-full);
  background: var(--cd-primary-gradient);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 8rpx 24rpx rgba(1, 7, 16, 0.2);
  transition: all var(--cd-transition-fast);
}

.extract-btn:active {
  transform: scale(0.97);
  box-shadow: 0 4rpx 10rpx rgba(1, 7, 16, 0.1);
}

.scroll {
  height: calc(100vh - 190rpx);
}

.list-wrap {
  padding: 4rpx 0 32rpx;
}

.share-cover-wrap {
  margin: 16rpx 24rpx 8rpx;
  border-radius: var(--cd-radius-xl);
  overflow: hidden;
  height: 360rpx;
  background: #0f172a;
  box-shadow: var(--cd-shadow-card);
}

.share-cover-img {
  width: 100%;
  height: 100%;
  display: block;
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}
</style>
