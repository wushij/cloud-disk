<script setup lang="ts">
import type { FileItem } from '@/stores/file'
import { fileCoverUrl, fileHasCover, fileCoverKind } from '@/utils/fileCover'
import { fileExtLabel, fileTypeColor, fileTypeIcon } from '@/utils/fileType'
import { fmtSize } from '@/utils/fileCover'

function formatDate(d: string) {
  const dt = new Date(d)
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  return `${dt.getFullYear()}/${m}/${day}`
}

defineProps<{
  item: FileItem
}>()

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'longpress'): void
}>()

function onMoreClick(e: MouseEvent) {
  // 阻止冒泡，避免触发父级 click（打开文件）
  e.stopPropagation && e.stopPropagation()
  emit('longpress')
}
</script>

<template>
  <view
    class="file-item cd-pressable"
    :class="{ folder: item.type === 'folder' }"
    @click="emit('click')"
    @longpress="emit('longpress')"
  >
    <view class="file-thumb" :class="{ cover: fileHasCover(item), folder: item.type === 'folder' }">
      <image
        v-if="fileHasCover(item) && fileCoverKind(item) === 'image'"
        :src="fileCoverUrl(item)"
        class="file-cover"
        mode="aspectFill"
      />
      <view v-else-if="fileHasCover(item) && fileCoverKind(item) === 'video'" class="file-video-wrap">
        <video
          :src="fileCoverUrl(item)"
          class="file-cover"
          muted
          :show-center-play-btn="false"
          :controls="false"
          object-fit="cover"
        />
        <view class="file-play-badge">
          <u-icon name="play-circle-fill" size="22" color="#fff" />
        </view>
      </view>
      <view v-else class="file-icon" :class="{ folder: item.type === 'folder' }">
        <u-icon
          :name="item.type === 'folder' ? 'folder' : fileTypeIcon(item)"
          size="28"
          :color="item.type === 'folder' ? '#f59e0b' : fileTypeColor(item)"
        />
        <text v-if="item.type === 'file'" class="file-ext">{{ fileExtLabel(item) }}</text>
      </view>
    </view>

    <view class="file-meta">
      <text class="file-name">{{ item.name }}</text>
      <view class="file-sub-row">
        <text class="file-sub">{{ item.type === 'folder' ? '文件夹' : fmtSize(item.sizeBytes || 0) }}</text>
        <text v-if="item.type === 'file' && item.createdAt" class="file-date">{{ formatDate(item.createdAt) }}</text>
      </view>
    </view>

    <view class="file-more" @click.stop="onMoreClick" @touchstart.stop>
      <u-icon name="more-dot-fill" color="#94a3b8" size="18" />
    </view>
  </view>
</template>

<style scoped lang="scss">
.file-item {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 20rpx 24rpx;
  margin: 0 24rpx 12rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
}

.file-item:active {
  transform: scale(0.985);
  box-shadow: var(--cd-shadow);
}

.file-item.folder {
  background: linear-gradient(90deg, #fffbeb 0%, #fefce8 20%, #ffffff 55%);
  border-color: rgba(245, 158, 11, 0.13);
}

.file-thumb {
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--cd-radius);
  overflow: hidden;
  flex-shrink: 0;
  background: #f8fafc;
}

.file-thumb.folder {
  background: rgba(245, 158, 11, 0.08);
}

.file-thumb.cover {
  box-shadow: inset 0 0 0 1rpx rgba(0, 0, 0, 0.03);
}

.file-cover {
  width: 100%;
  height: 100%;
}

.file-video-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.file-play-badge {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(2rpx);
}

.file-icon {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  background: linear-gradient(145deg, rgba(1, 7, 16, 0.04), rgba(15, 26, 46, 0.02));
}

.file-icon.folder {
  background: transparent;
}

.file-ext {
  font-size: 16rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  letter-spacing: 0.5rpx;
}

.file-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5rpx;
}

.file-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-sub-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.file-sub {
  font-size: 22rpx;
  color: var(--cd-text-muted);
}

.file-date {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  opacity: 0.7;
}

.file-more {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cd-radius-sm);
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border-light);
  flex-shrink: 0;
  transition: all var(--cd-transition);
}

.file-more:active {
  background: #e2e8f0;
  transform: scale(0.9);
}
</style>
