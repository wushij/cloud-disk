<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { request, fileApiUrl } from '@/api/http'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import FolderTypeIcon from '@/components/FolderTypeIcon.vue'
import MobileConfirmDialog from '@/components/MobileConfirmDialog.vue'
import { globalShareList } from '@/utils/sharedState'
import { buildPublicShareUrl } from '@/utils/shareUrl'

interface ShareItem {
  id: number
  shareCode: string
  shareType?: string
  fileName?: string
  extractCode?: string
  expireTime?: string
  viewCount?: number
  downloadCount?: number
  fileId?: number
  folderId?: number
  status?: number
}

function isFolderShare(item: ShareItem) {
  return item.shareType === 'FOLDER' || !!item.folderId || (item.fileName || '').includes('（文件夹）')
}

function isTeamFolderShare(item: ShareItem) {
  return isFolderShare(item) && (item.fileName || '').includes('[团队]')
}

function displayShareName(item: ShareItem) {
  return (item.fileName || item.shareCode).replace(/（文件夹）$/, '')
}

function isImageShare(item: ShareItem) {
  if (isFolderShare(item)) return false
  const name = item.fileName || ''
  if (!name || !item.fileId) return false
  const parts = name.split('.')
  if (parts.length <= 1) return false
  const ext = parts.pop()?.toLowerCase() || ''
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)
}

function getShareImageUrl(item: ShareItem) {
  if (!item.fileId) return ''
  return fileApiUrl(`/api/files/${item.fileId}/preview`)
}

function formatExpireTime(timeStr?: string) {
  if (!timeStr) return '永久有效'
  try {
    return timeStr.replace('T', ' ').substring(0, 16)
  } catch {
    return timeStr
  }
}

function getShareIcon(item: ShareItem) {
  const name = item.fileName || ''
  if (!name) return 'share-fill'
  const parts = name.split('.')
  if (parts.length <= 1) return 'folder'
  const ext = parts.pop()?.toLowerCase() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return 'photo-fill'
  if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) return 'play-circle-fill'
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return 'volume-fill'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'file-text-fill'
  if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md'].includes(ext)) return 'file-text-fill'
  return 'file-text-fill'
}

function getShareIconStyle(item: ShareItem) {
  if (isFolderShare(item)) return {}

  const name = item.fileName || ''
  const gradients = {
    folder: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)', // 琥珀
    image: 'linear-gradient(135deg, #10b981 0%, #059669 100%)', // 翡翠
    video: 'linear-gradient(135deg, #a855f7 0%, #7c3aed 100%)', // 罗兰
    audio: 'linear-gradient(135deg, #ec4899 0%, #db2777 100%)', // 玫瑰
    archive: 'linear-gradient(135deg, #f97316 0%, #ea580c 100%)', // 橙红
    document: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)', // 蔚蓝
    default: 'linear-gradient(135deg, #6b7280 0%, #4b5563 100%)' // 灰色
  }
  const shadows = {
    folder: 'rgba(245, 158, 11, 0.22)',
    image: 'rgba(16, 185, 129, 0.22)',
    video: 'rgba(168, 85, 247, 0.22)',
    audio: 'rgba(236, 72, 153, 0.22)',
    archive: 'rgba(249, 115, 22, 0.22)',
    document: 'rgba(59, 130, 246, 0.22)',
    default: 'rgba(107, 114, 128, 0.2)'
  }

  if (!name) return { background: gradients.default, boxShadow: `0 8rpx 20rpx ${shadows.default}` }
  const parts = name.split('.')
  if (parts.length <= 1) return { background: gradients.folder, boxShadow: `0 8rpx 20rpx ${shadows.folder}` }
  const ext = parts.pop()?.toLowerCase() || ''
  
  let key: keyof typeof gradients = 'default'
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) key = 'image'
  else if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) key = 'video'
  else if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) key = 'audio'
  else if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) key = 'archive'
  else if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md'].includes(ext)) key = 'document'
  
  return {
    background: gradients[key],
    boxShadow: `0 8rpx 20rpx ${shadows[key]}`
  }
}

const auth = useAuthStore()
const list = globalShareList
const loading = ref(false)
const activeTab = ref<'active' | 'expired'>('active')

const removeShareVisible = ref(false)
const shareToRemove = ref<ShareItem | null>(null)

const removeShareDialogTitle = computed(() =>
  shareToRemove.value && isExpired(shareToRemove.value) ? '彻底删除' : '取消分享'
)

const removeShareDialogMessage = computed(() => {
  const item = shareToRemove.value
  if (!item) return ''
  const name = displayShareName(item)
  if (isExpired(item)) {
    return `确定彻底删除失效分享「${name}」？删除后无法恢复。`
  }
  return `确定取消分享「${name}」？`
})

