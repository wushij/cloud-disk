<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore, type AppNotification } from '@/stores/notification'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { subscribeWs } from '@/utils/ws'

const auth = useAuthStore()
const notifyStore = useNotificationStore()
const loading = ref(false)
const actingId = ref<string | null>(null)
const selectedItem = ref<AppNotification | null>(null)
const detailVisible = ref(false)
const clearAllVisible = ref(false)
const deleteDialogVisible = ref(false)
const itemToDelete = ref<AppNotification | null>(null)

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

async function showDetail(item: AppNotification) {
  selectedItem.value = { ...item }
  detailVisible.value = true
  if (!item.read) {
    await notifyStore.markRead(item.id)
  }
}

async function handleAccept(item: AppNotification) {
  await acceptInvite(item)
  if (selectedItem.value && selectedItem.value.id === item.id) {
    selectedItem.value.inviteStatus = 'ACCEPTED'
    selectedItem.value.read = true
  }
}

async function handleReject(item: AppNotification) {
  await rejectInvite(item)
  if (selectedItem.value && selectedItem.value.id === item.id) {
    selectedItem.value.inviteStatus = 'REJECTED'
    selectedItem.value.read = true
  }
}

async function acceptInvite(item: AppNotification) {
  if (!item.refId || actingId.value) return
  actingId.value = item.id
  try {
    await notifyStore.acceptTeamInvite(item)
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
    await notifyStore.rejectTeamInvite(item)
    uni.showToast({ title: '已拒绝邀请', icon: 'none' })
  } catch {
    /* handled */
  } finally {
    actingId.value = null
  }
}

function triggerDeleteNotification(item: AppNotification) {
  itemToDelete.value = item
  deleteDialogVisible.value = true
}

async function handleConfirmDelete() {
  if (!itemToDelete.value) return
  const id = itemToDelete.value.id
  uni.showLoading({ title: '删除中...' })
  try {
    await notifyStore.deleteNotification(id)
    uni.showToast({ title: '已删除', icon: 'success' })
  } catch {
    /* handled */
  } finally {
    uni.hideLoading()
    itemToDelete.value = null
  }
}

function confirmClearAll() {
  clearAllVisible.value = true
}

async function handleClearAll() {
  uni.showLoading({ title: '清空中...' })
  try {
    await notifyStore.clearAllNotifications()
    uni.showToast({ title: '已清空所有通知', icon: 'success' })
  } catch {
    /* handled */
  } finally {
    uni.hideLoading()
  }
}

async function markAllRead() {
  try {
    await notifyStore.markAllRead()
    uni.showToast({ title: '已全部标记已读', icon: 'success' })
  } catch {
    /* handled */
  }
}

function getNotificationIcon(type: string, title: string) {
  if (type === 'TEAM_INVITED') return 'team'
  if (type === 'SHARE_EXPIRED' || title.includes('分享') || title.includes('外链')) return 'share'
  return 'bell'
}

function getNotificationColorClass(type: string, title: string) {
  if (type === 'TEAM_INVITED') return 'teal'
  if (type === 'SHARE_EXPIRED' || title.includes('分享') || title.includes('外链')) return 'orange'
  return 'indigo'
}

onShow(() => {
  notifyStore.markSeen()
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
  notifyStore.markSeen()
  unsubscribeWs?.()
  unsubscribeWs = null
})
</script>

