<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore, type AppNotification } from '@/stores/notification'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { subscribeWs } from '@/utils/ws'

const auth = useAuthStore()
const notifyStore = useNotificationStore()
const loading = ref(false)
const actingId = ref<string | null>(null)

const unread = computed(() => notifyStore.unreadCount())

let unsubscribeWs: (() => void) | null = null

function formatTime(ts: number) {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadList() {
  if (!auth.requireLogin()) return
  loading.value = true
  try {
    await notifyStore.loadFromApi()
  } catch {
    /* handled */
  } finally {
    loading.value = false
  }
}

async function onItemTap(item: AppNotification) {
  if (item.type === 'TEAM_INVITED') return
  await notifyStore.markRead(item.id)
}

async function acceptInvite(item: AppNotification) {
  if (!item.refId || actingId.value) return
  actingId.value = item.id
  try {
    await notifyStore.acceptTeamInvite(item.refId)
    await notifyStore.markRead(item.id)
    uni.showToast({ title: '已加入团队', icon: 'success' })
  } catch {
    /* handled */
  } finally {
    actingId.value = null
  }
}

async function rejectInvite(item: AppNotification) {
  if (!item.refId || actingId.value) return
  actingId.value = item.id
  try {
    await notifyStore.rejectTeamInvite(item.refId)
    await notifyStore.markRead(item.id)
    uni.showToast({ title: '已拒绝', icon: 'none' })
  } catch {
    /* handled */
  } finally {
    actingId.value = null
  }
}

async function markAllRead() {
  try {
    await notifyStore.markAllRead()
    uni.showToast({ title: '已全部已读', icon: 'success' })
  } catch {
    /* handled */
  }
}

onShow(() => {
  loadList()
  if (!auth.isLoggedIn) return
  if (unsubscribeWs) return
  unsubscribeWs = subscribeWs((data) => {
    if (data.type === 'notification') {
      notifyStore.push({
        id: data.notifyId,
        type: data.notifyType,
        title: data.title,
        content: data.content,
        refId: data.refId
      })
    }
  })
})

onUnload(() => {
  unsubscribeWs?.()
  unsubscribeWs = null
})
</script>

<template>
  <view class="page">
    <MobileHeader
      title="消息通知"
    >
      <template #right>
        <view v-if="notifyStore.items.length" class="mark-all cd-pressable" @click="markAllRead">
          <text class="mark-all-text">全部已读</text>
        </view>
      </template>
    </MobileHeader>

    <view v-if="loading" class="loading-wrap">
      <u-loading-icon mode="circle" size="28" />
    </view>

    <EmptyState
      v-else-if="!notifyStore.items.length"
      icon="bell"
      title="暂无通知"
      description="团队邀请、转码完成等消息会显示在这里"
    />

    <scroll-view v-else scroll-y class="list-scroll">
      <view
        v-for="item in notifyStore.items"
        :key="item.id"
        class="notify-item cd-pressable"
        :class="{ unread: !item.read }"
        @click="onItemTap(item)"
      >
        <view class="notify-head">
          <view class="notify-title-row">
            <view v-if="!item.read" class="unread-dot" />
            <text class="notify-title">{{ item.title }}</text>
          </view>
          <text class="notify-time">{{ formatTime(item.createdAt) }}</text>
        </view>
        <text class="notify-content">{{ item.content }}</text>

        <view
          v-if="item.type === 'TEAM_INVITED' && item.refId && !item.read"
          class="notify-actions"
          @click.stop
        >
          <view
            class="action-btn primary cd-pressable"
            :class="{ disabled: actingId === item.id }"
            @click="acceptInvite(item)"
          >
            <text>接受</text>
          </view>
          <view
            class="action-btn cd-pressable"
            :class="{ disabled: actingId === item.id }"
            @click="rejectInvite(item)"
          >
            <text>拒绝</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-if="unread > 0" class="unread-hint">
      <text>{{ unread }} 条未读</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--cd-bg);
  padding-bottom: calc(env(safe-area-inset-bottom) + 24rpx);
}

.mark-all {
  padding: 8rpx 16rpx;
}

.mark-all-text {
  font-size: 26rpx;
  color: var(--cd-primary);
  font-weight: 500;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 120rpx 0;
}

.list-scroll {
  height: calc(100vh - 200rpx);
  padding: 24rpx 32rpx 0;
  box-sizing: border-box;
}

.notify-item {
  background: #fff;
  border-radius: var(--cd-radius-lg);
  padding: 28rpx 32rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid var(--cd-border-light);
  box-shadow: 0 4rpx 16rpx rgba(15, 23, 42, 0.04);
}

.notify-item.unread {
  background: #f0f5ff;
  border-color: #c6dbfa;
}

.notify-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.notify-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex: 1;
  min-width: 0;
}

.unread-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--cd-primary);
  flex-shrink: 0;
}

.notify-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--cd-text);
}

.notify-time {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  flex-shrink: 0;
}

.notify-content {
  display: block;
  font-size: 26rpx;
  line-height: 1.55;
  color: var(--cd-text-secondary);
}

.notify-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.action-btn {
  flex: 1;
  height: 72rpx;
  border-radius: var(--cd-radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border-light);

  text {
    font-size: 28rpx;
    color: var(--cd-text-secondary);
    font-weight: 500;
  }
}

.action-btn.primary {
  background: var(--cd-primary);
  border-color: var(--cd-primary);

  text {
    color: #fff;
  }
}

.action-btn.disabled {
  opacity: 0.6;
}

.unread-hint {
  position: fixed;
  left: 50%;
  bottom: calc(env(safe-area-inset-bottom) + 32rpx);
  transform: translateX(-50%);
  background: rgba(1, 7, 16, 0.82);
  padding: 12rpx 28rpx;
  border-radius: var(--cd-radius-full);

  text {
    font-size: 24rpx;
    color: #fff;
  }
}
</style>
