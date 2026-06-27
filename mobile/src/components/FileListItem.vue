<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import type { FileItem } from '@/stores/file'
import { fileCoverUrl, fileHasCover, fileCoverKind, fileIsVideoCover, type FileCoverContext } from '@/utils/fileCover'
import CachedCover from '@/components/CachedCover.vue'
import { fileExtLabel, fileTypeColor, fileTypeIcon, fileTypeKind } from '@/utils/fileType'
import { fmtSize } from '@/utils/fileCover'
import { transcodeLabel } from '@/utils/fileMeta'
import { mediaTokenRef } from '@/utils/mediaToken'

function formatDate(d: string) {
  const dt = new Date(d)
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  return `${dt.getFullYear()}/${m}/${day}`
}

const props = defineProps<{
  item: FileItem
  selectMode?: boolean
  checked?: boolean
  shareCode?: string
  extractCode?: string
}>()

const coverCtx = computed<FileCoverContext | undefined>(() =>
  props.shareCode ? { shareCode: props.shareCode, extractCode: props.extractCode } : undefined
)

function coverUrl() {
  void mediaTokenRef.value
  return fileCoverUrl(props.item, coverCtx.value)
}

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'longpress'): void
  (e: 'more'): void
  (e: 'check-change', val: boolean): void
}>()

const coverBroken = ref(false)

function onMoreClick() {
  emit('more')
  emit('longpress')
}

let touchTimer: ReturnType<typeof setTimeout> | null = null
let startX = 0
let startY = 0
let isLongPress = false

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  const touch = e.touches[0]
  startX = touch.clientX
  startY = touch.clientY
  isLongPress = false

  if (touchTimer) clearTimeout(touchTimer)
  touchTimer = setTimeout(() => {
    isLongPress = true
    emit('longpress')
  }, 600)
}

function onTouchMove(e: TouchEvent) {
  if (!touchTimer) return
  const touch = e.touches[0]
  const deltaX = Math.abs(touch.clientX - startX)
  const deltaY = Math.abs(touch.clientY - startY)
  if (deltaX > 10 || deltaY > 10) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

function onTouchEnd() {
  if (touchTimer) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

function onTouchCancel() {
  if (touchTimer) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

function onItemClick() {
  if (isLongPress) {
    isLongPress = false
    return
  }
  emit('click')
}

onUnmounted(() => {
  if (touchTimer) clearTimeout(touchTimer)
})
</script>

<template>
  <view
    class="file-item cd-pressable"
    :class="{ folder: item.type === 'folder', 'is-selected': checked }"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchCancel"
    @click="onItemClick"
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
      <template v-if="fileHasCover(item) && !coverBroken">
        <CachedCover
          v-if="fileCoverKind(item) === 'image'"
          :file-id="item.id"
          :src="coverUrl()"
          :has-thumbnail="item.hasThumbnail"
          class="file-cover"
          @error="coverBroken = true"
        />
        <view v-if="fileIsVideoCover(item)" class="file-play-badge">
          <u-icon name="play-circle-fill" size="22" color="#fff" />
        </view>
      </template>
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
      <view
        v-if="item.type === 'file' && transcodeLabel(item.transcodeStatus)"
        class="file-transcode-badge"
        :class="{
          pending: !item.transcodeStatus || item.transcodeStatus === 'PENDING' || item.transcodeStatus === 'PROCESSING',
          done: item.transcodeStatus === 'DONE',
          failed: item.transcodeStatus === 'FAILED'
        }"
      >
        <text>{{ transcodeLabel(item.transcodeStatus) }}</text>
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
  position: relative;
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

.file-transcode-badge {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  z-index: 2;
  padding: 2rpx 8rpx;
  border-radius: 999rpx;
  font-size: 16rpx;
  font-weight: 600;
  color: #fff;
  background: rgba(59, 130, 246, 0.92);
}

.file-transcode-badge.done {
  background: rgba(16, 185, 129, 0.92);
}

.file-transcode-badge.failed {
  background: rgba(239, 68, 68, 0.92);
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
