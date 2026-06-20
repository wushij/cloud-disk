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
  (e: 'more'): void
  (e: 'check-change', val: boolean): void
}>()

function onMoreClick() {
  emit('more')
  emit('longpress')
}
</script>

<template>
  <view
    class="file-item cd-pressable"
    :class="{ folder: item.type === 'folder', 'is-selected': checked }"
    @click="emit('click')"
    @longpress="emit('longpress')"
  >
    <!-- 选择勾选框 -->
    <view v-if="selectMode" class="file-checkbox-area" @click.stop="emit('check-change', !checked)">
      <view class="file-checkbox-circle" :class="{ 'is-checked': checked }">
        <u-icon v-if="checked" name="checkbox-mark" color="#fff" size="10" />
      </view>
    </view>

    <view
      class="file-thumb"
      :class="{
        cover: fileHasCover(item),
        folder: item.type === 'folder',
        [`kind-${fileTypeKind(item)}`]: item.type === 'file' && !fileHasCover(item)
      }"
    >
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
      <view v-else class="file-icon" :class="fileTypeKind(item)">
        <FolderTypeIcon
          v-if="item.type === 'folder'"
          :size="40"
        />
        <FolderTypeIcon
          v-else-if="fileTypeKind(item) === 'archive'"
          archive
          :size="40"
        />
        <template v-else>
          <u-icon
            :name="fileTypeIcon(item)"
            size="28"
            :color="fileTypeColor(item)"
          />
          <text class="file-ext">{{ fileExtLabel(item) }}</text>
        </template>
      </view>
    </view>

    <view class="file-meta">
      <text class="file-name">{{ item.name }}</text>
      <view class="file-sub-row">
        <text class="file-sub">{{ item.type === 'folder' ? '文件夹' : fmtSize(item.sizeBytes || 0) }}</text>
        <text v-if="item.type === 'file' && item.createdAt" class="file-date">{{ formatDate(item.createdAt) }}</text>
      </view>
    </view>

    <view v-if="!selectMode" class="file-more" @tap.stop="onMoreClick" @click.stop="onMoreClick">
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
  background: transparent;
}

.file-thumb.kind-archive {
  background: transparent;
  border: none;
}

.file-thumb.kind-pdf {
  background: linear-gradient(145deg, #fef2f2 0%, #fee2e2 100%);
  border: 1rpx solid rgba(239, 68, 68, 0.12);
}

.file-thumb.kind-doc {
  background: linear-gradient(145deg, #eff6ff 0%, #dbeafe 100%);
  border: 1rpx solid rgba(37, 99, 235, 0.12);
}

.file-thumb.kind-sheet {
  background: linear-gradient(145deg, #ecfdf5 0%, #d1fae5 100%);
  border: 1rpx solid rgba(5, 150, 105, 0.12);
}

.file-thumb.kind-slide {
  background: linear-gradient(145deg, #fffbeb 0%, #fef3c7 100%);
  border: 1rpx solid rgba(217, 119, 6, 0.12);
}

.file-thumb.kind-audio {
  background: linear-gradient(145deg, #fdf2f8 0%, #fce7f3 100%);
  border: 1rpx solid rgba(236, 72, 153, 0.12);
}

.file-thumb.kind-code {
  background: linear-gradient(145deg, #eef2ff 0%, #e0e7ff 100%);
  border: 1rpx solid rgba(99, 102, 241, 0.12);
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

.file-icon.archive,
.file-icon.pdf,
.file-icon.doc,
.file-icon.sheet,
.file-icon.slide,
.file-icon.audio,
.file-icon.code {
  background: transparent;
}

.file-icon.archive .file-ext {
  color: #c2410c;
}

.file-icon.pdf .file-ext {
  color: #dc2626;
}

.file-icon.doc .file-ext {
  color: #2563eb;
}

.file-icon.sheet .file-ext {
  color: #059669;
}

.file-icon.slide .file-ext {
  color: #d97706;
}

.file-icon.audio .file-ext {
  color: #db2777;
}

.file-icon.code .file-ext {
  color: #4f46e5;
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

.file-item.is-selected {
  background: rgba(99, 102, 241, 0.04) !important;
  border-color: rgba(99, 102, 241, 0.25) !important;
}

.file-checkbox-area {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-right: 4rpx;
  flex-shrink: 0;
}

.file-checkbox-circle {
  width: 38rpx;
  height: 38rpx;
  border-radius: 50%;
  border: 3rpx solid #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-fast);
  background: #fff;
}

.file-checkbox-circle.is-checked {
  background: var(--cd-primary, #6366f1);
  border-color: var(--cd-primary, #6366f1);
}
</style>