<template>
  <view class="page">
    <MobileHeader
      title="消息通知"
      :subtitle="unread > 0 ? `${unread} 条未读` : ''"
      gradient
      icon-type="bell"
    >
      <template #right>
        <view v-if="notifyStore.items.length" class="header-action-row">
          <view class="header-action-btn cd-pressable" @click="markAllRead">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
            <text class="header-action-text">全部已读</text>
          </view>
          <view class="header-action-btn danger cd-pressable" @click="confirmClearAll">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
            <text class="header-action-text">清空</text>
          </view>
        </view>
      </template>
    </MobileHeader>

    <view v-if="loading" class="loading-wrap">
      <u-loading-icon mode="circle" size="28" color="var(--cd-primary)" />
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
        class="notify-card cd-pressable"
        :class="{ unread: !item.read }"
        @click="showDetail(item)"
      >
        <view class="notify-card-body">
          <view class="notify-icon-box" :class="getNotificationColorClass(item.type, item.title)">
            <!-- 团队 -->
            <svg v-if="getNotificationIcon(item.type, item.title) === 'team'" width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z" fill="currentColor"/>
            </svg>
            <!-- 分享 -->
            <svg v-else-if="getNotificationIcon(item.type, item.title) === 'share'" width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" fill="currentColor"/>
            </svg>
            <!-- 其他通知 -->
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z" fill="currentColor"/>
            </svg>
          </view>
          
          <view class="notify-card-info">
            <view class="notify-card-head">
              <text class="notify-card-title">{{ item.title }}</text>
              <view class="notify-card-head-right">
                <text class="notify-card-time">{{ formatTime(item.createdAt) }}</text>
                <view class="notify-delete-btn cd-pressable" @click.stop="triggerDeleteNotification(item)">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                  </svg>
                </view>
              </view>
            </view>
            <text class="notify-card-content">{{ item.content }}</text>

            <!-- Invite action pills -->
            <view
              v-if="item.type === 'TEAM_INVITED' && item.refId && item.inviteStatus === 'PENDING'"
              class="notify-card-actions"
              @click.stop
            >
              <view
                class="action-pill danger cd-pressable"
                :class="{ disabled: actingId === item.id }"
                @click="rejectInvite(item)"
              >
                <text>拒绝</text>
              </view>
              <view
                class="action-pill primary cd-pressable"
                :class="{ disabled: actingId === item.id }"
                @click="acceptInvite(item)"
              >
                <text>接受</text>
              </view>
            </view>
            <!-- Invite status label when not pending -->
            <view
              v-else-if="item.type === 'TEAM_INVITED' && item.refId && item.inviteStatus !== 'PENDING'"
              class="notify-card-status-row"
            >
              <view
                class="invite-status-badge"
                :class="item.inviteStatus === 'ACCEPTED' ? 'accepted' : 'rejected'"
              >
                <text>{{ item.inviteStatus === 'ACCEPTED' ? '✓ 已接受邀请' : item.inviteStatus === 'REJECTED' ? '✕ 已拒绝邀请' : '已失效' }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- Detail Modal -->
    <view v-if="detailVisible && selectedItem" class="detail-modal" @touchmove.stop.prevent>
      <view class="detail-mask" @click="detailVisible = false" />
      <view class="detail-panel cd-scale-in" @click.stop>
        <view class="detail-icon-wrap" :class="getNotificationColorClass(selectedItem.type, selectedItem.title)">
          <svg v-if="getNotificationIcon(selectedItem.type, selectedItem.title) === 'team'" width="32" height="32" viewBox="0 0 24 24" fill="none">
            <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z" fill="currentColor"/>
          </svg>
          <svg v-else-if="getNotificationIcon(selectedItem.type, selectedItem.title) === 'share'" width="32" height="32" viewBox="0 0 24 24" fill="none">
            <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" fill="currentColor"/>
          </svg>
          <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="none">
            <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z" fill="currentColor"/>
          </svg>
        </view>

        <text class="detail-title">{{ selectedItem.title }}</text>
        <text class="detail-time">{{ formatTime(selectedItem.createdAt) }}</text>
        
        <scroll-view scroll-y class="detail-body-scroll">
          <text class="detail-content">{{ selectedItem.content }}</text>
        </scroll-view>

        <!-- Pending invite: show accept/reject -->
        <view v-if="selectedItem.type === 'TEAM_INVITED' && selectedItem.refId && selectedItem.inviteStatus === 'PENDING'" class="detail-actions">
          <view class="detail-action-btn danger cd-pressable" @click="handleReject(selectedItem)">
            <text>拒绝</text>
          </view>
          <view class="detail-action-btn primary cd-pressable" @click="handleAccept(selectedItem)">
            <text>接受邀请</text>
          </view>
        </view>
        <!-- Non-pending invite: show status badge + close -->
        <view v-else-if="selectedItem.type === 'TEAM_INVITED' && selectedItem.refId && selectedItem.inviteStatus !== 'PENDING'" class="detail-actions detail-actions-col">
          <view
            class="invite-status-badge large"
            :class="selectedItem.inviteStatus === 'ACCEPTED' ? 'accepted' : 'rejected'"
          >
            <text>{{ selectedItem.inviteStatus === 'ACCEPTED' ? '✓ 已接受邀请' : selectedItem.inviteStatus === 'REJECTED' ? '✕ 已拒绝邀请' : '邀请已失效' }}</text>
          </view>
          <view class="detail-action-btn neutral cd-pressable" @click="detailVisible = false">
            <text>关闭</text>
          </view>
        </view>
        <view v-else class="detail-actions">
          <view class="detail-action-btn neutral cd-pressable" @click="detailVisible = false">
            <text>关闭</text>
          </view>
        </view>
      </view>
    </view>
  </view>

  <!-- Clear All Confirm Dialog -->
  <MobileConfirmDialog
    v-model:show="clearAllVisible"
    title="清空通知"
    message="确定清空所有通知吗？此操作不可恢复，包括已读和未读消息。"
    confirm-text="确认清空"
    danger
    @confirm="handleClearAll"
  />

  <!-- Delete Confirm Dialog -->
  <MobileConfirmDialog
    v-model:show="deleteDialogVisible"
    title="删除通知"
    message="确定删除这条通知吗？此操作不可恢复。"
    confirm-text="确认删除"
    danger
    @confirm="handleConfirmDelete"
  />
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--cd-bg);
  padding-bottom: calc(env(safe-area-inset-bottom) + 24rpx);
}

