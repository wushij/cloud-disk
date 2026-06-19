<script setup lang="ts">
withDefaults(
  defineProps<{
    show: boolean
    title: string
    message?: string
    confirmText?: string
    cancelText?: string
    /** 危险操作（如退出登录）使用红色确认按钮 */
    danger?: boolean
  }>(),
  {
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    danger: false
  }
)

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

function close() {
  emit('update:show', false)
}

function onCancel() {
  emit('cancel')
  close()
}

function onConfirm() {
  emit('confirm')
  close()
}
</script>

<template>
  <view v-if="show" class="dialog-root" @touchmove.stop.prevent>
    <view class="dialog-mask" @click="onCancel" />
    <view class="dialog-panel cd-scale-in" @click.stop>
      <view v-if="danger" class="dialog-icon dialog-icon--danger">
        <u-icon name="info-circle-fill" size="28" color="#ef4444" />
      </view>
      <text class="dialog-title">{{ title }}</text>
      <text v-if="message" class="dialog-message">{{ message }}</text>
      <view v-else-if="$slots.default" class="dialog-message">
        <slot />
      </view>

      <view class="dialog-actions">
        <view class="dialog-btn dialog-btn--ghost cd-pressable" @click="onCancel">
          <text>{{ cancelText }}</text>
        </view>
        <view
          class="dialog-btn cd-pressable"
          :class="danger ? 'dialog-btn--danger' : 'dialog-btn--primary'"
          @click="onConfirm"
        >
          <text>{{ confirmText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.dialog-root {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}

.dialog-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(6rpx);
}

.dialog-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 620rpx;
  padding: 44rpx 36rpx 32rpx;
  background: var(--cd-bg-card);
  border-radius: 32rpx;
  box-shadow:
    0 24rpx 64rpx rgba(15, 23, 42, 0.18),
    0 0 0 1rpx rgba(255, 255, 255, 0.6) inset;
  border: 1rpx solid var(--cd-border-light);
}

.dialog-icon {
  width: 88rpx;
  height: 88rpx;
  margin: 0 auto 20rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog-icon--danger {
  background: rgba(239, 68, 68, 0.1);
}

.dialog-title {
  display: block;
  text-align: center;
  font-size: 34rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
}

.dialog-message {
  display: block;
  margin-top: 16rpx;
  text-align: center;
  font-size: 26rpx;
  line-height: 1.55;
  color: var(--cd-text-secondary);
}

.dialog-actions {
  margin-top: 36rpx;
  display: flex;
  gap: 16rpx;
}

.dialog-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  transition: all var(--cd-transition-fast);
}

.dialog-btn--ghost {
  background: #f1f5f9;
  color: var(--cd-text-secondary);
  border: 1rpx solid var(--cd-border);
}

.dialog-btn--ghost:active {
  background: #e2e8f0;
}

.dialog-btn--primary {
  background: var(--cd-primary-gradient);
  color: #fff;
  box-shadow: 0 10rpx 28rpx rgba(1, 7, 16, 0.2);
}

.dialog-btn--primary:active {
  transform: scale(0.98);
}

.dialog-btn--danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: #fff;
  box-shadow: 0 10rpx 28rpx rgba(239, 68, 68, 0.28);
}

.dialog-btn--danger:active {
  transform: scale(0.98);
  opacity: 0.92;
}
</style>
