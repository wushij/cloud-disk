<script setup lang="ts">
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import type { FileItem } from '@/stores/file'
import { fileCoverUrl, fileHasCover, fileCoverKind } from '@/utils/fileCover'
import { fileExtLabel, fileTypeColor, fileTypeIcon, fileTypeKind } from '@/utils/fileType'
import { fmtSize } from '@/utils/fileCover'

function formatDate(d: string) {
  const dt = new Date(d)
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  return `${dt.getFullYear()}/${m}/${day}`
}

defineProps<{
  item: FileItem
  selectMode?: boolean
  checked?: boolean
}>()

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'longpress'): void
  (e: 'check-change', val: boolean): void
}>()
</script>

<template>
  <view
    class="grid-card cd-pressable"
    :class="{ folder: item.type === 'folder', 'is-selected': checked }"
    @click="$emit('click')"
    @longpress="$emit('longpress')"
  >
    <!-- 选择勾选框 -->
    <view v-if="selectMode" class="grid-checkbox-wrap" @click.stop="emit('check-change', !checked)">
      <view class="grid-checkbox-circle" :class="{ 'is-checked': checked }">
        <u-icon v-if="checked" name="checkbox-mark" color="#fff" size="8" />
      </view>
    </view>

    <view
      class="grid-thumb"
      :class="{
        folder: item.type === 'folder' || fileTypeKind(item) === 'archive',
        [`kind-${fileTypeKind(item)}`]: item.type === 'file' && !fileHasCover(item)
      }"
    >
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
      <view v-else class="grid-icon" :class="fileTypeKind(item)">
        <FolderTypeIcon
          v-if="item.type === 'folder'"
          :size="52"
        />
        <FolderTypeIcon
          v-else-if="fileTypeKind(item) === 'archive'"
          archive
          :size="52"
        />
        <template v-else>
          <u-icon
            :name="fileTypeIcon(item)"
            size="36"
            :color="fileTypeColor(item)"
          />
          <text class="grid-ext">{{ fileExtLabel(item) }}</text>
        </template>
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
  background: transparent;
}

.grid-thumb.kind-archive {
  background: transparent;
  border: none;
}

.grid-thumb.kind-pdf {
  background: linear-gradient(145deg, #fef2f2 0%, #fee2e2 100%);
  border: 1rpx solid rgba(239, 68, 68, 0.12);
}

.grid-thumb.kind-doc {
  background: linear-gradient(145deg, #eff6ff 0%, #dbeafe 100%);
  border: 1rpx solid rgba(37, 99, 235, 0.12);
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

.grid-icon.archive {
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

.grid-card.is-selected {
  border-color: rgba(99, 102, 241, 0.35) !important;
  background: rgba(99, 102, 241, 0.04) !important;
}

.grid-checkbox-wrap {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  z-index: 5;
}

.grid-checkbox-circle {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 3rpx solid #cbd5e1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-fast);
}

.grid-checkbox-circle.is-checked {
  background: var(--cd-primary, #6366f1);
  border-color: var(--cd-primary, #6366f1);
}
</style>
