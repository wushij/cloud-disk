<script setup lang="ts">
import HeaderIcon from '@/components/HeaderIcon.vue'

defineProps<{
  title: string
  subtitle?: string
  gradient?: boolean
  /** 图标类型：不同页面传入不同值，不传则不显示图标 */
  iconType?: 'cloud' | 'recycle' | 'share' | 'team'
  /** 图标下方的副标语（可选） */
  caption?: string
}>()
</script>

<template>
  <view class="m-header-wrap">
    <view class="m-header" :class="{ gradient }">
      <view class="m-header-bar">
        <view class="m-header-info">
          <view class="m-header-texts">
            <view class="m-header-title-row">
              <HeaderIcon v-if="iconType" :type="iconType" />
              <text class="m-title">{{ title }}</text>
              <view v-if="subtitle" class="m-subtitle-chip">
                <text class="m-subtitle-text">{{ subtitle }}</text>
              </view>
            </view>
            <text v-if="caption" class="m-caption">
              {{ caption }}
            </text>
          </view>
        </view>

        <view class="m-header-actions">
          <slot name="right" />
        </view>
      </view>

      <view v-if="$slots.extra" class="m-header-extra">
        <slot name="extra" />
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.m-header-wrap {
  padding: calc(var(--status-bar-height, 0rpx) + 20rpx) 24rpx 0;
  background: var(--cd-bg);
}

.m-header {
  position: relative;
  overflow: hidden;
  padding: 28rpx 28rpx 32rpx;
  background: var(--cd-accent-surface);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border-radius: 32rpx;
  border: 1rpx solid var(--cd-accent-border);
  box-shadow: var(--cd-accent-shadow);
}

.m-header.gradient {
  background: var(--cd-accent-surface);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border: 1rpx solid var(--cd-accent-border);
  box-shadow: var(--cd-accent-shadow);
}

.m-header-bar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.m-header-info {
  flex: 1;
  min-width: 0;
}

.m-header-texts {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.m-header-title-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-height: 44rpx;
}

.m-title {
  font-size: 38rpx;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: -0.5rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.m-subtitle-chip {
  flex-shrink: 0;
}

.m-subtitle-text {
  font-size: 22rpx;
  font-weight: 800;
  color: #000000;
  letter-spacing: 0.2rpx;
}

.m-caption {
  font-size: 20rpx;
  color: var(--cd-text-muted);
  font-weight: 400;
  letter-spacing: 0.5rpx;
}

.m-header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.m-header-extra {
  position: relative;
  z-index: 1;
  margin-top: 24rpx;
}
</style>
