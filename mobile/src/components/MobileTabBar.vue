<script setup lang="ts">
import { computed } from 'vue'
import { useNotificationStore } from '@/stores/notification'

defineProps<{
  active: 'disk' | 'shares' | 'teams' | 'profile'
}>()

const notifyStore = useNotificationStore()
const unreadCount = computed(() => notifyStore.unreadCount())

interface TabItem {
  key: string
  label: string
  path: string
  icon?: string
  iconType?: string
}

const tabs: TabItem[] = [
  { key: 'disk', label: '云盘', path: '/pages/disk/index', icon: 'home-fill' },
  { key: 'shares', label: '分享', path: '/pages/shares/index', icon: 'share-fill' },
  { key: 'teams', label: '团队', path: '/pages/teams/index', iconType: 'team' },
  { key: 'profile', label: '我的', path: '/pages/profile/index', icon: 'account-fill' }
]

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
          <svg
            v-if="tab.iconType === 'team'"
            class="tab-team-icon"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"
            />
          </svg>
          <u-icon
            v-else
            :name="tab.icon"
            :size="22"
            :color="active === tab.key ? '#010710' : '#94a3b8'"
          />
          <view v-if="tab.key === 'profile' && unreadCount > 0" class="tab-badge-dot">
            <text class="tab-badge-text">{{ unreadCount > 9 ? '9+' : unreadCount }}</text>
          </view>
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
  background: var(--cd-accent-surface);
  backdrop-filter: blur(24rpx);
  -webkit-backdrop-filter: blur(24rpx);
  border-radius: 999rpx;
  border: 1rpx solid var(--cd-accent-border);
  box-shadow: var(--cd-accent-shadow);
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
  position: relative;
  width: 50rpx;
  height: 50rpx;
  border-radius: 15rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition-bounce);
}

.tab-badge-dot {
  position: absolute;
  top: -2rpx;
  right: -6rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  border-radius: 999rpx;
  background: #ef4444;
  border: 2rpx solid #fff;
  box-shadow: 0 2rpx 8rpx rgba(239, 68, 68, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-badge-text {
  font-size: 18rpx;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}

.tab-icon-wrap.active {
  background: var(--cd-primary-muted);
}

.tab-team-icon {
  width: 44rpx;
  height: 44rpx;
  display: block;
}

.tab-team-icon path {
  fill: #94a3b8;
  transition: fill var(--cd-transition);
}

.tab-item.active .tab-team-icon path {
  fill: #010710;
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
