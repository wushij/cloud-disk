<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fmtSize, fileCoverUrl, fileHasCover, fileCoverKind } from '@/utils/fileCover'
import { fileExtLabel, fileTypeColor, fileTypeIcon } from '@/utils/fileType'

interface RecycleItem {
  id: number
  name: string
  type: 'file' | 'folder'
  sizeBytes?: number
  deletedAt?: string
  mimeType?: string | null
  hasThumbnail?: boolean
}

const auth = useAuthStore()
const list = ref<RecycleItem[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const data = await request<{ content?: RecycleItem[] } | RecycleItem[]>({ url: '/api/recycle' })
    list.value = Array.isArray(data) ? data : data.content || []
  } catch {
    /* handled */
  } finally {
    loading.value = false
  }
}

onShow(async () => {
  if (!auth.requireLogin()) return
  await loadList()
})

function formatDate(d?: string) {
  if (!d) return ''
  const dt = new Date(d)
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  const h = String(dt.getHours()).padStart(2, '0')
  const min = String(dt.getMinutes()).padStart(2, '0')
  return `删除于 ${dt.getFullYear()}/${m}/${day} ${h}:${min}`
}

async function restoreItem(item: RecycleItem) {
  const url =
    item.type === 'folder'
      ? `/api/recycle/restore/folder/${item.id}`
      : `/api/recycle/restore/file/${item.id}`
  await request({ url, method: 'POST' })
  uni.showToast({ title: '已恢复', icon: 'success' })
  await loadList()
}

async function purgeItem(item: RecycleItem) {
  uni.showModal({
    title: '彻底删除',
    content: `确定彻底删除「${item.name}」？此操作无法撤销！`,
    success: async (res) => {
      if (!res.confirm) return
      const url =
        item.type === 'folder' ? `/api/recycle/folder/${item.id}` : `/api/recycle/file/${item.id}`
      await request({ url, method: 'DELETE' })
      uni.showToast({ title: '已删除', icon: 'success' })
      await loadList()
    }
  })
}

function confirmClearAll() {
  uni.showModal({
    title: '清空回收站',
    content: '确定要清空回收站中的所有项目吗？此操作将永久删除且无法撤销！',
    success: async (res) => {
      if (!res.confirm) return
      await request({ url: '/api/recycle/clear', method: 'DELETE' })
      uni.showToast({ title: '回收站已清空', icon: 'success' })
      await loadList()
    }
  })
}
</script>

<template>
  <view class="page">
    <MobileHeader title="回收站" :subtitle="`${list.length} 项待处理`" gradient icon-type="recycle">
      <template #right>
        <view v-if="list.length > 0" class="clear-all-btn cd-pressable" @click="confirmClearAll">
          <u-icon name="trash" size="16" color="#fff" />
          <text class="clear-text">清空</text>
        </view>
      </template>
    </MobileHeader>

    <scroll-view scroll-y class="scroll">
      <view v-if="loading" class="state-box">
        <u-loading-icon text="加载中..." color="var(--cd-primary)" />
      </view>
      <view v-else-if="!list.length" class="state-box">
        <EmptyState
          icon="trash"
          title="回收站是空的"
          description="删除的文件会在这里保留，可随时恢复或彻底删除"
        />
      </view>
      <view v-else class="list-wrapper">
        <view v-for="item in list" :key="`${item.type}-${item.id}`" class="recycle-card">
          <!-- 封面或图标展示区域 -->
          <view class="recycle-thumb" :class="{ cover: fileHasCover(item as any), folder: item.type === 'folder' }">
            <image
              v-if="fileHasCover(item as any) && fileCoverKind(item as any) === 'image'"
              :src="fileCoverUrl(item as any)"
              class="recycle-cover"
              mode="aspectFill"
            />
            <view v-else-if="fileHasCover(item as any) && fileCoverKind(item as any) === 'video'" class="recycle-video-wrap">
              <video
                :src="fileCoverUrl(item as any)"
                class="recycle-cover"
                muted
                :show-center-play-btn="false"
                :controls="false"
                object-fit="cover"
              />
              <view class="recycle-play-badge">
                <u-icon name="play-circle-fill" size="18" color="#fff" />
              </view>
            </view>
            <view v-else class="recycle-file-icon" :class="{ folder: item.type === 'folder' }">
              <u-icon
                :name="item.type === 'folder' ? 'folder' : fileTypeIcon(item as any)"
                size="24"
                :color="item.type === 'folder' ? '#f59e0b' : fileTypeColor(item as any)"
              />
              <text v-if="item.type === 'file'" class="recycle-ext">{{ fileExtLabel(item as any) }}</text>
            </view>
          </view>

          <!-- 主文本区域 -->
          <view class="recycle-main">
            <text class="name">{{ item.name }}</text>
            <view class="recycle-meta-row">
              <text class="meta">{{ item.type === 'folder' ? '文件夹' : fmtSize(item.sizeBytes || 0) }}</text>
              <text v-if="item.deletedAt" class="delete-date">{{ formatDate(item.deletedAt) }}</text>
            </view>
          </view>

          <!-- 操作按钮区域（左右横排） -->
          <view class="actions">
            <view class="btn restore cd-pressable" @click="restoreItem(item)">
              <text>恢复</text>
            </view>
            <view class="btn purge cd-pressable" @click="purgeItem(item)">
              <text>删除</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <MobileTabBar active="recycle" />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(var(--cd-tab-height) + env(safe-area-inset-bottom));
  background: var(--cd-bg);
}

