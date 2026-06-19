<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/api/http'
import MobileHeader from '@/components/MobileHeader.vue'
import FileListItem from '@/components/FileListItem.vue'
import type { FileItem } from '@/stores/file'
import { isImageFile, isVideoFile } from '@/utils/fileCover'

const code = ref('')
const extractInput = ref('')
const verified = ref(false)
const needExtract = ref(false)
const info = ref<Record<string, unknown> | null>(null)
const items = ref<FileItem[]>([])
const loading = ref(false)

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
      previewable: !!info.value.previewable
    }
  ]
}

async function submitExtract() {
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
    uni.showToast({ title: '提取码错误', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function shareQuery() {
  return extractInput.value ? `&extractCode=${encodeURIComponent(extractInput.value)}` : ''
}

function goBack() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/disk/index' }) })
}

function openItem(item: FileItem) {
  if (item.type === 'folder') {
    uni.showToast({ title: '文件夹浏览请使用 PC 端', icon: 'none' })
    return
  }
  if (isImageFile(item)) {
    const url = encodeURIComponent(apiBase(`/share/${code.value}/preview?fileId=${item.id}${shareQuery()}`))
    uni.navigateTo({ url: `/pages/preview/image?url=${url}&name=${encodeURIComponent(item.name)}` })
    return
  }
  if (isVideoFile(item)) {
    const url = encodeURIComponent(apiBase(`/share/${code.value}/preview?fileId=${item.id}${shareQuery()}`))
    uni.navigateTo({ url: `/pages/preview/video?url=${url}&name=${encodeURIComponent(item.name)}` })
    return
  }
  const url = apiBase(`/share/${code.value}/download?fileId=${item.id}${shareQuery()}`)
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
}
</script>

<template>
  <view class="page">
    <MobileHeader
      :title="String(info?.fileName || info?.folderName || '分享详情')"
      gradient
      show-back
      @back="goBack"
    />

    <view v-if="needExtract && !verified" class="extract-panel">
      <view class="extract-icon">
        <u-icon name="lock-fill" size="36" color="var(--cd-primary)" />
      </view>
      <text class="extract-title">此分享需要提取码</text>
      <input v-model="extractInput" class="input" password placeholder="请输入提取码" />
      <button class="extract-btn" @click="submitExtract">访问分享</button>
    </view>

    <scroll-view v-else scroll-y class="scroll">
      <view v-if="loading" class="state-box"><u-loading-icon text="加载中" color="var(--cd-primary)" /></view>
      <view v-else-if="!items.length" class="state-box">
        <u-empty mode="list" text="暂无文件" />
      </view>
      <view v-else class="list-wrap">
        <FileListItem
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          :item="item"
          @click="openItem(item)"
        />
      </view>
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: var(--cd-bg);
}

.extract-panel {
  margin: 40rpx 32rpx;
  padding: 48rpx 36rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-xl);
  box-shadow: var(--cd-shadow-md);
  border: 1rpx solid var(--cd-border-light);
  text-align: center;
}

.extract-icon {
  width: 88rpx;
  height: 88rpx;
  margin: 0 auto 22rpx;
  border-radius: 26rpx;
  background: var(--cd-primary-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.extract-title {
  display: block;
  margin-bottom: 28rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text);
}

.input {
  height: 92rpx;
  margin-bottom: 22rpx;
  padding: 0 28rpx;
  background: #f8fafc;
  border-radius: var(--cd-radius-lg);
  border: 2rpx solid transparent;
  font-size: 28rpx;
  text-align: center;
  transition: all var(--cd-transition);
}

.input:focus {
  border-color: var(--cd-primary);
  background: #fff;
  box-shadow: 0 0 0 6rpx rgba(1, 7, 16, 0.04);
}

.extract-btn {
  height: 86rpx;
  line-height: 86rpx;
  border-radius: var(--cd-radius-lg);
  background: var(--cd-primary-gradient);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 10rpx 32rpx rgba(0, 0, 0, 0.15);
}

.scroll {
  height: calc(100vh - 190rpx);
}

.list-wrap {
  padding: 4rpx 0 32rpx;
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}
</style>
