<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    show: boolean
    value: number
    rates?: number[]
    title?: string
  }>(),
  {
    rates: () => [0.5, 0.75, 1, 1.25, 1.5, 2],
    title: '播放倍速'
  }
)

const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'update:value', value: number): void
  (e: 'select', value: number): void
}>()

function formatRate(rate: number) {
  return Number.isInteger(rate) ? `${rate}x` : `${rate}x`
}

function close() {
  emit('update:show', false)
}

function onMaskClick() {
  close()
}

function selectRate(rate: number) {
  emit('update:value', rate)
  emit('select', rate)
  close()
}
</script>

<template>
  <view v-if="show" class="sheet-root" @touchmove.stop.prevent>
    <view class="sheet-mask" @click="onMaskClick" />
    <view class="sheet-panel cd-slide-up" @click.stop>
      <view class="sheet-handle" />
      <text class="sheet-title">{{ title }}</text>
      <text class="sheet-desc">选择视频播放速度</text>

      <view class="rate-grid">
        <view
          v-for="rate in rates"
          :key="rate"
          class="rate-chip cd-pressable"
          :class="{ active: value === rate }"
          @click="selectRate(rate)"
        >
          <text class="chip-text">{{ formatRate(rate) }}</text>
          <u-icon
            v-if="value === rate"
            name="checkmark"
            size="14"
            color="var(--cd-primary)"
            class="chip-check"
          />
        </view>
      </view>

      <view class="sheet-cancel cd-pressable" @click="onMaskClick">
        <text>取消</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.sheet-root {
  position: fixed;
  inset: 0;
  z-index: 10100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.sheet-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.52);
  backdrop-filter: blur(4rpx);
}

.sheet-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 12rpx 32rpx calc(24rpx + env(safe-area-inset-bottom, 0px));
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-xl) var(--cd-radius-xl) 0 0;
  box-shadow: 0 -12rpx 48rpx rgba(15, 23, 42, 0.14);
  border-top: 1rpx solid var(--cd-border-light);
}

.sheet-handle {
  width: 72rpx;
  height: 8rpx;
  margin: 8rpx auto 24rpx;
  border-radius: 999rpx;
  background: #e2e8f0;
}

.sheet-title {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
}

.sheet-desc {
  display: block;
  margin-top: 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--cd-text-muted);
}

.rate-grid {
  margin-top: 32rpx;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.rate-chip {
  position: relative;
  height: 88rpx;
  border-radius: 20rpx;
  background: #f8fafc;
  border: 2rpx solid var(--cd-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-fast);

  .chip-text {
    font-size: 28rpx;
    color: var(--cd-text-secondary);
    font-weight: 700;
  }

  .chip-check {
    position: absolute;
    top: 10rpx;
    right: 12rpx;
  }

  &.active {
    background: var(--cd-primary-muted);
    border-color: rgba(1, 7, 16, 0.22);
    box-shadow: 0 8rpx 20rpx rgba(1, 7, 16, 0.08);

    .chip-text {
      color: var(--cd-primary);
    }
  }
}

.sheet-cancel {
  margin-top: 28rpx;
  height: 88rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text-secondary);
  transition: all var(--cd-transition-fast);
}

.sheet-cancel:active {
  background: #e2e8f0;
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
    opacity: 0.6;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.cd-slide-up {
  animation: slideUp 0.28s cubic-bezier(0.22, 1, 0.36, 1) both;
}
</style>
