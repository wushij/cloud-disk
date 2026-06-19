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
      <view class="action-fab">
        <u-icon name="arrow-upward" color="#fff" size="24" />
      </view>
      <text class="action-label">{{ uploadText || '上传文件' }}</text>
    </view>
    <view class="action-divider" />
    <view class="action-btn secondary cd-pressable" @click="$emit('folder')">
      <view class="action-icon-wrap">
        <u-icon name="plus" color="var(--cd-primary)" size="22" />
      </view>
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
  align-items: center;
  padding: 10rpx 14rpx;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border-radius: 999rpx;
  box-shadow:
    0 6rpx 28rpx rgba(15, 23, 42, 0.1),
    0 0 0 1rpx rgba(15, 23, 42, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.85);
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 12rpx 6rpx;
  border-radius: 999rpx;
}

.action-btn.primary {
  background: var(--cd-primary-gradient);
  box-shadow: 0 8rpx 24rpx rgba(1, 7, 16, 0.2), inset 0 1rpx 0 rgba(255, 255, 255, 0.15);
}

.action-fab {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-divider {
  width: 1rpx;
  height: 44rpx;
  background: var(--cd-border);
  margin: 0 6rpx;
  flex-shrink: 0;
}

.action-icon-wrap {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: var(--cd-primary-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-label {
  font-size: 24rpx;
  color: #fff;
  font-weight: 700;
  letter-spacing: 0.5rpx;
}

.action-label.secondary {
  color: var(--cd-text);
  font-weight: 600;
}
</style>