.header-action-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.header-action-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 20rpx;
  border-radius: var(--cd-radius-full);
  background: rgba(79, 124, 255, 0.05);
  border: 1rpx solid rgba(79, 124, 255, 0.12);
  transition: all var(--cd-transition-fast);

  text {
    font-size: 22rpx;
    color: var(--cd-primary);
    font-weight: 700;
  }

  svg {
    color: var(--cd-primary);
  }

  &:active {
    opacity: 0.8;
    background: rgba(79, 124, 255, 0.12);
  }

  &.danger {
    background: rgba(239, 68, 68, 0.05);
    border-color: rgba(239, 68, 68, 0.12);

    text {
      color: #ef4444;
    }
    svg {
      color: #ef4444;
    }
    &:active {
      background: rgba(239, 68, 68, 0.12);
    }
  }
}

.header-action-text {
  font-size: 22rpx;
  font-weight: 700;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 120rpx 0;
}

.list-scroll {
  height: calc(100vh - 200rpx);
  padding: 24rpx 24rpx 0;
  box-sizing: border-box;
}

.notify-card {
  background: #ffffff;
  border-radius: 28rpx;
  padding: 28rpx 28rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-sm);
  border-left: 6rpx solid transparent;
  transition: all var(--cd-transition);

  &:active {
    transform: scale(0.985);
    background: #f8fafc;
  }
}

.notify-card.unread {
  background: rgba(37, 99, 235, 0.02);
  border-color: rgba(37, 99, 235, 0.15);
  border-left: 6rpx solid var(--cd-primary);
  box-shadow: 0 4rpx 16rpx rgba(37, 99, 235, 0.04);
}

.notify-card-body {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.notify-icon-box {
  flex-shrink: 0;
  width: 80rpx;
  height: 80rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--cd-transition);
}

.notify-icon-box.teal {
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
}

.notify-icon-box.orange {
  background: rgba(249, 115, 22, 0.08);
  color: #f97316;
}

.notify-icon-box.indigo {
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
}

