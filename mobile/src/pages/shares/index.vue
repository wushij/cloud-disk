<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request } from '@/api/http'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

interface ShareItem {
  id: number
  shareCode: string
  fileName?: string
  extractCode?: string
  expireTime?: string
  viewCount?: number
  downloadCount?: number
}

const auth = useAuthStore()
const list = ref<ShareItem[]>([])
const loading = ref(false)

onShow(async () => {
  if (!auth.requireLogin()) return
  loading.value = true
  try {
    const data = await request<{ content?: ShareItem[] } | ShareItem[]>({ url: '/api/share/mine' })
    list.value = Array.isArray(data) ? data : data.content || []
  } finally {
    loading.value = false
  }
})

function openShare(item: ShareItem) {
  uni.navigateTo({ url: `/pages/share/view?code=${encodeURIComponent(item.shareCode)}` })
}

function copyLink(item: ShareItem) {
  const link = `${location.origin}${location.pathname}#/pages/share/view?code=${encodeURIComponent(item.shareCode)}`
  uni.setClipboardData({
    data: link,
    success: () => uni.showToast({ title: '链接已复制', icon: 'success' })
  })
}

async function removeShare(item: ShareItem) {
  uni.showModal({
    title: '取消分享',
    content: '确定取消该分享链接？',
    success: async (res) => {
      if (!res.confirm) return
      await request({ url: `/api/share/${item.id}`, method: 'DELETE' })
      uni.showToast({ title: '已取消', icon: 'success' })
      const data = await request<{ content?: ShareItem[] } | ShareItem[]>({ url: '/api/share/mine' })
      list.value = Array.isArray(data) ? data : data.content || []
    }
  })
}
</script>

<template>
  <view class="page">
    <MobileHeader title="我的分享" :subtitle="`${list.length} 个链接`" gradient icon-type="share" />

    <scroll-view scroll-y class="scroll">
      <view v-if="loading" class="state-box"><u-loading-icon text="加载中" color="var(--cd-primary)" /></view>
      <view v-else-if="!list.length" class="state-box">
        <EmptyState
          icon="share"
          title="还没有分享"
          description="在云盘中长按文件，即可创建分享链接发给好友"
        />
      </view>
      <view v-for="item in list" :key="item.id" class="share-card">
        <view class="share-badge">
          <u-icon name="share-fill" size="18" color="#fff" />
        </view>
        <view class="share-main" @click="openShare(item)">
          <text class="share-name">{{ item.fileName || item.shareCode }}</text>
          <view class="share-stats">
            <view class="stat-pill">
              <u-icon name="eye" size="14" color="#64748b" />
              <text>{{ item.viewCount || 0 }}</text>
            </view>
            <view class="stat-pill">
              <u-icon name="download" size="14" color="#64748b" />
              <text>{{ item.downloadCount || 0 }}</text>
            </view>
            <view v-if="item.extractCode" class="stat-pill lock">
              <u-icon name="lock" size="14" color="#1e293b" />
              <text>加密</text>
            </view>
          </view>
          <text v-if="item.expireTime" class="share-expire">过期 {{ item.expireTime }}</text>
        </view>
        <view class="share-actions">
          <view class="action-chip" @click="copyLink(item)">
            <u-icon name="file-text" size="16" color="var(--cd-primary)" />
            <text>复制</text>
          </view>
          <view class="action-chip danger" @click="removeShare(item)">
            <u-icon name="trash" size="16" color="#ef4444" />
            <text>取消</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <MobileTabBar active="shares" />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(var(--cd-tab-height) + env(safe-area-inset-bottom));
  background: var(--cd-bg);
}

.scroll {
  height: calc(100vh - 200rpx);
  padding: 4rpx 0 24rpx;
}

.share-card {
  position: relative;
  margin: 0 24rpx 16rpx;
  padding: 24rpx 24rpx 20rpx 72rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
}

.share-card:active {
  transform: scale(0.985);
}

.share-badge {
  position: absolute;
  left: 22rpx;
  top: 26rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 12rpx;
  background: var(--cd-primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.12);
}

.share-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 14rpx;
}

.stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #f8fafc;
  font-size: 20rpx;
  color: var(--cd-text-secondary);
  font-weight: 500;
}

.stat-pill.lock {
  background: var(--cd-primary-muted);
  color: var(--cd-primary);
}

.share-expire {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: var(--cd-text-muted);
}

.share-actions {
  display: flex;
  gap: 14rpx;
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid var(--cd-border-light);
}

.action-chip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  padding: 14rpx 0;
  border-radius: var(--cd-radius);
  background: #f8fafc;
  font-size: 24rpx;
  color: var(--cd-primary);
  font-weight: 600;
  transition: all var(--cd-transition-fast);
}

.action-chip:active {
  transform: scale(0.95);
  background: var(--cd-primary-muted);
}

.action-chip.danger {
  color: var(--cd-danger);
}

.action-chip.danger:active {
  background: var(--cd-danger-bg);
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}
</style>
