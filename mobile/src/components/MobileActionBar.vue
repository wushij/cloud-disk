<script setup lang="ts">
defineProps<{
  uploadText?: string
  folderText?: string
}>()

defineEmits<{
  (e: 'upload'): void
  (e: 'folder'): void
}>()
</script>

<template>
  <view class="action-bar">
    <view class="action-btn primary cd-pressable" @click="$emit('upload')">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style="display: block;">
        <path d="M12 20V4M12 4L6 10M12 4L18 10" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <text class="action-label">{{ uploadText || '上传文件' }}</text>
    </view>
    <view class="action-divider" />
    <view class="action-btn secondary cd-pressable" @click="$emit('folder')">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" style="display: block;">
        <path d="M12 5V19M5 12H19" stroke="var(--cd-primary)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <text class="action-label secondary">{{ folderText || '新建文件夹' }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.action-bar {
  position: fixed;
  left: 28rpx;
  right: 28rpx;
  bottom: calc(var(--cd-tab-height) + 32rpx + env(safe-area-inset-bottom));
  z-index: 90;
  display: flex;
  align-items: stretch;
  padding: 8rpx;
  background: #fff;
  border-radius: 999rpx;
  box-shadow: 0 8rpx 32rpx rgba(15, 23, 42, 0.1);
  border: 1rpx solid var(--cd-border-light);
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 20rpx 12rpx;
  border-radius: 999rpx;
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.action-btn:active {
  transform: scale(0.98);
  opacity: 0.92;
}

.action-btn.primary {
  background: var(--cd-primary-gradient);
}

.action-divider {
  width: 1rpx;
  align-self: center;
  height: 36rpx;
  background: var(--cd-border-light);
  flex-shrink: 0;
}

.action-label {
  font-size: 26rpx;
  color: #fff;
  font-weight: 600;
}

.action-label.secondary {
  color: var(--cd-text);
  font-weight: 600;
}
</style>