.notify-card-info {
  flex: 1;
  min-width: 0;
}

.notify-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.notify-card-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.notify-card-head-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex-shrink: 0;
}

.notify-card-time {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  flex-shrink: 0;
}

.notify-delete-btn {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0f172a;
  background: transparent;
  border: none;
  transition: opacity var(--cd-transition-fast);

  &:active {
    opacity: 0.5;
  }
}

.notify-card-content {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--cd-text-secondary);
}

.notify-card-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}

.notify-card-status-row {
  margin-top: 12rpx;
}

.invite-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 6rpx 18rpx;
  border-radius: 12rpx;
  font-size: 22rpx;
  font-weight: 600;

  &.accepted {
    background: rgba(13, 148, 136, 0.05);
    color: #0d9488;
    border: 1rpx solid rgba(13, 148, 136, 0.15);
  }

  &.rejected {
    background: rgba(239, 68, 68, 0.05);
    color: #ef4444;
    border: 1rpx solid rgba(239, 68, 68, 0.12);
  }

  &.large {
    font-size: 26rpx;
    padding: 16rpx 32rpx;
    justify-content: center;
    width: 100%;
    border-radius: 16rpx;
    margin-bottom: 16rpx;
  }
}

.action-pill {
  flex: 1;
  height: 64rpx;
  border-radius: var(--cd-radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  border: 1rpx solid var(--cd-border-light);
  box-shadow: var(--cd-shadow-sm);

  text {
    font-size: 24rpx;
    color: var(--cd-text-secondary);
    font-weight: 700;
  }
}

.action-pill.primary {
  background: var(--cd-primary-gradient);
  border: none;
  box-shadow: 0 4rpx 12rpx rgba(1, 7, 16, 0.1);

  text {
    color: #fff;
  }
}

.action-pill.danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  border: none;
  box-shadow: 0 4rpx 12rpx rgba(239, 68, 68, 0.1);

  text {
    color: #fff;
  }
}

.action-pill.disabled {
  opacity: 0.6;
}

/* Detail Modal */
.detail-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}

.detail-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(12rpx);
  -webkit-backdrop-filter: blur(12rpx);
}

.detail-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 580rpx;
  background: #ffffff;
  border-radius: 36rpx;
  padding: 56rpx 40rpx 44rpx;
  box-shadow: 0 24rpx 64rpx rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  border: 1rpx solid rgba(255, 255, 255, 0.6);
}

.detail-icon-wrap {
  width: 110rpx;
  height: 110rpx;
  border-radius: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28rpx;
}

.detail-icon-wrap.teal {
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
}

.detail-icon-wrap.orange {
  background: rgba(249, 115, 22, 0.08);
  color: #f97316;
}

.detail-icon-wrap.indigo {
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
}

.detail-title {
  font-size: 34rpx;
  font-weight: 800;
  color: var(--cd-text);
  margin-bottom: 8rpx;
}

.detail-time {
  font-size: 22rpx;
  color: var(--cd-text-muted);
  margin-bottom: 24rpx;
}

.detail-body-scroll {
  max-height: 320rpx;
  width: 100%;
  margin-bottom: 36rpx;
}

.detail-content {
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--cd-text-secondary);
  text-align: left;
  display: block;
}

.detail-actions {
  display: flex;
  gap: 16rpx;
  width: 100%;

  &.detail-actions-col {
    flex-direction: column;
    align-items: center;
  }
}

.detail-action-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}

.detail-action-btn.primary {
  background: var(--cd-primary-gradient);
  color: #fff;
  box-shadow: 0 8rpx 20rpx rgba(1, 7, 16, 0.15);
}

.detail-action-btn.danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: #fff;
  box-shadow: 0 8rpx 20rpx rgba(239, 68, 68, 0.15);
}

.detail-action-btn.neutral {
  background: #f1f5f9;
  color: var(--cd-text-secondary);
}

.detail-action-btn:active {
  transform: scale(0.97);
  opacity: 0.95;
}
</style>
