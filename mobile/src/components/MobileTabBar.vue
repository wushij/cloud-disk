<script setup lang="ts">
defineProps<{
  active: 'disk' | 'shares' | 'recycle' | 'profile'
}>()

const tabs = [
  { key: 'disk', label: '云盘', path: '/pages/disk/index', icon: 'home-fill' },
  { key: 'shares', label: '分享', path: '/pages/shares/index', icon: 'share-fill' },
  { key: 'recycle', label: '回收站', path: '/pages/recycle/index', icon: 'trash-fill' },
  { key: 'profile', label: '我的', path: '/pages/profile/index', icon: 'account-fill' }
] as const

function switchTab(path: string) {
  uni.reLaunch({ url: path })
}
</script>

<template>
  <view class="tab-bar">
    <view class="tab-bar-pill">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item cd-pressable"
        :class="{ active: active === tab.key }"
        @click="switchTab(tab.path)"
      >
        <view class="tab-icon-wrap" :class="{ active: active === tab.key }">
          <u-icon
            :name="tab.icon"
            :size="22"
            :color="active === tab.key ? '#010710' : '#94a3b8'"
          />
        </view>
        <text class="tab-label">{{ tab.label }}</text>
        <view v-if="active === tab.key" class="tab-dot" />
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.tab-bar {
  position: fixed;
  left: 20rpx;
  right: 20rpx;
  bottom: calc(20rpx + env(safe-area-inset-bottom));
  z-index: 100;
  pointer-events: none;
}

.tab-bar-pill {
  pointer-events: auto;
  display: flex;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border-radius: 999rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.85);
  box-shadow:
    0 6rpx 36rpx rgba(15, 23, 42, 0.12),
    0 0 0 1rpx rgba(15, 23, 42, 0.04),
    inset 0 1rpx 0 rgba(255, 255, 255, 0.8);
  padding: 8rpx 6rpx;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  padding: 8rpx 0 6rpx;
  position: relative;
}

.tab-icon-wrap {
  width: 50rpx;
  height: 50rpx;
  border-radius: 15rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-bounce);
}

.tab-icon-wrap.active {
  background: var(--cd-primary-muted);
}

.tab-label {
  font-size: 18rpx;
  color: var(--cd-text-muted);
  font-weight: 500;
  transition: all var(--cd-transition);
}

.tab-item.active .tab-label {
  color: var(--cd-primary);
  font-weight: 700;
}

.tab-dot {
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: var(--cd-primary);
  margin-top: 4rpx;
}
</style>