.scroll {
  height: calc(100vh - 200rpx);
  padding: 4rpx 0 24rpx;
}

.clear-all-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(8rpx);
  transition: all var(--cd-transition);
  &:active {
    background: rgba(255, 255, 255, 0.28);
    transform: scale(0.94);
  }
}

.clear-text {
  font-size: 22rpx;
  color: #fff;
  font-weight: 700;
}

.list-wrapper {
  padding-top: 8rpx;
  padding-bottom: 32rpx;
}

.recycle-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 0 24rpx 14rpx;
  padding: 24rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
  
  &:active {
    transform: scale(0.99);
  }
}

/* ============ 缩略图/图标 ============ */
.recycle-thumb {
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--cd-radius);
  overflow: hidden;
  flex-shrink: 0;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recycle-thumb.folder {
  background: rgba(245, 158, 11, 0.08);
}

.recycle-thumb.cover {
  box-shadow: inset 0 0 0 1rpx rgba(0, 0, 0, 0.03);
}

.recycle-cover {
  width: 100%;
  height: 100%;
}

.recycle-video-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.recycle-play-badge {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(2rpx);
}

.recycle-file-icon {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  background: linear-gradient(145deg, rgba(1, 7, 16, 0.04), rgba(15, 26, 46, 0.02));
}

.recycle-file-icon.folder {
  background: transparent;
}

.recycle-ext {
  font-size: 16rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  letter-spacing: 0.5rpx;
}

/* ============ 主文本 ============ */
.recycle-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recycle-meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.meta {
  font-size: 22rpx;
  color: var(--cd-text-muted);
}

.delete-date {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  opacity: 0.75;
}

/* ============ 操作按钮 ============ */
.actions {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-shrink: 0;
}

.btn {
  padding: 10rpx 20rpx;
  border-radius: var(--cd-radius-xs);
  font-size: 22rpx;
  font-weight: 600;
  text-align: center;
  transition: all var(--cd-transition-fast);
}

.btn:active {
  transform: scale(0.92);
  opacity: 0.85;
}

.btn.restore {
  background: var(--cd-primary-muted);
  color: var(--cd-primary);
  &:active {
    background: var(--cd-primary-muted-strong);
  }
}

.btn.purge {
  background: var(--cd-danger-bg);
  color: var(--cd-danger);
  &:active {
    background: rgba(239, 68, 68, 0.14);
  }
}

.state-box {
  padding: 140rpx 0;
  display: flex;
  justify-content: center;
}
</style>
