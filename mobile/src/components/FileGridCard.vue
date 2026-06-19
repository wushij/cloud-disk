<script setup lang="ts">
import type { FileItem } from '@/stores/file'
import { fileCoverUrl, fileHasCover, fileCoverKind } from '@/utils/fileCover'
import { fileExtLabel, fileTypeColor } from '@/utils/fileType'
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

defineEmits<{
  (e: 'click'): void
  (e: 'longpress'): void
}>()
</script>

<template>
  <view
    class="grid-card cd-pressable"
    :class="{ folder: item.type === 'folder' }"
    @click="$emit('click')"
    @longpress="$emit('longpress')"
  >
    <view class="grid-thumb" :class="{ folder: item.type === 'folder' }">
      <image
        v-if="fileHasCover(item) && fileCoverKind(item) === 'image'"
        :src="fileCoverUrl(item)"
        class="grid-cover"
        mode="aspectFill"
      />
      <video
        v-else-if="fileHasCover(item) && fileCoverKind(item) === 'video'"
        :src="fileCoverUrl(item)"
        class="grid-cover"
        muted
        :show-center-play-btn="false"
        :controls="false"
        object-fit="cover"
      />
      <view v-else class="grid-icon" :class="{ folder: item.type === 'folder' }">
        <u-icon
          :name="item.type === 'folder' ? 'folder' : 'file-text'"
          :size="item.type === 'folder' ? 40 : 36"
          :color="item.type === 'folder' ? '#f59e0b' : fileTypeColor(item)"
        />
        <text v-if="item.type === 'file'" class="grid-ext">{{ fileExtLabel(item) }}</text>
      </view>
      <view v-if="fileCoverKind(item) === 'video'" class="grid-play-badge">
        <u-icon name="play-circle-fill" size="20" color="#fff" />
      </view>
    </view>

    <view class="grid-info">
      <text class="grid-name">{{ item.name }}</text>
      <view class="grid-meta-row">
        <text class="grid-meta">{{ item.type === 'folder' ? '文件夹' : fmtSize(item.sizeBytes || 0) }}</text>
        <text v-if="item.type === 'file' && item.createdAt" class="grid-date">{{ formatDate(item.createdAt) }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.grid-card {
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  padding: 14rpx;
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
}

.grid-card:active {
  transform: scale(0.96);
  box-shadow: var(--cd-shadow);
}

.grid-card.folder {
  background: linear-gradient(180deg, #fffbeb 0%, #fefce8 30%, #ffffff 80%);
  border-color: rgba(245, 158, 11, 0.18);
}

.grid-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--cd-radius);
  overflow: hidden;
  background: #f8fafc;
}

.grid-thumb.folder {
  background: linear-gradient(145deg, rgba(245, 158, 11, 0.14), rgba(245, 158, 11, 0.04));
}

.grid-cover {
  width: 100%;
  height: 100%;
}

.grid-icon {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  background: linear-gradient(145deg, rgba(1, 7, 16, 0.04), rgba(15, 26, 46, 0.02));
}

.grid-icon.folder {
  background: transparent;
}

.grid-ext {
  font-size: 18rpx;
  font-weight: 700;
  color: var(--cd-text-muted);
  letter-spacing: 1rpx;
}

.grid-play-badge {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.2);
  backdrop-filter: blur(4rpx);
}

.grid-info {
  margin-top: 10rpx;
  min-height: 0;
}

.grid-name {
  display: block;
  font-size: 22rpx;
  font-weight: 600;
  color: var(--cd-text);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4rpx;
  gap: 6rpx;
}

.grid-meta {
  font-size: 18rpx;
  color: var(--cd-text-muted);
  flex-shrink: 0;
}

.grid-date {
  font-size: 18rpx;
  color: var(--cd-text-muted);
  opacity: 0.7;
  flex-shrink: 0;
}
</style>