const removeShareConfirmText = computed(() =>
  shareToRemove.value && isExpired(shareToRemove.value) ? '彻底删除' : '确定'
)

function isExpired(item: ShareItem) {
  if (item.status === 0) return true
  if (!item.expireTime) return false
  const exp = new Date(item.expireTime.replace('T', ' ').replace(/-/g, '/'))
  return exp.getTime() < Date.now()
}

const expiredList = computed(() => {
  return list.value.filter(item => isExpired(item))
})

const activeList = computed(() => {
  return list.value.filter(item => !isExpired(item))
})

const displayedList = computed(() => {
  return activeTab.value === 'active' ? activeList.value : expiredList.value
})

onShow(async () => {
  uni.hideTabBar({ animation: false }).catch(() => {})
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
  const link = buildPublicShareUrl(item.shareCode)
  uni.setClipboardData({
    data: link,
    success: () => uni.showToast({ title: '链接已复制', icon: 'success' })
  })
}

function removeShare(item: ShareItem) {
  shareToRemove.value = item
  removeShareVisible.value = true
}

async function handleRemoveShareConfirm() {
  const item = shareToRemove.value
  if (!item) return
  const expired = isExpired(item)
  try {
    await request({ url: `/api/share/${item.id}`, method: 'DELETE' })
    if (expired) {
      list.value = list.value.filter((r) => r.id !== item.id)
      uni.showToast({ title: '已彻底删除', icon: 'success' })
    } else {
      const idx = list.value.findIndex((r) => r.id === item.id)
      if (idx >= 0) {
        list.value[idx] = { ...list.value[idx], status: 0 }
      }
      uni.showToast({ title: '已取消分享', icon: 'success' })
    }
  } catch {
    /* handled */
  } finally {
    shareToRemove.value = null
  }
}
</script>

<template>
  <view class="page">
    <MobileHeader 
      title="我的分享" 
      :subtitle="`进行中 ${activeList.length} · 已失效 ${expiredList.length}`" 
      gradient 
      icon-type="share" 
    />

    <!-- 分类 Tab 切换栏 -->
    <view class="tab-bar-container">
      <view class="tab-bar-pill">
        <view 
          class="tab-item" 
          :class="{ active: activeTab === 'active' }"
          @click="activeTab = 'active'"
        >
          <text class="tab-text">进行中</text>
          <text class="tab-badge" v-if="activeList.length > 0">{{ activeList.length }}</text>
        </view>
        <view 
          class="tab-item" 
          :class="{ active: activeTab === 'expired' }"
          @click="activeTab = 'expired'"
        >
          <text class="tab-text">已失效</text>
          <text class="tab-badge expired" v-if="expiredList.length > 0">{{ expiredList.length }}</text>
        </view>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <view v-if="loading && !displayedList.length" class="state-box"><u-loading-icon text="加载中" color="var(--cd-primary)" /></view>
      <view v-else-if="!displayedList.length" class="state-box">
        <EmptyState
          icon="share"
          :title="activeTab === 'active' ? '没有进行中的分享' : '没有已失效的分享'"
          :description="activeTab === 'active' ? '在云盘中长按文件，即可创建分享链接发给好友' : '过期的分享链接清理后会显示在这里'"
        />
      </view>
      <view 
        v-for="item in displayedList" 
        :key="item.id" 
        class="share-card"
        :class="{ 'is-expired-card': isExpired(item) }"
      >
        <view class="share-body" @click="openShare(item)">
          <view
            class="share-badge"
            :class="{
              'is-folder': isFolderShare(item),
              'is-team-folder': isTeamFolderShare(item)
            }"
            :style="getShareIconStyle(item)"
          >
            <image
              v-if="isImageShare(item)"
              class="share-cover-img"
              :src="getShareImageUrl(item)"
              mode="aspectFill"
            />
            <view v-else-if="isFolderShare(item)" class="share-folder-icon">
              <FolderTypeIcon :size="44" />
            </view>
            <u-icon v-else :name="getShareIcon(item)" size="22" color="#fff" />
          </view>
          <view class="share-main">
            <text class="share-name">{{ displayShareName(item) }}</text>
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
            <text v-if="item.expireTime" class="share-expire">
              {{ isExpired(item) ? '已于 ' + formatExpireTime(item.expireTime) + ' 过期' : '过期时间: ' + formatExpireTime(item.expireTime) }}
            </text>
            <text v-else class="share-expire permanent">永久有效</text>
          </view>
        </view>
        <view class="share-actions">
          <template v-if="isExpired(item)">
            <view class="action-chip danger" style="flex: 1;" @click="removeShare(item)">
              <u-icon name="trash" size="16" color="#ef4444" />
              <text>彻底删除</text>
            </view>
          </template>
          <template v-else>
            <view class="action-chip" @click="copyLink(item)">
              <u-icon name="file-text" size="16" color="var(--cd-primary)" />
              <text>复制</text>
            </view>
            <view class="action-chip danger" @click="removeShare(item)">
              <u-icon name="trash" size="16" color="#ef4444" />
              <text>取消</text>
            </view>
          </template>
        </view>
      </view>
    </scroll-view>

    <MobileTabBar active="shares" />

    <MobileConfirmDialog
      v-model:show="removeShareVisible"
      :title="removeShareDialogTitle"
      :message="removeShareDialogMessage"
      :confirm-text="removeShareConfirmText"
      danger
      @confirm="handleRemoveShareConfirm"
    />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(var(--cd-tab-height) + env(safe-area-inset-bottom));
  background: var(--cd-bg);
}

.scroll {
  height: calc(100vh - 300rpx);
  padding: 4rpx 0 24rpx;
}

.share-card {
  position: relative;
  margin: 0 24rpx 18rpx;
  padding: 32rpx;
  background: var(--cd-bg-card);
  border-radius: var(--cd-radius-lg);
  box-shadow: var(--cd-shadow-card);
  border: 1rpx solid var(--cd-border-light);
  transition: all var(--cd-transition-bounce);
}

.share-card.is-expired-card {
  background: #f8fafc !important;
  border-color: #e2e8f0 !important;

  .share-body {
    opacity: 0.72;
  }

  .share-name {
    color: #64748b !important;
    text-decoration: line-through;
  }
}

.tab-bar-container {
  padding: 16rpx 24rpx 8rpx;
  background: var(--cd-bg);
}

.tab-bar-pill {
  display: flex;
  background: rgba(148, 163, 184, 0.08);
  border-radius: 24rpx;
  padding: 6rpx;
  border: 1rpx solid var(--cd-border-light, #f1f5f9);
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 18rpx 0;
  border-radius: 18rpx;
  transition: all var(--cd-transition-fast, 0.2s);
  
  &.active {
    background: #ffffff;
    box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.06);
    
    .tab-text {
      color: var(--cd-primary, #6366f1);
      font-weight: 700;
    }
  }
}

.tab-text {
  font-size: 26rpx;
  color: var(--cd-text-muted, #64748b);
  font-weight: 500;
}

.tab-badge {
  font-size: 18rpx;
  font-weight: 700;
  color: #fff;
  background: var(--cd-primary, #6366f1);
  min-width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 4rpx;
  box-shadow: 0 2rpx 6rpx rgba(99, 102, 241, 0.2);

  &.expired {
    background: #94a3b8;
    box-shadow: none;
  }
}

.share-expire.permanent {
  color: #10b981 !important;
  font-weight: 600;
}

.share-card:active {
  transform: scale(0.985);
  box-shadow: var(--cd-shadow-xs);
  border-color: rgba(1, 7, 16, 0.08);
}

.share-body {
  display: flex;
  align-items: center;
  gap: 24rpx;
  width: 100%;
}

.share-badge {
  width: 112rpx;
  height: 112rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.share-badge.is-folder {
  background: transparent;
  border: none;
  box-shadow: none;
}

.share-badge.is-team-folder {
  background: transparent;
  border: none;
  box-shadow: none;
}

.share-folder-icon {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.share-cover-img {
  width: 100%;
  height: 100%;
  border-radius: 24rpx;
  background: var(--cd-bg-surface);
}

.share-main {
  flex: 1;
  min-width: 0;
}

.share-name {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 10rpx;
}

.stat-pill {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 4rpx 14rpx;
  border-radius: var(--cd-radius-full);
  background: var(--cd-bg-surface);
  font-size: 18rpx;
  color: var(--cd-text-secondary);
  font-weight: 600;
  border: 1rpx solid var(--cd-border-light);
}

.stat-pill.lock {
  background: var(--cd-warning-bg);
  color: var(--cd-warning);
  border-color: rgba(245, 158, 11, 0.12);
}

.share-expire {
  display: block;
  margin-top: 8rpx;
  font-size: 19rpx;
  color: var(--cd-text-muted);
  font-weight: 500;
}

.share-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--cd-border-light);
}

.action-chip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 16rpx 0;
  border-radius: var(--cd-radius-full);
  background: var(--cd-primary-muted);
  font-size: 24rpx;
  color: var(--cd-primary);
  font-weight: 700;
  transition: all var(--cd-transition-fast);
  border: 1rpx solid rgba(1, 7, 16, 0.04);
}

.action-chip:active {
  transform: scale(0.95);
  background: var(--cd-primary-muted-strong);
}

.action-chip.danger {
  background: var(--cd-danger-bg);
  color: var(--cd-danger);
  border: 1rpx solid rgba(239, 68, 68, 0.08);
}

.action-chip.danger:active {
  background: rgba(239, 68, 68, 0.15);
}

.state-box {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}
</style>
